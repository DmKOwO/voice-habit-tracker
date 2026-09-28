package com.voicehabit.tracker.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.voicehabit.tracker.R
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.AppTheme

/**
 * Обучение при первом запуске — строгий минималистичный стиль dairy без эмодзи.
 */
@Composable
fun OnboardingDialog(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()

    val pages = listOf(
        Triple(
            R.drawable.ic_mic_minimal,
            "Говорите — приложение понимает",
            "«Завтра надо поучить английский» → задача с дедлайном.\n«Сделал тренировку» → привычка отмечена.\n«Начни фокус 25 минут» → таймер."
        ),
        Triple(
            R.drawable.ic_journal_night,
            "А если не задача, а мысли?",
            "Начните говорить свободно — приложение соберёт структурированную выжимку мыслей: суть, решения и открытые вопросы без захламления списка дел."
        ),
        Triple(
            R.drawable.ic_habit_check,
            "Задачи и привычки",
            "Быстрые задачи закрываются в один тап, регулярные цели живут как ритм привычек со стрик-прогрессом."
        ),
        Triple(
            R.drawable.ic_analytics_trend,
            "Статистика и бэкапы",
            "Пульс продуктивности, фокус-минуты, экспорт в Obsidian Vault и локальные бэкапы — данные принадлежат только вам."
        )
    )
    val lastPage = pages.lastIndex
    val page = state.onboardingPage.coerceIn(0, lastPage)
    val (iconRes, title, text) = pages[page]

    Dialog(
        onDismissRequest = { viewModel.completeOnboarding() },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            color = AppTheme.colors.surface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Чистый круглый бейдж с тонкой векторной иконкой вместо эмодзи
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.surfaceElevated)
                        .border(1.dp, AppTheme.colors.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = AppTheme.colors.accent
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = text,
                    fontSize = 13.sp,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    pages.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (index == page) 10.dp else 7.dp)
                                .background(
                                    if (index == page) AppTheme.colors.accent else AppTheme.colors.border,
                                    RoundedCornerShape(50)
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (page > 0) {
                        OutlinedButton(
                            onClick = { viewModel.setOnboardingPage(page - 1) },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "Назад",
                                color = AppTheme.colors.textPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (page < lastPage) viewModel.setOnboardingPage(page + 1)
                            else viewModel.completeOnboarding()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.accent,
                            contentColor = AppTheme.colors.onAccent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(if (page > 0) 1.6f else 1f)
                    ) {
                        Text(
                            text = if (page < lastPage) "Дальше" else "Начать",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}
