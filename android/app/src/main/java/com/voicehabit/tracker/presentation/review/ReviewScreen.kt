package com.voicehabit.tracker.presentation.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.hub.ScreenHeader
import com.voicehabit.tracker.presentation.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** F3: вечерний разбор — итог дня, план на завтра, сводка вслух. */
@Composable
fun ReviewScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    var summary by remember { mutableStateOf("") }
    var tomorrowPlan by remember { mutableStateOf("") }

    val zone = ZoneId.systemDefault()
    val doneHabits = state.habits.filter { it.isCompletedToday && it.deletedAt == null && !it.archived }
    val openTasks = state.tasks.filter { !it.isCompleted }
    val doneTasksToday = state.completedTasks
    val tomorrowDate = LocalDate.now().plusDays(1).toString()
    val tomorrowTasks = (state.tasks + state.completedTasks).filter {
        it.dueDateIso?.startsWith(tomorrowDate) == true && !it.isCompleted
    }

    LaunchedEffect(Unit) {
        if (state.daySummary == null) viewModel.buildDaySummary(speakOut = false)
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
        ScreenHeader(title = "Вечерний разбор", subtitle = "Что сделано и что завтра") {
            viewModel.closeScreen()
        }
        Spacer(modifier = Modifier.height(12.dp))

        state.daySummary?.let { text ->
            Surface(
                color = AppTheme.colors.surface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.voicehabit.tracker.R.drawable.ic_journal_night),
                            contentDescription = null,
                            tint = AppTheme.colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Сводка дня",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.accent
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = text, fontSize = 13.sp, color = AppTheme.colors.textPrimary, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    // Прослушка — второстепенная и только при включённом TTS:
                    // компактная строка вместо навязчивой кнопки на всю ширину.
                    if (state.ttsEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable { viewModel.speak(text) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Прослушать сводку",
                                tint = AppTheme.colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Прослушать", color = AppTheme.colors.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        ReviewSection(title = "Привычки сегодня (${doneHabits.size})") {
            if (doneHabits.isEmpty()) Text(text = "Пока ничего не отмечено", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
            doneHabits.forEach { Text(text = "• ${it.title}", fontSize = 13.sp, color = AppTheme.colors.textPrimary) }
        }
        ReviewSection(title = "Задачи закрыты (${doneTasksToday.size})") {
            if (doneTasksToday.isEmpty()) Text(text = "—", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
            doneTasksToday.forEach { Text(text = "• ${it.title}", fontSize = 13.sp, color = AppTheme.colors.textPrimary) }
        }
        ReviewSection(title = "Осталось открыто (${openTasks.size})") {
            if (openTasks.isEmpty()) Text(text = "Всё закрыто", fontSize = 12.sp, color = DairySuccess)
            openTasks.take(8).forEach { Text(text = "• ${it.title}", fontSize = 13.sp, color = AppTheme.colors.textPrimary) }
        }
        ReviewSection(title = "На завтра (${tomorrowTasks.size})") {
            if (tomorrowTasks.isEmpty()) Text(text = "Ничего не запланировано", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
            tomorrowTasks.forEach { Text(text = "• ${it.title}", fontSize = 13.sp, color = AppTheme.colors.textPrimary) }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it },
            label = { Text("Итог дня своими словами", fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.colors.accent,
                unfocusedBorderColor = AppTheme.colors.border,
                cursorColor = AppTheme.colors.accent,
                focusedLabelColor = AppTheme.colors.accent,
                unfocusedLabelColor = AppTheme.colors.textSecondary,
                focusedContainerColor = AppTheme.colors.surface,
                unfocusedContainerColor = AppTheme.colors.surface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = tomorrowPlan,
            onValueChange = { tomorrowPlan = it },
            label = { Text("План на завтра", fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.colors.accent,
                unfocusedBorderColor = AppTheme.colors.border,
                cursorColor = AppTheme.colors.accent,
                focusedLabelColor = AppTheme.colors.accent,
                unfocusedLabelColor = AppTheme.colors.textSecondary,
                focusedContainerColor = AppTheme.colors.surface,
                unfocusedContainerColor = AppTheme.colors.surface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                viewModel.saveReview(summary.ifBlank { "Итог дня" }, tomorrowPlan)
                summary = ""
                tomorrowPlan = ""
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = AppTheme.colors.accent,
                contentColor = AppTheme.colors.onAccent
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить разбор", fontWeight = FontWeight.Bold)
        }
        if (state.reviews.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Прошлые разборы", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            state.reviews.take(7).forEach { (day, text) ->
                val date = LocalDate.ofEpochDay(day).toString()
                Text(text = "$date — $text", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
private fun ReviewSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.accent)
    Spacer(modifier = Modifier.height(6.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, AppTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        content = content
    )
    Spacer(modifier = Modifier.height(12.dp))
}
