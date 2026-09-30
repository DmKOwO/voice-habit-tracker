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
import androidx.compose.runtime.LaunchedEffect
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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
    var localScheduleDays by remember(habit.id, habit.scheduleDays) {
        mutableStateOf(habit.scheduleDays.ifEmpty { (1..7).toSet() })
    }
    val today = remember { LocalDate.now() }
    // Календарный месяц вместо фиксированных 28 дней: сетка показывает 28–31
    // клетку в зависимости от месяца и меняется каждый месяц.
    var displayedMonth by remember(habit.id) { mutableStateOf(YearMonth.now()) }
    var monthEpochs by remember(habit.id) { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(habit.id, displayedMonth) {
        monthEpochs = viewModel?.habitMonthCompletions(habit.id, displayedMonth) ?: emptySet()
    }
    val createdEpoch = remember(habit.id) {
        Instant.ofEpochMilli(habit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }
    val monthStart = remember(displayedMonth) { displayedMonth.atDay(1).toEpochDay() }
    val monthEnd = remember(displayedMonth) { displayedMonth.atEndOfMonth().toEpochDay() }
    val stats = remember(monthEpochs, localScheduleDays, displayedMonth) {
        com.voicehabit.tracker.core.analysis.HabitStats.windowCompletion(
            logEpochDays = monthEpochs,
            scheduleDays = localScheduleDays,
            windowStartEpochDay = monthStart,
            windowEndEpochDay = monthEnd,
            createdEpochDay = createdEpoch,
            todayEpochDay = today.toEpochDay()
        )
    }
    val scheduledCount = stats.scheduled
    val completedScheduledCount = stats.completed
    val completionPercentage = stats.percentage
    // Предложный падеж для "в ...": java.time его не дает, поэтому карта вручную.
    val monthLabel = remember(displayedMonth) {
        val prepositional = mapOf(
            1 to "январе", 2 to "феврале", 3 to "марте", 4 to "апреле",
            5 to "мае", 6 to "июне", 7 to "июле", 8 to "августе",
            9 to "сентябре", 10 to "октябре", 11 to "ноябре", 12 to "декабре"
        )
        val nominative = displayedMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
        MonthLabels(
            nominative = nominative.replaceFirstChar { it.uppercase() } + " " + displayedMonth.year,
            prepositional = prepositional.getValue(displayedMonth.monthValue)
        )
    }
    val scheduleSummary = when {
        localScheduleDays.size == 7 -> "Каждый день"
        localScheduleDays == (1..5).toSet() -> "По будням"
        localScheduleDays == setOf(6, 7) -> "По выходным"
        else -> "${localScheduleDays.size} дн. в нед."
    }

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
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Всего дней", fontSize = 11.sp, color = DuroTextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$completedScheduledCount из $scheduledCount",
                            fontSize = if ("$completedScheduledCount из $scheduledCount".length > 8) 16.sp else 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Text(
                            text = "по плану",
                            fontSize = 11.sp,
                            color = DuroTextSecondary
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
                            text = "$completionPercentage%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroLime
                        )
                        Text(
                            text = "в ${monthLabel.prepositional} ${displayedMonth.year}",
                            fontSize = 11.sp,
                            color = DuroTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Календарь месяца с навигацией: 28–31 клетка по длине месяца.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = monthLabel.nominative.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DuroTextMuted,
                    letterSpacing = 1.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(-1 to "‹", 1 to "›").forEach { (delta, label) ->
                        val target = displayedMonth.plusMonths(delta.toLong())
                        val enabled = delta < 0 || target <= YearMonth.now()
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (enabled) DuroSurface else Color.Transparent)
                                .border(1.dp, DuroBorder, RoundedCornerShape(8.dp))
                                .clickable(enabled = enabled) { displayedMonth = target },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (enabled) DuroTextPrimary else DuroTextMuted.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Календарная сетка месяца
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { name ->
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = name, fontSize = 10.sp, color = DuroTextMuted)
                            }
                        }
                    }
                    val firstOffset = (displayedMonth.atDay(1).dayOfWeek.value - 1)
                    val cells: List<LocalDate?> =
                        List(firstOffset) { null } +
                            (1..displayedMonth.lengthOfMonth()).map { displayedMonth.atDay(it) }
                    val createdDate = LocalDate.ofEpochDay(createdEpoch)
                    cells.chunked(7).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            week.forEach { dayDate ->
                                if (dayDate == null) {
                                    Spacer(modifier = Modifier.size(36.dp))
                                } else {
                                    val epoch = dayDate.toEpochDay()
                                    val isDone = epoch in monthEpochs
                                    val isToday = dayDate.isEqual(today)
                                    val isScheduled = dayDate.dayOfWeek.value in localScheduleDays
                                    val isFuture = dayDate.isAfter(today)
                                    val notExisted = dayDate.isBefore(createdDate)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    isDone -> accentColor
                                                    notExisted -> Color.Transparent
                                                    !isScheduled -> Color(0xFF101017)
                                                    else -> Color(0xFF16161F)
                                                }
                                            )
                                            .border(
                                                width = if (isToday) 2.dp else 1.dp,
                                                color = when {
                                                    isToday -> DuroOrange
                                                    isDone -> accentColor.copy(alpha = 0.5f)
                                                    !isScheduled -> DuroBorder.copy(alpha = 0.3f)
                                                    else -> DuroBorder
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${dayDate.dayOfMonth}",
                                            fontSize = 11.sp,
                                            fontWeight = if (isToday || isDone) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                isDone -> Color.White
                                                notExisted || isFuture -> DuroTextMuted.copy(alpha = 0.35f)
                                                !isScheduled -> DuroTextMuted.copy(alpha = 0.35f)
                                                isToday -> DuroTextPrimary
                                                else -> DuroTextMuted
                                            }
                                        )
                                    }
                                }
                            }
                            // Добиваем неполную неделю пустыми клетками для ровной сетки.
                            repeat(7 - week.size) {
                                Spacer(modifier = Modifier.size(36.dp))
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Дни недели", fontSize = 12.sp, color = DuroTextSecondary)
                    Text(text = scheduleSummary, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                    (1..7).forEach { day ->
                        val selected = day in localScheduleDays
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) accentColor.copy(alpha = 0.25f) else DuroSurface)
                                .border(1.dp, if (selected) accentColor else DuroBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    val updated = if (selected) {
                                        if (localScheduleDays.size > 1) localScheduleDays - day else localScheduleDays
                                    } else {
                                        localScheduleDays + day
                                    }
                                    if (updated != localScheduleDays) {
                                        localScheduleDays = updated
                                        viewModel.setHabitSchedule(habit.id, updated)
                                    }
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

/** Заголовок месяца в двух падежах. */
private data class MonthLabels(val nominative: String, val prepositional: String)
