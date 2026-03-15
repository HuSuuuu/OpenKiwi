package com.orizon.openkiwi.core.model

import com.orizon.openkiwi.data.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ModelManager(private val modelRepository: ModelRepository) {

    fun getAllModels(): Flow<List<ModelConfig>> = modelRepository.getAllConfigs()

    suspend fun getModelForScene(scene: String): ModelConfig? {
        val configs = modelRepository.getAllConfigs().first()
        return configs.firstOrNull { scene in it.sceneTags }
            ?: configs.firstOrNull { it.isDefault }
            ?: configs.firstOrNull()
    }

    suspend fun getDefaultModel(): ModelConfig? = modelRepository.getDefaultConfig()

    suspend fun saveModel(config: ModelConfig): String = modelRepository.saveConfig(config)

    suspend fun deleteModel(config: ModelConfig) = modelRepository.deleteConfig(config)

    suspend fun setDefaultModel(id: String) = modelRepository.setDefault(id)
}
