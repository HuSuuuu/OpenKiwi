package com.orizon.openkiwi.core.tool.builtin

import android.graphics.Rect
import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.service.KiwiAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GUIOperationTool : Tool {
    override val definition = ToolDefinition(
        name = "gui_operation",
        description = "Perform GUI operations on Android screen. Use action='batch' with 'steps' JSON array for multiple actions in one call.",
        category = ToolCategory.GUI.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action to perform, or 'batch' for multiple steps", true),
            "steps" to ToolParamDef("string", "JSON array of steps for batch mode, e.g. [{\"action\":\"click_text\",\"text\":\"OK\"},{\"action\":\"scroll_down\"}]"),
            "text" to ToolParamDef("string", "Text to find/click/type"),
            "view_id" to ToolParamDef("string", "View resource ID"),
            "x" to ToolParamDef("string", "X coordinate"),
            "y" to ToolParamDef("string", "Y coordinate"),
            "x2" to ToolParamDef("string", "End X for swipe"),
            "y2" to ToolParamDef("string", "End Y for swipe"),
            "duration" to ToolParamDef("string", "Duration in ms", false, "200")
        ),
        requiredParams = listOf("action")
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun getService(): KiwiAccessibilityService? = KiwiAccessibilityService.instance

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val service = getService() ?: return ToolResult(
            toolName = definition.name, success = false, output = "",
            error = "Accessibility Service not running. Enable it in Settings > Accessibility > OpenKiwi."
        )

        val action = params["action"]?.toString() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing action"
        )

        return runCatching {
            if (action == "batch") {
                executeBatch(service, params)
            } else {
                executeSingle(service, action, params)
            }
        }.getOrElse { e ->
            ToolResult(toolName = definition.name, success = false, output = "", error = e.message)
        }
    }

    private suspend fun executeBatch(service: KiwiAccessibilityService, params: Map<String, Any?>): ToolResult {
        val stepsJson = params["steps"]?.toString() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing 'steps' for batch mode"
        )

        val steps = runCatching {
            json.decodeFromString<JsonArray>(stepsJson)
        }.getOrElse {
            return ToolResult(toolName = definition.name, success = false, output = "", error = "Invalid JSON in 'steps': ${it.message}")
        }

        val results = StringBuilder()
        var allSuccess = true

        for ((i, step) in steps.withIndex()) {
            val obj = step.jsonObject
            val stepAction = obj["action"]?.jsonPrimitive?.content ?: continue
            val stepParams = obj.mapValues { (_, v) -> v.jsonPrimitive.content as Any? }

            val result = executeSingle(service, stepAction, stepParams)
            results.appendLine("Step ${i + 1} [$stepAction]: ${if (result.success) "OK" else "FAIL"} - ${result.output.take(100)}")
            if (!result.success) allSuccess = false

            if (i < steps.size - 1) delay(80)
        }

        return ToolResult(
            toolName = definition.name,
            success = allSuccess,
            output = results.toString()
        )
    }

    private suspend fun executeSingle(
        service: KiwiAccessibilityService,
        action: String,
        params: Map<String, Any?>
    ): ToolResult = when (action) {
        "get_screen" -> withContext(Dispatchers.Main) { getScreenInfo(service) }
        "get_focused" -> withContext(Dispatchers.Main) { getFocusedElement(service) }
        "click_text" -> withContext(Dispatchers.Main) { clickByText(service, params) }
        "click_id" -> withContext(Dispatchers.Main) { clickById(service, params) }
        "click_xy" -> withContext(Dispatchers.Main) { tapAtCoords(service, params) }
        "long_press_xy" -> withContext(Dispatchers.Main) { longPressAtCoords(service, params) }
        "set_text" -> withContext(Dispatchers.Main) { setText(service, params) }
        "swipe" -> withContext(Dispatchers.Main) { swipe(service, params) }
        "scroll_down" -> withContext(Dispatchers.Main) {
            service.performGesture(540f, 1500f, 540f, 500f, 250)
            delay(200)
            service.invalidateNodeCache()
            ToolResult(toolName = definition.name, success = true, output = "Scrolled down")
        }
        "scroll_up" -> withContext(Dispatchers.Main) {
            service.performGesture(540f, 500f, 540f, 1500f, 250)
            delay(200)
            service.invalidateNodeCache()
            ToolResult(toolName = definition.name, success = true, output = "Scrolled up")
        }
        "press_back" -> withContext(Dispatchers.Main) {
            service.pressBack(); delay(150)
            service.invalidateNodeCache()
            ToolResult(toolName = definition.name, success = true, output = "Pressed back")
        }
        "press_home" -> withContext(Dispatchers.Main) {
            service.pressHome(); delay(150)
            service.invalidateNodeCache()
            ToolResult(toolName = definition.name, success = true, output = "Pressed home")
        }
        "press_recents" -> withContext(Dispatchers.Main) {
            service.pressRecents(); delay(150)
            service.invalidateNodeCache()
            ToolResult(toolName = definition.name, success = true, output = "Opened recents")
        }
        "open_notifications" -> withContext(Dispatchers.Main) {
            service.openNotifications(); delay(200)
            service.invalidateNodeCache()
            ToolResult(toolName = definition.name, success = true, output = "Opened notification shade")
        }
        "open_quick_settings" -> withContext(Dispatchers.Main) {
            service.openQuickSettings(); delay(200)
            service.invalidateNodeCache()
            ToolResult(toolName = definition.name, success = true, output = "Opened quick settings")
        }
        "find_text" -> withContext(Dispatchers.Main) { findByText(service, params) }
        else -> ToolResult(toolName = definition.name, success = false, output = "", error = "Unknown action: $action")
    }

    private fun getScreenInfo(service: KiwiAccessibilityService): ToolResult {
        val nodes = service.getScreenNodes()
        if (nodes.isEmpty()) {
            return ToolResult(toolName = definition.name, success = true, output = "No UI nodes found on screen")
        }

        val interactable = nodes.filter {
            it.text.isNotBlank() || it.contentDescription.isNotBlank() || it.isClickable || it.isEditable
        }

        val sb = StringBuilder()
        sb.appendLine("UI: ${interactable.size} interactive / ${nodes.size} total")

        for (node in interactable.take(60)) {
            val cls = node.className.substringAfterLast('.')
            val label = when {
                node.text.isNotBlank() -> "\"${node.text.take(60)}\""
                node.contentDescription.isNotBlank() -> "desc=\"${node.contentDescription.take(40)}\""
                else -> ""
            }
            val flags = buildList {
                if (node.isClickable) add("C")
                if (node.isEditable) add("E")
                if (node.isScrollable) add("S")
            }.joinToString("")
            val b = node.bounds
            sb.appendLine("$cls $label [$flags] (${b.centerX()},${b.centerY()})")
        }

        return ToolResult(toolName = definition.name, success = true, output = sb.toString())
    }

    private fun getFocusedElement(service: KiwiAccessibilityService): ToolResult {
        val nodes = service.getScreenNodes()
        val editable = nodes.firstOrNull { it.isEditable }
        return if (editable != null) {
            val b = editable.bounds
            ToolResult(
                toolName = definition.name, success = true,
                output = "Focused: \"${editable.text.take(60)}\" id=${editable.viewId} (${b.centerX()},${b.centerY()})"
            )
        } else {
            ToolResult(toolName = definition.name, success = true, output = "No editable element currently focused")
        }
    }

    private suspend fun clickByText(service: KiwiAccessibilityService, params: Map<String, Any?>): ToolResult {
        val text = params["text"]?.toString() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing 'text'"
        )
        val node = service.findNodeByText(text) ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Text '$text' not found"
        )
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!service.performClick(node)) {
            service.performTap(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        delay(150)
        service.invalidateNodeCache()
        return ToolResult(toolName = definition.name, success = true, output = "Clicked '$text' (${rect.centerX()},${rect.centerY()})")
    }

    private suspend fun clickById(service: KiwiAccessibilityService, params: Map<String, Any?>): ToolResult {
        val viewId = params["view_id"]?.toString() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing 'view_id'"
        )
        val node = service.findNodeById(viewId) ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "ID '$viewId' not found"
        )
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!service.performClick(node)) {
            service.performTap(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        delay(150)
        service.invalidateNodeCache()
        return ToolResult(toolName = definition.name, success = true, output = "Clicked '$viewId' (${rect.centerX()},${rect.centerY()})")
    }

    private suspend fun tapAtCoords(service: KiwiAccessibilityService, params: Map<String, Any?>): ToolResult {
        val x = params["x"]?.toString()?.toFloatOrNull() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Invalid 'x'"
        )
        val y = params["y"]?.toString()?.toFloatOrNull() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Invalid 'y'"
        )
        service.performTap(x, y)
        delay(150)
        service.invalidateNodeCache()
        return ToolResult(toolName = definition.name, success = true, output = "Tapped ($x,$y)")
    }

    private suspend fun longPressAtCoords(service: KiwiAccessibilityService, params: Map<String, Any?>): ToolResult {
        val x = params["x"]?.toString()?.toFloatOrNull() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Invalid 'x'"
        )
        val y = params["y"]?.toString()?.toFloatOrNull() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Invalid 'y'"
        )
        val duration = params["duration"]?.toString()?.toLongOrNull() ?: 600
        service.performGesture(x, y, x, y, duration)
        delay(duration + 100)
        service.invalidateNodeCache()
        return ToolResult(toolName = definition.name, success = true, output = "Long pressed ($x,$y) ${duration}ms")
    }

    private suspend fun setText(service: KiwiAccessibilityService, params: Map<String, Any?>): ToolResult {
        val text = params["text"]?.toString() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing 'text'"
        )
        val viewId = params["view_id"]?.toString()

        val node = when {
            viewId != null -> service.findNodeById(viewId)
            else -> service.findFocusedInput() ?: service.findEditableNode()
        }

        if (node != null) {
            node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
            delay(50)
            if (service.performSetText(node, text)) {
                delay(100)
                service.invalidateNodeCache()
                return ToolResult(toolName = definition.name, success = true, output = "Set text: '$text'")
            }
            node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE)
            delay(100)
            service.invalidateNodeCache()
            return ToolResult(toolName = definition.name, success = true, output = "Set text (paste): '$text'")
        }
        return ToolResult(toolName = definition.name, success = false, output = "", error = "No editable element found")
    }

    private suspend fun swipe(service: KiwiAccessibilityService, params: Map<String, Any?>): ToolResult {
        val x1 = params["x"]?.toString()?.toFloatOrNull() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Invalid 'x'"
        )
        val y1 = params["y"]?.toString()?.toFloatOrNull() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Invalid 'y'"
        )
        val x2 = params["x2"]?.toString()?.toFloatOrNull() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Invalid 'x2'"
        )
        val y2 = params["y2"]?.toString()?.toFloatOrNull() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Invalid 'y2'"
        )
        val duration = params["duration"]?.toString()?.toLongOrNull() ?: 200
        service.performGesture(x1, y1, x2, y2, duration)
        delay(duration + 100)
        service.invalidateNodeCache()
        return ToolResult(toolName = definition.name, success = true, output = "Swiped ($x1,$y1)->($x2,$y2)")
    }

    private fun findByText(service: KiwiAccessibilityService, params: Map<String, Any?>): ToolResult {
        val text = params["text"]?.toString() ?: return ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing 'text'"
        )
        val node = service.findNodeByText(text) ?: return ToolResult(
            toolName = definition.name, success = true, output = "'$text' not found on screen"
        )
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return ToolResult(
            toolName = definition.name, success = true,
            output = "Found '$text': ${node.className?.toString()?.substringAfterLast('.') ?: "?"} (${rect.centerX()},${rect.centerY()}) clickable=${node.isClickable}"
        )
    }
}
