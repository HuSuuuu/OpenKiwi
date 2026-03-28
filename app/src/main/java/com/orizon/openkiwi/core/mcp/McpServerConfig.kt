package com.orizon.openkiwi.core.mcp

import kotlinx.serialization.Serializable

@Serializable
data class McpServerConfig(
    val id: String = "",
    val name: String = "",
    val transportType: TransportType = TransportType.SSE,
    val url: String = "",
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class TransportType {
    SSE, STDIO
}
