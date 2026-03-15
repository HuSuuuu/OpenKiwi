package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.orizon.openkiwi.core.tool.*

class PhoneSmsTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "phone_sms",
        description = "Make phone calls and send/read SMS messages",
        category = ToolCategory.COMMUNICATION.name,
        permissionLevel = PermissionLevel.SENSITIVE.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: call, dial, send_sms, read_sms", true,
                enumValues = listOf("call", "dial", "send_sms", "read_sms")),
            "phone" to ToolParamDef("string", "Phone number"),
            "message" to ToolParamDef("string", "SMS message text"),
            "limit" to ToolParamDef("string", "Max SMS to read", false, "10")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        return when (action) {
            "call" -> {
                val phone = params["phone"]?.toString() ?: return errorResult("Missing phone")
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }.fold(
                    onSuccess = { ToolResult(definition.name, true, "Calling $phone") },
                    onFailure = { ToolResult(definition.name, false, "", it.message) }
                )
            }
            "dial" -> {
                val phone = params["phone"]?.toString() ?: return errorResult("Missing phone")
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult(definition.name, true, "Opened dialer for $phone")
            }
            "send_sms" -> {
                val phone = params["phone"]?.toString() ?: return errorResult("Missing phone")
                val message = params["message"]?.toString() ?: return errorResult("Missing message")
                runCatching {
                    @Suppress("DEPRECATION")
                    val smsManager = SmsManager.getDefault()
                    smsManager.sendTextMessage(phone, null, message, null, null)
                }.fold(
                    onSuccess = { ToolResult(definition.name, true, "SMS sent to $phone") },
                    onFailure = { ToolResult(definition.name, false, "", "Failed to send SMS: ${it.message}") }
                )
            }
            "read_sms" -> {
                val limit = params["limit"]?.toString()?.toIntOrNull() ?: 10
                runCatching {
                    val cursor = context.contentResolver.query(
                        Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"),
                        null, null, "date DESC"
                    )
                    val sb = StringBuilder("SMS Inbox:\n")
                    cursor?.use {
                        var count = 0
                        while (it.moveToNext() && count < limit) {
                            val addr = it.getString(0)
                            val body = it.getString(1)
                            val date = it.getLong(2)
                            sb.appendLine("From: $addr | ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(date))}")
                            sb.appendLine("  $body")
                            count++
                        }
                    }
                    ToolResult(definition.name, true, sb.toString())
                }.getOrElse { ToolResult(definition.name, false, "", it.message) }
            }
            else -> errorResult("Unknown action: $action")
        }
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
