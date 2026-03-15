package com.orizon.openkiwi.core.plugin

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File

/**
 * Dynamic plugin loader supporting APK/DEX plugin loading at runtime.
 * Plugins are loaded from the app's plugin directory.
 */
class DynamicPluginLoader(private val context: Context) {

    companion object {
        private const val TAG = "PluginLoader"
        private const val PLUGIN_INTERFACE_CLASS = "com.orizon.openkiwi.core.plugin.PluginInterface"
        private const val PLUGIN_ENTRY_META = "plugin_entry_class"
    }

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
        return if (file.exists()) {
            file.delete().also {
                Log.i(TAG, "Plugin uninstalled: $pluginFileName")
            }
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

    fun scanAndLoadPlugins(): List<PluginInterface> {
        val plugins = mutableListOf<PluginInterface>()

        for (file in listPluginFiles()) {
            val metaFile = File(file.parentFile, "${file.nameWithoutExtension}.meta")
            val entryClass = if (metaFile.exists()) {
                metaFile.readText().trim()
            } else {
                Log.w(TAG, "No .meta file for ${file.name}, skipping")
                continue
            }

            loadPlugin(file, entryClass)?.let { plugins.add(it) }
        }

        return plugins
    }

    fun createPluginMeta(pluginFileName: String, entryClassName: String) {
        val metaFile = File(pluginDir, "${pluginFileName.substringBeforeLast(".")}.meta")
        metaFile.writeText(entryClassName)
    }
}
