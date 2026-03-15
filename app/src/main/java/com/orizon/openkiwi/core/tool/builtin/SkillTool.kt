package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.skill.SkillDefinition
import com.orizon.openkiwi.core.skill.SkillExecutor
import com.orizon.openkiwi.core.skill.SkillManager
import com.orizon.openkiwi.core.skill.SkillStep
import com.orizon.openkiwi.core.tool.*

class SkillTool(private val skillManager: SkillManager, private val skillExecutor: SkillExecutor) : Tool {
    override val definition = ToolDefinition(
        name = "skill",
        description = "Manage and execute reusable skills. Skills are multi-step workflows that chain multiple tools together.",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: list, run, create, delete, export, import", true,
                enumValues = listOf("list", "run", "create", "delete", "export", "import")),
            "skill_id" to ToolParamDef("string", "Skill ID"),
            "name" to ToolParamDef("string", "Skill name (for create)"),
            "description" to ToolParamDef("string", "Skill description (for create)"),
            "steps_json" to ToolParamDef("string", "JSON array of steps (for create)"),
            "json_data" to ToolParamDef("string", "JSON skill data (for import)"),
            "input_params" to ToolParamDef("string", "Comma-separated key=value pairs for skill execution")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        return runCatching {
            when (action) {
                "list" -> {
                    val skills = skillManager.getEnabledSkills()
                    if (skills.isEmpty()) ToolResult(definition.name, true, "No skills configured")
                    else {
                        val sb = StringBuilder("Skills (${skills.size}):\n")
                        skills.forEach { s -> sb.appendLine("  [${s.id.take(8)}] ${s.name} (${s.type}) — ${s.description.take(60)}") }
                        ToolResult(definition.name, true, sb.toString())
                    }
                }
                "run" -> {
                    val skillId = params["skill_id"]?.toString() ?: return@runCatching errorResult("Missing skill_id")
                    val skill = skillManager.getSkill(skillId) ?: return@runCatching errorResult("Skill not found: $skillId")
                    val inputStr = params["input_params"]?.toString() ?: ""
                    val inputs = inputStr.split(",").filter { it.contains("=") }.associate {
                        val (k, v) = it.split("=", limit = 2)
                        k.trim() to v.trim()
                    }
                    val result = skillExecutor.executeSkill(skill, inputs)
                    if (result.success) ToolResult(definition.name, true, "Skill '${skill.name}' completed:\n${result.output}")
                    else ToolResult(definition.name, false, "", "Skill failed: ${result.error}")
                }
                "create" -> {
                    val name = params["name"]?.toString() ?: return@runCatching errorResult("Missing name")
                    val desc = params["description"]?.toString() ?: ""
                    val def = SkillDefinition(name = name, description = desc, type = "flow")
                    val id = skillManager.createSkill(def)
                    ToolResult(definition.name, true, "Created skill '$name' (id=$id)")
                }
                "delete" -> {
                    val id = params["skill_id"]?.toString() ?: return@runCatching errorResult("Missing skill_id")
                    skillManager.deleteSkill(id)
                    ToolResult(definition.name, true, "Deleted skill: $id")
                }
                "export" -> {
                    val id = params["skill_id"]?.toString() ?: return@runCatching errorResult("Missing skill_id")
                    val skill = skillManager.getSkill(id) ?: return@runCatching errorResult("Skill not found")
                    ToolResult(definition.name, true, skillManager.exportSkill(skill))
                }
                "import" -> {
                    val jsonData = params["json_data"]?.toString() ?: return@runCatching errorResult("Missing json_data")
                    val skill = skillManager.importSkill(jsonData) ?: return@runCatching errorResult("Invalid skill JSON")
                    val id = skillManager.createSkill(skill)
                    ToolResult(definition.name, true, "Imported skill '${skill.name}' (id=$id)")
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
