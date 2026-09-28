package com.voicehabit.tracker.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.data.local.entity.VoiceLogEntity
import com.voicehabit.tracker.presentation.theme.*
import com.voicehabit.tracker.worker.VoiceUploadWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceQueueBottomSheet(
    logs: List<VoiceLogEntity>,
    onReapply: (VoiceLogEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101016),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = DuroOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Очередь и история записей",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DuroTextPrimary
                    )
                }

                Surface(
                    color = DuroOrange.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${logs.size} записей",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DuroOrange,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = DuroTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Пока нет сохраненных записей",
                            fontSize = 14.sp,
                            color = DuroTextMuted
                        )
                        Text(
                            text = "Нажмите на микрофон внизу, чтобы сказать привычку",
                            fontSize = 12.sp,
                            color = DuroTextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(logs, key = { it.id }) { log ->
                        val dateString = try {
                            dateFormat.format(Date(log.createdAt))
                        } catch (e: Exception) {
                            ""
                        }

                        Surface(
                            color = DuroSurface,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateString,
                                        fontSize = 11.sp,
                                        color = DuroTextMuted,
                                        fontWeight = FontWeight.Medium
                                    )

                                    // Раньше здесь было ровно два состояния, и «в очереди»
                                    // было недостижимо: статус в БД всегда писался PROCESSED.
                                    val (statusLabel, statusColor) = when (log.status) {
                                        VoiceUploadWorker.VOICE_STATUS_APPLIED ->
                                            "Применено" to DairySuccess
                                        VoiceUploadWorker.VOICE_STATUS_PENDING_UPLOAD ->
                                            "В очереди" to DairyWarning
                                        VoiceUploadWorker.VOICE_STATUS_FAILED ->
                                            "Ошибка" to DairyDanger
                                        else ->
                                            "Разобрано" to AppTheme.colors.accent
                                    }
                                    Surface(
                                        color = statusColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Transcript
                                Text(
                                    text = "«${log.rawTranscript.ifBlank { "Голосовая команда" }}»",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DuroTextPrimary
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Actions Summary
                                Text(
                                    text = log.summary,
                                    fontSize = 12.sp,
                                    color = DuroTextSecondary
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { onReapply(log) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = DuroOrange
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Применить повторно", fontSize = 11.sp, color = DuroOrange)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
