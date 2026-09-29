package com.voicehabit.tracker.presentation.hub

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.presentation.home.AppScreen
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.*

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = DuroOrange
            )
        }
        Column {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DuroTextPrimary
            )
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 12.sp, color = DuroTextSecondary)
            }
        }
    }
}

data class HubItem(
    val screen: AppScreen,
    val iconRes: Int? = null,
    val vectorIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val title: String,
    val subtitle: String
)

@Composable
fun HubScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    val items = listOf(
        HubItem(AppScreen.STATS, iconRes = com.voicehabit.tracker.R.drawable.ic_analytics_trend, title = "Статистика", subtitle = "Год, дни, фокус, настроение"),
        HubItem(AppScreen.ARCHIVE, vectorIcon = Icons.Default.Inventory2, title = "Архив и корзина", subtitle = "Восстановление за 30 дней"),
        HubItem(AppScreen.ROUTINES, iconRes = com.voicehabit.tracker.R.drawable.ic_routines_cycle, title = "Рутины", subtitle = "Одна фраза — много привычек"),
        HubItem(AppScreen.CHALLENGES, iconRes = com.voicehabit.tracker.R.drawable.ic_challenges_peak, title = "Челленджи", subtitle = "30 дней подряд"),
        HubItem(AppScreen.ACHIEVEMENTS, iconRes = com.voicehabit.tracker.R.drawable.ic_achievements_spark, title = "Достижения", subtitle = "Бейджи и рекорды"),
        HubItem(AppScreen.DIGESTS, iconRes = com.voicehabit.tracker.R.drawable.ic_journal_night, title = "Конспекты", subtitle = "Выжимки разговоров и мыслей"),
        HubItem(AppScreen.REVIEW, iconRes = com.voicehabit.tracker.R.drawable.ic_journal_night, title = "Вечерний разбор", subtitle = "Итог дня и план"),
        HubItem(AppScreen.FOCUS, vectorIcon = Icons.Default.RadioButtonChecked, title = "Фокус", subtitle = "Pomodoro-таймер"),
        HubItem(AppScreen.BACKUP, vectorIcon = Icons.Default.CloudUpload, title = "Бэкап", subtitle = "Экспорт и импорт"),
        HubItem(AppScreen.ABOUT, vectorIcon = Icons.Default.HelpOutline, title = "О приложении", subtitle = "Версия и GitHub")
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(AppTheme.colors.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(title = "Ещё", subtitle = "Все разделы приложения") { viewModel.closeScreen() }
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(items) { item ->
                HubCard(item = item, onClick = { viewModel.openScreen(item.screen) })
            }
            item {
                // Быстрая заморозка дня прямо из хаба
                HubActionCard(
                    icon = Icons.Default.PauseCircle,
                    title = "Заморозить день",
                    subtitle = "Стрик не прервётся"
                ) {
                    viewModel.freezeToday()
                }
            }
            item {
                HubActionCard(
                    icon = Icons.Default.Search,
                    title = "Поиск по всему",
                    subtitle = "Привычки, задачи, конспекты"
                ) {
                    viewModel.openGlobalSearch()
                }
            }
            item {
                HubActionCard(
                    icon = Icons.Default.BugReport,
                    title = "Сообщить о проблеме",
                    subtitle = "Баг или UI — с логом"
                ) {
                    viewModel.openFeedback()
                }
            }
            item {
                HubActionCard(
                    icon = Icons.Default.Psychology,
                    title = "Что обо мне понял",
                    subtitle = state.inferredInsights.ifBlank { "Обновить выводы" }.take(48)
                ) {
                    viewModel.refreshInferredInsights()
                }
            }
            item {
                HubActionCard(
                    icon = Icons.Default.CloudDownload,
                    title = "Импорт из Obsidian",
                    subtitle = "Markdown из буфера → конспект"
                ) {
                    val clipboard = (viewModel.getApplicationContext().getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE
                    ) as android.content.ClipboardManager)
                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(viewModel.getApplicationContext())?.toString().orEmpty()
                    viewModel.importObsidianMarkdown(text)
                }
            }
        }
    }
    if (state.isGlobalSearchOpen) {
        GlobalSearchSheet(
            query = state.globalSearchQuery,
            onQueryChange = { viewModel.setGlobalSearchQuery(it) },
            habits = state.habits,
            tasks = state.tasks + state.completedTasks,
            digests = state.digests,
            onDismiss = { viewModel.closeGlobalSearch() }
        )
    }
    if (state.isFeedbackOpen) {
        FeedbackSheet(
            appVersion = com.voicehabit.tracker.BuildConfig.VERSION_NAME,
            recentLog = viewModel.recentLogText(),
            usageSummary = viewModel.usageSummary(),
            onDismiss = { viewModel.closeFeedback() }
        )
    }
}

@Composable
private fun HubCard(item: HubItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        if (item.iconRes != null) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = item.iconRes),
                contentDescription = null,
                tint = AppTheme.colors.accent,
                modifier = Modifier.size(24.dp)
            )
        } else if (item.vectorIcon != null) {
            Icon(
                imageVector = item.vectorIcon,
                contentDescription = null,
                tint = AppTheme.colors.accent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
        Text(text = item.subtitle, fontSize = 11.sp, color = AppTheme.colors.textSecondary)
    }
}

@Composable
private fun HubActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppTheme.colors.accent,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.accent)
        Text(text = subtitle, fontSize = 11.sp, color = AppTheme.colors.textSecondary)
    }
}

/** Пульс-празднование закрытого дня. */
@Composable
fun CelebrationOverlay(onDismiss: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "celebrate")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(AppTheme.colors.surfaceElevated)
                    .border(1.dp, AppTheme.colors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                DairyLogo(size = 40.dp, color = AppTheme.colors.accent)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Всё выполнено",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Text(text = "Нажмите, чтобы продолжить", fontSize = 13.sp, color = AppTheme.colors.textSecondary)
        }
    }
}

/** G38: о приложении. */
@Composable
fun AboutScreen(viewModel: HomeViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(DuroBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(title = "О приложении", subtitle = "Duro Voice Habits") { viewModel.closeScreen() }
        Spacer(modifier = Modifier.height(16.dp))
        AboutRow("Версия", version)
        AboutRow("Канал обновлений", "GitHub Releases (DmKOwO)")
        AboutRow("Данные", "Только на устройстве, без облака")
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.reopenOnboarding() },
            colors = ButtonDefaults.buttonColors(containerColor = DuroOrange),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Показать обучение заново")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.openSettings() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Проверить обновления", color = DuroTextPrimary)
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp, color = DuroTextSecondary)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DuroTextPrimary)
    }
}
