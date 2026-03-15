package com.orizon.openkiwi.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class TokenUsageRecord(
    val modelName: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String? = null
)

data class TokenUsageSummary(
    val totalPromptTokens: Long = 0,
    val totalCompletionTokens: Long = 0,
    val totalTokens: Long = 0,
    val requestCount: Int = 0,
    val byModel: Map<String, ModelTokenUsage> = emptyMap()
)

data class ModelTokenUsage(
    val modelName: String,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
    val requestCount: Int = 0
)

object TokenTracker {
    private val records = mutableListOf<TokenUsageRecord>()
    private val _summary = MutableStateFlow(TokenUsageSummary())
    val summary: StateFlow<TokenUsageSummary> = _summary.asStateFlow()

    private val _lastUsage = MutableStateFlow<TokenUsageRecord?>(null)
    val lastUsage: StateFlow<TokenUsageRecord?> = _lastUsage.asStateFlow()

    fun record(usage: Usage?, modelName: String, sessionId: String? = null) {
        if (usage == null) return
        val record = TokenUsageRecord(
            modelName = modelName,
            promptTokens = usage.promptTokens,
            completionTokens = usage.completionTokens,
            totalTokens = usage.totalTokens,
            sessionId = sessionId
        )
        synchronized(records) {
            records.add(record)
            if (records.size > 10000) records.removeAt(0)
        }
        _lastUsage.value = record
        recalcSummary()
    }

    fun getSummary(): TokenUsageSummary = _summary.value

    fun getRecordsSince(since: Long): List<TokenUsageRecord> {
        synchronized(records) {
            return records.filter { it.timestamp >= since }
        }
    }

    fun clearRecords() {
        synchronized(records) { records.clear() }
        _lastUsage.value = null
        _summary.value = TokenUsageSummary()
    }

    private fun recalcSummary() {
        synchronized(records) {
            val byModel = records.groupBy { it.modelName }.mapValues { (name, recs) ->
                ModelTokenUsage(
                    modelName = name,
                    promptTokens = recs.sumOf { it.promptTokens.toLong() },
                    completionTokens = recs.sumOf { it.completionTokens.toLong() },
                    totalTokens = recs.sumOf { it.totalTokens.toLong() },
                    requestCount = recs.size
                )
            }
            _summary.value = TokenUsageSummary(
                totalPromptTokens = records.sumOf { it.promptTokens.toLong() },
                totalCompletionTokens = records.sumOf { it.completionTokens.toLong() },
                totalTokens = records.sumOf { it.totalTokens.toLong() },
                requestCount = records.size,
                byModel = byModel
            )
        }
    }
}
