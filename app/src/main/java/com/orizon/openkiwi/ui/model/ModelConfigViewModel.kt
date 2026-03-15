package com.orizon.openkiwi.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.core.model.ModelConfig
import com.orizon.openkiwi.core.model.ModelManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ModelConfigUiState(
    val models: List<ModelConfig> = emptyList(),
    val editingModel: ModelConfig? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false
)

class ModelConfigViewModel(private val modelManager: ModelManager) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelConfigUiState())
    val uiState: StateFlow<ModelConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modelManager.getAllModels().collect { models ->
                _uiState.value = _uiState.value.copy(models = models)
            }
        }
    }

    fun startEditing(model: ModelConfig? = null) {
        _uiState.value = _uiState.value.copy(
            editingModel = model ?: ModelConfig(),
            isEditing = true
        )
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(editingModel = null, isEditing = false)
    }

    fun updateEditingModel(model: ModelConfig) {
        _uiState.value = _uiState.value.copy(editingModel = model)
    }

    fun saveModel() {
        val model = _uiState.value.editingModel ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            modelManager.saveModel(model)
            _uiState.value = _uiState.value.copy(
                editingModel = null,
                isEditing = false,
                isSaving = false
            )
        }
    }

    fun deleteModel(model: ModelConfig) {
        viewModelScope.launch { modelManager.deleteModel(model) }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { modelManager.setDefaultModel(id) }
    }

    class Factory(private val modelManager: ModelManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ModelConfigViewModel(modelManager) as T
        }
    }
}
