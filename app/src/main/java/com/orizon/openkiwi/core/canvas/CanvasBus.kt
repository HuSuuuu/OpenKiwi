package com.orizon.openkiwi.core.canvas

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * AI [CanvasTool] 与画布 UI 之间的单向事件流。
 */
sealed class CanvasEvent {
    data class PushHtml(val html: String) : CanvasEvent()
    data class EvalJs(val js: String) : CanvasEvent()
    data object Clear : CanvasEvent()
}

object CanvasBus {
    private val _events = MutableSharedFlow<CanvasEvent>(replay = 1, extraBufferCapacity = 8)
    val events: SharedFlow<CanvasEvent> = _events
    fun tryEmit(e: CanvasEvent): Boolean = _events.tryEmit(e)
}

/**
 * WebView 中用户操作通过 JS Bridge 回传，再转为聊天消息发送给 Agent。
 */
object CanvasActionBus {
    private val _prompts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val prompts: SharedFlow<String> = _prompts
    fun emitPrompt(s: String) {
        _prompts.tryEmit(s)
    }
}

/** 最近一次画布 HTML，用于返回聊天页后再进入时恢复 */
object CanvasSnapshot {
    @Volatile
    var lastHtml: String? = null
}
