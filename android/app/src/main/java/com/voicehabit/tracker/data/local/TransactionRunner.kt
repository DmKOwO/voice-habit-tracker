package com.voicehabit.tracker.data.local

/**
 * Обёртка над `RoomDatabase.withTransaction`.
 *
 * Нужна, чтобы многошаговые записи (лог отметки + пересчёт стрика, применение
 * голосового разбора из нескольких действий) не оставляли базу в промежуточном
 * состоянии при исключении на середине.
 */
interface TransactionRunner {
    suspend operator fun <T> invoke(block: suspend () -> T): T

    companion object {
        /** Для юнит-тестов и случаев, когда транзакция не нужна. */
        val NoOp: TransactionRunner = object : TransactionRunner {
            override suspend fun <T> invoke(block: suspend () -> T): T = block()
        }
    }
}
