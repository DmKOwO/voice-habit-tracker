package com.voicehabit.tracker.core.calendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * F10: экспорт дедлайна в системный календарь.
 *
 * Используется ACTION_INSERT — системное приложение календаря само показывает
 * подтверждение, поэтому разрешение WRITE_CALENDAR не требуется и событие
 * создаётся только с ведома пользователя.
 */
object CalendarHelper {

    fun insertEventIntent(
        title: String,
        description: String?,
        dueIso: String?,
        zone: ZoneId = ZoneId.systemDefault()
    ): Intent {
        val begin = dueIso?.let { parseBeginMillis(it, zone) }
            ?: System.currentTimeMillis() + 3600_000L
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            if (!description.isNullOrBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, description)
            }
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 3600_000L)
        }
    }

    fun canHandle(context: Context, intent: Intent): Boolean =
        intent.resolveActivity(context.packageManager) != null

    private fun parseBeginMillis(dueIso: String, zone: ZoneId): Long? {
        return try {
            val ldt = LocalDateTime.parse(dueIso.take(19))
            ldt.atZone(zone).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                java.time.LocalDate.parse(dueIso.take(10)).atTime(9, 0).atZone(zone)
                    .toInstant().toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }
}
