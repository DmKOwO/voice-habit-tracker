package com.voicehabit.tracker

import android.app.Application
import com.voicehabit.tracker.core.logging.AppLogger
import com.voicehabit.tracker.core.logging.LogLevel
import com.voicehabit.tracker.data.local.AppDatabase

class VoiceHabitApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Журнал поднимается первым: всё, что происходит дальше (миграция БД, сид,
        // фоновые операции), должно попадать в stdout и в экран операций.
        val logger = AppLogger.install(this)
        logger.i("app", "Запуск приложения", mapOf("version" to BuildConfigCompat.versionName()))

        val start = System.currentTimeMillis()
        AppDatabase.getInstance(this)
        logger.d(
            "db",
            "База данных открыта",
            mapOf("openedInMs" to (System.currentTimeMillis() - start).toString())
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Записи и разбор должны пережить сворачивание: сбрасываем только кэш.
        AppLogger.instance().w(
            "app",
            "Система запросила освобождение памяти",
            mapOf("level" to level.toString()),
            null
        )
    }
}

private object BuildConfigCompat {
    fun versionName(): String = runCatching {
        val clazz = Class.forName("com.voicehabit.tracker.BuildConfig")
        clazz.getField("VERSION_NAME").get(null) as? String ?: "unknown"
    }.getOrDefault("unknown")
}
