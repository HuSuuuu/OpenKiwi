package com.orizon.openkiwi.core.agent

import com.orizon.openkiwi.core.memory.MemoryManager
import com.orizon.openkiwi.core.model.*
import com.orizon.openkiwi.core.tool.ToolExecutor
import com.orizon.openkiwi.core.tool.ToolRegistry
import com.orizon.openkiwi.data.repository.ChatRepository
import com.orizon.openkiwi.data.repository.ModelRepository
import com.orizon.openkiwi.network.OpenAIApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SubAgentState(
    val id: String,
    val config: SubAgentConfig,
    val status: String = "IDLE",
    val lastResult: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class SubAgentManager(
    private val apiClient: OpenAIApiClient,
    private val masterToolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val memoryManager: MemoryManager,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository
) {
    private val agents = ConcurrentHashMap<String, SubAgentState>()
    private val agentJobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _agentStates = MutableStateFlow<Map<String, SubAgentState>>(emptyMap())
    val agentStates: StateFlow<Map<String, SubAgentState>> = _agentStates.asStateFlow()

    fun createAgent(config: SubAgentConfig): String {
        val id = config.id.ifBlank { UUID.randomUUID().toString() }
        val state = SubAgentState(id = id, config = config.copy(id = id))
        agents[id] = state
        _agentStates.value = agents.toMap()
        return id
    }

    suspend fun executeTask(agentId: String, task: String): Flow<String> = flow {
        val state = agents[agentId] ?: run {
            emit("Error: SubAgent $agentId not found")
            return@flow
        }

        agents[agentId] = state.copy(status = "RUNNING")
        _agentStates.value = agents.toMap()

        val config = state.config
        val modelConfig = if (config.modelConfigId.isNotBlank()) {
            modelRepository.getConfig(config.modelConfigId)
        } else {
            modelRepository.getDefaultConfig()
        }

        if (modelConfig == null) {
            emit("Error: No model configured for SubAgent")
            agents[agentId] = state.copy(status = "FAILED", lastResult = "No model")
            _agentStates.value = agents.toMap()
            return@flow
        }

        val filteredRegistry = ToolRegistry().apply {
            if (config.enabledTools.isEmpty()) {
                masterToolRegistry.getAllTools().forEach { register(it) }
            } else {
                config.enabledTools.forEach { name ->
                    masterToolRegistry.getTool(name)?.let { register(it) }
                }
            }
        }

        val sessionId = chatRepository.createSession(
            modelConfigId = modelConfig.id,
            systemPrompt = config.systemPrompt,
            title = "SubAgent: ${config.name}"
        )

        val subEngine = AgentEngine(
            apiClient = apiClient,
            toolRegistry = filteredRegistry,
            toolExecutor = ToolExecutor(filteredRegistry),
            memoryManager = memoryManager,
            chatRepository = chatRepository,
            modelRepository = modelRepository
        )

        val resultBuilder = StringBuilder()
        subEngine.processMessage(sessionId, task, modelConfig).collect { chunk ->
            resultBuilder.append(chunk)
            emit(chunk)
        }

        agents[agentId] = state.copy(status = "COMPLETED", lastResult = resultBuilder.toString())
        _agentStates.value = agents.toMap()
    }

    fun destroyAgent(agentId: String) {
        agentJobs[agentId]?.cancel()
        agentJobs.remove(agentId)
        agents.remove(agentId)
        _agentStates.value = agents.toMap()
    }

    fun listAgents(): List<SubAgentState> = agents.values.toList()

    fun getAgent(agentId: String): SubAgentState? = agents[agentId]

    fun destroyAll() {
        agentJobs.values.forEach { it.cancel() }
        agentJobs.clear()
        agents.clear()
        _agentStates.value = emptyMap()
    }
}
