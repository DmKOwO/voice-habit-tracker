package com.voicehabit.tracker.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.core.logging.ActiveOperation
import com.voicehabit.tracker.core.logging.LogEvent
import com.voicehabit.tracker.core.logging.LogLevel
import com.voicehabit.tracker.presentation.theme.*
import kotlin.math.max

/**
 * Журнал фоновых операций.
 *
 * Существует потому, что фоновая работа (миграция БД, сид, расшифровка записи,
 * очередь, обновление виджетов, OTA) раньше была полностью невидимой: интерфейс
 * просто «ничего не делал», а причину можно было выяснить только в логасте.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsLogBottomSheet(
    activeOperations: List<ActiveOperation>,
    events: List<LogEvent>,
    levelFilter: LogLevel?,
    onFilterChange: (LogLevel?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(events, levelFilter) {
        if (levelFilter == null) events else events.filter { it.level.priority >= levelFilter.priority }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101016),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ЖУРНАЛ ОПЕРАЦИЙ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.accent,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (activeOperations.isEmpty()) {
                            "Фоновых задач нет"
                        } else {
                            "Активно: ${activeOperations.size}"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (activeOperations.isEmpty()) DuroLime else DuroAmber
                    )
                }

                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Очистить журнал",
                        tint = DuroTextMuted
                    )
                }
            }

            if (activeOperations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                activeOperations.forEach { operation ->
                    val elapsed = max(0, (System.currentTimeMillis() - operation.startedAtMillis) / 1000)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DuroSurfaceElevated)
                            .padding(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = DuroAmber,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = operation.title,
                            fontSize = 13.sp,
                            color = DuroTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${elapsed}s",
                            fontSize = 12.sp,
                            color = DuroTextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipLabel("Все", levelFilter == null) { onFilterChange(null) }
                FilterChipLabel("Инфо", levelFilter == LogLevel.INFO) { onFilterChange(LogLevel.INFO) }
                FilterChipLabel("Ошибки", levelFilter == LogLevel.ERROR) { onFilterChange(LogLevel.ERROR) }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filtered.isEmpty()) {
                Text(
                    text = "Событий пока нет. Журнал наполняется миграциями, сидом, записью и очередью.",
                    fontSize = 12.sp,
                    color = DuroTextMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered.asReversed(), key = { "${it.timestampMillis}-${it.tag}-${it.message.hashCode()}" }) { event ->
                        LogRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AppTheme.colors.accent.copy(alpha = 0.18f) else DuroSurface)
            .border(1.dp, if (selected) AppTheme.colors.accent else DuroBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) AppTheme.colors.accent else DuroTextSecondary
        )
    }
}

@Composable
private fun LogRow(event: LogEvent) {
    val levelColor = when (event.level) {
        LogLevel.DEBUG -> DuroTextMuted
        LogLevel.INFO -> AppTheme.colors.accent
        LogLevel.WARN -> DuroAmber
        LogLevel.ERROR -> DuroRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DuroSurface)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = event.level.label,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = levelColor
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.message,
                fontSize = 12.sp,
                color = DuroTextPrimary,
                fontFamily = FontFamily.Monospace
            )
            if (event.attributes.isNotEmpty()) {
                Text(
                    text = event.attributes.entries.joinToString(" ") { "${it.key}=${it.value}" },
                    fontSize = 10.sp,
                    color = DuroTextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Text(
            text = event.tag,
            fontSize = 10.sp,
            color = DuroTextMuted
        )
    }
}
