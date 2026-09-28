package com.voicehabit.tracker.presentation.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.domain.model.CaptureDigest
import com.voicehabit.tracker.domain.model.IntentMode
import com.voicehabit.tracker.domain.model.TaskType
import com.voicehabit.tracker.domain.model.VoiceNoteAction
import com.voicehabit.tracker.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewBottomSheet(
    action: VoiceNoteAction,
    onApply: (VoiceNoteAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSpeak: (String) -> Unit = {},
    onOverrideMode: (IntentMode) -> Unit = {}
) {
    var habitsState by remember { mutableStateOf(action.habitsCompleted) }
    var tasksState by remember { mutableStateOf(action.tasksToAdd) }
    var deleteState by remember { mutableStateOf(action.tasksToDelete) }
    var rescheduleState by remember { mutableStateOf(action.tasksToReschedule) }
    var focusState by remember { mutableStateOf(action.focusToStart) }
    var digestState by remember { mutableStateOf(action.digest) }

    fun currentAction(): VoiceNoteAction = action.copy(
        habitsCompleted = habitsState,
        tasksToAdd = tasksState,
        tasksToDelete = deleteState,
        tasksToReschedule = rescheduleState,
        focusToStart = focusState,
        digest = digestState
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DuroSurface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = DuroBorder)
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Чистый заголовок без отвлекающих таймеров и дебаг-метрик
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DuroAsterisk(size = 20.dp, color = DuroOrange)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Разбор записи",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DuroTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // H1. Что приложение само поняло о намерении + возможность поправить.
            ModeBadge(
                action = action,
                onOverride = { mode ->
                    onOverrideMode(mode)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Summary + TTS (G1)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "«${action.summary}»",
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = DuroTextPrimary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        onSpeak(action.summary)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Прослушать",
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (action.daySummaryRequested) {
                Text(
                    text = "Сводка дня будет показана и озвучена после применения",
                    fontSize = 12.sp,
                    color = AppTheme.colors.accent,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "insights") {
                        HorizontalDivider(color = DuroBorder)
                        Spacer(modifier = Modifier.height(4.dp))
            if (action.insights.isNotEmpty()) {
                        Surface(
                            color = DuroSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "КАК Я ЭТО ПОНЯЛ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.accentSecondary,
                                    letterSpacing = 1.sp
                                )
                                action.insights.take(MAX_VISIBLE_INSIGHTS).forEach { insight ->
                                    Text(
                                        text = "• $insight",
                                        fontSize = 11.sp,
                                        color = DuroTextSecondary
                                    )
                                }
                                if (action.insights.size > MAX_VISIBLE_INSIGHTS) {
                                    Text(
                                        text = "…и ещё ${action.insights.size - MAX_VISIBLE_INSIGHTS}",
                                        fontSize = 11.sp,
                                        color = DuroTextMuted
                                    )
                                }
                            }
                        }
                    }


                }

                // H1. Конспект / Дневник свободного потока.
                digestState?.let { digest ->
                    if (!digest.isEmpty) {
                        item(key = "digest") {
                            if (action.mode == IntentMode.JOURNAL) {
                                JournalReviewCard(
                                    digest = digest,
                                    transcript = action.rawTranscript,
                                    onTitleChange = { value ->
                                        digestState = digest.copy(title = value)
                                    },
                                    onSpeak = { text ->
                                        onSpeak(text)
                                    }
                                )
                            } else {
                                DigestPreview(
                                    digest = digest,
                                    editable = action.mode == IntentMode.DICTATE,
                                    onTitleChange = { value ->
                                        digestState = digest.copy(title = value)
                                    },
                                    onDropSection = { section ->
                                        digestState = digest.without(section)
                                    },
                                    onSpeak = { text ->
                                        onSpeak(text)
                                    }
                                )
                            }
                        }
                    }
                }

                if (habitsState.isNotEmpty()) {
                    item(key = "habits_header") {
                        Text(
                            text = "ПРИВЫЧКИ К ОТМЕТКЕ / СОЗДАНИЮ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroOrange,
                            letterSpacing = 1.sp
                        )
                    }

                    items(habitsState, key = { "habit_" + it.habitTitle }) { habitAction ->
                        Surface(
                            color = DuroSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = habitAction.isSelected,
                                        onCheckedChange = { checked ->
                                            habitsState = habitsState.map {
                                                if (it.habitTitle == habitAction.habitTitle) it.copy(isSelected = checked) else it
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = DuroOrange,
                                            uncheckedColor = DuroTextMuted
                                        )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = habitAction.habitTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = DuroTextPrimary
                                        )
                                        if (habitAction.comment != null) {
                                            Text(
                                                text = habitAction.comment,
                                                fontSize = 11.sp,
                                                color = DuroTextSecondary
                                            )
                                        }
                                    }
                                }

                                if (habitAction.incrementValue != null) {
                                    Text(
                                        text = "+${habitAction.incrementValue}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DuroLime
                                    )
                                }
                            }
                        }
                    }
                }

                if (deleteState.isNotEmpty() || rescheduleState.isNotEmpty() || focusState != null) {
                    item(key = "commands_header") {
                        Text(
                            text = "КОМАНДЫ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroAmber,
                            letterSpacing = 1.sp
                        )
                    }
                    deleteState.forEach { deleteAction ->
                        item(key = "del_" + deleteAction.taskTitle) {
                            CommandRow(
                                icon = Icons.Default.Delete,
                                title = deleteAction.taskTitle,
                                subtitle = "В корзину (восстановимо)",
                                checked = deleteAction.isSelected,
                                onChecked = { checked ->
                                    deleteState = deleteState.map {
                                        if (it.taskTitle == deleteAction.taskTitle) it.copy(isSelected = checked) else it
                                    }
                                }
                            )
                        }
                    }
                    rescheduleState.forEach { reschedule ->
                        item(key = "resch_" + reschedule.taskTitle) {
                            CommandRow(
                                icon = Icons.Default.Event,
                                title = reschedule.taskTitle,
                                subtitle = "Перенести" + (reschedule.newDueDate?.let { " до $it" } ?: ""),
                                checked = reschedule.isSelected,
                                onChecked = { checked ->
                                    rescheduleState = rescheduleState.map {
                                        if (it.taskTitle == reschedule.taskTitle) it.copy(isSelected = checked) else it
                                    }
                                }
                            )
                        }
                    }
                    focusState?.let { focus ->
                        item(key = "focus") {
                            CommandRow(
                                icon = Icons.Default.Timer,
                                title = "Фокус ${focus.minutes} мин",
                                subtitle = focus.label,
                                checked = focus.isSelected,
                                onChecked = { checked ->
                                    focusState = focus.copy(isSelected = checked)
                                }
                            )
                        }
                    }
                }

                if (tasksState.isNotEmpty()) {
                    item(key = "tasks_header") {
                        Text(
                            text = "НОВЫЕ ЗАДАЧИ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.accent,
                            letterSpacing = 1.sp
                        )
                    }

                    items(tasksState, key = { "task_" + it.title }) { taskAction ->
                        Surface(
                            color = DuroSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = taskAction.isSelected,
                                        onCheckedChange = { checked ->
                                            tasksState = tasksState.map {
                                                if (it.title == taskAction.title) it.copy(isSelected = checked) else it
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = DuroOrange,
                                            uncheckedColor = DuroTextMuted
                                        )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = taskAction.title,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = DuroTextPrimary
                                        )
                                        if (taskAction.dueDate != null) {
                                            Text(
                                                text = formatDue(taskAction.dueDate),
                                                fontSize = 11.sp,
                                                color = DuroOrange
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = taskAction.priority,
                                        fontSize = 10.sp,
                                        color = DuroAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (taskAction.taskType == TaskType.LONG.name) "ДОЛГАЯ" else "БЫСТРАЯ",
                                        fontSize = 9.sp,
                                        color = if (taskAction.taskType == TaskType.LONG.name) AppTheme.colors.accentSecondary else AppTheme.colors.accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DuroBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Панель осознанных действий: всегда видна и не перекрывается длинным текстом
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DuroTextMuted),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Отклонить",
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { onApply(currentAction()) },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action.mode == IntentMode.JOURNAL) DuroJournalLavender else DuroOrange,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (action.mode == IntentMode.JOURNAL) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (action.mode) {
                            IntentMode.JOURNAL -> "Сохранить в дневник"
                            IntentMode.LOG, IntentMode.MIXED -> "Применить всё"
                            else -> "Сохранить"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Больше пяти пунктов «как я это понял» превращаются в простыню и съедают шторку. */
private const val MAX_VISIBLE_INSIGHTS = 5


/** ISO-дата в компактный вид: без «T» и секунд, если их не было. */
private fun formatDue(iso: String): String =
    runCatching {
        val parsed = java.time.LocalDateTime.parse(iso.take(19))
        val time = if (parsed.hour == 14 && parsed.minute == 0) "" else " в %02d:%02d".format(parsed.hour, parsed.minute)
        "${parsed.toLocalDate()}$time"
    }.getOrDefault(iso)

/** Секции конспекта, которые можно выбросить в шторке. */
enum class DigestSection(val title: String) {
    KEY_POINTS("Ключевые мысли"),
    DECISIONS("Решения"),
    OPEN_QUESTIONS("Открытые вопросы"),
    NEXT_STEPS("Что дальше"),
    PEOPLE("Люди"),
    NUMBERS("Цифры")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeBadge(
    action: VoiceNoteAction,
    onOverride: (IntentMode) -> Unit
) {
    val accent = modeAccent(action.mode)
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = modeIcon(action.mode),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = action.mode.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                ConfidenceBar(
                    confidence = action.modeConfidence,
                    accent = accent,
                    overridden = action.modeIsOverridden
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (action.modeReason.isNotBlank()) action.modeReason else action.mode.hint,
                fontSize = 11.sp,
                color = DuroTextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Изменить режим:",
                fontSize = 10.sp,
                color = DuroTextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IntentMode.entries.forEach { mode ->
                    val selected = action.mode == mode
                    val chipAccent = modeAccent(mode)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) chipAccent else DuroSurfaceElevated)
                            .border(
                                1.dp,
                                if (selected) chipAccent else DuroBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { if (!selected) onOverride(mode) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = modeIcon(mode),
                                contentDescription = null,
                                tint = if (selected) DuroBackground else DuroTextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = mode.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) DuroBackground else DuroTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBar(confidence: Float, accent: Color, overridden: Boolean) {
    val percent = (confidence * 100).toInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (overridden) {
            Text(
                text = "вручную",
                fontSize = 10.sp,
                color = DuroTextMuted,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = "уверенность $percent%",
                fontSize = 10.sp,
                color = DuroTextMuted
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DuroBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(confidence.coerceIn(0.05f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }
        }
    }
}

@Composable
private fun DigestPreview(
    digest: com.voicehabit.tracker.domain.model.CaptureDigest,
    editable: Boolean,
    onTitleChange: (String) -> Unit,
    onDropSection: (DigestSection) -> Unit,
    onSpeak: (String) -> Unit
) {
    Surface(
        color = DuroSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ВЫЖИМКА РАЗГОВОРА",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.accent,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                if (digest.speechSeconds > 0) {
                    Text(
                        text = "%d:%02d речи · %d слов".format(
                            digest.speechSeconds / 60, digest.speechSeconds % 60, digest.wordCount
                        ),
                        fontSize = 10.sp,
                        color = DuroTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (editable) {
                var title by remember(digest.title) { mutableStateOf(digest.title) }
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        if (it.length <= 60) {
                            title = it
                            onTitleChange(it)
                        }
                    },
                    label = { Text("Тема конспекта", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = DuroTextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppTheme.colors.accent,
                        unfocusedBorderColor = DuroBorder,
                        focusedLabelColor = AppTheme.colors.accent,
                        unfocusedLabelColor = DuroTextMuted,
                        cursorColor = AppTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = digest.title.ifBlank { "Без темы" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DuroTextPrimary
                )
            }

            if (digest.gist.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = digest.gist,
                        fontSize = 12.sp,
                        color = DuroTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { onSpeak(digest.gist) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Прослушать",
                            tint = DuroTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            DigestSection.entries.forEach { section ->
                val items = digest.itemsOf(section)
                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = section.title,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.accent,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (editable) {
                            Text(
                                text = "убрать",
                                fontSize = 10.sp,
                                color = DuroTextMuted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onDropSection(section) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    items.forEach { item ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(text = "· ", fontSize = 12.sp, color = DuroTextMuted)
                            Text(
                                text = item,
                                fontSize = 12.sp,
                                color = DuroTextPrimary
                            )
                        }
                    }
                }
            }

            if (digest.tone.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Тон: ${digest.tone}",
                    fontSize = 11.sp,
                    color = AppTheme.colors.accentSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun com.voicehabit.tracker.domain.model.CaptureDigest.itemsOf(
    section: DigestSection
): List<String> = when (section) {
    DigestSection.KEY_POINTS -> keyPoints
    DigestSection.DECISIONS -> decisions
    DigestSection.OPEN_QUESTIONS -> openQuestions
    DigestSection.NEXT_STEPS -> nextSteps
    DigestSection.PEOPLE -> people
    DigestSection.NUMBERS -> numbers
}

/** Убирает секцию целиком: пользователь решил, что она не про то. */
private fun com.voicehabit.tracker.domain.model.CaptureDigest.without(
    section: DigestSection
): com.voicehabit.tracker.domain.model.CaptureDigest = when (section) {
    DigestSection.KEY_POINTS -> copy(keyPoints = emptyList())
    DigestSection.DECISIONS -> copy(decisions = emptyList())
    DigestSection.OPEN_QUESTIONS -> copy(openQuestions = emptyList())
    DigestSection.NEXT_STEPS -> copy(nextSteps = emptyList())
    DigestSection.PEOPLE -> copy(people = emptyList())
    DigestSection.NUMBERS -> copy(numbers = emptyList())
}

internal fun modeAccent(mode: IntentMode): Color = when (mode) {
    IntentMode.LOG -> DairyAccentWarm
    IntentMode.JOURNAL -> MidnightAccent
    IntentMode.DICTATE -> EspressoAccent
    IntentMode.MIXED -> MarsRustAccent
    IntentMode.QUERY -> DairySuccess
}

internal fun modeIcon(mode: IntentMode): ImageVector = when (mode) {
    IntentMode.LOG -> Icons.Default.TaskAlt
    IntentMode.JOURNAL -> Icons.Default.MenuBook
    IntentMode.DICTATE -> Icons.Default.GraphicEq
    IntentMode.MIXED -> Icons.Default.AutoAwesome
    IntentMode.QUERY -> Icons.Default.HelpOutline
}

@Composable
private fun JournalReviewCard(
    digest: CaptureDigest,
    transcript: String,
    onTitleChange: (String) -> Unit,
    onSpeak: (String) -> Unit
) {
    var expandedTranscript by remember { mutableStateOf(false) }
    var title by remember(digest.title) { mutableStateOf(digest.title) }

    Surface(
        color = DuroSurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, DuroJournalLavender.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.voicehabit.tracker.R.drawable.ic_journal_night),
                        contentDescription = null,
                        tint = DuroJournalLavender,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ЗАПИСЬ В ДНЕВНИК",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DuroJournalLavender,
                        letterSpacing = 1.sp
                    )
                }

                if (digest.tone.isNotBlank()) {
                    Surface(
                        color = DuroJournalLavender.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = digest.tone,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DuroJournalLavender,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (it.length <= 80) {
                        title = it
                        onTitleChange(it)
                    }
                },
                label = { Text("Тема мысли / Заголовок", fontSize = 11.sp) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DuroTextPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DuroJournalLavender,
                    unfocusedBorderColor = DuroBorder,
                    focusedLabelColor = DuroJournalLavender,
                    unfocusedLabelColor = DuroTextMuted,
                    cursorColor = DuroJournalLavender
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Core Insight (Суть) formatted with quote container
            if (digest.gist.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = DuroBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(40.dp)
                                .background(DuroJournalLavender, RoundedCornerShape(2.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "СУТЬ (CORE INSIGHT)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuroTextMuted,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "«${digest.gist}»",
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.Medium,
                                color = DuroTextPrimary
                            )
                        }
                        IconButton(
                            onClick = { onSpeak(digest.gist) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Прослушать",
                                tint = AppTheme.colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Structured Key Points (Тезисы)
            if (digest.keyPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "КЛЮЧЕВЫЕ ТЕЗИСЫ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = DuroJournalLavender,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                digest.keyPoints.forEach { point ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "•", color = DuroJournalLavender, fontWeight = FontWeight.Bold)
                        Text(
                            text = point,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = DuroTextSecondary
                        )
                    }
                }
            }

            // Collapsible transcript
            if (transcript.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expandedTranscript = !expandedTranscript }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expandedTranscript) "Скрыть полную расшифровку" else "Полная расшифровка (Транскрипт)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DuroTextMuted
                    )
                    Text(
                        text = "${transcript.split(' ').filter { it.isNotBlank() }.size} слов",
                        fontSize = 10.sp,
                        color = DuroTextMuted
                    )
                }

                AnimatedVisibility(visible = expandedTranscript) {
                    Surface(
                        color = DuroBackground,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = transcript,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = DuroTextSecondary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Surface(
        color = DuroSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onChecked,
                colors = CheckboxDefaults.colors(
                    checkedColor = AppTheme.colors.accent,
                    uncheckedColor = DuroTextMuted
                )
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.colors.accent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = DuroTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = DuroTextSecondary
                )
            }
        }
    }
}
