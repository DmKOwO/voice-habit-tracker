package com.voicehabit.tracker.presentation.programmes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.core.analysis.HabitProjection
import com.voicehabit.tracker.core.analysis.ProgrammeDraft
import com.voicehabit.tracker.core.analysis.ProgrammeTemplates
import com.voicehabit.tracker.domain.model.Programme
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.hub.ScreenHeader
import com.voicehabit.tracker.presentation.theme.*

/**
 * P1. Экран тренировочных программ.
 *
 * ## Два состояния в одной компоновке
 *
 * Пустой экран предлагает два входа — встроенный шаблон и вставку из буфера — и
 * показывает, что именно будет создано, **до** записи. Активная программа
 * показывает неделю целиком: человек должен видеть не только сегодняшний день, но и
 * что завтра отдых. Иначе «сплит 3/4/7» превращается в ощущение пропущенных дней.
 *
 * ## Почему показываем решение по дням
 *
 * [HabitProjection.reason] выводится рядом с планом намеренно: «почему это серия, а
 * не сетка» и «встроим в существующую» — решения, которые иначе человек увидит
 * только по факту, уже в списке привычек.
 */
@Composable
fun ProgrammesScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val draft = state.programmeDraft

    if (draft != null) {
        ProgrammeImportPreview(
            draft = draft,
            plan = state.programmePlan,
            importing = state.programmeImporting,
            onConfirm = { viewModel.confirmProgrammeImport(draft) },
            onDiscard = viewModel::discardProgrammeDraft
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
            title = "Программы",
            subtitle = state.programme?.let { "${it.sessionsPerWeek} тренировки в неделю" } ?: "Тренировочный план и прогресс"
        ) { viewModel.closeScreen() }
        Spacer(modifier = Modifier.height(12.dp))

        state.programmeMessage?.let { message ->
            MessageCard(message)
            Spacer(modifier = Modifier.height(12.dp))
        }

        val programme = state.programme
        if (programme == null) {
            ImportPanel(
                onUseTemplate = {
                    viewModel.previewProgrammeFromClipboard(ProgrammeTemplates.CALISTHENICS)
                },
                onPaste = {
                    val text = clipboard.getText()?.text.orEmpty()
                    if (text.isBlank()) {
                        viewModel.discardProgrammeDraft()
                    }
                    viewModel.previewProgrammeFromClipboard(text)
                }
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                ProgrammeHeaderCard(
                    title = programme.title,
                    athleteNote = programme.athleteNote,
                    goals = programme.goals,
                    trainingWeekdays = programme.trainingWeekdays,
                    mentionedWeekdays = programme.mentionedWeekdays,
                    sessionsPerWeek = programme.sessionsPerWeek
                )
            }
            items(programme.days, key = { it.id }) { day ->
                ProgrammeDayCard(
                    day = day,
                    isToday = day.weekday == programme.todayWeekday,
                    onLogSet = { exercise ->
                        viewModel.logProgrammeExercise(
                            dayId = day.id,
                            exerciseId = exercise.id,
                            sets = (exercise.setsDoneToday + 1).coerceAtMost(exercise.sets),
                            value = if (exercise.targetValue > 0) exercise.targetValue else null
                        )
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.completeProgrammeDay() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Whatshot, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Тренировка сделана целиком")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.deleteProgramme() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = DuroRed)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Удалить программу, оставить привычки")
                }
            }
        }
    }
}

@Composable
private fun ProgrammeHeaderCard(
    title: String,
    athleteNote: String,
    goals: String,
    trainingWeekdays: Set<Int>,
    mentionedWeekdays: Set<Int>,
    sessionsPerWeek: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DuroSurface)
            .border(1.dp, DuroBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(title.ifBlank { "Программа" }, color = DuroTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (athleteNote.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(athleteNote, color = DuroTextSecondary, fontSize = 13.sp)
        }
        if (goals.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Цели", color = DuroTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(goals, color = DuroTextPrimary, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "$sessionsPerWeek ${if (sessionsPerWeek % 10 == 1 && sessionsPerWeek % 100 != 11) "тренировка" else "тренировки"} в неделю",
            color = DuroTextSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            mentionedWeekdays.sorted().forEach { weekday ->
                val trains = weekday in trainingWeekdays
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (trains) DuroOrange.copy(alpha = 0.18f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (trains) DuroOrange else DuroBorder,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        Programme.Day(id = "", programmeId = "", weekday = weekday).weekdayLabel,
                        color = if (trains) DuroOrange else DuroTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgrammeDayCard(
    day: Programme.Day,
    isToday: Boolean,
    onLogSet: (Programme.Exercise) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isToday) DuroSurfaceElevated else DuroSurface)
            .border(
                1.dp,
                if (isToday) DuroOrange.copy(alpha = 0.5f) else DuroBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(day.weekdayLabel, color = DuroOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (isToday) "сегодня" else day.weekdayFull.lowercase(),
                        color = if (isToday) DuroOrange else DuroTextMuted,
                        fontSize = 11.sp
                    )
                }
                Text(
                    day.title.ifBlank { if (day.isRest) "День отдыха" else "Тренировка" },
                    color = DuroTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (day.focusNote.isNotBlank()) {
                    Text(day.focusNote, color = DuroTextSecondary, fontSize = 12.sp)
                }
            }
            if (day.habitId != null) {
                Text(
                    "в привычке",
                    color = DuroTextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, DuroBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (day.exercises.isEmpty()) return@Column

        Spacer(modifier = Modifier.height(10.dp))
        day.exercises.forEach { exercise ->
            ExerciseRow(exercise = exercise, onLogSet = { onLogSet(exercise) })
            if (exercise != day.exercises.last()) {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Programme.Exercise, onLogSet: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = exercise.setsDoneToday < exercise.sets, onClick = onLogSet)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(exercise.title, color = DuroTextPrimary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exercise.prescription, color = DuroTextSecondary, fontSize = 11.sp)
                if (exercise.tempo.isNotBlank()) {
                    Text(exercise.tempo, color = DuroTextMuted, fontSize = 11.sp)
                }
                if (exercise.hasProgression) {
                    Text(exercise.progressionLabel, color = DuroOrange, fontSize = 11.sp)
                }
            }
            if (exercise.outdoor.isNotBlank() || exercise.home.isNotBlank()) {
                Text(
                    listOf(exercise.outdoor, exercise.home).filter { it.isNotBlank() }.joinToString(" · "),
                    color = DuroTextMuted,
                    fontSize = 10.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        SetDots(done = exercise.setsDoneToday, total = exercise.sets)
    }
}

/**
 * Точки подходов вместо чекбокса.
 *
 * Человек не думает «отметить или нет», он считает «сколько подходов осталось».
 * Заполненные точки дают этот ответ без чтения цифр.
 */
@Composable
private fun SetDots(done: Int, total: Int) {
    val count = total.coerceIn(1, 10)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (index < done) DuroOrange else DuroBorder)
            )
        }
    }
}

@Composable
private fun ImportPanel(onUseTemplate: () -> Unit, onPaste: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Приложение разберёт программу сам: дни недели, упражнения, подходы, повторы, " +
                "темп, отдых и прогрессия. Перед сохранением покажу, что получилось.",
            color = DuroTextSecondary,
            fontSize = 13.sp
        )

        OutlinedButton(
            onClick = onUseTemplate,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Встроенный шаблон: калистеника Пн/Ср/Пт")
        }

        OutlinedButton(
            onClick = onPaste,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Вставить свой текст из буфера")
        }

        ProgrammeTemplates.all.forEach { template ->
            Text(
                "${template.name} — ${template.note}",
                color = DuroTextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Предпросмотр импорта.
 *
 * Показывает и разбор, и последствия: без плана привычек человек не понимает,
 * не будет ли программа конфликтовать с тем, что уже есть в его списке.
 */
@Composable
private fun ProgrammeImportPreview(
    draft: ProgrammeDraft,
    plan: List<HabitProjection>,
    importing: Boolean,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(DuroBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(title = "Проверьте разбор", subtitle = "Ничего ещё не сохранено") { onDiscard() }
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                ProgrammeHeaderCard(
                    title = draft.title,
                    athleteNote = draft.athleteNote,
                    goals = draft.goals,
                    trainingWeekdays = draft.trainingDays.map { it.weekday }.toSet(),
                    mentionedWeekdays = draft.days.map { it.weekday }.filter { it in 1..7 }.toSet(),
                    sessionsPerWeek = draft.trainingDays.size
                )
            }

            if (draft.warnings.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, DuroAmber, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text("Что не удалось разобрать", color = DuroAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        draft.warnings.forEach { warning ->
                            Text("· $warning", color = DuroTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Text(
                    "Привычки: ${plan.count { !it.isReused }} новых, ${plan.count { it.isReused }} существующих",
                    color = DuroTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Ключ — день недели, а не dayId: у черновика разбора id ещё пустые
            // (их назначает репозиторий при записи), и три дня приходили с ключом
            // "" — LazyColumn падал с «Key "" was already used».
            items(plan, key = { "plan-${it.weekday}" }) { projection ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DuroSurface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${Programme.Day(id = "", programmeId = "", weekday = projection.weekday).weekdayLabel} · ${projection.habitTitle}",
                            color = DuroTextPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            "Только ${projection.scheduleDays.sorted().joinToString(", ") { d ->
                                Programme.Day(id = "", programmeId = "", weekday = d).weekdayLabel
                            }} · ${projection.action}",
                            color = DuroTextSecondary,
                            fontSize = 11.sp
                        )
                        Text(projection.reason, color = DuroTextMuted, fontSize = 10.sp)
                    }
                }
            }

            items(draft.trainingDays, key = { "day-${it.weekday}" }) { day ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DuroSurface)
                        .padding(12.dp)
                ) {
                    Text(
                        "${day.weekdayLabel} — ${day.title}",
                        color = DuroOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    day.exercises.forEach { exercise ->
                        Text(
                            "${exercise.title} — ${exercise.prescription}" +
                                if (exercise.hasProgression) " · ${exercise.progressionLabel}" else "",
                            color = DuroTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Отмена") }

            Button(
                onClick = onConfirm,
                enabled = !importing,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (importing) "Сохраняю…" else "Сохранить") }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MessageCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DuroSurfaceElevated)
            .border(1.dp, DuroBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(message, color = DuroTextPrimary, fontSize = 12.sp)
    }
}
