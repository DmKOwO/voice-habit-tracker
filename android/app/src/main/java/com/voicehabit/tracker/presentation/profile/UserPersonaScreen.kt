package com.voicehabit.tracker.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.hub.ScreenHeader
import com.voicehabit.tracker.presentation.theme.*

/**
 * Экран «Контекст обо мне» (User Persona & Memory Engine).
 * Управляет базовым профилем, текущим активным фокусом и прозрачной памятью фактов ИИ.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserPersonaScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()

    var hardFacts by remember(state.userPersonaHardFacts) { mutableStateOf(state.userPersonaHardFacts) }
    var activeFocus by remember(state.userPersonaActiveFocus) { mutableStateOf(state.userPersonaActiveFocus) }
    var newFactText by remember { mutableStateOf("") }

    val quickSkillTags = remember {
        listOf("Android", "Kotlin", "Compose", "Python", "Backend", "Product", "UI/UX", "System Architect")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(AppTheme.colors.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(
            title = "Контекст и память ИИ",
            subtitle = "Персональный контекст, активный фокус и память фактов"
        ) {
            viewModel.closeScreen()
        }
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // БЛОК 1: Базовый профиль (Hard Facts)
            item(key = "hard_facts_block") {
                Surface(
                    color = AppTheme.colors.surface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Базовый профиль и стек",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Сферы деятельности, стек технологий, роли и ключевые навыки. Помогает ИИ точно понимать термины и контекст задач.",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary,
                            lineHeight = 17.sp
                        )

                        OutlinedTextField(
                            value = hardFacts,
                            onValueChange = { hardFacts = it },
                            placeholder = {
                                Text(
                                    "Например: Senior Android Developer, Kotlin, Jetpack Compose, архитектура приложений, основатель стартапа",
                                    fontSize = 13.sp,
                                    color = AppTheme.colors.textMuted
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(14.dp),
                            colors = duroTextFieldColors()
                        )

                        Text(
                            text = "Быстрое добавление тегов:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppTheme.colors.textSecondary
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickSkillTags.forEach { tag ->
                                Surface(
                                    color = AppTheme.colors.surfaceElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                                    modifier = Modifier.clickable {
                                        hardFacts = if (hardFacts.isBlank()) tag else "$hardFacts, $tag"
                                    }
                                ) {
                                    Text(
                                        text = "+ $tag",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AppTheme.colors.accent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // БЛОК 2: Текущий фокус недели/месяца (Active State)
            item(key = "active_focus_block") {
                Surface(
                    color = AppTheme.colors.surface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Текущий фокус недели/месяца",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "2–3 предложения о текущей работе, приоритетных релизах или жизненных целях на ближайшее время.",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary,
                            lineHeight = 17.sp
                        )

                        OutlinedTextField(
                            value = activeFocus,
                            onValueChange = { activeFocus = it },
                            placeholder = {
                                Text(
                                    "Например: Релиз v1.2.4 с фокус-движком и Obsidian-синхронизацией. Подготовка к запуску в продакшн.",
                                    fontSize = 13.sp,
                                    color = AppTheme.colors.textMuted
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 5,
                            shape = RoundedCornerShape(14.dp),
                            colors = duroTextFieldColors()
                        )
                    }
                }
            }

            // БЛОК 3: Прозрачная память (Memory Log)
            item(key = "memory_log_block") {
                Surface(
                    color = AppTheme.colors.surface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Прозрачная память ИИ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Факты, которые ИИ вывел из ваших записей или которые вы сохранили вручную. Вы можете удалить любой факт в один клик.",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary,
                            lineHeight = 17.sp
                        )

                        // Добавление факта вручную
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newFactText,
                                onValueChange = { newFactText = it },
                                placeholder = { Text("Добавить факт...", fontSize = 12.sp, color = AppTheme.colors.textMuted) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = duroTextFieldColors()
                            )
                            IconButton(
                                onClick = {
                                    if (newFactText.isNotBlank()) {
                                        viewModel.addMemoryFact(newFactText)
                                        newFactText = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppTheme.colors.accent.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Добавить факт",
                                    tint = AppTheme.colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (state.userPersonaMemoryLog.isEmpty()) {
                            Surface(
                                color = AppTheme.colors.surfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Память пока пуста. ИИ будет автоматически фиксировать важные факты при разборе ваших голосовых мыслей.",
                                    fontSize = 12.sp,
                                    color = AppTheme.colors.textMuted,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.userPersonaMemoryLog.forEach { fact ->
                                    Surface(
                                        color = AppTheme.colors.surfaceElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = fact,
                                                fontSize = 12.sp,
                                                color = AppTheme.colors.textPrimary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { viewModel.deleteMemoryFact(fact) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Удалить факт",
                                                    tint = AppTheme.colors.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Кнопка сохранения
            item(key = "save_persona_button") {
                Button(
                    onClick = {
                        viewModel.saveUserPersona(hardFacts, activeFocus)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.accent,
                        contentColor = AppTheme.colors.onAccent
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "Сохранить контекст",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
