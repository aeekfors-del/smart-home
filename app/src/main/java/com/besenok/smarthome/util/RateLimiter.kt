package com.besenok.smarthome.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rate limiter с поддержкой sliding window
 * Ограничивает количество запросов к ioBroker API
 */
class RateLimiter(
    private val maxRequests: Int = 10,
    private val windowSeconds: Long = 1
) {
    private val timestamps = ArrayDeque<Long>()
    private val mutex = Mutex()

    /**
     * Ждёт пока станет доступен slot для запроса
     */
    suspend fun acquire() {
        while (true) {
            val waitNanos = mutex.withLock {
                val now = System.nanoTime()
                val windowNanos = windowSeconds * 1_000_000_000L
                val minTimestamp = now - windowNanos

                // Удаляем старые timestamps (список упорядочен по времени)
                while (timestamps.isNotEmpty() && timestamps.first() < minTimestamp) {
                    timestamps.removeFirst()
                }

                if (timestamps.size >= maxRequests) {
                    // Ждём освобождения самого старого слота; мьютекс при этом отпущен
                    (timestamps.first() + windowNanos) - now
                } else {
                    timestamps.addLast(now)
                    0L
                }
            }
            if (waitNanos <= 0L) return
            delay(waitNanos / 1_000_000L + 1)
        }
    }

    /**
     * Проверяет можно ли сделать запрос без ожидания
     */
    suspend fun canExecute(): Boolean = mutex.withLock {
        val now = System.nanoTime()
        val windowNanos = windowSeconds * 1_000_000_000L
        val minTimestamp = now - windowNanos

        while (timestamps.isNotEmpty() && timestamps.first() < minTimestamp) {
            timestamps.removeFirst()
        }
        timestamps.size < maxRequests
    }
}
