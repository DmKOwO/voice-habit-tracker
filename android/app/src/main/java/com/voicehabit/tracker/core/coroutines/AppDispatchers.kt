package com.voicehabit.tracker.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Явные диспетчеры вместо обращения к [Dispatchers] по коду.
 *
 * Задача: UI не должен ждать БД, сеть или аудио. В тестах подменяется целиком,
 * поэтому `runTest` управляет временем, а не реальными потоками.
 */
data class AppDispatchers(
    val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val audio: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        val Default = AppDispatchers()
    }
}
