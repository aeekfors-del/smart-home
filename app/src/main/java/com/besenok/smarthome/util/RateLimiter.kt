package com.besenok.smarthome.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

/**
 * Rate limiter с поддержкой sliding window
 * Ограничивает количество запросов к ioBroker API
 */
class RateLimiter(
    private val maxRequests: Int = 10,
    private val windowSeconds: Long = 1
) {
    private val timestamps = mutableListOf<Long>()
    private val mutex = Mutex()

    /**
     * Ждёт пока станет доступен slot для запроса
     */
    suspend fun acquire() {
        mutex.withLock {
            val now = System.nanoTime()
            val windowNanos = windowSeconds * 1_000_000_000L
            val minTimestamp = now - windowNanos

            // Удаляем старые timestamps
            timestamps.removeAll { it < minTimestamp }

            // Если лимит исчерпан — ждём
            if (timestamps.size >= maxRequests) {
                val oldest = timestamps.minOrNull() ?: 0
                val waitTime = (oldest + windowNanos) - now
                if (waitTime > 0) {
                    mutex.unlock()
                    delay(waitTime / 1_000_000) // convert to ms
                    mutex.withLock { }
                }
            }

            timestamps.add(System.nanoTime())
        }
    }

    /**
     * Проверяет можно ли сделать запрос без ожидания
     */
    suspend fun canExecute(): Boolean {
        mutex.withLock {
            val now = System.nanoTime()
            val windowNanos = windowSeconds * 1_000_000_000L
            val minTimestamp = now - windowNanos

            timestamps.removeAll { it < minTimestamp }
            return timestamps.size < maxRequests
        }
    }
}
