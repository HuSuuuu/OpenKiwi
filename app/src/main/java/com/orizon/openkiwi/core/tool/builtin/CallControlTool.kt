package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.service.CallControlService

class CallControlTool(private val callService: CallControlService) : Tool {

    override val definition = ToolDefinition(
        name = "call_control",
        description = "Full telephony control: answer/hangup calls, send DTMF tones, toggle speaker/mute, check call state",
        category = ToolCategory.COMMUNICATION.name,
        permissionLevel = PermissionLevel.SENSITIVE.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: answer, hangup, dtmf, speaker_on, speaker_off, mute, unmute, state", required = true),
            "tone" to ToolParamDef("string", "DTMF tone character (0-9, *, #) - required for dtmf action")
        ),
        requiredParams = listOf("action"),
        returnDescription = "Call control result",
        timeoutMs = 10_000
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return ToolResult("call_control", false, "", "Missing action")
        val tone = params["tone"]?.toString()

        return when (action.lowercase()) {
            "answer" -> {
                val ok = callService.answerCall()
                ToolResult("call_control", ok, if (ok) "Call answered" else "Failed to answer")
            }
            "hangup" -> {
                val ok = callService.hangupCall()
                ToolResult("call_control", ok, if (ok) "Call ended" else "Failed to hang up")
            }
            "dtmf" -> {
                val ch = tone?.firstOrNull() ?: return ToolResult("call_control", false, "", "Missing tone")
                val ok = callService.sendDTMF(ch)
                ToolResult("call_control", ok, if (ok) "DTMF sent: $ch" else "Failed")
            }
            "speaker_on" -> { callService.toggleSpeakerphone(true); ToolResult("call_control", true, "Speaker on") }
            "speaker_off" -> { callService.toggleSpeakerphone(false); ToolResult("call_control", true, "Speaker off") }
            "mute" -> { callService.toggleMute(true); ToolResult("call_control", true, "Muted") }
            "unmute" -> { callService.toggleMute(false); ToolResult("call_control", true, "Unmuted") }
            "state" -> ToolResult("call_control", true, "Call state: ${callService.getCallState()}")
            else -> ToolResult("call_control", false, "", "Unknown action: $action")
        }
    }
}
