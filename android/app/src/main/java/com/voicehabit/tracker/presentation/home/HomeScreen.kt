package com.voicehabit.tracker.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import com.voicehabit.tracker.core.update.AppUpdateUiState
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import com.voicehabit.tracker.presentation.achievements.AchievementsScreen
import com.voicehabit.tracker.presentation.archive.ArchiveScreen
import com.voicehabit.tracker.presentation.backup.BackupScreen
import com.voicehabit.tracker.presentation.challenges.ChallengesScreen
import com.voicehabit.tracker.presentation.focus.FocusScreen
import com.voicehabit.tracker.presentation.hub.AboutScreen
import com.voicehabit.tracker.presentation.hub.CelebrationOverlay
import com.voicehabit.tracker.presentation.digests.DigestsScreen
import com.voicehabit.tracker.presentation.hub.HubScreen
import com.voicehabit.tracker.presentation.onboarding.OnboardingDialog
import com.voicehabit.tracker.presentation.review.ReviewScreen
import com.voicehabit.tracker.presentation.routines.RoutinesScreen
import com.voicehabit.tracker.presentation.stats.StatsScreen
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.voicehabit.tracker.presentation.create.CreateHabitScreen
import com.voicehabit.tracker.presentation.home.components.DuroHabitGridCard
import com.voicehabit.tracker.presentation.home.components.HabitCard
import com.voicehabit.tracker.presentation.home.components.SwipeableTaskCard
import com.voicehabit.tracker.presentation.home.components.HabitDetailBottomSheet
import com.voicehabit.tracker.presentation.home.components.OperationsLogBottomSheet
import com.voicehabit.tracker.presentation.home.components.SettingsDialog
import com.voicehabit.tracker.presentation.home.components.TaskCard
import com.voicehabit.tracker.presentation.home.components.TaskEditorBottomSheet
import com.voicehabit.tracker.presentation.home.components.VoiceQueueBottomSheet
import com.voicehabit.tracker.presentation.theme.*
import com.voicehabit.tracker.presentation.voice.ReviewBottomSheet
import com.voicehabit.tracker.presentation.voice.VoiceRecordFab

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    githubUpdateController: com.voicehabit.tracker.core.update.github.GithubUpdateController,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val updateState by githubUpdateController.state.collectAsState()
    val haptics = rememberDuroHaptics()
    val openTaskCount by remember(state.tasks) {
        derivedStateOf { state.tasks.count { !it.isCompleted } }
    }

    if (state.isCreateHabitOpen) {
        CreateHabitScreen(
            onDismiss = { viewModel.closeCreateHabit() },
            onSaveHabit = { habit -> viewModel.addHabit(habit) },
            onSaveTask = { task -> viewModel.addTaskWithReminder(task) },
            viewModel = viewModel
        )
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // infoMessage больше не висит вечно: раньше баннер «Слышу: ...» оставался
    // до закрытия экрана, а ошибки не показывались вообще.
    LaunchedEffect(state.infoMessage, state.errorMessage, state.snackbarMessage) {
        val text = state.errorMessage ?: state.snackbarMessage ?: state.infoMessage
        if (!text.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = text, withDismissAction = true)
            viewModel.consumeSnackbar()
        }
    }

    // One shared-motion scope wraps the list and detail sheet. The same hero
    // key links the selected row to the sheet header, so the selected habit
    // identity travels instead of popping into existence.
    val selectedHabit = state.selectedHabitForDetail
    val selectedHeroKey = selectedHabit?.let { "habit-${it.id}-hero" }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        val sharedTransitionScope = this
        AnimatedVisibility(
            visible = true,
            modifier = Modifier.fillMaxSize()
        ) {
            val animatedVisibilityScope = this
            Scaffold(
                containerColor = DuroBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    )
                },
                floatingActionButton = {
                    VoiceRecordFab(
                        isRecordingFlow = viewModel.audioRecorder.isRecording,
                        durationSecondsFlow = viewModel.audioRecorder.recordDurationSeconds,
                        amplitudeFlow = viewModel.audioRecorder.amplitudeNormalized,
                        onStartRecord = { viewModel.startRecording() },
                        onStopRecord = { viewModel.stopRecording() },
                        onCancelRecord = { viewModel.cancelRecording() },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    )
                },
                floatingActionButtonPosition = FabPosition.Start
            ) { innerPadding ->
                // contentWindowInsets=0 + ручные safeDrawing/navigationBars выше:
                // innerPadding здесь всегда 0 (нет topBar/bottomBar), padding
                // применён чтобы не прятать контент за FAB и пройти lint.
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
            // Маршрутизация доп. экранов: поверх главной, FAB и оверлеи остаются.
            // Системная кнопка «назад» возвращает на главную, а не выкидывает из приложения.
            androidx.activity.compose.BackHandler(enabled = state.screen != AppScreen.HOME) {
                viewModel.closeScreen()
            }
            if (state.screen != AppScreen.HOME) {
                when (state.screen) {
                    AppScreen.HUB -> HubScreen(viewModel = viewModel)
                    AppScreen.STATS -> StatsScreen(viewModel = viewModel)
                    AppScreen.ARCHIVE -> ArchiveScreen(viewModel = viewModel)
                    AppScreen.ROUTINES -> RoutinesScreen(viewModel = viewModel)
                    AppScreen.CHALLENGES -> ChallengesScreen(viewModel = viewModel)
                    AppScreen.ACHIEVEMENTS -> AchievementsScreen(viewModel = viewModel)
                    AppScreen.REVIEW -> ReviewScreen(viewModel = viewModel)
                    AppScreen.FOCUS -> FocusScreen(viewModel = viewModel)
                    AppScreen.BACKUP -> BackupScreen(viewModel = viewModel)
                    AppScreen.ABOUT -> AboutScreen(viewModel = viewModel)
                    AppScreen.DIGESTS -> DigestsScreen(viewModel = viewModel)
                    AppScreen.HOME -> Unit
                }
            } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Кнопки верхней панели залезали под статус-бар/декор:
                    // тапы туда не доходили. Отступ под системные бары обязателен.
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // TOP BAR: Logo + "Reached X% of 2026" (Frame 18) + Settings Button
                // На узком экране левая подпись сжимается эллипсисом, кнопки —
                // фиксированного размера и не налезают друг на друга.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DuroAsterisk(size = 20.dp, color = DuroOrange)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reached ${state.yearProgressPercentage}% of 2026",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = DuroTextSecondary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Voice Queue / History Button
                        DuroIconActionButton(
                            onClick = { viewModel.openVoiceQueue() },
                            icon = Icons.Default.History,
                            contentDescription = "Очередь и история записей",
                            containerColor = Color(0xFF181822),
                            iconTint = DuroOrange
                        )

                        // Журнал операций со счётчиком активных фоновых задач
                        BadgedBox(
                            badge = {
                                if (state.activeOperations.isNotEmpty()) {
                                    Badge(
                                        containerColor = DuroAmber,
                                        contentColor = Color.Black
                                    ) { Text("${state.activeOperations.size}") }
                                }
                            }
                        ) {
                            DuroIconActionButton(
                                onClick = { viewModel.openOperationsLog() },
                                icon = Icons.Default.Terminal,
                                contentDescription = "Журнал операций",
                                containerColor = Color(0xFF181822),
                                iconTint = if (state.activeOperations.isEmpty()) DuroTextSecondary else DuroAmber
                            )
                        }

                        // Settings Button
                        DuroIconActionButton(
                            onClick = { viewModel.openSettings() },
                            icon = Icons.Default.Settings,
                            contentDescription = "Настройки ИИ и виджетов",
                            containerColor = Color(0xFF181822),
                            iconTint = if (!state.hasApiKeysConfigured) DuroOrange else DuroTextSecondary
                        )

                        // Hub: все разделы (статистика, архив, рутины, фокус, бэкап…)
                        DuroIconActionButton(
                            onClick = { viewModel.openScreen(AppScreen.HUB) },
                            icon = Icons.Default.MoreVert,
                            contentDescription = "Ещё разделы",
                            containerColor = Color(0xFF181822),
                            iconTint = DuroTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                DuroHairline()

                // OTA Update banner: появляется автоматически при скачивании или готовности установки
                AnimatedVisibility(
                    visible = updateState is AppUpdateUiState.Downloaded || updateState is AppUpdateUiState.InProgress,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    when (val uState = updateState) {
                        is AppUpdateUiState.Downloaded -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, DuroLime.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                color = Color(0xFF162518)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = DuroLime,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Обновление готово к установке",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = DuroTextPrimary
                                            )
                                        )
                                        Text(
                                            text = "Нажмите для обновления приложения",
                                            style = MaterialTheme.typography.bodySmall.copy(color = DuroTextSecondary)
                                        )
                                    }
                                    Button(
                                        onClick = { githubUpdateController.installDownloaded() },
                                        colors = ButtonDefaults.buttonColors(containerColor = DuroLime, contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Установить", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        is AppUpdateUiState.InProgress -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, DuroCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                color = Color(0xFF121C24)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val progress = if (uState.totalBytes > 0) {
                                        (uState.bytesDownloaded.toFloat() / uState.totalBytes).coerceIn(0f, 1f)
                                    } else 0f
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Загрузка обновления…",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                color = DuroCyan
                                            )
                                        )
                                        if (uState.totalBytes > 0) {
                                            Text(
                                                text = "${(progress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall.copy(color = DuroTextSecondary)
                                            )
                                        }
                                    }
                                    if (uState.totalBytes > 0) {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                            color = DuroCyan,
                                            trackColor = DuroBorder
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                            color = DuroCyan,
                                            trackColor = DuroBorder
                                        )
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                // BIG BOLD DATE HEADER + "+" Add Button (Frame 18: "FEB 18" +)
                // Заголовок занимает гибкую ширину с эллипсисом, кнопка фиксированная.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.dateDisplayString,
                        style = MaterialTheme.typography.displaySmall.copy(
                            color = DuroTextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.weight(1f)
                    )

                    // "+" Add Button (Frame 18)
                    DuroIconActionButton(
                        onClick = { viewModel.openCreateHabit() },
                        icon = Icons.Default.Add,
                        contentDescription = "Add Habit",
                        containerColor = Color(0xFF181822),
                        iconTint = DuroTextPrimary,
                        size = 40.dp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // FILTER TABS: All, D, W, M (Frame 18)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf("All", "D", "W", "M")
                    tabs.forEach { tab ->
                        val isSelected = tab == state.selectedTab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.duroPressable(
                                pressedScale = 0.96f,
                                onClick = {
                                    haptics.select()
                                    viewModel.setSelectedTab(tab)
                                }
                            )
                        ) {
                            Text(
                                text = tab,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = if (isSelected) DuroOrange else DuroTabInactive
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(DuroContentSpring) + scaleIn(
                                    DuroContentSpring,
                                    initialScale = 0.4f
                                ),
                                exit = fadeOut(DuroContentSpring) + scaleOut(
                                    DuroContentSpring,
                                    targetScale = 0.4f
                                ),
                                label = "tabUnderline"
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(DuroOrange)
                                )
                            }
                            if (!isSelected) {
                                Spacer(modifier = Modifier.height(2.5.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // F20/G33: быстрый ввод текстом + поиск.
                QuickAddBar(viewModel = viewModel)
                Spacer(modifier = Modifier.height(10.dp))
                SearchSortRow(viewModel = viewModel, state = state)
                Spacer(modifier = Modifier.height(6.dp))

                // Filtered Habits list based on tab (+ поиск и категория)
                val query = state.searchQuery.trim().lowercase()
                val visibleHabits = remember(state.habits, state.selectedTab, query, state.categoryFilter) {
                    // Страховка: архив и корзина на главной не показываются
                    // (основной фильтр — в HomeViewModel.observeData).
                    val activeHabits = state.habits.filter { it.deletedAt == null && !it.archived }
                    val byTab = when (state.selectedTab) {
                        "D" -> activeHabits.filter { it.frequency == "DAILY" }
                        "W" -> activeHabits.filter { it.frequency == "WEEKLY" || it.displayType == "BAR_GRAPH" }
                        "M" -> activeHabits.filter { it.frequency == "MONTHLY" || it.displayType == "GRID" }
                        else -> activeHabits
                    }
                    byTab.filter { habit ->
                        (query.isBlank() || habit.title.lowercase().contains(query)) &&
                            (state.categoryFilter == null || habit.category == state.categoryFilter)
                    }
                }
                val filteredHabits = visibleHabits
                // G11/G12: видимые задачи с учётом поиска, категории и сортировки.
                val visibleTasks = remember(state.tasks, query, state.categoryFilter, state.taskSort) {
                    val filtered = state.tasks.filter { task ->
                        (query.isBlank() || task.title.lowercase().contains(query)) &&
                            (state.categoryFilter == null || task.category == state.categoryFilter)
                    }
                    when (state.taskSort) {
                        TaskSort.DUE -> filtered.sortedWith(
                            compareBy({ it.dueDateIso == null }, { it.dueDateIso })
                        )
                        TaskSort.PRIORITY -> filtered.sortedBy {
                            when (it.priority) {
                                com.voicehabit.tracker.domain.model.Priority.HIGH -> 0
                                com.voicehabit.tracker.domain.model.Priority.MEDIUM -> 1
                                com.voicehabit.tracker.domain.model.Priority.LOW -> 2
                            }
                        }
                        TaskSort.CREATED -> filtered.sortedByDescending { it.createdAt }
                        TaskSort.MANUAL -> filtered
                    }
                }

                // Banner if API keys are not configured yet
                if (!state.hasApiKeysConfigured) {
                    Surface(
                        color = DuroOrange.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DuroOrange.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .duroPressable(
                                haptic = HapticFeedbackType.TextHandleMove,
                                onClick = { viewModel.openSettings() }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "⚡", fontSize = 16.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Нажмите здесь для настройки API-ключей",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = DuroOrange
                                    )
                                )
                                Text(
                                    text = "Вставьте бесплатные ключи Groq и Gemini для онлайн-распознавания на 4G без ПК",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = DuroTextSecondary
                                    )
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = DuroOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Info banner for voice / AI processing
                val infoMessage = state.infoMessage
                AnimatedVisibility(
                    visible = state.isLoading || infoMessage != null,
                    enter = fadeIn(DuroContentSpring),
                    exit = fadeOut(DuroContentSpring),
                    label = "statusBanner"
                ) {
                    if (state.isLoading) {
                        DuroLoadingBanner(
                            status = infoMessage,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    } else {
                        Surface(
                            color = DuroSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder.copy(alpha = 0.72f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = infoMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = DuroTextSecondary
                                    )
                                )
                            }
                        }
                    }
                }

                // HABIT CONTENT: 2-Column Grid or List View
                AnimatedContent(
                    targetState = state.isGridView,
                    transitionSpec = {
                        (fadeIn(DuroContentSpring) + scaleIn(DuroContentSpring, initialScale = 0.97f))
                            .togetherWith(fadeOut(DuroContentSpring) + scaleOut(DuroContentSpring, targetScale = 0.97f))
                    },
                    label = "habitLayoutMode"
                ) { isGrid ->
                if (isGrid) {
                    // Adaptive вместо Fixed(2): на тонком телефоне (<~360dp)
                    // сетка сама падает в 1 колонку и текст не сплющивается.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredHabits, key = { it.id }) { habit ->
                            DuroHabitGridCard(
                                habit = habit,
                                modifier = Modifier.animateItem(),
                                onToggle = {
                                    haptics.confirm()
                                    viewModel.toggleHabit(habit)
                                    viewModel.showSnackbar(
                                        if (!habit.isCompletedToday) "«${habit.title}» выполнено! 🔥" else "«${habit.title}» отменено"
                                    )
                                },
                                onCardClick = {
                                    haptics.select()
                                    viewModel.openHabitDetail(habit)
                                },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                heroKey = if (selectedHabit?.id == habit.id) selectedHeroKey else null
                            )
                        }

                        // Open Tasks section
                        if (visibleTasks.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                                    Text(
                                        text = "ЗАДАЧИ · $openTaskCount",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = DuroCyan
                                        )
                                    )
                                }
                            }

                            items(visibleTasks, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { task ->
                                SwipeableTaskCard(
                                    task = task,
                                    modifier = Modifier.animateItem(),
                                    onToggle = {
                                        haptics.confirm()
                                        viewModel.completeTask(task)
                                    },
                                    onEdit = { viewModel.openTaskEditor(task) },
                                    onStartFocus = {
                                        viewModel.openFocusForTask(task)
                                    }
                                )
                            }
                        }

                        if (state.completedTasks.isNotEmpty() || state.completedEarlierCount > 0) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CompletedTasksHeader(
                                    completedCount = state.completedTasks.size,
                                    earlierCount = state.completedEarlierCount,
                                    expanded = state.showCompletedTasks,
                                    onToggle = {
                                        haptics.select()
                                        viewModel.toggleCompletedTasksVisibility()
                                    },
                                    modifier = Modifier
                                        .padding(top = 16.dp, bottom = 8.dp)
                                        .animateItem()
                                )
                            }

                            if (state.showCompletedTasks) {
                                items(state.completedTasks, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { task ->
                                    TaskCard(
                                        task = task,
                                        modifier = Modifier.animateItem(),
                                        onToggle = { viewModel.completeTask(task) },
                                        onEdit = { viewModel.openTaskEditor(task) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredHabits, key = { it.id }) { habit ->
                            HabitCard(
                                habit = habit,
                                modifier = Modifier.animateItem(),
                                onToggle = {
                                    haptics.confirm()
                                    viewModel.toggleHabit(habit)
                                    viewModel.showSnackbar(
                                        if (!habit.isCompletedToday) "«${habit.title}» выполнено! 🔥" else "«${habit.title}» отменено"
                                    )
                                },
                                onCardClick = {
                                    haptics.select()
                                    viewModel.openHabitDetail(habit)
                                },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                heroKey = if (selectedHabit?.id == habit.id) selectedHeroKey else null
                            )
                        }

                        if (visibleTasks.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "ЗАДАЧИ · $openTaskCount",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = DuroCyan
                                    )
                                )
                            }

                            items(visibleTasks, key = { it.id }) { task ->
                                SwipeableTaskCard(
                                    task = task,
                                    modifier = Modifier.animateItem(),
                                    onToggle = {
                                        haptics.confirm()
                                        viewModel.completeTask(task)
                                    },
                                    onEdit = { viewModel.openTaskEditor(task) },
                                    onStartFocus = {
                                        viewModel.openFocusForTask(task)
                                    }
                                )
                            }
                        }

                        if (state.completedTasks.isNotEmpty() || state.completedEarlierCount > 0) {
                            item {
                                CompletedTasksHeader(
                                    completedCount = state.completedTasks.size,
                                    earlierCount = state.completedEarlierCount,
                                    expanded = state.showCompletedTasks,
                                    onToggle = {
                                        haptics.select()
                                        viewModel.toggleCompletedTasksVisibility()
                                    },
                                    modifier = Modifier
                                        .padding(top = 16.dp)
                                        .animateItem()
                                )
                            }

                            if (state.showCompletedTasks) {
                                items(state.completedTasks, key = { it.id }) { task ->
                                    TaskCard(
                                        task = task,
                                        modifier = Modifier.animateItem(),
                                        onToggle = { viewModel.completeTask(task) },
                                        onEdit = { viewModel.openTaskEditor(task) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            } // end main Column — pill switcher ниже лежит в BoxScope
            } // end else (экран HOME)

            // Переключатель сетка/список — только на главной.
            if (state.screen == AppScreen.HOME) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            ) {
                Surface(
                    color = Color(0xFF16161E),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Grid Button
                        DuroIconActionButton(
                            onClick = {
                                haptics.select()
                                viewModel.setGridView(true)
                            },
                            icon = Icons.Default.GridView,
                            contentDescription = "Grid View",
                            containerColor = if (state.isGridView) Color(0xFF282838) else Color.Transparent,
                            iconTint = if (state.isGridView) Color.White else DuroTextMuted,
                            size = 36.dp,
                            iconSize = 18.dp
                        )

                        // List Button
                        DuroIconActionButton(
                            onClick = {
                                haptics.select()
                                viewModel.setGridView(false)
                            },
                            icon = Icons.AutoMirrored.Filled.List,
                            contentDescription = "List View",
                            containerColor = if (!state.isGridView) Color(0xFF282838) else Color.Transparent,
                            iconTint = if (!state.isGridView) Color.White else DuroTextMuted,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                    }
                }
            }
            } // end if HOME (pill switcher)

            // Празднование закрытого дня поверх всего (G31).
            if (state.celebrateAllDone) {
                CelebrationOverlay(onDismiss = { viewModel.consumeCelebration() })
            }

            // Онбординг поверх всего при первом запуске (F18).
            if (state.showOnboarding) {
                OnboardingDialog(viewModel = viewModel)
            }

            // Review BottomSheet for voice notes
            state.pendingReviewAction?.let { action ->
                ReviewBottomSheet(
                    action = action,
                    onApply = { updatedAction -> viewModel.applyVoiceAction(updatedAction) },
                    onDismiss = { viewModel.dismissReview() },
                    onSpeak = { text -> viewModel.speak(text) },
                    onOverrideMode = { mode -> viewModel.overrideMode(action, mode) }
                )
            }

            // Settings Dialog
            if (state.isSettingsOpen) {
                SettingsDialog(
                    settingsManager = viewModel.getSettingsManager(),
                    githubUpdateController = githubUpdateController,
                    onDismiss = { viewModel.closeSettings() },
                    viewModel = viewModel
                )
            }

            // Habit Detail BottomSheet
            state.selectedHabitForDetail?.let { habit ->
                HabitDetailBottomSheet(
                    habit = habit,
                    // heroKey намеренно null: sharedBounds между карточкой (окно
                    // активности) и ModalBottomSheet (окно диалога) падает с
                    // "layouts are not part of the same hierarchy".
                    heroKey = null,
                    viewModel = viewModel,
                    onToggleToday = {
                        haptics.confirm()
                        viewModel.toggleHabit(habit)
                        viewModel.showSnackbar(
                            if (!habit.isCompletedToday) "«${habit.title}» выполнено! 🔥" else "«${habit.title}» отменено"
                        )
                    },
                    onDelete = {
                        haptics.reject()
                        viewModel.deleteHabitToTrash(habit.id)
                    },
                    onDismiss = { viewModel.closeHabitDetail() },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }

            // Voice Queue BottomSheet
            if (state.isVoiceQueueOpen) {
                VoiceQueueBottomSheet(
                    logs = state.voiceLogs,
                    onReapply = { log -> viewModel.reapplyVoiceLog(log) },
                    onDismiss = { viewModel.closeVoiceQueue() }
                )
            }

            if (state.isOperationsLogOpen) {
                OperationsLogBottomSheet(
                    activeOperations = state.activeOperations,
                    events = state.logEvents,
                    levelFilter = state.logLevelFilter,
                    onFilterChange = { viewModel.setLogLevelFilter(it) },
                    onClear = { viewModel.clearLog() },
                    onDismiss = { viewModel.closeOperationsLog() }
                )
            }

            // Task Editor BottomSheet (правка названия, типа «быстрая/долгая», приоритета, срока)
            state.selectedTaskForEdit?.let { task ->
                TaskEditorBottomSheet(
                    task = task,
                    onSave = { updated -> viewModel.updateTask(updated) },
                    onToggleCompletion = { viewModel.completeTask(task) },
                    onDelete = { viewModel.deleteTaskToTrash(task) },
                    onDismiss = { viewModel.closeTaskEditor() },
                    viewModel = viewModel
                )
            }
                }
            }
        }
    }
}

/** F20: быстрый ввод текстом — тот же парсер, что и у голоса, но без микрофона. */
@Composable
private fun QuickAddBar(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    var text by remember { mutableStateOf("") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Быстро: «купить молоко завтра»", fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DuroOrange,
                unfocusedBorderColor = DuroBorder,
                focusedTextColor = DuroTextPrimary,
                unfocusedTextColor = DuroTextPrimary
            )
        )
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    viewModel.quickAdd(text)
                    text = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = DuroOrange),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (state.searchQuery.isNotBlank() || state.categoryFilter != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    if (state.searchQuery.isNotBlank()) append("🔍 «${state.searchQuery}» ")
                    state.categoryFilter?.let { append("📁 $it") }
                },
                fontSize = 12.sp,
                color = DuroOrange,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                viewModel.setSearchQuery("")
                viewModel.setCategoryFilter(null)
            }) {
                Text(text = "Сбросить", fontSize = 12.sp, color = DuroTextSecondary)
            }
        }
    }
}

/** G33/G11/G12: поиск, сортировка и фильтр категорий. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSortRow(viewModel: HomeViewModel, state: HomeState) {
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!searchExpanded) {
            IconButton(onClick = { searchExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Поиск",
                    tint = if (state.searchQuery.isBlank()) DuroTextSecondary else DuroOrange
                )
            }
        } else {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Поиск…", fontSize = 13.sp) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        viewModel.setSearchQuery("")
                        searchExpanded = false
                    }) {
                        Text(text = "✕", color = DuroTextSecondary)
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DuroOrange,
                    unfocusedBorderColor = DuroBorder,
                    focusedTextColor = DuroTextPrimary,
                    unfocusedTextColor = DuroTextPrimary
                )
            )
        }
        Box {
            OutlinedButton(
                onClick = { sortExpanded = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when (state.taskSort) {
                        TaskSort.MANUAL -> "⇅ Порядок"
                        TaskSort.DUE -> "⇅ Срок"
                        TaskSort.PRIORITY -> "⇅ Приоритет"
                        TaskSort.CREATED -> "⇅ Новые"
                    },
                    fontSize = 12.sp,
                    color = DuroTextPrimary
                )
            }
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                TaskSort.values().forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.title) },
                        onClick = {
                            viewModel.setTaskSort(sort)
                            sortExpanded = false
                        }
                    )
                }
            }
        }
    }
    val categories = viewModel.allCategories().take(12)
    if (categories.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories.size) { index ->
                val cat = categories[index]
                val selected = state.categoryFilter == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) DuroCyan.copy(alpha = 0.2f) else DuroSurface)
                        .clickable {
                            viewModel.setCategoryFilter(if (selected) null else cat)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat,
                        fontSize = 12.sp,
                        color = if (selected) DuroCyan else DuroTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedTasksHeader(
    completedCount: Int,
    earlierCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .duroPressable(onClick = onToggle)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildString {
                append("ВЫПОЛНЕНО СЕГОДНЯ · $completedCount")
                if (earlierCount > 0) append("  (+$earlierCount ранее)")
            },
            style = MaterialTheme.typography.labelMedium.copy(
                color = DuroLime
            )
        )
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (fadeIn(DuroIconSpring) + scaleIn(DuroIconSpring, initialScale = 0.68f))
                    .togetherWith(fadeOut(DuroContentSpring) + scaleOut(DuroContentSpring, targetScale = 0.68f))
            },
            label = "completedChevronMorph"
        ) { isExpanded ->
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Свернуть выполненные" else "Показать выполненные",
                tint = DuroLime,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
