package com.orizon.openkiwi.data.repository

import com.orizon.openkiwi.core.mcp.McpServerConfig
import com.orizon.openkiwi.core.mcp.TransportType
import com.orizon.openkiwi.data.local.dao.McpServerConfigDao
import com.orizon.openkiwi.data.local.entity.McpServerConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID

class McpServerRepository(private val dao: McpServerConfigDao) {

    private val json = Json { ignoreUnknownKeys = true }

    fun getAllConfigs(): Flow<List<McpServerConfig>> =
        dao.getAllConfigs().map { entities -> entities.map { it.toDomain() } }

    suspend fun getAllConfigsOnce(): List<McpServerConfig> =
        dao.getAllConfigsOnce().map { it.toDomain() }

    suspend fun getEnabledConfigs(): List<McpServerConfig> =
        dao.getEnabledConfigs().map { it.toDomain() }

    suspend fun saveConfig(config: McpServerConfig): String {
        val id = config.id.ifBlank { UUID.randomUUID().toString() }
        dao.insertConfig(config.copy(id = id).toEntity())
        return id
    }

    suspend fun deleteConfig(id: String) {
        dao.deleteById(id)
    }

    suspend fun toggleEnabled(id: String, enabled: Boolean) {
        val entity = dao.getConfig(id) ?: return
        dao.updateConfig(entity.copy(isEnabled = enabled))
    }

    private fun McpServerConfigEntity.toDomain() = McpServerConfig(
        id = id,
        name = name,
        transportType = runCatching { TransportType.valueOf(transportType.uppercase()) }
            .getOrDefault(TransportType.SSE),
        url = url,
        command = command,
        args = runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), args)
        }.getOrDefault(emptyList()),
        env = runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), env)
        }.getOrDefault(emptyMap()),
        isEnabled = isEnabled,
        createdAt = createdAt
    )

    private fun McpServerConfig.toEntity() = McpServerConfigEntity(
        id = id,
        name = name,
        transportType = transportType.name.lowercase(),
        url = url,
        command = command,
        args = json.encodeToString(ListSerializer(String.serializer()), args),
        env = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), env),
        isEnabled = isEnabled,
        createdAt = createdAt
    )
}
