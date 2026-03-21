package com.orizon.openkiwi.core.clipboard

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** (action, text) — action: analyze | search | translate */
object ClipboardQuickBus {
    private val _events = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val events: SharedFlow<Pair<String, String>> = _events.asSharedFlow()

    fun emit(action: String, text: String) {
        _events.tryEmit(action to text)
    }
}
