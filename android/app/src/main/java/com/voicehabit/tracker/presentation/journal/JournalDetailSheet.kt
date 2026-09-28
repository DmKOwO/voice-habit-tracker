package com.voicehabit.tracker.presentation.journal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.voicehabit.tracker.core.obsidian.ObsidianMarkdownFormatter
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
    val state by viewModel.state.collectAsState()
    var isPinned by remember(digest.pinned) { mutableStateOf(digest.pinned) }
    var expandedTranscript by remember { mutableStateOf(false) }
    val addedSteps = remember { mutableStateListOf<String>() }

    val playbackState by viewModel.audioPlayer.playbackState.collectAsState()
    var audioPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(digest.voiceLogId) {
        audioPath = viewModel.getAudioPath(digest.voiceLogId)
    }

    val openVaultFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            haptics.confirm()
            viewModel.setObsidianVaultUri(it)
            viewModel.exportRecordToObsidian(digest) { success, path ->
                if (success) {
                    Toast.makeText(context, "Сохранено в Obsidian: $path", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val isThisAudioPlaying = playbackState.isPlaying && playbackState.currentAudioPath == audioPath

    val formattedDate = remember(digest.createdAt) {
        val sdf = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru"))
        sdf.format(Date(if (digest.createdAt > 0) digest.createdAt else System.currentTimeMillis()))
    }

    fun generateMarkdown(): String = ObsidianMarkdownFormatter.formatJournal(
        record = digest,
        audioRelativePath = if (audioPath != null) "attachments/voice_${digest.createdAt}_${digest.id.takeLast(6)}.m4a" else null
    )

    Scaffold(
        containerColor = AppTheme.colors.background,
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
                        .background(AppTheme.colors.surface)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = AppTheme.colors.textPrimary
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
                            .background(if (isPinned) AppTheme.colors.accent.copy(alpha = 0.2f) else AppTheme.colors.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Закрепить",
                            tint = if (isPinned) AppTheme.colors.accent else AppTheme.colors.textSecondary
                        )
                    }

                    // Obsidian MD / Save / Copy Button
                    IconButton(
                        onClick = {
                            haptics.confirm()
                            val md = generateMarkdown()
                            if (state.isObsidianConfigured) {
                                viewModel.exportRecordToObsidian(digest) { success, path ->
                                    if (success) {
                                        Toast.makeText(context, "Сохранено в Obsidian: $path", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Obsidian Journal Note", md))
                                Toast.makeText(context, "Markdown скопирован. Выберите папку Vault для сохранения.", Toast.LENGTH_LONG).show()
                                openVaultFolderLauncher.launch(null)
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (state.isObsidianConfigured) AppTheme.colors.accent.copy(alpha = 0.2f) else AppTheme.colors.surface)
                    ) {
                        Icon(
                            imageVector = if (state.isObsidianConfigured) Icons.Default.Save else Icons.Default.ContentCopy,
                            contentDescription = "Obsidian MD",
                            tint = if (state.isObsidianConfigured) AppTheme.colors.accent else AppTheme.colors.textPrimary
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
                            .background(AppTheme.colors.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться",
                            tint = AppTheme.colors.textPrimary
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
                            .background(AppTheme.colors.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Удалить",
                            tint = AppTheme.colors.error
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
                        color = AppTheme.colors.textMuted
                    )

                    if (digest.tone.isNotBlank()) {
                        Surface(
                            color = AppTheme.colors.accent.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = digest.tone,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.accent,
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
                    color = AppTheme.colors.textPrimary
                )
            }

            // Audio Player Bar (if recording exists)
            item {
                Surface(
                    color = AppTheme.colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
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
                                .background(AppTheme.colors.accent)
                        ) {
                            Icon(
                                imageVector = if (isThisAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isThisAudioPlaying) "Пауза" else "Слушать",
                                tint = AppTheme.colors.surface
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isThisAudioPlaying) "Воспроизведение записи…" else "Оригинальная голосовая запись",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
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
                                color = AppTheme.colors.textSecondary
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
                                color = AppTheme.colors.accent
                            )
                        }
                    }
                }
            }

            // Core Insight (Суть мысли) with quote border
            if (digest.gist.isNotBlank()) {
                item {
                    Surface(
                        color = AppTheme.colors.surfaceElevated,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.accent.copy(alpha = 0.35f)),
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
                                    .background(AppTheme.colors.accent, RoundedCornerShape(2.dp))
                            )
                            Column {
                                Text(
                                    text = "СУТЬ (CORE INSIGHT)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.accent,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "«${digest.gist}»",
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AppTheme.colors.textPrimary
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
                        color = AppTheme.colors.textPrimary
                    )
                }

                items(digest.keyPoints) { point ->
                    Surface(
                        color = AppTheme.colors.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(6.dp)
                                    .background(AppTheme.colors.accent, CircleShape)
                            )
                            Text(
                                text = point,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = AppTheme.colors.textPrimary
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
                        color = AppTheme.colors.textPrimary
                    )
                }

                items(digest.decisions) { decision ->
                    Surface(
                        color = AppTheme.colors.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AppTheme.colors.accent,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                            Text(
                                text = decision,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = AppTheme.colors.textPrimary
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
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "нажмите для создания задачи",
                            fontSize = 11.sp,
                            color = AppTheme.colors.textMuted
                        )
                    }
                }

                items(digest.nextSteps) { step ->
                    val isAdded = addedSteps.contains(step)
                    Surface(
                        color = if (isAdded) AppTheme.colors.surfaceElevated else AppTheme.colors.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAdded) AppTheme.colors.accent.copy(alpha = 0.5f) else AppTheme.colors.border
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
                                Icon(
                                    imageVector = if (isAdded) Icons.Default.Check else Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = if (isAdded) AppTheme.colors.accent else AppTheme.colors.textMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = step,
                                    fontSize = 13.sp,
                                    color = if (isAdded) AppTheme.colors.accent else AppTheme.colors.textPrimary
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
                                    containerColor = if (isAdded) AppTheme.colors.surfaceElevated else AppTheme.colors.accent,
                                    contentColor = if (isAdded) AppTheme.colors.accent else AppTheme.colors.surface
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isAdded) "В списке" else "+ В задачи",
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
                        color = AppTheme.colors.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
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
                                        tint = AppTheme.colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Полная расшифровка (Транскрипт)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                }
                                Text(
                                    text = "${digest.transcript.split(' ').filter { it.isNotBlank() }.size} слов",
                                    fontSize = 11.sp,
                                    color = AppTheme.colors.textMuted
                                )
                            }

                            AnimatedVisibility(visible = expandedTranscript) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    HorizontalDivider(color = AppTheme.colors.border)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = digest.transcript,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Obsidian Export Hint & Quick Action
            item {
                Surface(
                    color = AppTheme.colors.surfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (state.isObsidianConfigured) AppTheme.colors.accent.copy(alpha = 0.4f) else AppTheme.colors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptics.confirm()
                            if (state.isObsidianConfigured) {
                                viewModel.exportRecordToObsidian(digest) { success, path ->
                                    if (success) {
                                        Toast.makeText(context, "Сохранено в Obsidian: $path", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                val md = generateMarkdown()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Obsidian Journal Note", md))
                                Toast.makeText(context, "Скопировано в буфер. Выберите Vault для прямого сохранения.", Toast.LENGTH_SHORT).show()
                                openVaultFolderLauncher.launch(null)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.voicehabit.tracker.R.drawable.ic_dairy_logo),
                            contentDescription = null,
                            tint = AppTheme.colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.isObsidianConfigured) {
                                    "Obsidian Vault: ${state.obsidianVaultName}"
                                } else {
                                    "Синхронизация с Obsidian Vault"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isObsidianConfigured) AppTheme.colors.accent else AppTheme.colors.textPrimary
                            )
                            Text(
                                text = if (state.isObsidianConfigured) {
                                    "Нажмите здесь для мгновенного сохранения заметки и аудио в Voice Journal/"
                                } else {
                                    "Нажмите, чтобы скопировать Markdown и выбрать папку хранилища"
                                },
                                fontSize = 11.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
