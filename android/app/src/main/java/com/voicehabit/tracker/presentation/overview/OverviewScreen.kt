package com.voicehabit.tracker.presentation.overview

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.R
import com.voicehabit.tracker.core.analysis.HabitStats
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.*
import java.time.LocalDate

@Composable
fun OverviewScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // P1: в знаменателе только привычки, запланированные на сегодня. Раньше стояли
    // все четыре, и пульс показывал 50% после выполнения обеих сегодняшних —
    // привычки на другие дни не должны быть «невыполненными сегодня».
    val todayDow = java.time.LocalDate.now().dayOfWeek.value
    val (completedHabitsCount, totalHabitsCount) = HabitStats.todayCounts(
        habits = state.habits,
        todayDow = todayDow,
        isRestDay = { it.isRestDay(todayDow) },
        isCompleted = { it.isCompletedToday }
    )
    val completionPercentage =
        if (totalHabitsCount > 0) (completedHabitsCount * 100) / totalHabitsCount else 100
    val hasHabitsToday = totalHabitsCount > 0
    val maxStreak = state.habits.maxOfOrNull { it.currentStreak } ?: 0

    val timerProgress = if (state.focusTimerTotalSeconds > 0) {
        (state.focusTimerSeconds.toFloat() / state.focusTimerTotalSeconds.toFloat())
    } else 0f

    val animatedTimerProgress by animateFloatAsState(targetValue = timerProgress, label = "TimerProgress")

    val timerMinutes = state.focusTimerSeconds / 60
    val timerSeconds = state.focusTimerSeconds % 60
    val formattedTimer = String.format("%02d:%02d", timerMinutes, timerSeconds)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item {
            Column {
                Text(
                    text = "Фокус и обзор",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppTheme.colors.textPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Глубокая концентрация и баланс дня",
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Pomodoro Focus Timer Card
        item {
            Surface(
                color = AppTheme.colors.surface,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = AppTheme.colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "ТАЙМЕР ФОКУСА",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.accent,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            color = AppTheme.colors.accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Сессий: ${state.focusSessionCount}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Circular Countdown Display
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(170.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedTimerProgress },
                            modifier = Modifier.size(170.dp),
                            color = AppTheme.colors.accent,
                            trackColor = AppTheme.colors.surfaceElevated,
                            strokeWidth = 9.dp,
                            strokeCap = StrokeCap.Round,
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formattedTimer,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AppTheme.colors.textPrimary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (state.isFocusTimerRunning) "Концентрация..." else "Готов к старту",
                                fontSize = 11.sp,
                                color = AppTheme.colors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Preset Duration Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val presets = listOf(
                            Pair("25 мин", 25 * 60),
                            Pair("15 мин", 15 * 60),
                            Pair("45 мин", 45 * 60),
                            Pair("5 мин", 5 * 60)
                        )

                        presets.forEach { (label, durationSec) ->
                            val isSelected = state.focusTimerTotalSeconds == durationSec
                            Surface(
                                color = if (isSelected) AppTheme.colors.accent.copy(alpha = 0.15f) else AppTheme.colors.surfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) AppTheme.colors.accent else Color.Transparent
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setFocusTimerDuration(durationSec)
                                    }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) AppTheme.colors.accent else AppTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Timer Control Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.resetFocusTimer()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.textSecondary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Сброс")
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleFocusTimer()
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isFocusTimerRunning) DairyWarning else AppTheme.colors.accent,
                                contentColor = if (state.isFocusTimerRunning) Color.White else Color(0xFF101014)
                            )
                        ) {
                            Icon(
                                imageVector = if (state.isFocusTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isFocusTimerRunning) "Пауза" else "Старт фокуса",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Daily Pulse Analytics Card
        item {
            Surface(
                color = AppTheme.colors.surface,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ПУЛЬС ПРОДУКТИВНОСТИ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.accent,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Сегодня",
                            fontSize = 11.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Habit Completion Stat
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppTheme.colors.surfaceElevated)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                // 0/0 — это не «100% выполнено», а «сегодня нечего делать».
                                // Показывать 100% в этот день было бы враньём наравне с 50%.
                                text = if (hasHabitsToday) "$completionPercentage%" else "—",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (hasHabitsToday) {
                                    AppTheme.colors.textPrimary
                                } else {
                                    AppTheme.colors.textMuted
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (hasHabitsToday) {
                                    "Привычки ($completedHabitsCount/$totalHabitsCount)"
                                } else {
                                    "На сегодня пусто"
                                },
                                fontSize = 11.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Streak Stat
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppTheme.colors.surfaceElevated)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$maxStreak дн.",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AppTheme.colors.accent
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Макс. стрик",
                                fontSize = 11.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Focus minutes
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppTheme.colors.surfaceElevated)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Реальные минуты за сегодня. Раньше стояло
                            // `focusSessionCount * 25` — то есть любая сессия,
                            // даже 5-минутная, показывалась как 25 минут.
                            val totalFocusMins = state.focusMinutesToday
                            Text(
                                text = "${totalFocusMins}м",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AppTheme.colors.accentSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Фокус-время",
                                fontSize = 11.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Weekly Consistency Mini Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val days = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
                        days.forEachIndexed { index, day ->
                            // Реальные отметки недели. Раньше здесь стояло
                            // `index <= 3` — то есть карточка всегда показывала
                            // «4 из 7» вне зависимости от того, что пользователь делал.
                            val isDone = state.weekCompletedDays.getOrElse(index) { false }
                            val isFuture = index > LocalDate.now().dayOfWeek.value - 1
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isDone) AppTheme.colors.accent else AppTheme.colors.surfaceElevated)
                                        .border(1.dp, if (isDone) AppTheme.colors.accent else AppTheme.colors.border, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDone) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (LocalAppPalette.current.id == "noir_ink") Color.Black else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = day,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isDone -> AppTheme.colors.textPrimary
                                        isFuture -> AppTheme.colors.textMuted.copy(alpha = 0.4f)
                                        else -> AppTheme.colors.textMuted
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Adaptive Sprint Flow Engine Prompt Card
        item {
            val run = state.focusRun
            Surface(
                color = if (run != null) AppTheme.colors.accent.copy(alpha = 0.12f) else AppTheme.colors.surfaceElevated.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (run != null) AppTheme.colors.accent else AppTheme.colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.FOCUS)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = AppTheme.colors.accent.copy(alpha = if (run != null) 0.25f else 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (run != null) Icons.Default.PlayArrow else Icons.Default.Bolt,
                                contentDescription = null,
                                tint = AppTheme.colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (run != null) {
                                val mm = run.remainingSec / 60
                                val ss = run.remainingSec % 60
                                "Активный спринт: %02d:%02d".format(mm, ss)
                            } else {
                                "Адаптивный спринт (Flow Engine)"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (run != null) {
                                run.currentStep ?: run.label
                            } else {
                                "Калибровка состояния, физические шаги и Focus Guard"
                            },
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Открыть",
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Evening Review Prompt Card ("Вечерний дневник")
        item {
            Surface(
                color = AppTheme.colors.surfaceElevated.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.REVIEW)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = AppTheme.colors.accent.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_journal_night),
                                contentDescription = null,
                                tint = AppTheme.colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Вечерний дневник",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Надиктовать запись мыслей за день",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }

                    Icon(
                        painter = painterResource(R.drawable.ic_mic_minimal),
                        contentDescription = "Записать",
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Quick Modules Shortcuts
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "РАЗДЕЛЫ И МОДУЛИ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textSecondary,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModuleCard(
                        iconResId = R.drawable.ic_routines_cycle,
                        title = "Рутины",
                        subtitle = "Суточный цикл",
                        onClick = { viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.ROUTINES) },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleCard(
                        iconResId = R.drawable.ic_challenges_peak,
                        title = "Челленджи",
                        subtitle = "Цели на 30 дней",
                        onClick = { viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.CHALLENGES) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModuleCard(
                        iconResId = R.drawable.ic_achievements_spark,
                        title = "Достижения",
                        subtitle = "Бейджи и вехи",
                        onClick = { viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.ACHIEVEMENTS) },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleCard(
                        iconResId = R.drawable.ic_analytics_trend,
                        title = "Аналитика",
                        subtitle = "Тренды и стрики",
                        onClick = { viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.STATS) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // P1: тренировочная программа. Подпись показывает не «модуль», а
                // ближайший день — иначе карточка не отвечает на вопрос «когда
                // мне в зал», ради которого её и открывают.
                ModuleCard(
                    iconResId = R.drawable.ic_programmes_dumbbell,
                    title = "Программы",
                    subtitle = state.programme?.let { p ->
                        val today = p.todayDay()
                        if (today == null) "Добавьте программу"
                        else if (today.isRest) "${today.weekdayLabel}: отдых"
                        else "${today.weekdayLabel}: ${today.title.take(24)}"
                    } ?: "Импорт из буфера",
                    onClick = { viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.PROGRAMMES) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ModuleCard(
    @DrawableRes iconResId: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AppTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.accent.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconResId),
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                Text(text = subtitle, fontSize = 11.sp, color = AppTheme.colors.textSecondary)
            }
        }
    }
}
