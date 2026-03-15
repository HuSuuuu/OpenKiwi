package com.orizon.openkiwi.core.security

import com.orizon.openkiwi.core.memory.MemoryManager
import com.orizon.openkiwi.core.skill.SkillManager
import com.orizon.openkiwi.data.preferences.UserPreferences
import com.orizon.openkiwi.data.repository.ModelRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExportedConfig(
    val version: Int = 2,
    val models: List<ExportedModel> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val skills: List<ExportedSkill> = emptyList(),
    val memories: List<ExportedMemory> = emptyList()
)

@Serializable
data class ExportedModel(
    val name: String,
    val apiBaseUrl: String,
    val modelName: String,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val supportsTools: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsVision: Boolean = false,
    val isDefault: Boolean = false,
    val sceneTags: List<String> = emptyList(),
    val reasoningEffort: String = "low"
)

@Serializable
data class ExportedSkill(
    val name: String,
    val description: String,
    val type: String,
    val category: String = "",
    val version: String = "1.0",
    val definitionJson: String = ""
)

@Serializable
data class ExportedMemory(
    val type: String,
    val key: String,
    val content: String,
    val category: String = "",
    val scope: String = "",
    val importance: Float = 0.5f
)

class ConfigExporter(
    private val modelRepository: ModelRepository,
    private val userPreferences: UserPreferences,
    private val skillManager: SkillManager? = null,
    private val memoryManager: MemoryManager? = null
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun exportConfig(includeApiKeys: Boolean = false, includeAll: Boolean = false): String {
        val models = modelRepository.getAllConfigsOnce().map { config ->
            ExportedModel(
                name = config.name,
                apiBaseUrl = config.apiBaseUrl,
                modelName = config.modelName,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                supportsTools = config.supportsTools,
                supportsStreaming = config.supportsStreaming,
                supportsVision = config.supportsVision,
                isDefault = config.isDefault,
                sceneTags = config.sceneTags,
                reasoningEffort = config.reasoningEffort
            )
        }

        val settings = mapOf(
            "theme" to userPreferences.themeMode.first(),
            "streaming" to userPreferences.enableStreaming.first().toString(),
            "contextSize" to userPreferences.maxContextMessages.first().toString(),
            "confirmDangerous" to userPreferences.confirmDangerousOps.first().toString(),
            "auditLog" to userPreferences.enableAuditLog.first().toString()
        )

        val skills = if (includeAll && skillManager != null) {
            skillManager.getEnabledSkills().map {
                ExportedSkill(it.name, it.description, it.type, it.category, it.version)
            }
        } else emptyList()

        val memories = if (includeAll && memoryManager != null) {
            memoryManager.searchMemories("", limit = 500).map {
                ExportedMemory(it.type.name, it.key, it.content, it.category, it.scope, it.importance)
            }
        } else emptyList()

        val config = ExportedConfig(
            models = models, settings = settings,
            skills = skills, memories = memories
        )
        return json.encodeToString(ExportedConfig.serializer(), config)
    }

    suspend fun exportEncrypted(): String {
        val plainJson = exportConfig(includeApiKeys = false, includeAll = true)
        return KeystoreManager.encrypt(plainJson)
    }

    fun importConfig(jsonStr: String): ExportedConfig? =
        runCatching { json.decodeFromString(ExportedConfig.serializer(), jsonStr) }.getOrNull()

    fun importEncrypted(encrypted: String): ExportedConfig? {
        val decrypted = runCatching { KeystoreManager.decrypt(encrypted) }.getOrNull() ?: return null
        return importConfig(decrypted)
    }
}
