package com.voicehabit.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

data class VoiceProcessResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("raw_transcript") val rawTranscript: String,
    @SerializedName("duration_seconds") val durationSeconds: Double,
    @SerializedName("stt_duration_ms") val sttDurationMs: Int,
    @SerializedName("llm_duration_ms") val llmDurationMs: Int,
    @SerializedName("model_used") val modelUsed: String,
    @SerializedName("summary") val summary: String,
    @SerializedName("habits_completed") val habitsCompleted: List<HabitCompletedDto>,
    @SerializedName("tasks_to_add") val tasksToAdd: List<TaskCreateDto>,
    @SerializedName("tasks_to_complete") val tasksToComplete: List<TaskCompleteDto>,
    @SerializedName("quick_notes") val quickNotes: List<QuickNoteDto>,
    @SerializedName("insights") val insights: List<String>? = null,
    /** H1. Режим, который определила облачная модель. */
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("mode_confidence") val modeConfidence: Double? = null,
    @SerializedName("mode_reason") val modeReason: String? = null,
    /** H1. Конспект свободного потока. */
    @SerializedName("digest") val digest: DigestDto? = null
)

/** H1. Конспект в том же контракте, что и офлайн-разбор: те же секции, те же имена. */
data class DigestDto(
    @SerializedName("title") val title: String? = null,
    @SerializedName("gist") val gist: String? = null,
    @SerializedName("key_points") val keyPoints: List<String>? = null,
    @SerializedName("decisions") val decisions: List<String>? = null,
    @SerializedName("open_questions") val openQuestions: List<String>? = null,
    @SerializedName("next_steps") val nextSteps: List<String>? = null,
    @SerializedName("people") val people: List<String>? = null,
    @SerializedName("numbers") val numbers: List<String>? = null,
    @SerializedName("tone") val tone: String? = null
)

data class HabitCompletedDto(
    @SerializedName("habit_id") val habitId: String?,
    @SerializedName("habit_title") val habitTitle: String,
    @SerializedName("increment_value") val incrementValue: Double?,
    @SerializedName("comment") val comment: String?
)

data class TaskCreateDto(
    @SerializedName("title") val title: String,
    @SerializedName("due_date") val dueDate: String?,
    @SerializedName("priority") val priority: String,
    @SerializedName("category") val category: String,
    @SerializedName("subtasks") val subtasks: List<String>?,
    @SerializedName("task_type") val taskType: String? = null
)

data class TaskCompleteDto(
    @SerializedName("task_id") val taskId: String?,
    @SerializedName("task_title") val taskTitle: String
)

data class QuickNoteDto(
    @SerializedName("text") val text: String,
    @SerializedName("tags") val tags: List<String>?
)
