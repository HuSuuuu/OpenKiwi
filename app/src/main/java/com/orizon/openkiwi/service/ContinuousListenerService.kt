package com.orizon.openkiwi.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Always-on voice listener that auto-restarts recognition.
 * Emits recognized phrases via a SharedFlow.
 */
class ContinuousListenerService(private val context: Context) {

    companion object {
        private const val TAG = "ContinuousListener"
    }

    private var recognizer: SpeechRecognizer? = null
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val recognizedText: SharedFlow<String> = _recognizedText.asSharedFlow()

    private var wakeWord: String? = null
    private var autoRestart = false

    fun setWakeWord(word: String?) {
        wakeWord = word?.lowercase()
    }

    fun start(language: String = "zh-CN") {
        if (_isListening.value) return
        autoRestart = true

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        if (wakeWord != null) {
                            if (text.lowercase().contains(wakeWord!!)) {
                                val command = text.lowercase().substringAfter(wakeWord!!).trim()
                                if (command.isNotBlank()) _recognizedText.tryEmit(command)
                            }
                        } else {
                            _recognizedText.tryEmit(text)
                        }
                    }
                    if (autoRestart) restartRecognition(language)
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "Recognition error: $error")
                    if (autoRestart && error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        restartRecognition(language)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onEndOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            startRecognition(language)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start continuous listener", e)
        }
    }

    fun stop() {
        autoRestart = false
        _isListening.value = false
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
        } catch (_: Exception) {}
    }

    private fun startRecognition(language: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
        }
    }

    private fun restartRecognition(language: String) {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {}
        android.os.Handler(context.mainLooper).postDelayed({
            if (autoRestart) startRecognition(language)
        }, 500)
    }
}
