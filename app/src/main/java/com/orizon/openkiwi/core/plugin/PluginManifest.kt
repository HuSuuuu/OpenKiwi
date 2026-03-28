package com.orizon.openkiwi.core.plugin

import kotlinx.serialization.Serializable

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val entryClass: String,
    val minSdkVersion: Int = 1,
    val permissions: List<String> = emptyList(),
    val tools: List<PluginToolDeclaration> = emptyList()
)

@Serializable
data class PluginToolDeclaration(
    val name: String,
    val description: String = ""
)
