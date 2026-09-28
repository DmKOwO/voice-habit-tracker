package com.voicehabit.tracker.core.obsidian

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.voicehabit.tracker.data.local.SettingsManager
import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Результат экспорта одного файла в Obsidian Vault.
 */
data class ExportResult(
    val fileName: String,
    val uri: Uri,
    val relativePath: String,
    val hasAudio: Boolean = false
)

/**
 * Сводка массового экспорта в Obsidian Vault.
 */
data class BatchExportResult(
    val totalEntries: Int,
    val successCount: Int,
    val failureCount: Int,
    val vaultName: String
)

/**
 * Менеджер взаимодействия с Obsidian Vault через Storage Access Framework (SAF).
 *
 * Организует структуру:
 * <Vault>/
 * └── Voice Journal/
 *     ├── attachments/
 *     │   └── voice_...m4a
 *     ├── YYYY-MM-DD Заметка.md
 *     └── Привычки и задачи.md
 */
class ObsidianVaultManager(
    private val context: Context,
    private val settings: SettingsManager
) {

    companion object {
        const val DIR_VOICE_JOURNAL = "Voice Journal"
        const val DIR_ATTACHMENTS = "attachments"
        const val HABITS_TASKS_FILE = "Привычки и задачи.md"
    }

    /**
     * Захватывает постоянные права на выбранную папку хранилища (persist across reboot)
     * и сохраняет её Uri в настройки.
     */
    fun saveVaultUri(treeUri: Uri): Boolean {
        runCatching {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        }
        settings.obsidianVaultUri = treeUri.toString()
        return isVaultConfigured()
    }

    /**
     * Сбрасывает сохранённое хранилище и освобождает права SAF.
     */
    fun clearVaultUri() {
        val currentUri = getVaultUri()
        if (currentUri != null) {
            runCatching {
                val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(currentUri, releaseFlags)
            }
        }
        settings.obsidianVaultUri = ""
    }

    /**
     * Возвращает сохранённый Uri дерева хранилища или null.
     */
    fun getVaultUri(): Uri? {
        val uriStr = settings.obsidianVaultUri
        if (uriStr.isBlank()) return null
        return runCatching { Uri.parse(uriStr) }.getOrNull()
    }

    /**
     * Проверяет, настроено ли хранилище и доступно ли оно для записи.
     */
    fun isVaultConfigured(): Boolean {
        val uri = getVaultUri() ?: return false
        return runCatching {
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc != null && doc.exists() && doc.canWrite()
        }.getOrDefault(false)
    }

    /**
     * Возвращает читаемое имя папки хранилища для отображения в UI.
     */
    fun getVaultDisplayName(): String {
        val uri = getVaultUri() ?: return "Не подключено"
        return runCatching {
            val doc = DocumentFile.fromTreeUri(context, uri)
            val docName = doc?.name
            if (!docName.isNullOrBlank()) {
                return@runCatching docName
            }
            val lastSegment = Uri.decode(uri.lastPathSegment ?: "")
            val parsedName = lastSegment.split(":").lastOrNull()?.trim()
            if (!parsedName.isNullOrBlank()) parsedName else "Obsidian Vault"
        }.getOrDefault("Obsidian Vault")
    }

    /**
     * Возвращает подробный путь/статус хранилища.
     */
    fun getVaultDisplayStatus(): String {
        if (!isVaultConfigured()) return "Хранилище не подключено"
        val name = getVaultDisplayName()
        return "Подключено к: $name"
    }

    /**
     * Экспортирует отдельную мысль ([DigestRecord]) в Obsidian Vault.
     * При наличии аудиозаписи копирует её в `Voice Journal/attachments/` и связывает в Markdown.
     */
    suspend fun exportJournalEntry(
        record: DigestRecord,
        audioLocalPath: String? = null
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        val vaultUri = getVaultUri() ?: return@withContext Result.failure(
            IllegalStateException("Obsidian Vault не настроен")
        )
        val rootDoc = DocumentFile.fromTreeUri(context, vaultUri)
            ?: return@withContext Result.failure(IOException("Не удалось открыть директорию Vault"))

        val journalDir = getOrCreateDirectory(rootDoc, DIR_VOICE_JOURNAL)
            ?: return@withContext Result.failure(IOException("Не удалось создать папку '$DIR_VOICE_JOURNAL'"))

        // Копирование аудио (если есть)
        var audioRelativePath: String? = null
        if (!audioLocalPath.isNullOrBlank()) {
            val audioFile = File(audioLocalPath)
            if (audioFile.exists() && audioFile.length() > 0) {
                val attachmentsDir = getOrCreateDirectory(journalDir, DIR_ATTACHMENTS)
                if (attachmentsDir != null) {
                    val audioFileName = "voice_${record.createdAt}_${record.id.takeLast(6)}.m4a"
                    val audioDoc = attachmentsDir.findFile(audioFileName)
                        ?: attachmentsDir.createFile("audio/mp4", audioFileName)

                    if (audioDoc != null) {
                        runCatching {
                            context.contentResolver.openOutputStream(audioDoc.uri, "wt")?.use { out ->
                                FileInputStream(audioFile).use { it.copyTo(out) }
                            }
                            audioRelativePath = "$DIR_ATTACHMENTS/$audioFileName"
                        }
                    }
                }
            }
        }

        // Генерация Markdown-содержимого
        val markdown = ObsidianMarkdownFormatter.formatJournal(record, audioRelativePath)
        val fileName = ObsidianMarkdownFormatter.suggestFileName(record)

        val noteDoc = journalDir.findFile(fileName)
            ?: journalDir.createFile("text/markdown", fileName)
            ?: return@withContext Result.failure(IOException("Не удалось создать файл заметки '$fileName'"))

        return@withContext runCatching {
            context.contentResolver.openOutputStream(noteDoc.uri, "wt")?.use { out ->
                out.write(markdown.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            ExportResult(
                fileName = fileName,
                uri = noteDoc.uri,
                relativePath = "$DIR_VOICE_JOURNAL/$fileName",
                hasAudio = audioRelativePath != null
            )
        }
    }

    /**
     * Экспортирует привычки и задачи в Markdown-файл в Vault.
     */
    suspend fun exportHabitsAndTasks(
        habits: List<Habit>,
        tasks: List<Task>
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        val vaultUri = getVaultUri() ?: return@withContext Result.failure(
            IllegalStateException("Obsidian Vault не настроен")
        )
        val rootDoc = DocumentFile.fromTreeUri(context, vaultUri)
            ?: return@withContext Result.failure(IOException("Не удалось открыть директорию Vault"))

        val journalDir = getOrCreateDirectory(rootDoc, DIR_VOICE_JOURNAL)
            ?: return@withContext Result.failure(IOException("Не удалось создать папку '$DIR_VOICE_JOURNAL'"))

        val markdown = ObsidianMarkdownFormatter.formatHabitsAndTasks(habits, tasks)
        val fileName = HABITS_TASKS_FILE

        val doc = journalDir.findFile(fileName)
            ?: journalDir.createFile("text/markdown", fileName)
            ?: return@withContext Result.failure(IOException("Не удалось создать файл '$fileName'"))

        return@withContext runCatching {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                out.write(markdown.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            ExportResult(
                fileName = fileName,
                uri = doc.uri,
                relativePath = "$DIR_VOICE_JOURNAL/$fileName",
                hasAudio = false
            )
        }
    }

    /**
     * Экспортирует все записи дневника, а также привычки и задачи.
     */
    suspend fun exportAll(
        records: List<DigestRecord>,
        habits: List<Habit>,
        tasks: List<Task>,
        audioLocalPathResolver: suspend (String?) -> String?
    ): BatchExportResult = withContext(Dispatchers.IO) {
        var success = 0
        var failure = 0
        val vaultName = getVaultDisplayName()

        for (record in records) {
            val audioPath = audioLocalPathResolver(record.voiceLogId)
            val result = exportJournalEntry(record, audioPath)
            if (result.isSuccess) {
                success++
            } else {
                failure++
            }
        }

        if (habits.isNotEmpty() || tasks.isNotEmpty()) {
            val habitsTasksResult = exportHabitsAndTasks(habits, tasks)
            if (habitsTasksResult.isSuccess) {
                success++
            } else {
                failure++
            }
        }

        BatchExportResult(
            totalEntries = records.size + if (habits.isNotEmpty() || tasks.isNotEmpty()) 1 else 0,
            successCount = success,
            failureCount = failure,
            vaultName = vaultName
        )
    }

    /**
     * Открывает заметку в официальном приложении Obsidian через deep-link `obsidian://`.
     */
    fun openNoteInObsidian(vaultName: String, relativeFilePath: String): Boolean {
        val encodedVault = Uri.encode(vaultName)
        val encodedFile = Uri.encode(relativeFilePath)
        val obsidianUri = Uri.parse("obsidian://open?vault=$encodedVault&file=$encodedFile")

        val intent = Intent(Intent.ACTION_VIEW, obsidianUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun getOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile? {
        val existing = parent.findFile(name)
        if (existing != null && existing.isDirectory) {
            return existing
        }
        return parent.createDirectory(name)
    }
}
