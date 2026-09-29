package com.voicehabit.tracker.presentation.home.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HabitDetailBottomSheet(
    habit: Habit,
    onToggleToday: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    heroKey: String? = null,
    viewModel: HomeViewModel? = null
) {
    val accentColor = try {
        Color(android.graphics.Color.parseColor(habit.colorHex))
    } catch (e: Exception) {
        DuroOrange
    }
    val deleteInteractions = remember(habit.id) { MutableInteractionSource() }
    val actionInteractions = remember(habit.id, habit.isCompletedToday) { MutableInteractionSource() }
    // Подтверждение удаления: раньше тап по корзине стирал привычку с первого
    // нажатия без единого вопроса.
    var showDeleteConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить привычку?") },
            text = { Text("«${habit.title}» уйдёт вместе со всей историей отметок. Это нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Удалить", color = DairyDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Оставить") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101016),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .duroHero(
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        key = heroKey
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = DuroTextPrimary
                        )
                    )
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    interactionSource = deleteInteractions,
                    modifier = Modifier.duroPressScale(deleteInteractions, pressedScale = 0.88f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = DuroTextMuted
                    )
                }
            }

            // Category & Quote
            if (habit.quote.isNotBlank()) {
                Text(
                    text = "«${habit.quote}»",
                    fontSize = 13.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = DuroTextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Семантика привычки: норма, воздержание, цель на неделю.
            run {
                val tagsCsv = habit.tags.joinToString(",")
                val avoid = com.voicehabit.tracker.core.analysis.HabitTagCodec.isAvoidTags(tagsCsv)
                val perWeek = com.voicehabit.tracker.core.analysis.HabitTagCodec.weeklyTargetTags(tagsCsv)
                val norm = buildString {
                    if (habit.targetValue != 1.0 || habit.unit != null) {
                        append("Норма: ${habit.targetValue}" + (habit.unit?.let { " $it" } ?: ""))
                    }
                    if (avoid) { if (isNotEmpty()) append(" · "); append("Воздержание") }
                    if (perWeek > 0) { if (isNotEmpty()) append(" · "); append("Цель: $perWeek раз в неделю") }
                }
                if (norm.isNotEmpty()) {
                    Text(
                        text = norm,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }

            // Stats Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Streak Card
                Surface(
                    color = DuroSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = DuroOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Стрик", fontSize = 11.sp, color = DuroTextMuted)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${habit.currentStreak} дн.",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroTextPrimary
                        )
                        Text(
                            text = "Рекорд: ${maxOf(habit.bestStreak, habit.currentStreak)}",
                            fontSize = 11.sp,
                            color = DuroTextSecondary
                        )
                    }
                }

                // Total Completions
                Surface(
                    color = DuroSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    val completedCount = habit.historyDaysCompleted.count { it }
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Всего дней", fontSize = 11.sp, color = DuroTextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$completedCount из 28",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }

                // Completion Rate
                Surface(
                    color = DuroSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Успех", fontSize = 11.sp, color = DuroTextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${habit.completionPercentage}%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroLime
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 28-day Heatmap Title
            Text(
                text = "ИСТОРИЯ ЗА 28 ДНЕЙ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DuroTextMuted,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4x7 Interactive Heatmap Grid
            Surface(
                color = DuroSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val history = habit.historyDaysCompleted.ifEmpty { List(28) { false } }
                    val today = LocalDate.now()
                    val startDay = today.minusDays(27)

                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (col in 0 until 7) {
                                val idx = row * 7 + col
                                val isDone = if (idx < history.size) history[idx] else false
                                val dayDate = startDay.plusDays(idx.toLong())
                                val isToday = dayDate.isEqual(today)

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isDone) accentColor
                                            else Color(0xFF16161F)
                                        )
                                        .border(
                                            width = if (isToday) 2.dp else 1.dp,
                                            color = if (isToday) DuroOrange else DuroBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${dayDate.dayOfMonth}",
                                        fontSize = 11.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isDone) Color.White else DuroTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (viewModel != null) {
                // F4/F6/G19/G20/G9: расписание, напоминание, заморозка, закреп, архив.
                Text(
                    text = "УПРАВЛЕНИЕ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DuroTextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(text = "Дни недели", fontSize = 12.sp, color = DuroTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                    (1..7).forEach { day ->
                        val selected = day in habit.scheduleDays
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) accentColor.copy(alpha = 0.25f) else DuroSurface)
                                .border(1.dp, if (selected) accentColor else DuroBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    val updated = if (selected) habit.scheduleDays - day else habit.scheduleDays + day
                                    if (updated.isNotEmpty()) viewModel.setHabitSchedule(habit.id, updated)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayNames[day - 1],
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) DuroTextPrimary else DuroTextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Напоминание", fontSize = 12.sp, color = DuroTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "Нет", 480 to "08:00", 720 to "12:00", 1080 to "18:00", 1260 to "21:00").forEach { (option, label) ->
                        val selected = habit.reminderMin == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) DuroAmber.copy(alpha = 0.2f) else DuroSurface)
                                .border(1.dp, if (selected) DuroAmber else DuroBorder, RoundedCornerShape(8.dp))
                                .clickable { viewModel.setHabitReminder(habit.id, option) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = if (selected) DuroAmber else DuroTextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { viewModel.freezeToday() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Заморозить день", fontSize = 12.sp, color = AppTheme.colors.textPrimary)
                    }
                    OutlinedButton(
                        onClick = { viewModel.repairYesterday() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отметить вчера", fontSize = 12.sp, color = AppTheme.colors.textPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { viewModel.togglePinHabit(habit) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (habit.pinned) "Открепить" else "Закрепить",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textPrimary
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.archiveHabit(habit.id, !habit.archived)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (habit.archived) "Вернуть из архива" else "В архив",
                            fontSize = 12.sp,
                            color = DuroTextPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { viewModel.cloneHabit(habit) },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Дублировать", fontSize = 12.sp, color = AppTheme.colors.textPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.startFocus(25, habit.title, habitId = habit.id)
                            viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.FOCUS)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Фокус 25 мин", fontSize = 12.sp, color = AppTheme.colors.textPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action: Mark completed for today
            Button(
                onClick = onToggleToday,
                interactionSource = actionInteractions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .duroPressScale(actionInteractions, pressedScale = 0.97f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (habit.isCompletedToday) Color(0xFF242432) else accentColor
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (habit.isCompletedToday) "Снять отметку за сегодня" else "Отметить выполненным за сегодня",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
