package com.voicehabit.tracker.presentation.home

import androidx.compose.runtime.Immutable
import com.voicehabit.tracker.core.logging.ActiveOperation
import com.voicehabit.tracker.core.logging.LogEvent
import com.voicehabit.tracker.core.logging.LogLevel
import com.voicehabit.tracker.domain.model.AppStats
import com.voicehabit.tracker.domain.model.Challenge
import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.domain.model.FocusSession
import com.voicehabit.tracker.domain.model.FocusStartAction
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.Routine
import com.voicehabit.tracker.domain.model.Subtask
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.domain.model.VoiceNoteAction

/** 3 ключевые вкладки новой навигации: Ритм, Дневник, Обзор. */
enum class MainTab(val title: String) {
    RHYTHM("Ритм"),
    JOURNAL("Дневник"),
    OVERVIEW("Обзор");

    val label: String get() = title
}

/** Экраны хаба (панель «Ещё») и вспомогательные экраны. */
enum class AppScreen {
    HOME, HUB, STATS, ARCHIVE, ROUTINES, CHALLENGES, ACHIEVEMENTS, REVIEW, FOCUS, BACKUP, ABOUT,
    /** H1: конспекты свободного потока — «диктофон с выжимкой». */
    DIGESTS,
    /** Дневник мыслей первого класса. */
    JOURNAL,
    /** Модуль «Контекст обо мне» (User Persona & Memory Engine). */
    USER_PERSONA
}

/** Сортировка задач (G11). */
enum class TaskSort(val title: String) {
    MANUAL("Мой порядок"),
    DUE("Сначала срочные"),
    PRIORITY("По приоритету"),
    CREATED("Сначала новые")
}

/** Черновик фокуса: подстановка из карточки задачи (G10). */
data class FocusDraft(
    val minutes: Int,
    val label: String,
    val taskId: String? = null,
    val habitId: String? = null
)

/** Идущий фокус-таймер и Flow Engine спринт (G4/F8). */
data class FocusRun(
    val totalSec: Int,
    val remainingSec: Int,
    val label: String,
    val taskId: String? = null,
    val habitId: String? = null,
    val isPaused: Boolean = false,
    val pauseReasonPrompt: Boolean = false,
    val isAdaptiveMicroSprint: Boolean = false,
    val currentStep: String? = null,
    val completedSteps: List<String> = emptyList(),
    val showDebriefModal: Boolean = false,
    val isStepLoading: Boolean = false
) {
    val progress: Float get() = if (totalSec <= 0) 0f else 1f - remainingSec.toFloat() / totalSec
}

/** Предпросмотр импорта для экрана бэкапа. */
data class ImportPreviewUi(
    val tables: Map<String, Int>,
    val warnings: List<String>
)

@Immutable
data class HomeState(
    val habits: List<Habit> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
    val completedEarlierCount: Int = 0,
    val showCompletedTasks: Boolean = true,
    val selectedTaskForEdit: Task? = null,
    val selectedTab: String = "All", // "All", "D", "W", "M"
    val isGridView: Boolean = true, // Grid view (true) or List view (false)
    val isCreateHabitOpen: Boolean = false,
    val yearProgressPercentage: Int = 74,
    val dateDisplayString: String = "SEP 25",
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val pendingReviewAction: VoiceNoteAction? = null,
    val isSettingsOpen: Boolean = false,
    val selectedHabitForDetail: Habit? = null,
    val hasApiKeysConfigured: Boolean = false,
    val isVoiceQueueOpen: Boolean = false,
    val voiceLogs: List<com.voicehabit.tracker.data.local.entity.VoiceLogEntity> = emptyList(),
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val isOperationsLogOpen: Boolean = false,
    val activeOperations: List<ActiveOperation> = emptyList(),
    val logEvents: List<LogEvent> = emptyList(),
    val logLevelFilter: LogLevel? = null,
    val snackbarMessage: String? = null,
    /**
     * День, за который посчитаны секции задач (epoch-day). Меняется только через
     * [HomeViewModel.onDayChanged]: это и есть midnight-reset — секции и шапка даты
     * пересчитываются при смене суток, а не висят вчерашними до первого тапа.
     */
    val todayEpochDay: Long = java.time.LocalDate.now().toEpochDay(),
    // Навигация и фильтры (новая 3-вкладочная структура: Ритм, Дневник, Обзор)
    val selectedMainTab: MainTab = MainTab.RHYTHM,
    val isProfileMenuOpen: Boolean = false,
    val isSearchExpanded: Boolean = false,
    val journalFilterMood: String? = null,
    val journalFilterTag: String? = null,
    val journalSearchQuery: String = "",
    val selectedJournalForDetail: DigestRecord? = null,
    val screen: AppScreen = AppScreen.HOME,
    val searchQuery: String = "",
    val taskSort: TaskSort = TaskSort.MANUAL,
    val categoryFilter: String? = null,
    // Архив и корзина (F7)
    val archivedHabits: List<Habit> = emptyList(),
    val trashedHabits: List<Habit> = emptyList(),
    val trashedTasks: List<Task> = emptyList(),
    // Рутины, челленджи, достижения, фокус, разборы (F2/F17/F16/F8/F3)
    val routines: List<Routine> = emptyList(),
    val editingRoutine: Routine? = null,
    val challenges: List<Challenge> = emptyList(),
    val editingChallenge: Challenge? = null,
    val unlockedAchievements: Set<String> = emptySet(),
    val focusSessions: List<FocusSession> = emptyList(),
    val focusRun: FocusRun? = null,
    val focusDebriefRun: FocusRun? = null,
    val focusRequest: FocusStartAction? = null,
    val focusDraft: FocusDraft? = null,
    val reviews: List<Pair<Long, String>> = emptyList(),
    val daySummary: String? = null,
    // Конспекты свободного потока (H1)
    val digests: List<DigestRecord> = emptyList(),
    // Модуль «Контекст обо мне»
    val userPersonaHardFacts: String = "",
    val userPersonaActiveFocus: String = "",
    val userPersonaMemoryLog: Set<String> = emptySet(),
    // Подзадачи по открытым редакторам (F5)
    val subtasks: Map<String, List<Subtask>> = emptyMap(),
    // Статистика, настроение, здоровье (F15/G23/G26/F11)
    val stats: AppStats? = null,
    val moods: Map<Long, Int> = emptyMap(),
    val moodToday: Int? = null,
    val healthSteps: Int? = null,
    val healthSleepHours: Double? = null,
    val healthStatus: String = "unknown",
    // Vosk (F1)
    val voskStatus: String = "unknown",
    val voskProgress: Float = 0f,
    // Онбординг (F18/G39)
    val showOnboarding: Boolean = false,
    val onboardingPage: Int = 0,
    // Бэкап (F13/G34/G35)
    val lastBackupName: String? = null,
    val importPreview: ImportPreviewUi? = null,
    // Празднование закрытого дня (G31)
    val celebrateAllDone: Boolean = false,
    // Тема (F19): читается из настроек, пересоздаёт палитру.
    val themePreset: com.voicehabit.tracker.presentation.theme.AppThemePreset = com.voicehabit.tracker.presentation.theme.AppThemePreset.NOIR_INK,
    val amoledTheme: Boolean = false,
    val fontScale: Float = 1.0f,
    val appLanguage: String = "ru",
    // Focus Pomodoro Timer
    val focusTimerSeconds: Int = 25 * 60,
    val focusTimerTotalSeconds: Int = 25 * 60,
    val isFocusTimerRunning: Boolean = false,
    val focusSessionCount: Int = 0,
    // Obsidian Vault Integration
    val isObsidianConfigured: Boolean = false,
    val obsidianVaultName: String = "Не подключено",
    val obsidianAutoExport: Boolean = false,
    val isObsidianSyncSheetOpen: Boolean = false
)

/** Сообщение + действие для Snackbar (например, отмена отметки). */
data class SnackbarState(
    val message: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)
