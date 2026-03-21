package com.orizon.openkiwi.core.notification

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.orizon.openkiwi.data.preferences.UserPreferences
import com.orizon.openkiwi.service.NotificationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends a quick reply via [android.app.Notification.Action] + [RemoteInput] when the notification exposes inline reply.
 */
class AutoReplyManager(
    private val context: Context,
    private val userPreferences: UserPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val replyCounts = ConcurrentHashMap<String, MutableList<Long>>()

    fun maybeReplyAsync(sbn: StatusBarNotification, info: NotificationInfo) {
        scope.launch {
            runCatching { maybeReply(sbn, info) }
                .onFailure { Log.e(TAG, "auto-reply error", it) }
        }
    }

    private suspend fun maybeReply(sbn: StatusBarNotification, info: NotificationInfo) {
        if (!userPreferences.notifAutoReplyEnabled.first()) return
        if (info.isOngoing) return
        val template = userPreferences.notifAutoReplyTemplate.first().ifBlank { "收到" }
        val allow = userPreferences.notifAutoReplyPackages.first().trim()
        val allowedPkgs = if (allow.isBlank()) DEFAULT_MESSAGING_PACKAGES else allow.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (info.packageName !in allowedPkgs) return

        if (!rateOk(info.packageName)) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return
        val notification = sbn.notification
        val actions = notification.actions ?: return

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            if (remoteInputs.isEmpty()) continue
            val remoteInput = remoteInputs[0]
            val resultKey = remoteInput.resultKey ?: continue

            val fillIn = Intent()
            val results = Bundle()
            results.putCharSequence(resultKey, template)
            RemoteInput.addResultsToIntent(remoteInputs, fillIn, results)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    action.actionIntent?.send(
                        context,
                        0,
                        fillIn,
                        null,
                        null,
                        null,
                        Bundle()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    action.actionIntent?.send(context, 0, fillIn)
                }
                markReply(info.packageName)
                Log.i(TAG, "Sent inline reply to ${info.packageName}")
                return
            } catch (e: Exception) {
                Log.w(TAG, "send failed for ${info.packageName}: ${e.message}")
            }
        }
    }

    private fun rateOk(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - RATE_WINDOW_MS
        val list = replyCounts.getOrPut(pkg) { mutableListOf() }
        list.removeAll { it < windowStart }
        return list.size < MAX_REPLIES_PER_WINDOW
    }

    private fun markReply(pkg: String) {
        val now = System.currentTimeMillis()
        replyCounts.getOrPut(pkg) { mutableListOf() }.add(now)
    }

    companion object {
        private const val TAG = "AutoReplyManager"
        private const val RATE_WINDOW_MS = 60 * 60 * 1000L
        private const val MAX_REPLIES_PER_WINDOW = 8

        private val DEFAULT_MESSAGING_PACKAGES = setOf(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms"
        )
    }
}
