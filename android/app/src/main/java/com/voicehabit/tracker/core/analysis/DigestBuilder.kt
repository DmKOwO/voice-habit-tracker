package com.voicehabit.tracker.core.analysis

import com.voicehabit.tracker.domain.model.CaptureDigest
import java.util.Locale

/**
 * H1. Сборка конспекта свободного потока.
 *
 * ## Зачем
 *
 * «Хочу просто диктофон, который сделает выжимку разговора» — это не заметка и не
 * пересказ. Это структура: суть, ключевые мысли, что решили, что осталось открытым,
 * что делать дальше. Собирается локально, без сети, потому что длинный поток мыслей —
 * худший случай для любого сетевого таймаута.
 *
 * ## Честность выжимки
 *
 * Каждая секция заполняется только если в речи она реально есть. Пустой «открытые
 * вопросы» честнее выдуманного пункта: выдуманный вопрос в конспекте читается как
 * реальный и потом всплывает через неделю в виде «а что это было?».
 */
object DigestBuilder {

    private val WHITESPACE = Regex("\\s+")
    private val SENTENCE_SPLIT = Regex("[.!?;…]+|\\p{Punct}")

    /** Точки, по которым режется поток без знаков препинания — обычная речь надиктовки. */
    private val DISCOURSE_CUTS = listOf(
        "как я уже говорил", "и вот что важно", "и вот что я понял", "если коротко",
        "с другой стороны", "грубо говоря", "то есть", "в общем", "по сути", "вкратце",
        "если честно", "в принципе", "и вот", "короче", "значит", "кстати", "в итоге",
        "получается", "итак", "слушай", "мне кажется", "по-моему", "по моему", "потому что",
        "и дальше", "а дальше", "потом"
    ).sortedByDescending { it.length }

    /** Слова-обвязки в начале фразы: «короче», «то есть» — в тезис не идут. */
    private val LEAD_FILLERS = listOf(
        "как я уже говорил", "и вот что важно", "и вот что я понял", "если коротко",
        "грубо говоря", "с другой стороны", "если честно", "в общем", "по сути", "вкратце",
        "в принципе", "и вот что", "и вот", "то есть", "короче", "итак", "слушай",
        "знаешь", "смотри", "мне кажется", "по-моему", "по моему", "значит так", "кстати",
        "короче говоря", "эм", "э-э", "ээ", "ну", "вот", "в общем-то", "типа"
    )

    /** Обвязки, которые вычищаются в любом месте фразы. Только однозначные: «ну» как слово. */
    private val MID_FILLER = Regex("(?<![\\p{L}])(ну|типа|как бы)(?![\\p{L}])")

    private val DECISION_MARKERS = listOf(
        // Слово «решение» как маркер убрано намеренно: «я не уверен, что это правильное
        // решение» — это сомнение, а не решение, и в конспекте оно попадало в «Решения».
        "решили", "решил", "решила", "решено", "договорились", "договорился",
        "выбрали", "выбрал", "выбрала", "остановились на", "остановились", "берем", "берём",
        "решили так", "решили что", "решил что", "решила что",
        "идем с", "идём с", "в итоге", "утвердили", "утверждено", "согласовали",
        "согласовано", "подтвердили", "договорились что", "решено что", "вывод такой",
        "будем делать", "остаемся на", "остаёмся на", "фиксируем", "зафиксировали"
    )

    private val KEY_MARKERS = listOf(
        "главное", "важно", "важное", "суть", "идея", "проблема", "вывод", "риск", "минус",
        "плюс", "причина", "оказалось", "выяснилось", "получается", "по сути", "вкратце",
        "итак", "тезис", "факт", "проблемы", "идеи", "выводы", "итог"
    )

    private val NEXT_STEP_MARKERS = listOf(
        "надо", "нужно", "стоит", "давай", "давайте", "попробуем", "попробовать", "попробую",
        "попробуй", "следующий шаг", "в следующий раз", "на следующей неделе", "завтра",
        "не забыть", "напомнить", "возьму на себя", "возьмем на себя", "возьмём на себя",
        "займусь", "начну с", "нужно бы", "надо бы", "стоит попробовать", "план такой",
        "сначала", "потом надо", "в первую очередь"
    )

    private val LEADING_CONNECTIVES = setOf(
        "а", "и", "но", "ну", "вот", "так", "то", "же", "итак", "короче", "слушай", "ладно"
    )

    private val QUESTION_STARTS = listOf(
        "что", "как", "почему", "зачем", "когда", "сколько", "где", "кто", "какой",
        "какая", "какие", "чей", "чья", "разве", "а что", "а как", "а почему"
    )

    private val NAMES_BEFORE_VERB = Regex(
        "\\b([А-ЯЁ][а-яё]{2,})\\s+(сказал|сказала|говор[ия]л|спросил|спросила|предложил|" +
            "предложила|написал|написала|ответил|ответила|позвонил|позвонила|думает|думают|" +
            "считает|считают|взял|взяла|убедил|убедила|предупредил|предупредила)\\b"
    )

    private val NAMES_AFTER_PREPOSITION = Regex(
        "\\b(?:с|от|у|для|к)\\s+([А-ЯЁ][а-яё]{2,})\\b"
    )

    private val SURNAME_OR_ROLE = Regex(
        "\\b([А-ЯЁ][а-яё]{2,})\\s+(заказчик|клиент|заказчику|клиенту|дизайнер|дизайнера|" +
            "менеджер|менеджера|начальник|начальника|тестировщик|тестировщика|разработчик|" +
            "разработчика|аналитик|аналитика|юрист|юриста|бухгалтер|бухгалтера)\\b"
    )

    private val TONE_RULES: List<Pair<List<String>, String>> = listOf(
        listOf("устал", "устала", "выгорел", "выгорела", "вымотался", "вымоталась", "нет сил", "сил нет") to "Усталость",
        listOf("рад", "рада", "отлично", "круто", "нравится", "получилось", "удалось", "удалось наконец", "супер", "офиген", "нереально", "вау") to "Подъём",
        listOf("зол", "зла", "бесит", "раздражает", "выводит из себя", "надоел", "надоела", "хрень", "ерунда") to "Раздражение",
        listOf("тревож", "тревожно", "стресс", "переживаю", "боюсь", "страшно", "опасно", "неопределенность", "неопределённость") to "Тревога",
        listOf("спокойно", "уверен", "уверенно", "разобрался", "разобралась", "понятно", "всё под контролем", "ровно") to "Спокойствие",
        listOf("лень", "лень", "неохота", "забил", "забила", "наплевать", "забросил", "забросила") to "Истощение"
    )

    private val NUMBER_PATTERN = Regex(
        "[\\-−+]?\\d[\\d\\s]*(?:[.,]\\d+)?\\s*(?:тыс\\.?|тысяч[а-я]*|млн\\.?|миллион[а-я]*|" +
            "миллиард[а-я]*|руб\\.?|рубл[а-я]*|коп\\.?|%(?![а-я])|процент[а-я]*|час[а-я]*|" +
            "минут[а-я]*|секунд[а-я]*|день|дня|дней|недел[а-я]*|месяц[а-я]*|год[а-я]*|" +
            "раз[а-я]*|километр[а-я]*|км\\.?|килограмм[а-я]*|кг\\.?|грамм[а-я]*|литр[а-я]*|" +
            "мл\\.?|градус[а-я]*|°[сc]?|тыср\\.?)",
        RegexOption.IGNORE_CASE
    )

    private const val MAX_ITEMS = 5

    /**
     * Собирает конспект из транскрипта.
     *
     * @param speechSeconds длительность речи — попадает в конспект для контекста
     *   («3:20 разговора → 4 тезиса»). Не влияет на разбор.
     */
    fun build(transcript: String, speechSeconds: Int = 0): CaptureDigest {
        val raw = transcript.replace(WHITESPACE, " ").trim()
        if (raw.isBlank()) {
            return CaptureDigest(speechSeconds = speechSeconds.coerceAtLeast(0))
        }

        val sentences = sentences(raw)
        val cleaned = sentences.map { stripFillers(it) }.filter { it.length >= 3 }
        if (cleaned.isEmpty()) {
            return CaptureDigest(
                title = titleFrom(raw, emptyList(), emptyList(), emptyList()),
                gist = raw.take(280),
                wordCount = words(raw),
                speechSeconds = speechSeconds.coerceAtLeast(0)
            )
        }

        val decisions = mutableListOf<String>()
        val openQuestions = mutableListOf<String>()
        val nextSteps = mutableListOf<String>()
        val keyPoints = mutableListOf<String>()

        for (sentence in cleaned) {
            when {
                isQuestion(sentence) -> openQuestions.add(sentence.trimEnd('?', '.', ' ').trim())
                hasMarker(sentence, DECISION_MARKERS) -> decisions.add(sentence)
                hasMarker(sentence, NEXT_STEP_MARKERS) -> nextSteps.add(sentence)
                hasMarker(sentence, KEY_MARKERS) -> keyPoints.add(sentence)
            }
        }

        // Темы без явного маркера тоже важны: если речь длинная, верхние по длине
        // предложения — это и есть содержание. Иначе конспект может остаться пустым
        // при богатом потоке мыслей без слов «главное» и «суть».
        if (cleaned.size >= 3) {
            for (sentence in cleaned.sortedByDescending { it.length }) {
                if (keyPoints.size >= MAX_ITEMS) break
                if (sentence in keyPoints || sentence in decisions || sentence in nextSteps) continue
                if (sentence in openQuestions) continue
                if (sentence.length < 18) continue
                // «Я тут надиктовываю мысли после разговора» — длинная фраза, но не тезис.
                // Без этой проверки она становилась первым пунктом «ключевых мыслей»,
                // а значит и первой строкой выжимки.
                if (isMetaOpener(sentence)) continue
                keyPoints.add(sentence)
            }
        }

        val gist = buildGist(cleaned, keyPoints, decisions)

        return CaptureDigest(
            title = titleFrom(raw, cleaned, keyPoints, decisions),
            gist = gist,
            keyPoints = keyPoints.distinct().take(MAX_ITEMS),
            decisions = decisions.distinct().take(MAX_ITEMS),
            openQuestions = openQuestions.distinct().take(MAX_ITEMS),
            nextSteps = nextSteps.distinct().take(MAX_ITEMS),
            people = peopleFrom(raw),
            numbers = numbersFrom(raw),
            tone = toneOf(raw),
            wordCount = words(raw),
            speechSeconds = speechSeconds.coerceAtLeast(0)
        )
    }

    // ── Разбивка на предложения ────────────────────────────────────────────────────

    private fun sentences(text: String): List<String> {
        val byPunctuation = text.split(SENTENCE_SPLIT)
            .map { it.trim() }
            .filter { it.length >= 3 }
        if (byPunctuation.size >= 3) return byPunctuation

        val byDiscourse = splitOnDiscourse(text)
        return (byPunctuation + byDiscourse)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Речь надиктовки почти не имеет знаков препинания, поэтому длинный поток режется
     * по словам-связкам. Порог длины в 12 символов не даёт развалить фразу вроде
     * «и дальше» посреди предложения на бесполезные обрывки.
     */
    private fun splitOnDiscourse(text: String): List<String> {
        if (text.length < 40) return emptyList()
        val cuts = mutableListOf(0)
        for (marker in DISCOURSE_CUTS) {
            var index = text.indexOf(marker, ignoreCase = true)
            while (index > 0) {
                cuts.add(index)
                index = text.indexOf(marker, startIndex = index + 1, ignoreCase = true)
            }
        }
        cuts.add(text.length)
        return cuts.distinct().sorted()
            .zipWithNext { from, to -> text.substring(from, to) }
            .map { it.trim().trim(' ', ',', ';', ':', '–', '—') }
            .filter { it.length >= 12 }
    }

    // ── Чистка обвязок ─────────────────────────────────────────────────────────────

    private fun stripFillers(sentence: String): String {
        var result = sentence.trim()
        var changed = true
        var guard = 0
        while (changed && guard < 8) {
            changed = false
            guard++
            for (filler in LEAD_FILLERS) {
                if (result.length > filler.length && result.startsWith("$filler ")) {
                    result = result.removePrefix(filler)
                        .trimStart(' ', ',', ';', ':', '—', '–', '.')
                    changed = true
                } else if (result.equals(filler, ignoreCase = true)) {
                    return ""
                }
            }
        }
        return result.replace(MID_FILLER, " ")
            .replace(Regex("[\\s,;:.!]+$"), "")
            .replace(WHITESPACE, " ")
            .trim()
    }

    // ── Классификация фраз ─────────────────────────────────────────────────────────

    private fun isQuestion(sentence: String): Boolean {
        if (sentence.endsWith("?")) return true
        // Связка перед вопросом («а как быть с обучением?», «и что дальше?») не должна
        // прятать его от секции: без снятия «а» фраза уходила в тезисы, а вопрос,
        // который человек задал вслух, оставался незамеченным.
        var body = sentence.trimStart(' ', '—', '–', '-')
        var guard = 0
        while (guard < 3) {
            val firstWord = body.substringBefore(' ').lowercase(Locale.getDefault())
            if (firstWord !in LEADING_CONNECTIVES) break
            body = body.substringAfter(' ', "").trimStart(' ', ',', '—', '–', '-')
            guard++
        }
        if (body.isBlank()) return false
        val firstWord = body.substringBefore(' ').lowercase(Locale.getDefault())
        if (QUESTION_STARTS.none { firstWord == it || firstWord.startsWith("$it ") }) return false
        // «когда будем делать — не знаю, надо подумать» это всё-таки не вопрос к собеседнику.
        return !hasMarker(sentence, NEXT_STEP_MARKERS)
    }

    private fun hasMarker(sentence: String, markers: List<String>): Boolean =
        markers.any { marker ->
            if (marker.contains(' ')) sentence.contains(marker)
            else containsWord(sentence.lowercase(Locale.getDefault()), marker)
        }

    private fun containsWord(text: String, word: String): Boolean {
        if (word.isEmpty()) return false
        var index = text.indexOf(word)
        while (index >= 0) {
            val end = index + word.length
            val beforeOk = index == 0 || !text[index - 1].isLetter()
            val afterOk = end >= text.length || !text[end].isLetter()
            if (beforeOk && afterOk) return true
            index = text.indexOf(word, index + 1)
        }
        return false
    }

    // ── Сборка секций ──────────────────────────────────────────────────────────────

    private fun buildGist(
        cleaned: List<String>,
        keyPoints: List<String>,
        decisions: List<String>
    ): String {
        // Решение идёт первым: «мы решили перенести релиз на пятницу» — это и есть суть
        // разговора, а первая по длине фраза потока часто вообще ни о чём.
        val content = cleaned.filterNot { isMetaOpener(it) }
        val priority = (decisions.take(1) + keyPoints.take(1) + content.take(2))
            .distinct()
        val gist = priority.joinToString(" ").trim()
        val clipped = if (gist.length <= 320) gist else gist.take(317).trimEnd() + "…"
        return clipped
    }

    /**
     * Заголовок конспекта.
     *
     * Три ступени отбора, от лучшей к худшей, потому что первые фразы потока мыслей
     * почти никогда не являются его темой:
     *  1. Решение («решили перенести релиз на пятницу») — это и есть тема разговора.
     *  2. Тезис — следующий по полезности кандидат.
     *  3. Самая длинная фраза, не начинающаяся с «я тут / слушай / короче».
     *
     * Отбраковка первой фразы нужна прямо: «короче я тут надиктовываю мысли после
     * разговора» — это описание самого акта речи, и такой заголовок бесполезен ровно
     * настолько же, насколько бесполезна папка «Разное».
     */
    private fun titleFrom(
        raw: String,
        cleaned: List<String>,
        keyPoints: List<String>,
        decisions: List<String>
    ): String {
        val source = decisions.firstOrNull { it.length >= 18 }
            ?: keyPoints.firstOrNull { it.length >= 18 }
            ?: cleaned.firstOrNull { it.length >= 20 && !isMetaOpener(it) }
            ?: cleaned.firstOrNull { it.length >= 15 && !isMetaOpener(it) }
            ?: cleaned.firstOrNull { !isMetaOpener(it) }
            ?: cleaned.maxByOrNull { it.length }
            ?: raw

        val withoutLabel = stripTopicLabel(source)
        val withoutTail = withoutLabel.trim().trimEnd('.', '!', '?', ' ', ',', ';')
        val words = withoutTail.split(' ').filter { it.isNotBlank() }.take(6)
        val candidate = words.joinToString(" ").replaceFirstChar { it.uppercase() }
        return when {
            candidate.isBlank() -> "Диктовка"
            candidate.length <= 52 -> candidate
            else -> candidate.take(49).trimEnd().trimEnd(',', '.', '—', '–') + "…"
        }
    }

    /** «И вот что важно — …» → «…»: служебная подпись тезиса в заголовок не годится. */
    private fun stripTopicLabel(sentence: String): String {
        var result = sentence.trim()
        var changed = true
        var guard = 0
        while (changed && guard < 4) {
            changed = false
            guard++
            for (label in TOPIC_LABELS) {
                if (result.length > label.length &&
                    result.startsWith("$label ") &&
                    result.removePrefix(label).isNotBlank()
                ) {
                    result = result.removePrefix(label).trimStart(' ', '—', '–', '-', ':', ',')
                    changed = true
                }
            }
        }
        // Дефис внутри класса символов обязан быть экранирован: «—–-:» без экрана
        // компилируется как диапазон от тире до двоеточия и роняет разбор на любой фразе.
        return result.replace(Regex("^[—–\\-:,.\\s]+"), "")
    }

    private val TOPIC_LABELS = listOf(
        "и вот что важно", "и вот что я понял", "важно", "важное", "суть в том что", "суть",
        "идея в том что", "идея", "вывод", "выводы", "главное", "главная мысль",
        "проблема", "проблемы", "риск", "причина", "тезис", "тезисы", "итог", "в итоге",
        "в общем", "по сути", "значит", "получается", "итак", "и", "а", "но"
    )

    /** Фразы, которые говорят о разговоре, а не о его предмете. */
    private fun isMetaOpener(sentence: String): Boolean {
        val lower = sentence.lowercase(Locale.getDefault())
        val first = lower.substringBefore(' ').trim(',', '.', '!', '?')
        return META_OPENERS.any { first == it || lower.startsWith("$it ") }
    }

    private val META_OPENERS = listOf(
        "я", "слушай", "знаешь", "смотри", "хочу", "надиктовываю", "записываю", "проговорил",
        "проговорила", "расскажу", "выслушай", "тут", "да", "нет", "ок", "ладно", "в общем",
        "короче", "то есть", "итак", "короче говоря", "в двух словах", "если коротко"
    )

    private fun peopleFrom(raw: String): List<String> {
        val found = linkedMapOf<String, String>()
        fun add(name: String) {
            val clean = name.trim(' ', ',', '.', '!', '?', ';', ':', '—', '–')
            if (clean.length < 3) return
            // Один и тот же человек встречается в разных падежах: «с Мариной… Марина
            // считает». По регистру это разные строки, по человеку — один, и список
            // вида «Марина, Мариной» выглядит как две разные люди.
            val key = clean.lowercase(Locale.getDefault()).take(4)
            if (!found.containsKey(key)) found[key] = clean
        }
        NAMES_BEFORE_VERB.findAll(raw).forEach { add(it.groupValues[1]) }
        SURNAME_OR_ROLE.findAll(raw).forEach { add(it.groupValues[1]) }
        NAMES_AFTER_PREPOSITION.findAll(raw).forEach { add(it.groupValues[1]) }

        // Заглавные слова не в начале фразы — тоже имена: «встретился с Олегом»,
        // «передал Марине». Первое слово предложения пропускаем: там заглавная буква
        // просто от начала предложения, а не имя.
        var wordStart = 0
        val tokens = raw.split(' ')
        tokens.forEachIndexed { index, rawToken ->
            val token = rawToken.trim(' ', ',', '.', '!', '?', ';', ':', '—', '–')
            val isSentenceStart = index == 0 || isSentenceBoundary(raw, wordStart)
            if (token.isNotEmpty() && !isSentenceStart && looksLikeName(token)) {
                add(token)
            }
            wordStart += rawToken.length + 1
        }
        return found.values.take(6)
    }

    private fun isSentenceBoundary(text: String, wordStart: Int): Boolean {
        var i = wordStart
        while (i > 0 && text[i - 1] == ' ') i--
        return i == 0 || text[i - 1] in ".!?…"
    }

    private fun looksLikeName(token: String): Boolean {
        if (token.length < 3) return false
        if (!token[0].isUpperCase() && !token[0].isTitleCase()) return false
        if (token.all { !it.isLetter() }) return false
        // Местоимения и служебные слова с заглавной — не люди.
        return token.lowercase(Locale.getDefault()) !in NOT_NAMES
    }

    private val NOT_NAMES = setOf(
        "и", "а", "но", "же", "ну", "вот", "так", "уже", "ещё", "еще", "бы", "было",
        "это", "этот", "эта", "эти", "всё", "все", "всем", "который", "которая", "которые",
        "если", "что", "как", "когда", "где", "потому", "затем", "иначе", "итак", "кстати",
        "значит", "однако", "при этом", "с другой стороны", "сегодня", "завтра", "вчера",
        "да", "нет", "ок", "окей", "слушай", "смотри", "знаешь"
    )

    private fun numbersFrom(raw: String): List<String> =
        NUMBER_PATTERN.findAll(raw)
            .map { it.value.replace(WHITESPACE, " ").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
            .toList()

    private fun toneOf(raw: String): String {
        val lower = raw.lowercase(Locale.getDefault())
        for ((markers, tone) in TONE_RULES) {
            if (markers.any { lower.contains(it) }) return tone
        }
        return ""
    }

    private fun words(text: String): Int = text.split(' ').count { it.isNotBlank() }
}
