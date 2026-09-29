package com.voicehabit.tracker.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.voicehabit.tracker.core.analysis.IntentRouter
import com.voicehabit.tracker.core.logging.AppLogger
import com.voicehabit.tracker.core.network.NetworkEndpointPolicy
import com.voicehabit.tracker.data.local.SettingsManager
import com.voicehabit.tracker.data.local.dao.HabitDao
import com.voicehabit.tracker.data.local.dao.TaskDao
import com.voicehabit.tracker.data.local.dao.VoiceLogDao
import com.voicehabit.tracker.data.local.entity.HabitEntity
import com.voicehabit.tracker.data.local.entity.TaskEntity
import com.voicehabit.tracker.data.local.entity.VoiceLogEntity
import com.voicehabit.tracker.data.remote.DirectAiService
import com.voicehabit.tracker.data.remote.OfflineVoiceParser
import com.voicehabit.tracker.data.remote.VoiceApiService
import com.voicehabit.tracker.domain.model.*
import com.voicehabit.tracker.domain.repository.VoiceRepository
import com.voicehabit.tracker.worker.VoiceUploadWorker
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

class VoiceRepositoryImpl(
    private val habitDao: HabitDao,
    private val taskDao: TaskDao,
    private val voiceLogDao: VoiceLogDao,
    private val settingsManager: SettingsManager? = null,
    /**
     * H1. Конспекты пишутся сразу при разборе, а не по кнопке «Применить»: диктофон
     * не должен спрашивать разрешения на сохранение разговора.
     */
    private val digestRepository: DigestRepository? = null,
    private val directAiService: DirectAiService = DirectAiService(),
    private val gson: Gson = Gson()
) : VoiceRepository {

    /**
     * H1. Сохраняет конспект, если разбор его выдал, и возвращает его id.
     * Ошибка записи не должна ронять весь разбор: конспект — ценность, но не повод
     * показать пользователю красный экран вместо его собственных слов.
     */
    private suspend fun persistDigest(action: VoiceNoteAction, logId: String?): VoiceNoteAction {
        val digest = action.digest ?: return action
        val repo = digestRepository ?: return action
        val savedId = runCatching {
            repo.saveFromVoice(
                content = digest,
                mode = action.mode,
                modeConfidence = action.modeConfidence,
                transcript = action.rawTranscript,
                voiceLogId = logId
            )
        }.getOrNull() ?: return action
        return action.copy(digestId = savedId)
    }

    override suspend fun processVoiceAudio(
        audioFile: File,
        clientCurrentTimeIso: String,
        timezone: String,
        spokenTranscript: String?,
        speechSeconds: Int
    ): Result<VoiceNoteAction> {
        val currentHabits = habitDao.getAllHabitsFlow().first()
        val currentTasks = currentTasksForParsing()

        // 1. Если речь уже распознана на устройстве через SpeechRecognizer
        if (!spokenTranscript.isNullOrBlank()) {
            return processSpokenText(
                audioPath = audioFile.absolutePath,
                transcript = spokenTranscript,
                clientCurrentTimeIso = clientCurrentTimeIso,
                timezone = timezone,
                currentHabits = currentHabits,
                currentTasks = currentTasks,
                speechSeconds = speechSeconds
            )
        }

        val habitsJson = gson.toJson(currentHabits.map {
            mapOf("id" to it.id, "title" to it.title, "target_value" to it.targetValue, "unit" to it.unit)
        })
        val tasksJson = gson.toJson(currentTasks.map {
            mapOf("id" to it.id, "title" to it.title, "due_date" to it.dueDateIso)
        })

        // 2. Если есть API ключи Groq и Gemini — облачный вызов
        if (settingsManager != null && settingsManager.useDirectCloud && settingsManager.hasDirectKeys) {
            val sttRes = directAiService.transcribeAudioGroq(audioFile, settingsManager.effectiveGroqApiKey)
            if (sttRes.isSuccess) {
                val (rawTranscript, sttMs) = sttRes.getOrThrow()

                val userPersona = settingsManager.getUserPersonaContext()
                val geminiRes = directAiService.parseIntentGemini(
                    transcript = rawTranscript,
                    geminiApiKey = settingsManager.effectiveGeminiApiKey,
                    clientCurrentTimeIso = clientCurrentTimeIso,
                    timezone = timezone,
                    activeHabitsJson = habitsJson,
                    openTasksJson = tasksJson,
                    userPersonaContext = userPersona
                )

                if (geminiRes.isSuccess) {
                    val (jsonObj, llmMs) = geminiRes.getOrThrow()
                    val domainAction = mapJsonObjectToDomain(jsonObj, rawTranscript, sttMs, llmMs, "Groq + Gemini")
                    val logId = saveVoiceLog(
                        audioFile.absolutePath,
                        rawTranscript,
                        domainAction.summary,
                        VoiceUploadWorker.VOICE_STATUS_PROCESSED
                    )
                    return Result.success(
                        persistDigest(
                            domainAction.copy(logId = logId, processingMode = VoiceProcessingMode.CLOUD),
                            logId
                        )
                    )
                } else {
                    // Gemini не ответил, парсим локально транскрипт от Groq
                    val parsedAction = OfflineVoiceParser.parse(rawTranscript, currentHabits, currentTasks)
                    val logId = saveVoiceLog(
                        audioFile.absolutePath,
                        rawTranscript,
                        parsedAction.summary,
                        VoiceUploadWorker.VOICE_STATUS_PROCESSED
                    )
                    return Result.success(
                        persistDigest(
                            parsedAction.copy(logId = logId, processingMode = VoiceProcessingMode.CLOUD),
                            logId
                        )
                    )
                }
            }
        }

        // 3. Заданный адрес бэкенда, проходящий политику сети (HTTPS либо локальная схема 10.0.2.2).
        //    Дефолтный dev-адрес 10.0.2.2 сам по себе не опрашивается: на реальном устройстве
        //    такого хоста нет и вызов только добавлял бы таймаут к обработке голоса.
        val endpoint = NetworkEndpointPolicy.evaluate(settingsManager?.customBackendUrl)
        if (endpoint is NetworkEndpointPolicy.Endpoint.Allowed &&
            endpoint.baseUrl != VoiceApiService.DEFAULT_BASE_URL
        ) {
            try {
                // Retrofit строится ровно под настроенный адрес: раньше baseUrl оставался 10.0.2.2,
                // и пользовательский бэкенд не мог отработать никогда.
                val apiService = VoiceApiService.create(endpoint.baseUrl)

                val audioRequestBody = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
                val audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, audioRequestBody)
                val textMediaType = "text/plain".toMediaTypeOrNull()
                val timeBody = clientCurrentTimeIso.toRequestBody(textMediaType)
                val tzBody = timezone.toRequestBody(textMediaType)
                val habitsBody = habitsJson.toRequestBody(textMediaType)
                val tasksBody = tasksJson.toRequestBody(textMediaType)

                val response = apiService.processVoiceAudio(
                    audio = audioPart,
                    clientCurrentTime = timeBody,
                    timezone = tzBody,
                    activeHabitsContext = habitsBody,
                    openTasksContext = tasksBody
                )

                if (response.isSuccessful && response.body() != null) {
                    val dto = response.body()!!

                    // Сервер честно сообщает, что разбор не выполнялся и данные
                    // подставлены (ключи не настроены, провайдер упал или контекст
                    // не распарсился). Такой ответ НЕЛЬЗЯ применять к базе: иначе
                    // пользователь говорил про одно, а получал выдуманные отметки
                    // привычек и выдуманные задачи — и не знал об этом.
                    if (dto.simulated) {
                        AppLogger.instance().w(
                            "voice",
                            "Бэкенд вернул синтетический разбор — не применяем к базе",
                            mapOf("model" to dto.modelUsed)
                        )
                        return Result.failure(
                            SyntheticBackendResultException(dto.modelUsed)
                        )
                    }

                    val backendMode = dto.mode?.let { name ->
                        runCatching { IntentMode.valueOf(name.uppercase()) }.getOrNull()
                    }
                    val domainAction = VoiceNoteAction(
                        rawTranscript = dto.rawTranscript,
                        summary = dto.summary,
                        habitsCompleted = dto.habitsCompleted.map {
                            HabitCompletedAction(it.habitId, it.habitTitle, it.incrementValue, it.comment)
                        },
                        tasksToAdd = dto.tasksToAdd.map {
                            TaskCreateAction(
                                title = it.title,
                                dueDate = it.dueDate,
                                priority = it.priority,
                                category = it.category,
                                subtasks = it.subtasks ?: emptyList(),
                                taskType = it.taskType ?: TaskType.QUICK.name
                            )
                        },
                        tasksToComplete = dto.tasksToComplete.map {
                            TaskCompleteAction(it.taskId, it.taskTitle)
                        },
                        quickNotes = dto.quickNotes.map {
                            QuickNoteAction(it.text, it.tags ?: emptyList())
                        },
                        insights = dto.insights ?: emptyList(),
                        // Раньше серверный mode/confidence/digest молча выбрасывались,
                        // и конспект из JOURNAL-ответа приходил в шторку как LOG с
                        // уверенностью 0% и digest = null. Теперь пробрасываем.
                        mode = backendMode ?: IntentMode.LOG,
                        modeConfidence = dto.modeConfidence?.toFloat() ?: 0f,
                        modeReason = dto.modeReason ?: "",
                        digest = dto.digest?.toCaptureDigest(),
                        sttDurationMs = dto.sttDurationMs,
                        llmDurationMs = dto.llmDurationMs,
                        modelUsed = dto.modelUsed
                    )
                    val logId = saveVoiceLog(
                        audioFile.absolutePath,
                        domainAction.rawTranscript,
                        domainAction.summary,
                        VoiceUploadWorker.VOICE_STATUS_PROCESSED
                    )
                    return Result.success(
                        persistDigest(
                            domainAction.copy(logId = logId, processingMode = VoiceProcessingMode.BACKEND),
                            logId
                        )
                    )
                }
            } catch (e: SyntheticBackendResultException) {
                return Result.failure(e)
            } catch (e: Exception) {
                // Раньше здесь был пустой catch: падение бэкенда было неотличимо от
                // «бэкенд не настроен». Теперь причина попадает в лог, а разбор
                // всё равно уходит в офлайн-парсер — он не зависит от сети.
                AppLogger.instance().w(
                    "voice",
                    "Бэкенд недоступен, ухожу в офлайн-разбор",
                    mapOf("err" to (e.message ?: e::class.java.simpleName))
                )
            }
        }

        // 4. Мгновенный офлайн-парсер. Раньше здесь жёстко подставлялось «Выполнены все привычки»,
        //    и любая запись без ключей и бэкенда молча закрывала все привычки.
        val hasTranscript = !spokenTranscript.isNullOrBlank()
        val fallbackAction = OfflineVoiceParser.parse(
            spokenTranscript?.takeIf { it.isNotBlank() } ?: "Не удалось распознать речь",
            currentHabits,
            currentTasks
        )
        // Без транскрипта запись ждёт сети: статус PENDING_UPLOAD честно показывает
        // «в очереди», а не выдуманное «обработано».
        val logId = saveVoiceLog(
            audioFile.absolutePath,
            fallbackAction.rawTranscript,
            fallbackAction.summary,
            if (hasTranscript) {
                VoiceUploadWorker.VOICE_STATUS_PROCESSED
            } else {
                VoiceUploadWorker.VOICE_STATUS_PENDING_UPLOAD
            }
        )
        return Result.success(
            persistDigest(
                fallbackAction.copy(
                    logId = logId,
                    processingMode = VoiceProcessingMode.OFFLINE_FALLBACK
                ),
                logId
            )
        )
    }

    override suspend fun processVoiceText(
        transcript: String,
        clientCurrentTimeIso: String,
        timezone: String,
        speechSeconds: Int
    ): Result<VoiceNoteAction> {
        val currentHabits = habitDao.getAllHabitsFlow().first()
        val currentTasks = currentTasksForParsing()

        return processSpokenText(
            audioPath = "",
            transcript = transcript,
            clientCurrentTimeIso = clientCurrentTimeIso,
            timezone = timezone,
            currentHabits = currentHabits,
            currentTasks = currentTasks,
            speechSeconds = speechSeconds
        )
    }

    private suspend fun processSpokenText(
        audioPath: String,
        transcript: String,
        clientCurrentTimeIso: String,
        timezone: String,
        currentHabits: List<HabitEntity>,
        currentTasks: List<TaskEntity>,
        speechSeconds: Int
    ): Result<VoiceNoteAction> {
        if (settingsManager != null && settingsManager.hasDirectKeys && settingsManager.effectiveGeminiApiKey.isNotBlank()) {
            val habitsJson = gson.toJson(currentHabits.map {
                mapOf("id" to it.id, "title" to it.title, "target_value" to it.targetValue, "unit" to it.unit)
            })
            val tasksJson = gson.toJson(currentTasks.map {
                mapOf("id" to it.id, "title" to it.title, "due_date" to it.dueDateIso)
            })

            val userPersona = settingsManager.getUserPersonaContext()
            val geminiRes = directAiService.parseIntentGemini(
                transcript = transcript,
                geminiApiKey = settingsManager.effectiveGeminiApiKey,
                clientCurrentTimeIso = clientCurrentTimeIso,
                timezone = timezone,
                activeHabitsJson = habitsJson,
                openTasksJson = tasksJson,
                userPersonaContext = userPersona
            )

            if (geminiRes.isSuccess) {
                runCatching {
                    val (jsonObj, llmMs) = geminiRes.getOrThrow()
                    val domainAction = mapJsonObjectToDomain(jsonObj, transcript, 0, llmMs, "Gemini 2.5 Flash")
                    val logId = saveVoiceLog(
                        audioPath,
                        transcript,
                        domainAction.summary,
                        VoiceUploadWorker.VOICE_STATUS_PROCESSED
                    )
                    return Result.success(
                        persistDigest(
                            domainAction.copy(logId = logId, processingMode = VoiceProcessingMode.CLOUD),
                            logId
                        )
                    )
                }.onFailure { err ->
                    AppLogger.instance().w("voice", "Сбой маппинга ответа Gemini: ${err.message}", mapOf("err" to (err.message ?: "")))
                }
            }
        }

        val action = OfflineVoiceParser.parse(transcript, currentHabits, currentTasks, speechSeconds)
        val logId = saveVoiceLog(
            audioPath,
            transcript,
            action.summary,
            VoiceUploadWorker.VOICE_STATUS_PROCESSED
        )
        return Result.success(
            persistDigest(
                action.copy(
                    logId = logId,
                    processingMode = VoiceProcessingMode.ON_DEVICE_TRANSCRIPT
                ),
                logId
            )
        )
    }

    /**
     * Задачи для голосового контекста: открытые плюс выполненные сегодня.
     *
     * Раньше сюда попадали только открытые задачи, поэтому фраза «сдал отчёт по проекту»
     * не могла закрыть задачу, отмеченную сегодня вручную, — ИИ её просто не видел.
     * Задачи, закрытые в более ранние дни, в контекст не берутся: иначе LLM предложит
     * «закрыть» то, что уже закрыто.
     */
    suspend fun currentTasksForParsing(): List<TaskEntity> {
        val startOfToday = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return taskDao.getAllTasksList().filter { task ->
            !task.isCompleted || (task.completedAt != null && task.completedAt >= startOfToday)
        }
    }

    suspend fun currentHabitsForParsing(): List<HabitEntity> = habitDao.getAllHabitsList()

    override suspend fun saveVoiceLog(
        audioPath: String,
        transcript: String,
        summary: String,
        status: String
    ): String {
        val id = "voice_" + UUID.randomUUID().toString()
        voiceLogDao.upsertVoiceLog(
            VoiceLogEntity(
                id = id,
                audioPath = audioPath,
                rawTranscript = transcript,
                summary = summary,
                status = status,
                createdAt = System.currentTimeMillis()
            )
        )
        return id
    }

    private fun mapJsonObjectToDomain(
        json: JsonObject,
        rawTranscript: String,
        sttMs: Int,
        llmMs: Int,
        modelUsed: String
    ): VoiceNoteAction {
        val summary = runCatching {
            json.get("summary")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
        }.getOrNull() ?: "Запись распознана"

        val habitsList = mutableListOf<HabitCompletedAction>()
        if (json.has("habits_completed") && json.get("habits_completed").isJsonArray) {
            json.getAsJsonArray("habits_completed").forEach { elem ->
                if (elem.isJsonObject) {
                    val obj = elem.asJsonObject
                    val id = if (obj.has("habit_id") && !obj.get("habit_id").isJsonNull && obj.get("habit_id").isJsonPrimitive) obj.get("habit_id").asString else null
                    val title = if (obj.has("habit_title") && !obj.get("habit_title").isJsonNull && obj.get("habit_title").isJsonPrimitive) obj.get("habit_title").asString else "Привычка"
                    val inc = runCatching {
                        if (obj.has("increment_value") && !obj.get("increment_value").isJsonNull) obj.get("increment_value").asDouble else null
                    }.getOrNull()
                    val comm = if (obj.has("comment") && !obj.get("comment").isJsonNull && obj.get("comment").isJsonPrimitive) obj.get("comment").asString else null
                    habitsList.add(HabitCompletedAction(id, title, inc, comm))
                }
            }
        }

        val tasksList = mutableListOf<TaskCreateAction>()
        if (json.has("tasks_to_add") && json.get("tasks_to_add").isJsonArray) {
            json.getAsJsonArray("tasks_to_add").forEach { elem ->
                if (elem.isJsonObject) {
                    val obj = elem.asJsonObject
                    val title = if (obj.has("title") && !obj.get("title").isJsonNull && obj.get("title").isJsonPrimitive) obj.get("title").asString else "Задача"
                    val due = if (obj.has("due_date") && !obj.get("due_date").isJsonNull && obj.get("due_date").isJsonPrimitive) obj.get("due_date").asString else null
                    val prio = if (obj.has("priority") && !obj.get("priority").isJsonNull && obj.get("priority").isJsonPrimitive) obj.get("priority").asString else "MEDIUM"
                    val cat = if (obj.has("category") && !obj.get("category").isJsonNull && obj.get("category").isJsonPrimitive) obj.get("category").asString else "General"
                    val type = if (obj.has("task_type") && !obj.get("task_type").isJsonNull && obj.get("task_type").isJsonPrimitive) obj.get("task_type").asString else TaskType.QUICK.name
                    tasksList.add(TaskCreateAction(title, due, prio, cat, taskType = type))
                }
            }
        }

        val completedTasksList = mutableListOf<TaskCompleteAction>()
        if (json.has("tasks_to_complete") && json.get("tasks_to_complete").isJsonArray) {
            json.getAsJsonArray("tasks_to_complete").forEach { elem ->
                if (elem.isJsonObject) {
                    val obj = elem.asJsonObject
                    val id = if (obj.has("task_id") && !obj.get("task_id").isJsonNull && obj.get("task_id").isJsonPrimitive) obj.get("task_id").asString else null
                    val title = if (obj.has("task_title") && !obj.get("task_title").isJsonNull && obj.get("task_title").isJsonPrimitive) obj.get("task_title").asString else ""
                    completedTasksList.add(TaskCompleteAction(id, title))
                }
            }
        }

        val notesList = mutableListOf<QuickNoteAction>()
        if (json.has("quick_notes") && json.get("quick_notes").isJsonArray) {
            json.getAsJsonArray("quick_notes").forEach { elem ->
                if (elem.isJsonObject) {
                    val obj = elem.asJsonObject
                    val text = if (obj.has("text") && !obj.get("text").isJsonNull && obj.get("text").isJsonPrimitive) obj.get("text").asString else ""
                    val tags = mutableListOf<String>()
                    if (obj.has("tags") && obj.get("tags").isJsonArray) {
                        obj.getAsJsonArray("tags").forEach { if (it.isJsonPrimitive) tags.add(it.asString) }
                    }
                    notesList.add(QuickNoteAction(text, tags))
                }
            }
        }

        val insightsList = mutableListOf<String>()
        if (json.has("insights") && json.get("insights").isJsonArray) {
            json.getAsJsonArray("insights").forEach { elem ->
                if (elem.isJsonPrimitive) insightsList.add(elem.asString)
            }
        }

        // Автоматическое пополнение прозрачной памяти фактами от ИИ
        if (json.has("inferred_facts") && json.get("inferred_facts").isJsonArray) {
            json.getAsJsonArray("inferred_facts").forEach { elem ->
                if (elem.isJsonPrimitive) {
                    val fact = elem.asString.trim()
                    if (fact.isNotBlank()) {
                        settingsManager?.addMemoryFact(fact)
                    }
                }
            }
        }

        val digest = if (json.has("digest") && json.get("digest").isJsonObject) {
            json.getAsJsonObject("digest")?.let { parseDigest(it, rawTranscript) }
        } else null

        val confidence = runCatching {
            json.get("mode_confidence")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asDouble?.toFloat()
        }.getOrNull() ?: IntentRouter.route(rawTranscript).confidence

        val reason = runCatching {
            json.get("mode_reason")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
        }.getOrNull() ?: ""

        return VoiceNoteAction(
            rawTranscript = rawTranscript,
            summary = summary,
            habitsCompleted = habitsList,
            tasksToAdd = tasksList,
            tasksToComplete = completedTasksList,
            quickNotes = notesList,
            digest = digest,
            mode = resolveMode(json, rawTranscript),
            modeConfidence = confidence,
            modeReason = reason,
            insights = insightsList,
            sttDurationMs = sttMs,
            llmDurationMs = llmMs,
            modelUsed = modelUsed
        )
    }

    /**
     * H1. Режим от облака, но с обязательной проверкой на согласованность.
     *
     * Модель может ответить `mode: "DICTATE"` и при этом навыдумывать пять задач —
     * тогда пользователь получил бы в шторке «конспект» вперемешку с выдуманным
     * списком дел. Поэтому режим, который запрещает создавать сущности, но принёс
     * сущности, понижается до [IntentMode.MIXED], и конспект при этом остаётся.
     * Обратное (режим LOG без единой сущности) просто дополняется конспектом.
     */
    private fun resolveMode(json: JsonObject, transcript: String): IntentMode {
        val declared = json.get("mode")?.takeIf { !it.isJsonNull }?.asString
            ?.let { name -> runCatching { IntentMode.valueOf(name.trim().uppercase()) }.getOrNull() }
            ?: IntentRouter.route(transcript).mode
        return when {
            !declared.producesEntities &&
                (json.getAsJsonArray("tasks_to_add")?.size() ?: 0) > 0 -> IntentMode.MIXED
            else -> declared
        }
    }

    private fun parseDigest(obj: JsonObject, transcript: String): CaptureDigest? {
        fun list(key: String): List<String> {
            val array = obj.getAsJsonArray(key) ?: return emptyList()
            return array.mapNotNull { elem ->
                if (elem.isJsonPrimitive) elem.asString.trim().takeIf { it.isNotEmpty() } else null
            }
        }

        val digest = CaptureDigest(
            title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString?.trim()?.take(60).orEmpty(),
            gist = obj.get("gist")?.takeIf { !it.isJsonNull }?.asString?.trim()?.take(600).orEmpty(),
            keyPoints = list("key_points").take(MAX_DIGEST_ITEMS),
            decisions = list("decisions").take(MAX_DIGEST_ITEMS),
            openQuestions = list("open_questions").take(MAX_DIGEST_ITEMS),
            nextSteps = list("next_steps").take(MAX_DIGEST_ITEMS),
            people = list("people").take(MAX_PEOPLE_ITEMS),
            numbers = list("numbers").take(MAX_PEOPLE_ITEMS),
            tone = obj.get("tone")?.takeIf { !it.isJsonNull }?.asString?.trim()?.take(24).orEmpty(),
            wordCount = transcript.split(' ').count { it.isNotBlank() }
        )
        return digest.takeIf { !it.isEmpty }
    }

    companion object {
        private const val MAX_DIGEST_ITEMS = 5
        private const val MAX_PEOPLE_ITEMS = 6
    }
}
