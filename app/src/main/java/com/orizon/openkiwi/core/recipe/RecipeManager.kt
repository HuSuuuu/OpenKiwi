package com.orizon.openkiwi.core.recipe

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class RecipeManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val storeFile get() = File(context.filesDir, "user_recipes.json")

    @Serializable
    private data class Store(val recipes: List<AutomationRecipe> = emptyList())

    fun allRecipes(): List<AutomationRecipe> = RecipePresets.builtin() + loadUser()

    fun getById(id: String): AutomationRecipe? = allRecipes().find { it.id == id }

    fun getByName(name: String): AutomationRecipe? =
        allRecipes().find { it.name.equals(name.trim(), ignoreCase = true) }

    private fun loadUser(): List<AutomationRecipe> = runCatching {
        if (!storeFile.exists()) return@runCatching emptyList()
        json.decodeFromString(Store.serializer(), storeFile.readText()).recipes
    }.getOrDefault(emptyList())

    suspend fun saveUserRecipe(
        name: String,
        description: String,
        steps: List<String>,
        targetPackage: String? = null
    ) = withContext(Dispatchers.IO) {
        val id = "user_" + UUID.randomUUID().toString().take(8)
        val recipe = AutomationRecipe(
            id = id,
            name = name.trim(),
            description = description.trim(),
            steps = steps.map { it.trim() }.filter { it.isNotBlank() },
            targetPackage = targetPackage?.trim()?.ifBlank { null },
            preset = false
        )
        val cur = loadUser().toMutableList()
        cur.add(recipe)
        storeFile.writeText(json.encodeToString(Store.serializer(), Store(cur)))
    }

    suspend fun deleteUserRecipe(id: String) = withContext(Dispatchers.IO) {
        val cur = loadUser().filter { it.id != id }
        storeFile.writeText(json.encodeToString(Store.serializer(), Store(cur)))
    }
}
