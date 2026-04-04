package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.security.BiometricAuthManager
import com.orizon.openkiwi.core.security.BiometricStatus
import com.orizon.openkiwi.core.tool.*

class BiometricGateTool(
    private val biometricAuthManager: BiometricAuthManager
) : Tool {
    override val definition = ToolDefinition(
        name = "biometric_gate",
        description = "Check device biometric/lock-screen authentication status. Use before sensitive operations to verify user identity.",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.SENSITIVE.name,
        parameters = mapOf(
            "action" to ToolParamDef(
                "string", "Action to perform", true,
                enumValues = listOf("check_status", "is_secure")
            )
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        return runCatching {
            when (action) {
                "check_status" -> {
                    val status = biometricAuthManager.canAuthenticate()
                    val secure = biometricAuthManager.isDeviceSecure()
                    val sb = buildString {
                        appendLine("Biometric status: $status")
                        appendLine("Device secure (PIN/pattern/password set): $secure")
                        when (status) {
                            BiometricStatus.AVAILABLE -> appendLine("Ready for authentication prompts.")
                            BiometricStatus.NOT_ENROLLED -> appendLine("No lock screen configured — cannot authenticate.")
                            BiometricStatus.NO_HARDWARE -> appendLine("Device lacks biometric hardware.")
                            BiometricStatus.UNAVAILABLE -> appendLine("Biometric hardware unavailable.")
                            BiometricStatus.UNKNOWN -> appendLine("Unable to determine biometric capability.")
                        }
                    }
                    ToolResult(definition.name, true, sb)
                }
                "is_secure" -> {
                    val secure = biometricAuthManager.isDeviceSecure()
                    ToolResult(definition.name, true, "Device secure: $secure")
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }

    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
