package com.voicehabit.tracker.presentation.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicehabit.tracker.core.audio.AudioRecorderManager
import com.voicehabit.tracker.core.audio.SpeechRecognizerHelper
import com.voicehabit.tracker.core.coroutines.AppDispatchers
import com.voicehabit.tracker.core.logging.AppLogger
import com.voicehabit.tracker.core.logging.LogLevel
import com.voicehabit.tracker.core.di.AppContainer
import com.voicehabit.tracker.data.local.SettingsManager
import com.voicehabit.tracker.data.remote.OfflineVoiceParser
import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.IntentMode
import com.voicehabit.tracker.domain.model.Priority
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.domain.model.TaskType
import com.voicehabit.tracker.domain.model.VoiceNoteAction
import com.voicehabit.tracker.domain.model.VoiceProcessingMode
import com.voicehabit.tracker.widget.DuroHabitWidgetProvider
import com.voicehabit.tracker.worker.VoiceUploadWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

/**
 * `@JvmOverloads` обязателен: `AndroidViewModelFactory` создаёт ViewModel
 * рефлексией по конструктору ровно с одним параметром `Application`.
 * Без него приложение падало на старте с NoSuchMethodException.
 */
class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val container: AppContainer = AppContainer.get(application),
    private val dispatchers: AppDispatchers = container.dispatchers
) : AndroidViewModel(application) {

    private val db = container.database
    private val habitRepository = container.habitRepository
    private val taskRepository = container.taskRepository
    private val settings = container.settings
    private val processVoiceUseCase = container.processVoiceUseCase
    private val applyVoiceActionsUseCase = container.applyVoiceActionsUseCase
    val obsidianVaultManager = container.obsidianVaultManager

    private val logger = AppLogger.instance()
    val audioRecorder: AudioRecorderManager = container.audioRecorder
    val audioPlayer: com.voicehabit.tracker.core.audio.AudioPlayerManager = com.voicehabit.tracker.core.audio.AudioPlayerManager()
    private val speechRecognizer: SpeechRecognizerHelper = container.speechRecognizer
    private val extras = container.extrasRepository
    /** H1: конспекты свободного потока. */
    private val digestRepository = container.digestRepository
    private val tts = container.tts
    private var focusTicker: kotlinx.coroutines.Job? = null

    @Volatile
    private var liveTranscript: String? = null

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /**
     * День, за который посчитаны секции. Тикает только через [onDayChanged]:
     * подмешан в combine коллекторов, чтобы midnight-reset происходил даже
     * без единой записи в БД.
     */
    private val dayTick = MutableStateFlow(LocalDate.now().toEpochDay())

    private val toggleGate = TaskToggleGate()

    init {
        updateDateAndYearProgress()
        observeData()
        seedInitialDataIfNeeded()
        _state.update {
            it.copy(
                hasApiKeysConfigured = settings.hasDirectKeys,
                showOnboarding = !settings.onboardingDone,
                amoledTheme = settings.amoledTheme,
                fontScale = settings.fontScale,
                appLanguage = settings.appLanguage,
                isObsidianConfigured = obsidianVaultManager.isVaultConfigured(),
                obsidianVaultName = obsidianVaultManager.getVaultDisplayName(),
                obsidianAutoExport = settings.obsidianAutoExport
            )
        }
        viewModelScope.launch(dispatchers.io) {
            runCatching {
                // Фоновая рутина старта: напоминания, бэкап, итог недели, чистка аудио.
                com.voicehabit.tracker.worker.ReminderRescheduler.rescheduleAll(getApplication(), container)
                com.voicehabit.tracker.worker.BackupWorker.scheduleWeekly(getApplication())
                com.voicehabit.tracker.worker.WeeklyReviewWorker.enqueueCheck(getApplication())
                com.voicehabit.tracker.worker.UpdateCheckWorker.scheduleDaily(getApplication())
                cleanupOldAudio()
                maybeAutoCheckUpdates()
            }
        }
    }

    private suspend fun cleanupOldAudio() {
        // G37: записи старше 30 дней из кэша удаляются, иначе cacheDir растёт бесконечно.
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        getApplication<Application>().cacheDir.listFiles { f ->
            f.isFile && f.name.startsWith("voice_temp_") && f.lastModified() < cutoff
        }?.forEach { runCatching { it.delete() } }
    }

    private fun maybeAutoCheckUpdates() {
        // G40: тихая проверка GitHub-релизов при старте (не чаще раза в сутки
        // внутри контроллера). Репозиторий по умолчанию — DmKOwO/voice-habit-tracker,
        // поэтому работает из коробки. Найденный релиз сразу загружается в фоне,
        // установка — одним тапом через системный инсталлер (тихой установки
        // без ведома пользователя на Android нет).
        if (!settings.autoUpdateCheck) return
        com.voicehabit.tracker.core.notifications.Notify.ensureChannels(getApplication())
        viewModelScope.launch(dispatchers.io) {
            container.githubUpdater.check(auto = true)
        }
        viewModelScope.launch {
            var announcedTag: String? = null
            container.githubUpdater.state.collect { updateState ->
                when (updateState) {
                    is com.voicehabit.tracker.core.update.AppUpdateUiState.Available -> {
                        if (announcedTag != updateState.tag) {
                            announcedTag = updateState.tag
                            showSnackbar("Доступно обновление ${updateState.version} — загружается…")
                        }
                    }
                    is com.voicehabit.tracker.core.update.AppUpdateUiState.Downloaded -> {
                        showSnackbar("Обновление готово к установке")
                        runCatching {
                            com.voicehabit.tracker.core.notifications.Notify.show(
                                context = getApplication(),
                                channel = com.voicehabit.tracker.core.notifications.Notify.CHANNEL_UPDATES,
                                id = 9201,
                                title = "⬇ Обновление готово к установке",
                                text = "Нажмите для установки новой версии.",
                                actions = listOf(com.voicehabit.tracker.core.notifications.Notify.updateInstallAction(getApplication())),
                                customContentIntent = com.voicehabit.tracker.core.notifications.Notify.updateInstallIntent(getApplication())
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun updateDateAndYearProgress() {
        val cal = Calendar.getInstance()
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val totalDays = if (cal.getActualMaximum(Calendar.DAY_OF_YEAR) > 365) 366 else 365
        val yearPct = (dayOfYear * 100) / totalDays

        val dateFormat = SimpleDateFormat("MMM d", Locale.US)
        val dateString = dateFormat.format(cal.time).uppercase()

        _state.update {
            it.copy(
                yearProgressPercentage = yearPct,
                dateDisplayString = dateString
            )
        }
    }

    private fun observeData() {
        observeVosk()
        viewModelScope.launch(dispatchers.io) {
            habitRepository.getAllHabitsFlow().collect { allHabits ->
                _state.update { current ->
                    val updatedDetailHabit = current.selectedHabitForDetail?.let { selected ->
                        allHabits.find { it.id == selected.id }
                    }
                    // На главной показываем только активные: архив и корзина живут
                    // в Архиве. Раньше сюда попадали все, поэтому "удаление"
                    // (moveToTrash) визуально ничего не меняло.
                    val activeHabits = allHabits.filter { it.deletedAt == null && !it.archived }
                    current.copy(
                        // G9: закреплённые всегда вверху, затем по созданию.
                        habits = activeHabits.sortedWith(
                            compareByDescending<Habit> { it.pinned }.thenBy { it.createdAt }
                        ),
                        selectedHabitForDetail = updatedDetailHabit
                    )
                }
            }
        }

        // Потоки второго контура: рутины, челленджи, достижения, фокус, разборы.
        viewModelScope.launch(dispatchers.io) {
            extras.routinesFlow().collect { routines ->
                _state.update { it.copy(routines = routines) }
            }
        }
        viewModelScope.launch(dispatchers.io) {
            extras.challengesFlow().collect { challenges ->
                _state.update { it.copy(challenges = challenges) }
            }
        }
        viewModelScope.launch(dispatchers.io) {
            extras.achievementsFlow().collect { unlocked ->
                _state.update { it.copy(unlockedAchievements = unlocked) }
            }
        }
        viewModelScope.launch(dispatchers.io) {
            extras.focusRecentFlow().collect { sessions ->
                _state.update { it.copy(focusSessions = sessions) }
            }
        }
        viewModelScope.launch(dispatchers.io) {
            extras.reviewFlow().collect { reviews ->
                _state.update { it.copy(reviews = reviews) }
            }
        }
        // H1: конспекты пишутся при разборе голоса, поэтому список должен обновиться
        // сам, а не после ручного захода на экран.
        viewModelScope.launch(dispatchers.io) {
            digestRepository.allFlow().collect { digests ->
                _state.update { it.copy(digests = digests) }
            }
        }

        viewModelScope.launch(dispatchers.io) {
            // Раньше здесь был getOpenTasksFlow(): выполненная задача пропадала с экрана,
            // поэтому цвет «задача выполнена» не мог появиться. Смотрим все задачи и режем
            // их на секции, а LONG-задачи продолжают жить в списке как привычки.
            //
            // dayTick подмешан в combine специально: Room Flow переизлучается только
            // при записи в БД, и без этого приложение, открытое через полночь,
            // показывало бы вчерашние секции до первого тапа.
            combine(
                taskRepository.getAllTasksFlow(),
                dayTick
            ) { allTasks, todayEpochDay ->
                val today = LocalDate.ofEpochDay(todayEpochDay)
                val sections = TaskSectioning.split(allTasks, today)
                Triple(allTasks, today, sections)
            }.collect { (allTasks, _, sections) ->
                _state.update { current ->
                    val refreshedSelection = current.selectedTaskForEdit?.let { selected ->
                        allTasks.find { it.id == selected.id }
                    }
                    current.copy(
                        tasks = sections.openTasks,
                        completedTasks = sections.completedToday,
                        completedEarlierCount = sections.completedEarlierCount,
                        selectedTaskForEdit = refreshedSelection
                    )
                }
            }
        }

        viewModelScope.launch(dispatchers.io) {
            db.voiceLogDao().getAllVoiceLogsFlow().collect { logs ->
                _state.update { it.copy(voiceLogs = logs) }
            }
        }

        // Прозрачность фоновых операций: журнал и активные задачи видны в интерфейсе,
        // поэтому «ничего не происходит» можно диагностировать, а не угадывать.
        viewModelScope.launch(dispatchers.default) {
            logger.ring.events.collect { events ->
                _state.update { current -> current.copy(logEvents = events) }
            }
        }
        viewModelScope.launch(dispatchers.default) {
            logger.ring.activeOperations.collect { operations ->
                _state.update { current -> current.copy(activeOperations = operations) }
            }
        }

        observeRecorderState()
    }

    /**
     * Только факт записи живёт в общем состоянии экрана: длительность и
     * амплитуда идут отдельными потоками прямо в FAB, иначе каждое обновление
     * аудио пересобирало бы весь домашний экран.
     */
    private fun observeRecorderState() {
        viewModelScope.launch(dispatchers.audio) {
            audioRecorder.isRecording.collect { isRec ->
                _state.update { it.copy(isRecording = isRec) }
            }
        }
    }

    fun setSelectedTab(tab: String) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun setGridView(isGrid: Boolean) {
        _state.update { it.copy(isGridView = isGrid) }
    }

    fun openCreateHabit() {
        _state.update { it.copy(isCreateHabitOpen = true) }
    }

    fun closeCreateHabit() {
        _state.update { it.copy(isCreateHabitOpen = false) }
    }

    fun openSettings() {
        _state.update { it.copy(isSettingsOpen = true) }
    }

    fun closeSettings() {
        _state.update { it.copy(isSettingsOpen = false, hasApiKeysConfigured = settings.hasDirectKeys) }
    }

    fun getSettingsManager(): SettingsManager = settings

    fun openHabitDetail(habit: Habit) {
        _state.update { it.copy(selectedHabitForDetail = habit) }
    }

    fun closeHabitDetail() {
        _state.update { it.copy(selectedHabitForDetail = null) }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            habitRepository.deleteHabit(habitId)
            closeHabitDetail()
            DuroHabitWidgetProvider.updateAllWidgets(getApplication())
            com.voicehabit.tracker.widget.DuroHabitCardWidgetProvider.updateAllCardWidgets(getApplication())
        }
    }

    fun addHabit(habit: Habit) {
        viewModelScope.launch {
            habitRepository.insertOrUpdateHabit(habit)
            DuroHabitWidgetProvider.updateAllWidgets(getApplication())
            com.voicehabit.tracker.widget.DuroHabitCardWidgetProvider.updateAllCardWidgets(getApplication())
            closeCreateHabit()
        }
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            taskRepository.insertTask(task)
            closeCreateHabit()
        }
    }

    fun startRecording() {
        liveTranscript = null
        val audioFile = File(getApplication<Application>().cacheDir, "voice_temp_${System.currentTimeMillis()}.m4a")
        audioRecorder.startRecording(audioFile)
        if (speechRecognizer.isAvailable()) {
            speechRecognizer.startListening(
                onPartialText = { partial ->
                    liveTranscript = partial
                    _state.update { it.copy(infoMessage = "Слышу: \"$partial\"") }
                },
                onFinalText = { text ->
                    liveTranscript = text
                },
                onError = { err ->
                    // Ошибка распознавания речи (не критично, есть оффлайн-парсер и аудиофайл)
                }
            )
        }
    }

    fun stopRecording() {
        speechRecognizer.stopListening()
        // Длительность снимаем ДО stopRecording(): он обнуляет счётчик, и конспект
        // лишился бы единственной цифры, объясняющей, сколько это было минут речи.
        val speechSeconds = audioRecorder.recordDurationSeconds.value
        val file = audioRecorder.stopRecording() ?: run {
            _state.update {
                it.copy(
                    errorMessage = "Запись пустая: микрофон не отдал данных. Попробуйте ещё раз."
                )
            }
            return
        }

        var capturedTranscript = liveTranscript
        _state.update { it.copy(isLoading = true, infoMessage = "Обрабатываем запись...") }

        viewModelScope.launch {
            // F1: нет транскрипта с микрофона, но Vosk-модель готова — расшифровываем файл локально.
            if (capturedTranscript.isNullOrBlank() && settings.offlineSttEnabled && container.vosk.isReady()) {
                _state.update { it.copy(infoMessage = "Офлайн-распознавание (Vosk)…") }
                val voskText = withContext(dispatchers.io) { container.vosk.transcribe(file) }
                if (!voskText.isNullOrBlank()) {
                    capturedTranscript = voskText
                    liveTranscript = voskText
                    logger.i("voice", "Vosk расшифровал запись локально")
                }
            }
            logger.startOperation(VOICE_OPERATION, "Расшифровка и разбор записи")
            val result = processVoiceUseCase(
                file,
                spokenTranscript = capturedTranscript,
                speechSeconds = speechSeconds
            )
            result.onSuccess { action ->
                logger.i(
                    "voice",
                    "Запись разобрана",
                    mapOf(
                        "mode" to action.processingMode.name,
                        "sttMs" to action.sttDurationMs.toString(),
                        "llmMs" to action.llmDurationMs.toString()
                    )
                )
                logger.finishOperation(VOICE_OPERATION, action.summary)
                enqueueRetryIfTranscriptMissing(file, action)
                _state.update {
                    it.copy(
                        isLoading = false,
                        pendingReviewAction = action,
                        infoMessage = null
                    )
                }
            }.onFailure { error ->
                // Блокирующий first() по Flow раньше выполнялся в UI-потоке и зависал
                // интерфейс до следующей эмиссии. Здесь только suspend-вызовы БД.
                val fallbackAction = withContext(dispatchers.io) {
                    com.voicehabit.tracker.data.remote.OfflineVoiceParser.parse(
                        capturedTranscript ?: "Не удалось распознать речь",
                        container.voiceRepository.currentHabitsForParsing(),
                        container.voiceRepository.currentTasksForParsing()
                    )
                }
                logger.w("voice", "Офлайн-разбор: ${fallbackAction.summary}")
                logger.finishOperation(VOICE_OPERATION, fallbackAction.summary)
                _state.update {
                    it.copy(
                        isLoading = false,
                        pendingReviewAction = fallbackAction,
                        infoMessage = null
                    )
                }
            }
        }
    }

    fun cancelRecording() {
        speechRecognizer.stopListening()
        liveTranscript = null
        audioRecorder.cancelRecording()
    }

    fun applyVoiceAction(action: VoiceNoteAction) {
        viewModelScope.launch {
            applyVoiceActionsUseCase(action)
            // H1. Конспект пишется при разборе, но режим и текст секций человек мог
            // поправить в шторке. Без этой записи в списке конспектов оставался бы
            // «Поток + дело» после того, как пользователь сам выбрал «Выжимку», —
            // то есть приложение показывало бы не то, что человек подтвердил.
            persistReviewedDigest(action)
            action.logId?.let { logId ->
                withContext(dispatchers.io) {
                    db.voiceLogDao().updateStatus(logId, VoiceUploadWorker.VOICE_STATUS_APPLIED, action.summary)
                }
            }
            _state.update { it.copy(pendingReviewAction = null) }
            DuroHabitWidgetProvider.updateAllWidgets(getApplication())
            com.voicehabit.tracker.widget.DuroHabitCardWidgetProvider.updateAllCardWidgets(getApplication())
            // G4/G5/F2: фокус, сводка и рутины из голосовой команды.
            action.focusToStart?.takeIf { it.isSelected }?.let { focus ->
                _state.update { it.copy(focusRequest = focus, screen = AppScreen.FOCUS) }
            }
            if (action.daySummaryRequested) {
                buildDaySummary(speakOut = true)
            } else {
                runRoutineByTrigger(action.rawTranscript)
            }
            evaluateAchievements()
            checkAllDoneCelebration()
        }
    }

    /**
     * Запись без транскрипта не должна теряться: если ключи настроены, файл уходит
     * в очередь и будет разобран, когда появится сеть. Раньше `enqueue()` не
     * вызывался нигде, и запись просто исчезала.
     */
    private fun enqueueRetryIfTranscriptMissing(file: File, action: VoiceNoteAction) {
        val transcriptMissing = action.processingMode == VoiceProcessingMode.OFFLINE_FALLBACK &&
            liveTranscript.isNullOrBlank() &&
            action.logId != null &&
            settings.hasDirectKeys

        if (transcriptMissing) {
            VoiceUploadWorker.enqueue(getApplication(), file.absolutePath, null, action.logId)
        }
    }

    fun openOperationsLog() {
        _state.update { it.copy(isOperationsLogOpen = true) }
    }

    fun closeOperationsLog() {
        _state.update { it.copy(isOperationsLogOpen = false) }
    }

    fun setLogLevelFilter(level: LogLevel?) {
        _state.update { it.copy(logLevelFilter = level) }
    }

    fun clearLog() {
        logger.ring.clear()
        _state.update { it.copy(logEvents = emptyList()) }
    }

    fun showSnackbar(state: SnackbarState) {
        _state.update { it.copy(snackbarMessage = state.message) }
    }

    fun showSnackbar(message: String) {
        _state.update { it.copy(snackbarMessage = message) }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private suspend fun persistReviewedDigest(action: VoiceNoteAction) {
        val digest = action.digest ?: return
        val id = action.digestId ?: return
        val existing = digestRepository.byId(id) ?: return
        val updated = existing.withContent(digest).copy(
            mode = action.mode,
            modeConfidence = action.modeConfidence,
            transcript = action.rawTranscript
        )
        digestRepository.upsert(updated)
        if (settings.obsidianAutoExport && obsidianVaultManager.isVaultConfigured()) {
            val audioPath = getAudioPath(updated.voiceLogId)
            obsidianVaultManager.exportJournalEntry(updated, audioPath)
        }
    }

    fun dismissReview() {
        _state.update { it.copy(pendingReviewAction = null) }
    }

    /**
     * H1. Текст извне (поделиться, «выделить → Duro») идёт через тот же конвейер,
     * что и голос: тот же детектор режима, тот же конспект, те же задачи.
     *
     * `speechSeconds` здесь ноль — это не ошибка, а признак «речи не было», и в
     * конспекте он честно остаётся пустым, а не заменяется выдуманной длительностью.
     */
    fun ingestExternalText(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        _state.update { it.copy(isLoading = true, infoMessage = "Разбираю текст...") }
        viewModelScope.launch {
            val result = processVoiceUseCase.processText(clean, speechSeconds = 0)
            result.onSuccess { action ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        pendingReviewAction = action,
                        infoMessage = null
                    )
                }
            }.onFailure {
                _state.update { state ->
                    state.copy(
                        isLoading = false,
                        pendingReviewAction = OfflineVoiceParser.parse(
                            clean,
                            container.voiceRepository.currentHabitsForParsing(),
                            container.voiceRepository.currentTasksForParsing()
                        ),
                        infoMessage = null
                    )
                }
            }
        }
    }

    /**
     * H1. Пользователь поправляет режим в шторке разбора.
     *
     * Отсюда идёт общий контракт: смена режима всегда **пересобирает** разбор, а не
     * просто меняет подпись. Иначе «я говорил, что это выжимка» превратилось бы в
     * тихое сохранение пяти выдуманных задач — ровно тот баг, ради которого режимы
     * и разделены.
     */
    fun overrideMode(action: VoiceNoteAction, mode: IntentMode) {
        if (action.mode == mode && action.modeIsOverridden) return
        val transcript = action.rawTranscript
        if (transcript.isBlank()) {
            _state.update {
                it.copy(
                    pendingReviewAction = action.copy(
                        mode = mode,
                        modeIsOverridden = true,
                        modeConfidence = 1f,
                        modeReason = "Режим выбран вручную"
                    )
                )
            }
            return
        }
        viewModelScope.launch(dispatchers.io) {
            val reclassified = withContext(dispatchers.default) {
                OfflineVoiceParser.parse(
                    transcript,
                    container.voiceRepository.currentHabitsForParsing(),
                    container.voiceRepository.currentTasksForParsing(),
                    action.digest?.speechSeconds ?: 0,
                    forcedMode = mode
                )
            }
            _state.update {
                it.copy(
                    pendingReviewAction = reclassified.copy(
                        logId = action.logId,
                        digestId = action.digestId,
                        mode = mode,
                        modeIsOverridden = true,
                        modeConfidence = 1f,
                        modeReason = "Режим выбран вручную вместо «${action.mode.label}»"
                    )
                )
            }
        }
    }

    /** H1. Экран конспектов. */
    fun openDigests() = openScreen(AppScreen.DIGESTS)

    fun setSelectedMainTab(tab: MainTab) {
        _state.update { it.copy(selectedMainTab = tab, screen = AppScreen.HOME) }
    }

    private var focusTimerJob: kotlinx.coroutines.Job? = null

    fun setFocusTimerDuration(durationSec: Int) {
        focusTimerJob?.cancel()
        _state.update {
            it.copy(
                focusTimerTotalSeconds = durationSec,
                focusTimerSeconds = durationSec,
                isFocusTimerRunning = false
            )
        }
    }

    fun toggleFocusTimer() {
        val isRunning = _state.value.isFocusTimerRunning
        if (isRunning) {
            focusTimerJob?.cancel()
            _state.update { it.copy(isFocusTimerRunning = false) }
        } else {
            _state.update { it.copy(isFocusTimerRunning = true) }
            focusTimerJob = viewModelScope.launch {
                while (_state.value.isFocusTimerRunning && _state.value.focusTimerSeconds > 0) {
                    kotlinx.coroutines.delay(1000)
                    _state.update {
                        val next = it.focusTimerSeconds - 1
                        if (next <= 0) {
                            it.copy(
                                focusTimerSeconds = it.focusTimerTotalSeconds,
                                isFocusTimerRunning = false,
                                focusSessionCount = it.focusSessionCount + 1
                            )
                        } else {
                            it.copy(focusTimerSeconds = next)
                        }
                    }
                }
            }
        }
    }

    fun resetFocusTimer() {
        focusTimerJob?.cancel()
        _state.update {
            it.copy(
                focusTimerSeconds = it.focusTimerTotalSeconds,
                isFocusTimerRunning = false
            )
        }
    }

    fun openProfileMenu() {
        _state.update { it.copy(isProfileMenuOpen = true) }
    }

    fun closeProfileMenu() {
        _state.update { it.copy(isProfileMenuOpen = false) }
    }

    fun toggleSearchExpanded() {
        _state.update { it.copy(isSearchExpanded = !it.isSearchExpanded) }
    }

    fun setSearchExpanded(expanded: Boolean) {
        _state.update { it.copy(isSearchExpanded = expanded) }
    }

    fun setJournalFilterMood(mood: String?) {
        _state.update { it.copy(journalFilterMood = mood) }
    }

    fun setJournalFilterTag(tag: String?) {
        _state.update { it.copy(journalFilterTag = tag) }
    }

    fun setJournalSearchQuery(query: String) {
        _state.update { it.copy(journalSearchQuery = query) }
    }

    fun openJournalDetail(record: DigestRecord) {
        _state.update { it.copy(selectedJournalForDetail = record) }
    }

    fun closeJournalDetail() {
        _state.update { it.copy(selectedJournalForDetail = null) }
    }

    suspend fun getAudioPath(voiceLogId: String?): String? {
        if (voiceLogId == null) return null
        return withContext(dispatchers.io) {
            db.voiceLogDao().getVoiceLogById(voiceLogId)?.audioPath
        }
    }

    fun playAudioForRecord(record: DigestRecord) {
        viewModelScope.launch {
            val path = getAudioPath(record.voiceLogId)
            if (path != null) {
                audioPlayer.toggle(path)
            } else {
                showSnackbar("Аудиозапись не найдена на устройстве")
            }
        }
    }

    fun toggleDigestPinned(id: String) {
        viewModelScope.launch(dispatchers.io) {
            val digest = digestRepository.byId(id) ?: return@launch
            digestRepository.setPinned(id, !digest.pinned)
        }
    }

    fun deleteDigest(id: String) {
        viewModelScope.launch(dispatchers.io) {
            digestRepository.delete(id)
            showSnackbar("Конспект удалён")
        }
    }

    /**
     * H1. Превращает пункт «что дальше» из конспекта в задачу.
     *
     * Это мост между двумя половинами приложения: конспект — то, что человек сказал,
     * задача — то, что он решил сделать. Действие руками означало бы, что половина
     * конспектов остаётся декоративной.
     */
    fun addNextStepAsTask(digest: DigestRecord, step: String) {
        val title = com.voicehabit.tracker.core.analysis.ActionTitle.from(step)
        if (title.isBlank()) return
        viewModelScope.launch(dispatchers.io) {
            taskRepository.insertTask(
                Task(
                    id = "task_" + java.util.UUID.randomUUID().toString().take(8),
                    title = title,
                    dueDateIso = null,
                    priority = Priority.MEDIUM,
                    category = "Из конспекта",
                    type = TaskType.QUICK
                )
            )
            showSnackbar("Задача создана из конспекта")
        }
    }

    fun openObsidianSyncSheet() {
        _state.update {
            it.copy(
                isObsidianSyncSheetOpen = true,
                isObsidianConfigured = obsidianVaultManager.isVaultConfigured(),
                obsidianVaultName = obsidianVaultManager.getVaultDisplayName(),
                obsidianAutoExport = settings.obsidianAutoExport
            )
        }
    }

    fun closeObsidianSyncSheet() {
        _state.update { it.copy(isObsidianSyncSheetOpen = false) }
    }

    fun setObsidianVaultUri(uri: android.net.Uri) {
        val configured = obsidianVaultManager.saveVaultUri(uri)
        val name = obsidianVaultManager.getVaultDisplayName()
        _state.update {
            it.copy(
                isObsidianConfigured = configured,
                obsidianVaultName = name
            )
        }
        if (configured) {
            showSnackbar("Obsidian Vault подключен: $name")
        } else {
            showSnackbar("Не удалось получить доступ к выбранной папке")
        }
    }

    fun clearObsidianVault() {
        obsidianVaultManager.clearVaultUri()
        _state.update {
            it.copy(
                isObsidianConfigured = false,
                obsidianVaultName = "Не подключено"
            )
        }
        showSnackbar("Obsidian Vault отключен")
    }

    fun setObsidianAutoExport(enabled: Boolean) {
        settings.obsidianAutoExport = enabled
        _state.update { it.copy(obsidianAutoExport = enabled) }
        showSnackbar(if (enabled) "Авто-экспорт в Obsidian включен" else "Авто-экспорт в Obsidian выключен")
    }

    fun exportRecordToObsidian(record: DigestRecord, onDone: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(dispatchers.io) {
            if (!obsidianVaultManager.isVaultConfigured()) {
                withContext(dispatchers.main) {
                    showSnackbar("Obsidian Vault не настроен. Подключите его в меню профиля.")
                    onDone?.invoke(false, "Vault не настроен")
                }
                return@launch
            }
            val audioPath = getAudioPath(record.voiceLogId)
            val result = obsidianVaultManager.exportJournalEntry(record, audioPath)
            withContext(dispatchers.main) {
                if (result.isSuccess) {
                    val exported = result.getOrThrow()
                    showSnackbar("Сохранено в Obsidian: ${exported.relativePath}")
                    onDone?.invoke(true, exported.relativePath)
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Ошибка записи"
                    showSnackbar("Ошибка экспорта: $err")
                    onDone?.invoke(false, err)
                }
            }
        }
    }

    fun exportAllToObsidian(onDone: ((Int, Int) -> Unit)? = null) {
        viewModelScope.launch(dispatchers.io) {
            if (!obsidianVaultManager.isVaultConfigured()) {
                withContext(dispatchers.main) {
                    showSnackbar("Obsidian Vault не настроен. Подключите его в меню профиля.")
                }
                return@launch
            }
            val currentRecords = _state.value.digests
            val currentHabits = habitRepository.getAllHabitsList()
            val currentTasks = taskRepository.getAllTasksList()

            val batchResult = obsidianVaultManager.exportAll(
                records = currentRecords,
                habits = currentHabits,
                tasks = currentTasks,
                audioLocalPathResolver = { voiceLogId -> getAudioPath(voiceLogId) }
            )

            withContext(dispatchers.main) {
                showSnackbar("Экспорт в ${batchResult.vaultName} завершён: ${batchResult.successCount} заметок сохранено")
                onDone?.invoke(batchResult.successCount, batchResult.failureCount)
            }
        }
    }

    fun toggleHabit(habit: Habit) {
        viewModelScope.launch {
            habitRepository.toggleHabitCompletion(habit.id)
            DuroHabitWidgetProvider.updateAllWidgets(getApplication())
            com.voicehabit.tracker.widget.DuroHabitCardWidgetProvider.updateAllCardWidgets(getApplication())
            evaluateAchievements()
            checkAllDoneCelebration()
        }
    }

    /** G31: всё закрыто — показываем пульс-празднование один раз. */
    private fun checkAllDoneCelebration() {
        val s = _state.value
        val habitsOpen = s.habits.any { h -> !h.isCompletedToday && h.deletedAt == null && !h.archived }
        val tasksOpen = s.tasks.any { t -> !t.isCompleted }
        if (!habitsOpen && !tasksOpen && (s.habits.isNotEmpty() || s.tasks.isNotEmpty() || s.completedTasks.isNotEmpty())) {
            _state.update { it.copy(celebrateAllDone = true) }
            speak("Всё выполнено! Так держать!")
        }
    }

    fun consumeCelebration() {
        _state.update { it.copy(celebrateAllDone = false) }
    }

    fun completeTask(task: Task) {
        // Дебаунс ПЕРЕД оптимистичным апдейтом: отброшенный дребезг не должен
        // даже трогать стейт, иначе UI «мигнёт» и вернётся обратно.
        if (!toggleGate.shouldProceed(task.id)) return

        // Оптимистичный UI-апдейт: цвет/состояние карточки меняются мгновенно,
        // не дожидаясь кругооборота записи в БД. Иммутабельная копия гарантирует
        // перерисовку именно этой карточки (списки keyed по id).
        // Секции поправятся сами при следующем излучении Flow из репозитория.
        _state.update { current ->
            current.copy(
                tasks = current.tasks.flipCompletion(task.id),
                completedTasks = current.completedTasks.flipCompletion(task.id),
                selectedTaskForEdit = current.selectedTaskForEdit
                    ?.takeIf { it.id == task.id }
                    ?.copy(isCompleted = !task.isCompleted)
            )
        }

        viewModelScope.launch {
            toggleGate.serialized(task.id) {
                if (task.isCompleted) {
                    taskRepository.reopenTask(task.id)
                } else {
                    taskRepository.completeTask(task.id)
                    spawnRecurrence(task)
                }
            }
            evaluateAchievements()
            checkAllDoneCelebration()
        }
    }

    /** F4: повторяющаяся задача при ручном закрытии тоже порождает следующий экземпляр. */
    private suspend fun spawnRecurrence(task: Task) {
        if (task.recurrence == com.voicehabit.tracker.domain.model.TaskRecurrence.NONE) return
        val days = if (task.recurrence == com.voicehabit.tracker.domain.model.TaskRecurrence.DAILY) 1L else 7L
        val nextIso = try {
            val base = task.dueDateIso?.take(19)?.let {
                java.time.LocalDateTime.parse(it)
            } ?: java.time.LocalDate.now().atTime(9, 0)
            base.plusDays(days).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (e: Exception) {
            null
        }
        taskRepository.insertTask(
            task.copy(
                id = "task_" + java.util.UUID.randomUUID().toString(),
                isCompleted = false,
                completedAt = null,
                dueDateIso = nextIso,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun openTaskEditor(task: Task) {
        _state.update { it.copy(selectedTaskForEdit = task) }
    }

    fun closeTaskEditor() {
        _state.update { it.copy(selectedTaskForEdit = null) }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            taskRepository.updateTask(task)
            // F9: напоминание перепланируется при каждом сохранении.
            com.voicehabit.tracker.worker.ReminderRescheduler.scheduleTaskReminder(
                getApplication(), task.id, task.title, task.dueDateIso, task.reminderMinutesBefore
            )
            _state.update { it.copy(selectedTaskForEdit = null) }
        }
    }

    fun addTaskWithReminder(task: Task) {
        viewModelScope.launch {
            taskRepository.insertTask(task)
            com.voicehabit.tracker.worker.ReminderRescheduler.scheduleTaskReminder(
                getApplication(), task.id, task.title, task.dueDateIso, task.reminderMinutesBefore
            )
            closeCreateHabit()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskRepository.deleteTask(task.id)
            _state.update { it.copy(selectedTaskForEdit = null) }
        }
    }

    fun toggleCompletedTasksVisibility() {
        _state.update { it.copy(showCompletedTasks = !it.showCompletedTasks) }
    }

    /**
     * Midnight-reset: вызывается трекером смены суток и при возврате на экран.
     * Пересчитывает шапку даты, двигает dayTick (секции задач пересекутся сами
     * через combine) и обновляет виджеты.
     */
    fun onDayChanged() {
        val today = LocalDate.now().toEpochDay()
        if (today == _state.value.todayEpochDay && today == dayTick.value) return
        updateDateAndYearProgress()
        dayTick.value = today
        _state.update { it.copy(todayEpochDay = today) }
        viewModelScope.launch {
            DuroHabitWidgetProvider.updateAllWidgets(getApplication())
            com.voicehabit.tracker.widget.DuroHabitCardWidgetProvider.updateAllCardWidgets(getApplication())
        }
    }

    /** Страховка на случай пропущенного бродкаста: проверка при каждом onResume. */
    fun onForegrounded() {
        if (LocalDate.now().toEpochDay() != _state.value.todayEpochDay) {
            onDayChanged()
        }
    }

    fun openVoiceQueue() {
        _state.update { it.copy(isVoiceQueueOpen = true) }
    }

    private fun List<Task>.flipCompletion(taskId: String): List<Task> =
        map { task ->
            if (task.id == taskId) task.copy(isCompleted = !task.isCompleted) else task
        }

    fun closeVoiceQueue() {
        _state.update { it.copy(isVoiceQueueOpen = false) }
    }

    fun reapplyVoiceLog(log: com.voicehabit.tracker.data.local.entity.VoiceLogEntity) {
        viewModelScope.launch {
            val result = processVoiceUseCase.processText(log.rawTranscript)
            result.onSuccess { action ->
                applyVoiceActionsUseCase(action)
                DuroHabitWidgetProvider.updateAllWidgets(getApplication())
                com.voicehabit.tracker.widget.DuroHabitCardWidgetProvider.updateAllCardWidgets(getApplication())
                _state.update {
                    it.copy(
                        infoMessage = "Запись от ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(log.createdAt))} повторно применена! 🔥"
                    )
                }
            }
        }
    }

    private fun seedInitialDataIfNeeded() {
        // Тестовые привычки убраны: изначально список пустой.
        // Заодно подчищаем демо-набор со старых установок (только фиксированные id).
        viewModelScope.launch(dispatchers.io) {
            runCatching {
                val demoHabitIds = listOf(
                    "habit_make_bed", "habit_workout", "habit_posting", "habit_vitamin"
                )
                var removed = 0
                for (id in demoHabitIds) {
                    if (habitRepository.getHabitById(id) != null) {
                        habitRepository.deleteHabit(id)
                        removed++
                    }
                }
                val demoTaskIds = listOf("task_sample_1", "task_sample_2")
                for (id in demoTaskIds) {
                    if (taskRepository.getById(id) != null) {
                        taskRepository.deleteTask(id)
                        removed++
                    }
                }
                if (removed > 0) {
                    logger.d("db", "Демо-данные удалены", mapOf("removed" to removed.toString()))
                    DuroHabitWidgetProvider.updateAllWidgets(getApplication())
                    com.voicehabit.tracker.widget.DuroHabitCardWidgetProvider.updateAllCardWidgets(getApplication())
                } else {
                    logger.d("db", "Сид отключён: старт с пустым списком")
                }
            }
        }
    }

    // ---------- Навигация хаба, поиск, сортировка, фильтры ----------

    fun openScreen(screen: AppScreen) {
        _state.update { it.copy(screen = screen) }
        when (screen) {
            AppScreen.ARCHIVE -> refreshArchive()
            AppScreen.STATS -> refreshStats()
            AppScreen.ACHIEVEMENTS -> Unit // achievementsFlow уже наблюдается
            else -> Unit
        }
    }

    fun closeScreen() {
        _state.update {
            it.copy(
                screen = AppScreen.HOME, daySummary = null, focusRequest = null,
                importPreview = null, celebrateAllDone = false
            )
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setTaskSort(sort: TaskSort) {
        _state.update { it.copy(taskSort = sort) }
    }

    fun setCategoryFilter(category: String?) {
        _state.update { it.copy(categoryFilter = category) }
    }

    /** F20: быстрый ввод текстом через тот же офлайн-парсер, дальше — обычное ревью. */
    fun quickAdd(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(dispatchers.io) {
            val action = com.voicehabit.tracker.data.remote.OfflineVoiceParser.parse(
                clean,
                container.voiceRepository.currentHabitsForParsing(),
                container.voiceRepository.currentTasksForParsing()
            )
            _state.update { it.copy(pendingReviewAction = action) }
        }
    }

    fun allCategories(): List<String> {
        val used = (_state.value.habits.map { it.category } + _state.value.tasks.map { it.category })
            .filter { it.isNotBlank() }.distinct()
        return (listOf("Morning", "Fitness", "Work", "Health", "Study", "General") + used + settings.customCategories)
            .distinct()
    }

    fun addCustomCategory(name: String) {
        val clean = name.trim().take(24)
        if (clean.isEmpty() || clean in allCategories()) return
        settings.customCategoriesCsv = (settings.customCategories + clean).joinToString(",")
        showSnackbar("Категория «$clean» добавлена")
    }

    // ---------- Архив, корзина, закреп, клонирование (F7/G9/G16/G17) ----------

    fun refreshArchive() {
        viewModelScope.launch(dispatchers.io) {
            val habits = habitRepository.getAllHabitsList()
            val tasks = taskRepository.getAllTasksList()
            _state.update {
                it.copy(
                    archivedHabits = habits.filter { h -> h.archived && h.deletedAt == null },
                    trashedHabits = habits.filter { h -> h.deletedAt != null },
                    trashedTasks = tasks.filter { t -> t.deletedAt != null }
                )
            }
        }
    }

    fun archiveHabit(id: String, archived: Boolean) {
        viewModelScope.launch { habitRepository.setArchived(id, archived); refreshArchive() }
    }

    fun archiveTask(id: String, archived: Boolean) {
        viewModelScope.launch { taskRepository.setArchived(id, archived); refreshArchive() }
    }

    fun trashHabit(id: String) {
        viewModelScope.launch {
            habitRepository.moveToTrash(id)
            closeHabitDetail()
            showSnackbar("Привычка в корзине (Архив → Корзина → восстановить)")
            refreshArchive()
        }
    }

    fun trashTask(id: String) {
        viewModelScope.launch {
            taskRepository.moveToTrash(id)
            _state.update { it.copy(selectedTaskForEdit = null) }
            showSnackbar("Задача в корзине (Архив → Корзина → восстановить)")
            refreshArchive()
        }
    }

    fun restoreHabit(id: String) {
        viewModelScope.launch { habitRepository.restoreFromTrash(id); refreshArchive() }
    }

    fun restoreTask(id: String) {
        viewModelScope.launch { taskRepository.restoreFromTrash(id); refreshArchive() }
    }

    fun deleteHabitForever(id: String) {
        viewModelScope.launch { habitRepository.deleteHabit(id); refreshArchive() }
    }

    fun deleteTaskForever(id: String) {
        viewModelScope.launch { taskRepository.deleteTask(id); refreshArchive() }
    }

    fun purgeTrash() {
        viewModelScope.launch(dispatchers.io) {
            val h = habitRepository.purgeTrash(30L * 24 * 60 * 60 * 1000)
            val t = taskRepository.purgeTrash(30L * 24 * 60 * 60 * 1000)
            refreshArchive()
            showSnackbar("Корзина очищена: привычек $h, задач $t")
        }
    }

    fun togglePinTask(task: Task) {
        viewModelScope.launch { taskRepository.setPinned(task.id, !task.pinned) }
    }

    fun togglePinHabit(habit: Habit) {
        viewModelScope.launch { habitRepository.setPinned(habit.id, !habit.pinned) }
    }

    fun cloneHabit(habit: Habit) {
        viewModelScope.launch {
            habitRepository.insertOrUpdateHabit(
                habit.copy(
                    id = "habit_" + java.util.UUID.randomUUID().toString(),
                    title = (habit.title + " (копия)").take(60),
                    currentStreak = 0
                )
            )
            showSnackbar("Привычка продублирована")
        }
    }

    fun cloneTask(task: Task) {
        viewModelScope.launch {
            taskRepository.insertTask(
                task.copy(
                    id = "task_" + java.util.UUID.randomUUID().toString(),
                    title = (task.title + " (копия)").take(60),
                    isCompleted = false,
                    completedAt = null
                )
            )
            showSnackbar("Задача продублирована")
        }
    }

    // Удаление через корзину вместо безвозвратного (G16: восстановить можно в Архиве).
    fun deleteTaskToTrash(task: Task) = trashTask(task.id)
    fun deleteHabitToTrash(habitId: String) = trashHabit(habitId)

    // ---------- Расписание, напоминания, freeze, ремонт (F4/F6/G19/G20) ----------

    fun setHabitSchedule(id: String, days: Set<Int>) {
        viewModelScope.launch {
            val habit = habitRepository.getHabitById(id) ?: return@launch
            habitRepository.updateScheduleAndTags(id, days, habit.tags)
        }
    }

    fun setHabitTags(id: String, tags: List<String>) {
        viewModelScope.launch {
            val habit = habitRepository.getHabitById(id) ?: return@launch
            habitRepository.updateScheduleAndTags(id, habit.scheduleDays, tags)
        }
    }

    fun setHabitReminder(id: String, minutesOfDay: Int?) {
        viewModelScope.launch {
            habitRepository.setReminderMin(id, minutesOfDay)
            val habit = habitRepository.getHabitById(id)
            if (minutesOfDay != null && habit != null) {
                val delay = com.voicehabit.tracker.worker.ReminderRescheduler.run {
                    minutesUntilTodayAtPublic(minutesOfDay)
                }
                com.voicehabit.tracker.worker.ReminderWorker.enqueue(
                    getApplication(), null, id, habit.title, delay
                )
                showSnackbar("Напоминание каждый день в ${"%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)}")
            }
        }
    }

    fun freezeToday() {
        viewModelScope.launch(dispatchers.io) {
            when (extras.freezeDay(LocalDate.now())) {
                com.voicehabit.tracker.data.repository.FreezeResult.OK ->
                    showSnackbar("❄ День заморожен: стрик не прервётся")
                com.voicehabit.tracker.data.repository.FreezeResult.LIMIT ->
                    showSnackbar("Лимит заморозок — 2 в неделю")
            }
        }
    }

    fun repairYesterday() {
        // G20: вчерашний пропуск закрываем freeze-баллом (историю не выдумываем —
        // день помечается нейтральным и не разрывает стрик).
        viewModelScope.launch(dispatchers.io) {
            when (extras.freezeDay(LocalDate.now().minusDays(1))) {
                com.voicehabit.tracker.data.repository.FreezeResult.OK -> {
                    showSnackbar("❄ Вчерашний день восстановлен заморозкой")
                    recomputeAllStreaks()
                }
                com.voicehabit.tracker.data.repository.FreezeResult.LIMIT ->
                    showSnackbar("Лимит заморозок — 2 в неделю")
            }
        }
    }

    private suspend fun recomputeAllStreaks() {
        // Стрики пересчитываются лениво при следующей отметке; форс — обновлением best.
        val habits = habitRepository.getAllHabitsList()
        for (habit in habits) {
            habitRepository.updateBestStreak(habit.id, habit.currentStreak)
        }
    }

    // ---------- Подзадачи (F5) ----------

    fun loadSubtasks(taskId: String) {
        viewModelScope.launch(dispatchers.io) {
            val list = extras.subtasksFor(taskId)
            _state.update { it.copy(subtasks = it.subtasks + (taskId to list)) }
        }
    }

    fun addSubtask(taskId: String, title: String) {
        val clean = title.trim().take(120)
        if (clean.isEmpty()) return
        viewModelScope.launch(dispatchers.io) {
            val existing = extras.subtasksFor(taskId)
            extras.upsertSubtask(
                taskId,
                com.voicehabit.tracker.domain.model.Subtask(
                    id = extras.newSubtaskId(), taskId = taskId, title = clean,
                    position = existing.size
                )
            )
            loadSubtasks(taskId)
        }
    }

    fun toggleSubtask(taskId: String, subtask: com.voicehabit.tracker.domain.model.Subtask) {
        viewModelScope.launch(dispatchers.io) {
            extras.setSubtaskDone(subtask.id, !subtask.isDone)
            val updated = extras.subtasksFor(taskId)
            _state.update { it.copy(subtasks = it.subtasks + (taskId to updated)) }
            // Авто-выполнение задачи при 100% подзадач.
            if (updated.isNotEmpty() && updated.all { s -> s.isDone }) {
                val task = taskRepository.getById(taskId)
                if (task != null && !task.isCompleted) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) { completeTask(task) }
                }
            }
        }
    }

    fun deleteSubtask(taskId: String, subtaskId: String) {
        viewModelScope.launch(dispatchers.io) {
            extras.deleteSubtask(subtaskId)
            loadSubtasks(taskId)
        }
    }

    fun subtaskProgress(taskId: String): Double? {
        val list = _state.value.subtasks[taskId] ?: return null
        if (list.isEmpty()) return null
        return list.count { it.isDone }.toDouble() / list.size
    }

    // ---------- Рутины (F2) ----------

    fun saveRoutine(routine: com.voicehabit.tracker.domain.model.Routine) {
        viewModelScope.launch(dispatchers.io) {
            extras.upsertRoutine(routine)
            _state.update { it.copy(editingRoutine = null) }
        }
    }

    fun deleteRoutine(id: String) {
        viewModelScope.launch(dispatchers.io) { extras.deleteRoutine(id) }
    }

    fun openRoutineEditor(routine: com.voicehabit.tracker.domain.model.Routine?) {
        _state.update { it.copy(editingRoutine = routine) }
    }

    fun closeRoutineEditor() {
        _state.update { it.copy(editingRoutine = null) }
    }

    fun runRoutine(id: String) {
        viewModelScope.launch {
            val routine = extras.allRoutines().find { it.id == id } ?: return@launch
            var marked = 0
            for (habitId in routine.habitIds) {
                val habit = habitRepository.getHabitById(habitId) ?: continue
                if (!habit.isCompletedToday) {
                    habitRepository.logHabitCompletion(habitId, habit.targetValue, "Рутина «${routine.title}»")
                    marked++
                }
            }
            showSnackbar("⚡ Рутина «${routine.title}»: отмечено $marked")
            evaluateAchievements(routineExecuted = true)
            refreshWidgets()
        }
    }

    /** Возвращает true, если фраза совпала с триггером рутины и та выполнена. */
    private suspend fun runRoutineByTrigger(transcript: String): Boolean {
        val lower = transcript.lowercase()
        val routine = extras.allRoutines().firstOrNull { r ->
            r.triggerPhrase.isNotBlank() && lower.contains(r.triggerPhrase.lowercase())
        } ?: return false
        var marked = 0
        for (habitId in routine.habitIds) {
            val habit = habitRepository.getHabitById(habitId) ?: continue
            if (!habit.isCompletedToday) {
                habitRepository.logHabitCompletion(habitId, habit.targetValue, "Рутина «${routine.title}»")
                marked++
            }
        }
        showSnackbar("⚡ Рутина «${routine.title}»: отмечено $marked")
        evaluateAchievements(routineExecuted = true)
        return true
    }

    // ---------- Челленджи (F17) ----------

    fun saveChallenge(challenge: com.voicehabit.tracker.domain.model.Challenge) {
        viewModelScope.launch(dispatchers.io) {
            extras.upsertChallenge(challenge)
            _state.update { it.copy(editingChallenge = null) }
        }
    }

    fun deleteChallenge(id: String) {
        viewModelScope.launch(dispatchers.io) { extras.deleteChallenge(id) }
    }

    fun openChallengeEditor(challenge: com.voicehabit.tracker.domain.model.Challenge?) {
        _state.update { it.copy(editingChallenge = challenge) }
    }

    fun closeChallengeEditor() {
        _state.update { it.copy(editingChallenge = null) }
    }

    suspend fun challengeProgressDays(challenge: com.voicehabit.tracker.domain.model.Challenge): Pair<Int, Int> {
        val logDays = if (challenge.habitId != null) {
            withContext(dispatchers.io) {
                db.habitDao().getLogsForHabit(challenge.habitId)
                    .map { java.time.Instant.ofEpochMilli(it.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() }
                    .toSet()
            }
        } else emptySet()
        return extras.challengeProgress(challenge, logDays)
    }

    // ---------- Достижения (F16) ----------

    suspend fun evaluateAchievements(routineExecuted: Boolean = false, challengeDone: Boolean = false) {
        val state = _state.value
        val habits = habitRepository.getAllHabitsList()
        val tasks = taskRepository.getAllTasksList()
        val focus = withContext(dispatchers.io) { db.focusDao().since(0L) }
        val now = java.time.LocalTime.now()
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val weekLogs = withContext(dispatchers.io) { db.habitDao().getLogsBetween(weekAgo, System.currentTimeMillis()) }
        val weekDays = weekLogs.map {
            java.time.Instant.ofEpochMilli(it.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
        }.toSet()
        val snapshot = com.voicehabit.tracker.data.repository.AchievementSnapshot(
            habitCount = habits.size,
            voiceNotesProcessed = state.voiceLogs.count { it.status == "APPLIED" || it.status == "PROCESSED" },
            bestStreak = habits.maxOfOrNull { maxOf(it.currentStreak, it.bestStreak) } ?: 0,
            tasksCompleted = tasks.count { it.isCompleted },
            focusCompleted = focus.count { it.completed },
            earlyBirdToday = weekLogs.any {
                val t = java.time.Instant.ofEpochMilli(it.completedAt).atZone(java.time.ZoneId.systemDefault())
                t.toLocalDate() == LocalDate.now() && t.hour < 7
            },
            nightOwlToday = now.hour >= 23,
            perfectWeek = (0L..6L).all { (LocalDate.now().toEpochDay() - it) in weekDays },
            routineExecuted = routineExecuted,
            challengeDone = challengeDone
        )
        val newly = extras.evaluateAchievements(snapshot)
        for (id in newly) {
            val def = com.voicehabit.tracker.domain.model.ALL_ACHIEVEMENTS.find { it.id == id }
            showSnackbar("🏆 Достижение: ${def?.title ?: id}")
            speak("Новое достижение: ${def?.title ?: id}")
        }
    }

    // ---------- Вечерний разбор и сводка дня (F3/G5) ----------

    fun saveReview(summary: String, tomorrowPlan: String) {
        viewModelScope.launch(dispatchers.io) {
            extras.saveReview(LocalDate.now(), summary, tomorrowPlan)
            showSnackbar("Вечерний разбор сохранён")
        }
    }

    /** Строит текстовую сводку дня, показывает и (если включено) озвучивает. */
    fun buildDaySummary(speakOut: Boolean = true) {
        viewModelScope.launch(dispatchers.io) {
            val habits = habitRepository.getAllHabitsList().filter { it.deletedAt == null && !it.archived }
            val doneHabits = habits.count { it.isCompletedToday }
            val tasks = taskRepository.getAllTasksList()
            val open = tasks.filter { !it.isCompleted && it.deletedAt == null && !it.isArchived }
            val doneToday = tasks.count {
                it.isCompleted && it.completedAt != null &&
                    java.time.Instant.ofEpochMilli(it.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
            }
            val tomorrow = open.filter { it.dueDateIso?.startsWith(LocalDate.now().plusDays(1).toString()) == true }
            val text = buildString {
                append("Сегодня: привычек $doneHabits из ${habits.size}, задач закрыто $doneToday, открыто ${open.size}. ")
                if (tomorrow.isNotEmpty()) {
                    append("На завтра: ${tomorrow.take(5).joinToString("; ") { it.title }}. ")
                } else {
                    append("На завтра ничего не запланировано. ")
                }
                val best = habits.maxOfOrNull { maxOf(it.currentStreak, it.bestStreak) } ?: 0
                if (best > 0) append("Лучший стрик: $best.")
            }
            _state.update { it.copy(daySummary = text) }
            if (speakOut) speak(text)
        }
    }

    fun clearDaySummary() {
        _state.update { it.copy(daySummary = null) }
    }

    fun speak(text: String) {
        if (!settings.ttsEnabled) return
        try {
            tts.speak(text)
        } catch (e: Exception) {
        }
    }

    // ---------- Фокус-таймер (F8/G4) ----------

    /** G10: открыть фокус с подставленной задачей. */
    fun openFocusForTask(task: Task) {
        _state.update {
            it.copy(
                focusDraft = FocusDraft(
                    minutes = task.estimatedMin ?: 25,
                    label = task.title,
                    taskId = task.id
                ),
                screen = AppScreen.FOCUS
            )
        }
    }

    fun clearFocusDraft() {
        _state.update { it.copy(focusDraft = null, focusRequest = null) }
    }

    fun startFocus(minutes: Int, label: String, taskId: String? = null, habitId: String? = null) {
        focusTicker?.cancel()
        val totalSec = (minutes.coerceIn(1, 180)) * 60
        _state.update {
            it.copy(focusRun = FocusRun(totalSec, totalSec, label, taskId, habitId))
        }
        focusTicker = viewModelScope.launch {
            var remaining = totalSec
            while (remaining > 0) {
                kotlinx.coroutines.delay(1000)
                remaining--
                _state.update { s ->
                    s.copy(focusRun = s.focusRun?.copy(remainingSec = remaining))
                }
            }
            finishFocus(completed = true)
        }
    }

    fun cancelFocus() {
        focusTicker?.cancel()
        focusTicker = null
        val run = _state.value.focusRun
        _state.update { it.copy(focusRun = null) }
        if (run != null) {
            viewModelScope.launch(dispatchers.io) {
                extras.saveFocusSession(
                    com.voicehabit.tracker.domain.model.FocusSession(
                        id = extras.newFocusId(), taskId = run.taskId, habitId = run.habitId,
                        label = run.label, durationMin = run.totalSec / 60, completed = false
                    )
                )
            }
        }
    }

    private fun finishFocus(completed: Boolean) {
        val run = _state.value.focusRun ?: return
        focusTicker = null
        _state.update { it.copy(focusRun = null) }
        viewModelScope.launch(dispatchers.io) {
            extras.saveFocusSession(
                com.voicehabit.tracker.domain.model.FocusSession(
                    id = extras.newFocusId(), taskId = run.taskId, habitId = run.habitId,
                    label = run.label, durationMin = run.totalSec / 60, completed = completed
                )
            )
            showSnackbar("🧠 Фокус завершён: ${run.label} (${run.totalSec / 60} мин)")
            speak("Фокус завершён. Отличная работа!")
            evaluateAchievements()
        }
    }

    // ---------- Статистика, настроение, здоровье (F15/G23/G26/F11) ----------

    fun refreshStats() {
        viewModelScope.launch(dispatchers.io) {
            var stats = extras.computeStats()
            val healthStatus = container.health.availability().name
            var steps: Int? = null
            var sleep: Double? = null
            if (container.health.availability() ==
                com.voicehabit.tracker.core.health.HealthManager.Availability.AVAILABLE
            ) {
                steps = container.health.stepsToday()
                sleep = container.health.sleepHoursLastNight()
            }
            stats = stats.copy(
                healthAvailable = healthStatus == "AVAILABLE",
                stepsToday = steps,
                sleepHoursLastNight = sleep
            )
            val today = LocalDate.now().toEpochDay()
            val moods = db.moodDao().range(today - 364, today).associate { it.dateEpochDay to it.mood }
            _state.update {
                it.copy(
                    stats = stats,
                    moods = moods,
                    moodToday = moods[today],
                    healthSteps = steps,
                    healthSleepHours = sleep,
                    healthStatus = healthStatus
                )
            }
        }
    }

    fun setMood(mood: Int, note: String = "") {
        viewModelScope.launch(dispatchers.io) {
            db.moodDao().upsert(
                com.voicehabit.tracker.data.local.entity.MoodEntity(
                    LocalDate.now().toEpochDay(), mood.coerceIn(1, 5), note.take(200)
                )
            )
            refreshStats()
        }
    }

    fun shareStatsText(): String {
        val s = _state.value.stats ?: return "Пока нет данных."
        val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        return buildString {
            appendLine("📊 Моя неделя в Duro:")
            appendLine("Выполнений всего: ${s.totalCompletions}")
            appendLine("Лучший день: ${days.getOrElse(s.bestWeekday) { "?" }}")
            appendLine("Фокуса за неделю: ${s.focusMinutesWeek} мин")
            appendLine("Лучший стрик: ${s.currentBestStreak}")
            if (s.completionByCategory.isNotEmpty()) {
                appendLine("Категории: " + s.completionByCategory.take(3).joinToString(", ") { "${it.first} — ${it.second}" })
            }
        }
    }

    fun healthManager() = container.health

    fun refreshHealth() = refreshStats()

    // ---------- Vosk офлайн-STT (F1) ----------

    fun observeVosk() {
        viewModelScope.launch {
            container.vosk.status.collect { status ->
                _state.update { it.copy(voskStatus = status.name) }
            }
        }
        viewModelScope.launch {
            container.vosk.downloadProgress.collect { progress ->
                _state.update { it.copy(voskProgress = progress) }
            }
        }
    }

    fun downloadVoskModel() {
        viewModelScope.launch(dispatchers.io) {
            val ok = container.vosk.downloadModel()
            showSnackbar(if (ok) "Модель Vosk готова — работает без сети" else "Не скачалось: ${container.vosk.lastError()}")
        }
    }

    fun setOfflineStt(enabled: Boolean) {
        settings.offlineSttEnabled = enabled
        if (enabled && !container.vosk.isReady()) downloadVoskModel()
    }

    // ---------- Бэкап (F13/G34/G35) ----------

    fun doBackup() {
        viewModelScope.launch(dispatchers.io) {
            try {
                val file = container.backup.export()
                _state.update { it.copy(lastBackupName = file.name) }
                showSnackbar("Бэкап сохранён: ${file.name}")
            } catch (e: Exception) {
                showSnackbar("Бэкап не удался: ${e.message}")
            }
        }
    }

    fun previewImport(json: String) {
        viewModelScope.launch(dispatchers.io) {
            try {
                val preview = container.backup.previewImport(json)
                _state.update {
                    it.copy(importPreview = ImportPreviewUi(preview.tables, preview.warnings))
                }
            } catch (e: Exception) {
                showSnackbar("Файл не читается: ${e.message}")
            }
        }
    }

    fun doImport(json: String) {
        viewModelScope.launch(dispatchers.io) {
            try {
                val count = container.backup.importValidated(json)
                _state.update { it.copy(importPreview = null) }
                showSnackbar("Импортировано строк: $count")
                logger.i("backup", "Импорт завершён", mapOf("rows" to count.toString()))
            } catch (e: Exception) {
                showSnackbar("Импорт не удался: ${e.message}")
            }
        }
    }

    fun clearImportPreview() {
        _state.update { it.copy(importPreview = null) }
    }

    fun shareLastBackup(context: android.content.Context) {
        viewModelScope.launch(dispatchers.io) {
            try {
                val file = container.backup.export()
                _state.update { it.copy(lastBackupName = file.name) }
                context.startActivity(
                    android.content.Intent.createChooser(
                        container.backup.shareIntent(file), "Поделиться бэкапом"
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                showSnackbar("Не удалось поделиться: ${e.message}")
            }
        }
    }

    fun evaluateChallengeDone() {
        viewModelScope.launch { evaluateAchievements(challengeDone = true) }
    }

    // ---------- Онбординг, тема, язык (F18/F19/G39) ----------

    fun completeOnboarding() {
        settings.onboardingDone = true
        _state.update { it.copy(showOnboarding = false) }
    }

    fun reopenOnboarding() {
        _state.update { it.copy(showOnboarding = true, onboardingPage = 0) }
    }

    fun setOnboardingPage(page: Int) {
        _state.update { it.copy(onboardingPage = page) }
    }

    fun toggleAmoled() {
        settings.amoledTheme = !settings.amoledTheme
        _state.update { it.copy(amoledTheme = settings.amoledTheme) }
    }

    /** Синхронизация темы после прямого изменения настроек из диалога. */
    fun refreshThemeState() {
        _state.update {
            it.copy(
                amoledTheme = settings.amoledTheme,
                fontScale = settings.fontScale,
                appLanguage = settings.appLanguage
            )
        }
    }

    fun setFontScale(scale: Float) {
        settings.fontScale = scale
        _state.update { it.copy(fontScale = settings.fontScale) }
    }

    fun setLanguage(code: String) {
        settings.appLanguage = code
        _state.update { it.copy(appLanguage = code) }
        try {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.create(java.util.Locale(code))
            )
        } catch (e: Exception) {
            showSnackbar("Язык применится после перезапуска")
        }
    }

    fun setQuietHours(enabled: Boolean) {
        settings.quietHoursEnabled = enabled
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        settings.autoUpdateCheck = enabled
    }

    fun setTtsEnabled(enabled: Boolean) {
        settings.ttsEnabled = enabled
        if (!enabled) tts.stop()
    }

    // ---------- Хуки в существующие flow ----------

    private fun refreshWidgets() {
        DuroHabitWidgetProvider.updateAllWidgets(getApplication())
        com.voicehabit.tracker.widget.DuroHabitCardWidgetProvider.updateAllCardWidgets(getApplication())
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }

    private companion object {
        const val SEED_OPERATION = "seed"
        const val VOICE_OPERATION = "voice_processing"
    }
}
