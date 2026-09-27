package com.voicehabit.tracker.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class VoiceNoteAction(
    val rawTranscript: String = "",
    val summary: String = "",
    val habitsCompleted: List<HabitCompletedAction> = emptyList(),
    val tasksToAdd: List<TaskCreateAction> = emptyList(),
    val tasksToComplete: List<TaskCompleteAction> = emptyList(),
    val tasksToDelete: List<TaskDeleteAction> = emptyList(),
    val tasksToReschedule: List<TaskRescheduleAction> = emptyList(),
    val focusToStart: FocusStartAction? = null,
    /** G5: пользователь спросил «что у меня сегодня» — сущностей нет, нужен ответ. */
    val daySummaryRequested: Boolean = false,
    val quickNotes: List<QuickNoteAction> = emptyList(),
    /**
     * H1. Конспект свободного потока. Заполняется в режимах [IntentMode.DICTATE] и
     * [IntentMode.MIXED] — это ответ на «хочу просто надиктовать и получить выжимку».
     */
    val digest: CaptureDigest? = null,
    /**
     * H1. Что приложение само поняло о намерении: [IntentMode.LOG] — нашёл действие,
     * [IntentMode.DICTATE] — это просто разговор, [IntentMode.MIXED] — и то и то,
     * [IntentMode.QUERY] — вопрос. Пользователь может поправить выбор в шторке разбора.
     */
    val mode: IntentMode = IntentMode.LOG,
    /** Насколько уверен детектор: 0f — угадал, 1f — не сомневается. */
    val modeConfidence: Float = 0f,
    /** Почему выбран именно этот режим, одной строкой. Показывается в шторке. */
    val modeReason: String = "",
    /** Пользователь принудительно выбрал режим — доверяем ему, а не детектору. */
    val modeIsOverridden: Boolean = false,
    /** Как именно разбиралась фраза: намерение, дата, тип задачи, почему не закрыта привычка. */
    val insights: List<String> = emptyList(),
    /** Откуда получен разбор: влияет на то, стоит ли файл в очередь на повторную обработку. */
    val processingMode: VoiceProcessingMode = VoiceProcessingMode.ON_DEVICE_TRANSCRIPT,
    /** id записи в `voice_logs`, чтобы после применения пометить её как APPLIED. */
    val logId: String? = null,
    /** H1. id сохранённого конспекта в `digests`. */
    val digestId: String? = null,
    val sttDurationMs: Int = 0,
    val llmDurationMs: Int = 0,
    val modelUsed: String = "Gemini 3.5 Flash-Lite"
)

enum class VoiceProcessingMode {
    /** Распознавание на устройстве (SpeechRecognizer), разбор локальный. */
    ON_DEVICE_TRANSCRIPT,

    /** Облако: Groq (STT) + Gemini (разбор). */
    CLOUD,

    /** Свой бэкенд. */
    BACKEND,

    /**
     * Локальный офлайн-парсер. Если при этом нет транскрипта, запись кладётся
     * в очередь и будет обработана при появлении сети.
     */
    OFFLINE_FALLBACK
}

@Immutable
data class HabitCompletedAction(
    val habitId: String?,
    val habitTitle: String,
    val incrementValue: Double?,
    val comment: String?,
    val isSelected: Boolean = true
)

@Immutable
data class TaskCreateAction(
    val title: String,
    val dueDate: String?,
    val priority: String,
    val category: String,
    val subtasks: List<String> = emptyList(),
    val taskType: String = TaskType.QUICK.name,
    val isSelected: Boolean = true
)

@Immutable
data class TaskCompleteAction(
    val taskId: String?,
    val taskTitle: String,
    val isSelected: Boolean = true
)

/** G2/G3: удаление и перенос задачи голосом. */
data class TaskDeleteAction(
    val taskId: String?,
    val taskTitle: String,
    var isSelected: Boolean = true
)

data class TaskRescheduleAction(
    val taskId: String?,
    val taskTitle: String,
    val newDueDate: String?,
    var isSelected: Boolean = true
)

/** G4: просьба запустить фокус-таймер. */
data class FocusStartAction(
    val minutes: Int,
    val label: String,
    var isSelected: Boolean = true
)

data class QuickNoteAction(
    val text: String,
    val tags: List<String> = emptyList()
)
