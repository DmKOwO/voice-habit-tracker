package com.voicehabit.tracker.presentation.home

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Сериализация и дебаунс переключения задач.
 *
 * Две проблемы, которые он закрывает:
 * 1. Двойной тап по чекбоксу: два запроса `complete`+`reopen` уходят в БД почти
 *    одновременно, итог зависит от порядка выполнения — визуально «зависание»
 *    или «тап не сработал». Тапы чаще [debounceMillis] отбрасываются.
 * 2. Гонка параллельных переключений одной задачи (тап + свайп + виджет):
 *    записи в журнал идут строго по очереди через per-task [Mutex].
 */
class TaskToggleGate(
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMillis: Long = 300L
) {
    private val mutexes = ConcurrentHashMap<String, Mutex>()
    private val lastTapAt = ConcurrentHashMap<String, Long>()
    private val checkLock = Any()

    /**
     * Синхронная проверка дебаунса. Вызывать ДО оптимистичного обновления UI:
     * false означает «дребезг, ничего не делать и не трогать стейт».
     */
    fun shouldProceed(taskId: String): Boolean {
        val now = clock()
        synchronized(checkLock) {
            val last = lastTapAt[taskId]
            // Первый тап всегда проходит; вычитание делается только при
            // известном предыдущем тапе, иначе Long.MIN_VALUE даёт переполнение.
            if (last != null && now - last < debounceMillis) return false
            lastTapAt[taskId] = now
            return true
        }
    }

    suspend fun <T> serialized(taskId: String, block: suspend () -> T): T {
        val mutex = mutexes.getOrPut(taskId) { Mutex() }
        return mutex.withLock { block() }
    }

    fun forget(taskId: String) {
        lastTapAt.remove(taskId)
    }
}
