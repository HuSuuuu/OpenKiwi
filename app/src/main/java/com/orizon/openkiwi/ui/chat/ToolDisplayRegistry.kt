package com.orizon.openkiwi.ui.chat

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 将工具名 + 参数映射为可读摘要（emoji、标题、动词、详情），配置见 [tool-display.json]。
 */
object ToolDisplayRegistry {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class ToolDisplayConfig(
        val version: Int = 1,
        val fallback: ToolDisplayFallbackSpec? = null,
        val tools: Map<String, ToolDisplaySpec>? = null
    )

    @Serializable
    private data class ToolDisplayFallbackSpec(
        val emoji: String? = null,
        val detailKeys: List<String>? = null
    )

    @Serializable
    private data class ToolDisplaySpec(
        val emoji: String? = null,
        val title: String? = null,
        val label: String? = null,
        val detailKeys: List<String>? = null,
        val actions: Map<String, ActionDisplaySpec>? = null
    )

    @Serializable
    private data class ActionDisplaySpec(
        val label: String? = null,
        val detailKeys: List<String>? = null
    )

    private var cached: ToolDisplayConfig? = null

    private fun loadConfig(context: Context): ToolDisplayConfig {
        cached?.let { return it }
        val cfg = runCatching {
            context.assets.open("tool-display.json").bufferedReader().use { json.decodeFromString<ToolDisplayConfig>(it.readText()) }
        }.getOrNull() ?: ToolDisplayConfig()
        cached = cfg
        return cfg
    }

    private fun titleFromName(name: String): String =
        name.replace('_', ' ').split(' ').joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private fun normalizeVerb(v: String?): String? =
        v?.trim()?.takeIf { it.isNotEmpty() }?.replace('_', ' ')

    private fun firstValue(args: JsonObject?, keys: List<String>): String? {
        if (args == null || keys.isEmpty()) return null
        for (key in keys) {
            val v = if (key.contains('.')) getNested(args, key) else args[key]
            val s = jsonElementToShortString(v)
            if (s.isNotBlank()) return shortenPath(s)
        }
        return null
    }

    private fun getNested(root: JsonObject, path: String): JsonElement? {
        val parts = path.split('.')
        var cur: JsonElement? = root
        for (p in parts) {
            cur = (cur as? JsonObject)?.get(p) ?: return null
        }
        return cur
    }

    private fun jsonElementToShortString(el: JsonElement?): String {
        if (el == null) return ""
        return when (el) {
            is JsonPrimitive -> el.content
            else -> el.toString().take(200)
        }
    }

    private fun shortenPath(s: String): String {
        val home = System.getProperty("user.home") ?: return s
        return if (s.startsWith(home)) "~" + s.removePrefix(home) else s
    }

    data class ToolDisplaySummary(
        val name: String,
        val emoji: String,
        val title: String,
        val label: String,
        val verb: String?,
        val detail: String?
    ) {
        val detailLine: String?
            get() = when {
                !verb.isNullOrBlank() && !detail.isNullOrBlank() -> "$verb · $detail"
                !verb.isNullOrBlank() -> verb
                !detail.isNullOrBlank() -> detail
                else -> null
            }
    }

    fun resolve(context: Context, name: String?, args: JsonObject?, meta: String? = null): ToolDisplaySummary {
        val trimmedName = name?.trim().orEmpty().ifEmpty { "tool" }
        val key = trimmedName.lowercase()
        val config = loadConfig(context)
        val spec = config.tools?.get(key)
        val fallback = config.fallback

        val emoji = spec?.emoji ?: fallback?.emoji ?: "🧩"
        val title = spec?.title ?: titleFromName(trimmedName)
        val label = spec?.label ?: trimmedName

        val actionRaw = (args?.get("action") as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
        val actionSpec = actionRaw?.let { spec?.actions?.get(it) }
        val verb = normalizeVerb(actionSpec?.label ?: actionRaw)

        var detail: String? = null
        val detailKeys = actionSpec?.detailKeys ?: spec?.detailKeys ?: fallback?.detailKeys ?: emptyList()
        if (detailKeys.isNotEmpty()) {
            detail = firstValue(args, detailKeys)
        }
        if (detail == null) {
            detail = meta?.takeIf { it.isNotBlank() }
        }
        if (detail != null) {
            detail = shortenPath(detail)
        }

        return ToolDisplaySummary(
            name = trimmedName,
            emoji = emoji,
            title = title,
            label = label,
            verb = verb,
            detail = detail
        )
    }

    /** 测试或热更新配置时调用 */
    fun clearCache() {
        cached = null
    }
}
