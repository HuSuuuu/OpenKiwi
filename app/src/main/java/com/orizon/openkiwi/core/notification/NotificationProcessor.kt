package com.orizon.openkiwi.core.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.orizon.openkiwi.MainActivity
import com.orizon.openkiwi.OpenKiwiApp
import com.orizon.openkiwi.R
import com.orizon.openkiwi.core.model.ChatCompletionRequest
import com.orizon.openkiwi.core.model.ChatMessage
import com.orizon.openkiwi.core.model.ChatRole
import com.orizon.openkiwi.data.local.dao.NoteDao
import com.orizon.openkiwi.data.local.entity.NoteEntity
import com.orizon.openkiwi.data.preferences.UserPreferences
import com.orizon.openkiwi.data.repository.ModelRepository
import com.orizon.openkiwi.network.OpenAIApiClient
import com.orizon.openkiwi.service.NotificationInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NotificationProcessor(
    private val apiClient: OpenAIApiClient,
    private val modelRepository: ModelRepository,
    private val noteDao: NoteDao,
    private val userPreferences: UserPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "NotificationProcessor"
        private val SYSTEM_PROMPT = """
你是一个手机通知分析助手。分析用户收到的通知，用纯JSON回复（不要markdown）：
{"importance":0,"summary":"一句话摘要","action":"建议操作，无则留空"}
importance: 0=可忽略(广告/推广), 1=一般信息, 2=需要关注(验证码/重要消息/支付/日程)
""".trimIndent()
    }

    fun process(info: NotificationInfo) {
        scope.launch {
            try {
                val enabled = userPreferences.notificationProcessing.first()
                if (!enabled) return@launch

                val modelId = userPreferences.notificationModelId.first()
                if (modelId.isBlank()) return@launch

                val config = modelRepository.getConfig(modelId) ?: return@launch

                val userMsg = "来源: ${info.packageName}\n标题: ${info.title}\n内容: ${info.text}"
                val request = ChatCompletionRequest(
                    model = config.modelName,
                    messages = listOf(
                        ChatMessage(role = ChatRole.SYSTEM, content = SYSTEM_PROMPT),
                        ChatMessage(role = ChatRole.USER, content = userMsg)
                    ),
                    temperature = 0.1,
                    maxTokens = 150
                )

                val result = apiClient.chatCompletion(config.apiBaseUrl, config.apiKey, request)
                val reply = result.getOrNull()?.choices?.firstOrNull()?.message?.content ?: ""
                val analysis = parseAnalysis(reply)

                val note = NoteEntity(
                    sourcePackage = info.packageName,
                    sourceTitle = info.title,
                    sourceContent = info.text,
                    summary = analysis.summary,
                    category = info.category,
                    status = if (analysis.importance >= 2) "pending" else "processed",
                    importance = analysis.importance,
                    suggestedAction = analysis.action
                )
                noteDao.insertNote(note)
                if (note.status == "pending") {
                    postBadgeNotification(note.summary.ifBlank { info.title })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process notification", e)
                noteDao.insertNote(NoteEntity(
                    sourcePackage = info.packageName,
                    sourceTitle = info.title,
                    sourceContent = info.text,
                    status = "pending",
                    category = info.category
                ))
                postBadgeNotification(info.title)
            }
        }
    }

    private suspend fun postBadgeNotification(latestSummary: String) {
        val pendingCount = noteDao.getPendingCount().first()
        if (pendingCount <= 0) return

        val ctx = OpenKiwiApp.instance
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(ctx, OpenKiwiApp.CHANNEL_PENDING_NOTES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$pendingCount 条待处理通知")
            .setContentText(latestSummary)
            .setNumber(pendingCount)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(OpenKiwiApp.NOTIFICATION_ID_PENDING_NOTES, notification)
    }

    fun clearBadge() {
        val ctx = OpenKiwiApp.instance
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(OpenKiwiApp.NOTIFICATION_ID_PENDING_NOTES)
    }

    private fun parseAnalysis(raw: String): AnalysisResult {
        return runCatching {
            val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            json.decodeFromString<AnalysisResult>(cleaned)
        }.getOrDefault(AnalysisResult(importance = 1, summary = raw.take(100), action = ""))
    }

    @Serializable
    private data class AnalysisResult(
        val importance: Int = 0,
        val summary: String = "",
        val action: String = ""
    )
}
