package com.voicehabit.tracker.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.domain.model.AppStats
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.hub.ScreenHeader
import com.voicehabit.tracker.presentation.theme.*
import java.time.LocalDate

/** F15/G23/G26: статистика, настроение, здоровье. */
@Composable
fun StatsScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    val stats = state.stats
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshStats() }

    val healthLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        viewModel.healthManager().permissionContract()
    ) { viewModel.refreshHealth() }

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
        ScreenHeader(title = "Статистика", subtitle = "Год, дни, фокус, настроение") {
            viewModel.closeScreen()
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Настроение дня (G26)
        Text(text = "Настроение сегодня", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val ratings = listOf("1" to 1, "2" to 2, "3" to 3, "4" to 4, "5" to 5)
            ratings.forEach { (label, value) ->
                val selected = state.moodToday == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AppTheme.colors.accent else AppTheme.colors.surface)
                        .border(1.dp, if (selected) AppTheme.colors.accent else AppTheme.colors.border, RoundedCornerShape(12.dp))
                        .clickable { viewModel.setMood(value) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) AppTheme.colors.onAccent else AppTheme.colors.textPrimary
                    )
                }
            }
        }
        if (state.moods.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            val avg = state.moods.values.average()
            Text(
                text = "Среднее за год: ${"%.1f".format(avg)} / 5",
                fontSize = 12.sp,
                color = DuroTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (stats == null) {
            CircularProgressIndicator(color = DuroOrange, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            StatCards(stats)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Активность по дням недели", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            WeekdayBars(stats)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Фокус по дням (мин)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            FocusWeekBars(sessions = state.focusSessions)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Год выполнений", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            YearGrid(stats)
            Spacer(modifier = Modifier.height(16.dp))
            if (stats.completionByCategory.isNotEmpty()) {
                Text(text = "По категориям", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                stats.completionByCategory.forEach { (cat, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = cat, fontSize = 13.sp, color = DuroTextPrimary)
                        Text(text = "$count", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroOrange)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Здоровье (F11)
        Text(text = "Здоровье", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        when (state.healthStatus) {
            "AVAILABLE" -> {
                Text(
                    text = "Шагов сегодня: ${state.healthSteps ?: "—"}\nСна прошлой ночью: ${
                        state.healthSleepHours?.let { "%.1f ч".format(it) } ?: "—"
                    }",
                    fontSize = 13.sp,
                    color = DuroTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.refreshHealth() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Обновить данные", color = DuroTextPrimary)
                }
            }
            "NOT_INSTALLED" -> Text(
                text = "Health Connect не установлен — установите его из Play, чтобы видеть шаги и сон.",
                fontSize = 12.sp,
                color = DuroTextSecondary
            )
            else -> {
                Text(
                    text = "Разрешите чтение шагов и сна, чтобы видеть данные здесь.",
                    fontSize = 12.sp,
                    color = DuroTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            healthLauncher.launch(viewModel.healthManager().permissions)
                        } catch (e: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Запросить доступ", color = DuroTextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                val share = android.content.Intent.createChooser(
                    android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, viewModel.shareStatsText())
                    },
                    "Поделиться статистикой"
                )
                context.startActivity(share)
            },
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Поделиться статистикой", color = AppTheme.colors.textPrimary)
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
private fun StatCards(stats: AppStats) {
    val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("Выполнений", "${stats.totalCompletions}", Modifier.weight(1f))
        StatCard("Лучший день", days.getOrElse(stats.bestWeekday) { "?" }, Modifier.weight(1f))
        StatCard("Стрик", "${stats.currentBestStreak}", Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("Фокус всего", "${stats.focusMinutesTotal} мин", Modifier.weight(1f))
        StatCard("Фокус неделя", "${stats.focusMinutesWeek} мин", Modifier.weight(1f))
        StatCard("Задач закрыто", "${stats.tasksCompleted}", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.accent)
        Text(text = label, fontSize = 11.sp, color = AppTheme.colors.textSecondary)
    }
}

@Composable
private fun WeekdayBars(stats: AppStats) {
    val max = (stats.completionsByWeekday.maxOrNull() ?: 0).coerceAtLeast(1)
    val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        stats.completionsByWeekday.forEachIndexed { index, count ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((20 + 100 * count / max).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index == stats.bestWeekday) AppTheme.colors.accent else AppTheme.colors.surfaceElevated)
                )
                Text(text = days[index], fontSize = 10.sp, color = AppTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun FocusWeekBars(sessions: List<com.voicehabit.tracker.domain.model.FocusSession>) {
    val today = LocalDate.now().toEpochDay()
    val perDay = (0L..6L).map { offset ->
        val day = today - (6 - offset)
        val start = java.time.LocalDate.ofEpochDay(day).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val end = start + 24 * 60 * 60 * 1000
        sessions.filter { it.completed && it.startedAt in start until end }.sumOf { it.durationMin }
    }
    val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        perDay.forEach { minutes ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((16 + 80 * minutes / max).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppTheme.colors.accentSecondary.copy(alpha = if (minutes > 0) 0.9f else 0.25f))
                )
                Text(
                    text = if (minutes > 0) "$minutes" else "",
                    fontSize = 9.sp,
                    color = DuroTextSecondary
                )
            }
        }
    }
}

@Composable
private fun YearGrid(stats: AppStats) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(26),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        userScrollEnabled = false
    ) {
        itemsIndexed(stats.yearGrid) { _, done ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (done) DuroLime else DuroSurface)
            )
        }
    }
}
