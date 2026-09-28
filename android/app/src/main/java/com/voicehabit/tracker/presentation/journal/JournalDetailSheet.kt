package com.voicehabit.tracker.presentation.journal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalDetailSheet(
    digest: DigestRecord,
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberDuroHaptics()
    var isPinned by remember(digest.pinned) { mutableStateOf(digest.pinned) }
    var expandedTranscript by remember { mutableStateOf(false) }
    val addedSteps = remember { mutableStateListOf<String>() }

    val playbackState by viewModel.audioPlayer.playbackState.collectAsState()
    var audioPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(digest.voiceLogId) {
        audioPath = viewModel.getAudioPath(digest.voiceLogId)
    }

    val isThisAudioPlaying = playbackState.isPlaying && playbackState.currentAudioPath == audioPath

    val formattedDate = remember(digest.createdAt) {
        val sdf = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru"))
        sdf.format(Date(if (digest.createdAt > 0) digest.createdAt else System.currentTimeMillis()))
    }

    fun generateMarkdown(): String = buildString {
        appendLine("# ${digest.title.ifBlank { "Запись в дневнике" }}")
        appendLine()
        appendLine("- **Дата:** $formattedDate")
        if (digest.tone.isNotBlank()) appendLine("- **Настроение:** ${digest.tone}")
        if (digest.speechSeconds > 0) appendLine("- **Длительность:** ${digest.speechSeconds / 60} мин ${digest.speechSeconds % 60} сек")
        appendLine()
        if (digest.gist.isNotBlank()) {
            appendLine("## Суть (Core Insight)")
            appendLine("> ${digest.gist}")
            appendLine()
        }
        if (digest.keyPoints.isNotEmpty()) {
            appendLine("## Ключевые тезисы")
            digest.keyPoints.forEach { appendLine("- $it") }
            appendLine()
        }
        if (digest.decisions.isNotEmpty()) {
            appendLine("## Принятые решения")
            digest.decisions.forEach { appendLine("- [x] $it") }
            appendLine()
        }
        if (digest.nextSteps.isNotEmpty()) {
            appendLine("## Следующие шаги")
            digest.nextSteps.forEach { appendLine("- [ ] $it") }
            appendLine()
        }
        if (digest.transcript.isNotBlank()) {
            appendLine("## Полная расшифровка")
            appendLine(digest.transcript)
        }
    }

    Scaffold(
        containerColor = DuroBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DuroSurface)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = DuroTextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Pin Button
                    IconButton(
                        onClick = {
                            haptics.select()
                            isPinned = !isPinned
                            viewModel.toggleDigestPinned(digest.id)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isPinned) DuroJournalLavender.copy(alpha = 0.2f) else DuroSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Закрепить",
                            tint = if (isPinned) DuroJournalLavender else DuroTextSecondary
                        )
                    }

                    // Export / Copy Button
                    IconButton(
                        onClick = {
                            haptics.confirm()
                            val md = generateMarkdown()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Obsidian Journal Note", md))
                            viewModel.showSnackbar("Скопировано в буфер обмена в формате Markdown (Obsidian)")
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DuroSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Копировать Markdown",
                            tint = DuroTextPrimary
                        )
                    }

                    // Share Button
                    IconButton(
                        onClick = {
                            haptics.select()
                            val md = generateMarkdown()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, md)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Поделиться мыслью"))
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DuroSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться",
                            tint = DuroTextPrimary
                        )
                    }

                    // Delete Button
                    IconButton(
                        onClick = {
                            haptics.confirm()
                            viewModel.deleteDigest(digest.id)
                            onBack()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DuroSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Удалить",
                            tint = DuroRed
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header Info & Mood Tag
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedDate,
                        fontSize = 13.sp,
                        color = DuroTextMuted
                    )

                    if (digest.tone.isNotBlank()) {
                        Surface(
                            color = DuroJournalLavender.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = digest.tone,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuroJournalLavender,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Big Bold Title (Medium / Notion Style)
            item {
                Text(
                    text = digest.title.ifBlank { "Запись в дневнике" },
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DuroTextPrimary
                )
            }

            // Audio Player Bar (if recording exists)
            item {
                Surface(
                    color = DuroSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptics.select()
                                viewModel.playAudioForRecord(digest)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(DuroJournalLavender)
                        ) {
                            Icon(
                                imageVector = if (isThisAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isThisAudioPlaying) "Пауза" else "Слушать",
                                tint = Color.White
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isThisAudioPlaying) "Воспроизведение записи…" else "Оригинальная голосовая запись",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DuroTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val durationLabel = if (digest.speechSeconds > 0) {
                                "%d мин %02d сек речи".format(digest.speechSeconds / 60, digest.speechSeconds % 60)
                            } else {
                                "Аудиозапись мысли"
                            }
                            Text(
                                text = durationLabel,
                                fontSize = 11.sp,
                                color = DuroTextSecondary
                            )
                        }

                        if (isThisAudioPlaying && playbackState.totalDurationMs > 0) {
                            Text(
                                text = "%02d:%02d".format(
                                    playbackState.currentPositionMs / 1000 / 60,
                                    (playbackState.currentPositionMs / 1000) % 60
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuroJournalLavender
                            )
                        }
                    }
                }
            }

            // Core Insight (Суть мысли) with quote border
            if (digest.gist.isNotBlank()) {
                item {
                    Surface(
                        color = DuroSurfaceElevated,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DuroJournalLavender.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(56.dp)
                                    .background(DuroJournalLavender, RoundedCornerShape(2.dp))
                            )
                            Column {
                                Text(
                                    text = "СУТЬ (CORE INSIGHT)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DuroJournalLavender,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "«${digest.gist}»",
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DuroTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Structured Key Points (Тезисы)
            if (digest.keyPoints.isNotEmpty()) {
                item {
                    Text(
                        text = "Ключевые тезисы",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = DuroTextPrimary
                    )
                }

                items(digest.keyPoints) { point ->
                    Surface(
                        color = DuroSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "◆",
                                fontSize = 12.sp,
                                color = DuroJournalLavender,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = point,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = DuroTextPrimary
                            )
                        }
                    }
                }
            }

            // Decisions (Принятые решения)
            if (digest.decisions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Принятые решения",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = DuroTextPrimary
                    )
                }

                items(digest.decisions) { decision ->
                    Surface(
                        color = DuroSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "✓",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuroLime
                            )
                            Text(
                                text = decision,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = DuroTextPrimary
                            )
                        }
                    }
                }
            }

            // Actions from thought (Действия из мысли -> Превращение в задачу)
            if (digest.nextSteps.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Действия из мысли",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroTextPrimary
                        )
                        Text(
                            text = "нажмите для создания задачи",
                            fontSize = 11.sp,
                            color = DuroTextMuted
                        )
                    }
                }

                items(digest.nextSteps) { step ->
                    val isAdded = addedSteps.contains(step)
                    Surface(
                        color = if (isAdded) DuroSurfaceElevated else DuroSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAdded) DuroOrange.copy(alpha = 0.5f) else DuroBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isAdded) "✓" else "→",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAdded) DuroOrange else DuroTextMuted
                                )
                                Text(
                                    text = step,
                                    fontSize = 13.sp,
                                    color = if (isAdded) DuroOrange else DuroTextPrimary
                                )
                            }

                            Button(
                                onClick = {
                                    if (!isAdded) {
                                        haptics.confirm()
                                        addedSteps.add(step)
                                        viewModel.addNextStepAsTask(digest, step)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAdded) DuroSurfaceElevated else DuroOrange,
                                    contentColor = if (isAdded) DuroOrange else Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isAdded) "В списке ✓" else "+ В задачи",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Full Transcript (Полная расшифровка)
            if (digest.transcript.isNotBlank()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = DuroSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedTranscript = !expandedTranscript }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (expandedTranscript) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = DuroTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Полная расшифровка (Транскрипт)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DuroTextPrimary
                                    )
                                }
                                Text(
                                    text = "${digest.transcript.split(' ').filter { it.isNotBlank() }.size} слов",
                                    fontSize = 11.sp,
                                    color = DuroTextMuted
                                )
                            }

                            AnimatedVisibility(visible = expandedTranscript) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    HorizontalDivider(color = DuroBorder)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = digest.transcript,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = DuroTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Obsidian Export Hint
            item {
                Surface(
                    color = DuroSurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "💎", fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Синхронизация с Obsidian",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuroTextPrimary
                            )
                            Text(
                                text = "Нажмите иконку копирования вверху, чтобы вставить готовую заметку с метаданными в свой Vault",
                                fontSize = 11.sp,
                                color = DuroTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
