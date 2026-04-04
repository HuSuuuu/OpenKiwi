package com.orizon.openkiwi.core.skill

import com.orizon.openkiwi.data.local.dao.SkillDao
import com.orizon.openkiwi.data.local.entity.SkillEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class SkillDefinition(
    val id: String = "",
    val name: String,
    val description: String,
    val type: String,
    val category: String = "general",
    val version: String = "1.0",
    val steps: List<SkillStep> = emptyList(),
    val inputParams: Map<String, String> = emptyMap(),
    val outputDescription: String = ""
)

@Serializable
data class SkillStep(
    val toolName: String,
    val params: Map<String, String> = emptyMap(),
    val description: String = "",
    val onError: String = "stop"
)

/**
 * 持久化 **工作流技能**（多步工具链，Room + [SkillDefinition]）。
 *
 * 与 OpenClaw 生态的 `SKILL.md` 指令包不同；后者由 `OpenClawSkillRegistry`（`core.openclaw`）管理。
 */
class SkillManager(private val skillDao: SkillDao) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun getAllSkills(): Flow<List<SkillEntity>> = skillDao.getAllSkills()

    suspend fun getEnabledSkills(): List<SkillDefinition> =
        skillDao.getEnabledSkills().mapNotNull { parseSkill(it) }

    suspend fun getSkill(id: String): SkillDefinition? =
        skillDao.getSkill(id)?.let { parseSkill(it) }

    suspend fun createSkill(definition: SkillDefinition): String {
        val id = definition.id.ifBlank { UUID.randomUUID().toString() }
        val entity = SkillEntity(
            id = id, name = definition.name, description = definition.description,
            type = definition.type, category = definition.category, version = definition.version,
            definitionJson = json.encodeToString(SkillDefinition.serializer(), definition.copy(id = id)),
            isEnabled = true
        )
        skillDao.insertSkill(entity)
        return id
    }

    suspend fun updateSkill(definition: SkillDefinition) {
        val entity = skillDao.getSkill(definition.id) ?: return
        skillDao.updateSkill(entity.copy(
            name = definition.name, description = definition.description,
            type = definition.type, version = definition.version,
            definitionJson = json.encodeToString(SkillDefinition.serializer(), definition),
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun deleteSkill(id: String) {
        skillDao.getSkill(id)?.let { skillDao.deleteSkill(it) }
    }

    suspend fun toggleSkill(id: String, enabled: Boolean) {
        val entity = skillDao.getSkill(id) ?: return
        skillDao.updateSkill(entity.copy(isEnabled = enabled))
    }

    fun exportSkill(definition: SkillDefinition): String =
        json.encodeToString(SkillDefinition.serializer(), definition)

    fun importSkill(jsonStr: String): SkillDefinition? =
        runCatching { json.decodeFromString(SkillDefinition.serializer(), jsonStr) }.getOrNull()

    private fun parseSkill(entity: SkillEntity): SkillDefinition? =
        runCatching { json.decodeFromString(SkillDefinition.serializer(), entity.definitionJson) }.getOrNull()
}
