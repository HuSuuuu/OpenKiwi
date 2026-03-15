package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.service.TextToSpeechService
import com.orizon.openkiwi.service.VoiceRecognitionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class VoiceTool(
    private val sttService: VoiceRecognitionService,
    private val ttsService: TextToSpeechService
) : Tool {
    override val definition = ToolDefinition(
        name = "voice",
        description = "Voice interaction: listen via microphone (speech-to-text) and speak text aloud (text-to-speech)",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: listen, speak, stop_listen, stop_speak, speak_and_wait", true,
                enumValues = listOf("listen", "speak", "stop_listen", "stop_speak", "speak_and_wait")),
            "text" to ToolParamDef("string", "Text for TTS to speak"),
            "language" to ToolParamDef("string", "Language code (e.g. zh-CN, en-US)", false, "zh-CN"),
            "timeout_seconds" to ToolParamDef("string", "Listening timeout", false, "10")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        return runCatching {
            when (action) {
                "listen" -> {
                    val lang = params["language"]?.toString() ?: "zh-CN"
                    val timeoutSec = params["timeout_seconds"]?.toString()?.toLongOrNull() ?: 10
                    var result = ""
                    sttService.startListening(lang) { result = it }
                    withTimeout(timeoutSec * 1000) {
                        while (sttService.isListening.value || result.isEmpty()) { delay(200) }
                    }
                    if (result.isBlank()) ToolResult(definition.name, true, "(No speech detected)")
                    else ToolResult(definition.name, true, "Recognized: $result")
                }
                "speak" -> {
                    val text = params["text"]?.toString() ?: return@runCatching errorResult("Missing text")
                    ttsService.speak(text)
                    ToolResult(definition.name, true, "Speaking: ${text.take(100)}")
                }
                "speak_and_wait" -> {
                    val text = params["text"]?.toString() ?: return@runCatching errorResult("Missing text")
                    val ok = ttsService.speakAndWait(text)
                    ToolResult(definition.name, ok, if (ok) "Finished speaking" else "TTS error")
                }
                "stop_listen" -> { sttService.stopListening(); ToolResult(definition.name, true, "Stopped listening") }
                "stop_speak" -> { ttsService.stop(); ToolResult(definition.name, true, "Stopped speaking") }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
