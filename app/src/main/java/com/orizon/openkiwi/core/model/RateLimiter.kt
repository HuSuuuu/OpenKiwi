package com.orizon.openkiwi.core.model

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RateLimiter(
    private val maxRequestsPerMinute: Int = 60,
    private val maxTokensPerMinute: Int = 100_000
) {
    private val mutex = Mutex()
    private val requestTimestamps = mutableListOf<Long>()
    private val tokenRecords = mutableListOf<Pair<Long, Int>>()

    suspend fun acquire(estimatedTokens: Int = 0) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val oneMinuteAgo = now - 60_000L

            requestTimestamps.removeAll { it < oneMinuteAgo }
            tokenRecords.removeAll { it.first < oneMinuteAgo }

            if (requestTimestamps.size >= maxRequestsPerMinute) {
                val waitUntil = requestTimestamps.first() + 60_000L
                val waitMs = waitUntil - now
                if (waitMs > 0) delay(waitMs)
                requestTimestamps.removeAll { it < System.currentTimeMillis() - 60_000L }
            }

            if (maxTokensPerMinute > 0 && estimatedTokens > 0) {
                val currentTokens = tokenRecords.sumOf { it.second }
                if (currentTokens + estimatedTokens > maxTokensPerMinute) {
                    val waitUntil = tokenRecords.first().first + 60_000L
                    val waitMs = waitUntil - System.currentTimeMillis()
                    if (waitMs > 0) delay(waitMs)
                    tokenRecords.removeAll { it.first < System.currentTimeMillis() - 60_000L }
                }
            }

            requestTimestamps.add(System.currentTimeMillis())
            if (estimatedTokens > 0) {
                tokenRecords.add(System.currentTimeMillis() to estimatedTokens)
            }
        }
    }

    fun recordTokens(tokens: Int) {
        tokenRecords.add(System.currentTimeMillis() to tokens)
    }

    fun getRemainingRequests(): Int {
        val now = System.currentTimeMillis()
        requestTimestamps.removeAll { it < now - 60_000L }
        return (maxRequestsPerMinute - requestTimestamps.size).coerceAtLeast(0)
    }
}
