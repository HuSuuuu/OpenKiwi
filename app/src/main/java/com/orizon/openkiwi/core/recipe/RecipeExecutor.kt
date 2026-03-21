package com.orizon.openkiwi.core.recipe

import com.orizon.openkiwi.core.gui.GuiAgent

class RecipeExecutor(private val guiAgent: GuiAgent) {

    suspend fun run(recipe: AutomationRecipe): String {
        if (recipe.steps.isEmpty()) return "配方没有步骤"
        val sb = StringBuilder()
        recipe.steps.forEachIndexed { index, step ->
            sb.appendLine("=== 步骤 ${index + 1}/${recipe.steps.size}: $step")
            val res = guiAgent.executeTask(goal = step, onProgress = {})
            sb.appendLine(res)
            sb.appendLine()
        }
        return sb.toString().trim()
    }
}
