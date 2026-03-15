package com.orizon.openkiwi.core.plugin

import com.orizon.openkiwi.core.tool.Tool

interface PluginInterface {
    val id: String
    val name: String
    val version: String
    val description: String
    val requiredPermissions: List<String>

    fun onLoad()
    fun onUnload()
    fun getTools(): List<Tool>
}

data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val isEnabled: Boolean = false,
    val requiredPermissions: List<String> = emptyList()
)
