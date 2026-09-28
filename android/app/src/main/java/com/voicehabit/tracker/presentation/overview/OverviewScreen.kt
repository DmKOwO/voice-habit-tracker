package com.voicehabit.tracker.presentation.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.*

@Composable
fun OverviewScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val completedHabitsCount = state.habits.count { it.isCompletedToday }
    val totalHabitsCount = state.habits.size
    val completionPercentage = if (totalHabitsCount > 0) (completedHabitsCount * 100) / totalHabitsCount else 0
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
                    color = DuroTextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Глубокая концентрация и баланс дня",
                    fontSize = 12.sp,
                    color = DuroTextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Pomodoro Focus Timer Card
        item {
            Surface(
                color = DuroSurface,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
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
                                tint = DuroOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "ТАЙМЕР ФОКУСА",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuroOrange,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            color = DuroOrange.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Сессий: ${state.focusSessionCount}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuroOrange,
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
                            color = DuroOrange,
                            trackColor = DuroSurfaceElevated,
                            strokeWidth = 10.dp,
                            strokeCap = StrokeCap.Round,
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formattedTimer,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DuroTextPrimary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (state.isFocusTimerRunning) "Концентрация..." else "Готов к старту",
                                fontSize = 11.sp,
                                color = DuroTextSecondary,
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
                                color = if (isSelected) DuroOrange else DuroSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) DuroOrange else DuroBorder),
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setFocusTimerDuration(durationSec)
                                }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else DuroTextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DuroTextSecondary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder)
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
                                containerColor = if (state.isFocusTimerRunning) DuroAmber else DuroOrange,
                                contentColor = Color.White
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
                color = DuroSurface,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
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
                            color = DuroCyan,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Сегодня",
                            fontSize = 11.sp,
                            color = DuroTextSecondary
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
                                .background(DuroSurfaceElevated)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$completionPercentage%",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DuroLime
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Привычки ($completedHabitsCount/$totalHabitsCount)",
                                fontSize = 11.sp,
                                color = DuroTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Streak Stat
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(DuroSurfaceElevated)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$maxStreak дн. 🔥",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DuroOrange
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Макс. стрик",
                                fontSize = 11.sp,
                                color = DuroTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Focus minutes
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(DuroSurfaceElevated)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val totalFocusMins = state.focusSessionCount * 25
                            Text(
                                text = "${totalFocusMins}м",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DuroPurple
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Фокус-время",
                                fontSize = 11.sp,
                                color = DuroTextSecondary
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
                            val isDone = index <= 3 // Sample consistency
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isDone) DuroOrange else DuroSurfaceElevated)
                                        .border(1.dp, if (isDone) DuroOrange else DuroBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDone) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Text(
                                    text = day,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDone) DuroTextPrimary else DuroTextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Evening Review Prompt Card ("Вечерний разбор")
        item {
            Surface(
                color = DuroJournalLavender.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DuroJournalLavender.copy(alpha = 0.4f)),
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
                        color = DuroJournalLavender.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "🌙", fontSize = 22.sp)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Вечерний разбор дня",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Как прошел ваш день? Надиктуйте мысли и инсайты в Дневник голосом.",
                            fontSize = 12.sp,
                            color = DuroTextSecondary,
                            lineHeight = 16.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Записать",
                        tint = DuroJournalLavender,
                        modifier = Modifier.size(24.dp)
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
                    color = DuroTextSecondary,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModuleCard(
                        emoji = "🔄",
                        title = "Рутины",
                        subtitle = "Утро и вечер",
                        onClick = { viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.ROUTINES) },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleCard(
                        emoji = "🏆",
                        title = "Челленджи",
                        subtitle = "30-дневные цели",
                        onClick = { viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.CHALLENGES) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModuleCard(
                        emoji = "🎖",
                        title = "Достижения",
                        subtitle = "Геймификация и бейджи",
                        onClick = { viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.ACHIEVEMENTS) },
                        modifier = Modifier.weight(1f)
                    )
                    ModuleCard(
                        emoji = "📊",
                        title = "Статистика",
                        subtitle = "Тренды и аналитика",
                        onClick = { viewModel.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.STATS) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ModuleCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DuroSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = emoji, fontSize = 20.sp)
            Column {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroTextPrimary)
                Text(text = subtitle, fontSize = 11.sp, color = DuroTextSecondary)
            }
        }
    }
}
