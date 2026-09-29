package com.voicehabit.tracker.presentation.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.Priority
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.domain.model.TaskType
import com.voicehabit.tracker.presentation.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class DisplayOption(
    val type: String,
    val title: String,
    val description: String,
    val defaultColor: Color
)

/** Готовые варианты активностей: подсказка не «придумывай название», а выбор из списка + случайная подборка. */
data class ActivityPreset(
    val title: String,
    val category: String,
    val colorHex: String,
    val displayType: String,
    val frequency: String,
    val icon: ImageVector
)

private val activityPresets = listOf(
    ActivityPreset("Английский язык", "Study", "#F4F1EA", "GRID", "DAILY", Icons.Default.Translate),
    ActivityPreset("Тренировка", "Fitness", "#DE6B48", "BAR_GRAPH", "DAILY", Icons.Default.FitnessCenter),
    ActivityPreset("Прогулка", "Fitness", "#5BA872", "STREAKS", "DAILY", Icons.Default.DirectionsRun),
    ActivityPreset("Велозаезд", "Fitness", "#C7A774", "BAR_GRAPH", "WEEKLY", Icons.Default.DirectionsBike),
    ActivityPreset("Чтение книги", "Evening", "#F4F1EA", "STREAKS", "DAILY", Icons.Default.Book),
    ActivityPreset("Пить воду", "Health", "#B8A5E3", "DAILY_CHECK", "DAILY", Icons.Default.WaterDrop),
    ActivityPreset("Витамины", "Health", "#5BA872", "DAILY_CHECK", "DAILY", Icons.Default.WaterDrop),
    ActivityPreset("Ранний подъём", "Morning", "#C7A774", "STREAKS", "DAILY", Icons.Default.WbSunny),
    ActivityPreset("Отбой до 23:00", "Evening", "#B8A5E3", "STREAKS", "DAILY", Icons.Default.Bedtime),
    ActivityPreset("Медитация", "Mindset", "#F4F1EA", "MINIMAL", "DAILY", Icons.Default.SelfImprovement),
    ActivityPreset("Растяжка", "Morning", "#DE6B48", "STREAKS", "DAILY", Icons.Default.Brush),
    ActivityPreset("Уборка", "Home", "#5BA872", "GRID", "WEEKLY", Icons.Default.CleaningServices),
    ActivityPreset("Нормальное питание", "Health", "#5BA872", "DAILY_CHECK", "DAILY", Icons.Default.Restaurant),
    ActivityPreset("Откладывать деньги", "Mindset", "#C7A774", "BAR_GRAPH", "WEEKLY", Icons.Default.Savings),
    ActivityPreset("Код / практика", "Work", "#F4F1EA", "BAR_GRAPH", "DAILY", Icons.Default.Code),
    ActivityPreset("Музыка", "Evening", "#B8A5E3", "GRID", "DAILY", Icons.Default.MusicNote)
)

private enum class CreateMode { HABIT, TASK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateHabitScreen(
    onDismiss: () -> Unit,
    onSaveHabit: (Habit) -> Unit,
    onSaveTask: (Task) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: com.voicehabit.tracker.presentation.home.HomeViewModel? = null
) {
    val displayOptions = remember {
        listOf(
            DisplayOption("DAILY_CHECK", "Чекбокс", "Лаконичный круглый чекбокс для ежедневной отметки.", DairyAccentWarm),
            DisplayOption("GRID", "Сетка", "История выполнения за месяц в виде аккуратных точек.", MarsRustAccent),
            DisplayOption("BAR_GRAPH", "Столбцы", "Прогресс за неделю в виде компактного графика.", EspressoAccent),
            DisplayOption("STREAKS", "Серия", "Счётчик непрерывной цепочки успешных дней.", MidnightAccent),
            DisplayOption("MINIMAL", "Минимализм", "Чистый фокус с процентом достижения.", DairySuccess)
        )
    }

    var mode by remember { mutableStateOf(CreateMode.HABIT) }
    var selectedDisplayIndex by remember { mutableIntStateOf(0) }
    var habitTitle by remember { mutableStateOf("") }
    var habitCategory by remember { mutableStateOf("Morning") }
    var habitQuote by remember { mutableStateOf("") }
    var habitFrequency by remember { mutableStateOf("DAILY") }
    // Норма количества («2 литра») — поле было в схеме, но ни один экран его не показывал.
    var habitTarget by remember { mutableStateOf("1") }
    var habitUnit by remember { mutableStateOf("") }
    // Воздержание («не курить») и цель «N раз в неделю» — кодируются в tags,
    // чтобы не делать миграцию БД (см. HabitTagCodec).
    var habitAvoid by remember { mutableStateOf(false) }
    var habitPerWeek by remember { mutableIntStateOf(0) }
    var habitScheduleDays by remember { mutableStateOf((1..7).toSet()) }
    var taskType by remember { mutableStateOf(TaskType.QUICK) }
    var taskPriority by remember { mutableStateOf(Priority.MEDIUM) }
    var dueOffset by remember { mutableIntStateOf(1) }
    var presetOrder by remember { mutableStateOf(activityPresets) }
    var randomizeCount by remember { mutableIntStateOf(0) }

    val colors = remember {
        listOf(
            "#F4F1EA" to DairyAccentWarm,
            "#DE6B48" to MarsRustAccent,
            "#C7A774" to EspressoAccent,
            "#B8A5E3" to MidnightAccent,
            "#5BA872" to DairySuccess
        )
    }
    var selectedColorHex by remember { mutableStateOf("#F4F1EA") }

    val categories = remember(viewModel) {
        (listOf("Morning", "Fitness", "Work", "Health", "Home", "Study", "Evening", "Mindset") +
            (viewModel?.allCategories() ?: emptyList())).distinct().take(14)
    }

    val selectedColor = try {
        Color(android.graphics.Color.parseColor(selectedColorHex))
    } catch (e: Exception) {
        AppTheme.colors.accent
    }

    val haptics = rememberDuroHaptics()

    fun saveCurrentEntry() {
        val title = habitTitle.trim().ifEmpty {
            if (mode == CreateMode.HABIT) "New Habit" else "Новая задача"
        }
        if (mode == CreateMode.HABIT) {
            val targetValue = habitTarget.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
            val unit = habitUnit.trim().ifEmpty { null }
            var tagsCsv = ""
            tagsCsv = com.voicehabit.tracker.core.analysis.HabitTagCodec.withAvoid(tagsCsv, habitAvoid)
            tagsCsv = com.voicehabit.tracker.core.analysis.HabitTagCodec.withWeeklyTarget(tagsCsv, habitPerWeek)
            val tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            onSaveHabit(
                Habit(
                    id = "habit_" + UUID.randomUUID().toString().take(8),
                    title = title,
                    category = habitCategory,
                    displayType = displayOptions[selectedDisplayIndex].type,
                    colorHex = selectedColorHex,
                    quote = habitQuote.trim(),
                    targetValue = targetValue,
                    unit = unit,
                    tags = tags,
                    frequency = habitFrequency,
                    currentStreak = 0,
                    isCompletedToday = false,
                    scheduleDays = habitScheduleDays
                )
            )
        } else {
            onSaveTask(
                Task(
                    id = "task_" + UUID.randomUUID().toString().take(8),
                    title = title,
                    dueDateIso = LocalDateTime
                        .of(LocalDate.now().plusDays(dueOffset.toLong()), LocalTime.of(14, 0))
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    priority = taskPriority,
                    category = habitCategory,
                    type = taskType
                )
            )
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DuroIconActionButton(
                    onClick = {
                        haptics.select()
                        onDismiss()
                    },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    containerColor = DuroSurface,
                    iconTint = AppTheme.colors.accent
                )

                DuroAsterisk(size = 24.dp, color = AppTheme.colors.accent)
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DuroIconActionButton(
                    onClick = {
                        haptics.select()
                        onDismiss()
                    },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Cancel",
                    containerColor = DuroSurface,
                    iconTint = AppTheme.colors.accent,
                    size = 52.dp,
                    iconSize = 24.dp
                )

                DuroIconActionButton(
                    onClick = {
                        haptics.confirm()
                        saveCurrentEntry()
                    },
                    icon = Icons.Default.DoneAll,
                    contentDescription = "Confirm",
                    containerColor = AppTheme.colors.accent,
                    iconTint = AppTheme.colors.onAccent,
                    size = 56.dp,
                    iconSize = 28.dp,
                    pressedScale = 0.92f
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Режим: привычка или задача
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeChip(
                    label = "Привычка",
                    selected = mode == CreateMode.HABIT,
                    accent = AppTheme.colors.accent,
                    modifier = Modifier.weight(1f),
                    onClick = { mode = CreateMode.HABIT }
                )
                ModeChip(
                    label = "Задача",
                    selected = mode == CreateMode.TASK,
                    accent = AppTheme.colors.accent,
                    modifier = Modifier.weight(1f),
                    onClick = { mode = CreateMode.TASK }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(DuroContentSpring) + slideInHorizontally(DuroOffsetSpring) { it / 3 })
                        .togetherWith(fadeOut(DuroContentSpring) + slideOutHorizontally(DuroOffsetSpring) { -it / 3 })
                },
                label = "createMode"
            ) { currentMode ->
                when (currentMode) {
                    CreateMode.HABIT -> HabitCreationContent(
                        displayOptions = displayOptions,
                        selectedDisplayIndex = selectedDisplayIndex,
                        onSelectDisplay = { selectedDisplayIndex = it },
                        selectedColor = selectedColor,
                        title = habitTitle,
                        onTitleChange = { habitTitle = it },
                        categories = categories,
                        category = habitCategory,
                        onCategoryChange = { habitCategory = it },
                        frequency = habitFrequency,
                        onFrequencyChange = {
                            habitFrequency = it
                            if (it == "DAILY") habitScheduleDays = (1..7).toSet()
                        },
                        scheduleDays = habitScheduleDays,
                        onScheduleDaysChange = { habitScheduleDays = it },
                        colors = colors,
                        selectedColorHex = selectedColorHex,
                        onColorChange = { selectedColorHex = it },
                        quote = habitQuote,
                        onQuoteChange = { habitQuote = it },
                        target = habitTarget,
                        onTargetChange = { habitTarget = it },
                        unit = habitUnit,
                        onUnitChange = { habitUnit = it },
                        avoid = habitAvoid,
                        onAvoidChange = { habitAvoid = it },
                        perWeek = habitPerWeek,
                        onPerWeekChange = { habitPerWeek = it },
                        onAddCategory = { viewModel?.addCustomCategory(it) },
                        presets = presetOrder,
                        randomizeCount = randomizeCount,
                        onPresetClick = { preset ->
                            habitTitle = preset.title
                            habitCategory = preset.category
                            habitFrequency = preset.frequency
                            selectedColorHex = preset.colorHex
                            val index = displayOptions.indexOfFirst { it.type == preset.displayType }
                            if (index >= 0) selectedDisplayIndex = index
                        },
                        onSurpriseMe = {
                            presetOrder = presetOrder.shuffled()
                            val surprise = presetOrder.random()
                            habitTitle = surprise.title
                            habitCategory = surprise.category
                            habitFrequency = surprise.frequency
                            selectedColorHex = surprise.colorHex
                            val index = displayOptions.indexOfFirst { it.type == surprise.displayType }
                            if (index >= 0) selectedDisplayIndex = index
                            randomizeCount++
                        }
                    )

                    CreateMode.TASK -> TaskCreationContent(
                        title = habitTitle,
                        onTitleChange = { habitTitle = it },
                        taskType = taskType,
                        onTaskTypeChange = { taskType = it },
                        priority = taskPriority,
                        onPriorityChange = { taskPriority = it },
                        categories = categories,
                        category = habitCategory,
                        onCategoryChange = { habitCategory = it },
                        dueOffset = dueOffset,
                        onDueOffsetChange = { dueOffset = it },
                        onAddCategory = { viewModel?.addCustomCategory(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitCreationContent(
    displayOptions: List<DisplayOption>,
    selectedDisplayIndex: Int,
    onSelectDisplay: (Int) -> Unit,
    selectedColor: Color,
    title: String,
    onTitleChange: (String) -> Unit,
    categories: List<String>,
    category: String,
    onCategoryChange: (String) -> Unit,
    frequency: String,
    onFrequencyChange: (String) -> Unit,
    scheduleDays: Set<Int> = (1..7).toSet(),
    onScheduleDaysChange: (Set<Int>) -> Unit = {},
    colors: List<Pair<String, Color>>,
    selectedColorHex: String,
    onColorChange: (String) -> Unit,
    quote: String,
    onQuoteChange: (String) -> Unit,
    target: String,
    onTargetChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
    avoid: Boolean,
    onAvoidChange: (Boolean) -> Unit,
    perWeek: Int,
    onPerWeekChange: (Int) -> Unit,
    presets: List<ActivityPreset>,
    randomizeCount: Int,
    onPresetClick: (ActivityPreset) -> Unit,
    onSurpriseMe: () -> Unit,
    onAddCategory: (String) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Варианты активностей: подсказки + «удиви меня»
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Варианты активностей",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DuroTextSecondary
                )
                Text(
                    text = "Удиви меня",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSurpriseMe() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(presets, key = { index, preset -> "${preset.title}-$index-${randomizeCount}" }) { _, preset ->
                val isSelected = title == preset.title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(104.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) DuroSurfaceElevated else DuroSurface)
                        .border(
                            1.dp,
                            if (isSelected) selectedColor else DuroBorder,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onPresetClick(preset) }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = preset.icon,
                        contentDescription = null,
                        tint = if (isSelected) selectedColor else DuroTextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = preset.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) DuroTextPrimary else DuroTextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Заголовок секции визуализации
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Как отображать?",
                fontSize = 13.sp,
                color = DuroTextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Вид карточки",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DuroTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(displayOptions) { index, option ->
                val isSelected = index == selectedDisplayIndex
                val cardColor = if (isSelected) selectedColor else option.defaultColor

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(220.dp)
                        .clickable { onSelectDisplay(index) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(cardColor.copy(alpha = if (isSelected) 0.95f else 0.4f))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color.White.copy(0.8f) else DuroBorder,
                                shape = RoundedCornerShape(26.dp)
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = option.type,
                            transitionSpec = {
                                (fadeIn(DuroContentSpring) + scaleIn(DuroContentSpring))
                                    .togetherWith(fadeOut(DuroContentSpring))
                            },
                            label = "displayPreview"
                        ) { type ->
                            DisplayPreview(type = type)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = option.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) DuroTextPrimary else DuroTextSecondary
                    )
                    Text(
                        text = option.description,
                        fontSize = 11.sp,
                        color = DuroTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("Название привычки", color = DuroTextSecondary) },
                placeholder = { Text("Workout, Make Bed, Read...", color = DuroTextMuted) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = duioFieldColors(selectedColor),
                singleLine = true
            )

            // Частота
            Column {
                Text(
                    text = "Частота",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DuroTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "DAILY" to "Каждый день",
                        "WEEKLY" to "Раз в неделю",
                        "MONTHLY" to "Раз в месяц"
                    ).forEach { (value, label) ->
                        ModeChip(
                            label = label,
                            selected = frequency == value,
                            accent = selectedColor,
                            modifier = Modifier.weight(1f),
                            onClick = { onFrequencyChange(value) }
                        )
                    }
                }
            }

            // Дни недели
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Дни недели",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DuroTextSecondary
                    )
                    val daysSummary = when {
                        scheduleDays.size == 7 -> "Каждый день"
                        scheduleDays == (1..5).toSet() -> "По будням"
                        scheduleDays == setOf(6, 7) -> "По выходным"
                        else -> "${scheduleDays.size} дн. в нед."
                    }
                    Text(
                        text = daysSummary,
                        fontSize = 11.sp,
                        color = selectedColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    (1..7).forEach { day ->
                        val selected = day in scheduleDays
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) selectedColor.copy(alpha = 0.25f) else DuroSurface)
                                .border(1.dp, if (selected) selectedColor else DuroBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    val updated = if (selected) {
                                        if (scheduleDays.size > 1) scheduleDays - day else scheduleDays
                                    } else {
                                        scheduleDays + day
                                    }
                                    onScheduleDaysChange(updated)
                                    if (updated.size == 7) {
                                        onFrequencyChange("DAILY")
                                    } else {
                                        onFrequencyChange("WEEKLY")
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayNames[day - 1],
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) DuroTextPrimary else DuroTextSecondary
                            )
                        }
                    }
                }
            }

            // Категория
            Column {
                Text(
                    text = "Категория",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DuroTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                categories.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { cat ->
                            ModeChip(
                                label = cat,
                                selected = cat == category,
                                accent = selectedColor,
                                modifier = Modifier.weight(1f),
                                onClick = { onCategoryChange(cat) }
                            )
                        }
                    }
                }
            }
            AddCategoryRow(onAddCategory = onAddCategory)

            // Цвет
            Column {
                Text(
                    text = "Цветовая тема",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DuroTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    colors.forEach { (hex, color) ->
                        val isSelected = hex == selectedColorHex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { onColorChange(hex) }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(3.dp, Color.White, CircleShape)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = quote,
                onValueChange = onQuoteChange,
                label = { Text("Мотивационная цитата", color = DuroTextSecondary) },
                placeholder = { Text("Необязательно", color = DuroTextMuted) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = duioFieldColors(selectedColor),
                singleLine = true
            )

            // Норма количества: «2» + «л» — раньше поле было только в БД.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = target,
                    onValueChange = onTargetChange,
                    label = { Text("Норма", color = DuroTextSecondary) },
                    placeholder = { Text("1", color = DuroTextMuted) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = duioFieldColors(selectedColor),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = onUnitChange,
                    label = { Text("Единица", color = DuroTextSecondary) },
                    placeholder = { Text("раз, л, стр.", color = DuroTextMuted) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = duioFieldColors(selectedColor),
                    singleLine = true
                )
            }

            // Воздержание и цель «N раз в неделю».
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(
                    checked = avoid,
                    onCheckedChange = onAvoidChange,
                    colors = CheckboxDefaults.colors(checkedColor = selectedColor)
                )
                Text(
                    text = "Воздержание («не делать» вместо «делать»)",
                    fontSize = 13.sp,
                    color = DuroTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Раз в неделю:",
                    fontSize = 13.sp,
                    color = DuroTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                listOf(0, 2, 3, 5).forEach { n ->
                    val label = if (n == 0) "каждый день" else "×$n"
                    val selected = perWeek == n
                    ModeChip(
                        label = label,
                        selected = selected,
                        accent = selectedColor,
                        onClick = { onPerWeekChange(n) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCreationContent(
    title: String,
    onTitleChange: (String) -> Unit,
    taskType: TaskType,
    onTaskTypeChange: (TaskType) -> Unit,
    priority: Priority,
    onPriorityChange: (Priority) -> Unit,
    categories: List<String>,
    category: String,
    onCategoryChange: (String) -> Unit,
    dueOffset: Int,
    onDueOffsetChange: (Int) -> Unit,
    onAddCategory: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Быстрые шаблоны",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DuroTextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Позвонить", "Позвонить", "General"),
                Triple("Купить", "Купить", "Shopping"),
                Triple("Оплатить", "Оплатить", "Finance")
            ).forEach { (label, title, cat) ->
                ModeChip(
                    label = label,
                    selected = false,
                    accent = AppTheme.colors.accent,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onTitleChange(title)
                        onCategoryChange(cat)
                    }
                )
            }
        }

        Text(
            text = "Быстрая задача или долгая цель",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DuroTextSecondary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TaskTypeCard(
                modifier = Modifier.weight(1f),
                selected = taskType == TaskType.QUICK,
                accent = AppTheme.colors.accent,
                icon = Icons.Default.Bolt,
                title = "Быстрая",
                description = "Одно действие: закрыл — и забыл",
                onClick = { onTaskTypeChange(TaskType.QUICK) }
            )
            TaskTypeCard(
                modifier = Modifier.weight(1f),
                selected = taskType == TaskType.LONG,
                accent = AppTheme.colors.accentSecondary,
                icon = Icons.Default.LocalFireDepartment,
                title = "Долгая",
                description = "Цель как привычка, живёт в списке",
                onClick = { onTaskTypeChange(TaskType.LONG) }
            )
        }

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Название задачи", color = DuroTextSecondary) },
            placeholder = { Text("Например: выучить английский", color = DuroTextMuted) },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = RoundedCornerShape(16.dp),
            colors = duioFieldColors(AppTheme.colors.accent),
            singleLine = true
        )

        Column {
            Text(
                text = "Приоритет",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DuroTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Priority.LOW to ("Низкий" to DuroTextMuted),
                    Priority.MEDIUM to ("Средний" to DuroAmber),
                    Priority.HIGH to ("Срочный" to DuroRed)
                ).forEach { (value, meta) ->
                    ModeChip(
                        label = meta.first,
                        selected = priority == value,
                        accent = meta.second,
                        modifier = Modifier.weight(1f),
                        onClick = { onPriorityChange(value) }
                    )
                }
            }
        }

        Column {
            Text(
                text = "Срок",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DuroTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Сегодня", 1 to "Завтра", 2 to "Послезавтра", 7 to "Неделя").forEach { (offset, label) ->
                    ModeChip(
                        label = label,
                        selected = dueOffset == offset,
                        accent = AppTheme.colors.accent,
                        modifier = Modifier.weight(1f),
                        onClick = { onDueOffsetChange(offset) }
                    )
                }
            }
        }

        Column {
            Text(
                text = "Категория",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DuroTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            categories.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { cat ->
                        ModeChip(
                            label = cat,
                            selected = cat == category,
                            accent = AppTheme.colors.accent,
                            modifier = Modifier.weight(1f),
                            onClick = { onCategoryChange(cat) }
                        )
                    }
                }
            }
        }
        AddCategoryRow(onAddCategory = onAddCategory)
    }
}

@Composable
private fun DisplayPreview(type: String) {
    when (type) {
        "DAILY_CHECK" -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Check",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Today",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        "GRID" -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (r in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until 7) {
                            val isBright = (r + c) % 3 != 0
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isBright) Color.White.copy(0.9f) else Color.White.copy(0.2f)
                                    )
                            )
                        }
                    }
                }
            }
        }

        "BAR_GRAPH" -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(90.dp)
            ) {
                listOf(50.dp, 80.dp, 35.dp, 75.dp, 40.dp, 60.dp, 85.dp).forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(h)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(0.9f))
                    )
                }
            }
        }

        "STREAKS" -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Flame",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "13 Streaks",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }

        else -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 0.75f },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White,
                    trackColor = Color.White.copy(0.2f),
                    strokeWidth = 6.dp
                )
                Text(
                    text = "75%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
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
                1.dp,
                if (selected) accent else DuroBorder,
                RoundedCornerShape(12.dp)
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
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun AddCategoryRow(onAddCategory: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("+ Своя категория", fontSize = 12.sp, color = DuroTextPrimary)
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Новая категория", color = DuroTextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Например: Учёба", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddCategory(newName)
                        newName = ""
                        showDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Добавить", color = AppTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Отмена", color = DuroTextSecondary)
                }
            },
            containerColor = DuroSurface
        )
    }
}

@Composable
private fun TaskTypeCard(
    selected: Boolean,
    accent: Color,
    icon: ImageVector,
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
private fun duioFieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    unfocusedBorderColor = DuroBorder,
    focusedTextColor = DuroTextPrimary,
    unfocusedTextColor = DuroTextPrimary,
    focusedContainerColor = DuroSurface,
    unfocusedContainerColor = DuroSurface
)
