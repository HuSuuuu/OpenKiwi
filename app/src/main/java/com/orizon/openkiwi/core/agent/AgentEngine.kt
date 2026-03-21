package com.orizon.openkiwi.core.agent

import com.orizon.openkiwi.core.memory.MemoryManager
import com.orizon.openkiwi.core.model.*
import com.orizon.openkiwi.core.skill.SkillLearner
import com.orizon.openkiwi.core.tool.ToolArtifact
import com.orizon.openkiwi.core.tool.ToolExecutor
import com.orizon.openkiwi.core.tool.ToolRegistry
import com.orizon.openkiwi.data.repository.ChatRepository
import com.orizon.openkiwi.data.repository.ModelRepository
import com.orizon.openkiwi.network.OpenAIApiClient
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class AgentEngine(
    private val apiClient: OpenAIApiClient,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val memoryManager: MemoryManager,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val smartModelDispatcher: SmartModelDispatcher? = null,
    private val skillLearner: SkillLearner? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _agentState = MutableStateFlow(AgentState())
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private var currentJob: Job? = null
    private val maxToolIterations = 10

    suspend fun processMessage(
        sessionId: String,
        userMessage: String,
        modelConfig: ModelConfig? = null,
        imageUrl: String? = null,
        videoUrl: String? = null,
        userText: String? = null
    ): Flow<String> = flow {
        _agentState.value = _agentState.value.copy(isProcessing = true, error = null)

        val config = modelConfig
            ?: smartModelDispatcher?.dispatch(userMessage)
            ?: modelRepository.getDefaultConfig()
            ?: run {
                val err = "未配置模型，请先在「设置 → 模型配置」中添加模型并填写 API Key。"
                emitError(sessionId, err)
                return@flow
            }

        if (config.apiKey.isBlank()) {
            val err = "模型「${config.name}」的 API Key 为空，请在「模型配置」中填写。"
            emitError(sessionId, err)
            return@flow
        }

        chatRepository.addMessage(sessionId, ChatMessage(role = ChatRole.USER, content = userMessage))

        val messages = chatRepository.getMessagesOnce(sessionId).toMutableList()

        if (imageUrl != null || videoUrl != null) {
            val lastUserIdx = messages.indexOfLast { it.role == ChatRole.USER }
            if (lastUserIdx >= 0) {
                messages[lastUserIdx] = messages[lastUserIdx].copy(
                    imageUrl = imageUrl ?: messages[lastUserIdx].imageUrl,
                    videoUrl = videoUrl ?: messages[lastUserIdx].videoUrl
                )
            }
        }

        val relevantMemories = memoryManager.searchMemories(userMessage, limit = 5, scope = sessionId)
        if (relevantMemories.isNotEmpty()) {
            val memoryContext = relevantMemories.joinToString("\n") { "- ${it.content}" }
            messages.add(0, ChatMessage(
                role = ChatRole.SYSTEM,
                content = "Relevant context from memory:\n$memoryContext"
            ))
        }

        if (messages.none { it.role == ChatRole.SYSTEM }) {
            messages.add(0, ChatMessage(role = ChatRole.SYSTEM, content = AgentSystemPrompt.DEFAULT))
        }

        val compressedMessages = memoryManager.compressContext(
            messages, config.maxTokens / 2
        )

        val intentSource = userText ?: userMessage
        val parasiticOn = ParasiticQueryTool.enabled
        val needsTools = config.supportsTools && (parasiticOn || needsToolUse(intentSource))
        val toolSpecs = if (needsTools) toolRegistry.toToolSpecs() else null

        if (parasiticOn && needsTools) {
            val hint = ChatMessage(
                role = ChatRole.SYSTEM,
                content = "用户已开启寄生模式。请使用 parasitic_query 工具将用户的问题发送给豆包，获取回复后转达给用户。"
            )
            messages.add(hint)
        }

        val globalFullContent = StringBuilder()
        val globalThinkingContent = StringBuilder()
        val globalToolLog = StringBuilder()
        val pendingArtifacts = mutableListOf<Pair<String, ToolArtifact>>()

        try {
            var iterationMessages = compressedMessages.toMutableList()
            var iteration = 0
            val toolCallRecords = mutableListOf<SkillLearner.ToolCallRecord>()
            val taskStartTime = System.currentTimeMillis()

            while (iteration < maxToolIterations) {
                val request = ChatCompletionRequest(
                    model = config.modelName,
                    messages = iterationMessages,
                    temperature = config.temperature,
                    maxTokens = config.maxTokens,
                    tools = toolSpecs?.takeIf { it.isNotEmpty() },
                    topP = config.topP,
                    frequencyPenalty = config.frequencyPenalty,
                    presencePenalty = config.presencePenalty,
                    reasoningEffort = config.reasoningEffort.takeIf { it in listOf("low", "medium", "high") }
                )

                if (config.supportsStreaming) {
                    val fullContent = StringBuilder()
                    val thinkingContent = StringBuilder()
                    var toolCallsDetected = false
                    var streamUsage: Usage? = null
                    data class ToolCallAccumulator(var id: String, var name: String, val arguments: StringBuilder = StringBuilder())
                    val accumulatedToolCalls = mutableMapOf<Int, ToolCallAccumulator>()

                    apiClient.streamChatCompletion(config.apiBaseUrl, config.apiKey, request)
                        .collect { chunk ->
                            chunk.usage?.let { streamUsage = it }
                            chunk.choices.firstOrNull()?.let { choice ->
                                choice.delta?.reasoningContent?.let { reasoning ->
                                    thinkingContent.append(reasoning)
                                    globalThinkingContent.clear()
                                    globalThinkingContent.append(thinkingContent)
                                    emit("$THINKING_MARKER$reasoning")
                                }
                                choice.delta?.content?.let { content ->
                                    fullContent.append(content)
                                    globalFullContent.clear()
                                    globalFullContent.append(fullContent)
                                    emit(content)
                                }
                                choice.delta?.toolCalls?.forEach { tc ->
                                    toolCallsDetected = true
                                    val idx = tc.index
                                    val acc = accumulatedToolCalls.getOrPut(idx) { ToolCallAccumulator(id = "", name = "") }
                                    if (tc.id.isNotBlank()) acc.id = tc.id
                                    if (tc.function.name.isNotBlank()) acc.name = tc.function.name
                                    if (tc.function.arguments.isNotEmpty()) acc.arguments.append(tc.function.arguments)
                                }
                                if (choice.finishReason == "tool_calls") {
                                    toolCallsDetected = true
                                }
                            }
                        }

                    TokenTracker.record(streamUsage, config.modelName, sessionId)

                    if (toolCallsDetected && accumulatedToolCalls.isNotEmpty()) {
                        val toolCalls = accumulatedToolCalls.entries.sortedBy { it.key }.map { (idx, acc) ->
                            ToolCall(
                                id = acc.id.ifBlank { "call_$idx" },
                                index = idx,
                                function = FunctionCall(name = acc.name, arguments = acc.arguments.toString())
                            )
                        }

                        val assistantMsg = ChatMessage(
                            role = ChatRole.ASSISTANT,
                            content = fullContent.toString().takeIf { it.isNotBlank() },
                            toolCalls = toolCalls
                        )
                        iterationMessages.add(assistantMsg)

                        for (tc in toolCalls) {
                            val params = parseToolArguments(tc.function.arguments)
                            val callingMarker = "\n[Calling tool: ${tc.function.name}]\n"
                            globalToolLog.append(callingMarker)
                            emit(callingMarker)

                            val result = toolExecutor.execute(tc.function.name, params, sessionId)
                            result.artifacts.forEach { artifact ->
                                pendingArtifacts += tc.function.name to artifact
                            }
                            toolCallRecords.add(SkillLearner.ToolCallRecord(
                                toolName = tc.function.name,
                                params = params.mapValues { it.value?.toString() ?: "" },
                                result = result,
                                stepIndex = toolCallRecords.size
                            ))
                            iterationMessages.add(
                                ChatMessage(
                                    role = ChatRole.TOOL,
                                    content = result.output.ifBlank { result.error ?: "No output" },
                                    toolCallId = tc.id
                                )
                            )
                            val resultMarker = "[Tool result: ${if (result.success) "success" else "failed"}]\n"
                            globalToolLog.append(resultMarker)
                            emit(resultMarker)
                        }
                        iteration++
                        continue
                    }

                    globalFullContent.clear()
                    globalThinkingContent.clear()
                    globalToolLog.clear()

                    val savedContent = buildString {
                        if (globalToolLog.isNotBlank()) append(globalToolLog)
                        if (thinkingContent.isNotBlank()) append("<think>\n$thinkingContent\n</think>\n\n")
                        append(fullContent)
                    }
                    val messageId = chatRepository.addMessage(
                        sessionId,
                        ChatMessage(role = ChatRole.ASSISTANT, content = savedContent)
                    )
                    pendingArtifacts.groupBy({ it.first }, { it.second }).forEach { (toolName, artifacts) ->
                        chatRepository.saveToolArtifacts(sessionId, messageId, toolName, artifacts)
                    }
                    pendingArtifacts.clear()
                    break
                } else {
                    val result = apiClient.chatCompletion(config.apiBaseUrl, config.apiKey, request)
                    if (result.isFailure) {
                        val err = "API 请求失败: ${result.exceptionOrNull()?.message}"
                        emitError(sessionId, err)
                        break
                    }

                    val response = result.getOrThrow()
                    TokenTracker.record(response.usage, config.modelName, sessionId)
                    val choice = response.choices.firstOrNull()
                    val message = choice?.message

                    if (message?.toolCalls != null) {
                        iterationMessages.add(message)
                        for (tc in message.toolCalls) {
                            val params = parseToolArguments(tc.function.arguments)
                            val callingMarker = "\n[Calling tool: ${tc.function.name}]\n"
                            globalToolLog.append(callingMarker)
                            emit(callingMarker)

                            val toolResult = toolExecutor.execute(tc.function.name, params, sessionId)
                            toolResult.artifacts.forEach { artifact ->
                                pendingArtifacts += tc.function.name to artifact
                            }
                            toolCallRecords.add(SkillLearner.ToolCallRecord(
                                toolName = tc.function.name,
                                params = params.mapValues { it.value?.toString() ?: "" },
                                result = toolResult,
                                stepIndex = toolCallRecords.size
                            ))
                            iterationMessages.add(
                                ChatMessage(
                                    role = ChatRole.TOOL,
                                    content = toolResult.output.ifBlank { toolResult.error ?: "No output" },
                                    toolCallId = tc.id
                                )
                            )
                            val resultMarker = "[Tool result: ${if (toolResult.success) "success" else "failed"}]\n"
                            globalToolLog.append(resultMarker)
                            emit(resultMarker)
                        }
                        iteration++
                        continue
                    }

                    val content = message?.content ?: ""
                    val reasoning = message?.reasoningContent
                    globalFullContent.clear()
                    globalFullContent.append(content)
                    if (!reasoning.isNullOrBlank()) {
                        globalThinkingContent.clear()
                        globalThinkingContent.append(reasoning)
                        emit("$THINKING_MARKER$reasoning")
                    }
                    emit(content)

                    globalFullContent.clear()
                    globalThinkingContent.clear()
                    globalToolLog.clear()

                    val savedContent = buildString {
                        if (globalToolLog.isNotBlank()) append(globalToolLog)
                        if (!reasoning.isNullOrBlank()) append("<think>\n$reasoning\n</think>\n\n")
                        append(content)
                    }
                    val messageId = chatRepository.addMessage(
                        sessionId,
                        ChatMessage(role = ChatRole.ASSISTANT, content = savedContent)
                    )
                    pendingArtifacts.groupBy({ it.first }, { it.second }).forEach { (toolName, artifacts) ->
                        chatRepository.saveToolArtifacts(sessionId, messageId, toolName, artifacts)
                    }
                    pendingArtifacts.clear()
                    break
                }
            }

            if (toolCallRecords.isNotEmpty()) {
                runCatching {
                    skillLearner?.learnFromTrace(SkillLearner.TaskTrace(
                        goal = userMessage,
                        toolCalls = toolCallRecords,
                        success = true,
                        totalTimeMs = System.currentTimeMillis() - taskStartTime
                    ))
                }.onSuccess { skill ->
                    if (skill != null) Log.d("AgentEngine", "Learned skill: ${skill.name}")
                }
            }
        } catch (e: CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                savePartialResponse(sessionId, globalToolLog, globalThinkingContent, globalFullContent, pendingArtifacts)
            }
            throw e
        } catch (e: Exception) {
            savePartialResponse(sessionId, globalToolLog, globalThinkingContent, globalFullContent, pendingArtifacts)
            val err = "错误: ${e.message}"
            emitError(sessionId, err)
        } finally {
            _agentState.value = _agentState.value.copy(isProcessing = false)
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun savePartialResponse(
        sessionId: String,
        toolLog: StringBuilder,
        thinking: StringBuilder,
        content: StringBuilder,
        pendingArtifacts: List<Pair<String, ToolArtifact>>
    ) {
        if (content.isBlank() && thinking.isBlank() && toolLog.isBlank()) return
        runCatching {
            val savedContent = buildString {
                if (toolLog.isNotBlank()) append(toolLog)
                if (thinking.isNotBlank()) append("<think>\n$thinking\n</think>\n\n")
                append(content)
                if (isNotBlank()) append("\n\n[生成中断]")
            }
            if (savedContent.isNotBlank()) {
                val messageId = chatRepository.addMessage(
                    sessionId,
                    ChatMessage(role = ChatRole.ASSISTANT, content = savedContent)
                )
                pendingArtifacts.groupBy({ it.first }, { it.second }).forEach { (toolName, artifacts) ->
                    chatRepository.saveToolArtifacts(sessionId, messageId, toolName, artifacts)
                }
            }
        }
    }

    private suspend fun FlowCollector<String>.emitError(sessionId: String, error: String) {
        _agentState.value = _agentState.value.copy(isProcessing = false, error = error)
        chatRepository.addMessage(sessionId, ChatMessage(role = ChatRole.ASSISTANT, content = error))
        emit(error)
    }

    fun cancelCurrentTask() {
        currentJob?.cancel()
        _agentState.value = _agentState.value.copy(isProcessing = false)
    }

    private fun parseToolArguments(arguments: String): Map<String, Any?> {
        return runCatching {
            val jsonObj = json.decodeFromString<JsonObject>(arguments)
            jsonObj.mapValues { (_, value) -> value.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
    }

    private fun needsToolUse(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        val actionKeywords = listOf(
            "打开", "启动", "运行", "执行", "安装", "卸载", "删除",
            "发送", "发短信", "打电话", "拨打", "呼叫",
            "搜索", "搜一下", "查一下", "查找", "查询",
            "下载", "上传", "传输",
            "拍照", "录音", "录像", "截图", "截屏",
            "设置", "修改设置", "调整",
            "创建文件", "写入文件", "保存到", "新建",
            "连接", "SSH", "蓝牙", "WiFi",
            "导航到", "定位", "位置",
            "提醒", "闹钟", "日历",
            "复制", "粘贴", "剪切",
            "寄生", "豆包", "问豆包", "让豆包",
            "open", "launch", "run", "execute", "install", "delete",
            "send", "call", "search", "download", "upload",
            "take photo", "record", "screenshot",
            "navigate", "connect", "parasitic", "doubao"
        )
        val lower = trimmed.lowercase()
        return actionKeywords.any { lower.contains(it) }
    }
}

data class AgentState(
    val isProcessing: Boolean = false,
    val currentToolCall: String? = null,
    val error: String? = null
)

const val THINKING_MARKER = "§T§"
