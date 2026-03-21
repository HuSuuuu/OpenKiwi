package com.orizon.openkiwi.network

import android.util.Log
import com.lark.oapi.core.utils.Jsons
import com.lark.oapi.event.EventDispatcher
import com.lark.oapi.service.im.ImService
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1
import com.lark.oapi.ws.Client
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * Feishu enterprise app long connection on the phone (same as lark-oapi on PC).
 * Requires developer console: 事件订阅 → 使用长连接接收事件.
 */
class FeishuLarkWsClient(
    private val feishuEventHandler: FeishuEventHandler
) {
    private val clientRef = AtomicReference<Client?>(null)
    private val threadRef = AtomicReference<Thread?>(null)

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun start(appId: String, appSecret: String) {
        stop()
        if (appId.isBlank() || appSecret.isBlank()) {
            Log.w(TAG, "start skipped: empty app id/secret")
            return
        }

        val dispatcher = EventDispatcher.newBuilder("", "")
            .onP2MessageReceiveV1(object : ImService.P2MessageReceiveV1Handler() {
                override fun handle(event: P2MessageReceiveV1?) {
                    if (event == null) return
                    try {
                        val ev = event.event ?: return
                        val jsonStr = Jsons.DEFAULT.toJson(ev)
                        val element = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr)
                        val obj = element as? JsonObject ?: return
                        val normalized = normalizeLarkEventToWebhookShape(obj)
                        feishuEventHandler.handleIncomingEventAsync(normalized)
                    } catch (e: Exception) {
                        Log.e(TAG, "Feishu WS event error", e)
                    }
                }
            })
            .build()

        val client = Client.Builder(appId, appSecret)
            .eventHandler(dispatcher)
            .build()

        val t = Thread({
            _active.value = true
            try {
                Log.i(TAG, "Feishu long connection thread running")
                client.start()
            } catch (e: Exception) {
                Log.e(TAG, "Feishu WS client error", e)
            } finally {
                _active.value = false
                Log.i(TAG, "Feishu long connection thread exit")
            }
        }, "FeishuLarkWs")
        t.isDaemon = true
        clientRef.set(client)
        threadRef.set(t)
        t.start()
    }

    /**
     * Ensure [FeishuEventHandler] shape: top-level `message` (+ optional `sender`).
     */
    private fun normalizeLarkEventToWebhookShape(ev: JsonObject): JsonObject {
        if (ev["message"] != null) return ev
        return kotlinx.serialization.json.buildJsonObject {
            put("message", ev)
            val sender = ev["sender"] as? JsonObject
            if (sender != null) put("sender", sender)
        }
    }

    fun stop() {
        _active.value = false
        threadRef.getAndSet(null)
        val c = clientRef.getAndSet(null)
        tryStopLarkClient(c)
    }

    private fun tryStopLarkClient(c: Client?) {
        if (c == null) return
        runCatching {
            val m = c.javaClass.methods.firstOrNull { it.name == "stop" && it.parameterCount == 0 }
            m?.invoke(c)
        }.onFailure {
            runCatching {
                val m = c.javaClass.methods.firstOrNull { it.name == "disconnect" && it.parameterCount == 0 }
                m?.invoke(c)
            }
        }
    }

    companion object {
        private const val TAG = "FeishuLarkWs"
    }
}
