package com.orizon.openkiwi.core.security

import android.util.Log
import com.orizon.openkiwi.data.local.dao.AuditLogDao
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Detects anomalous agent behavior patterns:
 * - High-frequency dangerous operations
 * - Repeated permission escalation attempts
 * - Unusual API call patterns
 * - Operations outside authorized scope
 */
class AnomalyDetector(private val auditLogDao: AuditLogDao?) {

    companion object {
        private const val TAG = "AnomalyDetector"
        private const val WINDOW_MS = 60_000L
        private const val MAX_DANGEROUS_OPS_PER_MINUTE = 10
        private const val MAX_FAILED_OPS_PER_MINUTE = 15
        private const val MAX_SENSITIVE_OPS_PER_MINUTE = 5
    }

    data class AnomalyAlert(
        val type: AnomalyType,
        val message: String,
        val severity: Severity,
        val timestamp: Long = System.currentTimeMillis(),
        val shouldPause: Boolean = false
    )

    enum class AnomalyType {
        HIGH_FREQUENCY_DANGEROUS,
        REPEATED_FAILURES,
        SENSITIVE_OP_SPIKE,
        SCOPE_VIOLATION,
        UNUSUAL_PATTERN
    }

    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    private val _alerts = MutableSharedFlow<AnomalyAlert>(extraBufferCapacity = 50)
    val alerts: SharedFlow<AnomalyAlert> = _alerts.asSharedFlow()

    private val recentOps = mutableListOf<OpRecord>()

    private data class OpRecord(
        val toolName: String,
        val permissionLevel: String,
        val success: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun recordOperation(toolName: String, permissionLevel: String, success: Boolean) {
        val now = System.currentTimeMillis()
        synchronized(recentOps) {
            recentOps.add(OpRecord(toolName, permissionLevel, success))
            recentOps.removeAll { now - it.timestamp > WINDOW_MS * 5 }
        }
        checkAnomalies()
    }

    private fun checkAnomalies() {
        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_MS

        synchronized(recentOps) {
            val windowOps = recentOps.filter { it.timestamp >= windowStart }

            // Check high-frequency dangerous operations
            val dangerousOps = windowOps.count { it.permissionLevel == "DANGEROUS" }
            if (dangerousOps > MAX_DANGEROUS_OPS_PER_MINUTE) {
                emitAlert(AnomalyAlert(
                    type = AnomalyType.HIGH_FREQUENCY_DANGEROUS,
                    message = "1分钟内执行了${dangerousOps}次危险操作，已超出阈值($MAX_DANGEROUS_OPS_PER_MINUTE)",
                    severity = Severity.HIGH,
                    shouldPause = true
                ))
            }

            // Check repeated failures
            val failedOps = windowOps.count { !it.success }
            if (failedOps > MAX_FAILED_OPS_PER_MINUTE) {
                emitAlert(AnomalyAlert(
                    type = AnomalyType.REPEATED_FAILURES,
                    message = "1分钟内有${failedOps}次操作失败，可能存在异常循环",
                    severity = Severity.MEDIUM,
                    shouldPause = false
                ))
            }

            // Check sensitive operation spike
            val sensitiveOps = windowOps.count { it.permissionLevel == "SENSITIVE" }
            if (sensitiveOps > MAX_SENSITIVE_OPS_PER_MINUTE) {
                emitAlert(AnomalyAlert(
                    type = AnomalyType.SENSITIVE_OP_SPIKE,
                    message = "1分钟内执行了${sensitiveOps}次敏感操作，已暂停任务",
                    severity = Severity.CRITICAL,
                    shouldPause = true
                ))
            }

            // Check for unusual repetitive patterns
            if (windowOps.size >= 6) {
                val lastSix = windowOps.takeLast(6).map { it.toolName }
                if (lastSix.take(3) == lastSix.drop(3)) {
                    emitAlert(AnomalyAlert(
                        type = AnomalyType.UNUSUAL_PATTERN,
                        message = "检测到重复操作模式: ${lastSix.take(3).joinToString("→")}",
                        severity = Severity.LOW,
                        shouldPause = false
                    ))
                }
            }
        }
    }

    private fun emitAlert(alert: AnomalyAlert) {
        Log.w(TAG, "[${alert.severity}] ${alert.type}: ${alert.message}")
        _alerts.tryEmit(alert)
        if (alert.shouldPause) {
            EmergencyStop.activate()
        }
    }

    fun clearHistory() {
        synchronized(recentOps) { recentOps.clear() }
    }
}
