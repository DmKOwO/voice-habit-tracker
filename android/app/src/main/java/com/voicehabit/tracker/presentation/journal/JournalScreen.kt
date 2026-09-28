package com.voicehabit.tracker.presentation.journal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.core.text.RussianPlural
import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.*
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun JournalScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val haptics = rememberDuroHaptics()

    // If reading detail
    val readingRecord = state.selectedJournalForDetail
    if (readingRecord != null) {
        JournalDetailSheet(
            digest = readingRecord,
            viewModel = viewModel,
            onBack = { viewModel.closeJournalDetail() }
        )
        return
    }

    val query = state.journalSearchQuery.trim().lowercase(Locale.getDefault())
    val selectedMood = state.journalFilterMood
    val selectedTag = state.journalFilterTag

    // Filter digests
    val filteredDigests = remember(state.digests, query, selectedMood, selectedTag) {
        state.digests.filter { record ->
            val matchesQuery = query.isBlank() ||
                record.title.lowercase(Locale.getDefault()).contains(query) ||
                record.gist.lowercase(Locale.getDefault()).contains(query) ||
                record.keyPoints.any { it.lowercase(Locale.getDefault()).contains(query) } ||
                record.transcript.lowercase(Locale.getDefault()).contains(query)

            val matchesMood = selectedMood == null || record.tone.equals(selectedMood, ignoreCase = true)
            val matchesTag = selectedTag == null || when (selectedTag) {
                "Закрепленные" -> record.pinned
                "Идеи" -> record.title.contains("иде", ignoreCase = true) || record.gist.contains("иде", ignoreCase = true)
                "Работа" -> record.title.contains("работ", ignoreCase = true) || record.title.contains("проект", ignoreCase = true)
                "Планы" -> record.nextSteps.isNotEmpty()
                else -> true
            }

            matchesQuery && matchesMood && matchesTag
        }
    }

    // Grouping by time
    val groupedDigests = remember(filteredDigests) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val weekAgo = today.minusDays(7)

        val pinned = filteredDigests.filter { it.pinned }
        val unpinned = filteredDigests.filter { !it.pinned }

        val groups = mutableListOf<Pair<String, List<DigestRecord>>>()

        if (pinned.isNotEmpty()) {
            groups.add("Закреплённые мысли" to pinned)
        }

        val todayItems = unpinned.filter {
            val recordDate = Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            recordDate == today
        }
        if (todayItems.isNotEmpty()) groups.add("Сегодня" to todayItems)

        val yesterdayItems = unpinned.filter {
            val recordDate = Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            recordDate == yesterday
        }
        if (yesterdayItems.isNotEmpty()) groups.add("Вчера" to yesterdayItems)

        val weekItems = unpinned.filter {
            val recordDate = Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            recordDate < yesterday && recordDate >= weekAgo
        }
        if (weekItems.isNotEmpty()) groups.add("На этой неделе" to weekItems)

        val olderItems = unpinned.filter {
            val recordDate = Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            recordDate < weekAgo
        }
        if (olderItems.isNotEmpty()) groups.add("Ранее" to olderItems)

        groups
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Bar: Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.voicehabit.tracker.R.drawable.ic_journal_night),
                        contentDescription = null,
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Дневник мыслей",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = AppTheme.colors.textPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (state.digests.isEmpty()) "Хроника рефлексий и голосовых заметок" else RussianPlural.count(
                        state.digests.size,
                        "запись мыслей",
                        "записи мыслей",
                        "записей мыслей"
                    ),
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search bar
        OutlinedTextField(
            value = state.journalSearchQuery,
            onValueChange = { viewModel.setJournalSearchQuery(it) },
            placeholder = { Text("Поиск по мыслям и расшифровкам…", fontSize = 13.sp, color = AppTheme.colors.textMuted) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Поиск",
                    tint = AppTheme.colors.accent,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (state.journalSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setJournalSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Очистить",
                            tint = AppTheme.colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = AppTheme.colors.textPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.colors.accent,
                unfocusedBorderColor = AppTheme.colors.border,
                cursorColor = AppTheme.colors.accent,
                focusedContainerColor = AppTheme.colors.surface,
                unfocusedContainerColor = AppTheme.colors.surface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Mood & Tag Filter Chips (комфортная высота 38dp для точного тапа пальцем)
        val moodFilters = listOf("Все", "Спокойное", "Вдохновленное", "Тревожное", "Аналитическое", "Закрепленные", "Идеи", "Планы")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(moodFilters) { filter ->
                val isSelected = when (filter) {
                    "Все" -> selectedMood == null && selectedTag == null
                    "Спокойное", "Вдохновленное", "Тревожное", "Аналитическое" -> selectedMood == filter
                    else -> selectedTag == filter
                }

                Surface(
                    color = if (isSelected) AppTheme.colors.accent else AppTheme.colors.surface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) AppTheme.colors.accent else AppTheme.colors.border
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            haptics.select()
                            when (filter) {
                                "Все" -> {
                                    viewModel.setJournalFilterMood(null)
                                    viewModel.setJournalFilterTag(null)
                                }
                                "Спокойное", "Вдохновленное", "Тревожное", "Аналитическое" -> {
                                    viewModel.setJournalFilterMood(if (selectedMood == filter) null else filter)
                                }
                                else -> {
                                    viewModel.setJournalFilterTag(if (selectedTag == filter) null else filter)
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) (if (LocalAppPalette.current.id == "noir_ink") Color(0xFF101014) else Color.White) else AppTheme.colors.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content List
        if (filteredDigests.isEmpty()) {
            EmptyJournalView(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                groupedDigests.forEach { (groupTitle, items) ->
                    item(key = "header_$groupTitle") {
                        Text(
                            text = groupTitle.uppercase(Locale.getDefault()),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.accent,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(items, key = { it.id }) { record ->
                        JournalThoughtCard(
                            record = record,
                            viewModel = viewModel,
                            onClick = { viewModel.openJournalDetail(record) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JournalThoughtCard(
    record: DigestRecord,
    viewModel: HomeViewModel,
    onClick: () -> Unit
) {
    val haptics = rememberDuroHaptics()
    val playbackState by viewModel.audioPlayer.playbackState.collectAsState()
    var audioPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(record.voiceLogId) {
        audioPath = viewModel.getAudioPath(record.voiceLogId)
    }

    val isPlaying = playbackState.isPlaying && playbackState.currentAudioPath == audioPath

    val timeLabel = remember(record.createdAt) {
        val sdf = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))
        val dateStr = sdf.format(Date(if (record.createdAt > 0) record.createdAt else System.currentTimeMillis()))
        if (record.speechSeconds > 0) {
            val mins = record.speechSeconds / 60
            val secs = record.speechSeconds % 60
            "$dateStr · %d:%02d речи".format(mins, secs)
        } else {
            dateStr
        }
    }

    Surface(
        color = AppTheme.colors.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (record.pinned) AppTheme.colors.accent.copy(alpha = 0.5f) else AppTheme.colors.border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Date, Duration, Mood Pill, Pin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (record.pinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Закреплено",
                            tint = AppTheme.colors.accent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = timeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppTheme.colors.textMuted
                    )
                }

                if (record.tone.isNotBlank()) {
                    Surface(
                        color = AppTheme.colors.accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = record.tone,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = record.title.ifBlank { "Запись мыслей" },
                fontSize = 17.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Core Insight (Quote preview)
            if (record.gist.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(36.dp)
                            .background(AppTheme.colors.accent, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "«${record.gist}»",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = AppTheme.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Key Points (bullet preview up to 2)
            if (record.keyPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                record.keyPoints.take(2).forEach { point ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "•", color = AppTheme.colors.accent, fontSize = 11.sp)
                        Text(
                            text = point,
                            fontSize = 12.sp,
                            color = AppTheme.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Player / Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play audio button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isPlaying) AppTheme.colors.accent else AppTheme.colors.surfaceElevated)
                        .clickable {
                            haptics.select()
                            viewModel.playAudioForRecord(record)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Слушать запись",
                        tint = if (isPlaying) AppTheme.colors.surface else AppTheme.colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isPlaying) "Играет…" else "Слушать голос",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPlaying) AppTheme.colors.surface else AppTheme.colors.textPrimary
                    )
                }

                // Quick next-steps to task conversion
                if (record.nextSteps.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppTheme.colors.accent.copy(alpha = 0.12f))
                            .clickable {
                                haptics.confirm()
                                val step = record.nextSteps.first()
                                viewModel.addNextStepAsTask(record, step)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = "→", fontSize = 12.sp, color = AppTheme.colors.accent, fontWeight = FontWeight.Bold)
                        Text(
                            text = "+ В задачи",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyJournalView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = AppTheme.colors.surface,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.voicehabit.tracker.R.drawable.ic_journal_night),
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Пространство ваших мыслей",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Зажмите кнопку микрофона и просто говорите. Делитесь рефлексией, переживаниями или идеями без команд «напомни/сделай». dairy сам бережно структурирует суть, тезисы и настроение.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = AppTheme.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
