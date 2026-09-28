package com.voicehabit.tracker.presentation.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.core.update.AppUpdateUiState
import com.voicehabit.tracker.core.update.github.GithubUpdateController
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.presentation.achievements.AchievementsScreen
import com.voicehabit.tracker.presentation.archive.ArchiveScreen
import com.voicehabit.tracker.presentation.backup.BackupScreen
import com.voicehabit.tracker.presentation.challenges.ChallengesScreen
import com.voicehabit.tracker.presentation.create.CreateHabitScreen
import com.voicehabit.tracker.presentation.digests.DigestsScreen
import com.voicehabit.tracker.presentation.focus.FocusScreen
import com.voicehabit.tracker.presentation.home.components.DuroHabitGridCard
import com.voicehabit.tracker.presentation.home.components.HabitCard
import com.voicehabit.tracker.presentation.home.components.HabitDetailBottomSheet
import com.voicehabit.tracker.presentation.home.components.OperationsLogBottomSheet
import com.voicehabit.tracker.presentation.home.components.SettingsDialog
import com.voicehabit.tracker.presentation.home.components.SwipeableTaskCard
import com.voicehabit.tracker.presentation.home.components.TaskEditorBottomSheet
import com.voicehabit.tracker.presentation.home.components.VoiceQueueBottomSheet
import com.voicehabit.tracker.presentation.hub.AboutScreen
import com.voicehabit.tracker.presentation.hub.CelebrationOverlay
import com.voicehabit.tracker.presentation.hub.HubScreen
import com.voicehabit.tracker.presentation.journal.JournalScreen
import com.voicehabit.tracker.presentation.navigation.DuroBottomNavigation
import com.voicehabit.tracker.presentation.onboarding.OnboardingDialog
import com.voicehabit.tracker.presentation.overview.OverviewScreen
import com.voicehabit.tracker.presentation.profile.ProfileMenuSheet
import com.voicehabit.tracker.presentation.review.ReviewScreen
import com.voicehabit.tracker.presentation.routines.RoutinesScreen
import com.voicehabit.tracker.presentation.stats.StatsScreen
import com.voicehabit.tracker.presentation.theme.*
import com.voicehabit.tracker.presentation.voice.ReviewBottomSheet
import com.voicehabit.tracker.presentation.voice.VoiceRecordFab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    githubUpdateController: GithubUpdateController,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val updateState by githubUpdateController.state.collectAsState()
    val haptics = rememberDuroHaptics()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    if (state.isCreateHabitOpen) {
        CreateHabitScreen(
            onDismiss = { viewModel.closeCreateHabit() },
            onSaveHabit = { habit -> viewModel.addHabit(habit) },
            onSaveTask = { task -> viewModel.addTaskWithReminder(task) },
            viewModel = viewModel
        )
        return
    }

    LaunchedEffect(state.infoMessage, state.errorMessage, state.snackbarMessage) {
        val text = state.errorMessage ?: state.snackbarMessage ?: state.infoMessage
        if (!text.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = text, withDismissAction = true)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        containerColor = DuroBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            )
        },
        bottomBar = {
            if (state.screen == AppScreen.HOME) {
                DuroBottomNavigation(
                    selectedTab = state.selectedMainTab,
                    onTabSelected = { tab ->
                        haptics.select()
                        viewModel.setSelectedMainTab(tab)
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        },
        floatingActionButton = {
            if (state.screen == AppScreen.HOME) {
                VoiceRecordFab(
                    isRecordingFlow = viewModel.audioRecorder.isRecording,
                    durationSecondsFlow = viewModel.audioRecorder.recordDurationSeconds,
                    amplitudeFlow = viewModel.audioRecorder.amplitudeNormalized,
                    onStartRecord = { viewModel.startRecording() },
                    onStopRecord = { viewModel.stopRecording() },
                    onCancelRecord = { viewModel.cancelRecording() },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Start
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Fullscreen sub-screen overlay routing
            BackHandler(enabled = state.screen != AppScreen.HOME) {
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
                    AppScreen.JOURNAL -> JournalScreen(viewModel = viewModel)
                    AppScreen.HOME -> Unit
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    // CALM TOP BAR: Minimalist Asterisk Logo + Progress + Profile Icon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DuroAsterisk(size = 20.dp, color = DuroOrange)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Reached ${state.yearProgressPercentage}% of 2026",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = DuroTextSecondary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.activeOperations.isNotEmpty()) {
                                Surface(
                                    color = DuroAmber.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DuroAmber.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable { viewModel.openOperationsLog() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            color = DuroAmber,
                                            strokeWidth = 1.5.dp
                                        )
                                        Text(
                                            text = "${state.activeOperations.size}",
                                            fontSize = 11.sp,
                                            color = DuroAmber,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Profile / System Menu Button (комфортная зона нажатия 48x48dp)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.select()
                                        viewModel.openProfileMenu()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(DuroSurfaceElevated)
                                        .border(1.dp, DuroBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonOutline,
                                        contentDescription = "Профиль и система",
                                        tint = if (!state.hasApiKeysConfigured) DuroOrange else DuroTextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // OTA Update banner
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
                                        .padding(horizontal = 20.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, DuroLime.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .clickable { githubUpdateController.installDownloaded() },
                                    color = Color(0xFF162518)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, tint = DuroLime)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "Обновление готово к установке", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroTextPrimary)
                                            Text(text = "Нажмите для обновления", fontSize = 11.sp, color = DuroLime)
                                        }
                                    }
                                }
                            }
                            is AppUpdateUiState.InProgress -> {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    color = DuroSurfaceElevated
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DuroOrange, strokeWidth = 2.dp)
                                        Text(text = "Загрузка обновления...", fontSize = 12.sp, color = DuroTextSecondary)
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }

                    // Info banner for voice / AI processing
                    if (state.infoMessage != null) {
                        Surface(
                            color = DuroSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = DuroOrange,
                                        strokeWidth = 2.dp
                                    )
                                }
                                Text(
                                    text = state.infoMessage ?: "",
                                    fontSize = 12.sp,
                                    color = DuroTextSecondary
                                )
                            }
                        }
                    }

                    // Плавный переход между табами без конфликтов жестов и свайпов
                    AnimatedContent(
                        targetState = state.selectedMainTab,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                (slideInHorizontally { width -> width / 5 } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> -width / 5 } + fadeOut()
                                )
                            } else {
                                (slideInHorizontally { width -> -width / 5 } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> width / 5 } + fadeOut()
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "MainTabTransition"
                    ) { tab ->
                        when (tab) {
                            MainTab.RHYTHM -> RhythmView(viewModel = viewModel)
                            MainTab.JOURNAL -> JournalScreen(viewModel = viewModel)
                            MainTab.OVERVIEW -> OverviewScreen(viewModel = viewModel)
                        }
                    }
                }

                // FLOATING PILL SWITCHER: Grid vs List (Only visible on Rhythm tab)
                if (state.selectedMainTab == MainTab.RHYTHM) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 80.dp)
                    ) {
                        Surface(
                            color = Color(0xFF161622),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        haptics.select()
                                        viewModel.setGridView(true)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (state.isGridView) Color(0xFF282838) else Color.Transparent)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Сетка",
                                        tint = if (state.isGridView) Color.White else DuroTextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptics.select()
                                        viewModel.setGridView(false)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (!state.isGridView) Color(0xFF282838) else Color.Transparent)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.List,
                                        contentDescription = "Список",
                                        tint = if (!state.isGridView) Color.White else DuroTextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // MODALS & SHEETS
            state.pendingReviewAction?.let { action ->
                ReviewBottomSheet(
                    action = action,
                    onApply = { updatedAction -> viewModel.applyVoiceAction(updatedAction) },
                    onDismiss = { viewModel.dismissReview() },
                    onOverrideMode = { mode -> viewModel.overrideMode(action, mode) },
                    onSpeak = { text -> viewModel.speak(text) }
                )
            }

            if (state.isProfileMenuOpen) {
                ProfileMenuSheet(
                    viewModel = viewModel,
                    onDismiss = { viewModel.closeProfileMenu() }
                )
            }

            if (state.isObsidianSyncSheetOpen) {
                com.voicehabit.tracker.presentation.profile.ObsidianSyncSheet(
                    viewModel = viewModel,
                    onDismiss = { viewModel.closeObsidianSyncSheet() }
                )
            }

            if (state.isSettingsOpen) {
                SettingsDialog(
                    settingsManager = viewModel.getSettingsManager(),
                    githubUpdateController = githubUpdateController,
                    onDismiss = { viewModel.closeSettings() },
                    viewModel = viewModel
                )
            }

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

            state.selectedHabitForDetail?.let { habit ->
                HabitDetailBottomSheet(
                    habit = habit,
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
                    onDismiss = { viewModel.closeHabitDetail() }
                )
            }

            state.selectedTaskForEdit?.let { task ->
                TaskEditorBottomSheet(
                    task = task,
                    onSave = { updated -> viewModel.updateTask(updated) },
                    onToggleCompletion = { viewModel.completeTask(task) },
                    onDelete = { viewModel.deleteTask(task) },
                    onDismiss = { viewModel.closeTaskEditor() },
                    viewModel = viewModel
                )
            }

            if (state.showOnboarding) {
                OnboardingDialog(viewModel = viewModel)
            }

            if (state.celebrateAllDone) {
                CelebrationOverlay(
                    onDismiss = { viewModel.consumeCelebration() }
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RhythmView(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val haptics = rememberDuroHaptics()
    var searchExpanded by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }

    val filteredHabits = remember(state.habits, state.selectedTab) {
        when (state.selectedTab) {
            "D" -> state.habits.filter { it.frequency == "DAILY" }
            "W" -> state.habits.filter { it.frequency == "WEEKLY" || it.displayType == "BAR_GRAPH" }
            "M" -> state.habits.filter { it.frequency == "MONTHLY" || it.displayType == "GRID" }
            else -> state.habits
        }
    }

    val displayTasks = remember(state.tasks, state.searchQuery, state.categoryFilter) {
        state.tasks.filter { task ->
            val matchesQuery = state.searchQuery.isBlank() || task.title.contains(state.searchQuery, ignoreCase = true)
            val matchesCat = state.categoryFilter == null || task.category == state.categoryFilter
            matchesQuery && matchesCat
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Date Header + Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.dateDisplayString,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DuroTextPrimary,
                letterSpacing = 0.5.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Toggle (touch target 44x44dp)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptics.select()
                            searchExpanded = !searchExpanded
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (searchExpanded) DuroOrange.copy(alpha = 0.15f) else Color(0xFF181822)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск",
                            tint = if (searchExpanded) DuroOrange else DuroTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Add Habit / Task (touch target 44x44dp)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptics.confirm()
                            viewModel.openCreateHabit()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF181822)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Создать привычку или задачу",
                            tint = DuroTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Habit Frequency Tabs: All, D, W, M с комфортной зоной нажатия
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf("All", "D", "W", "M")
            tabs.forEach { tab ->
                val isSelected = tab == state.selectedTab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.select()
                            viewModel.setSelectedTab(tab)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tab,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) DuroOrange else DuroTabInactive
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(DuroOrange)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.5.dp))
                    }
                }
            }
        }

        // Expandable Search & Categories
        AnimatedVisibility(
            visible = searchExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Поиск задач и дел…", fontSize = 13.sp, color = DuroTextMuted) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить", tint = DuroTextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DuroOrange,
                        unfocusedBorderColor = DuroBorder,
                        focusedTextColor = DuroTextPrimary,
                        unfocusedTextColor = DuroTextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                val categories = viewModel.allCategories().take(8)
                if (categories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            val selected = state.categoryFilter == cat
                            Surface(
                                color = if (selected) DuroCyan.copy(alpha = 0.2f) else DuroSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) DuroCyan else DuroBorder),
                                modifier = Modifier.clickable {
                                    haptics.select()
                                    viewModel.setCategoryFilter(if (selected) null else cat)
                                }
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    color = if (selected) DuroCyan else DuroTextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Habit & Task Content: Grid or List
        if (state.isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Habits
                items(filteredHabits, key = { it.id }) { habit ->
                    DuroHabitGridCard(
                        habit = habit,
                        onToggle = {
                            haptics.confirm()
                            viewModel.toggleHabit(habit)
                        },
                        onCardClick = { viewModel.openHabitDetail(habit) }
                    )
                }

                // Open Tasks
                if (displayTasks.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = "ОТКРЫТЫЕ ЗАДАЧИ · ${displayTasks.size}".uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroCyan,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }

                    items(displayTasks, key = { it.id }, span = { GridItemSpan(2) }) { task ->
                        SwipeableTaskCard(
                            task = task,
                            onToggle = {
                                haptics.confirm()
                                viewModel.completeTask(task)
                            },
                            onEdit = { viewModel.openTaskEditor(task) }
                        )
                    }
                }

                // Completed Tasks
                if (state.completedTasks.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        CompletedTasksHeader(
                            completedCount = state.completedTasks.size,
                            earlierCount = state.completedEarlierCount,
                            expanded = showCompleted,
                            onToggle = {
                                haptics.select()
                                showCompleted = !showCompleted
                            }
                        )
                    }

                    if (showCompleted) {
                        items(state.completedTasks, key = { it.id }, span = { GridItemSpan(2) }) { task ->
                            SwipeableTaskCard(
                                task = task,
                                onToggle = {
                                    haptics.confirm()
                                    viewModel.completeTask(task)
                                },
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
                // Habits
                items(filteredHabits, key = { it.id }) { habit ->
                    HabitCard(
                        habit = habit,
                        onToggle = {
                            haptics.confirm()
                            viewModel.toggleHabit(habit)
                        },
                        onCardClick = { viewModel.openHabitDetail(habit) }
                    )
                }

                // Open Tasks
                if (displayTasks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "ОТКРЫТЫЕ ЗАДАЧИ · ${displayTasks.size}".uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroCyan,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(displayTasks, key = { it.id }) { task ->
                        SwipeableTaskCard(
                            task = task,
                            onToggle = {
                                haptics.confirm()
                                viewModel.completeTask(task)
                            },
                            onEdit = { viewModel.openTaskEditor(task) }
                        )
                    }
                }

                // Completed Tasks
                if (state.completedTasks.isNotEmpty()) {
                    item {
                        CompletedTasksHeader(
                            completedCount = state.completedTasks.size,
                            earlierCount = state.completedEarlierCount,
                            expanded = showCompleted,
                            onToggle = {
                                haptics.select()
                                showCompleted = !showCompleted
                            }
                        )
                    }

                    if (showCompleted) {
                        items(state.completedTasks, key = { it.id }) { task ->
                            SwipeableTaskCard(
                                task = task,
                                onToggle = {
                                    haptics.confirm()
                                    viewModel.completeTask(task)
                                },
                                onEdit = { viewModel.openTaskEditor(task) }
                            )
                        }
                    }
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
