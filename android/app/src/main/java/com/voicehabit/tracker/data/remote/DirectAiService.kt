package com.voicehabit.tracker.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voicehabit.tracker.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class DirectAiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {

    suspend fun transcribeAudioGroq(
        audioFile: File,
        groqApiKey: String
    ): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-large-v3-turbo")
                .addFormDataPart("language", "ru")
                .addFormDataPart("response_format", "json")
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/m4a".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $groqApiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Ошибка Groq API (${response.code}): $responseBody")
                )
            }

            val json = JsonParser.parseString(responseBody).asJsonObject
            val text = json.get("text")?.asString ?: ""
            val duration = (System.currentTimeMillis() - start).toInt()

            Result.success(Pair(text, duration))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun parseIntentGemini(
        transcript: String,
        geminiApiKey: String,
        clientCurrentTimeIso: String,
        timezone: String,
        activeHabitsJson: String,
        openTasksJson: String,
        userPersonaContext: String = ""
    ): Result<Pair<JsonObject, Int>> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val personaSection = if (userPersonaContext.isNotBlank()) "- Контекст пользователя (интересы, сферы жизни, идеи, фокус, память):\n$userPersonaContext\n" else ""
            val systemInstructionText = """
Ты — персональный AI-ассистент продуктивности и трекинга привычек Duro.
Твоя задача — разобрать поток мыслей пользователя на отдельные решения (намерение → срок → действие) и вернуть JSON.

Контекст:
- Текущее время: $clientCurrentTimeIso (Таймзона: $timezone)
- Активные привычки: $activeHabitsJson
- Открытые задачи: $openTasksJson
$personaSection

ШАГ 0. СНАЧАЛА ОПРЕДЕЛИ РЕЖИМ (поле "mode"). Это главное решение, от него зависит всё остальное.
  - "LOG"     — человек ставит поручение или отчитывается: «напомни купить хлеб завтра», «выпил таблетки».
  - "JOURNAL" — человек наговаривает монолог, рассуждает, делится переживаниями или идеями без команд «напомни/сделай».
                Это Дневник мыслей. Задачи НЕ создаем! Сохраняем всё в digest (заголовок, суть, тезисы, настроение).
  - "DICTATE" — человек просто говорит, надиктовывает мысли, пересказывает разговор.
                Тут НЕЛЬЗЯ придумывать задачи. Его слова сохраняются как конспект.
  - "MIXED"   — внутри рассуждения есть одно конкретное действие. Сохрани и конспект, и действие.
  - "QUERY"   — вопрос к приложению: «что у меня сегодня», «сколько я вчера сделал».
  Укажи mode_confidence от 0 до 1 и коротко mode_reason: почему именно этот режим.
  Если сомневаешься между LOG и JOURNAL/DICTATE — выбирай JOURNAL или DICTATE. Выдуманная задача в списке дел
  хуже, чем потерянная задача внутри конспекта.

ЖЁСТКИЕ ПРАВИЛА РАЗБОРА:
1. Будущее не равно выполненное. Фразы «надо», «нужно», «не забудь», «хочу», «завтра», «напомни» — это план, а не отметка выполнения.
   В habits_completed попадает только реально совершённое действие в прошедшем времени: «сделал», «выпил», «пробежал», «сдал».
2. Модальные слова («надо», «нужно», «хочу», «напомни», «должен») НЕ входят в название задачи.
3. Название задачи — инфинитив без слов «надо/нужно»: «мне завтра надо поучить английский» → title: "Выучить английский", due_date: завтра.
4. task_type: "QUICK" — разовое действие, "LONG" — регулярная цель/привычка («каждый день», «постоянно», «регулярно»).
5. Не выдумывай привычки и задачи. Если фраза непонятна — верни её в quick_notes и опиши причину в insights.
6. В insights кратко запиши ход рассуждения по шагам: что за намерение, какая дата, почему закрыта или не закрыта привычка.
7. Правила дневника/конспекта (режимы JOURNAL, DICTATE и MIXED):
   - Каждая секция заполняется ТОЛЬКО если она реально есть в речи. Пустой список честнее выдуманного пункта.
   - gist — 1–2 предложения: о чём вообще был разговор / Core Insight.
   - key_points — структурированные тезисы, а не пересказ. До 5 пунктов.
   - decisions — что решили / договорились / утвердили. До 5.
   - open_questions — что осталось без ответа. До 5.
   - next_steps — что отсюда следует сделать. До 5. Это кандидаты в задачи, а не сами задачи.
   - people — кого упоминали. До 6.
   - numbers — конкретные цифры с единицами. До 6.
   - tone — эмоциональный тон/настроение («Спокойное», «Вдохновленное», «Тревожное», «Аналитическое») или "" если тон нейтральный.
   - title — ёмкая и точная тема мысли одной строкой до 50 символов (например: «Размышления о смене фокуса в работе»).

8. Контекст пользователя и прозрачная память:
   - Если пользователь формулирует задачу, мысль или идею в терминах своих интересов, проектов или сфер жизни, учитывай это для выбора точной категории (Work, Health, Study, Home, General) и приоритета.
   - Если в речи пользователя есть новые устойчивые факты о нём (новые интересы, замыслы, проекты, роли, привычки, идеи или принципы), добавь их в массив inferred_facts (до 3 фактов, лаконично одной строкой).

Требования к JSON:
{
  "mode": "LOG | JOURNAL | DICTATE | MIXED | QUERY",
  "mode_confidence": 0.0,
  "mode_reason": "почему такой режим",
  "summary": "краткое резюме на русском",
  "insights": ["шаг рассуждения 1", "шаг рассуждения 2"],
  "inferred_facts": ["новый факт о пользователе 1"],
  "digest": {
    "title": "тема разговора",
    "gist": "суть в 1-2 предложения",
    "key_points": [],
    "decisions": [],
    "open_questions": [],
    "next_steps": [],
    "people": [],
    "numbers": [],
    "tone": ""
  },
  "habits_completed": [
    {
      "habit_id": "id существующей привычки или null если новая",
      "habit_title": "название привычки",
      "increment_value": 1.0,
      "comment": "детали или контекст"
    }
  ],
  "tasks_to_add": [
    {
      "title": "название задачи в инфинитиве, без модальных слов",
      "due_date": "ISO-8601 дата дедлайна или null",
      "priority": "HIGH / MEDIUM / LOW",
      "category": "Работа / Дом / Здоровье / General",
      "task_type": "QUICK / LONG",
      "subtasks": []
    }
  ],
  "tasks_to_complete": [
    { "task_id": "id выполненной задачи или null", "task_title": "название" }
  ],
  "quick_notes": [
    { "text": "мысль или идея", "tags": ["тег1"] }
  ]
}
Обязательно верни только валидный JSON без markdown-разметки!
"""

            val requestJson = JsonObject().apply {
                val contentsArray = com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", com.google.gson.JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("text", "Транскрипт пользователя:\n\"\"\"$transcript\"\"\"")
                            })
                        })
                    })
                }
                add("contents", contentsArray)

                val sysInstObj = JsonObject().apply {
                    add("parts", com.google.gson.JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("text", systemInstructionText)
                        })
                    })
                }
                add("systemInstruction", sysInstObj)

                val genConfig = JsonObject().apply {
                    addProperty("responseMimeType", "application/json")
                    addProperty("temperature", 0.1)
                }
                add("generationConfig", genConfig)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)

            val primaryUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey"
            val fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiApiKey"

            var request = Request.Builder()
                .url(primaryUrl)
                .post(body)
                .build()

            var response = client.newCall(request).execute()
            var responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                // Попытка с fallback моделью gemini-1.5-flash
                request = Request.Builder()
                    .url(fallbackUrl)
                    .post(body)
                    .build()
                response = client.newCall(request).execute()
                responseBody = response.body?.string() ?: ""
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Ошибка Gemini API (${response.code}): $responseBody")
                )
            }

            val rootJson = JsonParser.parseString(responseBody).asJsonObject
            val candidates = rootJson.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) {
                return@withContext Result.failure(Exception("Пустой ответ от Gemini"))
            }

            val textContent = candidates[0].asJsonObject
                .getAsJsonObject("content")
                .getAsJsonArray("parts")[0].asJsonObject
                .get("text").asString

            val cleanJsonString = textContent
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val parsedOutput = JsonParser.parseString(cleanJsonString).asJsonObject
            val duration = (System.currentTimeMillis() - start).toInt()

            Result.success(Pair(parsedOutput, duration))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    enum class FocusStepType {
        INITIAL,
        NEXT,
        ALTERNATIVE,
        UNBLOCK
    }

    /**
     * Flow Engine: генерирует ОДНО атомарное физическое действие на 5–7 минут
     * с учетом этапа работы (старт, логическое развитие, смена формата, снятие затыка).
     */
    suspend fun decomposeFocusStep(
        taskOrGoal: String,
        currentStep: String? = null,
        completedSteps: List<String> = emptyList(),
        stepType: FocusStepType = if (currentStep.isNullOrBlank()) FocusStepType.INITIAL else FocusStepType.NEXT,
        geminiApiKey: String,
        userPersonaContext: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (geminiApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No Gemini API key"))
        }
        try {
            val prompt = when (stepType) {
                FocusStepType.INITIAL -> """
Ты — Flow Engine штурман глубокой работы.
Пользователь начинает спринт над целью: "$taskOrGoal".
${if (userPersonaContext.isNotBlank()) "Контекст и память пользователя (интересы, идеи, текущий фокус):\n$userPersonaContext" else ""}

Сформулируй ПЕРВОЕ конкретное атомарное физическое действие на 5–7 минут.
Требования:
1. Конкретный глагол в инфинитиве + осязаемый объект (например: «Открыть список слов B1 и выписать 5 глаголов с предлогами», «Создать черновик документа и набросать 3 раздела»).
2. Выполнимо ровно за 5–7 минут с минимальным порогом входа.
3. НИКАКИХ абстрактных мета-советов вроде «Открыть материалы», «Приступить к работе» или «Начать выполнение».
4. Строго без эмодзи.
5. Верни ТОЛЬКО текст этого действия (одно предложение), без кавычек и префиксов.
                """.trimIndent()

                FocusStepType.NEXT -> """
Ты — Flow Engine штурман глубокой работы.
Пользователь выполняет спринт над целью: "$taskOrGoal".
Только что успешно завершён шаг: "$currentStep".
${if (completedSteps.isNotEmpty()) "Ранее выполненные шаги в этом спринте: ${completedSteps.joinToString("; ")}" else ""}
${if (userPersonaContext.isNotBlank()) "Контекст и память пользователя:\n$userPersonaContext" else ""}

Сформулируй СЛЕДУЮЩИЙ логический шаг на 5–7 минут, развивающий результат предыдущего шага.
Пример: если предыдущий шаг был «Открыть список слов B1 и выписать 5 глаголов с предлогами», то следующий шаг — «Составить по 1 предложению с каждым из 5 глаголов».
Требования:
1. Логическое продолжение результата предыдущего шага.
2. Конкретный глагол + физический объект на 5–7 минут.
3. КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО писать абстрактные мета-фразы: «Выполнить следующий конкретный шаг по задаче», «Продолжить работу над целью» и т.п. Назови точное физическое действие!
4. Строго без эмодзи.
5. Верни ТОЛЬКО текст действия одним предложением без кавычек.
                """.trimIndent()

                FocusStepType.ALTERNATIVE -> """
Ты — Flow Engine штурман глубокой работы.
Пользователь работает над целью: "$taskOrGoal".
Пользователю НЕ ПОДОШЁЛ текущий шаг или формат работы: "$currentStep".
${if (userPersonaContext.isNotBlank()) "Контекст и память пользователя:\n$userPersonaContext" else ""}

Сформулируй АЛЬТЕРНАТИВНЫЙ шаг на 5–7 минут для той же цели, ОБЯЗАТЕЛЬНО СМЕНИВ ФОРМАТ РАБОТЫ (МОДАЛЬНОСТЬ).
Пример: если текущий шаг был письменным («Выписать 5 глаголов»), то альтернативный со сменой формата — «Включить аудиодиалог B1 на 5 минут и выписать незнакомые фразы» (смена на аудирование), или устное проговаривание, или визуальная схема/набросок.
Требования:
1. Смена формата: вместо письма/кода — аудио, устная речь, просмотр примера, схема или быстрый набросок.
2. Выполнимо за 5–7 минут.
3. НИКАКИХ абстрактных фраз («Сделать альтернативный шаг», «Попробовать по-другому»).
4. Строго без эмодзи.
5. Верни ТОЛЬКО текст действия одним предложением без кавычек.
                """.trimIndent()

                FocusStepType.UNBLOCK -> """
Ты — Flow Engine штурман глубокой работы (Focus Guard).
Пользователь столкнулся со ступором или затыком на шаге: "$currentStep" (цель: "$taskOrGoal").
${if (userPersonaContext.isNotBlank()) "Контекст и память пользователя:\n$userPersonaContext" else ""}

Сформулируй УЛЬТРА-ПРОСТОЕ действие на 1–2 минуты, которое снимает психологическое сопротивление и затык (микро-действие с нулевым когнитивным барьером: открыть словарь и найти 1 пример предложения, набросать 1 черновую строку без проверки, прочитать 1 абзац вслух).
Требования:
1. Начинается со слов «Снять затык: ...» с предельно простым действием на 2 минуты.
2. Строго без абстракций («подумать», «сосредоточиться»).
3. Строго без эмодзи.
4. Верни ТОЛЬКО текст действия одним предложением без кавычек.
                """.trimIndent()
            }

            val requestJson = JsonObject().apply {
                val contentsArray = com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", com.google.gson.JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("text", prompt)
                            })
                        })
                    })
                }
                add("contents", contentsArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)

            val primaryUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey"
            val fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiApiKey"

            var request = Request.Builder()
                .url(primaryUrl)
                .post(body)
                .build()

            var response = client.newCall(request).execute()
            var responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                request = Request.Builder()
                    .url(fallbackUrl)
                    .post(body)
                    .build()
                response = client.newCall(request).execute()
                responseBody = response.body?.string() ?: ""
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini error (${response.code}): $responseBody"))
            }

            val json = JsonParser.parseString(responseBody).asJsonObject
            val text = json.getAsJsonArray("candidates")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.get(0)?.asJsonObject
                ?.get("text")?.asString?.trim() ?: ""

            val cleaned = text
                .replace("\"", "")
                .replace("**", "")
                .lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""

            Result.success(cleaned)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
