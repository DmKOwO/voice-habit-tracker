package com.voicehabit.tracker.domain.model

import androidx.compose.runtime.Immutable

/**
 * H1. Режим, который приложение определило само, без ручного выбора пользователем.
 *
 * Раньше разбор фразы сразу пытался найти в ней задачу или отметку привычки, и любой
 * монолог без «надо/напомни/завтра» молча превращался в бесполезную заметку, которая
 * нигде не сохранялась. Теперь режим — это первое, что вычисляется: сначала *зачем*
 * человек говорил, и только потом — какие сущности из этого получаются.
 */
enum class IntentMode(
    val label: String,
    val emoji: String,
    val hint: String
) {
    /** «Напомни купить молоко завтра», «я выпил таблетку» — извлекаем сущности. */
    LOG(
        label = "Задача",
        emoji = "✓",
        hint = "Нашёл конкретное действие — превращаю в задачу или отметку"
    ),

    /**
     * Человек просто говорит: надиктовка, размышление вслух, «диктофон, который
     * сделает выжимку». Сущностей не создаём — вместо них структурированный конспект.
     */
    DICTATE(
        label = "Выжимка",
        emoji = "◎",
        hint = "Это не задача, а свободный поток — соберу конспект, не трогая список дел"
    ),

    /** Поток мыслей, внутри которого всё же есть конкретное действие: и конспект, и сущности. */
    MIXED(
        label = "Поток + дело",
        emoji = "⁂",
        hint = "Нашёл и рассуждение, и конкретное действие — сохраню оба"
    ),

    /** Вопрос к приложению или к себе: «что у меня сегодня», «сколько я вчера сделал». */
    QUERY(
        label = "Вопрос",
        emoji = "?",
        hint = "Это вопрос — отвечу, а не создам запись"
    );

    /** Создаёт ли режим новые сущности (задачи, отметки привычек). */
    val producesEntities: Boolean
        get() = this == LOG || this == MIXED

    /**
     * Нужен ли конспект как таковой.
     *
     * Для [QUERY] это только защитная сетка: на «а почему тогда сломалось» приложение
     * ответить не умеет, и без конспекта вопрос просто пропадёт. На «что у меня сегодня»
     * ответ есть, поэтому конспект не создаётся вовсе — решение об этом принимает
     * [com.voicehabit.tracker.data.remote.OfflineVoiceParser], а источник правды —
     * [com.voicehabit.tracker.domain.model.VoiceNoteAction.digest].
     */
    val needsDigest: Boolean
        get() = this != LOG
}

/**
 * H1. Конспект свободного потока — тот самый «диктофон, который сделает выжимку».
 *
 * Собран из транскрипта, а не из настроения: каждая секция заполняется только если
 * в речи действительно есть такое. Пустой список — честнее выдуманного пункта.
 */
@Immutable
data class CaptureDigest(
    /** Заголовок конспекта: тема записи в одну строку. */
    val title: String = "",
    /** Суть в одном-двух предложениях. Главное, что человек хотел сказать. */
    val gist: String = "",
    /** Ключевые мысли — тезисы, а не пересказ. */
    val keyPoints: List<String> = emptyList(),
    /** Принятые решения: «решили перенести релиз», «договорились». */
    val decisions: List<String> = emptyList(),
    /** Открытые вопросы, которые остались без ответа. */
    val openQuestions: List<String> = emptyList(),
    /** Что из сказанного превращается в действие (кандидаты в задачи, не задачи). */
    val nextSteps: List<String> = emptyList(),
    /** Упомянутые люди: извлекаются из именительной конструкции и заглавных букв. */
    val people: List<String> = emptyList(),
    /** Цифры из речи с единицами: «2 часа», «15 тысяч», «−3 °C». */
    val numbers: List<String> = emptyList(),
    /** Тон разговора одним словом, если настроение распозналось. */
    val tone: String = "",
    /** Сколько слов в исходном потоке. */
    val wordCount: Int = 0,
    /** Длительность речи в секундах, если известна. */
    val speechSeconds: Int = 0
) {
    /** Есть ли хоть что-то показать. Пустой конспект в UI не рисуем. */
    val isEmpty: Boolean
        get() = gist.isBlank() && keyPoints.isEmpty() && decisions.isEmpty() &&
            openQuestions.isEmpty() && nextSteps.isEmpty() && people.isEmpty() &&
            numbers.isEmpty()

    /** Плотность: сколько секций заполнено из всех. Показывается в списке конспектов. */
    val filledSections: Int
        get() = listOf(
            gist.isNotBlank(),
            keyPoints.isNotEmpty(),
            decisions.isNotEmpty(),
            openQuestions.isNotEmpty(),
            nextSteps.isNotEmpty(),
            people.isNotEmpty(),
            numbers.isNotEmpty()
        ).count { it }
}

/**
 * H1. Сохранённый конспект: [CaptureDigest] плюс то, чему конспект принадлежит.
 *
 * Содержимое «развёрнуто», а не вложено в [CaptureDigest], потому что экран списка
 * и экран чтения обращаются к секциям напрямую, и вложенность превращала бы
 * каждый экран в `record.content.keyPoints` — без выигрыша в читаемости.
 */
@Immutable
data class DigestRecord(
    val id: String,
    val voiceLogId: String? = null,
    val mode: IntentMode = IntentMode.DICTATE,
    val modeConfidence: Float = 0f,
    val transcript: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    val title: String = "",
    val gist: String = "",
    val keyPoints: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val nextSteps: List<String> = emptyList(),
    val people: List<String> = emptyList(),
    val numbers: List<String> = emptyList(),
    val tone: String = "",
    val wordCount: Int = 0,
    val speechSeconds: Int = 0
) {
    val isEmpty: Boolean
        get() = gist.isBlank() && keyPoints.isEmpty() && decisions.isEmpty() &&
            openQuestions.isEmpty() && nextSteps.isEmpty() && people.isEmpty() &&
            numbers.isEmpty()

    val filledSections: Int
        get() = listOf(
            gist.isNotBlank(),
            keyPoints.isNotEmpty(),
            decisions.isNotEmpty(),
            openQuestions.isNotEmpty(),
            nextSteps.isNotEmpty(),
            people.isNotEmpty(),
            numbers.isNotEmpty()
        ).count { it }

    /** Длительность в формате «мм:сс» — для карточки в списке. */
    val durationLabel: String
        get() = if (speechSeconds <= 0) "" else "%d:%02d".format(speechSeconds / 60, speechSeconds % 60)

    /** Короткая подпись для списка: суть, а не пересказ. */
    val preview: String
        get() = gist.ifBlank {
            keyPoints.firstOrNull() ?: decisions.firstOrNull() ?: nextSteps.firstOrNull() ?: ""
        }

    /**
     * Копия с новым содержимым: правки из шторки разбора (тема, убранные секции)
     * должны дойти до сохранённого конспекта, иначе список показывал бы старую версию.
     */
    fun withContent(content: CaptureDigest): DigestRecord = copy(
        title = content.title,
        gist = content.gist,
        keyPoints = content.keyPoints,
        decisions = content.decisions,
        openQuestions = content.openQuestions,
        nextSteps = content.nextSteps,
        people = content.people,
        numbers = content.numbers,
        tone = content.tone,
        wordCount = content.wordCount,
        speechSeconds = content.speechSeconds
    )

    fun toCaptureDigest(): CaptureDigest = CaptureDigest(
        title = title,
        gist = gist,
        keyPoints = keyPoints,
        decisions = decisions,
        openQuestions = openQuestions,
        nextSteps = nextSteps,
        people = people,
        numbers = numbers,
        tone = tone,
        wordCount = wordCount,
        speechSeconds = speechSeconds
    )

    companion object {
        /** Собирает запись из только что разобранной речи. */
        fun from(
            id: String,
            content: CaptureDigest,
            mode: IntentMode,
            modeConfidence: Float,
            transcript: String,
            voiceLogId: String? = null,
            pinned: Boolean = false,
            createdAt: Long = System.currentTimeMillis()
        ): DigestRecord = DigestRecord(
            id = id,
            voiceLogId = voiceLogId,
            mode = mode,
            modeConfidence = modeConfidence,
            transcript = transcript,
            pinned = pinned,
            createdAt = createdAt,
            title = content.title,
            gist = content.gist,
            keyPoints = content.keyPoints,
            decisions = content.decisions,
            openQuestions = content.openQuestions,
            nextSteps = content.nextSteps,
            people = content.people,
            numbers = content.numbers,
            tone = content.tone,
            wordCount = content.wordCount,
            speechSeconds = content.speechSeconds
        )
    }
}
