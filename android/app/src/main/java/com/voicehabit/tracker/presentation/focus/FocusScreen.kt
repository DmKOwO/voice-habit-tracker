package com.voicehabit.tracker.presentation.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.domain.model.FocusStartAction
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.hub.ScreenHeader
import com.voicehabit.tracker.presentation.theme.*

/**
 * F8/G4: Режим Адаптивного Спринта и Flow Engine.
 * Калибровка под состояние, Just-In-Time атомизация шагов, Focus Guard при паузе и Obsidian-дебрифинг.
 */
@Composable
fun FocusScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    var label by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(25) }
    var taskId by remember { mutableStateOf<String?>(null) }
    var habitId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.focusRequest) {
        state.focusRequest?.let { request: FocusStartAction ->
            minutes = request.minutes
            label = request.label
        }
    }

    LaunchedEffect(state.focusDraft) {
        state.focusDraft?.let { draft ->
            minutes = draft.minutes
            label = draft.label
            taskId = draft.taskId
            habitId = draft.habitId
        }
    }

    val run = state.focusRun
    val debrief = state.focusDebriefRun

    // ДЕБРИФИНГ В КОНЦЕ СЕССИИ (Obsidian Synthesis)
    if (debrief != null) {
        FocusDebriefDialog(
            debrief = debrief,
            onSave = { notes -> viewModel.saveFocusDebriefToObsidian(notes) },
            onDismiss = { viewModel.dismissFocusDebrief() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(AppTheme.colors.background)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(
            title = "Фокус-спринт",
            subtitle = "Flow Engine • Атомарные шаги • Obsidian Vault"
        ) {
            viewModel.closeScreen()
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (run != null) {
            RunningFocusSession(
                run = run,
                viewModel = viewModel
            )
        } else {
            // HERO КАРТОЧКА: АДАПТИВНЫЙ СПРИНТ (FLOW ENGINE)
            Surface(
                color = AppTheme.colors.surface,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.accent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = AppTheme.colors.accent.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = AppTheme.colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "Адаптивный спринт (Flow Engine)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                    }

                    Text(
                        text = "Автоматическая калибровка под ментальное состояние. Ведёт через микро-шаги по 5–7 минут. По завершении — дебрифинг в Obsidian.",
                        fontSize = 12.sp,
                        color = AppTheme.colors.textSecondary,
                        lineHeight = 17.sp
                    )

                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Над чем работаем", fontSize = 12.sp, color = AppTheme.colors.textSecondary) },
                        placeholder = { Text("Например: Дописать архитектурный модуль", fontSize = 13.sp, color = AppTheme.colors.textMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = duroTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            viewModel.startAdaptiveFocus(
                                label.ifBlank { "Глубокая работа" },
                                taskId,
                                habitId
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.accent,
                            contentColor = AppTheme.colors.onAccent
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Запустить Адаптивный Спринт",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // РУЧНАЯ НАСТРОЙКА ТАЙМЕРА
            Text(
                text = "Или классический таймер",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 25, 50, 90).forEach { option ->
                    val selected = minutes == option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) AppTheme.colors.accent.copy(alpha = 0.2f) else AppTheme.colors.surface)
                            .border(
                                width = 1.dp,
                                color = if (selected) AppTheme.colors.accent else AppTheme.colors.border,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { minutes = option }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$option мин",
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) AppTheme.colors.accent else AppTheme.colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Привязать к задаче:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            TaskPicker(
                items = state.tasks.map { it.id to it.title },
                selected = taskId,
                onSelect = { taskId = it; if (it != null) habitId = null }
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "Или к привычке:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            TaskPicker(
                items = state.habits.filter { it.deletedAt == null && !it.archived }.map { it.id to it.title },
                selected = habitId,
                onSelect = { habitId = it; if (it != null) taskId = null }
            )

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    viewModel.startFocus(
                        minutes,
                        label.ifBlank { "Фокус-сессия" },
                        taskId,
                        habitId
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Запустить таймер на $minutes мин", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
            }
        }

        if (state.focusSessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Недавние сессии", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            state.focusSessions.take(8).forEach { session ->
                Surface(
                    color = AppTheme.colors.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (session.completed) Icons.Default.CheckCircle else Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = if (session.completed) AppTheme.colors.accent else AppTheme.colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = session.label.ifBlank { "Фокус" },
                                fontSize = 13.sp,
                                color = AppTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(text = "${session.durationMin} мин", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

/**
 * Активная фокус-сессия с поддержкой Flow Engine и Focus Guard.
 */
@Composable
private fun RunningFocusSession(
    run: com.voicehabit.tracker.presentation.home.FocusRun,
    viewModel: HomeViewModel
) {
    val mm = run.remainingSec / 60
    val ss = run.remainingSec % 60

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.border, RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок сессии
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (run.isAdaptiveMicroSprint) AppTheme.colors.accent.copy(alpha = 0.15f) else AppTheme.colors.surfaceElevated,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (run.isAdaptiveMicroSprint) "АДАПТИВНЫЙ СПРИНТ" else "ФОКУС-СЕССИЯ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (run.isAdaptiveMicroSprint) AppTheme.colors.accent else AppTheme.colors.textSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (run.isPaused) {
                Text(text = "ПАУЗА", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.accentSecondary)
            }
        }

        Text(
            text = run.label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textPrimary
        )

        // Цифровой таймер
        Text(
            text = "%02d:%02d".format(mm, ss),
            fontSize = 54.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (run.isPaused) AppTheme.colors.textMuted else AppTheme.colors.accent,
            letterSpacing = 2.sp
        )

        LinearProgressIndicator(
            progress = { run.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = AppTheme.colors.accent,
            trackColor = AppTheme.colors.border
        )

        // ДИНАМИЧЕСКАЯ АТОМИЗАЦИЯ (JUST-IN-TIME CHUNKING)
        if (run.isAdaptiveMicroSprint || run.currentStep != null) {
            Surface(
                color = AppTheme.colors.surfaceElevated,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ТЕКУЩИЙ ШАГ (5–7 МИН)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.accent,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = run.currentStep ?: run.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppTheme.colors.textPrimary,
                        lineHeight = 20.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.completeCurrentFocusStep() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.accent,
                                contentColor = AppTheme.colors.onAccent
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("Сделано", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.overrideCurrentFocusStep() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("Другой шаг", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }

        // FOCUS GUARD OVERLAY ПРИ ПАУЗЕ
        if (run.pauseReasonPrompt) {
            Surface(
                color = AppTheme.colors.surfaceElevated,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.accent.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Голосовой штурман: причина паузы",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "Помочь продолжить движение или подождать?",
                        fontSize = 11.sp,
                        color = AppTheme.colors.textSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.resolveFocusBlocker() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent, contentColor = AppTheme.colors.onAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Затык в задаче", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.confirmDistraction() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Отвлекли", fontSize = 11.sp, color = AppTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }

        // КНОПКИ УПРАВЛЕНИЯ ТАЙМЕРОМ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (run.isPaused) {
                Button(
                    onClick = { viewModel.resumeFocus() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent, contentColor = AppTheme.colors.onAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("Продолжить", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.pauseFocus() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("Пауза", fontSize = 13.sp, color = AppTheme.colors.textPrimary)
                }
            }

            OutlinedButton(
                onClick = { viewModel.cancelFocus() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Text("Завершить", fontSize = 13.sp, color = AppTheme.colors.error)
            }
        }
    }
}

/**
 * Диалог дебрифинга и синхронизации с Obsidian Vault.
 */
@Composable
private fun FocusDebriefDialog(
    debrief: com.voicehabit.tracker.presentation.home.FocusRun,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Спринт завершён", fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                Text(
                    text = "${debrief.totalSec / 60} мин • Шагов выполнено: ${debrief.completedSteps.size}",
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Зафиксируйте результат или инсайты (сформирует отчёт в Obsidian):",
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Что получилось сделать, ключевые выводы...", fontSize = 12.sp, color = AppTheme.colors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    colors = duroTextFieldColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(notes) },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent, contentColor = AppTheme.colors.onAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("В Obsidian", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Пропустить", color = AppTheme.colors.textSecondary)
            }
        },
        containerColor = AppTheme.colors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun TaskPicker(
    items: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = items.find { it.first == selected }?.second ?: "— не выбрано —"
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = selectedTitle, color = AppTheme.colors.textPrimary, fontSize = 13.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppTheme.colors.surface)
        ) {
            DropdownMenuItem(
                text = { Text("— не выбрано —", color = AppTheme.colors.textSecondary) },
                onClick = { onSelect(null); expanded = false }
            )
            items.take(20).forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title.take(40), color = AppTheme.colors.textPrimary) },
                    onClick = { onSelect(id); expanded = false }
                )
            }
        }
    }
}
