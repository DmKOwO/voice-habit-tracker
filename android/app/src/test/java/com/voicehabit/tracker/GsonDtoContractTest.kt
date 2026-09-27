package com.voicehabit.tracker

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.voicehabit.tracker.data.remote.dto.HabitCompletedDto
import com.voicehabit.tracker.data.remote.dto.QuickNoteDto
import com.voicehabit.tracker.data.remote.dto.TaskCompleteDto
import com.voicehabit.tracker.data.remote.dto.TaskCreateDto
import com.voicehabit.tracker.data.remote.dto.VoiceProcessResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Контракт рефлексии для release-сборки (R8).
 *
 * Gson разбирает DTO по аннотациям @SerializedName, поэтому R8 обязан сохранить и поля
 * с аннотациями, и сами аннотации (см. proguard-rules.pro). Тест фиксирует оба условия:
 * аннотация не должна потеряться/быть снята, а snake_case-ключи ответа должны доезжать
 * до доменных полей.
 */
class GsonDtoContractTest {

    private val gson = Gson()

    @Test
    fun everyDtoFieldKeepsSerializedNameAnnotation() {
        dtoClasses().forEach { type ->
            type.declaredFields
                // $stable и прочие служебные поля, добавленные Compose-компилятором, Gson не нужны
                .filterNot { field -> field.isSynthetic || Modifier.isStatic(field.modifiers) }
                .forEach { field ->
                    assertTrue(
                        "Поле ${type.simpleName}.${field.name} должно иметь @SerializedName " +
                            "(иначе R8/Gson не сопоставят snake_case-ключ)",
                        field.getAnnotation(SerializedName::class.java) != null
                    )
                }
        }
    }

    @Test
    fun deserializesSnakeCaseBackendResponse() {
        val json = """
            {
              "success": true,
              "raw_transcript": "пробежал пять километров",
              "duration_seconds": 12.5,
              "stt_duration_ms": 900,
              "llm_duration_ms": 1500,
              "model_used": "Groq + Gemini",
              "summary": "Пробежка",
              "habits_completed": [
                {"habit_id": "habit_run", "habit_title": "Workout", "increment_value": 5.0, "comment": "утром"}
              ],
              "tasks_to_add": [
                {"title": "купить кроссовки", "due_date": null, "priority": "HIGH", "category": "Health", "subtasks": ["большой размер"]}
              ],
              "tasks_to_complete": [
                {"task_id": "task_1", "task_title": "Сдать отчет"}
              ],
              "quick_notes": [
                {"text": "темп 5:20", "tags": ["бег"]}
              ]
            }
        """.trimIndent()

        val dto = gson.fromJson(json, VoiceProcessResponseDto::class.java)

        assertTrue(dto.success)
        assertEquals("пробежал пять километров", dto.rawTranscript)
        assertEquals(12.5, dto.durationSeconds, 0.0001)
        assertEquals(900, dto.sttDurationMs)
        assertEquals(1500, dto.llmDurationMs)
        assertEquals("Groq + Gemini", dto.modelUsed)
        assertEquals(1, dto.habitsCompleted.size)
        assertEquals("habit_run", dto.habitsCompleted[0].habitId)
        assertEquals("Workout", dto.habitsCompleted[0].habitTitle)
        assertEquals(5.0, dto.habitsCompleted[0].incrementValue!!, 0.0001)
        assertEquals("утром", dto.habitsCompleted[0].comment)
        assertEquals("купить кроссовки", dto.tasksToAdd[0].title)
        assertEquals("HIGH", dto.tasksToAdd[0].priority)
        assertEquals(listOf("большой размер"), dto.tasksToAdd[0].subtasks)
        assertEquals("task_1", dto.tasksToComplete[0].taskId)
        assertEquals(listOf("бег"), dto.quickNotes[0].tags)
    }

    @Test
    fun keepsNullsAndNullsSafeDefaults() {
        val json = """
            {
              "success": true,
              "raw_transcript": "ничего",
              "duration_seconds": 1.0,
              "stt_duration_ms": 0,
              "llm_duration_ms": 0,
              "model_used": "offline",
              "summary": "пусто",
              "habits_completed": [{"habit_id": null, "habit_title": "Vitamin", "increment_value": null, "comment": null}],
              "tasks_to_add": [],
              "tasks_to_complete": [],
              "quick_notes": []
            }
        """.trimIndent()

        val dto = gson.fromJson(json, VoiceProcessResponseDto::class.java)

        assertTrue(dto.habitsCompleted[0].habitId == null)
        assertTrue(dto.habitsCompleted[0].incrementValue == null)
        assertEquals("Vitamin", dto.habitsCompleted[0].habitTitle)
        assertTrue(dto.tasksToAdd.isEmpty())
    }

    @Test
    fun singleFieldDtosMapSnakeCaseNames() {
        val habit = gson.fromJson(
            "{\"habit_id\": \"h1\", \"habit_title\": \"Make Bed\", \"increment_value\": 1.0, \"comment\": null}",
            HabitCompletedDto::class.java
        )
        val task = gson.fromJson(
            "{\"task_id\": null, \"task_title\": \"Позвонить\"}",
            TaskCompleteDto::class.java
        )
        val note = gson.fromJson("{\"text\": \"мысль\", \"tags\": null}", QuickNoteDto::class.java)
        val created = gson.fromJson(
            "{\"title\": \"t\", \"due_date\": null, \"priority\": \"LOW\", \"category\": \"General\", \"subtasks\": null}",
            TaskCreateDto::class.java
        )

        assertEquals("h1", habit.habitId)
        assertEquals("Make Bed", habit.habitTitle)
        assertEquals("Позвонить", task.taskTitle)
        assertEquals("мысль", note.text)
        assertEquals("LOW", created.priority)
    }

    private fun dtoClasses(): List<Class<*>> = listOf(
        VoiceProcessResponseDto::class.java,
        HabitCompletedDto::class.java,
        TaskCreateDto::class.java,
        TaskCompleteDto::class.java,
        QuickNoteDto::class.java
    )
}
