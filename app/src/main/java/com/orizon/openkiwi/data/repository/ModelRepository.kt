package com.orizon.openkiwi.data.repository

import com.orizon.openkiwi.core.model.ModelConfig
import com.orizon.openkiwi.data.local.dao.ModelConfigDao
import com.orizon.openkiwi.data.local.entity.ModelConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID

class ModelRepository(private val modelConfigDao: ModelConfigDao) {

    private val json = Json { ignoreUnknownKeys = true }

    fun getAllConfigs(): Flow<List<ModelConfig>> =
        modelConfigDao.getAllConfigs().map { entities ->
            entities.map { it.toModelConfig() }
        }

    suspend fun getAllConfigsOnce(): List<ModelConfig> =
        modelConfigDao.getAllConfigs().first().map { it.toModelConfig() }

    suspend fun getConfig(id: String): ModelConfig? =
        modelConfigDao.getConfig(id)?.toModelConfig()

    suspend fun getDefaultConfig(): ModelConfig? =
        modelConfigDao.getDefaultConfig()?.toModelConfig()

    suspend fun saveConfig(config: ModelConfig): String {
        val id = config.id.ifBlank { UUID.randomUUID().toString() }
        if (config.isDefault) {
            modelConfigDao.clearDefaultFlags()
        }
        modelConfigDao.insertConfig(config.copy(id = id).toEntity())
        return id
    }

    suspend fun deleteConfig(config: ModelConfig) {
        modelConfigDao.deleteConfig(config.toEntity())
    }

    suspend fun setDefault(id: String) {
        modelConfigDao.clearDefaultFlags()
        val config = modelConfigDao.getConfig(id) ?: return
        modelConfigDao.updateConfig(config.copy(isDefault = true))
    }

    private fun ModelConfigEntity.toModelConfig() = ModelConfig(
        id = id,
        name = name,
        apiBaseUrl = apiBaseUrl,
        apiKey = apiKey,
        modelName = modelName,
        maxTokens = maxTokens,
        temperature = temperature,
        topP = topP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        timeoutSeconds = timeoutSeconds,
        maxRetries = maxRetries,
        proxyHost = proxyHost,
        proxyPort = proxyPort,
        isDefault = isDefault,
        supportsVision = supportsVision,
        supportsTools = supportsTools,
        supportsStreaming = supportsStreaming,
        sceneTags = runCatching {
            json.decodeFromString<List<String>>(sceneTagsJson)
        }.getOrDefault(emptyList()),
        reasoningEffort = reasoningEffort,
        isSmallModel = isSmallModel,
        includeWebSearchTool = includeWebSearchTool,
        webSearchExclusive = webSearchExclusive,
        providerType = providerType,
        maxToolIterations = maxToolIterations
    )

    private fun ModelConfig.toEntity() = ModelConfigEntity(
        id = id,
        name = name,
        apiBaseUrl = apiBaseUrl,
        apiKey = apiKey,
        modelName = modelName,
        maxTokens = maxTokens,
        temperature = temperature,
        topP = topP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        timeoutSeconds = timeoutSeconds,
        maxRetries = maxRetries,
        proxyHost = proxyHost,
        proxyPort = proxyPort,
        isDefault = isDefault,
        supportsVision = supportsVision,
        supportsTools = supportsTools,
        supportsStreaming = supportsStreaming,
        sceneTagsJson = json.encodeToString(
            ListSerializer(String.serializer()),
            sceneTags
        ),
        reasoningEffort = reasoningEffort,
        isSmallModel = isSmallModel,
        includeWebSearchTool = includeWebSearchTool,
        webSearchExclusive = webSearchExclusive,
        providerType = providerType,
        maxToolIterations = maxToolIterations
    )
}
