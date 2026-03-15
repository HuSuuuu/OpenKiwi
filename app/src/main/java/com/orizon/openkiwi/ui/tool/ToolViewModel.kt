package com.orizon.openkiwi.ui.tool

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolRegistry
import com.orizon.openkiwi.data.local.dao.CustomToolDao
import com.orizon.openkiwi.data.local.entity.CustomToolEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ToolInfo(
    val name: String,
    val description: String,
    val category: String,
    val isBuiltIn: Boolean,
    val isEnabled: Boolean,
    val paramsJson: String = "{}",
    val script: String = "",
    val createdAt: Long = 0
)

class ToolViewModel(
    private val customToolDao: CustomToolDao,
    private val toolRegistry: ToolRegistry
) : ViewModel() {

    val customTools: StateFlow<List<CustomToolEntity>> = customToolDao
        .getAllCustomTools()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _builtInTools = MutableStateFlow<List<ToolInfo>>(emptyList())

    val allTools: StateFlow<List<ToolInfo>> = combine(
        _builtInTools, customTools
    ) { builtIn, custom ->
        builtIn + custom.map { it.toToolInfo() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        refreshBuiltIn()
    }

    fun refreshBuiltIn() {
        val tools = toolRegistry.getAllTools()
        _builtInTools.value = tools
            .filter { it.definition.category != "CUSTOM" }
            .map { it.toToolInfo() }
    }

    fun toggleCustomTool(name: String, enabled: Boolean) {
        viewModelScope.launch {
            val entity = customToolDao.getByName(name) ?: return@launch
            customToolDao.update(entity.copy(isEnabled = enabled))
        }
    }

    fun deleteCustomTool(name: String) {
        viewModelScope.launch {
            customToolDao.delete(name)
            toolRegistry.unregister(name)
        }
    }

    private fun CustomToolEntity.toToolInfo() = ToolInfo(
        name = name,
        description = description,
        category = "CUSTOM",
        isBuiltIn = false,
        isEnabled = isEnabled,
        paramsJson = paramsJson,
        script = script,
        createdAt = createdAt
    )

    private fun Tool.toToolInfo() = ToolInfo(
        name = definition.name,
        description = definition.description,
        category = definition.category,
        isBuiltIn = true,
        isEnabled = definition.isEnabled
    )

    class Factory(
        private val customToolDao: CustomToolDao,
        private val toolRegistry: ToolRegistry
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ToolViewModel(customToolDao, toolRegistry) as T
    }
}
