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
        openTasksJson: String
    ): Result<Pair<JsonObject, Int>> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val systemInstructionText = """
Ты — персональный AI-ассистент продуктивности и трекинга привычек Duro.
Твоя задача — разобрать поток мыслей пользователя на отдельные решения (намерение → срок → действие) и вернуть JSON.

Контекст:
- Текущее время: $clientCurrentTimeIso (Таймзона: $timezone)
- Активные привычки: $activeHabitsJson
- Открытые задачи: $openTasksJson

ШАГ 0. СНАЧАЛА ОПРЕДЕЛИ РЕЖИМ (поле "mode"). Это главное решение, от него зависит всё остальное.
  - "LOG"    — человек ставит поручение или отчитывается: «напомни купить хлеб завтра», «выпил таблетки».
  - "DICTATE" — человек просто говорит, надиктовывает мысли, рассуждает, пересказывает разговор.
               Тут НЕЛЬЗЯ придумывать задачи. Его слова сохраняются как конспект.
  - "MIXED"  — внутри рассуждения есть одно конкретное действие. Сохрани и конспект, и действие.
  - "QUERY"  — вопрос к приложению: «что у меня сегодня», «сколько я вчера сделал».
  Укажи mode_confidence от 0 до 1 и коротко mode_reason: почему именно этот режим.
  Если сомневаешься между LOG и DICTATE — выбирай DICTATE. Выдуманная задача в списке дел
  хуже, чем потерянная задача внутри конспекта.

ЖЁСТКИЕ ПРАВИЛА РАЗБОРА:
1. Будущее не равно выполненное. Фразы «надо», «нужно», «не забудь», «хочу», «завтра», «напомни» — это план, а не отметка выполнения.
   В habits_completed попадает только реально совершённое действие в прошедшем времени: «сделал», «выпил», «пробежал», «сдал».
2. Модальные слова («надо», «нужно», «хочу», «напомни», «должен») НЕ входят в название задачи.
3. Название задачи — инфинитив без слов «надо/нужно»: «мне завтра надо поучить английский» → title: "Выучить английский", due_date: завтра.
4. task_type: "QUICK" — разовое действие, "LONG" — регулярная цель/привычка («каждый день», «постоянно», «регулярно»).
5. Не выдумывай привычки и задачи. Если фраза непонятна — верни её в quick_notes и опиши причину в insights.
6. В insights кратко запиши ход рассуждения по шагам: что за намерение, какая дата, почему закрыта или не закрыта привычка.
7. Правила конспекта (режимы DICTATE и MIXED):
   - Каждая секция заполняется ТОЛЬКО если она реально есть в речи. Пустой список честнее выдуманного пункта.
   - gist — 1–2 предложения: о чём вообще был разговор.
   - key_points — тезисы, а не пересказ. До 5 пунктов.
   - decisions — что решили / договорились / утвердили. До 5.
   - open_questions — что осталось без ответа. До 5.
   - next_steps — что отсюда следует сделать. До 5. Это кандидаты в задачи, а не сами задачи.
   - people — кого упоминали. До 6.
   - numbers — конкретные цифры с единицами. До 6.
   - tone — одно слово о тоне разговора («Усталость», «Подъём», «Тревога») или "" если тон нейтральный.
   - title — тема разговора одной строкой до 50 символов.

Требования к JSON:
{
  "mode": "LOG | DICTATE | MIXED | QUERY",
  "mode_confidence": 0.0,
  "mode_reason": "почему такой режим",
  "summary": "краткое резюме на русском",
  "insights": ["шаг рассуждения 1", "шаг рассуждения 2"],
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
}
