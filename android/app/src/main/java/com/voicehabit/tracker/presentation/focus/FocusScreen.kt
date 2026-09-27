package com.voicehabit.tracker.presentation.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.domain.model.FocusStartAction
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.hub.ScreenHeader
import com.voicehabit.tracker.presentation.theme.*

/** F8/G4: Pomodoro-таймер с привязкой к задаче/привычке. */
@Composable
fun FocusScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    var label by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(25) }
    var taskId by remember { mutableStateOf<String?>(null) }
    var habitId by remember { mutableStateOf<String?>(null) }

    // Голосовой запрос фокуса подставляет значения (G4).
    LaunchedEffect(state.focusRequest) {
        state.focusRequest?.let { request: FocusStartAction ->
            minutes = request.minutes
            label = request.label
        }
    }
    // Черновик из карточки задачи (G10).
    LaunchedEffect(state.focusDraft) {
        state.focusDraft?.let { draft ->
            minutes = draft.minutes
            label = draft.label
            taskId = draft.taskId
            habitId = draft.habitId
        }
    }

    val run = state.focusRun

    Column(
        modifier = Modifier
            .fillMaxSize()
        .statusBarsPadding()
            .statusBarsPadding()
            .background(DuroBackground)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(title = "Фокус", subtitle = "Одна задача — один таймер") {
            viewModel.closeScreen()
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (run != null) {
            RunningTimer(run = run, onCancel = { viewModel.cancelFocus() })
        } else {
            Text(text = "Длительность", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 25, 50, 90).forEach { option ->
                    val selected = minutes == option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) DuroOrange.copy(alpha = 0.25f) else DuroSurface)
                            .clickable { minutes = option }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$option",
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) DuroOrange else DuroTextPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Над чем работаем", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Привязать к задаче:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
            TaskPicker(
                items = state.tasks.map { it.id to it.title },
                selected = taskId,
                onSelect = { taskId = it; if (it != null) habitId = null }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Или к привычке:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
            TaskPicker(
                items = state.habits.filter { it.deletedAt == null && !it.archived }.map { it.id to it.title },
                selected = habitId,
                onSelect = { habitId = it; if (it != null) taskId = null }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.startFocus(
                        minutes,
                        label.ifBlank { "Фокус-сессия" },
                        taskId,
                        habitId
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = DuroOrange),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("▶ Начать $minutes мин")
            }
        }

        if (state.focusSessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "Недавние сессии", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            state.focusSessions.take(8).forEach { session ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = (if (session.completed) "✅ " else "⏸ ") +
                            session.label.ifBlank { "Фокус" },
                        fontSize = 13.sp,
                        color = DuroTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "${session.durationMin} мин", fontSize = 12.sp, color = DuroTextSecondary)
                }
            }
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
private fun RunningTimer(
    run: com.voicehabit.tracker.presentation.home.FocusRun,
    onCancel: () -> Unit
) {
    val mm = run.remainingSec / 60
    val ss = run.remainingSec % 60
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DuroSurface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = run.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DuroTextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "%02d:%02d".format(mm, ss),
            fontSize = 56.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DuroOrange
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { run.progress },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = DuroOrange,
            trackColor = DuroBorder
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Отменить", color = DuroTextPrimary)
        }
    }
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
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(text = selectedTitle, color = DuroTextPrimary, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("— не выбрано —") },
                onClick = { onSelect(null); expanded = false }
            )
            items.take(20).forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title.take(40)) },
                    onClick = { onSelect(id); expanded = false }
                )
            }
        }
    }
}
