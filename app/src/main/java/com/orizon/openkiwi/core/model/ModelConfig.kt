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
    val isSmallModel: Boolean = false
)
