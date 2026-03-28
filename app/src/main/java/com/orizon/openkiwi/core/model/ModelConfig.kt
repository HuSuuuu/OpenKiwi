package com.orizon.openkiwi.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelConfig(
    val id: String = "",
    val name: String = "",
    val apiBaseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val topP: Double = 1.0,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val timeoutSeconds: Int = 60,
    val maxRetries: Int = 3,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val isDefault: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = true,
    val supportsStreaming: Boolean = true,
    val sceneTags: List<String> = emptyList(),
    val reasoningEffort: String = "low",
    val isSmallModel: Boolean = false,
    /**
     * 在 **Chat Completions** 请求中附加方舟联网声明：`tools[].type=function` 且 `function.name=web_search`。
     * 官方文档里 `type:web_search`、`max_keyword`、`sources`、`user_location` 属于 **Responses API**（`/responses`），
     * 与 chat/completions 不是同一路径；本开关**不会**把 `max_keyword` 等写入当前聊天请求。
     * 需在方舟控制台开通「联网内容插件」。与本地 `web_search` 工具互斥（同名会去重）。
     */
    val includeWebSearchTool: Boolean = false,
    /**
     * 为 true 时仅下发 web_search，不下发本地 function 工具（适配「联网插件与 FC 互斥」类限制）。
     */
    val webSearchExclusive: Boolean = false,
    /** LLM provider type: "openai" (default), "anthropic", "gemini". */
    val providerType: String = "openai",
    val maxToolIterations: Int = 10
)
