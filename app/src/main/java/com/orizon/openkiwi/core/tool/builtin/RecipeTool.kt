package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.recipe.RecipeExecutor
import com.orizon.openkiwi.core.recipe.RecipeManager
import com.orizon.openkiwi.core.tool.PermissionLevel
import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolCategory
import com.orizon.openkiwi.core.tool.ToolDefinition
import com.orizon.openkiwi.core.tool.ToolParamDef
import com.orizon.openkiwi.core.tool.ToolResult
import com.orizon.openkiwi.service.KiwiAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecipeTool(
    private val recipeManager: RecipeManager,
    private val recipeExecutor: RecipeExecutor
) : Tool {

    override val definition = ToolDefinition(
        name = "run_recipe",
        description = """Run a saved GUI automation recipe (preset or user-defined).
Recipes are sequences of natural-language goals executed by the GuiAgent, one after another.
Use recipe_id from the recipe list, or recipe_name for an exact name match.""",
        category = ToolCategory.GUI.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "recipe_id" to ToolParamDef("string", "Recipe id (e.g. preset_open_wechat)", false),
            "recipe_name" to ToolParamDef("string", "Exact recipe display name", false)
        ),
        requiredParams = emptyList(),
        timeoutMs = 900_000L
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        if (KiwiAccessibilityService.instance == null) {
            return@withContext ToolResult(
                toolName = definition.name,
                success = false,
                output = "",
                error = "无障碍服务未开启，无法执行配方。"
            )
        }
        val id = params["recipe_id"]?.toString()?.trim()?.ifBlank { null }
        val name = params["recipe_name"]?.toString()?.trim()?.ifBlank { null }
        val recipe = when {
            !id.isNullOrBlank() -> recipeManager.getById(id)
            !name.isNullOrBlank() -> recipeManager.getByName(name)
            else -> null
        }
        if (recipe == null) {
            return@withContext ToolResult(
                toolName = definition.name,
                success = false,
                output = "",
                error = "未找到配方，请提供 recipe_id 或 recipe_name。"
            )
        }
        val out = runCatching { recipeExecutor.run(recipe) }.getOrElse { e ->
            return@withContext ToolResult(
                toolName = definition.name,
                success = false,
                output = "",
                error = e.message ?: "recipe failed"
            )
        }
        ToolResult(
            toolName = definition.name,
            success = true,
            output = out,
            error = null
        )
    }
}
