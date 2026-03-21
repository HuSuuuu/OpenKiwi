package com.orizon.openkiwi.core.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Carries text recognized after the wake phrase from [com.orizon.openkiwi.service.ContinuousListenerService] to [com.orizon.openkiwi.ui.chat.ChatViewModel]. */
object VoiceWakeCommandBus {
    private val _commands = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val commands: SharedFlow<String> = _commands.asSharedFlow()

    fun tryEmit(text: String) = _commands.tryEmit(text)
}
