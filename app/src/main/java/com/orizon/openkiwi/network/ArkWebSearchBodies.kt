package com.orizon.openkiwi.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

/**
 * 火山方舟 **Responses API**（`POST .../responses`）中 `tools[]` 里 `web_search` 的 JSON 形态。
 *
 * **与 OpenKiwi 当前聊天路径无关**：应用内对话使用 **Chat Completions**（`/chat/completions`），
 * 方舟在该接口下要求 `tools[].type=function`，联网通过 `function.name=web_search` 声明，
 * **不能**在 chat 请求里直接塞 `{"type":"web_search","max_keyword":2}`（否则会 400）。
 *
 * 若将来接入 Responses API，可使用 [responsesApiWebSearchTool] 构造工具项。
 *
 * @see <a href="https://www.volcengine.com/docs/82379/1756990">联网搜索 Web Search</a>
 */
object ArkWebSearchBodies {

    /**
     * 对应官方示例：`{ "type": "web_search", "max_keyword": 2, ... }`
     */
    fun responsesApiWebSearchTool(
        maxKeyword: Int? = null,
        limit: Int? = null,
        sources: List<String> = emptyList(),
        userLocation: JsonObject? = null
    ): JsonObject = buildJsonObject {
        put("type", "web_search")
        maxKeyword?.let { put("max_keyword", it) }
        limit?.let { put("limit", it) }
        if (sources.isNotEmpty()) {
            putJsonArray("sources") { sources.forEach { add(it) } }
        }
        userLocation?.let { put("user_location", it) }
    }

    /**
     * `user_location` 子对象（approximate）
     */
    fun userLocationApproximate(country: String, region: String = "", city: String = ""): JsonObject =
        buildJsonObject {
            put("type", "approximate")
            put("country", country)
            if (region.isNotBlank()) put("region", region)
            if (city.isNotBlank()) put("city", city)
        }
}
