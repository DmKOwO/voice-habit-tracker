package com.voicehabit.tracker.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.domain.model.Priority
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTaskCard(
    task: Task,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onStartFocus: (() -> Unit)? = null
) {
    val haptics = rememberDuroHaptics()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.StartToEnd) {
                haptics.confirm()
                onToggle()
            }
            false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.34f }
    )
    val swipeActive = dismissState.targetValue != SwipeToDismissBoxValue.Settled
    val actionScale by animateFloatAsState(
        targetValue = if (swipeActive) 1.18f else 1f,
        animationSpec = DuroIconSpring,
        label = "swipeCompleteScale"
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            // Фон для свайпа показывается ТОЛЬКО во время активного жеста
            if (swipeActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    AppTheme.colors.accent.copy(alpha = 0.25f),
                                    AppTheme.colors.accent.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Отметить выполненной",
                        tint = AppTheme.colors.accent,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = actionScale
                                scaleY = actionScale
                            }
                            .size(20.dp)
                    )
                }
            }
        }
    ) {
        TaskCard(
            task = task,
            onToggle = onToggle,
            onEdit = onEdit,
            onStartFocus = onStartFocus,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: Task,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onStartFocus: (() -> Unit)? = null
) {
    // Непрозрачный фон предотвращает просвечивание фоновых элементов
    val containerColor by animateColorAsState(
        targetValue = if (task.isCompleted) DuroSurface.copy(alpha = 0.85f) else DuroSurface,
        animationSpec = DuroColorSpring,
        label = "taskContainerColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (task.isCompleted) DuroBorder.copy(alpha = 0.45f) else DuroBorder.copy(alpha = 0.72f),
        animationSpec = DuroColorSpring,
        label = "taskBorderColor"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.15f else 1f,
        animationSpec = DuroIconSpring,
        label = "taskCheckScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .then(if (onEdit != null) Modifier.duroPressable(onClick = onEdit) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AppTheme.colors.accent,
                        checkmarkColor = AppTheme.colors.surface,
                        uncheckedColor = DuroTextMuted
                    )
                )

                if (task.pinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Закреплено",
                        tint = DuroOrange,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (task.isCompleted) DuroTextSecondary.copy(alpha = 0.75f) else DuroTextPrimary
                    ),
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f)
                )

                if (onEdit != null) {
                    IconButton(onClick = { onEdit() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редактировать задачу",
                            tint = DuroTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (task.description.isNotBlank()) {
                Text(
                    text = task.description.take(120),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = DuroTextSecondary
                    ),
                    maxLines = 2
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Дедлайн (векторная иконка вместо эмодзи)
                task.dueDateIso?.let { due ->
                    val overdue = isOverdue(due) && !task.isCompleted
                    val tintColor = when {
                        task.isCompleted -> DuroTextMuted
                        overdue -> DuroRed
                        else -> DuroOrange
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tintColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = tintColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = formatTaskDue(due),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = tintColor,
                                fontWeight = if (overdue) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }

                // Категория (векторная иконка папки вместо эмодзи)
                if (task.category.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DuroSurfaceElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = DuroTextMuted,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = task.category,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = DuroTextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                // Оценка времени (векторная иконка таймера вместо эмодзи)
                task.estimatedMin?.let { minutes ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppTheme.colors.accent.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = AppTheme.colors.accent,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "$minutes мин",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = AppTheme.colors.accent,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                TypeBadge(task = task)
                PriorityBadge(task = task)

                if (onStartFocus != null && !task.isCompleted) {
                    IconButton(
                        onClick = { onStartFocus() },
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppTheme.colors.accent.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Фокус",
                            tint = AppTheme.colors.accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(task: Task) {
    val isLong = task.type.isLong
    val tint = if (isLong) AppTheme.colors.accentSecondary else AppTheme.colors.accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (isLong) Icons.Default.LocalFireDepartment else Icons.Default.Bolt,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = if (isLong) "ДОЛГАЯ" else "БЫСТРАЯ",
            fontSize = 9.sp,
            color = tint,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PriorityBadge(task: Task) {
    val (prioColor, prioBg) = when (task.priority) {
        Priority.HIGH -> DuroRed to DuroRed.copy(alpha = 0.15f)
        Priority.MEDIUM -> DuroAmber to DuroAmber.copy(alpha = 0.15f)
        Priority.LOW -> DuroTextMuted to DuroTextMuted.copy(alpha = 0.15f)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(prioBg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = when (task.priority) {
                Priority.HIGH -> "СРОЧНО"
                Priority.MEDIUM -> "СРЕДНЯЯ"
                Priority.LOW -> "НИЗКАЯ"
            },
            fontSize = 9.sp,
            color = prioColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/** G13: дедлайн в прошлом и задача не закрыта — просрочка. */
private fun isOverdue(dueDateIso: String): Boolean {
    return try {
        val parsed = java.time.LocalDateTime.parse(dueDateIso.take(19))
        parsed.isBefore(java.time.LocalDateTime.now())
    } catch (e: Exception) {
        try {
            java.time.LocalDate.parse(dueDateIso.take(10)).isBefore(java.time.LocalDate.now())
        } catch (e2: Exception) {
            false
        }
    }
}

/** Форматирование дедлайна с аккуратными русскими месяцами (без артефактов вроде 'М09') */
private fun formatTaskDue(dueDateIso: String): String {
    val months = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    return try {
        val parsed = java.time.LocalDateTime.parse(dueDateIso.take(19))
        val monthName = months.getOrElse(parsed.monthValue - 1) { "" }
        val day = parsed.dayOfMonth
        val hasTime = dueDateIso.length >= 16 && !dueDateIso.endsWith("T00:00")
        if (hasTime) {
            "%02d:%02d · %d %s".format(parsed.hour, parsed.minute, day, monthName)
        } else {
            "%d %s".format(day, monthName)
        }
    } catch (e: Exception) {
        try {
            val parsedDate = java.time.LocalDate.parse(dueDateIso.take(10))
            val monthName = months.getOrElse(parsedDate.monthValue - 1) { "" }
            "%d %s".format(parsedDate.dayOfMonth, monthName)
        } catch (e2: Exception) {
            dueDateIso.replace("T", " ").take(16)
        }
    }
}
