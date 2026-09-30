package com.voicehabit.tracker.presentation.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicehabit.tracker.core.analysis.ProgrammeDraft
import com.voicehabit.tracker.core.analysis.ProgrammeHabitProjector
import com.voicehabit.tracker.core.analysis.ProgrammeParser
import com.voicehabit.tracker.core.analysis.ProgrammeVoiceResolver
import com.voicehabit.tracker.core.audio.AudioRecorderManager
import com.voicehabit.tracker.core.audio.SpeechRecognizerHelper
import com.voicehabit.tracker.core.coroutines.AppDispatchers
import com.voicehabit.tracker.core.logging.AppLogger
import com.voicehabit.tracker.core.logging.LogLevel
import com.voicehabit.tracker.core.di.AppContainer
import com.voicehabit.tracker.data.local.SettingsManager
import com.voicehabit.tracker.data.remote.DirectAiService.FocusStepType
import com.voicehabit.tracker.data.remote.OfflineVoiceParser
import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.Programme
import com.voicehabit.tracker.domain.model.IntentMode
import com.voicehabit.tracker.domain.model.Priority
import com.voicehabit.tracker.domain.model.Subtask
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import java.util.UUID

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
    val directAiService = container.directAiService

    private val logger = AppLogger.instance()
    val audioRecorder: AudioRecorderManager = container.audioRecorder
    val audioPlayer: com.voicehabit.tracker.core.audio.AudioPlayerManager = com.voicehabit.tracker.core.audio.AudioPlayerManager()
    private val speechRecognizer: SpeechRecognizerHelper = container.speechRecognizer
    private val extras = container.extrasRepository
    /** H1: конспекты свободного потока. */
    private val digestRepository = container.digestRepository
    /** P1: тренировочные программы. */
    private val programmeRepository = container.programmeRepository
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
        restorePersistedUi()
        _state.update { it.copy(ttsEnabled = settings.ttsEnabled) }
        runCatching { container.usageAnalytics.trackAppOpen() }
        _state.update {
            it.copy(
                hasApiKeysConfigured = settings.hasDirectKeys,
                showOnboarding = !settings.onboardingDone,
                themePreset = settings.themePreset,
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
                                id = com.voicehabit.tracker.core.notifications.Notify.ID_UPDATE,
                                title = "Обновление готово к установке",
                                text = "Нажмите для установки новой версии.",
                                actions = listOf(com.voicehabit.tracker.core.notifications.Notify.updateInstallAction(getApplication())),
                                customContentIntent = com.voicehabit.tracker.core.notifications.Notify.updateInstallIntent(getApplication())
                            )
                        }
                    }
                    is com.voicehabit.tracker.core.update.AppUpdateUiState.UpToDate -> {
                        runCatching {
                            com.voicehabit.tracker.core.notifications.Notify.cancel(
                                getApplication(),
                                com.voicehabit.tracker.core.notifications.Notify.ID_UPDATE
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

        // Русская локаль: приложение русскоязычное, а «SEP 25» на главном экране
        // выглядело как недоделанный перевод.
        val dateFormat = SimpleDateFormat("d MMMM", Locale("ru"))
        val dateString = dateFormat.format(cal.time).replaceFirstChar { it.uppercase() }

        // Реальная неделя по отметкам привычек вместо жёсткой отрисовки 4 из 7.
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
        val weekEnd = weekStart.plusDays(6)

        viewModelScope.launch(dispatchers.io) {
            val completedDays = runCatching {
                db.habitDao().getLogsBetween(
                    weekStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                    weekEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                ).map { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() }
                    .toSet()
            }.getOrDefault(emptySet())

            val weekFlags = (0..6).map { weekStart.plusDays(it.toLong()) in completedDays }

            val focusMinutes = runCatching {
                val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
                db.focusDao().since(dayStart).sumOf { it.durationMin }
            }.getOrDefault(0)

            _state.update {
                it.copy(
                    yearProgressPercentage = yearPct,
                    dateDisplayString = dateString,
                    weekCompletedDays = weekFlags,
                    focusMinutesToday = focusMinutes
                )
            }
        }
    }

    /**
     * P1. Наблюдение за активной программой.
     *
     * Срез приходит с датой по умолчанию — на сегодня, — поэтому после отметки
     * подхода карточка сразу показывает новое число, без ручного обновления.
     */
    private fun observeProgramme() {
        viewModelScope.launch(dispatchers.io) {
            programmeRepository.activeProgrammeWithDaysFlow().collect { programme ->
                _state.update { it.copy(programme = programme) }
            }
        }
    }

    private fun observeData() {
        observeVosk()
        observeProgramme()
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

        val sm = settings
        _state.update {
            it.copy(
                userPersonaHardFacts = sm.userPersonaHardFacts,
                userPersonaActiveFocus = sm.userPersonaActiveFocus,
                userPersonaMemoryLog = sm.userPersonaMemoryLog
            )
        }
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
        persistUiState()
    }

    /**
     * Отказ в микрофоне — не тупик: объясняем, что текст и офлайн-режим работают,
     * и оставляем возможность спросить снова по кнопке (раньше отказ был dead end
     * на всё время жизни процесса).
     */
    fun onMicDenied() {
        _state.update {
            it.copy(
                infoMessage = "Без микрофона запись не выйдет, но текст и кнопки работают. " +
                    "Нажмите на микрофон ещё раз, чтобы разрешить доступ."
            )
        }
    }

    /**
     * Переживание смерти процесса без SavedStateHandle: класс создаётся фабрикой
     * только с Application, поэтому ключевое UI-состояние дублируем в prefs.
     * При старте восстанавливаем вкладку/сетку/фильтр и незаконченный разбор
     * (по logId из voice_logs).
     */
    private fun persistUiState() {
        runCatching {
            val s = _state.value
            settings.putString("ui_selected_tab", s.selectedTab)
            settings.putString("ui_main_tab", s.selectedMainTab.name)
            settings.putBoolean("ui_grid", s.isGridView)
            settings.putString("ui_pending_log", s.pendingReviewAction?.logId ?: "")
        }
    }

    fun restorePersistedUi() {
        runCatching {
            val tab = settings.getString("ui_selected_tab", "All")
            val mainTab = runCatching {
                MainTab.valueOf(settings.getString("ui_main_tab", MainTab.RHYTHM.name))
            }.getOrDefault(MainTab.RHYTHM)
            _state.update { it.copy(selectedTab = tab, selectedMainTab = mainTab) }
            val pendingLog = settings.getString("ui_pending_log", "")
            if (pendingLog.isNotBlank()) {
                viewModelScope.launch(dispatchers.io) {
                    val log = db.voiceLogDao().getVoiceLogById(pendingLog)
                    if (log != null && log.rawTranscript.isNotBlank()) {
                        val action = processVoiceUseCase.processText(log.rawTranscript).getOrNull()
                        if (action != null) {
                            _state.update {
                                it.copy(
                                    pendingReviewAction = action.copy(logId = log.id),
                                    infoMessage = "Восстановил незаконченный разбор записи."
                                )
                            }
                        }
                    }
                }
            }
        }
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
        // Foreground-сервис держит процесс живым при выключенном экране.
        runCatching {
            val app = getApplication<Application>()
            val intent = android.content.Intent(app, com.voicehabit.tracker.worker.VoiceRecordingService::class.java)
                .setAction(com.voicehabit.tracker.worker.VoiceRecordingService.ACTION_START)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }
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
        stopRecordingService()
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
            try {
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
                val result = runCatching {
                    processVoiceUseCase(
                        file,
                        spokenTranscript = capturedTranscript,
                        speechSeconds = speechSeconds
                    )
                }.getOrElse { error ->
                    Result.failure(error)
                }

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
                    handleParsedVoiceAction(action)
                }.onFailure { error ->
                    logger.w(
                        "voice",
                        "Ошибка обработки голоса: ${error.message}",
                        mapOf("err" to (error.message ?: error::class.java.simpleName))
                    )
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
                    handleParsedVoiceAction(fallbackAction)
                }
            } catch (t: Throwable) {
                logger.e("voice", "Непредвиденное падение при обработке аудио", error = t)
                _state.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = null,
                        errorMessage = "Не удалось обработать аудио: ${t.localizedMessage ?: "Сбой"}"
                    )
                }
            }
        }
    }

    fun cancelRecording() {
        stopRecordingService()
        speechRecognizer.stopListening()
        liveTranscript = null
        audioRecorder.cancelRecording()
    }

    private fun stopRecordingService() {
        runCatching {
            getApplication<Application>().stopService(
                android.content.Intent(
                    getApplication(),
                    com.voicehabit.tracker.worker.VoiceRecordingService::class.java
                )
            )
        }
    }

    private fun handleParsedVoiceAction(action: VoiceNoteAction) {
        // Паттерн «Shadow Pre-fill» (Автозаполнение с правом вето):
        // Если задача вводится голосом («Купить протеин до пятницы, срочно, категория здоровье»):
        // Приложение сразу открывает знакомый экран добавления задачи с уже выставленными ИИ чипами.
        val isSingleTaskFlow = action.tasksToAdd.isNotEmpty() &&
            action.habitsCompleted.isEmpty() &&
            action.tasksToDelete.isEmpty() &&
            action.tasksToReschedule.isEmpty() &&
            action.focusToStart == null &&
            (action.digest == null || action.digest.isEmpty)

        if (isSingleTaskFlow) {
            val taskAction = action.tasksToAdd.first()
            val draftTask = Task(
                id = "task_" + UUID.randomUUID(),
                title = taskAction.title,
                dueDateIso = taskAction.dueDate,
                priority = parsePriorityString(taskAction.priority),
                category = normalizeTaskCategory(taskAction.category),
                type = if (taskAction.taskType == TaskType.LONG.name) TaskType.LONG else TaskType.QUICK
            )
            if (taskAction.subtasks.isNotEmpty()) {
                val subList: List<Subtask> = taskAction.subtasks.mapIndexed { idx: Int, st: String ->
                    Subtask(
                        id = "sub_" + UUID.randomUUID(),
                        taskId = draftTask.id,
                        title = st,
                        isDone = false,
                        position = idx
                    )
                }
                _state.update { s ->
                    val updatedSubtasks = s.subtasks.toMutableMap()
                    updatedSubtasks[draftTask.id] = subList
                    s.copy(
                        isLoading = false,
                        pendingReviewAction = null,
                        selectedTaskForEdit = draftTask,
                        subtasks = updatedSubtasks,
                        infoMessage = null
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        pendingReviewAction = null,
                        selectedTaskForEdit = draftTask,
                        infoMessage = null
                    )
                }
            }
        } else {
            _state.update {
                it.copy(
                    isLoading = false,
                    pendingReviewAction = action,
                    infoMessage = null
                )
            }
        }
    }

    private fun normalizeTaskCategory(category: String?): String {
        if (category.isNullOrBlank()) return "General"
        val lower = category.trim().lowercase(Locale.ROOT)
        return when {
            lower.contains("здоров") || lower.contains("health") || lower.contains("спорт") || lower.contains("fitness") -> "Health"
            lower.contains("работ") || lower.contains("work") || lower.contains("бизнес") || lower.contains("проект") -> "Work"
            lower.contains("дом") || lower.contains("home") || lower.contains("быт") -> "Home"
            lower.contains("учеб") || lower.contains("учёб") || lower.contains("study") || lower.contains("книг") -> "Study"
            lower.contains("голос") -> "Голосовое"
            else -> category.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun parsePriorityString(prio: String?): Priority {
        if (prio.isNullOrBlank()) return Priority.MEDIUM
        val lower = prio.trim().lowercase(Locale.ROOT)
        return when {
            lower.contains("критич") || lower.contains("срочн") || lower.contains("urgent") || lower.contains("critical") || lower.contains("high") || lower.contains("высок") -> Priority.HIGH
            lower.contains("низк") || lower.contains("low") -> Priority.LOW
            else -> Priority.MEDIUM
        }
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
        // Обучение на правках (#6): запоминаем выбор для похожих фраз в будущем.
        if (action.rawTranscript.isNotBlank()) {
            runCatching { container.correctionStore.record(action.rawTranscript, mode) }
        }
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
        persistUiState()
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
                            val durationMin = it.focusTimerTotalSeconds / 60
                            viewModelScope.launch(dispatchers.io) {
                                extras.saveFocusSession(
                                    com.voicehabit.tracker.domain.model.FocusSession(
                                        id = extras.newFocusId(),
                                        taskId = null,
                                        habitId = null,
                                        label = "Фокус-таймер",
                                        durationMin = durationMin,
                                        completed = true
                                    )
                                )
                                withContext(dispatchers.main) {
                                    showSnackbar("Фокус завершён ($durationMin мин)")
                                    speak("Фокус завершён.")
                                    evaluateAchievements()
                                }
                            }
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
            val exists = taskRepository.getAllTasksList().any { it.id == task.id }
            if (exists) {
                taskRepository.updateTask(task)
            } else {
                taskRepository.insertTask(task)
            }
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
        reconcileFocusTimer()
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

    // ---------- 1.3.0: поиск, обратная связь, автовыводы ----------
    fun openGlobalSearch() = _state.update { it.copy(isGlobalSearchOpen = true) }
    fun closeGlobalSearch() = _state.update { it.copy(isGlobalSearchOpen = false, globalSearchQuery = "") }
    fun setGlobalSearchQuery(q: String) = _state.update { it.copy(globalSearchQuery = q) }
    fun openFeedback() = _state.update { it.copy(isFeedbackOpen = true) }
    fun closeFeedback() = _state.update { it.copy(isFeedbackOpen = false) }

    fun getApplicationContext(): android.content.Context = getApplication()

    /**
     * Импорт конспекта из Markdown (вторая сторона Obsidian-синхронизации).
     * Источник — буфер обмена: скопируйте заметку в Obsidian, нажмите импорт.
     */
    fun importObsidianMarkdown(markdown: String) {
        if (markdown.isBlank()) {
            _state.update { it.copy(errorMessage = "Буфер пуст: скопируйте Markdown-заметку из Obsidian.") }
            return
        }
        viewModelScope.launch(dispatchers.io) {
            val record = com.voicehabit.tracker.core.obsidian.ObsidianImporter.parseMarkdown("", markdown)
            if (record == null) {
                _state.update { it.copy(errorMessage = "Не похоже на конспект: нет заголовка и тезисов.") }
            } else {
                digestRepository.upsert(record)
                _state.update { it.copy(infoMessage = "Импортирован конспект: ${record.title.take(40)}") }
            }
        }
    }

    fun usageSummary(): String =
        container.usageAnalytics.snapshot().entries.joinToString("\n") { "${it.key}: ${it.value}" } +
            "\nПравок режима: ${container.correctionStore.correctionsCount()}"

    fun recentLogText(): String = runCatching {
        _state.value.logEvents.takeLast(40).joinToString("\n") { "${it.level} ${it.tag}: ${it.message}" }
    }.getOrDefault("")

    /**
     * Автовыводы о пользователе из задач и конспектов. Ручные факты персоны
     * не трогает — результат лежит отдельно и ручное всегда главнее.
     */
    fun refreshInferredInsights() {
        viewModelScope.launch(dispatchers.io) {
            val tasks = taskRepository.getAllTasksList().map { it.title }
            val digests = runCatching { digestRepository.recent(50) }.getOrDefault(emptyList())
                .map { (it.title + " " + it.gist) }
            val r = com.voicehabit.tracker.core.analysis.UserInsightEngine.infer(tasks, digests)
            val text = buildString {
                r.profession?.let { append("Похоже, вы: $it. ") }
                if (r.topics.isNotEmpty()) append("Частые темы: ${r.topics.joinToString(", ")}.")
            }.ifBlank { "Пока мало данных для выводов." }
            settings.inferredInsights = text
            _state.update { it.copy(inferredInsights = text, infoMessage = text) }
        }
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
                        infoMessage = "Запись от ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(log.createdAt))} повторно применена."
                    )
                }
            }
        }
    }

    /**
     * «Разобрать заново»: запись уже лежит в voice_logs с аудио и/или транскриптом,
     * но первый разбор не удался (не было сети, упал провайдер, Vosk не был готов).
     * Пробуем полный пайплайн: сначала аудиофайл через processVoiceUseCase,
     * при отсутствии файла — текст через processText. Статус обновляется,
     * запись не дублируется.
     */
    fun retryVoiceLog(logId: String) {
        viewModelScope.launch(dispatchers.io) {
            val log = db.voiceLogDao().getVoiceLogById(logId) ?: return@launch
            _state.update { it.copy(isLoading = true, infoMessage = "Разбираю запись заново…") }
            // Учитываем обучение на правках: если пользователь уже правил похожий
            // разбор, сохранённый режим имеет приоритет над правилами.
            val learned = container.correctionStore.lookup(log.rawTranscript)
            val result = runCatching {
                val audioFile = java.io.File(log.audioPath)
                if (audioFile.exists() && audioFile.length() > 0) {
                    processVoiceUseCase(audioFile, spokenTranscript = log.rawTranscript.ifBlank { null }, speechSeconds = 0)
                } else {
                    processVoiceUseCase.processText(log.rawTranscript)
                }
            }.getOrElse { Result.failure(it) }
            result.onSuccess { action ->
                val finalAction = if (learned != null && !action.modeIsOverridden) {
                    action.copy(mode = learned, modeIsOverridden = false)
                } else action
                handleParsedVoiceAction(finalAction)
                db.voiceLogDao().updateStatus(logId, VoiceUploadWorker.VOICE_STATUS_PROCESSED, finalAction.summary)
            }.onFailure { e ->
                db.voiceLogDao().updateStatus(logId, VoiceUploadWorker.VOICE_STATUS_FAILED, log.summary)
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Не разобралось и сейчас: ${e.message ?: "неизвестная ошибка"}. Аудио сохранено, попробуйте позже."
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

    // ── P1. Программы тренировок ───────────────────────────────────────────────

    /**
     * Импорт из буфера: сначала черновик, потом решение пользователя.
     *
     * Разбор не пишет в базу сразу. Человек видит неделю и упражнения и может
     * отказаться — программа не должна появляться в привычках незаметно.
     */
    fun previewProgrammeFromClipboard(raw: String) {
        val draft = ProgrammeParser.parse(raw)
        if (draft.isEmpty) {
            _state.value = _state.value.copy(
                programmeDraft = null,
                programmeMessage = draft.warnings.firstOrNull() ?: "Не разобрал ни одного дня тренировки"
            )
            return
        }
        viewModelScope.launch {
            val plan = ProgrammeHabitProjector.plan(draft, habitRepository.getAllHabitsList())
            val reused = plan.count { it.isReused }
            _state.value = _state.value.copy(
                programmeDraft = draft,
                programmePlan = plan,
                programmeMessage = buildString {
                    append("Разобрано: ${draft.days.size} дн., ${draft.trainingDays.size} тренировочных.")
                    if (reused > 0) append(" Встроим в существующие: $reused.")
                }
            )
        }
    }

    /** Отмена черновика — в базу ничего не попало, отменять нечего. */
    fun discardProgrammeDraft() {
        _state.value = _state.value.copy(
            programmeDraft = null,
            programmePlan = emptyList(),
            programmeMessage = null
        )
    }

    /**
     * Запись программы и её проекция на привычки.
     *
     * `scheduleDays` приходит из [ProgrammeHabitProjector] и здесь не вычисляется:
     * тренировки Пн/Ср/Пт обязаны стать привычками ровно с этими днями.
     */
    fun confirmProgrammeImport(draft: ProgrammeDraft? = _state.value.programmeDraft) {
        val source = draft ?: return
        if (_state.value.programmeImporting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(programmeImporting = true, programmeMessage = null)
            try {
                val programmeId = programmeRepository.saveProgramme(
                    id = null,
                    title = source.title,
                    athleteNote = source.athleteNote,
                    goals = source.goals,
                    sourceText = "",
                    days = source.days
                )
                val programme = programmeRepository.byId(programmeId) ?: return@launch
                val existing = habitRepository.getAllHabitsList()
                val plan = ProgrammeHabitProjector.plan(programme, existing)
                var created = 0
                var reused = 0
                plan.forEach { projection ->
                    val day = programme.days.first { it.id == projection.dayId }
                    val tags = ProgrammeHabitProjector.tagsFor(programme, day)
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val habitId = projection.existingHabitId
                    if (habitId != null) {
                        // Встраиваем день в существующую привычку: расписание и теги
                        // приводятся к программе, название и цвет остаются своими.
                        habitRepository.updateScheduleAndTags(habitId, projection.scheduleDays, tags)
                        programmeRepository.linkHabit(projection.dayId, habitId)
                        reused++
                    } else {
                        val habit = Habit(
                            id = java.util.UUID.randomUUID().toString(),
                            title = projection.habitTitle,
                            category = projection.category,
                            displayType = projection.displayType,
                            colorHex = projection.colorHex,
                            frequency = projection.frequency,
                            scheduleDays = projection.scheduleDays,
                            tags = tags
                        )
                        habitRepository.insertOrUpdateHabit(habit)
                        programmeRepository.linkHabit(projection.dayId, habit.id)
                        created++
                    }
                }
                _state.value = _state.value.copy(
                    programmeDraft = null,
                    programmePlan = emptyList(),
                    programmeImporting = false,
                    programmeMessage = "Готово: $created новых, $reused существующих. " +
                        "Дни: ${weekdaySummary(programme.trainingWeekdays)}"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    programmeImporting = false,
                    programmeMessage = "Не удалось сохранить программу: ${e.message ?: "ошибка"}"
                )
            }
        }
    }

    /** Удаление программы. Привычки и их стрики остаются: это карточки пользователя. */
    fun deleteProgramme() {
        val programme = _state.value.programme ?: return
        viewModelScope.launch {
            programmeRepository.delete(programme.id)
            _state.value = _state.value.copy(
                programme = null,
                programmeMessage = "Программа удалена. Привычки и стрики остались."
            )
        }
    }

    /** Отметка подхода в упражнении. */
    fun logProgrammeExercise(dayId: String, exerciseId: String, sets: Int, value: Double?) {
        viewModelScope.launch {
            val programmeId = state.value.programme?.id ?: return@launch
            // Число не названо — пишем 0, а не выдумываем повторы: в логе ноль
            // читается как «подход отработан, величина неизвестна».
            val amount = value ?: 0.0
            repeat(sets.coerceIn(1, 20)) {
                programmeRepository.logSet(exerciseId, programmeId, amount)
            }
            _state.value = _state.value.copy(
                programmeMessage = "Отмечено: $sets ${Programme.setWord(sets)}"
            )
        }
    }

    /** Закрытие дня целиком — «программа на сегодня сделана». */
    fun completeProgrammeDay() {
        viewModelScope.launch {
            val programmeId = state.value.programme?.id ?: return@launch
            val count = programmeRepository.completeWholeDay(programmeId)
            _state.value = _state.value.copy(programmeMessage = "День закрыт: упражнений — $count")
        }
    }

    /** Голосовая отметка прогресса внутри тренировки. */
    fun applyProgrammeVoice(raw: String) {
        val programme = state.value.programme ?: return
        val day = programme.days.firstOrNull { it.weekday == programme.todayWeekday && !it.isRest }
        val result = ProgrammeVoiceResolver.resolve(raw, day)
        if (result == null) {
            _state.value = _state.value.copy(
                programmeMessage = "Не понял, что отметить. Скажите «сделал 12 отжиманий»."
            )
            return
        }
        if (result.completesWholeDay) {
            completeProgrammeDay()
        } else {
            logProgrammeExercise(day!!.id, result.exercise.id, result.sets, result.number)
        }
        _state.value = _state.value.copy(programmeMessage = result.reason)
    }

    private fun weekdaySummary(days: Set<Int>): String = days.sorted().joinToString(", ") {
        Programme.Day(id = "", programmeId = "", weekday = it).weekdayLabel
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

    /**
     * Дни месяца с отметками привычки — для календарной сетки в карточке.
     * Грузится по требованию при листании месяцев, а не одним фиксированным окном.
     */
    suspend fun habitMonthCompletions(habitId: String, month: java.time.YearMonth): Set<Long> =
        withContext(dispatchers.io) {
            runCatching {
                val zone = java.time.ZoneId.systemDefault()
                db.habitDao().getLogsForHabit(habitId)
                    .map { java.time.Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() }
                    .filter { it.year == month.year && it.month == month.month }
                    .map { it.toEpochDay() }
                    .toSet()
            }.getOrDefault(emptySet())
        }

    fun setHabitSchedule(id: String, days: Set<Int>) {        viewModelScope.launch {
            val habit = habitRepository.getHabitById(id) ?: return@launch
            val safeDays = days.ifEmpty { (1..7).toSet() }
            habitRepository.updateScheduleAndTags(id, safeDays, habit.tags)
            val updated = habitRepository.getHabitById(id) ?: return@launch
            _state.update { s ->
                if (s.selectedHabitForDetail?.id == id) {
                    s.copy(selectedHabitForDetail = updated)
                } else s
            }
            DuroHabitWidgetProvider.updateAllWidgets(getApplication())
            com.voicehabit.tracker.widget.DuroHabitCardWidgetProvider.updateAllCardWidgets(getApplication())
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
                    showSnackbar("День заморожен: стрик не прервётся")
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
                    showSnackbar("Вчерашний день восстановлен заморозкой")
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
            showSnackbar("Рутина «${routine.title}»: отмечено $marked")
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
        showSnackbar("Рутина «${routine.title}»: отмечено $marked")
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
            showSnackbar("Достижение: ${def?.title ?: id}")
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
            val todayDow = java.time.LocalDate.now().dayOfWeek.value
            val (doneHabits, habitsToday) = com.voicehabit.tracker.core.analysis.HabitStats.todayCounts(
                habits = habits,
                todayDow = todayDow,
                isRestDay = { it.isRestDay(todayDow) },
                isCompleted = { it.isCompletedToday }
            )
            val tasks = taskRepository.getAllTasksList()
            val open = tasks.filter { !it.isCompleted && it.deletedAt == null && !it.isArchived }
            val doneToday = tasks.count {
                it.isCompleted && it.completedAt != null &&
                    java.time.Instant.ofEpochMilli(it.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
            }
            val tomorrow = open.filter { it.dueDateIso?.startsWith(LocalDate.now().plusDays(1).toString()) == true }
            val text = buildString {
                // P1: «из ${habits.size}» врало — в счёт попадали привычки на другие дни.
                append(
                    if (habitsToday > 0) {
                        "Сегодня: привычек $doneHabits из $habitsToday, задач закрыто $doneToday, открыто ${open.size}. "
                    } else {
                        "Сегодня: привычек нет, задач закрыто $doneToday, открыто ${open.size}. "
                    }
                )
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
        // Анти-«копипаст»: сырой текст режется на короткий заголовок + детали.
        val split = com.voicehabit.tracker.core.analysis.FocusTitleCleaner.split(
            label.trim().ifBlank { settings.userPersonaActiveFocus.trim().ifBlank { "Фокус-сессия" } }
        )
        _state.update {
            it.copy(
                focusRun = FocusRun(
                    totalSec = totalSec,
                    remainingSec = totalSec,
                    label = split.title,
                    details = split.details,
                    taskId = taskId,
                    habitId = habitId
                ),
                screen = AppScreen.FOCUS
            )
        }
        startFocusTimerLoop(totalSec)
    }

    enum class MentalEnergy { FATIGUED, NORMAL }

    /**
     * Калибровка под ментальное состояние (State-Aware Calibration):
     * Инспектирует недавние записи в дневнике. При обнаружении усталости/тревоги
     * молча запускает микро-спринт на 15 минут (micro-commitment) без снисходительного текста жалости.
     */
    fun detectMentalState(): MentalEnergy {
        val recent = _state.value.digests.take(5)
        val fatigueKeywords = listOf("устал", "устала", "нет сил", "выгоран", "тяжело", "перегруз", "стресс", "сонлив", "тревож", "апатия")
        val isFatigued = recent.any { record ->
            val tone = record.tone.lowercase(Locale.ROOT)
            val content = (record.gist + " " + record.transcript).lowercase(Locale.ROOT)
            tone.contains("тревож") || tone.contains("устал") || tone.contains("стресс") ||
                fatigueKeywords.any { content.contains(it) }
        }
        return if (isFatigued) MentalEnergy.FATIGUED else MentalEnergy.NORMAL
    }

    /** Сборка всестороннего контекста пользователя для ИИ (профиль, идеи, память, дневник, задачи). */
    fun buildFullAiFocusContext(): String {
        val sb = StringBuilder()
        val persona = settings.getUserPersonaContext()
        if (persona.isNotBlank()) {
            sb.appendLine(persona)
        }
        val recentThoughts = _state.value.digests.take(3)
        if (recentThoughts.isNotEmpty()) {
            sb.appendLine("Недавние мысли и темы из дневника:")
            recentThoughts.forEach { d ->
                sb.appendLine("- ${d.title}: ${d.gist.take(120)}")
            }
        }
        val activeTasks = _state.value.tasks.filter { !it.isCompleted }.take(4)
        if (activeTasks.isNotEmpty()) {
            sb.appendLine("Актуальные открытые задачи:")
            activeTasks.forEach { t ->
                sb.appendLine("- ${t.title}")
            }
        }
        return sb.toString().trim()
    }

    /** Локальный смысловой синтезатор микро-действий (гарантирует отсутствие шаблонных абстракций). */
    fun generateSmartSemanticStep(
        goal: String,
        previousStep: String?,
        type: FocusStepType
    ): String {
        val lowerGoal = goal.lowercase(Locale.ROOT)
        val isLanguageOrStudy = lowerGoal.contains("язык") || lowerGoal.contains("english") ||
            lowerGoal.contains("английск") || lowerGoal.contains("слов") ||
            lowerGoal.contains("vocab") || lowerGoal.contains("граммат") || lowerGoal.contains("учеб")
        val isCodeOrDev = lowerGoal.contains("код") || lowerGoal.contains("проект") ||
            lowerGoal.contains("разработ") || lowerGoal.contains("архитектур") ||
            lowerGoal.contains("модул") || lowerGoal.contains("фич") || lowerGoal.contains("баг")
        val isWritingOrIdea = lowerGoal.contains("стать") || lowerGoal.contains("текст") ||
            lowerGoal.contains("пост") || lowerGoal.contains("иде") ||
            lowerGoal.contains("дневник") || lowerGoal.contains("мысл") || lowerGoal.contains("книг")

        return when (type) {
            FocusStepType.INITIAL -> {
                when {
                    isLanguageOrStudy -> "Открыть список материалов по «$goal» и выписать первые 5 ключевых элементов"
                    isCodeOrDev -> "Открыть проект и записать 3 ключевые точки реализации в черновик"
                    isWritingOrIdea -> "Открыть черновик и сформулировать 3 главных тезиса одной строкой"
                    else -> "Сфокусироваться на «$goal»: открыть материалы и выписать первые 3 пункта"
                }
            }
            FocusStepType.NEXT -> {
                when {
                    isLanguageOrStudy -> "Составить по 1 практическому примеру или предложению с каждым элементом"
                    isCodeOrDev -> "Написать реализацию первой точки без оптимизаций и тестов"
                    isWritingOrIdea -> "Развернуть первый тезис в 2 предложения своими словами"
                    else -> "Взять первый пункт из выписанных по «$goal» и выполнить черновой вариант"
                }
            }
            FocusStepType.ALTERNATIVE -> {
                when {
                    isLanguageOrStudy -> "Сменить формат: включить аудио или диалог по «$goal» на 5 минут и уловить 3 фразы"
                    isCodeOrDev -> "Сменить фокус: открыть документацию или схему и проследить путь данных глазами"
                    isWritingOrIdea -> "Сменить формат: наговорить черновой вариант мысли вслух за 3 минуты"
                    else -> "Сменить формат по «$goal»: набросать схему на листе или проговорить план вслух"
                }
            }
            FocusStepType.UNBLOCK -> {
                when {
                    isLanguageOrStudy -> "Снять затык: открыть словарь и найти ровно 1 пример использования"
                    isCodeOrDev -> "Снять затык: открыть нужный файл и написать 1 строку комментария к коду"
                    isWritingOrIdea -> "Снять затык: записать самое первое, пусть даже неидеальное предложение"
                    else -> "Снять затык: записать ровно 1 простое действие на листе бумаги за 1 минуту"
                }
            }
        }
    }

    /**
     * Запуск Адаптивного Спринта (Flow Engine).
     * Автоматически подтягивает фокус из профиля, если поле оставлено пустым.
     */
    fun startAdaptiveFocus(label: String, taskId: String? = null, habitId: String? = null) {
        val resolvedLabel = label.trim().ifBlank {
            settings.userPersonaActiveFocus.trim().ifBlank {
                _state.value.tasks.firstOrNull { !it.isCompleted }?.title ?: "Фокус-сессия"
            }
        }
        val energy = detectMentalState()
        val durationMin = if (energy == MentalEnergy.FATIGUED) 15 else 25
        val smartInitialStep = generateSmartSemanticStep(
            goal = resolvedLabel,
            previousStep = null,
            type = FocusStepType.INITIAL
        )

        focusTicker?.cancel()
        val totalSec = durationMin * 60
        val splitAdaptive = com.voicehabit.tracker.core.analysis.FocusTitleCleaner.split(resolvedLabel)
        _state.update {
            it.copy(
                focusRun = FocusRun(
                    totalSec = totalSec,
                    remainingSec = totalSec,
                    label = splitAdaptive.title,
                    details = splitAdaptive.details,
                    taskId = taskId,
                    habitId = habitId,
                    isAdaptiveMicroSprint = true,
                    currentStep = smartInitialStep,
                    completedSteps = emptyList(),
                    isStepLoading = settings.effectiveGeminiApiKey.isNotBlank()
                ),
                screen = AppScreen.FOCUS
            )
        }
        startFocusTimerLoop(totalSec)

        // Фоново уточняем атомарный шаг через Gemini при наличии API-ключа
        if (settings.effectiveGeminiApiKey.isNotBlank()) {
            val fullContext = buildFullAiFocusContext()
            viewModelScope.launch(dispatchers.io) {
                val enhanced = directAiService.decomposeFocusStep(
                    taskOrGoal = resolvedLabel,
                    currentStep = null,
                    completedSteps = emptyList(),
                    stepType = FocusStepType.INITIAL,
                    geminiApiKey = settings.effectiveGeminiApiKey,
                    userPersonaContext = fullContext
                ).getOrNull()
                withContext(dispatchers.main) {
                    _state.update { s ->
                        s.copy(
                            focusRun = s.focusRun?.copy(
                                currentStep = enhanced?.takeIf { it.isNotBlank() } ?: s.focusRun?.currentStep,
                                isStepLoading = false
                            )
                        )
                    }
                }
            }
        }
    }

    private fun startFocusTimerLoop(startRemainingSec: Int, totalMin: Int = startRemainingSec / 60) {
        startFocusTimerService(startRemainingSec, totalMin)
        focusTicker = viewModelScope.launch {
            var remaining = startRemainingSec
            while (remaining > 0) {
                kotlinx.coroutines.delay(1000)
                val isPaused = _state.value.focusRun?.isPaused ?: false
                if (!isPaused) {
                    remaining--
                    _state.update { s ->
                        s.copy(focusRun = s.focusRun?.copy(remainingSec = remaining))
                    }
                }
            }
            finishFocus(completed = true)
        }
    }

    /**
     * Фоновый страж таймера: сервис — источник правды об окончании.
     * UI-тикер выше — только для плавного отображения секунд.
     */
    private fun startFocusTimerService(totalSec: Int, totalMin: Int = totalSec / 60) {
        runCatching {
            val app = getApplication<Application>()
            val label = _state.value.focusRun?.label ?: "Фокус-сессия"
            val intent = android.content.Intent(app, com.voicehabit.tracker.worker.FocusTimerService::class.java)
                .setAction(com.voicehabit.tracker.worker.FocusTimerService.ACTION_START)
                .putExtra(com.voicehabit.tracker.worker.FocusTimerService.EXTRA_TOTAL_SEC, totalSec)
                .putExtra(com.voicehabit.tracker.worker.FocusTimerService.EXTRA_LABEL, label)
                .putExtra(com.voicehabit.tracker.worker.FocusTimerService.EXTRA_TOTAL_MIN, totalMin)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }
    }

    private fun stopFocusTimerService() {
        runCatching {
            val app = getApplication<Application>()
            app.startService(
                android.content.Intent(app, com.voicehabit.tracker.worker.FocusTimerService::class.java)
                    .setAction(com.voicehabit.tracker.worker.FocusTimerService.ACTION_CANCEL)
            )
        }
    }

    /**
     * Сверка с фоновым таймером при возврате: процесс могли убить, а сервис —
     * нет. Если конец в будущем, а сессии в памяти нет — восстанавливаем.
     * Если сервис уже отзвенел — закрываем сессию задним числом.
     */
    fun reconcileFocusTimer() {
        val prefs = com.voicehabit.tracker.worker.FocusTimerService.prefs(getApplication())
        val finishedLabel = prefs.getString(com.voicehabit.tracker.worker.FocusTimerService.KEY_FINISHED_LABEL, null)
        if (finishedLabel != null) {
            prefs.edit()
                .remove(com.voicehabit.tracker.worker.FocusTimerService.KEY_FINISHED_LABEL)
                .remove(com.voicehabit.tracker.worker.FocusTimerService.KEY_FINISHED_MIN)
                .remove(com.voicehabit.tracker.worker.FocusTimerService.KEY_FINISHED_AT)
                .apply()
            val run = _state.value.focusRun
            if (run != null) {
                finishFocus(completed = true)
            } else {
                val mins = prefs.getInt(com.voicehabit.tracker.worker.FocusTimerService.KEY_FINISHED_MIN, 0)
                viewModelScope.launch(dispatchers.io) {
                    extras.saveFocusSession(
                        com.voicehabit.tracker.domain.model.FocusSession(
                            id = extras.newFocusId(), taskId = null, habitId = null,
                            label = finishedLabel, durationMin = mins, completed = true
                        )
                    )
                    withContext(dispatchers.main) {
                        showSnackbar("Фокус «$finishedLabel» завершён в фоне")
                    }
                }
            }
            return
        }
        val endAt = prefs.getLong(com.voicehabit.tracker.worker.FocusTimerService.KEY_END_AT, 0L)
        if (endAt > System.currentTimeMillis() && _state.value.focusRun == null) {
            // totalMin берём из prefs, а не из остатка: иначе 15-минутный спринт
            // после перезапуска превращался бы в «2 мин» целочисленным делением.
            val origTotalMin = prefs.getInt(com.voicehabit.tracker.worker.FocusTimerService.KEY_TOTAL_MIN, 0)
            val remaining = ((endAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(1)
            val label = prefs.getString(com.voicehabit.tracker.worker.FocusTimerService.KEY_LABEL, "Фокус-сессия")
                ?: "Фокус-сессия"
            val totalMin = origTotalMin.takeIf { it > 0 } ?: (remaining / 60)
            _state.update {
                it.copy(
                    focusRun = FocusRun(
                        totalSec = (totalMin * 60).coerceAtLeast(remaining),
                        remainingSec = remaining,
                        label = label
                    )
                )
            }
            startFocusTimerLoop(remaining, totalMin)
        }
    }

    /**
     * Знакома ли тема дневнику: топ-1 совпадение по токенам среди конспектов
     * или прямое вхождение в открытую задачу. Ниже порога — не гадаем уровень,
     * а показываем калибровку.
     */
    suspend fun isTopicFamiliar(label: String): Boolean = withContext(dispatchers.io) {
        if (label.length < 3) return@withContext true
        val digests = runCatching { digestRepository.recent(60) }.getOrDefault(emptyList())
        val best = com.voicehabit.tracker.core.analysis.TokenOverlapSearch.rank(
            label, digests, { (it.title + " " + it.gist + " " + it.keyPoints.joinToString(" ")) }
        ).firstOrNull()
        if (best != null && best.second >= 0.34) return@withContext true
        val tasks = taskRepository.getAllTasksList().map { it.title.lowercase() }
        val low = label.lowercase()
        return@withContext tasks.any { it.contains(low.take(12)) || low.contains(it.take(12)) }
    }

    /** Точка входа адаптивного старта с калибровкой незнакомых тем. */
    fun requestAdaptiveStart(label: String, taskId: String? = null, habitId: String? = null) {
        val target = label.trim().ifBlank {
            settings.userPersonaActiveFocus.trim().ifBlank {
                _state.value.tasks.firstOrNull { !it.isCompleted }?.title ?: "Фокус-сессия"
            }
        }
        viewModelScope.launch {
            if (isTopicFamiliar(target)) {
                startAdaptiveFocus(target, taskId, habitId)
            } else {
                _state.update { it.copy(focusCalibration = FocusCalibration(target, taskId, habitId)) }
            }
        }
    }

    fun dismissCalibration() {
        _state.update { it.copy(focusCalibration = null) }
    }

    /** Пользователь уточнил уровень — стартуем с обогащённым контекстом. */
    fun confirmCalibration(level: String, contextText: String) {
        val cal = _state.value.focusCalibration ?: return
        val enriched = buildString {
            append(cal.label)
            append(", уровень: ").append(level)
            if (contextText.isNotBlank()) append(", сейчас: ").append(contextText.trim().take(120))
        }
        _state.update { it.copy(focusCalibration = null) }
        startAdaptiveFocus(enriched, cal.taskId, cal.habitId)
    }

    /** Динамическая атомизация: закрыть текущий физический шаг и запросить следующий. */
    fun completeCurrentFocusStep() {
        val run = _state.value.focusRun ?: return
        val step = run.currentStep ?: return
        val updatedSteps = run.completedSteps + step
        val fullGoal = if (run.details.isNotBlank()) "${run.label} (${run.details})" else run.label
        val smartNextStep = generateSmartSemanticStep(
            goal = fullGoal,
            previousStep = step,
            type = FocusStepType.NEXT
        )

        _state.update { s ->
            s.copy(
                focusRun = s.focusRun?.copy(
                    completedSteps = updatedSteps,
                    currentStep = smartNextStep,
                    isStepLoading = settings.effectiveGeminiApiKey.isNotBlank()
                )
            )
        }

        if (settings.effectiveGeminiApiKey.isNotBlank()) {
            val fullContext = buildFullAiFocusContext()
            viewModelScope.launch(dispatchers.io) {
                val nextStepAi = directAiService.decomposeFocusStep(
                    taskOrGoal = fullGoal,
                    currentStep = step,
                    completedSteps = updatedSteps,
                    stepType = FocusStepType.NEXT,
                    geminiApiKey = settings.effectiveGeminiApiKey,
                    userPersonaContext = fullContext
                ).getOrNull()
                withContext(dispatchers.main) {
                    _state.update { s ->
                        s.copy(
                            focusRun = s.focusRun?.copy(
                                currentStep = nextStepAi?.takeIf { it.isNotBlank() } ?: s.focusRun?.currentStep,
                                isStepLoading = false
                            )
                        )
                    }
                }
            }
        }
    }

    /** Переопределение в один тап: сменить формат работы (смена модальности). */
    fun overrideCurrentFocusStep(customStep: String? = null) {
        val run = _state.value.focusRun ?: return
        val currentStep = run.currentStep
        val fullGoal = if (run.details.isNotBlank()) "${run.label} (${run.details})" else run.label
        val smartAltStep = customStep?.takeIf { it.isNotBlank() } ?: generateSmartSemanticStep(
            goal = fullGoal,
            previousStep = currentStep,
            type = FocusStepType.ALTERNATIVE
        )

        _state.update { s ->
            s.copy(
                focusRun = s.focusRun?.copy(
                    currentStep = smartAltStep,
                    isStepLoading = settings.effectiveGeminiApiKey.isNotBlank()
                )
            )
        }

        if (settings.effectiveGeminiApiKey.isNotBlank()) {
            val fullContext = buildFullAiFocusContext()
            viewModelScope.launch(dispatchers.io) {
                val altStepAi = directAiService.decomposeFocusStep(
                    taskOrGoal = fullGoal,
                    currentStep = currentStep,
                    completedSteps = run.completedSteps,
                    stepType = FocusStepType.ALTERNATIVE,
                    geminiApiKey = settings.effectiveGeminiApiKey,
                    userPersonaContext = fullContext
                ).getOrNull()
                withContext(dispatchers.main) {
                    _state.update { s ->
                        s.copy(
                            focusRun = s.focusRun?.copy(
                                currentStep = altStepAi?.takeIf { it.isNotBlank() } ?: s.focusRun?.currentStep,
                                isStepLoading = false
                            )
                        )
                    }
                }
            }
        }
    }

    /** Голосовой штурман при паузе (Focus Guard). */
    fun pauseFocus() {
        _state.update { s ->
            s.copy(focusRun = s.focusRun?.copy(isPaused = true, pauseReasonPrompt = true))
        }
    }

    fun resumeFocus() {
        _state.update { s ->
            s.copy(focusRun = s.focusRun?.copy(isPaused = false, pauseReasonPrompt = false))
        }
    }

    /** Focus Guard: [Затык в задаче] генерирует 2-минутное микро-действие для снятия ступора. */
    fun resolveFocusBlocker() {
        val run = _state.value.focusRun ?: return
        val currentStep = run.currentStep
        val fullGoal = if (run.details.isNotBlank()) "${run.label} (${run.details})" else run.label
        val smartUnblock = generateSmartSemanticStep(
            goal = fullGoal,
            previousStep = currentStep,
            type = FocusStepType.UNBLOCK
        )

        _state.update { s ->
            s.copy(
                focusRun = s.focusRun?.copy(
                    isPaused = false,
                    pauseReasonPrompt = false,
                    currentStep = smartUnblock,
                    isStepLoading = settings.effectiveGeminiApiKey.isNotBlank()
                )
            )
        }

        if (settings.effectiveGeminiApiKey.isNotBlank()) {
            val fullContext = buildFullAiFocusContext()
            viewModelScope.launch(dispatchers.io) {
                val unblockAi = directAiService.decomposeFocusStep(
                    taskOrGoal = fullGoal,
                    currentStep = currentStep,
                    completedSteps = run.completedSteps,
                    stepType = FocusStepType.UNBLOCK,
                    geminiApiKey = settings.effectiveGeminiApiKey,
                    userPersonaContext = fullContext
                ).getOrNull()
                withContext(dispatchers.main) {
                    _state.update { s ->
                        s.copy(
                            focusRun = s.focusRun?.copy(
                                currentStep = unblockAi?.takeIf { it.isNotBlank() } ?: s.focusRun?.currentStep,
                                isStepLoading = false
                            )
                        )
                    }
                }
            }
        }
    }

    /** Focus Guard: [Отвлекли] держит таймер на паузе в тишине. */
    fun confirmDistraction() {
        _state.update { s ->
            s.copy(focusRun = s.focusRun?.copy(pauseReasonPrompt = false))
        }
    }

    fun dismissFocusDebrief() {
        _state.update { it.copy(focusDebriefRun = null) }
    }

    fun saveFocusDebriefToObsidian(notes: String) {
        val debrief = _state.value.focusDebriefRun ?: return
        viewModelScope.launch(dispatchers.io) {
            val result = container.obsidianVaultManager.exportFocusSprint(
                label = debrief.label,
                durationMin = debrief.totalSec / 60,
                completed = true,
                debriefNotes = notes.ifBlank { null },
                steps = debrief.completedSteps
            )
            withContext(dispatchers.main) {
                _state.update { it.copy(focusDebriefRun = null) }
                if (result.isSuccess) {
                    showSnackbar("Спринт сохранён в Obsidian: ${result.getOrNull()?.relativePath}")
                } else {
                    showSnackbar("Не удалось сохранить в Obsidian: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun cancelFocus() {
        focusTicker?.cancel()
        focusTicker = null
        stopFocusTimerService()
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

    /** Завершить спринт успешно: вызывает дебрифинг и сохраняет запись в дневник. */
    fun completeFocus() {
        finishFocus(completed = true)
    }

    private fun finishFocus(completed: Boolean) {
        val run = _state.value.focusRun ?: return
        focusTicker = null
        stopFocusTimerService()
        // Двусторонняя связка со спринтами: завершённый спринт сам становится
        // записью дневника, обогащая контекст для следующих калибровок.
        if (completed) {
            viewModelScope.launch(dispatchers.io) {
                runCatching {
                    val done = run.completedSteps.size
                    digestRepository.upsert(
                        com.voicehabit.tracker.domain.model.DigestRecord(
                            id = digestRepository.newId(),
                            title = "Фокус: ${run.label}",
                            gist = "Спринт ${run.totalSec / 60} мин завершён" +
                                (if (done > 0) ", закрыто шагов: $done" else ""),
                            keyPoints = run.completedSteps.take(8),
                            tone = "",
                            mode = com.voicehabit.tracker.domain.model.IntentMode.DICTATE,
                            modeConfidence = 1f,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        _state.update { it.copy(focusRun = null, focusDebriefRun = run) }
        viewModelScope.launch(dispatchers.io) {
            extras.saveFocusSession(
                com.voicehabit.tracker.domain.model.FocusSession(
                    id = extras.newFocusId(), taskId = run.taskId, habitId = run.habitId,
                    label = run.label, durationMin = run.totalSec / 60, completed = completed
                )
            )
            withContext(dispatchers.main) {
                showSnackbar("Фокус завершён: ${run.label} (${run.totalSec / 60} мин)")
                speak("Фокус завершён.")
                evaluateAchievements()
            }
        }
    }

    /** Убрать тестовый запуск из истории сессий. */
    fun deleteFocusSession(id: String) {
        viewModelScope.launch(dispatchers.io) {
            extras.deleteFocusSession(id)
            _state.update {
                it.copy(infoMessage = "Сессия убрана из истории")
            }
        }
    }

    // ---------- Модуль «Контекст обо мне» (User Persona & Memory Engine) ----------

    fun saveUserPersona(hardFacts: String, activeFocus: String) {
        val sm = settings
        sm.userPersonaHardFacts = hardFacts
        sm.userPersonaActiveFocus = activeFocus
        _state.update {
            it.copy(
                userPersonaHardFacts = hardFacts,
                userPersonaActiveFocus = activeFocus
            )
        }
        showSnackbar("Контекст ИИ сохранён")
    }

    fun addMemoryFact(fact: String) {
        if (fact.isBlank()) return
        val sm = settings
        sm.addMemoryFact(fact)
        _state.update {
            it.copy(userPersonaMemoryLog = sm.userPersonaMemoryLog)
        }
    }

    fun deleteMemoryFact(fact: String) {
        val sm = settings
        sm.removeMemoryFact(fact)
        _state.update {
            it.copy(userPersonaMemoryLog = sm.userPersonaMemoryLog)
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
            appendLine("Обзор недели:")
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

    fun setThemePreset(preset: com.voicehabit.tracker.presentation.theme.AppThemePreset) {
        settings.themePreset = preset
        _state.update { it.copy(themePreset = preset) }
    }

    /** Синхронизация темы после прямого изменения настроек из диалога. */
    fun refreshThemeState() {
        _state.update {
            it.copy(
                themePreset = settings.themePreset,
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
        _state.update { it.copy(ttsEnabled = enabled) }
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
