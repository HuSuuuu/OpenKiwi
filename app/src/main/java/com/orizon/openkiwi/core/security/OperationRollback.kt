package com.orizon.openkiwi.core.security

import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Tracks reversible operations and provides undo/rollback capability.
 */
class OperationRollback {

    companion object {
        private const val TAG = "Rollback"
        private const val MAX_HISTORY = 50
    }

    data class RollbackEntry(
        val id: String = java.util.UUID.randomUUID().toString(),
        val description: String,
        val timestamp: Long = System.currentTimeMillis(),
        val rollbackAction: suspend () -> Boolean,
        val canRollback: Boolean = true
    )

    private val history = ConcurrentLinkedDeque<RollbackEntry>()

    fun record(description: String, canRollback: Boolean = true, rollbackAction: suspend () -> Boolean) {
        history.addLast(RollbackEntry(
            description = description,
            rollbackAction = rollbackAction,
            canRollback = canRollback
        ))
        while (history.size > MAX_HISTORY) history.removeFirst()
    }

    suspend fun rollbackLast(): Boolean {
        val entry = history.pollLast() ?: return false
        if (!entry.canRollback) {
            Log.w(TAG, "Cannot rollback: ${entry.description}")
            return false
        }
        return try {
            val ok = entry.rollbackAction()
            Log.i(TAG, "Rollback ${if (ok) "success" else "failed"}: ${entry.description}")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Rollback error: ${entry.description}", e)
            false
        }
    }

    suspend fun rollbackById(id: String): Boolean {
        val entry = history.find { it.id == id } ?: return false
        history.remove(entry)
        if (!entry.canRollback) return false
        return try {
            entry.rollbackAction()
        } catch (e: Exception) {
            Log.e(TAG, "Rollback error", e)
            false
        }
    }

    fun getHistory(): List<RollbackEntry> = history.toList().reversed()

    fun clear() { history.clear() }

    fun canRollback(): Boolean = history.peekLast()?.canRollback == true
}
