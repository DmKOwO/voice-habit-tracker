package com.voicehabit.tracker.presentation.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.voicehabit.tracker.domain.model.CaptureDigest
import com.voicehabit.tracker.domain.model.IntentMode
import com.voicehabit.tracker.domain.model.TaskType
import com.voicehabit.tracker.domain.model.VoiceNoteAction
import com.voicehabit.tracker.presentation.theme.*
import kotlinx.coroutines.delay

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
    var autoApplySecondsLeft by remember { mutableIntStateOf(autoApplySeconds(action.mode)) }
    var isTimerCancelled by remember { mutableStateOf(false) }

    fun currentAction(): VoiceNoteAction = action.copy(
        habitsCompleted = habitsState,
        tasksToAdd = tasksState,
        tasksToDelete = deleteState,
        tasksToReschedule = rescheduleState,
        focusToStart = focusState,
        digest = digestState
    )

    LaunchedEffect(isTimerCancelled) {
        if (!isTimerCancelled) {
            while (autoApplySecondsLeft > 0) {
                delay(1000)
                autoApplySecondsLeft--
            }
            onApply(currentAction())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DuroSurface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = DuroBorder)
        }
    ) {
        Column(
            // Высота намеренно не задаётся: ModalBottomSheet меряет содержимое по
            // wrapContent, и любая попытка ограничить её долей экрана здесь не
            // срабатывает — блок «как я это понял» всё равно выдавливал кнопки за
            // нижний край. Поэтому кнопки лежат последним элементом скролл-списка:
            // до них всегда можно дотянуться, а нельзя потерять.
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header with Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DuroAsterisk(size = 18.dp, color = DuroOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Разбор записи",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DuroTextPrimary,
                            // Две строки, а не одна: на 720px при шрифте 1.3x «Разбор
                            // записи» переносится, и жёсткий maxLines=1 превращал
                            // заголовок в «Разбор …» — он переставал объяснять экран.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${action.modelUsed} · ${action.sttDurationMs + action.llmDurationMs} мс",
                        fontSize = 11.sp,
                        color = DuroTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isTimerCancelled && autoApplySecondsLeft > 0) {
                    AssistChip(
                        onClick = { isTimerCancelled = true },
                        label = {
                            Text("Авто-сохранение: ${autoApplySecondsLeft}с", fontSize = 11.sp)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = DuroOrange.copy(alpha = 0.15f),
                            labelColor = DuroOrange
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // H1. Что приложение само поняло о намерении + возможность поправить.
            ModeBadge(
                action = action,
                onOverride = { mode ->
                    isTimerCancelled = true
                    onOverrideMode(mode)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Summary + TTS (G1)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "«${action.summary}»",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = DuroTextSecondary,
                    // Сводка — короткая строка, а не пересказ. Раньше она не имела
                    // ограничения по высоте и выталкивала конспект за нижний край экрана.
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        isTimerCancelled = true
                        onSpeak(action.summary)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(text = "🔊", fontSize = 18.sp)
                }
            }
            if (action.daySummaryRequested) {
                Text(
                    text = "📋 Сводка дня будет показана и озвучена после применения",
                    fontSize = 12.sp,
                    color = DuroCyan,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.padding(top = 12.dp),
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
                                    color = DuroPurple,
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

                // H1. Конспект свободного потока — то самое «диктофон с выжимкой».
                digestState?.let { digest ->
                    if (!digest.isEmpty) {
                        item(key = "digest") {
                            DigestPreview(
                                digest = digest,
                                editable = action.mode == IntentMode.DICTATE,
                                onTitleChange = { value ->
                                    isTimerCancelled = true
                                    digestState = digest.copy(title = value)
                                },
                                onDropSection = { section ->
                                    isTimerCancelled = true
                                    digestState = digest.without(section)
                                },
                                onSpeak = { text ->
                                    isTimerCancelled = true
                                    onSpeak(text)
                                }
                            )
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
                                            isTimerCancelled = true
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
                                emoji = "🗑",
                                title = deleteAction.taskTitle,
                                subtitle = "В корзину (восстановимо)",
                                checked = deleteAction.isSelected,
                                onChecked = { checked ->
                                    isTimerCancelled = true
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
                                emoji = "📅",
                                title = reschedule.taskTitle,
                                subtitle = "Перенести" + (reschedule.newDueDate?.let { " до $it" } ?: ""),
                                checked = reschedule.isSelected,
                                onChecked = { checked ->
                                    isTimerCancelled = true
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
                                emoji = "🧠",
                                title = "Фокус ${focus.minutes} мин",
                                subtitle = focus.label,
                                checked = focus.isSelected,
                                onChecked = { checked ->
                                    isTimerCancelled = true
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
                            color = DuroCyan,
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
                                            isTimerCancelled = true
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
                                                text = "⏰ ${formatDue(taskAction.dueDate)}",
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
                                        text = if (taskAction.taskType == TaskType.LONG.name) "🔥 ДОЛГАЯ" else "⚡ БЫСТРАЯ",
                                        fontSize = 9.sp,
                                        color = if (taskAction.taskType == TaskType.LONG.name) DuroPurple else DuroCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // H1: кнопки — последний элемент списка, а не фиксированный ряд под ним.
                item(key = "actions") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DuroTextMuted),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Отклонить", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { onApply(currentAction()) },
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DuroOrange,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (action.mode.producesEntities) "Применить всё" else "Сохранить",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Больше пяти пунктов «как я это понял» превращаются в простыню и съедают шторку. */
private const val MAX_VISIBLE_INSIGHTS = 5

/**
 * H1. На конспекте авто-сохранение идёт медленнее, чем на задаче.
 *
 * Восемь секунд — это «не заметил и согласился» для одной задачи. Для конспекта
 * восемь секунд — это «не успел прочитать свои же мысли»: текст всё равно сохранится
 * в `digests`, но несохранённые правки заголовка потерялись бы, а шторка с таймером
 * читается как «что-то сейчас произойдёт».
 */
private fun autoApplySeconds(mode: IntentMode): Int = when (mode) {
    IntentMode.LOG -> 8
    IntentMode.MIXED -> 14
    IntentMode.DICTATE, IntentMode.QUERY -> 20
}

/** ISO-дата в компактный вид: без «T» и секунд, если их не было. */
private fun formatDue(iso: String): String =
    runCatching {
        val parsed = java.time.LocalDateTime.parse(iso.take(19))
        val time = if (parsed.hour == 14 && parsed.minute == 0) "" else " в %02d:%02d".format(parsed.hour, parsed.minute)
        "${parsed.toLocalDate()}$time"
    }.getOrDefault(iso)

/** Секции конспекта, которые можно выбросить в шторке. */
enum class DigestSection(val title: String, val emoji: String) {
    KEY_POINTS("Ключевые мысли", "◆"),
    DECISIONS("Решения", "✓"),
    OPEN_QUESTIONS("Открытые вопросы", "?"),
    NEXT_STEPS("Что дальше", "→"),
    PEOPLE("Люди", "☺"),
    NUMBERS("Цифры", "#")
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
                Text(
                    text = action.mode.emoji,
                    fontSize = 16.sp,
                    color = accent,
                    fontWeight = FontWeight.Bold
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
                text = "Я ошибся?",
                fontSize = 10.sp,
                color = DuroTextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Ровно четыре варианта: столько же, сколько режимов. Чипы в одну строку с
            // переносом — иначе на узком экране при шрифте 1.3x они съедали шторку.
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
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "${mode.emoji} ${mode.label}",
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
        border = androidx.compose.foundation.BorderStroke(1.dp, DuroCyan.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ВЫЖИМКА РАЗГОВОРА",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = DuroCyan,
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
                        focusedBorderColor = DuroCyan,
                        unfocusedBorderColor = DuroBorder,
                        focusedLabelColor = DuroCyan,
                        unfocusedLabelColor = DuroTextMuted,
                        cursorColor = DuroCyan
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
                        Text(text = "🔊", fontSize = 14.sp)
                    }
                }
            }

            DigestSection.entries.forEach { section ->
                val items = digest.itemsOf(section)
                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${section.emoji} ${section.title}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroAmber,
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
                    color = DuroPurple,
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
    IntentMode.LOG -> DuroCyan
    IntentMode.DICTATE -> DuroPurple
    IntentMode.MIXED -> DuroAmber
    IntentMode.QUERY -> DuroLime
}

@Composable
private fun CommandRow(
    emoji: String,
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
                    checkedColor = DuroOrange,
                    uncheckedColor = DuroTextMuted
                )
            )
            Text(text = emoji, fontSize = 18.sp)
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
