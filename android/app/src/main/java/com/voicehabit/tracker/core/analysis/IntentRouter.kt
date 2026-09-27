package com.voicehabit.tracker.core.analysis

import com.voicehabit.tracker.core.text.RussianPlural
import com.voicehabit.tracker.domain.model.IntentMode
import java.util.Locale

/**
 * H1. Вердикт детектора намерения.
 *
 * [reason] показывается пользователю в шторке разбора — чтобы он не гадал, почему
 * приложение решило, что он «просто говорил». [signals] — те маркеры, которые сработали.
 */
data class ModeVerdict(
    val mode: IntentMode,
    val confidence: Float,
    val reason: String,
    val signals: List<String> = emptyList()
)

/**
 * H1. Детектор режима: это задача, вопрос — или человек просто говорит?
 *
 * ## Зачем он нужен
 *
 * До H1 разбор начинался с попытки вытащить из фразы задачу. Любой поток мыслей без
 * «надо/напомни/завтра» падал в заметку, которая даже не сохранялась, и пользователь
 * получал пустую шторку. Теперь режим определяется **первым**, и режим [IntentMode.DICTATE]
 * — полноценный сценарий, а не ошибка разбора.
 *
 * ## Почему без сети
 *
 * Детектор — чистая функция от строки, без моделей и без ключей. Иначе фича, ради которой
 * пользователь и говорит «просто запиши», ломалась бы в самом важном месте: в метро,
 * без интернета, и падала бы ровно на длинном потоке мыслей, который сложнее всего
 * разобрать коротким регулярным выражением. Слой сети может уточнить вердикт
 * ([com.voicehabit.tracker.data.remote.DirectAiService]), но никогда не является условием
 * его работы.
 *
 * ## Приоритет ошибки
 *
 * Ключевое решение: при неопределённости побеждает [IntentMode.DICTATE], а не
 * [IntentMode.LOG]. Выдумать задачу из обрывочной фразы — испортить список дел и
 * стрики; не заметить задачу внутри монолога — всего лишь показать конспект, где
 * действие перечислено в разделе «что дальше». Ошибка первого рода необратима,
 * второго — в один тап.
 */
object IntentRouter {

    // ── Решения и команды приложения: проверяются раньше всего остального ──────────

    private val SUMMARY_MARKERS = listOf(
        "что у меня сегодня", "что у меня запланировано", "мои дела", "что осталось",
        "что нужно сделать", "расскажи мои дела", "подведи итог", "что сегодня",
        "что у меня на завтра", "какие у меня задачи", "что я планировал"
    )

    private val FOCUS_MARKERS = listOf(
        "начни фокус", "включи фокус", "фокус", "помодоро", "сфокусируйся", "сосредоточься",
        "таймер на", "запусти таймер"
    )

    private val DELETE_MARKERS = listOf(
        "удали", "удалить", "убери", "убрать", "вычеркни", "вычеркнуть", "сотри", "стереть"
    )

    private val RESCHEDULE_MARKERS = listOf(
        "перенеси", "перенести", "отложи", "отложить", "перемести", "сдвинь", "сдвинуть"
    )

    // ── Жёсткие маркеры задачи: одно такое слово почти всегда означает действие ───────

    private val PLAN_TRIGGERS = listOf(
        "надо", "нужно", "не забудь", "не забыть", "напомни", "напомнить", "запланируй",
        "добавь", "поставь", "хочу", "должен", "должна", "планирую", "планируешь",
        "собираюсь", "задача", "задачу", "дедлайн", "назначь", "перенеси на", "поставь задачу"
    )

    private val PAST_MARKERS = listOf(
        "сделал", "сделала", "сделали", "выполнил", "выполнила", "выполнили", "закончил",
        "закончила", "сдал", "сдала", "сдали", "отправил", "отправила", "опубликовал",
        "опубликовала", "выложил", "выпил", "выпила", "принял", "приняла", "потренировался",
        "потренировалась", "пробежал", "пробежала", "заправил", "заправила", "застелил",
        "застелила", "поел", "поела", "позавтракал", "позавтракала", "погулял", "погуляла",
        "прочитал", "прочитала", "доделал", "доделала", "написал", "написала", "получил",
        "получила", "сходил", "сходила", "позвонил", "позвонила", "купил", "купила",
        "проверил", "проверила", "готов", "готово", "выполнено", "отмечено", "отметил",
        "отметила", "подписал", "подписала", "смог", "успела", "успел"
    )

    private val ALL_DONE_MARKERS = listOf(
        "всё сделал", "все сделал", "сделал всё", "сделала всё", "сделали всё", "всё выполнил",
        "все выполнил", "выполнил всё", "выполнила всё", "всё готово", "все готово",
        "все привычки сделал", "всё закончил", "все закончил", "закрыл всё", "всё закрыл"
    )

    private val TIME_MARKERS = listOf(
        "сегодня", "завтра", "послезавтра", "в понедельник", "во вторник", "в среду",
        "в четверг", "в пятницу", "в субботу", "в воскресенье", "через", "до пятницы",
        "до конца", "на следующей неделе"
    )

    // ── Мягкие маркеры задачи: глагол действия, но без воли пользователя ─────────────

    private val ACTION_VERBS = listOf(
        "позвонить", "написать", "купить", "сделать", "оплатить", "заплатить", "прочитать",
        "выучить", "изучить", "подготовить", "записаться", "сходить", "принять", "начать",
        "закончить", "отправить", "забрать", "получить", "проверить", "сдать", "закрыть",
        "открыть", "запустить", "погулять", "покормить", "забронировать", "заказать",
        "зарегистрироваться", "переписать", "дописать", "починить", "настроить", "перенести"
    )

    private val INFINITIVE_ENDINGS = listOf("ить", "ать", "еть", "уть", "ыть", "ять")

    // ── Маркеры свободного потока: человек рассуждает, а не ставит задачу ────────────

    private val DICTATE_MARKERS = listOf(
        "короче", "то есть", "в общем", "если честно", "в принципе", "кстати", "знаешь",
        "смотри", "представь", "я думаю", "мне кажется", "по-моему", "по моему", "наверное",
        "возможно", "идея", "мысль", "задумался", "задумалась", "размышлял", "размышляла",
        "хотел сказать", "я тут", "выслушай", "расскажу", "итак", "вкратце", "по сути",
        "грубо говоря", "с другой стороны", "получается", "вроде", "типа", "и вот",
        "надиктов", "записываю", "хочу записать", "мы обсуждали", "мы обсудили", "мы говорили",
        "как я уже говорил", "обдумывал", "обдумываю", "продумал", "размышлял про",
        "просто говорю", "просто хотел", "сейчас расскажу", "в двух словах", "если коротко",
        "слушай", "ну и", "и дальше", "а дальше", "проговорил", "проговорила", "думаю вслух"
    )

    private val NARRATIVE_MARKERS = listOf(
        "он сказал", "она сказала", "они сказали", "с ним", "с ней", "с ними", "мы решили",
        "мы договорились", "потом", "потом ещё", "и дальше", "ну и дальше", "в тот раз",
        "в этот раз", "на прошлой неделе", "вчера обсуждали", "мы сидели", "в комнате"
    )

    // ── Вопрос ──────────────────────────────────────────────────────────────────────

    private val QUESTION_WORDS = listOf(
        "что", "как", "почему", "зачем", "когда", "сколько", "где", "кто", "какой",
        "какая", "какие", "чей", "чья", "чье", "разве", "правильно ли", "зачем же"
    )

    /** Порог длины, после которого поток считается разговором, даже без маркеров. */
    private const val RAMBLE_WORDS = 12
    private const val RAMBLE_WORDS_STRONG = 28
    private const val RAMBLE_WORDS_VERY_STRONG = 48

    /**
     * Определяет режим одной фразой.
     *
     * Порог `MIXED` намеренно высокий: «мне кажется, надо бы купить молоко» — это
     * всё-таки задача с вводным словом, а не поток мыслей. В поток попадает только то,
     * где рассуждения действительно много, а действие — одно среди многих.
     */
    fun route(transcript: String): ModeVerdict {
        val lower = transcript.lowercase(Locale.getDefault()).replace('ё', 'е').trim()
        if (lower.isBlank()) {
            return ModeVerdict(
                mode = IntentMode.DICTATE,
                confidence = 0f,
                reason = "Не расслышал ни слова — сохраняю пустой конспект, список дел не трогаю"
            )
        }

        val words = lower.split(WHITESPACE).count { it.isNotBlank() }

        // 1. Вопрос к приложению разбирается раньше задач: «что у меня сегодня» не план.
        if (SUMMARY_MARKERS.any { lower.contains(it) }) {
            return ModeVerdict(
                mode = IntentMode.QUERY,
                confidence = 0.95f,
                reason = "Просишь сводку — это вопрос, а не новая запись",
                signals = SUMMARY_MARKERS.filter { lower.contains(it) }
            )
        }

        val hardSignals = collect(lower, PLAN_TRIGGERS + PAST_MARKERS + ALL_DONE_MARKERS + TIME_MARKERS)
        val commandSignal = listOf(FOCUS_MARKERS, DELETE_MARKERS, RESCHEDULE_MARKERS)
            .firstOrNull { group -> group.any { lower.contains(it) } }
            ?.first { lower.contains(it) }
        if (commandSignal != null) {
            hardSignals.add("команда: $commandSignal")
        }

        val softSignals = collect(lower, ACTION_VERBS).toMutableList()
        if (startsWithInfinitive(lower)) softSignals.add("глагол действия в начале")

        val dictateSignals = collect(lower, DICTATE_MARKERS + NARRATIVE_MARKERS)
        val questionSignals = collect(lower, QUESTION_WORDS)
        val hasQuestionMark = lower.contains('?')
        if (hasQuestionMark) questionSignals.add("знак вопроса")

        // 2. Считаем веса. Порог в 2.0 для потока — примерно два разговорных маркера
        //    плюс запас; одно вводное слово до порога не добирает.
        val hardScore = if (hardSignals.isEmpty()) 0.0 else 3.0 + 0.4 * hardSignals.size.coerceAtMost(5)
        val softScore = if (softSignals.isEmpty()) 0.0 else 1.0 + 0.25 * softSignals.size.coerceAtMost(4)

        var dictateScore = if (dictateSignals.isEmpty()) 0.0 else 1.2 + 0.45 * dictateSignals.size.coerceAtMost(5)
        if (words >= RAMBLE_WORDS) dictateScore += 0.6
        if (words >= RAMBLE_WORDS_STRONG) dictateScore += 0.6
        if (words >= RAMBLE_WORDS_VERY_STRONG) dictateScore += 0.8
        // Ни одного признака действия, но речь заметная: почти наверняка это размышление.
        if (hardSignals.isEmpty() && softSignals.isEmpty() && words >= 6) dictateScore += 0.9

        val queryScore = if (questionSignals.isEmpty()) 0.0 else 1.4 + 0.4 * questionSignals.size.coerceAtMost(3)

        val taskScore = hardScore + softScore
        val mode = decide(taskScore, hardSignals.isNotEmpty(), softSignals.isEmpty(), dictateScore, queryScore, words)

        val scores = listOf(taskScore, dictateScore, queryScore)
        val top = scores.max()
        // Без единого сигнала уверенность низкая и по построению: дальше сравнивать не с чем.
        // Раньше здесь стоял фиктивный «0.05» вместо нуля, и фраза вроде «привет»
        // получала уверенность 0.98 — то есть выглядела как разобранная задача.
        val confidence = if (top <= 0.01) {
            0.35f
        } else {
            val runnerUp = scores.filter { it < top - 1e-9 }.maxOrNull() ?: 0.0
            (0.45f + 0.53f * ((top - runnerUp) / top).toFloat()).coerceIn(0.3f, 0.98f)
        }

        return ModeVerdict(
            mode = mode,
            confidence = confidence,
            reason = explain(mode, hardSignals, softSignals, dictateSignals, questionSignals, words),
            signals = (hardSignals + softSignals + dictateSignals + questionSignals).distinct()
        )
    }

    private fun decide(
        taskScore: Double,
        hasHard: Boolean,
        noSoft: Boolean,
        dictateScore: Double,
        queryScore: Double,
        words: Int
    ): IntentMode = when {
        hasHard && dictateScore >= 2.0 -> IntentMode.MIXED
        hasHard -> IntentMode.LOG
        !noSoft && dictateScore >= 2.4 -> IntentMode.MIXED
        !noSoft -> IntentMode.LOG
        queryScore >= 1.4 && queryScore >= dictateScore -> IntentMode.QUERY
        dictateScore > 0.0 -> IntentMode.DICTATE
        queryScore > 0.0 -> IntentMode.QUERY
        // Ничего не найдено: лучше конспект, чем выдуманная задача в списке дел.
        else -> if (words >= 3) IntentMode.DICTATE else IntentMode.LOG
    }

    private fun explain(
        mode: IntentMode,
        hard: List<String>,
        soft: List<String>,
        dictate: List<String>,
        question: List<String>,
        words: Int
    ): String {
        val sample = (hard + soft + dictate + question).take(3).joinToString(", ")
        // «74 слов» — ошибка в самой объяснялке, которую читает пользователь.
        val lengthNote = if (words >= RAMBLE_WORDS) {
            " · длинный поток (${RussianPlural.count(words, "слово", "слова", "слов")})"
        } else {
            ""
        }
        return when (mode) {
            IntentMode.LOG ->
                "Нашёл конкретное действие: $sample$lengthNote"
            IntentMode.MIXED ->
                "Действие есть, но оно внутри рассуждения: $sample$lengthNote"
            IntentMode.DICTATE ->
                if (hard.isEmpty() && soft.isEmpty()) {
                    "Не нашёл ни одного действия — это просто разговор$lengthNote"
                } else {
                    "Похоже на размышление, а не на план: $sample$lengthNote"
                }
            IntentMode.QUERY ->
                "Похоже на вопрос, а не на запись: $sample"
        }
    }

    /** Русский инфинитив в начале фразы: «позвонить», «выучить», «записаться». */
    private fun startsWithInfinitive(lower: String): Boolean {
        val firstWord = lower.substringBefore(' ').trim().trim(',', '.', '!', '?')
        if (firstWord.length < 4) return false
        return INFINITIVE_ENDINGS.any { firstWord.endsWith(it) }
    }

    private fun collect(lower: String, markers: List<String>): MutableList<String> =
        markers.filterTo(mutableListOf()) { marker ->
            if (marker.contains(' ')) lower.contains(marker) else containsWord(lower, marker)
        }

    /** Сравнение целого слова: «готов» не должен ловить «готовить». */
    private fun containsWord(text: String, word: String): Boolean {
        val needle = word.trim()
        if (needle.isEmpty()) return false
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

    private val WHITESPACE = Regex("\\s+")
}
