package com.orizon.openkiwi.core.plugin

import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PluginManager(private val toolRegistry: ToolRegistry) {

    private val plugins = mutableMapOf<String, PluginInterface>()
    private val disabledPlugins = mutableSetOf<String>()
    private val pluginManifests = mutableMapOf<String, PluginManifest>()
    private val _pluginInfos = MutableStateFlow<List<PluginInfo>>(emptyList())
    val pluginInfos: StateFlow<List<PluginInfo>> = _pluginInfos.asStateFlow()

    fun loadPlugin(plugin: PluginInterface, manifest: PluginManifest? = null): Boolean {
        if (plugins.containsKey(plugin.id)) return false
        plugin.onLoad()
        plugins[plugin.id] = plugin
        manifest?.let { pluginManifests[plugin.id] = it }
        if (plugin.id !in disabledPlugins) {
            plugin.getTools().forEach { toolRegistry.register(it) }
        }
        refreshInfos()
        return true
    }

    fun unloadPlugin(pluginId: String): Boolean {
        val plugin = plugins.remove(pluginId) ?: return false
        plugin.getTools().forEach { toolRegistry.unregister(it.definition.name) }
        plugin.onUnload()
        pluginManifests.remove(pluginId)
        disabledPlugins.remove(pluginId)
        refreshInfos()
        return true
    }

    fun enablePlugin(pluginId: String): Boolean {
        val plugin = plugins[pluginId] ?: return false
        if (pluginId !in disabledPlugins) return true
        disabledPlugins.remove(pluginId)
        plugin.getTools().forEach { toolRegistry.register(it) }
        refreshInfos()
        return true
    }

    fun disablePlugin(pluginId: String): Boolean {
        val plugin = plugins[pluginId] ?: return false
        if (pluginId in disabledPlugins) return true
        disabledPlugins.add(pluginId)
        plugin.getTools().forEach { toolRegistry.unregister(it.definition.name) }
        refreshInfos()
        return true
    }

    fun isEnabled(pluginId: String): Boolean = pluginId !in disabledPlugins

    fun getPlugin(pluginId: String): PluginInterface? = plugins[pluginId]

    fun getManifest(pluginId: String): PluginManifest? = pluginManifests[pluginId]

    fun listPlugins(): List<PluginInfo> = plugins.values.map {
        PluginInfo(
            id = it.id, name = it.name, version = it.version,
            description = it.description,
            isEnabled = it.id !in disabledPlugins,
            requiredPermissions = it.requiredPermissions
        )
    }

    private fun refreshInfos() { _pluginInfos.value = listPlugins() }
}
