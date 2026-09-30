package com.voicehabit.tracker.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.core.calendar.CalendarHelper
import com.voicehabit.tracker.domain.model.Priority
import com.voicehabit.tracker.domain.model.Subtask
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.domain.model.TaskRecurrence
import com.voicehabit.tracker.domain.model.TaskType
import com.voicehabit.tracker.presentation.home.HomeState
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorBottomSheet(
    task: Task,
    onSave: (Task) -> Unit,
    onToggleCompletion: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel? = null
) {
    val context = LocalContext.current
    var title by remember(task.id) { mutableStateOf(task.title) }
    var type by remember(task.id) { mutableStateOf(task.type) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var category by remember(task.id) { mutableStateOf(task.category) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    var url by remember(task.id) { mutableStateOf(task.url ?: "") }
    var pinned by remember(task.id) { mutableStateOf(task.pinned) }
    var estimatedMin by remember(task.id) { mutableStateOf(task.estimatedMin) }
    var tagsText by remember(task.id) { mutableStateOf(task.tags.joinToString(", ")) }
    var recurrence by remember(task.id) { mutableStateOf(task.recurrence) }
    var reminderMin by remember(task.id) { mutableStateOf(task.reminderMinutesBefore) }
    var newSubtask by remember(task.id) { mutableStateOf("") }

    val initialDateTime = remember(task.id) { parseTaskDateTime(task.dueDateIso) }
    var dueDate by remember(task.id) { mutableStateOf(initialDateTime?.toLocalDate()) }
    var dueTime by remember(task.id) { mutableStateOf(initialDateTime?.toLocalTime() ?: LocalTime.of(14, 0)) }

    val categories = remember {
        (listOf("General", "Work", "Health", "Home", "Study", "Голосовое") +
            (viewModel?.allCategories() ?: emptyList())).distinct().take(12)
    }
    val dayChips = listOf(
        null to "Без срока",
        0 to "Сегодня",
        1 to "Завтра",
        2 to "Послезавтра",
        7 to "Через неделю"
    )
    val timeChips = listOf(9 to "09:00", 12 to "12:00", 14 to "14:00", 19 to "19:00", 21 to "21:00")
    val reminderChips = listOf(null to "Нет", 15 to "15 мин", 60 to "1 час", 1440 to "1 день")

    LaunchedEffect(task.id) { viewModel?.loadSubtasks(task.id) }

    // Состояние собираем один раз и наблюдаем. Раньше здесь было
    // `viewModel?.state?.value?.tasks` прямо внутри remember(...) и внутри
    // композиции: чтение .value не подписывает composable на изменения,
    // поэтому список задач мог не обновиться и редактор вёлся не по тому
    // состоянию, которое отображалось на экране.
    val state by (viewModel?.state?.collectAsState() ?: remember { mutableStateOf(HomeState()) })
    val subtasks = state.subtasks[task.id] ?: emptyList()
    val doneCount = subtasks.count { it.isDone }
    val isExistingTask = remember(task.id, state.tasks) {
        state.tasks.any { it.id == task.id }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101016),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isExistingTask) "РЕДАКТИРОВАНИЕ ЗАДАЧИ" else "НОВАЯ ЗАДАЧА",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.accent,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (!isExistingTask) "Черновик (Голос)" else if (task.isCompleted) "Выполнена" else "В работе",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (!isExistingTask) AppTheme.colors.textPrimary else if (task.isCompleted) DuroTextSecondary else DuroTextPrimary
                    )
                }
                Row {
                    IconButton(onClick = {
                        viewModel?.togglePinTask(task.copy(pinned = pinned))
                        pinned = !pinned
                    }) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = if (pinned) "Открепить" else "Закрепить",
                            tint = if (pinned) AppTheme.colors.accent else DuroTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {
                        if (isExistingTask) onDelete() else onDismiss()
                    }) {
                        Icon(
                            imageVector = if (isExistingTask) Icons.Default.Delete else Icons.Default.Close,
                            contentDescription = if (isExistingTask) "В корзину" else "Отменить",
                            tint = if (isExistingTask) DuroRed else DuroTextMuted
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название", color = DuroTextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = duroTextFieldColors(),
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание (заметки к задаче)", color = DuroTextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = duroTextFieldColors(),
                minLines = 1,
                maxLines = 4
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Ссылка (https://…)", color = DuroTextSecondary) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = duroTextFieldColors(),
                    singleLine = true
                )
                if (url.isNotBlank()) {
                    Button(
                        onClick = {
                            try {
                                var link = url.trim()
                                if (!link.startsWith("http")) link = "https://$link"
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(link)
                                    )
                                )
                            } catch (e: Exception) {
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent, contentColor = AppTheme.colors.surface)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Открыть ссылку", modifier = Modifier.size(16.dp))
                    }
                }
            }

            EditorSection(title = "Подзадачи ($doneCount/${subtasks.size})") {
                subtasks.forEach { sub ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = sub.isDone,
                            onCheckedChange = { viewModel?.toggleSubtask(task.id, sub) },
                            colors = CheckboxDefaults.colors(checkedColor = DairySuccess)
                        )
                        Text(
                            text = sub.title,
                            fontSize = 13.sp,
                            color = if (sub.isDone) DuroTextSecondary else DuroTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel?.deleteSubtask(task.id, sub.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Удалить", tint = DuroTextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newSubtask,
                        onValueChange = { newSubtask = it },
                        placeholder = { Text("Новая подзадача…", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = duroTextFieldColors()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel?.addSubtask(task.id, newSubtask)
                            newSubtask = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent, contentColor = AppTheme.colors.surface),
                        enabled = newSubtask.isNotBlank()
                    ) {
                        Text("+")
                    }
                }
            }

            EditorSection(title = "Тип задачи") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TypeChoiceCard(
                        modifier = Modifier.weight(1f),
                        selected = type == TaskType.QUICK,
                        accent = AppTheme.colors.accent,
                        icon = Icons.Default.Bolt,
                        title = "Быстрая",
                        description = "Разовое действие: закрыл и забыл"
                    ) {
                        type = TaskType.QUICK
                    }
                    TypeChoiceCard(
                        modifier = Modifier.weight(1f),
                        selected = type == TaskType.LONG,
                        accent = AppTheme.colors.accentSecondary,
                        icon = Icons.Default.LocalFireDepartment,
                        title = "Долгая",
                        description = "Цель как привычка: остаётся в списке"
                    ) {
                        type = TaskType.LONG
                    }
                }
            }

            EditorSection(title = "Приоритет") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Priority.LOW to DuroTextMuted,
                        Priority.MEDIUM to DuroAmber,
                        Priority.HIGH to DuroRed
                    ).forEach { (option, accent) ->
                        EditorChip(
                            label = when (option) {
                                Priority.LOW -> "Низкий"
                                Priority.MEDIUM -> "Средний"
                                Priority.HIGH -> "Срочный"
                            },
                            selected = priority == option,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            onClick = { priority = option }
                        )
                    }
                }
            }

            EditorSection(title = "Повтор") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskRecurrence.values().forEach { option ->
                        EditorChip(
                            label = option.title,
                            selected = recurrence == option,
                            accent = AppTheme.colors.accentSecondary,
                            modifier = Modifier.weight(1f),
                            onClick = { recurrence = option }
                        )
                    }
                }
            }

            EditorSection(title = "Категория") {
                FlowRowChips(
                    items = categories,
                    selected = category,
                    onSelect = { category = it }
                )
            }

            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text("Теги через запятую", color = DuroTextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = duroTextFieldColors(),
                singleLine = true
            )

            EditorSection(title = "Оценка времени") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "—", 15 to "15 мин", 30 to "30 мин", 60 to "1 ч", 120 to "2 ч").forEach { (option, label) ->
                        EditorChip(
                            label = label,
                            selected = estimatedMin == option,
                            accent = AppTheme.colors.accent,
                            modifier = Modifier.weight(1f),
                            onClick = { estimatedMin = option }
                        )
                    }
                }
                if (estimatedMin != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                task.copy(
                                    title = title.trim().ifEmpty { task.title },
                                    type = type,
                                    priority = priority,
                                    category = category,
                                    dueDateIso = dueDate?.let {
                                        LocalDateTime.of(it, dueTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                    },
                                    description = description.trim(),
                                    url = url.trim().ifBlank { null },
                                    pinned = pinned,
                                    estimatedMin = estimatedMin,
                                    tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    reminderMinutesBefore = reminderMin,
                                    recurrence = recurrence
                                )
                            )
                            viewModel?.startFocus(
                                estimatedMin ?: 25,
                                title.trim().ifEmpty { task.title },
                                taskId = task.id
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent, contentColor = AppTheme.colors.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить и старт фокус ($estimatedMin мин)")
                    }
                }
            }

            EditorSection(title = "Срок") {
                val today = LocalDate.now()
                val selectedDayLabel = dayChips.firstOrNull { (offset, _) ->
                    dueDate != null && offset != null && dueDate == today.plusDays(offset.toLong())
                }?.second

                FlowRowChips(
                    items = dayChips.map { it.second },
                    selected = selectedDayLabel,
                    onSelect = { label ->
                        val offset = dayChips.firstOrNull { it.second == label }?.first
                        dueDate = if (offset == null) null else today.plusDays(offset.toLong())
                    }
                )
            }

            if (dueDate != null) {
                EditorSection(title = "Время") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        timeChips.forEach { (hour, label) ->
                            EditorChip(
                                label = label,
                                selected = dueTime.hour == hour && dueTime.minute == 0,
                                accent = AppTheme.colors.accent,
                                modifier = Modifier.weight(1f),
                                onClick = { dueTime = LocalTime.of(hour, 0) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Точно: %02d:%02d".format(dueTime.hour, dueTime.minute),
                            fontSize = 13.sp,
                            color = DuroTextPrimary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        listOf(-15 to "−15", 15 to "+15", 60 to "+1ч").forEach { (delta, label) ->
                            EditorChip(
                                label = label,
                                selected = false,
                                accent = DuroTextSecondary,
                                onClick = { dueTime = dueTime.plusMinutes(delta.toLong()) }
                            )
                        }
                    }
                }

                EditorSection(title = "Напомнить за") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        reminderChips.forEach { (option, label) ->
                            EditorChip(
                                label = label,
                                selected = reminderMin == option,
                                accent = DuroAmber,
                                modifier = Modifier.weight(1f),
                                onClick = { reminderMin = option }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val dueIso = LocalDateTime.of(dueDate, dueTime)
                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val intent = CalendarHelper.insertEventIntent(
                            title.trim().ifEmpty { task.title }, description.trim(), dueIso
                        )
                        if (CalendarHelper.canHandle(context, intent)) {
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("В системный календарь", color = AppTheme.colors.textPrimary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isExistingTask) {
                    OutlinedButton(
                        onClick = onToggleCompletion,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppTheme.colors.accent
                        )
                    ) {
                        Text(if (task.isCompleted) "Вернуть в работу" else "Отметить выполненной")
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppTheme.colors.textSecondary
                        )
                    ) {
                        Text("Отменить")
                    }
                }
                Button(
                    onClick = {
                        val dueIso = dueDate?.let {
                            LocalDateTime.of(it, dueTime)
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        }
                        onSave(
                            task.copy(
                                title = title.trim().ifEmpty { task.title },
                                type = type,
                                priority = priority,
                                category = category,
                                dueDateIso = dueIso,
                                description = description.trim(),
                                url = url.trim().ifBlank { null },
                                pinned = pinned,
                                estimatedMin = estimatedMin,
                                tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                reminderMinutesBefore = reminderMin,
                                recurrence = recurrence
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.accent,
                        contentColor = AppTheme.colors.onAccent
                    )
                ) {
                    Text(if (isExistingTask) "Сохранить" else "Создать задачу", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = DuroTextSecondary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun TypeChoiceCard(
    selected: Boolean,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else DuroSurface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else DuroBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accent else DuroTextMuted,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) DuroTextPrimary else DuroTextSecondary
        )
        Text(
            text = description,
            fontSize = 11.sp,
            color = DuroTextMuted
        )
    }
}

@Composable
private fun EditorChip(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else DuroSurface)
            .border(
                width = 1.dp,
                color = if (selected) accent else DuroBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else DuroTextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun FlowRowChips(
    items: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    EditorChip(
                        label = item,
                        selected = item == selected,
                        accent = AppTheme.colors.accent,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(item) }
                    )
                }
            }
        }
    }
}


private fun parseTaskDateTime(value: String?): LocalDateTime? {
    if (value.isNullOrBlank()) return null
    return try {
        LocalDateTime.parse(value.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (e: Exception) {
        try {
            LocalDate.parse(value.take(10)).atStartOfDay()
        } catch (e2: Exception) {
            null
        }
    }
}
