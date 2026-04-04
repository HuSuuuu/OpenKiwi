package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.nfc.NfcAdapter
import com.orizon.openkiwi.core.device.NfcSessionManager
import com.orizon.openkiwi.core.tool.*

class NfcTool(
    private val context: Context,
    private val nfcSessionManager: NfcSessionManager
) : Tool {
    override val definition = ToolDefinition(
        name = "nfc",
        description = "NFC operations: check adapter status, read last detected tag (NDEF text/URI/records), write NDEF text or URI to a tag. User must hold tag near device.",
        category = ToolCategory.DEVICE.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef(
                "string", "NFC action to perform", true,
                enumValues = listOf("status", "read_tag", "write_text", "write_uri")
            ),
            "text" to ToolParamDef("string", "Text content for write_text"),
            "uri" to ToolParamDef("string", "URI for write_uri (e.g. https://example.com)")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        val adapter = NfcAdapter.getDefaultAdapter(context)

        return runCatching {
            when (action) {
                "status" -> {
                    if (adapter == null) {
                        return@runCatching ToolResult(definition.name, true, "NFC is not available on this device")
                    }
                    val sb = buildString {
                        appendLine("NFC enabled: ${adapter.isEnabled}")
                        val lastTag = nfcSessionManager.lastTag.value
                        if (lastTag != null) {
                            appendLine("Last tag ID: ${lastTag.id}")
                            appendLine("Last tag tech: ${lastTag.techList.joinToString(", ")}")
                            appendLine("Last tag time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(lastTag.timestamp)}")
                        } else {
                            appendLine("No tag detected yet — hold a tag near the device.")
                        }
                    }
                    ToolResult(definition.name, true, sb)
                }

                "read_tag" -> {
                    if (adapter == null) return@runCatching errorResult("NFC not available")
                    if (!adapter.isEnabled) return@runCatching errorResult("NFC is disabled — enable it in system settings")
                    val tagInfo = nfcSessionManager.lastTag.value
                        ?: return@runCatching errorResult("No tag detected — hold a tag near the device and try again")

                    val sb = buildString {
                        appendLine("Tag ID: ${tagInfo.id}")
                        appendLine("Technologies: ${tagInfo.techList.joinToString(", ") { it.substringAfterLast('.') }}")
                        if (tagInfo.ndefMessages.isNotEmpty()) {
                            appendLine("NDEF Records (${tagInfo.ndefMessages.size}):")
                            tagInfo.rawNdefRecords.forEachIndexed { i, rec ->
                                appendLine("  [$i] TNF=${rec.tnf} Type=${rec.type}")
                                appendLine("      Payload: ${rec.payload}")
                                appendLine("      Hex: ${rec.payloadHex}")
                            }
                        } else {
                            appendLine("No NDEF data on this tag.")
                        }
                    }
                    ToolResult(definition.name, true, sb)
                }

                "write_text" -> {
                    if (adapter == null) return@runCatching errorResult("NFC not available")
                    val text = params["text"]?.toString()
                        ?: return@runCatching errorResult("Missing 'text' parameter")
                    nfcSessionManager.writeNdefText(text).fold(
                        onSuccess = { ToolResult(definition.name, true, "Written NDEF text to tag: $text") },
                        onFailure = { errorResult("Write failed: ${it.message}") }
                    )
                }

                "write_uri" -> {
                    if (adapter == null) return@runCatching errorResult("NFC not available")
                    val uri = params["uri"]?.toString()
                        ?: return@runCatching errorResult("Missing 'uri' parameter")
                    nfcSessionManager.writeNdefUri(uri).fold(
                        onSuccess = { ToolResult(definition.name, true, "Written NDEF URI to tag: $uri") },
                        onFailure = { errorResult("Write failed: ${it.message}") }
                    )
                }

                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }

    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
