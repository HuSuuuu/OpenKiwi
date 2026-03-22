package com.orizon.openkiwi.network

import android.util.Log
import com.orizon.openkiwi.core.agent.AgentEngine
import com.orizon.openkiwi.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles Feishu IM message events (webhook, PC forwarder, or phone long connection).
 * Shared by [CompanionServer] and [FeishuLarkWsClient].
 */
class FeishuEventHandler(
    private val agentEngine: AgentEngine,
    private val chatRepository: ChatRepository,
    private val feishuApiClient: FeishuApiClient?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val feishuMessageIds = ConcurrentHashMap<String, Long>()
    private val feishuSessions = ConcurrentHashMap<String, String>()

    /**
     * Webhook / forwarded payload: `event` object with `message` + optional `sender`.
     */
    fun handleIncomingEventAsync(event: JsonObject) {
        scope.launch {
            runCatching { handleFeishuMessage(event) }
                .onFailure { Log.e(TAG, "Feishu handle error", it) }
        }
    }

    suspend fun handleFeishuMessage(event: JsonObject) {
        val message = event["message"] as? JsonObject ?: return
        val sender = event["sender"] as? JsonObject

        val messageId = message["message_id"]?.let { (it as? JsonPrimitive)?.content } ?: return
        val chatId = message["chat_id"]?.let { (it as? JsonPrimitive)?.content } ?: return
        val msgType = message["message_type"]?.let { (it as? JsonPrimitive)?.content }
        val contentStr = message["content"]?.let { (it as? JsonPrimitive)?.content } ?: return

        val now = System.currentTimeMillis()
        feishuMessageIds[messageId]?.let { if (now - it < 60_000) return }
        feishuMessageIds[messageId] = now
        if (feishuMessageIds.size > 500) {
            val cutoff = now - 120_000
            feishuMessageIds.entries.removeAll { it.value < cutoff }
        }

        val userText = if (msgType == "text") {
            val contentJson = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(contentStr) as? JsonObject
            }.getOrNull()
            contentJson?.get("text")?.let { (it as? JsonPrimitive)?.content } ?: contentStr
        } else {
            "[$msgType 消息]"
        }

        if (userText.isBlank()) return

        val senderName = sender?.get("sender_id")?.let { senderIdObj ->
            (senderIdObj as? JsonObject)?.get("open_id")?.let {
                (it as? JsonPrimitive)?.content
            }
        } ?: "unknown"

        Log.i(TAG, "Feishu msg from $senderName in $chatId: $userText")

        try {
            val sessionId = ensureFeishuSession(chatId)
            val sb = StringBuilder()
            agentEngine.processMessage(sessionId, userText).collect { chunk ->
                if (!chunk.startsWith("§T§") && !chunk.startsWith("\n[Calling tool:") && !chunk.startsWith("[Tool result:")) {
                    sb.append(chunk)
                }
            }
            val replyText = com.orizon.openkiwi.util.VendorResponseSanitizer
                .stripPseudoFunctionCallBlocks(sb.toString())
                .trim()
            if (replyText.isNotBlank() && feishuApiClient != null) {
                val content = buildJsonObject { put("text", replyText) }.toString()
                feishuApiClient.replyMessage(messageId, "text", content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Feishu reply error", e)
            feishuApiClient?.runCatching {
                val safe = (e.message ?: "error").take(100).replace("\"", "")
                val errContent = buildJsonObject { put("text", "处理消息时出错: $safe") }.toString()
                replyMessage(messageId, "text", errContent)
            }
        }
    }

    private suspend fun ensureFeishuSession(chatId: String): String {
        feishuSessions[chatId]?.let { return it }
        val sessionId = chatRepository.createSession(title = "飞书: ${chatId.takeLast(8)}")
        feishuSessions[chatId] = sessionId
        return sessionId
    }

    companion object {
        private const val TAG = "FeishuEventHandler"
    }
}
