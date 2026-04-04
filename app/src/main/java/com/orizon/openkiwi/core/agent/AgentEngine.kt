package com.orizon.openkiwi.core.agent

import com.orizon.openkiwi.core.llm.*
import com.orizon.openkiwi.core.memory.MemoryManager
import com.orizon.openkiwi.core.model.*
import com.orizon.openkiwi.core.openclaw.OpenClawSkillRegistry
import com.orizon.openkiwi.core.skill.SkillLearner
import com.orizon.openkiwi.core.tool.ToolArtifact
import com.orizon.openkiwi.core.tool.ToolExecutor
import com.orizon.openkiwi.core.tool.ToolRegistry
import com.orizon.openkiwi.core.tool.ToolResult
import com.orizon.openkiwi.core.tool.ToolRetriever
import com.orizon.openkiwi.data.preferences.UserPreferences
import com.orizon.openkiwi.data.repository.ChatRepository
import com.orizon.openkiwi.data.repository.ModelRepository
import com.orizon.openkiwi.network.OpenAIApiClient
import com.orizon.openkiwi.util.VendorResponseSanitizer
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

class AgentEngine(
    private val apiClient: OpenAIApiClient,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val memoryManager: MemoryManager,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val smartModelDispatcher: SmartModelDispatcher? = null,
    private val skillLearner: SkillLearner? = null,
    private val userPreferences: UserPreferences? = null,
    private val llmProviderFactory: LlmProviderFactory? = null,
    private val agentWorkspace: AgentWorkspace? = null,
    val reflector: AgentReflector = AgentReflector(apiClient),
    private val openClawSkillRegistry: OpenClawSkillRegistry? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _agentState = MutableStateFlow(AgentState())
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    suspend fun processMessage(
        sessionId: String,
        userMessage: String,
        modelConfig: ModelConfig? = null,
        imageUrl: String? = null,
        videoUrl: String? = null
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

        if (messages.none { it.role == ChatRole.SYSTEM }) {
            val wsCtx = agentWorkspace?.buildPromptContext() ?: ""
            val skillCatalog = openClawSkillRegistry?.buildSkillCatalog() ?: ""
            messages.add(0, ChatMessage(role = ChatRole.SYSTEM, content = AgentSystemPrompt.buildWithTime(wsCtx, skillCatalog)))
        }

        val parasiticOn = ParasiticQueryTool.enabled

        val (relevantMemories, toolSpecs) = coroutineScope {
            val memJob = async(Dispatchers.IO) {
                memoryManager.searchMemories(userMessage, limit = 5, scope = sessionId)
            }
            val toolJob = async(Dispatchers.Default) {
                resolveAllToolSpecs(config, userMessage, messages, parasiticOn)
            }
            memJob.await() to toolJob.await()
        }

        if (relevantMemories.isNotEmpty()) {
            val memoryContext = relevantMemories.joinToString("\n") { "- ${it.content}" }
            messages.add(0, ChatMessage(
                role = ChatRole.SYSTEM,
                content = "Relevant context from memory:\n$memoryContext"
            ))
        }

        val compressedMessages = memoryManager.compressContext(
            messages, config.maxTokens / 2
        ).toMutableList()

        if (parasiticOn && !toolSpecs.isNullOrEmpty() && !config.webSearchExclusive) {
            val hint = ChatMessage(
                role = ChatRole.SYSTEM,
                content = "用户已开启寄生模式。请使用 parasitic_query 工具将用户的问题发送给豆包，获取回复后转达给用户。"
            )
            messages.add(hint)
        }

        val maxToolIterations = config.maxToolIterations.coerceIn(1, 50)
        reflector.resetForNewTask()

        val globalFullContent = StringBuilder()
        val globalThinkingContent = StringBuilder()
        val globalToolLog = StringBuilder()
        val pendingArtifacts = mutableListOf<Pair<String, ToolArtifact>>()

        val provider = llmProviderFactory?.getProvider(config.providerType)
        val useUnifiedProvider = provider != null && config.providerType != "openai"

        try {
            var iterationMessages = compressedMessages.toMutableList()
            var iteration = 0
            val toolCallRecords = mutableListOf<SkillLearner.ToolCallRecord>()
            val taskStartTime = System.currentTimeMillis()

            while (iteration < maxToolIterations) {
                if (useUnifiedProvider) {
                    val unifiedResult = executeWithUnifiedProvider(
                        provider!!, config, iterationMessages, toolSpecs,
                        sessionId, iteration, maxToolIterations,
                        globalFullContent, globalThinkingContent, globalToolLog,
                        pendingArtifacts, toolCallRecords
                    )
                    for (chunk in unifiedResult.emittedChunks) emit(chunk)
                    iterationMessages = unifiedResult.updatedMessages
                    if (unifiedResult.shouldBreak) break
                    if (unifiedResult.shouldContinue) { iteration++; continue }
                    break
                }
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
                    val contentStreamScrubber = VendorResponseSanitizer.StreamScrubber()
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
                                    val vis = contentStreamScrubber.deltaForCurrentBuffer(fullContent)
                                    if (vis.isNotEmpty()) emit(vis)
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
                            content = VendorResponseSanitizer.stripPseudoFunctionCallBlocks(fullContent.toString())
                                .takeIf { it.isNotBlank() },
                            toolCalls = toolCalls
                        )
                        iterationMessages.add(assistantMsg)

                        for (tc in toolCalls) {
                            val params = parseToolArguments(tc.function.arguments)
                            val callingMarker = emitCallingWithArgs(tc.function.name, params)
                            globalToolLog.append(callingMarker)
                            emit(callingMarker)

                            var result = toolExecutor.execute(tc.function.name, params, sessionId)

                            if (!result.success && config.supportsTools) {
                                val reflection = try {
                                    reflector.reflect(tc.function.name, params, result.error ?: "Unknown error", config, iterationMessages)
                                } catch (e: Exception) {
                                    Log.w("AgentEngine", "Reflection failed", e)
                                    null
                                }
                                if (reflection != null) {
                                    emit("$THINKING_MARKER[Reflect] ${reflection.reasoning}\n")
                                    when (reflection.decision) {
                                        ReflectionDecision.RETRY_SAME -> {
                                            result = toolExecutor.execute(tc.function.name, params, sessionId)
                                        }
                                        ReflectionDecision.RETRY_DIFFERENT -> {
                                            emit("$THINKING_MARKER[Retry] ${reflection.suggestedAction}\n")
                                            result = toolExecutor.execute(tc.function.name, params, sessionId)
                                        }
                                        ReflectionDecision.ABORT -> {
                                            emit("[Reflect: ABORT] ${reflection.reasoning}\n")
                                        }
                                        ReflectionDecision.SKIP -> { }
                                    }
                                }
                            }

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

                    val toolLogSnapshotStream = globalToolLog.toString()
                    globalFullContent.clear()
                    globalThinkingContent.clear()
                    globalToolLog.clear()

                    val savedContent = buildString {
                        if (toolLogSnapshotStream.isNotBlank()) append(toolLogSnapshotStream)
                        if (thinkingContent.isNotBlank()) append("<think>\n$thinkingContent\n</think>\n\n")
                        append(VendorResponseSanitizer.stripPseudoFunctionCallBlocks(fullContent.toString()))
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
                            val callingMarker = emitCallingWithArgs(tc.function.name, params)
                            globalToolLog.append(callingMarker)
                            emit(callingMarker)

                            val tool = toolRegistry.getTool(tc.function.name)
                            var toolResult: ToolResult
                            if (tool != null && tool.supportsStreaming) {
                                val streamOutput = StringBuilder()
                                tool.executeStreaming(params).collect { chunk ->
                                    streamOutput.append(chunk)
                                    emit("[stream:${tc.function.name}]$chunk")
                                }
                                toolResult = ToolResult(tc.function.name, true, streamOutput.toString())
                            } else {
                                toolResult = toolExecutor.execute(tc.function.name, params, sessionId)
                            }

                            if (!toolResult.success && config.supportsTools) {
                                val reflection = try {
                                    reflector.reflect(tc.function.name, params, toolResult.error ?: "Unknown error", config, iterationMessages)
                                } catch (e: Exception) {
                                    Log.w("AgentEngine", "Reflection failed", e)
                                    null
                                }
                                if (reflection != null) {
                                    emit("$THINKING_MARKER[Reflect] ${reflection.reasoning}\n")
                                    when (reflection.decision) {
                                        ReflectionDecision.RETRY_SAME -> {
                                            toolResult = toolExecutor.execute(tc.function.name, params, sessionId)
                                        }
                                        ReflectionDecision.RETRY_DIFFERENT -> {
                                            emit("$THINKING_MARKER[Retry] ${reflection.suggestedAction}\n")
                                            toolResult = toolExecutor.execute(tc.function.name, params, sessionId)
                                        }
                                        ReflectionDecision.ABORT -> {
                                            emit("[Reflect: ABORT] ${reflection.reasoning}\n")
                                        }
                                        ReflectionDecision.SKIP -> { }
                                    }
                                }
                            }

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
                    emit(VendorResponseSanitizer.stripPseudoFunctionCallBlocks(content))

                    val toolLogSnapshotNs = globalToolLog.toString()
                    globalFullContent.clear()
                    globalThinkingContent.clear()
                    globalToolLog.clear()

                    val savedContent = buildString {
                        if (toolLogSnapshotNs.isNotBlank()) append(toolLogSnapshotNs)
                        if (!reasoning.isNullOrBlank()) append("<think>\n$reasoning\n</think>\n\n")
                        append(VendorResponseSanitizer.stripPseudoFunctionCallBlocks(content))
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
                append(VendorResponseSanitizer.stripPseudoFunctionCallBlocks(content.toString()))
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
        _agentState.value = _agentState.value.copy(isProcessing = false)
    }

    suspend fun sendProactiveMessage(
        sessionId: String?,
        content: String,
        source: ProactiveMessage.Source
    ) {
        val targetSessionId = sessionId
            ?: chatRepository.getAllSessionsOnce().firstOrNull()?.id
            ?: chatRepository.createSession(title = "Kiwi 主动消息")

        val taggedContent = "[${source.name}] $content"
        chatRepository.addMessage(targetSessionId, ChatMessage(
            role = ChatRole.ASSISTANT,
            content = taggedContent
        ))

        ProactiveMessageBus.emit(targetSessionId, content, source)

        try {
            val ctx = com.orizon.openkiwi.OpenKiwiApp.instance
            val intent = android.content.Intent(ctx, com.orizon.openkiwi.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = android.app.PendingIntent.getActivity(ctx, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

            val sourceLabel = ProactiveMessage(null, "", source).sourceName
            val notification = androidx.core.app.NotificationCompat.Builder(ctx, com.orizon.openkiwi.OpenKiwiApp.CHANNEL_ALERTS)
                .setSmallIcon(com.orizon.openkiwi.R.mipmap.ic_launcher)
                .setContentTitle("Kiwi: $sourceLabel")
                .setContentText(content.take(100))
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(content.take(500)))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()

            val nm = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(System.currentTimeMillis().toInt() and 0x7FFF, notification)
        } catch (e: Exception) {
            Log.w("AgentEngine", "Failed to post proactive notification", e)
        }
    }

    /**
     * 合并服务商原生工具（如方舟 `web_search`）与本地 function 工具列表。
     */
    private suspend fun resolveAllToolSpecs(
        config: ModelConfig,
        userMessage: String,
        contextMessages: List<ChatMessage>,
        parasiticOn: Boolean
    ): List<ToolSpec>? {
        val webPart = if (config.includeWebSearchTool) {
            listOf(ToolSpec.volcanoWebSearchToolSpec())
        } else emptyList()

        val skipLocalFunctions = config.webSearchExclusive && config.includeWebSearchTool
        val fnPart = if (config.supportsTools && !skipLocalFunctions) {
            resolveFunctionToolSpecs(config, userMessage, contextMessages, parasiticOn).orEmpty()
        } else emptyList()

        return (webPart + fnPart).takeIf { it.isNotEmpty() }
    }

    /**
     * 全量或 BM25 检索子集。检索过窄时回退全量，避免漏工具。
     */
    private suspend fun resolveFunctionToolSpecs(
        config: ModelConfig,
        userMessage: String,
        contextMessages: List<ChatMessage>,
        parasiticOn: Boolean
    ): List<ToolSpec>? {
        // 方舟内置联网与本地 WebSearchTool 同名 web_search，合并会重复；开启方舟联网时去掉本地同名工具
        val enabledTools = toolRegistry.getEnabledTools().filter { tool ->
            !(config.includeWebSearchTool && tool.definition.name == ToolSpec.VOLCANO_WEB_SEARCH_TOOL_NAME)
        }
        if (enabledTools.isEmpty()) return null

        val useDynamic = userPreferences?.dynamicToolRetrieval?.first() ?: true
        val fullSpecs = toolRegistry.toToolSpecs(enabledTools)

        if (!useDynamic || enabledTools.size <= DYNAMIC_TOOL_MIN_REGISTRY_SIZE) {
            return fullSpecs.takeIf { it.isNotEmpty() }
        }

        val query = buildToolRetrievalQuery(userMessage, contextMessages)
        val pinNames = buildSet {
            add("memory")
            add("terminal")
            add("code_execution")
            add("file_manager")
            add("web_search")
            if (parasiticOn) add("parasitic_query")
        }
        val selected = ToolRetriever.selectTools(
            tools = enabledTools,
            query = query,
            topK = 15,
            pinNames = pinNames
        )
        val fallbackMin = minOf(10, (enabledTools.size + 1) / 2).coerceAtLeast(6)
        val finalTools = if (selected.size < fallbackMin) enabledTools else selected
        return toolRegistry.toToolSpecs(finalTools).takeIf { it.isNotEmpty() }
    }

    private fun buildToolRetrievalQuery(userMessage: String, contextMessages: List<ChatMessage>): String {
        val sb = StringBuilder(userMessage)
        contextMessages.takeLast(12).forEach { m ->
            if (m.role == ChatRole.USER || m.role == ChatRole.ASSISTANT) {
                m.content?.take(500)?.let { sb.append("\n").append(it) }
            }
        }
        return sb.toString()
    }

    private fun parseToolArguments(arguments: String): Map<String, Any?> {
        return runCatching {
            val jsonObj = json.decodeFromString<JsonObject>(arguments)
            jsonObj.mapValues { (_, value) -> jsonArgumentToKotlin(value) }
        }.getOrDefault(emptyMap())
    }

    /** 单行 JSON，供聊天 UI 解析并展示工具参数摘要 */
    private fun toolParamsToJsonLine(params: Map<String, Any?>): String? {
        if (params.isEmpty()) return null
        val obj = buildJsonObject {
            params.forEach { (k, v) ->
                when (v) {
                    null -> put(k, JsonNull)
                    is String -> put(k, JsonPrimitive(v))
                    is Boolean -> put(k, JsonPrimitive(v))
                    is Number -> put(k, JsonPrimitive(v.toDouble()))
                    else -> put(k, JsonPrimitive(v.toString()))
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun emitCallingWithArgs(toolName: String, params: Map<String, Any?>): String = buildString {
        append("\n[Calling tool: $toolName]\n")
        toolParamsToJsonLine(params)?.let { append(it).append("\n") }
    }

    /** 部分模型会对 code 等字段返回非 string 的 JSON；避免 jsonPrimitive 崩溃并丢参 */
    private fun jsonArgumentToKotlin(value: JsonElement): String = when (value) {
        is JsonPrimitive -> value.content
        is JsonArray -> value.toString()
        is JsonObject -> value.toString()
    }
    private data class UnifiedIterationResult(
        val emittedChunks: List<String>,
        val updatedMessages: MutableList<ChatMessage>,
        val shouldBreak: Boolean,
        val shouldContinue: Boolean
    )

    private suspend fun executeWithUnifiedProvider(
        provider: LlmProvider,
        config: ModelConfig,
        currentMessages: MutableList<ChatMessage>,
        toolSpecs: List<ToolSpec>?,
        sessionId: String,
        iteration: Int,
        maxIterations: Int,
        globalFullContent: StringBuilder,
        globalThinkingContent: StringBuilder,
        globalToolLog: StringBuilder,
        pendingArtifacts: MutableList<Pair<String, ToolArtifact>>,
        toolCallRecords: MutableList<SkillLearner.ToolCallRecord>
    ): UnifiedIterationResult {
        val emitted = mutableListOf<String>()
        val messages = currentMessages.toMutableList()

        val unifiedMessages = messages.map { msg ->
            UnifiedMessage(
                role = when (msg.role) {
                    ChatRole.SYSTEM -> UnifiedRole.SYSTEM
                    ChatRole.USER -> UnifiedRole.USER
                    ChatRole.ASSISTANT -> UnifiedRole.ASSISTANT
                    ChatRole.TOOL -> UnifiedRole.TOOL
                    null -> UnifiedRole.ASSISTANT
                },
                content = msg.content,
                toolCalls = msg.toolCalls?.map {
                    UnifiedToolCall(id = it.id, name = it.function.name, arguments = it.function.arguments)
                },
                toolCallId = msg.toolCallId,
                reasoningContent = msg.reasoningContent,
                imageUrl = msg.imageUrl,
                videoUrl = msg.videoUrl
            )
        }

        val unifiedToolSpecs = toolSpecs?.filter { it.type == ToolSpec.TYPE_FUNCTION }?.map { spec ->
            UnifiedToolSpec(
                name = spec.function.name,
                description = spec.function.description,
                parametersJson = json.encodeToString(ToolParameters.serializer(), spec.function.parameters)
            )
        }

        val unifiedRequest = UnifiedRequest(
            model = config.modelName,
            messages = unifiedMessages,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            topP = config.topP,
            frequencyPenalty = config.frequencyPenalty,
            presencePenalty = config.presencePenalty,
            tools = unifiedToolSpecs?.takeIf { it.isNotEmpty() },
            reasoningEffort = config.reasoningEffort.takeIf { it in listOf("low", "medium", "high") }
        )

        if (config.supportsStreaming) {
            val fullContent = StringBuilder()
            val thinkingContent = StringBuilder()
            var toolCallsDetected = false
            data class Acc(var id: String, var name: String, val args: StringBuilder = StringBuilder())
            val accToolCalls = mutableMapOf<Int, Acc>()

            provider.streamChatCompletion(config.apiBaseUrl, config.apiKey, unifiedRequest)
                .collect { chunk ->
                    chunk.reasoningDelta?.let { r ->
                        thinkingContent.append(r)
                        globalThinkingContent.clear().append(thinkingContent)
                        emitted += "$THINKING_MARKER$r"
                    }
                    chunk.contentDelta?.let { c ->
                        fullContent.append(c)
                        globalFullContent.clear().append(fullContent)
                        emitted += c
                    }
                    chunk.toolCalls?.forEach { tc ->
                        toolCallsDetected = true
                        val acc = accToolCalls.getOrPut(tc.index) { Acc("", "") }
                        if (tc.id.isNotBlank()) acc.id = tc.id
                        if (tc.name.isNotBlank()) acc.name = tc.name
                        if (tc.argumentsDelta.isNotEmpty()) acc.args.append(tc.argumentsDelta)
                    }
                    if (chunk.finishReason == "tool_calls") toolCallsDetected = true
                    chunk.usage?.let {
                        TokenTracker.record(
                            Usage(it.promptTokens, it.completionTokens, it.totalTokens),
                            config.modelName, sessionId
                        )
                    }
                }

            if (toolCallsDetected && accToolCalls.isNotEmpty()) {
                val toolCalls = accToolCalls.entries.sortedBy { it.key }.map { (idx, acc) ->
                    ToolCall(id = acc.id.ifBlank { "call_$idx" }, index = idx,
                        function = FunctionCall(name = acc.name, arguments = acc.args.toString()))
                }
                messages.add(ChatMessage(role = ChatRole.ASSISTANT,
                    content = fullContent.toString().takeIf { it.isNotBlank() }, toolCalls = toolCalls))
                for (tc in toolCalls) {
                    val params = parseToolArguments(tc.function.arguments)
                    val marker = emitCallingWithArgs(tc.function.name, params)
                    globalToolLog.append(marker); emitted += marker
                    val result = toolExecutor.execute(tc.function.name, params, sessionId)
                    result.artifacts.forEach { pendingArtifacts += tc.function.name to it }
                    toolCallRecords.add(SkillLearner.ToolCallRecord(tc.function.name,
                        params.mapValues { it.value?.toString() ?: "" }, result, toolCallRecords.size))
                    messages.add(ChatMessage(role = ChatRole.TOOL,
                        content = result.output.ifBlank { result.error ?: "No output" }, toolCallId = tc.id))
                    val rm = "[Tool result: ${if (result.success) "success" else "failed"}]\n"
                    globalToolLog.append(rm); emitted += rm
                }
                return UnifiedIterationResult(emitted, messages, shouldBreak = false, shouldContinue = true)
            }

            val savedContent = buildString {
                if (globalToolLog.isNotBlank()) append(globalToolLog)
                if (thinkingContent.isNotBlank()) append("<think>\n$thinkingContent\n</think>\n\n")
                append(VendorResponseSanitizer.stripPseudoFunctionCallBlocks(fullContent.toString()))
            }
            val messageId = chatRepository.addMessage(sessionId,
                ChatMessage(role = ChatRole.ASSISTANT, content = savedContent))
            pendingArtifacts.groupBy({ it.first }, { it.second }).forEach { (toolName, artifacts) ->
                chatRepository.saveToolArtifacts(sessionId, messageId, toolName, artifacts)
            }
            pendingArtifacts.clear()
            globalFullContent.clear(); globalThinkingContent.clear(); globalToolLog.clear()
            return UnifiedIterationResult(emitted, messages, shouldBreak = true, shouldContinue = false)
        } else {
            val result = provider.chatCompletion(config.apiBaseUrl, config.apiKey, unifiedRequest)
            if (result.isFailure) {
                val err = "API 请求失败: ${result.exceptionOrNull()?.message}"
                emitted += err
                return UnifiedIterationResult(emitted, messages, shouldBreak = true, shouldContinue = false)
            }
            val response = result.getOrThrow()
            response.usage?.let {
                TokenTracker.record(Usage(it.promptTokens, it.completionTokens, it.totalTokens),
                    config.modelName, sessionId)
            }
            if (!response.toolCalls.isNullOrEmpty()) {
                val toolCalls = response.toolCalls.mapIndexed { idx, tc ->
                    ToolCall(id = tc.id, index = idx, function = FunctionCall(name = tc.name, arguments = tc.arguments))
                }
                messages.add(ChatMessage(role = ChatRole.ASSISTANT, content = response.content, toolCalls = toolCalls))
                for (tc in toolCalls) {
                    val params = parseToolArguments(tc.function.arguments)
                    val marker = emitCallingWithArgs(tc.function.name, params)
                    globalToolLog.append(marker); emitted += marker
                    val toolResult = toolExecutor.execute(tc.function.name, params, sessionId)
                    toolResult.artifacts.forEach { pendingArtifacts += tc.function.name to it }
                    toolCallRecords.add(SkillLearner.ToolCallRecord(tc.function.name,
                        params.mapValues { it.value?.toString() ?: "" }, toolResult, toolCallRecords.size))
                    messages.add(ChatMessage(role = ChatRole.TOOL,
                        content = toolResult.output.ifBlank { toolResult.error ?: "No output" }, toolCallId = tc.id))
                    val rm = "[Tool result: ${if (toolResult.success) "success" else "failed"}]\n"
                    globalToolLog.append(rm); emitted += rm
                }
                return UnifiedIterationResult(emitted, messages, shouldBreak = false, shouldContinue = true)
            }
            val content = response.content ?: ""
            response.reasoningContent?.let {
                globalThinkingContent.clear().append(it)
                emitted += "$THINKING_MARKER$it"
            }
            emitted += VendorResponseSanitizer.stripPseudoFunctionCallBlocks(content)
            val savedContent = buildString {
                if (globalToolLog.isNotBlank()) append(globalToolLog)
                response.reasoningContent?.let { append("<think>\n$it\n</think>\n\n") }
                append(VendorResponseSanitizer.stripPseudoFunctionCallBlocks(content))
            }
            val messageId = chatRepository.addMessage(sessionId,
                ChatMessage(role = ChatRole.ASSISTANT, content = savedContent))
            pendingArtifacts.groupBy({ it.first }, { it.second }).forEach { (toolName, artifacts) ->
                chatRepository.saveToolArtifacts(sessionId, messageId, toolName, artifacts)
            }
            pendingArtifacts.clear()
            globalFullContent.clear(); globalThinkingContent.clear(); globalToolLog.clear()
            return UnifiedIterationResult(emitted, messages, shouldBreak = true, shouldContinue = false)
        }
    }

    companion object {
        /** 工具数量较少时检索收益小，直接全量下发 */
        private const val DYNAMIC_TOOL_MIN_REGISTRY_SIZE = 8
    }
}

data class AgentState(
    val isProcessing: Boolean = false,
    val currentToolCall: String? = null,
    val error: String? = null
)

const val THINKING_MARKER = "§T§"
