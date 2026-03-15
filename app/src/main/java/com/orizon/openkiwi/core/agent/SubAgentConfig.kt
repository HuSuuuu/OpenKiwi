package com.orizon.openkiwi.core.agent

import kotlinx.serialization.Serializable

@Serializable
data class SubAgentConfig(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val systemPrompt: String = "",
    val modelConfigId: String = "",
    val enabledTools: List<String> = emptyList(),
    val permissionLevel: String = "NORMAL",
    val maxConcurrentTasks: Int = 1,
    val isPersistent: Boolean = false
)
