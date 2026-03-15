package com.orizon.openkiwi.core.plugin

import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PluginManager(private val toolRegistry: ToolRegistry) {

    private val plugins = mutableMapOf<String, PluginInterface>()
    private val _pluginInfos = MutableStateFlow<List<PluginInfo>>(emptyList())
    val pluginInfos: StateFlow<List<PluginInfo>> = _pluginInfos.asStateFlow()

    fun loadPlugin(plugin: PluginInterface): Boolean {
        if (plugins.containsKey(plugin.id)) return false
        plugin.onLoad()
        plugins[plugin.id] = plugin
        plugin.getTools().forEach { toolRegistry.register(it) }
        refreshInfos()
        return true
    }

    fun unloadPlugin(pluginId: String): Boolean {
        val plugin = plugins.remove(pluginId) ?: return false
        plugin.getTools().forEach { toolRegistry.unregister(it.definition.name) }
        plugin.onUnload()
        refreshInfos()
        return true
    }

    fun getPlugin(pluginId: String): PluginInterface? = plugins[pluginId]

    fun listPlugins(): List<PluginInfo> = plugins.values.map {
        PluginInfo(id = it.id, name = it.name, version = it.version, description = it.description, isEnabled = true, requiredPermissions = it.requiredPermissions)
    }

    private fun refreshInfos() { _pluginInfos.value = listPlugins() }
}
