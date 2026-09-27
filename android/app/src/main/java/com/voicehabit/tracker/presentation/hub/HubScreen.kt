package com.voicehabit.tracker.presentation.hub

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
    val emoji: String,
    val title: String,
    val subtitle: String
)

@Composable
fun HubScreen(viewModel: HomeViewModel) {
    val items = listOf(
        HubItem(AppScreen.STATS, "📊", "Статистика", "Год, дни, фокус, настроение"),
        HubItem(AppScreen.ARCHIVE, "🗄️", "Архив и корзина", "Восстановление за 30 дней"),
        HubItem(AppScreen.ROUTINES, "⚡", "Рутины", "Одна фраза — много привычек"),
        HubItem(AppScreen.CHALLENGES, "🏁", "Челленджи", "30 дней подряд"),
        HubItem(AppScreen.ACHIEVEMENTS, "🏆", "Достижения", "Бейджи и рекорды"),
        HubItem(AppScreen.DIGESTS, "◎", "Конспекты", "Выжимки разговоров и мыслей"),
        HubItem(AppScreen.REVIEW, "🌙", "Вечерний разбор", "Итог дня и план"),
        HubItem(AppScreen.FOCUS, "🧠", "Фокус", "Pomodoro-таймер"),
        HubItem(AppScreen.BACKUP, "💾", "Бэкап", "Экспорт и импорт"),
        HubItem(AppScreen.ABOUT, "ℹ️", "О приложении", "Версия и GitHub")
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(DuroBackground)
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
                // Быстрая заморозка дня прямо из хаба (F6).
                HubActionCard(emoji = "❄️", title = "Заморозить день", subtitle = "Стрик не прервётся") {
                    viewModel.freezeToday()
                }
            }
        }
    }
}

@Composable
private fun HubCard(item: HubItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DuroSurface)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(text = item.emoji, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DuroTextPrimary)
        Text(text = item.subtitle, fontSize = 11.sp, color = DuroTextSecondary)
    }
}

@Composable
private fun HubActionCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DuroSurface)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(text = emoji, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DuroCyan)
        Text(text = subtitle, fontSize = 11.sp, color = DuroTextSecondary)
    }
}

/** G31: пульс-празднование закрытого дня. */
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
            .background(DuroBackground.copy(alpha = 0.92f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🎉", fontSize = 72.sp, modifier = Modifier.scale(scale))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Всё выполнено!",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DuroTextPrimary
            )
            Text(text = "Нажмите, чтобы продолжить", fontSize = 13.sp, color = DuroTextSecondary)
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
