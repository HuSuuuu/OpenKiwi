package com.orizon.openkiwi.ui.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.core.agent.AgentEngine
import com.orizon.openkiwi.core.agent.THINKING_MARKER
import com.orizon.openkiwi.core.gui.GuiAgent
import com.orizon.openkiwi.core.model.ChatRole
import com.orizon.openkiwi.data.repository.ChatRepository
import com.orizon.openkiwi.data.repository.ModelRepository
import com.orizon.openkiwi.ui.components.MessageUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

data class ToolCallStatus(val name: String, val status: String)

data class ChatUiState(
    val messages: List<MessageUiModel> = emptyList(),
    val isProcessing: Boolean = false,
    val currentSessionId: String? = null,
    val sessionTitle: String = "New Chat",
    val streamingContent: String = "",
    val streamingThinking: String = "",
    val error: String? = null,
    val isListening: Boolean = false,
    val activeToolCalls: List<ToolCallStatus> = emptyList()
)

class ChatViewModel(
    private val agentEngine: AgentEngine,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private var pendingImageUri: Uri? = null
    private var pendingFileUri: Uri? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val sessions = chatRepository.getAllSessions()

    private var messageObserverJob: Job? = null
    private var sendJob: Job? = null

    init {
        viewModelScope.launch { resumeOrCreateSession() }
    }

    private suspend fun resumeOrCreateSession() {
        val allSessions = chatRepository.getAllSessionsOnce()
        if (allSessions.isNotEmpty()) {
            val latest = allSessions.first()
            _uiState.value = ChatUiState(
                currentSessionId = latest.id,
                sessionTitle = latest.title
            )
            observeMessages(latest.id)
        } else {
            val sessionId = chatRepository.createSession()
            _uiState.value = ChatUiState(currentSessionId = sessionId)
            observeMessages(sessionId)
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val currentId = _uiState.value.currentSessionId
            if (currentId != null) {
                val messages = chatRepository.getMessagesOnce(currentId)
                if (messages.isEmpty()) return@launch
            }
            val sessionId = chatRepository.createSession()
            _uiState.value = ChatUiState(currentSessionId = sessionId)
            observeMessages(sessionId)
        }
    }

    fun switchSession(sessionId: String) {
        viewModelScope.launch {
            val session = chatRepository.getSession(sessionId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                currentSessionId = sessionId,
                sessionTitle = session.title,
                messages = emptyList(),
                streamingContent = "",
                error = null
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
                    messages = visible.mapIndexed { index, msg ->
                        MessageUiModel(
                            id = index.toLong(),
                            role = msg.role.name,
                            content = msg.content ?: ""
                        )
                    }
                )
            }
        }
    }

    fun setImageAttachment(uri: Uri?) { pendingImageUri = uri }
    fun setFileAttachment(uri: Uri?) { pendingFileUri = uri }

    fun sendMessage(content: String) {
        val sessionId = _uiState.value.currentSessionId ?: return
        val imageUri = pendingImageUri
        val fileUri = pendingFileUri
        pendingImageUri = null
        pendingFileUri = null

        if (content.isBlank() && imageUri == null && fileUri == null) return

        sendJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true, error = null,
                streamingContent = "", streamingThinking = "", activeToolCalls = emptyList()
            )

            var finalContent = content

            // Read file content into message text
            if (fileUri != null && appContext != null) {
                val fileText = readFileContent(appContext, fileUri)
                if (fileText != null) {
                    finalContent = if (content.isBlank()) "[附件内容]\n$fileText"
                    else "$content\n\n[附件内容]\n$fileText"
                }
            }

            // Convert image URI to base64 and store as imageUrl in ChatMessage
            var imageBase64: String? = null
            if (imageUri != null && appContext != null) {
                imageBase64 = uriToBase64(appContext, imageUri)
                if (finalContent.isBlank()) finalContent = "[用户发送了一张图片，请分析图片内容]"
            }

            if (finalContent.isBlank()) return@launch

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
                    imageUrl = imageBase64
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

    fun toggleVoiceInput() {
        _uiState.value = _uiState.value.copy(isListening = !_uiState.value.isListening)
    }

    private fun uriToBase64(ctx: Context, uri: Uri): String? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                val scaled = if (bitmap.width > 1024 || bitmap.height > 1024) {
                    val ratio = minOf(1024f / bitmap.width, 1024f / bitmap.height)
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true
                    )
                } else bitmap
                val baos = ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos)
                "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }
        } catch (_: Exception) { null }
    }

    private fun readFileContent(ctx: Context, uri: Uri): String? {
        return try {
            val mime = ctx.contentResolver.getType(uri) ?: ""
            val fileName = run {
                val cursor = ctx.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) it.getString(idx) else null
                    } else null
                } ?: uri.lastPathSegment ?: "unknown"
            }

            val textMimes = listOf(
                "text/", "application/json", "application/xml",
                "application/javascript", "application/x-yaml",
                "application/csv", "application/x-sh"
            )
            val isText = textMimes.any { mime.startsWith(it) }
                    || fileName?.matches(Regex(".*\\.(txt|md|csv|json|xml|html|css|js|ts|py|java|kt|kts|c|cpp|h|sh|yml|yaml|toml|ini|cfg|log|sql|gradle|properties|bat|ps1|rb|rs|go|swift|dart|lua|r|pl|php)$", RegexOption.IGNORE_CASE)) == true

            if (!isText) {
                val size = ctx.contentResolver.openInputStream(uri)?.use { it.available() } ?: 0
                val sizeStr = when {
                    size < 1024 -> "${size}B"
                    size < 1024 * 1024 -> "${size / 1024}KB"
                    else -> "${"%.1f".format(size / 1024.0 / 1024.0)}MB"
                }
                return "[文件: $fileName | 类型: $mime | 大小: $sizeStr]\n（二进制文件，无法以文本显示）"
            }

            val content = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readText().take(20_000)
            } ?: return null

            if (content.length >= 20_000) {
                "$content\n...[文件内容已截断，仅显示前20000字符]"
            } else content
        } catch (_: Exception) { null }
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
