package com.orizon.openkiwi.core.widget

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object WidgetActionBus {
    private val _prompts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val prompts: SharedFlow<String> = _prompts.asSharedFlow()

    fun emit(prompt: String) {
        _prompts.tryEmit(prompt)
    }
}
