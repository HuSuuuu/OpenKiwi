package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.orizon.openkiwi.core.tool.*

class IntentTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "intent",
        description = "Launch Android intents: open URLs, deep links, system settings, share text, send emails, make calls, open maps, etc.",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: open_url, open_settings, share_text, dial, send_email, open_map, open_deeplink, view_file, open_app_settings", true),
            "url" to ToolParamDef("string", "URL or URI to open"),
            "text" to ToolParamDef("string", "Text content for sharing"),
            "phone" to ToolParamDef("string", "Phone number for dial"),
            "email" to ToolParamDef("string", "Email address"),
            "subject" to ToolParamDef("string", "Email/share subject"),
            "latitude" to ToolParamDef("string", "Map latitude"),
            "longitude" to ToolParamDef("string", "Map longitude"),
            "package_name" to ToolParamDef("string", "Target app package name"),
            "setting" to ToolParamDef("string", "System setting name (wifi, bluetooth, display, sound, location, airplane, accessibility, notification_listener)")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing action"
        )

        return runCatching {
            when (action) {
                "open_url" -> {
                    val url = params["url"]?.toString() ?: return@runCatching errorResult("Missing 'url'")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(toolName = definition.name, success = true, output = "Opened URL: $url")
                }
                "open_settings" -> {
                    val setting = params["setting"]?.toString() ?: "main"
                    val settingsAction = when (setting) {
                        "wifi" -> Settings.ACTION_WIFI_SETTINGS
                        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                        "display" -> Settings.ACTION_DISPLAY_SETTINGS
                        "sound" -> Settings.ACTION_SOUND_SETTINGS
                        "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                        "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                        "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                        "notification_listener" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                        "app_notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        "date" -> Settings.ACTION_DATE_SETTINGS
                        "locale" -> Settings.ACTION_LOCALE_SETTINGS
                        "security" -> Settings.ACTION_SECURITY_SETTINGS
                        "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                        "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                        "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                        else -> Settings.ACTION_SETTINGS
                    }
                    val intent = Intent(settingsAction).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                    ToolResult(toolName = definition.name, success = true, output = "Opened $setting settings")
                }
                "share_text" -> {
                    val text = params["text"]?.toString() ?: return@runCatching errorResult("Missing 'text'")
                    val subject = params["subject"]?.toString() ?: ""
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share via").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                    ToolResult(toolName = definition.name, success = true, output = "Opened share dialog")
                }
                "dial" -> {
                    val phone = params["phone"]?.toString() ?: return@runCatching errorResult("Missing 'phone'")
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(toolName = definition.name, success = true, output = "Opened dialer for: $phone")
                }
                "send_email" -> {
                    val email = params["email"]?.toString() ?: return@runCatching errorResult("Missing 'email'")
                    val subject = params["subject"]?.toString() ?: ""
                    val body = params["text"]?.toString() ?: ""
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(toolName = definition.name, success = true, output = "Opened email composer to: $email")
                }
                "open_map" -> {
                    val lat = params["latitude"]?.toString() ?: return@runCatching errorResult("Missing 'latitude'")
                    val lon = params["longitude"]?.toString() ?: return@runCatching errorResult("Missing 'longitude'")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(toolName = definition.name, success = true, output = "Opened map at ($lat, $lon)")
                }
                "open_deeplink" -> {
                    val url = params["url"]?.toString() ?: return@runCatching errorResult("Missing 'url'")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(toolName = definition.name, success = true, output = "Opened deep link: $url")
                }
                "open_app_settings" -> {
                    val pkg = params["package_name"]?.toString() ?: context.packageName
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(toolName = definition.name, success = true, output = "Opened app settings for: $pkg")
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { e ->
            ToolResult(toolName = definition.name, success = false, output = "", error = e.message)
        }
    }

    private fun errorResult(msg: String) = ToolResult(toolName = definition.name, success = false, output = "", error = msg)
}
