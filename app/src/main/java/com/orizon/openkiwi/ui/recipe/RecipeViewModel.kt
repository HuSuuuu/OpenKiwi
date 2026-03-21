package com.orizon.openkiwi.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.core.recipe.AutomationRecipe
import com.orizon.openkiwi.core.recipe.RecipeExecutor
import com.orizon.openkiwi.core.recipe.RecipeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecipeViewModel(
    private val recipeManager: RecipeManager,
    private val recipeExecutor: RecipeExecutor
) : ViewModel() {

    private val _recipes = MutableStateFlow(recipeManager.allRecipes())
    val recipes: StateFlow<List<AutomationRecipe>> = _recipes.asStateFlow()

    private val _runLog = MutableStateFlow<String?>(null)
    val runLog: StateFlow<String?> = _runLog.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _recipes.value = recipeManager.allRecipes()
    }

    fun runRecipe(recipe: AutomationRecipe) {
        viewModelScope.launch {
            _runLog.value = "执行中…"
            _runLog.value = runCatching { recipeExecutor.run(recipe) }.fold(
                onSuccess = { it },
                onFailure = { e -> "错误: ${e.message}" }
            )
        }
    }

    fun addRecipe(name: String, description: String, stepsText: String) {
        viewModelScope.launch {
            val steps = stepsText.lines().map { it.trim() }.filter { it.isNotBlank() }
            recipeManager.saveUserRecipe(name, description, steps, null)
            refresh()
        }
    }

    fun deleteRecipe(recipe: AutomationRecipe) {
        if (recipe.preset) return
        viewModelScope.launch {
            recipeManager.deleteUserRecipe(recipe.id)
            refresh()
        }
    }

    fun clearLog() {
        _runLog.value = null
    }

    class Factory(
        private val recipeManager: RecipeManager,
        private val recipeExecutor: RecipeExecutor
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecipeViewModel(recipeManager, recipeExecutor) as T
        }
    }
}
