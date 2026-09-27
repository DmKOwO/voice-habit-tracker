package com.voicehabit.tracker.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalculateStreakUseCase(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    /**
     * Источник «сейчас». Раньше время бралось из `LocalDate.now()` напрямую,
     * поэтому стрик нельзя было проверить на границе суток: результат зависел
     * от времени запуска теста (часть тестов была нестабильна между 00:00 и 02:00).
     */
    private val now: () -> Long = System::currentTimeMillis
) {
    /**
     * Вычисляет текущий стрик выполнения по списку миллисекундных меток времени.
     * Дни группируются по локальному календарному дню (00:00 - 23:59).
     */
    /**
     * @param neutralDays epoch-дни заморозки (G6/G20): не продлевают стрик,
     * но и не разрывают его. Без них пропущенный день обнулял бы серию.
     */
    operator fun invoke(
        completedTimestamps: List<Long>,
        neutralDays: Set<Long> = emptySet()
    ): Int {
        if (completedTimestamps.isEmpty() && neutralDays.isEmpty()) return 0

        val daysSet = completedTimestamps
            .map { timestampToDayEpoch(it) }
            .toSet()

        val today = Instant.ofEpochMilli(now()).atZone(zoneId).toLocalDate().toEpochDay()

        var streak = 0
        var expectedDay = if (daysSet.contains(today) || neutralDays.contains(today)) today else today - 1
        var guard = 0
        while (guard++ < 365 * 5) {
            when {
                neutralDays.contains(expectedDay) -> expectedDay--
                daysSet.contains(expectedDay) -> {
                    streak++
                    expectedDay--
                }
                else -> break
            }
        }

        return streak
    }

    fun timestampToDayEpoch(millis: Long): Long {
        return Instant.ofEpochMilli(millis)
            .atZone(zoneId)
            .toLocalDate()
            .toEpochDay()
    }
}

