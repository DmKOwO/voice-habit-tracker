package com.voicehabit.tracker.data.repository

import com.voicehabit.tracker.data.local.AppDatabase
import com.voicehabit.tracker.data.local.entity.DigestEntity
import com.voicehabit.tracker.domain.model.CaptureDigest
import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.domain.model.IntentMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * H1. Хранилище конспектов свободного потока.
 *
 * ## Почему конспект пишется сразу, а не по кнопке «Применить»
 *
 * Диктофон не должен спрашивать разрешения на сохранение. Раньше запись голоса
 * попадала в `voice_logs` сразу, а сущности ждали подтверждения в шторке; с конспектом
 * обратный порядок опасен — если пользователь закрыл шторку, разговор был потерян
 * навсегда, и это ровно тот случай, ради которого он его записывал. Конспект пишется
 * на разборе, правки в шторке его не уничтожают.
 */
class DigestRepository(private val db: AppDatabase) {

    // --- Конспекты (H1) ---
    fun allFlow(): Flow<List<DigestRecord>> =
        db.digestDao().allFlow().map { list -> list.map { it.toDomain() } }

    fun countFlow(): Flow<Int> = db.digestDao().countFlow()

    suspend fun recent(limit: Int = 50): List<DigestRecord> =
        db.digestDao().recent(limit).map { it.toDomain() }

    suspend fun byId(id: String): DigestRecord? = db.digestDao().byId(id)?.toDomain()

    suspend fun newId(): String = "dgs_" + UUID.randomUUID().toString().take(12)

    /**
     * Сохраняет конспект из разбора голоса.
     *
     * Запись с тем же [DigestRecord.voiceLogId] перезаписывается, а не дублируется:
     * одна голосовая запись — один разговор, и повторная обработка (например, после
     * появления сети) не должна оставлять в списке два конспекта одного разговора.
     */
    suspend fun saveFromVoice(
        content: CaptureDigest,
        mode: IntentMode,
        modeConfidence: Float,
        transcript: String,
        voiceLogId: String? = null
    ): String {
        val existing = voiceLogId?.let { db.digestDao().byIdForVoiceLog(it) }
        val record = DigestRecord.from(
            id = existing?.id ?: newId(),
            content = content,
            mode = mode,
            modeConfidence = modeConfidence,
            transcript = transcript,
            voiceLogId = voiceLogId,
            pinned = existing?.pinned ?: false,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        db.digestDao().upsert(record.toEntity())
        return record.id
    }

    suspend fun upsert(record: DigestRecord) = db.digestDao().upsert(record.toEntity())

    suspend fun setPinned(id: String, pinned: Boolean) = db.digestDao().setPinned(id, pinned)

    suspend fun delete(id: String) = db.digestDao().delete(id)

    /**
     * Освобождает место: у конспектов месячной давности вычищается транскрипт,
     * а сама выжимка остаётся. Именно она и была нужна.
     */
    suspend fun stripOldTranscripts(olderThanDays: Int = 30) {
        val before = System.currentTimeMillis() - olderThanDays * 86_400_000L
        db.digestDao().stripOldTranscripts(before)
    }

    // --- Отображение ---

    private fun DigestEntity.toDomain() = DigestRecord(
        id = id,
        voiceLogId = voiceLogId,
        mode = runCatching { IntentMode.valueOf(mode) }.getOrDefault(IntentMode.DICTATE),
        modeConfidence = modeConfidence,
        transcript = transcript,
        pinned = pinned,
        createdAt = createdAt,
        title = title,
        gist = gist,
        keyPoints = keyPoints.decodeList(),
        decisions = decisions.decodeList(),
        openQuestions = openQuestions.decodeList(),
        nextSteps = nextSteps.decodeList(),
        people = people.decodeList(),
        numbers = numbers.decodeList(),
        tone = tone,
        wordCount = wordCount,
        speechSeconds = speechSeconds
    )

    private fun DigestRecord.toEntity() = DigestEntity(
        id = id,
        voiceLogId = voiceLogId,
        title = title,
        gist = gist,
        keyPoints = keyPoints.encodeList(),
        decisions = decisions.encodeList(),
        openQuestions = openQuestions.encodeList(),
        nextSteps = nextSteps.encodeList(),
        people = people.encodeList(),
        numbers = numbers.encodeList(),
        tone = tone,
        mode = mode.name,
        modeConfidence = modeConfidence,
        transcript = transcript,
        wordCount = wordCount,
        speechSeconds = speechSeconds,
        pinned = pinned,
        createdAt = createdAt
    )

    /**
     * Разделитель секций — unit separator, а не запятая.
     *
     * Пункты конспекта сплошь и рядом содержат запятые («перенести релиз, добавить рассылку»),
     * и CSV с запятой потребовал бы экранирования в двух местах чтения и записи.
     * Символ U+001F в тексте пользователя не встречается.
     */
    companion object {
        private const val SEPARATOR = "\u001F"

        fun List<String>.encodeList(): String =
            filter { it.isNotBlank() }.joinToString(SEPARATOR)

        fun String.decodeList(): List<String> =
            if (isBlank()) emptyList() else split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
