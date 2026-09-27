package com.voicehabit.tracker.presentation.digests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.core.text.RussianPlural
import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.domain.model.IntentMode
import com.voicehabit.tracker.presentation.hub.ScreenHeader
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.*
import com.voicehabit.tracker.presentation.voice.DigestSection
import com.voicehabit.tracker.presentation.voice.modeAccent

/**
 * H1. Конспекты свободного потока: «диктофон, который сделал выжимку разговора».
 *
 * Экран состоит из двух состояний в одной компоновке: список и чтение. Отдельная
 * навигация ради одного экрана означала бы ещё одну точку отказа в `AppScreen` и
 * ещё одно состояние в `HomeState`, а читается конспект обычно сразу после записи.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DigestsScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    var readingId by remember { mutableStateOf<String?>(null) }
    val reading = state.digests.firstOrNull { it.id == readingId }

    if (reading != null) {
        DigestReader(
            digest = reading,
            onBack = { readingId = null },
            onTogglePin = { viewModel.toggleDigestPinned(reading.id) },
            onDelete = {
                viewModel.deleteDigest(reading.id)
                readingId = null
            },
            onStepToTask = { step -> viewModel.addNextStepAsTask(reading, step) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(DuroBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(
            title = "Конспекты",
            subtitle = if (state.digests.isEmpty()) {
                "Пока пусто"
            } else {
                // «3 выжимок» — заметная ошибка в первом же заголовке, который
                // пользователь видит, открыв экран.
                RussianPlural.count(
                    state.digests.size,
                    "выжимка разговора",
                    "выжимки разговоров",
                    "выжимок разговоров"
                )
            }
        ) { viewModel.closeScreen() }
        Spacer(modifier = Modifier.height(16.dp))

        if (state.digests.isEmpty()) {
            EmptyDigests(modifier = Modifier.weight(1f))
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(state.digests, key = { it.id }) { digest ->
                DigestCard(digest = digest, onClick = { readingId = digest.id })
            }
        }
    }
}

@Composable
private fun EmptyDigests(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "◎", fontSize = 56.sp, color = DuroPurple)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Здесь появятся выжимки разговоров",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = DuroTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Просто наговори в микрофон — не задачу, а мысли, идеи или " +
                "пересказ разговора. Приложение само поймёт, что это не поручение, " +
                "и соберёт конспект: суть, решения, открытые вопросы и что дальше. " +
                "Список дел при этом не пострадает.",
            fontSize = 13.sp,
            color = DuroTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DigestCard(digest: DigestRecord, onClick: () -> Unit) {
    val accent = modeAccent(digest.mode)
    Surface(
        color = DuroSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = digest.mode.emoji, fontSize = 13.sp, color = accent)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = digest.mode.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (digest.durationLabel.isNotBlank()) {
                    Text(
                        text = "🎙 ${digest.durationLabel}",
                        fontSize = 10.sp,
                        color = DuroTextMuted
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (digest.pinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Закреплено",
                        tint = DuroAmber,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = digest.title.ifBlank { "Без темы" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DuroTextPrimary
            )
            if (digest.preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = digest.preview,
                    fontSize = 12.sp,
                    color = DuroTextSecondary,
                    maxLines = 3
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (digest.decisions.isNotEmpty()) {
                    MetaPill("✓ ${digest.decisions.size}", DuroLime)
                }
                if (digest.nextSteps.isNotEmpty()) {
                    MetaPill("→ ${digest.nextSteps.size}", DuroCyan)
                }
                if (digest.openQuestions.isNotEmpty()) {
                    MetaPill("? ${digest.openQuestions.size}", DuroAmber)
                }
                if (digest.people.isNotEmpty()) {
                    MetaPill("☺ ${digest.people.size}", DuroPurple)
                }
                if (digest.tone.isNotBlank()) {
                    MetaPill(digest.tone, DuroPink)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatDigestDate(digest.createdAt),
                fontSize = 10.sp,
                color = DuroTextMuted
            )
        }
    }
}

@Composable
private fun MetaPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DigestReader(
    digest: DigestRecord,
    onBack: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onStepToTask: (String) -> Unit
) {
    val accent = modeAccent(digest.mode)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(DuroBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(
            title = "Конспект",
            subtitle = "${digest.mode.label} · уверенность ${(digest.modeConfidence * 100).toInt()}% · ${formatDigestDate(digest.createdAt)}"
        ) { onBack() }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onTogglePin,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (digest.pinned) DuroAmber else DuroTextSecondary
                )
            ) {
                Text(if (digest.pinned) "Открепить" else "Закрепить", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DuroRed)
            ) {
                Text("Удалить", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = digest.title.ifBlank { "Без темы" },
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DuroTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (digest.gist.isNotBlank()) {
                Surface(
                    color = accent.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "СУТЬ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = digest.gist, fontSize = 14.sp, color = DuroTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            DigestSection.entries.forEach { section ->
                val items = digest.itemsOf(section)
                if (items.isNotEmpty()) {
                    val sectionAccent = when (section) {
                        DigestSection.DECISIONS -> DuroLime
                        DigestSection.NEXT_STEPS -> DuroCyan
                        DigestSection.OPEN_QUESTIONS -> DuroAmber
                        DigestSection.KEY_POINTS -> DuroPurple
                        DigestSection.PEOPLE -> DuroPink
                        DigestSection.NUMBERS -> DuroTextSecondary
                    }
                    Text(
                        text = "${section.emoji} ${section.title}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = sectionAccent,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    items.forEach { item ->
                        Surface(
                            color = DuroSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = item,
                                    fontSize = 13.sp,
                                    color = DuroTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                // Мост в список дел: конспект без действия остаётся
                                // просто текстом, а это ровно тот случай, где человек
                                // потом «не может вспомнить, о чём говорил».
                                if (section == DigestSection.NEXT_STEPS) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "→ задача",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DuroCyan,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onStepToTask(item) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            if (digest.tone.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "ТОН РАЗГОВОРА", fontSize = 10.sp, color = DuroTextMuted, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    MetaPill(digest.tone, DuroPink)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (digest.transcript.isNotBlank()) {
                Text(
                    text = "ИСХОДНЫЙ ТРАНСКРИПТ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = DuroTextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = digest.transcript,
                    fontSize = 12.sp,
                    color = DuroTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(READER_BOTTOM_PADDING))
        }
    }
}

/**
 * Отступ снизу под плавающую кнопку записи.
 *
 * Читатель — единственный экран, где скроллится длинный сырой текст, и он же
 * единственный, где FAB оказывался поверх последних строк транскрипта: 32dp не хватало
 * на кнопку 62dp плюс навигационную панель.
 */
private val READER_BOTTOM_PADDING = 132.dp

private fun DigestRecord.itemsOf(section: DigestSection): List<String> = when (section) {
    DigestSection.KEY_POINTS -> keyPoints
    DigestSection.DECISIONS -> decisions
    DigestSection.OPEN_QUESTIONS -> openQuestions
    DigestSection.NEXT_STEPS -> nextSteps
    DigestSection.PEOPLE -> people
    DigestSection.NUMBERS -> numbers
}

private fun formatDigestDate(millis: Long): String = runCatching {
    val date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    when (date) {
        today -> "сегодня"
        today.minusDays(1) -> "вчера"
        else -> date.toString()
    }
}.getOrDefault("")
