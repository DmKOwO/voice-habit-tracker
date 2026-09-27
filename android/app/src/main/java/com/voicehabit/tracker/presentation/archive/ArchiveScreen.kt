package com.voicehabit.tracker.presentation.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** F7: архив привычек + корзины с восстановлением и очисткой. */
@Composable
fun ArchiveScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { viewModel.refreshArchive() }

    Column(
        modifier = Modifier
            .fillMaxSize()
        .statusBarsPadding()
            .statusBarsPadding()
            .background(DuroBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(title = "Архив и корзина", subtitle = "Восстановление в течение 30 дней") {
            viewModel.closeScreen()
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Привычки", "Корзина").forEachIndexed { index, label ->
                val selected = tab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) DuroOrange.copy(alpha = 0.2f) else DuroSurface)
                        .clickableNoRipple { tab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) DuroOrange else DuroTextSecondary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (tab == 0) {
            if (state.archivedHabits.isEmpty()) {
                EmptyHint("Архив пуст — смахните привычку в архив из деталей")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(state.archivedHabits, key = { it.id }) { habit ->
                        ArchiveRow(
                            title = habit.title,
                            subtitle = habit.category,
                            primaryLabel = "Вернуть",
                            onPrimary = { viewModel.archiveHabit(habit.id, false) },
                            dangerLabel = "В корзину",
                            onDanger = { viewModel.trashHabit(habit.id) }
                        )
                    }
                }
            }
        } else {
            if (state.trashedHabits.isEmpty() && state.trashedTasks.isEmpty()) {
                EmptyHint("Корзина пуста")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(state.trashedHabits, key = { "h-" + it.id }) { habit ->
                        ArchiveRow(
                            title = habit.title,
                            subtitle = "привычка",
                            primaryLabel = "Восстановить",
                            onPrimary = { viewModel.restoreHabit(habit.id) },
                            dangerLabel = "Удалить",
                            onDanger = { viewModel.deleteHabitForever(habit.id) }
                        )
                    }
                    items(state.trashedTasks, key = { "t-" + it.id }) { task ->
                        ArchiveRow(
                            title = task.title,
                            subtitle = "задача",
                            primaryLabel = "Восстановить",
                            onPrimary = { viewModel.restoreTask(task.id) },
                            dangerLabel = "Удалить",
                            onDanger = { viewModel.deleteTaskForever(task.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.purgeTrash() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DuroRed)
                        ) {
                            Text("Очистить корзину старше 30 дней")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 13.sp, color = DuroTextSecondary)
    }
}

@Composable
private fun ArchiveRow(
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    dangerLabel: String,
    onDanger: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DuroSurface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DuroTextPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = DuroTextSecondary)
        }
        TextButton(onClick = onPrimary) {
            Text(text = primaryLabel, fontSize = 12.sp, color = DuroLime)
        }
        TextButton(onClick = onDanger) {
            Text(text = dangerLabel, fontSize = 12.sp, color = DuroRed)
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            indication = null,
            interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            onClick = onClick
        )
    )
