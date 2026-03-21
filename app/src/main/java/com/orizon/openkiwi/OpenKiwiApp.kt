package com.orizon.openkiwi

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.orizon.openkiwi.core.model.ModelConfig
import com.orizon.openkiwi.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenKiwiApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        createNotificationChannels()
        seedPresetModelsIfNeeded()
        warmupPythonAsync()
    }

    private fun warmupPythonAsync() {
        appScope.launch {
            if (!Python.isStarted()) {
                try {
                    Python.start(AndroidPlatform(this@OpenKiwiApp))
                    Log.i("OpenKiwiApp", "Embedded Python started successfully")
                } catch (t: Throwable) {
                    // Never block app startup on Python runtime init.
                    Log.e("OpenKiwiApp", "Failed to start embedded Python", t)
                }
            }
        }
    }

    private fun seedPresetModelsIfNeeded() {
        val prefs = getSharedPreferences("openkiwi_init", MODE_PRIVATE)
        if (prefs.getBoolean("models_seeded", false)) return

        appScope.launch {
            presetModels.forEach { container.modelRepository.saveConfig(it) }
            prefs.edit().putBoolean("models_seeded", true).apply()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val agentChannel = NotificationChannel(
                CHANNEL_AGENT_SERVICE,
                "Agent Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "OpenKiwi agent background service"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important alerts and confirmations"
            }

            val pendingNotesChannel = NotificationChannel(
                CHANNEL_PENDING_NOTES,
                "待处理通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "有需要关注的通知时提醒"
                setShowBadge(true)
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(agentChannel)
            nm.createNotificationChannel(alertChannel)
            nm.createNotificationChannel(pendingNotesChannel)
        }
    }

    companion object {
        const val CHANNEL_AGENT_SERVICE = "agent_service"
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_PENDING_NOTES = "pending_notes"
        const val NOTIFICATION_ID_PENDING_NOTES = 9001

        lateinit var instance: OpenKiwiApp
            private set

        private val presetModels = listOf(
            ModelConfig(
                id = "preset_doubao_seed2pro",
                name = "豆包 Seed 2.0 Pro",
                apiBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
                apiKey = "",
                modelName = "ep-your-endpoint-id",
                maxTokens = 32768,
                temperature = 0.7,
                timeoutSeconds = 120,
                maxRetries = 3,
                isDefault = true,
                supportsVision = true,
                supportsTools = true,
                supportsStreaming = true,
                sceneTags = listOf("general", "code", "vision")
            ),
            ModelConfig(
                id = "preset_qwen35",
                name = "Qwen 3.5",
                apiBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                apiKey = "",
                modelName = "qwen-plus",
                maxTokens = 32768,
                temperature = 0.7,
                timeoutSeconds = 120,
                maxRetries = 3,
                isDefault = false,
                supportsVision = true,
                supportsTools = true,
                supportsStreaming = true,
                sceneTags = listOf("general", "code")
            )
        )
    }
}
