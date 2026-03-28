package com.orizon.openkiwi.core.plugin

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import kotlinx.serialization.json.Json
import java.io.File

data class LoadedPluginInfo(
    val plugin: PluginInterface,
    val manifest: PluginManifest?
)

/**
 * Dynamic plugin loader supporting APK/DEX plugin loading at runtime.
 * Plugins are loaded from the app's plugin directory.
 * Supports both legacy .meta files and the new plugin.json manifest format.
 */
class DynamicPluginLoader(private val context: Context) {

    companion object {
        private const val TAG = "PluginLoader"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val pluginDir: File by lazy {
        File(context.filesDir, "plugins").also { it.mkdirs() }
    }
    private val dexOutputDir: File by lazy {
        File(context.codeCacheDir, "plugin_dex").also { it.mkdirs() }
    }

    fun getPluginDirectory(): File = pluginDir

    fun listPluginFiles(): List<File> {
        return pluginDir.listFiles()?.filter {
            it.extension in listOf("apk", "dex", "jar")
        } ?: emptyList()
    }

    fun installPlugin(sourceFile: File): File? {
        return try {
            val target = File(pluginDir, sourceFile.name)
            sourceFile.copyTo(target, overwrite = true)
            Log.i(TAG, "Plugin installed: ${target.name}")
            target
        } catch (e: Exception) {
            Log.e(TAG, "Plugin install failed", e)
            null
        }
    }

    fun uninstallPlugin(pluginFileName: String): Boolean {
        val file = File(pluginDir, pluginFileName)
        val jsonFile = File(pluginDir, "${pluginFileName.substringBeforeLast(".")}.json")
        val metaFile = File(pluginDir, "${pluginFileName.substringBeforeLast(".")}.meta")
        jsonFile.delete()
        metaFile.delete()
        return if (file.exists()) {
            file.delete().also { Log.i(TAG, "Plugin uninstalled: $pluginFileName") }
        } else false
    }

    fun loadPlugin(pluginFile: File, entryClassName: String): PluginInterface? {
        if (!pluginFile.exists()) {
            Log.e(TAG, "Plugin file not found: ${pluginFile.absolutePath}")
            return null
        }

        return try {
            val classLoader = DexClassLoader(
                pluginFile.absolutePath,
                dexOutputDir.absolutePath,
                null,
                context.classLoader
            )

            val pluginClass = classLoader.loadClass(entryClassName)

            if (!PluginInterface::class.java.isAssignableFrom(pluginClass)) {
                Log.e(TAG, "$entryClassName does not implement PluginInterface")
                return null
            }

            val constructor = pluginClass.getConstructor()
            val instance = constructor.newInstance() as PluginInterface
            Log.i(TAG, "Loaded plugin: ${instance.name} v${instance.version}")
            instance
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Plugin class not found: $entryClassName", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin: ${pluginFile.name}", e)
            null
        }
    }

    fun readManifest(pluginFile: File): PluginManifest? {
        val jsonFile = File(pluginFile.parentFile, "${pluginFile.nameWithoutExtension}.json")
        if (!jsonFile.exists()) return null
        return runCatching {
            json.decodeFromString(PluginManifest.serializer(), jsonFile.readText())
        }.onFailure {
            Log.w(TAG, "Failed to parse plugin.json for ${pluginFile.name}", it)
        }.getOrNull()
    }

    fun scanAndLoadPlugins(): List<PluginInterface> {
        val plugins = mutableListOf<PluginInterface>()

        for (file in listPluginFiles()) {
            val manifest = readManifest(file)
            val entryClass = if (manifest != null) {
                manifest.entryClass
            } else {
                val metaFile = File(file.parentFile, "${file.nameWithoutExtension}.meta")
                if (metaFile.exists()) {
                    metaFile.readText().trim()
                } else {
                    Log.w(TAG, "No plugin.json or .meta for ${file.name}, skipping")
                    continue
                }
            }

            loadPlugin(file, entryClass)?.let { plugins.add(it) }
        }

        return plugins
    }

    fun scanWithManifests(): List<LoadedPluginInfo> {
        val result = mutableListOf<LoadedPluginInfo>()
        for (file in listPluginFiles()) {
            val manifest = readManifest(file)
            val entryClass = manifest?.entryClass
                ?: File(file.parentFile, "${file.nameWithoutExtension}.meta")
                    .takeIf { it.exists() }?.readText()?.trim()
                ?: continue

            loadPlugin(file, entryClass)?.let {
                result.add(LoadedPluginInfo(it, manifest))
            }
        }
        return result
    }

    fun createPluginMeta(pluginFileName: String, entryClassName: String) {
        val metaFile = File(pluginDir, "${pluginFileName.substringBeforeLast(".")}.meta")
        metaFile.writeText(entryClassName)
    }

    fun createPluginManifest(manifest: PluginManifest) {
        val jsonFile = File(pluginDir, "${manifest.id}.json")
        jsonFile.writeText(json.encodeToString(PluginManifest.serializer(), manifest))
    }
}
