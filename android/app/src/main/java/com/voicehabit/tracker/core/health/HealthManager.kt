package com.voicehabit.tracker.core.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * F11: шаги и сон из Health Connect как автологи привычек.
 *
 * Всё обернуто в runCatching: на устройстве без Health Connect (эмулятор без
 * пакета, старые прошивки) фича честно сообщает «недоступно», а не падает.
 */
class HealthManager(private val appContext: Context) {

    val permissions = setOf(
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    fun availability(): Availability {
        // Провайдер может отсутствовать (эмулятор без пакета, старые прошивки):
        // сначала проверяем пакет, затем пробуем создать клиент.
        val providerInstalled = try {
            appContext.packageManager.getPackageInfo("com.google.android.apps.healthdata", 0)
            true
        } catch (e: Exception) {
            false
        }
        if (!providerInstalled) return Availability.NOT_INSTALLED
        return try {
            HealthConnectClient.getOrCreate(appContext)
            Availability.AVAILABLE
        } catch (e: Exception) {
            Availability.UNSUPPORTED
        }
    }

    suspend fun stepsToday(zone: ZoneId = ZoneId.systemDefault()): Int? {
        return try {
            val client = HealthConnectClient.getOrCreate(appContext)
            val today = LocalDate.now(zone)
            val start = today.atStartOfDay(zone).toInstant()
            val response = client.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, Instant.now())
                )
            )
            response.records.sumOf { it.count.toInt() }.takeIf { true }
        } catch (e: Exception) {
            null
        }
    }

    /** Часы сна за последнюю ночь (сессии, закончившиеся сегодня). */
    suspend fun sleepHoursLastNight(zone: ZoneId = ZoneId.systemDefault()): Double? {
        return try {
            val client = HealthConnectClient.getOrCreate(appContext)
            val now = Instant.now()
            val response = client.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minusSeconds(36 * 3600), now)
                )
            )
            val sessions = response.records.filter { it.endTime.isAfter(now.minusSeconds(20 * 3600)) }
            if (sessions.isEmpty()) return null
            sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() } / 60.0
        } catch (e: Exception) {
            null
        }
    }

    enum class Availability { AVAILABLE, NOT_INSTALLED, UNSUPPORTED }
}
