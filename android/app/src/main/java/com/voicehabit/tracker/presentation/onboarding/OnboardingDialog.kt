package com.voicehabit.tracker.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.*

/**
 * F18: обучение при первом запуске + кнопка «показать заново».
 *
 * Размер списка страниц задаёт сам себя: `LAST_PAGE` считается от `pages.size`.
 * Раньше верхняя граница была вписана вручную (`coerceIn(0, 2)` и `page < 2` в двух
 * местах), и добавление страницы молча ломало бы последнюю кнопку.
 */
@Composable
fun OnboardingDialog(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()

    val pages = listOf(
        Triple(
            "🎙️",
            "Говорите — приложение понимает",
            "«Завтра надо поучить английский» → задача с дедлайном.\n«Сделал тренировку» → привычка отмечена.\n«Начни фокус 25 минут» → таймер."
        ),
        Triple(
            "◎",
            "А если не задача, а мысли?",
            "Начните говорить просто так — приложение поймёт, что вы не ставите задачу,\n" +
                "и соберёт выжимку: суть, решения, открытые вопросы и что делать дальше.\n" +
                "Список дел при этом не пострадает."
        ),
        Triple(
            "✅🔥",
            "Задачи и привычки",
            "Быстрые задачи закрываются в один тап, долгие цели живут как привычки.\nПодзадачи, напоминания, повторы, архив и корзина — всё из карточки."
        ),
        Triple(
            "📊",
            "Статистика и бэкапы",
            "Год выполнений, фокус-минуты, настроение, достижения.\nБэкап в JSON — данные только ваши и только на устройстве."
        )
    )
    val lastPage = pages.lastIndex
    val page = state.onboardingPage.coerceIn(0, lastPage)
    val (emoji, title, text) = pages[page]

    Dialog(
        onDismissRequest = { viewModel.completeOnboarding() },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(color = DuroSurface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = emoji, fontSize = 56.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DuroTextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = text,
                    fontSize = 13.sp,
                    color = DuroTextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    pages.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (index == page) 10.dp else 8.dp)
                                .background(
                                    if (index == page) DuroOrange else DuroBorder,
                                    RoundedCornerShape(50)
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (page > 0) {
                        OutlinedButton(
                            onClick = { viewModel.setOnboardingPage(page - 1) },
                            modifier = Modifier.weight(1f),
                            // Стандартные 24dp горизонтальных отступов съедали у слова
                            // «Назад» почти всю ширину кнопки: на диалоге шириной 318dp
                            // на текст оставалось 39dp, и он превращался в «Наз…».
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "Назад",
                                color = DuroTextPrimary,
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
                        colors = ButtonDefaults.buttonColors(containerColor = DuroOrange),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(if (page > 0) 1.7f else 1f)
                    ) {
                        Text(
                            text = if (page < lastPage) "Дальше" else "Начать!",
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}
