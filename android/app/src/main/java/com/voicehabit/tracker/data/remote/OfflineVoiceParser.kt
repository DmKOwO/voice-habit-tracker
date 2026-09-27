package com.voicehabit.tracker.data.remote

import com.voicehabit.tracker.core.analysis.ActionTitle
import com.voicehabit.tracker.core.analysis.DigestBuilder
import com.voicehabit.tracker.core.analysis.IntentRouter
import com.voicehabit.tracker.data.local.entity.HabitEntity
import com.voicehabit.tracker.data.local.entity.TaskEntity
import com.voicehabit.tracker.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Локальный разбор фразы без сети. Работает отдельными шагами, а не одним поиском подстроки:
 *
 * 0. [com.voicehabit.tracker.core.analysis.IntentRouter] — **что** человек делает: ставит
 *    задачу, задаёт вопрос или просто говорит. Это решает всё остальное: при потоке мыслей
 *    список дел вообще не трогается, вместо него собирается конспект.
 * 1. [classifyIntent] — что хотел пользователь: отметить выполненное, поставить задачу или просто заметить.
 * 2. [extractWhen] — когда: «завтра», «в 18:30», «через 3 дня», «в понедельник».
 * 3. [extractActions] — что именно: чистит модальные слова («надо», «нужно») и нормализует глагол.
 * 4. [com.voicehabit.tracker.core.analysis.DigestBuilder] — конспект свободного потока.
 *
 * Привычка отмечается выполненной только при явном маркере прошедшего времени.
 * Раньше любое совпадение слова из названия закрывало привычку: фраза
 * «надо купить витамины» закрывала привычку «Vitamin».
 */
object OfflineVoiceParser {

    private val PAST_MARKERS = listOf(
        "сделал", "сделала", "сделали", "выполнил", "выполнила", "выполнили", "закончил", "закончила",
        "сдал", "сдала", "сдали", "отправил", "отправила", "опубликовал", "опубликовала", "выложил",
        "выпил", "выпила", "принял", "приняла", "потренировался", "потренировалась", "потренировал",
        "пробежал", "пробежала", "заправил", "заправила", "застелил", "застелила", "поел", "поела",
        "позавтракал", "позавтракала", "погулял", "погуляла", "прочитал", "прочитала", "доделал",
        "доделала", "написал", "написала", "получил", "получила", "сходил", "сходила", "позвонил",
        "позвонила", "купил", "купила", "проверил", "проверила", "готов", "готово", "выполнено",
        "отмечено", "отметил", "отметила", "подписал", "подписала", "решил", "понял", "смог"
    )

    private val ALL_DONE_MARKERS = listOf(
        "всё сделал", "все сделал", "сделал всё", "сделала всё", "сделали всё", "всё выполнил",
        "все выполнил", "выполнил всё", "выполнила всё", "всё готово", "все готово",
        "все привычки сделал", "всё закончил", "все закончил"
    )

    private val PLAN_TRIGGERS = listOf(
        "напомни", "надо", "нужно", "не забыть", "не забудь", "задача", "задачи", "задачу", "задач",
        "запланируй", "добавь", "поставь", "хочу", "буду", "должен", "должна", "планирую", "напомнить"
    )

    private val RECURRING_MARKERS = listOf(
        "привычка", "привычк", "каждый день", "ежедневно", "ежедневный", "постоянно", "регулярно",
        "каждую неделю", "систематически", "на регулярной основе"
    )

    private val LEADING_PREFIXES = listOf(
        "напомни мне", "запланируй", "добавь задачу", "поставь задачу", "не забудь", "не забыть",
        "мне нужно", "мне надо", "я хочу", "я буду", "я должен", "я должна", "нужно", "надо",
        "должен", "должна", "хочу", "буду", "давай", "пожалуйста", "добавь", "поставь"
    )

    private val FILLER_WORDS = listOf(
        "напомни", "надо", "нужно", "должен", "должна", "хочу", "буду", "давай", "пожалуйста",
        "запланируй", "добавь", "поставь", "не забудь", "не забыть", "спасибо", "мне", "я",
        "ок", "окей", "хорошо"
    )

    private val WEEKDAYS = mapOf(
        "понедельник" to 1, "вторник" to 2, "среда" to 3, "среду" to 3, "четверг" to 4, "пятница" to 5,
        "пятницу" to 5, "суббота" to 6, "субботу" to 6, "воскресенье" to 7, "воскресенья" to 7
    )

    private val VERB_NORMALIZATION = mapOf(
        "поучить" to "выучить", "поучать" to "учить", "почитать" to "прочитать", "погуглить" to "изучить",
        "покормить" to "покормить", "принять" to "принять", "сходить" to "сходить", "позвонить" to "позвонить",
        "написать" to "написать", "купить" to "купить", "оплатить" to "оплатить", "заплатить" to "оплатить",
        "сделать" to "сделать", "разобраться" to "разобраться", "повторить" to "повторить", "изучить" to "изучить"
    )

    private val HABIT_SYNONYMS: Map<String, List<String>> = mapOf(
        "habit_make_bed" to listOf("кроват", "постел", "заправил", "застелил", "bed"),
        "habit_workout" to listOf("трениров", "зарядк", "воркаут", "workout", "зал", "отжимани", "бег", "спортзал"),
        "habit_posting" to listOf("пост", "статью", "выложил", "опубликовал", "публикац", "posting"),
        "habit_vitamin" to listOf("витамин", "таблетк", "лекарств", "омега", "д3", "d3")
    )

    private val ACTION_VERBS = listOf(
        "позвонить", "написать", "купить", "сделать", "оплатить", "заплатить", "прочитать", "выучить",
        "изучить", "подготовить", "записаться", "сходить", "принять", "начать", "закончить", "отправить",
        "забрать", "получить", "проверить", "сдать", "закрыть", "открыть", "запустить", "погулять",
        "покормить", "попить", "поменять", "заменить", "забронировать", "заказать", "зарегистрироваться"
    )

    private val INFINITIVE_ENDINGS = listOf("ить", "ать", "еть", "уть", "ыть", "ять")

    private val CATEGORY_MARKERS: List<Pair<String, List<String>>> = listOf(
        "Work" to listOf("работ", "проект", "отчет", "отчёт", "встреч", "созвон", "письм", "дедлайн"),
        "Health" to listOf("спорт", "трениров", "бег", "зал", "здоров", "витамин", "таблетк", "лекарств", "врач"),
        "Home" to listOf("квартир", "дом", "уборк", "стирк", "готов", "посуд"),
        "Study" to listOf("учит", "английск", "курс", "лекц", "учеб", "книг", "экзамен")
    )

    enum class Intent { COMPLETED, PLAN, NOTE, COMMAND }

    private val DELETE_MARKERS = listOf("удали", "удалить", "убери", "убрать", "вычеркни", "вычеркнуть", "сотри", "стереть")
    private val RESCHEDULE_MARKERS = listOf("перенеси", "перенести", "отложи", "отложить", "перемести", "сдвинь")
    private val FOCUS_MARKERS = listOf("начни фокус", "включи фокус", "фокус", "помодоро", "помодор", "сфокусируйся", "сосредоточься")
    private val SUMMARY_MARKERS = listOf(
        "что у меня сегодня", "что у меня запланировано", "мои дела", "что осталось",
        "что нужно сделать", "расскажи мои дела", "подведи итог", "что сегодня"
    )
    private val TASK_TEMPLATES = mapOf(
        "позвонить" to ("Позвонить" to "General"),
        "купить" to ("Купить" to "Shopping"),
        "оплатить" to ("Оплатить" to "Finance"),
        "заплатить" to ("Оплатить" to "Finance"),
        "написать" to ("Написать" to "Work"),
        "встреча" to ("Встреча" to "Work"),
        "врач" to ("Записаться к врачу" to "Health"),
        "спорт" to ("Тренировка" to "Fitness")
    )

    fun parse(
        transcript: String,
        activeHabits: List<HabitEntity>,
        openTasks: List<TaskEntity>,
        speechSeconds: Int = 0,
        /**
         * H1. Режим, выбранный пользователем вручную.
         *
         * Не «подпись, которую надо перерисовать», а именно режим разбора: при
         * [IntentMode.DICTATE] сущности не создаются вообще, при [IntentMode.MIXED]
         * берутся из «что дальше», при [IntentMode.LOG] — из глагола действия.
         * Перерисовывать только ярлык было бы худшим вариантом: в шторке написано
         * «Выжимка», а в списке дел появляется задача, собранная из начала монолога.
         */
        forcedMode: IntentMode? = null
    ): VoiceNoteAction {
        val lower = transcript.lowercase(Locale.getDefault()).trim()
        val insights = mutableListOf<String>()

        // 0. Сначала решаем, что человек вообще делает. Раньше этот шаг был в конце,
        //    и поток мыслей без «надо/напомни» молча уходил в заметку, которая нигде
        //    не сохранялась: пользователь получал пустую шторку разбора.
        val detected = IntentRouter.route(transcript)
        val verdict = if (forcedMode == null) {
            detected
        } else {
            com.voicehabit.tracker.core.analysis.ModeVerdict(
                mode = forcedMode,
                confidence = 1f,
                reason = "Режим выбран вручную вместо «${detected.mode.label}»"
            )
        }
        insights.add(
            "Режим: ${verdict.mode.label}, " +
                if (forcedMode != null) "выбран вручную" else
                    "уверенность ${(verdict.confidence * 100).toInt()}%" +
                " — ${verdict.reason}"
        )

        val dueDateIso = extractWhen(lower)?.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val isRecurring = containsAnyStem(lower, RECURRING_MARKERS)

        val completedHabits = mutableListOf<HabitCompletedAction>()
        val tasksToAdd = mutableListOf<TaskCreateAction>()
        val tasksToComplete = mutableListOf<TaskCompleteAction>()
        val tasksToDelete = mutableListOf<TaskDeleteAction>()
        val tasksToReschedule = mutableListOf<TaskRescheduleAction>()
        var focusToStart: FocusStartAction? = null
        var daySummaryRequested = false
        val quickNotes = mutableListOf<QuickNoteAction>()

        // Сущности создаются только в режимах, которые действительно про поручение.
        // При потоке мыслей список дел не трогаем вообще.
        if (verdict.mode.producesEntities) {
            val intent = classifyIntent(lower, insights)

            if (intent == Intent.COMMAND) {
                handleCommand(lower, transcript, openTasks, dueDateIso, insights,
                    tasksToDelete, tasksToComplete, tasksToReschedule, tasksToAdd, quickNotes,
                    onFocus = { focusToStart = it }, onSummary = { daySummaryRequested = true })
            }

            if (intent == Intent.COMPLETED) {
                insights.add("Намерение: отметить выполненное (в прошедшем времени)")
                matchHabits(lower, activeHabits, completedHabits, insights)
                matchTasks(lower, openTasks, tasksToComplete, insights)

                if (completedHabits.isEmpty() && tasksToComplete.isEmpty()) {
                    insights.add("Ничего не совпало — сохраняю заметку, привычки наугад не закрываю")
                    quickNotes.add(QuickNoteAction(text = transcript, tags = listOf("голос", "не разобрано")))
                }
            }

            if (intent == Intent.PLAN) {
                if (verdict.mode == IntentMode.MIXED) {
                    // Действие есть, но фраза — часть монолога. Название задачи берём
                    // из раздела «что дальше» конспекта (ниже), а не из первых слов потока.
                    insights.add("Намерение: план внутри рассуждения — название возьму из «что дальше»")
                } else {
                    insights.add("Намерение: поставить задачу")
                    val cleaned = extractActions(lower, insights)
                    if (cleaned.isNotBlank()) {
                        val type = if (isRecurring) TaskType.LONG else TaskType.QUICK
                        insights.add("Тип: ${if (type.isLong) "долгая цель" else "быстрая задача"}")
                        tasksToAdd.add(
                            TaskCreateAction(
                                title = cleaned.take(60).trim().replaceFirstChar { it.uppercase() },
                                dueDate = dueDateIso,
                                priority = if (containsAnyStem(lower, listOf("срочно", "важно", "обязательно"))) "HIGH" else "MEDIUM",
                                category = inferCategory(lower, cleaned),
                                taskType = type.name
                            )
                        )
                    } else {
                        insights.add("После модальных слов ничего не осталось — сохраняю как заметку")
                        quickNotes.add(QuickNoteAction(text = transcript, tags = listOf("голос", "заметка")))
                    }
                }
            }
        } else if (verdict.mode == IntentMode.QUERY && containsAnyPhrase(lower, SUMMARY_MARKERS)) {
            // «Что у меня сегодня» приложение умеет ответить само — сущности не создаём.
            insights.add("Запрошена сводка дня — сущностей не создаю")
            daySummaryRequested = true
        } else {
            insights.add("Сущности не создаю: это не поручение, а свободная речь")
        }

        // Конспект собираем для всего, кроме двух случаев: режим «задача» (слова уже
        // разобрались в сущности) и вопрос, на который приложение отвечает само.
        // Второй случай важнее, чем кажется: «что у меня сегодня» не должно оставлять
        // после себя конспект «Что у меня сегодня», потому что ответ на него — не конспект,
        // а сводка дня. А вот на «почему тогда сломалось» конспект обязателен: иначе слова
        // пользователя просто пропадают, потому что мы их не поняли.
        val digest = if (verdict.mode.needsDigest && !daySummaryRequested) {
            DigestBuilder.build(transcript, speechSeconds).also { built ->
                insights.add(
                    buildString {
                        append("Конспект: ")
                        if (built.title.isNotBlank()) append(built.title)
                        if (built.filledSections > 0) append(" · заполнено секций: ${built.filledSections}")
                        if (built.tone.isNotBlank()) append(" · тон: ${built.tone}")
                    }
                )
            }
        } else {
            null
        }

        // H1. В режиме MIXED задача строится НЕ из сырой речи, а из раздела «что дальше»
        // конспекта. Иначе на длинном потоке получается задача «Короче тут надиктовываю
        // мысли после разговора с» — мусор из первых шести слов монолога, попавший в
        // список дел. Разбор «что дальше» отбирает законченные действия, а не начало потока.
        if (verdict.mode == IntentMode.MIXED && digest != null) {
            if (tasksToAdd.isEmpty() && tasksToDelete.isEmpty() && tasksToReschedule.isEmpty()) {
                val steps = digest.nextSteps.take(3)
                for ((index, step) in steps.withIndex()) {
                    val title = cleanStepTitle(step)
                    if (title.isBlank()) continue
                    // Дата берётся из САМОГО шага, а не из всего монолога: «переносим
                    // релиз на пятницу, …, и надо бы предупредить команду» не должен
                    // ставить «предупредить команду» на пятницу — пятница относится
                    // к переносу релиза, о котором этот шаг вообще не знает.
                    tasksToAdd.add(
                        TaskCreateAction(
                            // take(60) может разрезать фразу по пробелу, и без trim
                            // заголовок заканчивался бы висящим пробелом.
                            title = title.take(60).trim(),
                            dueDate = extractWhen(step.lowercase(Locale.getDefault()))
                                ?.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            priority = if (containsAnyStem(step.lowercase(Locale.getDefault()), listOf("срочно", "важно", "обязательно"))) "HIGH" else "MEDIUM",
                            category = inferCategory(step.lowercase(Locale.getDefault()), title),
                            taskType = TaskType.QUICK.name
                        )
                    )
                    insights.add("Задача ${index + 1} взята из раздела «что дальше»: «$step»")
                }
                if (tasksToAdd.isNotEmpty()) {
                    insights.add("Действие найдено внутри рассуждения — остальное сохранено конспектом")
                }
            }
        }

        val summaryParts = mutableListOf<String>()
        if (digest != null) {
            // Сводка — это то, что попадёт в список записей голоса и в уведомление.
            // Полный текст выжимки туда класть нельзя: список должен оставаться
            // обозримым, а сама выжимка живёт в конспекте.
            summaryParts.add("Конспект: " + digest.title.ifBlank { "диктовка" })
            val counts = buildList {
                if (digest.decisions.isNotEmpty()) add("решений: ${digest.decisions.size}")
                if (digest.nextSteps.isNotEmpty()) add("шагов: ${digest.nextSteps.size}")
                if (digest.openQuestions.isNotEmpty()) add("вопросов: ${digest.openQuestions.size}")
            }
            if (counts.isNotEmpty()) summaryParts.add(counts.joinToString(" · "))
        }
        if (completedHabits.isNotEmpty()) {
            summaryParts.add("Привычки: " + completedHabits.joinToString(", ") { it.habitTitle })
        }
        if (tasksToAdd.isNotEmpty()) {
            val task = tasksToAdd.first()
            summaryParts.add(
                "Задача${if (task.taskType == TaskType.LONG.name) " (долгая)" else ""}: ${task.title}" +
                    (task.dueDate?.let { " · до ${formatDueLabel(it)}" } ?: "")
            )
        }
        if (tasksToComplete.isNotEmpty()) {
            summaryParts.add("Закрыта задача: " + tasksToComplete.first().taskTitle)
        }
        if (tasksToDelete.isNotEmpty()) {
            summaryParts.add("Удалить задачу: " + tasksToDelete.first().taskTitle)
        }
        if (tasksToReschedule.isNotEmpty()) {
            val r = tasksToReschedule.first()
            summaryParts.add("Перенести «${r.taskTitle}»" + (r.newDueDate?.let { " · до ${formatDueLabel(it)}" } ?: ""))
        }
        focusToStart?.let { summaryParts.add("Фокус ${it.minutes} мин: ${it.label}") }
        if (daySummaryRequested) {
            summaryParts.add("Сводка дня запрошена")
        }
        if (summaryParts.isEmpty()) {
            summaryParts.add("Заметка сохранена: «$transcript»")
        }

        return VoiceNoteAction(
            rawTranscript = transcript,
            summary = summaryParts.joinToString(" • "),
            habitsCompleted = completedHabits,
            tasksToAdd = tasksToAdd,
            tasksToComplete = tasksToComplete,
            tasksToDelete = tasksToDelete,
            tasksToReschedule = tasksToReschedule,
            focusToStart = focusToStart,
            daySummaryRequested = daySummaryRequested,
            quickNotes = quickNotes,
            digest = digest,
            mode = verdict.mode,
            modeConfidence = verdict.confidence,
            modeReason = verdict.reason,
            modeIsOverridden = forcedMode != null,
            insights = insights,
            sttDurationMs = 50,
            llmDurationMs = 50,
            modelUsed = "Встроенный офлайн-движок (Мгновенно)"
        )
    }

    /** G2–G6: команды управления — проверяются раньше обычных намерений. */
    private fun handleCommand(
        lower: String,
        transcript: String,
        openTasks: List<TaskEntity>,
        dueDateIso: String?,
        insights: MutableList<String>,
        tasksToDelete: MutableList<TaskDeleteAction>,
        tasksToComplete: MutableList<TaskCompleteAction>,
        tasksToReschedule: MutableList<TaskRescheduleAction>,
        tasksToAdd: MutableList<TaskCreateAction>,
        quickNotes: MutableList<QuickNoteAction>,
        onFocus: (FocusStartAction) -> Unit,
        onSummary: () -> Unit
    ) {
        insights.add("Намерение: команда управления")
        when {
            containsAnyStem(lower, SUMMARY_MARKERS) -> {
                insights.add("Запрошена сводка дня — сущностей не создаю")
                onSummary()
            }
            containsAnyStem(lower, FOCUS_MARKERS) -> {
                val minutes = Regex("(\\d{1,3})\\s*(мин|минут)").find(lower)
                    ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 180) ?: 25
                val label = extractActions(lower, insights).ifBlank { "Фокус-сессия" }
                insights.add("Запуск фокуса на $minutes мин")
                onFocus(FocusStartAction(minutes, label.take(60).replaceFirstChar { it.uppercase() }))
            }
            containsAnyStem(lower, DELETE_MARKERS) -> {
                val matched = matchTaskByTitle(lower, openTasks)
                if (matched != null) {
                    insights.add("Удаление задачи «${matched.title}»")
                    tasksToDelete.add(TaskDeleteAction(matched.id, matched.title))
                } else {
                    // Без совпадения — тоже показать, что услышано (id=null = только текст).
                    val cleaned = extractActions(lower, insights).ifBlank { transcript }
                    insights.add("Задача для удаления не найдена — помечаю текстом")
                    tasksToDelete.add(TaskDeleteAction(null, cleaned.take(60)))
                }
            }
            containsAnyStem(lower, RESCHEDULE_MARKERS) -> {
                val matched = matchTaskByTitle(lower, openTasks)
                if (matched != null) {
                    insights.add("Перенос задачи «${matched.title}»")
                    tasksToReschedule.add(TaskRescheduleAction(matched.id, matched.title, dueDateIso))
                } else {
                    insights.add("Задача для переноса не найдена — сохраняю заметку")
                    quickNotes.add(QuickNoteAction(text = transcript, tags = listOf("голос", "не разобрано")))
                }
            }
            lower.startsWith("шаблон") -> {
                val rest = lower.removePrefix("шаблон").removePrefix(":").trim()
                val hit = TASK_TEMPLATES.entries.firstOrNull { (verb, _) -> rest.startsWith(verb) }
                if (hit != null) {
                    val (title, category) = hit.value
                    val detail = rest.removePrefix(hit.key).trim()
                    insights.add("Шаблон задачи: $title")
                    tasksToAdd.add(
                        TaskCreateAction(
                            title = (if (detail.isNotBlank()) "$title $detail" else title).take(60).replaceFirstChar { it.uppercase() },
                            dueDate = dueDateIso,
                            priority = "MEDIUM",
                            category = category
                        )
                    )
                } else {
                    insights.add("Шаблон не распознан — сохраняю заметку")
                    quickNotes.add(QuickNoteAction(text = transcript, tags = listOf("голос", "заметка")))
                }
            }
        }
    }

    /**
     * Заголовок задачи из пункта «что дальше»: снимает связки и модальные слова,
     * оставляя само действие.
     *
     * «и надо бы предупредить команду» → «Предупредить команду». Без этого в списке
     * дел появлялись задачи, начинающиеся с «И», — мелочь, но именно она заставляет
     * человека перечитывать каждую строку списка.
     */
    private fun cleanStepTitle(step: String): String = ActionTitle.from(step)


    private fun matchTaskByTitle(lower: String, openTasks: List<TaskEntity>): TaskEntity? {
        // Самый длинный совпадающий заголовок — точнее короткого.
        return openTasks.mapNotNull { task ->
            val words = task.title.lowercase(Locale.getDefault()).split(" ").filter { it.length >= 4 }
            val hits = words.count { containsWord(lower, it) }
            if (hits > 0) task to hits else null
        }.maxByOrNull { it.second }?.first
    }

    private fun classifyIntent(lower: String, insights: MutableList<String>): Intent {
        if (containsAnyStem(lower, SUMMARY_MARKERS + FOCUS_MARKERS + DELETE_MARKERS + RESCHEDULE_MARKERS)
            || lower.startsWith("шаблон")
        ) {
            return Intent.COMMAND
        }
        val hasPastMarker = containsAnyWord(lower, PAST_MARKERS)
        val hasAllDone = containsAnyPhrase(lower, ALL_DONE_MARKERS)

        return when {
            hasPastMarker || hasAllDone -> Intent.COMPLETED
            containsAnyWord(lower, PLAN_TRIGGERS) -> Intent.PLAN
            // «в 18:30 позвонить врачу» — тут нет ни «надо», ни «напомни», но это явно план.
            containsAnyWord(lower, ACTION_VERBS) || startsWithInfinitive(lower) -> Intent.PLAN
            else -> {
                if (lower.isBlank()) insights.add("Пустая фраза")
                Intent.NOTE
            }
        }
    }

    /** Русский инфинитив в начале фразы: «позвонить», «выучить», «записаться». */
    private fun startsWithInfinitive(lower: String): Boolean {
        val firstWord = lower.trim().substringBefore(' ').trim()
        if (firstWord.length < 4) return false
        return INFINITIVE_ENDINGS.any { firstWord.endsWith(it) }
    }

    private fun matchHabits(
        lower: String,
        activeHabits: List<HabitEntity>,
        out: MutableList<HabitCompletedAction>,
        insights: MutableList<String>
    ) {
        if (containsAnyPhrase(lower, ALL_DONE_MARKERS)) {
            insights.add("Фраза «всё сделал» — отмечаю все активные привычки")
            for (habit in activeHabits) {
                if (out.none { it.habitId == habit.id }) {
                    out.add(
                        HabitCompletedAction(
                            habitId = habit.id,
                            habitTitle = habit.title,
                            incrementValue = habit.targetValue,
                            comment = "Отмечено: всё выполнено"
                        )
                    )
                }
            }
            return
        }

        for (habit in activeHabits) {
            if (!matchesHabit(lower, habit)) continue
            if (out.none { it.habitId == habit.id }) {
                insights.add("Совпадение с привычкой «${habit.title}»")
                out.add(
                    HabitCompletedAction(
                        habitId = habit.id,
                        habitTitle = habit.title,
                        incrementValue = habit.targetValue,
                        comment = "Отмечено голосом"
                    )
                )
            }
        }
    }

    private fun matchesHabit(lower: String, habit: HabitEntity): Boolean {
        val title = habit.title.lowercase(Locale.getDefault())
        val synonyms = HABIT_SYNONYMS[habit.id]
            ?: HABIT_SYNONYMS.entries.firstOrNull { title.contains(it.key) }?.value
            ?: emptyList()

        if (synonyms.any { containsStem(lower, it) }) return true

        return title
            .split(" ", "-", "_")
            .filter { it.length >= 4 }
            .any { containsStem(lower, it) }
    }

    private fun matchTasks(
        lower: String,
        openTasks: List<TaskEntity>,
        out: MutableList<TaskCompleteAction>,
        insights: MutableList<String>
    ) {
        for (task in openTasks) {
            val words = task.title.lowercase(Locale.getDefault())
                .split(" ", "-", "_")
                .filter { it.length >= 4 }
            if (words.isNotEmpty() && words.any { containsStem(lower, it) }) {
                insights.add("Совпадение с открытой задачей «${task.title}»")
                out.add(TaskCompleteAction(taskId = task.id, taskTitle = task.title))
            }
        }
    }

    /** Убирает модальные слова и нормализует глагол: «надо поучить английский» → «выучить английский». */
    private fun extractActions(lower: String, insights: MutableList<String>): String {
        var cleaned = lower.trim()

        for (prefix in LEADING_PREFIXES) {
            if (cleaned.startsWith(prefix) && cleaned.length > prefix.length) {
                cleaned = cleaned.removePrefix(prefix).trim()
                insights.add("Убрал модальные слова: «$prefix»")
                break
            }
        }

        for (filler in FILLER_WORDS) {
            cleaned = removeWord(cleaned, filler)
        }
        cleaned = cleaned.trim().trimEnd('.', ',', '!')

        // Дату и время вырезаем первыми: иначе первое слово остаётся «завтра» и глагол не нормализуется.
        cleaned = stripMetaPhrases(cleaned)

        val firstWord = cleaned.substringBefore(' ').trim()
        val normalized = VERB_NORMALIZATION[firstWord]
        if (normalized != null && normalized != firstWord) {
            cleaned = normalized + cleaned.removePrefix(firstWord)
            insights.add("Нормализовал глагол: «$firstWord» → «$normalized»")
        }

        return cleaned.replace(Regex("\\s+"), " ").trim().trimEnd(',', '.')
    }

    /**
     * Вырезает из названия всё, что уже разобрано отдельно: дату, время, повторяемость,
     * слова срочности. Иначе в названии остаётся «завтра в 18:30».
     */
    /**
     * Вырезает из названия всё, что уже разобрано отдельно: дату, время, повторяемость,
     * слова срочности. Иначе в названии остаётся «завтра в 18:30».
     *
     * Список шаблонов живёт в [ActionTitle], чтобы ручное создание задачи из конспекта
     * вырезало ровно то же самое.
     */
    private fun stripMetaPhrases(text: String): String = ActionTitle.stripMeta(text)

    private fun removeWord(text: String, word: String): String {
        val pattern = Regex("(?<![\\p{L}])${Regex.escape(word)}(?![\\p{L}])")
        return text.replace(pattern, " ")
    }

    /** Возвращает LocalDateTime, если в фразе есть распознаваемая дата или время. */
    private fun extractWhen(lower: String): LocalDateTime? {
        val today = LocalDate.now()
        var date: LocalDate? = null
        var time: LocalTime? = null

        when {
            lower.contains("послезавтра") -> date = today.plusDays(2)
            lower.contains("завтра") -> date = today.plusDays(1)
            lower.contains("сегодня") -> date = today
        }

        val afterDays = Regex("через\\s+(\\d+)\\s*(дн\\w*|день|дня|дней|недел\\w*|месяц\\w*)")
            .find(lower)
        if (afterDays != null) {
            val amount = afterDays.groupValues[1].toIntOrNull()
            if (amount != null) {
                val isWeeks = afterDays.groupValues[2].startsWith("недел")
                val isMonths = afterDays.groupValues[2].startsWith("месяц")
                date = when {
                    isMonths -> today.plusMonths(amount.toLong())
                    isWeeks -> today.plusWeeks(amount.toLong())
                    else -> today.plusDays(amount.toLong())
                }
            }
        }

        for ((name, target) in WEEKDAYS) {
            if (lower.contains(name)) {
                val delta = (target - today.dayOfWeek.value + 7) % 7
                date = today.plusDays(delta.toLong())
                break
            }
        }

        val explicit = Regex("(\\d{1,2})[:.](\\d{2})").find(lower)
        val hour = Regex("\\b(\\d{1,2})\\s*час").find(lower)?.groupValues?.get(1)?.toIntOrNull()

        time = when {
            explicit != null -> runCatching { LocalTime.of(explicit.groupValues[1].toInt(), explicit.groupValues[2].toInt()) }.getOrNull()
            hour != null -> LocalTime.of(hour, 0)
            lower.contains("утром") -> LocalTime.of(9, 0)
            lower.contains("днем") || lower.contains("днём") -> LocalTime.of(14, 0)
            lower.contains("вечером") -> LocalTime.of(19, 0)
            lower.contains("ночью") -> LocalTime.of(22, 0)
            else -> null
        }

        if (date == null && time == null) return null
        return LocalDateTime.of(date ?: today, time ?: LocalTime.of(14, 0))
    }

    private fun inferCategory(lower: String, cleaned: String): String {
        val haystack = "$lower $cleaned"
        return CATEGORY_MARKERS.firstOrNull { (_, markers) ->
            markers.any { containsStem(haystack, it) }
        }?.first ?: "General"
    }

    private fun containsAnyWord(text: String, words: List<String>): Boolean =
        words.any { containsWord(text, it) }

    private fun containsAnyStem(text: String, stems: List<String>): Boolean =
        stems.any { containsStem(text, it) }

    private fun containsAnyPhrase(text: String, phrases: List<String>): Boolean =
        phrases.any { text.contains(it) }

    /** Сравнение целого слова: нужно для маркеров времени, чтобы «готов» не ловил «готовить». */
    private fun containsWord(text: String, word: String): Boolean {
        val needle = word.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) return false
        if (needle.contains(" ")) return text.contains(needle)

        var index = text.indexOf(needle)
        while (index >= 0) {
            val end = index + needle.length
            val beforeOk = index == 0 || !text[index - 1].isLetter()
            val afterOk = end >= text.length || !text[end].isLetter()
            if (beforeOk && afterOk) return true
            index = text.indexOf(needle, index + 1)
        }
        return false
    }

    /**
     * Совпадение по началу слова, а не по целому: русские окончания ломают точное
     * сравнение («витамины» против словаря «витамин», «работы» против «работ»).
     */
    private fun containsStem(text: String, stem: String): Boolean {
        val needle = stem.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) return false

        var index = text.indexOf(needle)
        while (index >= 0) {
            val beforeOk = index == 0 || !text[index - 1].isLetter()
            if (beforeOk) return true
            index = text.indexOf(needle, index + 1)
        }
        return false
    }

    /**
     * Человеческая подпись срока.
     *
     * Время 14:00 показывается, даже если в фразе его не было: это время по умолчанию
     * во всём приложении (редактор задачи ставит то же самое), и именно оно будет
     * сохранено. Скрывать его в шторке значило бы показать «до завтра», а потом
     * обнаружить в списке дел «14:00 · 28 SEP» — расхождение заметил бы уже пользователь,
     * а не шторка, где у него ещё можно снять галочку.
     */
    private fun formatDueLabel(iso: String): String {
        return try {
            val parsed = LocalDateTime.parse(iso.take(19))
            val time = " ${"%02d:%02d".format(parsed.hour, parsed.minute)}"
            when (parsed.toLocalDate()) {
                LocalDate.now() -> "сегодня$time"
                LocalDate.now().plusDays(1) -> "завтра$time"
                else -> parsed.toLocalDate().toString() + time
            }
        } catch (e: Exception) {
            iso
        }
    }
}
