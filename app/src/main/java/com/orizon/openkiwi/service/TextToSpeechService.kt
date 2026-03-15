package com.orizon.openkiwi.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class TextToSpeechService(context: Context) {

    private var tts: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
                _isReady.value = true
            }
        }
    }

    fun speak(text: String, utteranceId: String = "kiwi_${System.currentTimeMillis()}") {
        tts?.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { _isSpeaking.value = true }
                override fun onDone(id: String?) { _isSpeaking.value = false }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { _isSpeaking.value = false }
            })
            speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
    }

    suspend fun speakAndWait(text: String): Boolean = suspendCancellableCoroutine { cont ->
        val id = "kiwi_await_${System.currentTimeMillis()}"
        tts?.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(uid: String?) { _isSpeaking.value = true }
                override fun onDone(uid: String?) {
                    _isSpeaking.value = false
                    if (uid == id && cont.isActive) cont.resume(true)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(uid: String?) {
                    _isSpeaking.value = false
                    if (uid == id && cont.isActive) cont.resume(false)
                }
            })
            speak(text, TextToSpeech.QUEUE_ADD, null, id)
        } ?: cont.resume(false)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun setSpeechRate(rate: Float) { tts?.setSpeechRate(rate.coerceIn(0.1f, 3.0f)) }
    fun setPitch(pitch: Float) { tts?.setPitch(pitch.coerceIn(0.1f, 3.0f)) }
    fun setLanguage(locale: Locale) { tts?.language = locale }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
    }
}
