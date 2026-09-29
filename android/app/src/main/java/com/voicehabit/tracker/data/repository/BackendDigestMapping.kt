package com.voicehabit.tracker.data.repository

import com.voicehabit.tracker.data.remote.dto.DigestDto
import com.voicehabit.tracker.domain.model.CaptureDigest

/**
 * Бэкенд вернул синтетический разбор: аудио не было распознано или разобрано,
 * поля ответа подставлены демонстрационными значениями.
 *
 * Такое значение обязано доходить до пользователя явной ошибкой, а не молча
 * применяться к базе. Раньше такой ответ сохранялся как обычный: человек
 * говорил «сегодня продуктивный день» и получал отмеченный спортзал, две литра
 * воды и три выдуманных задачи — без единого признака, что этого он не говорил.
 */
class SyntheticBackendResultException(
    val modelUsed: String = ""
) : Exception(
    "Бэкенд не смог разобрать запись и вернул демонстрационные данные ($modelUsed). " +
        "Они не сохранены. Проверь ключи Groq/Gemini или разбери запись офлайн."
)

/**
 * Контракт конспекта с DTO бэкенда в доменную модель.
 *
 * Раньше `digest` из ответа бэкенда вообще не читался: JOURNAL-разбор приходил
 * в шторку как LOG с уверенностью 0% и пустым конспектом, хотя сервер всё
 * вычислил и всё отдал.
 */
fun DigestDto.toCaptureDigest(): CaptureDigest = CaptureDigest(
    title = title.orEmpty(),
    gist = gist.orEmpty(),
    keyPoints = keyPoints.orEmpty(),
    decisions = decisions.orEmpty(),
    openQuestions = openQuestions.orEmpty(),
    nextSteps = nextSteps.orEmpty(),
    people = people.orEmpty(),
    numbers = numbers.orEmpty(),
    tone = tone.orEmpty()
)
