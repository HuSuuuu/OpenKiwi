package com.orizon.openkiwi.ui.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.core.agent.AgentEngine
import com.orizon.openkiwi.core.agent.ProactiveMessageBus
import com.orizon.openkiwi.core.clipboard.ClipboardQuickBus
import com.orizon.openkiwi.core.widget.WidgetActionBus
import com.orizon.openkiwi.core.canvas.CanvasActionBus
import com.orizon.openkiwi.core.voice.VoiceWakeCommandBus
import com.orizon.openkiwi.core.agent.THINKING_MARKER
import com.orizon.openkiwi.core.gui.GuiAgent
import com.orizon.openkiwi.core.model.ChatRole
import com.orizon.openkiwi.data.repository.ChatRepository
import com.orizon.openkiwi.data.repository.ModelRepository
import com.orizon.openkiwi.ui.components.ArtifactUiModel
import com.orizon.openkiwi.ui.components.MessageUiModel
import com.orizon.openkiwi.util.DocumentTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.JsonObject

data class ToolCallStatus(val name: String, val status: String, val args: JsonObject? = null)

data class ChatUiState(
    val messages: List<MessageUiModel> = emptyList(),
    val isProcessing: Boolean = false,
    val currentSessionId: String? = null,
    val sessionTitle: String = "New Chat",
    val streamingContent: String = "",
    val streamingThinking: String = "",
    val error: String? = null,
    val isListening: Boolean = false,
    val activeToolCalls: List<ToolCallStatus> = emptyList(),
    val draftText: String = "",
    val proactiveHint: String? = null
)

class ChatViewModel(
    private val agentEngine: AgentEngine,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private var pendingImageUri: Uri? = null
    private var pendingFileUri: Uri? = null
    private var pendingVideoUri: Uri? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val sessions = chatRepository.getAllSessions()

    private var messageObserverJob: Job? = null
    private var sendJob: Job? = null
    private val draftBySession = mutableMapOf<String, String>()

    init {
        viewModelScope.launch { resumeOrCreateSession() }
        viewModelScope.launch {
            VoiceWakeCommandBus.commands.collect { cmd ->
                if (cmd.isNotBlank()) sendMessage(cmd)
            }
        }
        viewModelScope.launch {
            ClipboardQuickBus.events.collect { (action, text) ->
                if (text.isBlank()) return@collect
                val msg = when (action) {
                    "analyze" -> "请简要分析以下剪贴板内容：\n```\n${text.take(8000)}\n```"
                    "search" -> "请对以下内容做要点摘要，并说明可能相关的搜索关键词：\n${text.take(6000)}"
                    "translate" -> "请翻译下面内容（若主要为中文则译为英文，否则译为中文）：\n${text.take(6000)}"
                    else -> text
                }
                sendMessage(msg)
            }
        }
        viewModelScope.launch {
            WidgetActionBus.prompts.collect { p ->
                if (p.isNotBlank()) sendMessage(p)
            }
        }
        viewModelScope.launch {
            CanvasActionBus.prompts.collect { p ->
                if (p.isNotBlank()) sendMessage(p)
            }
        }
        viewModelScope.launch {
            ProactiveMessageBus.messages.collect { proactive ->
                val currentSession = _uiState.value.currentSessionId
                if (proactive.sessionId != null && proactive.sessionId == currentSession) {
                    observeMessages(currentSession)
                } else if (proactive.sessionId != null) {
                    _uiState.value = _uiState.value.copy(
                        proactiveHint = "[${proactive.sourceName}] ${proactive.content.take(80)}"
                    )
                }
            }
        }
    }

    private suspend fun resumeOrCreateSession() {
        val allSessions = chatRepository.getAllSessionsOnce()
        if (allSessions.isNotEmpty()) {
            val latest = allSessions.first()
            _uiState.value = ChatUiState(
                currentSessionId = latest.id,
                sessionTitle = latest.title,
                draftText = draftBySession[latest.id].orEmpty()
            )
            observeMessages(latest.id)
        } else {
            val sessionId = chatRepository.createSession()
            _uiState.value = ChatUiState(currentSessionId = sessionId, draftText = "")
            observeMessages(sessionId)
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val currentId = _uiState.value.currentSessionId
            if (currentId != null) {
                draftBySession[currentId] = _uiState.value.draftText
                val messages = chatRepository.getMessagesOnce(currentId)
                if (messages.isEmpty()) return@launch
            }
            val sessionId = chatRepository.createSession()
            _uiState.value = ChatUiState(currentSessionId = sessionId, draftText = draftBySession[sessionId].orEmpty())
            observeMessages(sessionId)
        }
    }

    fun switchSession(sessionId: String) {
        viewModelScope.launch {
            val session = chatRepository.getSession(sessionId) ?: return@launch
            _uiState.value.currentSessionId?.let { draftBySession[it] = _uiState.value.draftText }
            _uiState.value = _uiState.value.copy(
                currentSessionId = sessionId,
                sessionTitle = session.title,
                messages = emptyList(),
                streamingContent = "",
                error = null,
                draftText = draftBySession[sessionId].orEmpty()
            )
            observeMessages(sessionId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_uiState.value.currentSessionId == sessionId) {
                createNewSession()
            }
        }
    }

    private fun observeMessages(sessionId: String) {
        messageObserverJob?.cancel()
        messageObserverJob = viewModelScope.launch {
            chatRepository.getMessages(sessionId).collect { messages ->
                val visible = messages.filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }
                _uiState.value = _uiState.value.copy(
                    messages = visible.map { msg ->
                        MessageUiModel(
                            id = msg.messageId,
                            role = (msg.role ?: ChatRole.ASSISTANT).name,
                            content = msg.content ?: "",
                            turnId = msg.turnId,
                            artifacts = msg.artifacts.map {
                                ArtifactUiModel(
                                    id = it.id,
                                    path = it.filePath,
                                    displayName = it.displayName,
                                    mimeType = it.mimeType,
                                    sizeBytes = it.sizeBytes
                                )
                            }
                        )
                    }
                )
            }
        }
    }

    fun updateDraft(text: String) {
        _uiState.value = _uiState.value.copy(draftText = text)
        _uiState.value.currentSessionId?.let { draftBySession[it] = text }
    }

    fun setImageAttachment(uri: Uri?) { pendingImageUri = uri }
    fun setFileAttachment(uri: Uri?) { pendingFileUri = uri }
    fun setVideoAttachment(uri: Uri?) { pendingVideoUri = uri }

    fun sendMessage(content: String) {
        val sessionId = _uiState.value.currentSessionId ?: return
        val imageUri = pendingImageUri
        val fileUri = pendingFileUri
        val videoUri = pendingVideoUri
        pendingImageUri = null
        pendingFileUri = null
        pendingVideoUri = null

        if (content.isBlank() && imageUri == null && fileUri == null && videoUri == null) return

        sendJob = viewModelScope.launch {
            // Clear draft immediately for chat-like UX.
            updateDraft("")
            _uiState.value = _uiState.value.copy(
                isProcessing = true, error = null,
                streamingContent = "", streamingThinking = "", activeToolCalls = emptyList()
            )

            var finalContent = content

            // Offload heavy file IO from main thread to avoid UI jank.
            if (fileUri != null && appContext != null) {
                val fileResult = withContext(Dispatchers.IO) { readFileContent(appContext, fileUri) }
                if (fileResult != null) {
                    finalContent = if (content.isBlank()) fileResult
                    else "$content\n\n$fileResult"
                } else {
                    val fileName = withContext(Dispatchers.IO) { getFileName(appContext, fileUri) }
                    finalContent = if (content.isBlank()) "[用户发送了文件: $fileName，但无法读取内容]"
                    else "$content\n\n[用户发送了文件: $fileName，但无法读取内容]"
                }
            }

            // Offload bitmap decode/compress to IO thread to prevent frequent GC on UI thread.
            var imageBase64: String? = null
            if (imageUri != null && appContext != null) {
                imageBase64 = withContext(Dispatchers.IO) { uriToBase64(appContext, imageUri) }
                if (finalContent.isBlank()) finalContent = "[用户发送了一张图片，请分析图片内容]"
            }

            var videoBase64: String? = null
            if (videoUri != null && appContext != null) {
                videoBase64 = withContext(Dispatchers.IO) { uriToVideoBase64(appContext, videoUri) }
                if (finalContent.isBlank()) finalContent = "[用户发送了一段视频，请分析视频内容]"
            }

            if (finalContent.isBlank()) {
                _uiState.value = _uiState.value.copy(isProcessing = false)
                return@launch
            }

            val isFirstMessage = _uiState.value.messages.isEmpty()
            val toolCalls = mutableListOf<ToolCallStatus>()
            val callingRegex = Regex("""\[Calling tool: (.+)]""")
            val resultRegex = Regex("""\[Tool result: (.+)]""")

            val guiProgressJob = viewModelScope.launch {
                GuiAgent.stepUpdates.collect { update ->
                    if (update.done) return@collect
                    _uiState.value = _uiState.value.copy(
                        streamingThinking = "[GUI步骤${update.step}] ${update.thinking}\n=> ${update.action}"
                    )
                }
            }

            try {
                val textBuilder = StringBuilder()
                val thinkingBuilder = StringBuilder()

                agentEngine.processMessage(sessionId, finalContent,
                    imageUrl = imageBase64,
                    videoUrl = videoBase64
                ).collect { chunk ->
                    val callingMatch = callingRegex.find(chunk)
                    val resultMatch = resultRegex.find(chunk)
                    when {
                        chunk.startsWith(THINKING_MARKER) -> {
                            thinkingBuilder.append(chunk.removePrefix(THINKING_MARKER))
                            _uiState.value = _uiState.value.copy(
                                streamingThinking = thinkingBuilder.toString()
                            )
                        }
                        callingMatch != null -> {
                            toolCalls.add(ToolCallStatus(callingMatch.groupValues[1], "running"))
                            _uiState.value = _uiState.value.copy(
                                activeToolCalls = toolCalls.toList(),
                                streamingContent = textBuilder.toString()
                            )
                        }
                        resultMatch != null -> {
                            val idx = toolCalls.indexOfLast { it.status == "running" }
                            if (idx >= 0) toolCalls[idx] = toolCalls[idx].copy(status = resultMatch.groupValues[1])
                            _uiState.value = _uiState.value.copy(
                                activeToolCalls = toolCalls.toList(),
                                streamingContent = textBuilder.toString()
                            )
                        }
                        else -> {
                            textBuilder.append(chunk)
                            val cleaned = com.orizon.openkiwi.util.VendorResponseSanitizer.stripPseudoFunctionCallBlocks(
                                textBuilder.toString()
                            )
                            if (cleaned.length != textBuilder.length) {
                                textBuilder.clear()
                                textBuilder.append(cleaned)
                            }
                            val fullText = textBuilder.toString()

                            if (thinkingBuilder.isNotEmpty()) {
                                _uiState.value = _uiState.value.copy(streamingContent = fullText)
                            } else {
                                val thinkStart = fullText.indexOf("<think>")
                                val thinkEnd = fullText.indexOf("</think>")
                                if (thinkStart != -1) {
                                    if (thinkEnd != -1) {
                                        val t = fullText.substring(thinkStart + 7, thinkEnd).trim()
                                        val c = fullText.substring(thinkEnd + 8).trimStart()
                                        _uiState.value = _uiState.value.copy(
                                            streamingThinking = t,
                                            streamingContent = c
                                        )
                                    } else {
                                        val t = fullText.substring(thinkStart + 7).trim()
                                        _uiState.value = _uiState.value.copy(
                                            streamingThinking = t,
                                            streamingContent = ""
                                        )
                                    }
                                } else {
                                    _uiState.value = _uiState.value.copy(streamingContent = fullText)
                                }
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("connection abort", ignoreCase = true) == true ->
                        "网络连接中断，请检查网络后重试"
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "请求超时，请检查网络后重试"
                    else -> e.message ?: "未知错误"
                }
                _uiState.value = _uiState.value.copy(error = errorMsg)
            } finally {
                guiProgressJob.cancel()
                _uiState.value = _uiState.value.copy(
                    isProcessing = false, streamingContent = "",
                    streamingThinking = "", activeToolCalls = emptyList()
                )
            }

            if (isFirstMessage) {
                val title = content.take(30) + if (content.length > 30) "..." else ""
                chatRepository.updateSessionTitle(sessionId, title)
                _uiState.value = _uiState.value.copy(sessionTitle = title)
            }
        }
    }

    fun retryFromMessage(messageId: Long) {
        val messages = _uiState.value.messages
        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return
        val seedText = messages.subList(0, idx + 1).lastOrNull { it.role == "USER" }?.content ?: return
        sendMessage(seedText)
    }

    fun editMessageAsDraft(messageId: Long) {
        val msg = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        updateDraft(msg.content)
    }

    fun branchFromMessage(messageId: Long) {
        val sessionId = _uiState.value.currentSessionId ?: return
        viewModelScope.launch {
            val sourceMessages = chatRepository.getMessagesOnce(sessionId)
            val targetIdx = sourceMessages.indexOfFirst { it.messageId == messageId }
            if (targetIdx < 0) return@launch
            val prefix = sourceMessages.take(targetIdx + 1)
                .filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }
                .map { it.copy(messageId = 0, artifacts = emptyList()) }
            val newSessionId = chatRepository.createSession(title = "Branch of ${_uiState.value.sessionTitle}")
            chatRepository.addMessages(newSessionId, prefix)
            val session = chatRepository.getSession(newSessionId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                currentSessionId = newSessionId,
                sessionTitle = session.title,
                draftText = draftBySession[newSessionId].orEmpty(),
                streamingContent = "",
                streamingThinking = "",
                activeToolCalls = emptyList()
            )
            observeMessages(newSessionId)
        }
    }

    fun stopGeneration() {
        sendJob?.cancel()
        agentEngine.cancelCurrentTask()
        _uiState.value = _uiState.value.copy(
            isProcessing = false, streamingContent = "", streamingThinking = ""
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearProactiveHint() {
        _uiState.value = _uiState.value.copy(proactiveHint = null)
    }

    fun toggleVoiceInput() {
        _uiState.value = _uiState.value.copy(isListening = !_uiState.value.isListening)
    }

    private fun uriToBase64(ctx: Context, uri: Uri): String? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                val rawSize = input.available()
                if (rawSize > 2 * 1024 * 1024) options.inSampleSize = 2
                val bitmap = BitmapFactory.decodeStream(input, null, options) ?: return null
                val scaled = if (bitmap.width > 1024 || bitmap.height > 1024) {
                    val ratio = minOf(1024f / bitmap.width, 1024f / bitmap.height)
                    val result = android.graphics.Bitmap.createScaledBitmap(
                        bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true
                    )
                    if (result !== bitmap) bitmap.recycle()
                    result
                } else bitmap
                val baos = ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos)
                scaled.recycle()
                "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }
        } catch (_: Exception) { null }
    }

    private fun uriToVideoBase64(ctx: Context, uri: Uri): String? {
        return try {
            val mime = ctx.contentResolver.getType(uri) ?: "video/mp4"
            val videoMime = when {
                mime.contains("mp4") -> "video/mp4"
                mime.contains("avi") -> "video/avi"
                mime.contains("quicktime") || mime.contains("mov") -> "video/quicktime"
                mime.contains("webm") -> "video/webm"
                else -> "video/mp4"
            }
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                val maxSize = 50 * 1024 * 1024
                if (bytes.size > maxSize) {
                    android.util.Log.w("ChatViewModel", "Video too large: ${bytes.size} bytes, max $maxSize")
                    return null
                }
                "data:$videoMime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            android.util.Log.w("ChatViewModel", "uriToVideoBase64 failed", e)
            null
        }
    }

    private fun readFileContent(ctx: Context, uri: Uri): String? {
        return try {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }

            val mime = ctx.contentResolver.getType(uri) ?: ""
            val fileName = getFileName(ctx, uri)
            val fileSize = ctx.contentResolver.openInputStream(uri)?.use { it.available() } ?: 0
            val sizeStr = when {
                fileSize < 1024 -> "${fileSize}B"
                fileSize < 1024 * 1024 -> "${fileSize / 1024}KB"
                else -> "${"%.1f".format(fileSize / 1024.0 / 1024.0)}MB"
            }

            val textMimes = listOf(
                "text/", "application/json", "application/xml",
                "application/javascript", "application/x-yaml",
                "application/csv", "application/x-sh",
                "application/x-python", "application/sql",
                "application/xhtml", "application/x-httpd-php"
            )
            val textExtensions = Regex(
                ".*\\.(txt|md|csv|json|xml|html|css|js|ts|py|java|kt|kts|c|cpp|h|hpp|sh|yml|yaml|toml|ini|cfg|log|sql|gradle|properties|bat|ps1|rb|rs|go|swift|dart|lua|r|pl|php|tex|rst|org|conf|env|gitignore|dockerfile|makefile|cmake|proto|graphql|vue|svelte|jsx|tsx)$",
                RegexOption.IGNORE_CASE
            )
            val isText = textMimes.any { mime.startsWith(it) }
                    || fileName.matches(textExtensions)
                    || (mime.isBlank() && fileSize < 512_000)

            val header = "[文件: $fileName | 类型: ${mime.ifBlank { "unknown" }} | 大小: $sizeStr]"

            if (!isText) {
                val docText = DocumentTextExtractor.extract(ctx, uri, mime, fileName)
                if (docText != null) {
                    val body = if (docText.length >= 30_000) {
                        "${docText.take(30_000)}\n...[文档内容已截断，仅显示前30000字符]"
                    } else docText
                    return "$header\n```\n$body\n```"
                }
                return "$header\n（二进制文件，无法提取文本内容）"
            }

            val content = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readText().take(30_000)
            } ?: return "$header\n（无法读取文件内容）"

            if (content.isBlank()) {
                return "$header\n（文件内容为空）"
            }

            val body = if (content.length >= 30_000) {
                "$content\n...[文件内容已截断，仅显示前30000字符]"
            } else content

            "$header\n```\n$body\n```"
        } catch (e: Exception) {
            android.util.Log.w("ChatViewModel", "readFileContent failed", e)
            null
        }
    }

    private fun getFileName(ctx: Context, uri: Uri): String {
        return try {
            val cursor = ctx.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment ?: "unknown"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "unknown"
        }
    }

    class Factory(
        private val agentEngine: AgentEngine,
        private val chatRepository: ChatRepository,
        private val modelRepository: ModelRepository,
        private val appContext: Context? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(agentEngine, chatRepository, modelRepository, appContext) as T
        }
    }
}
