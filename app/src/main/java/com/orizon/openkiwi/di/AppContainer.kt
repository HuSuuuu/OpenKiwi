package com.orizon.openkiwi.di

import android.content.Context
import com.orizon.openkiwi.core.agent.AgentCommunicationBus
import com.orizon.openkiwi.core.agent.AgentEngine
import com.orizon.openkiwi.core.agent.AgentWorkspace
import com.orizon.openkiwi.core.agent.AppReplyBotTool
import com.orizon.openkiwi.core.agent.ParasiticQueryTool
import com.orizon.openkiwi.core.agent.SubAgentManager
import com.orizon.openkiwi.core.code.CodeSandbox
import com.orizon.openkiwi.core.code.TerminalSessionManager
import com.orizon.openkiwi.core.device.BluetoothGattSessionManager
import com.orizon.openkiwi.core.device.NfcSessionManager
import com.orizon.openkiwi.core.device.DeviceDiscovery
import com.orizon.openkiwi.core.device.SSHClient
import com.orizon.openkiwi.core.device.USBHostManager
import com.orizon.openkiwi.core.device.USBSerialDriver
import com.orizon.openkiwi.core.device.VNCClient
import com.orizon.openkiwi.core.memory.MemoryManager
import com.orizon.openkiwi.core.model.ModelManager
import com.orizon.openkiwi.core.model.RateLimiter
import com.orizon.openkiwi.core.model.SmartModelDispatcher
import com.orizon.openkiwi.core.notification.AutoReplyManager
import com.orizon.openkiwi.core.notification.NotificationProcessor
import com.orizon.openkiwi.core.plugin.DynamicPluginLoader
import com.orizon.openkiwi.core.plugin.PluginManager
import com.orizon.openkiwi.core.security.AnomalyDetector
import com.orizon.openkiwi.core.security.BiometricAuthManager
import com.orizon.openkiwi.core.security.OperationRollback
import com.orizon.openkiwi.core.security.PrivacyManager
import com.orizon.openkiwi.core.skill.SkillExecutor
import com.orizon.openkiwi.core.rag.RagSearchTool
import com.orizon.openkiwi.core.recipe.RecipeExecutor
import com.orizon.openkiwi.core.recipe.RecipeManager
import com.orizon.openkiwi.core.reminder.ReminderManager
import com.orizon.openkiwi.core.schedule.ScheduleManager
import com.orizon.openkiwi.core.skill.SkillLearner
import com.orizon.openkiwi.core.voice.VoiceWakeCommandBus
import com.orizon.openkiwi.core.skill.SkillManager
import com.orizon.openkiwi.core.tool.PipelineManager
import com.orizon.openkiwi.core.tool.ToolExecutor
import com.orizon.openkiwi.core.tool.ToolRegistry
import com.orizon.openkiwi.core.tool.builtin.*
import com.orizon.openkiwi.core.gui.GuiActionParser
import com.orizon.openkiwi.core.gui.GuiActionExecutor
import com.orizon.openkiwi.core.gui.GuiAgent
import com.orizon.openkiwi.core.gui.GuiAgentTool
import com.orizon.openkiwi.data.local.AppDatabase
import com.orizon.openkiwi.data.preferences.UserPreferences
import com.orizon.openkiwi.data.repository.ChatRepository
import com.orizon.openkiwi.data.repository.ArtifactRepository
import com.orizon.openkiwi.data.repository.ModelRepository
import com.orizon.openkiwi.core.llm.LlmProviderFactory
import com.orizon.openkiwi.core.mcp.McpManager
import com.orizon.openkiwi.core.openclaw.OpenClawPluginManager
import com.orizon.openkiwi.core.openclaw.OpenClawSavedGateway
import com.orizon.openkiwi.core.openclaw.OpenClawSkillRegistry
import com.orizon.openkiwi.data.repository.McpServerRepository
import com.orizon.openkiwi.network.ApiAuth
import com.orizon.openkiwi.network.ApiRouter
import com.orizon.openkiwi.network.CompanionServer
import com.orizon.openkiwi.network.FeishuApiClient
import com.orizon.openkiwi.network.FeishuConfig
import com.orizon.openkiwi.network.FeishuLarkWsClient
import com.orizon.openkiwi.network.HttpClientFactory
import com.orizon.openkiwi.network.OpenAIApiClient
import com.orizon.openkiwi.network.VolcanoVoiceClient
import com.orizon.openkiwi.service.CallControlService
import com.orizon.openkiwi.service.ClipboardMonitorService
import com.orizon.openkiwi.service.ContinuousListenerService
import com.orizon.openkiwi.service.TextToSpeechService
import com.orizon.openkiwi.service.VoiceRecognitionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 应用级依赖容器：单例组装数据库、网络、 [ToolRegistry]、[AgentEngine] 与后台协程。
 *
 * **工具注册**分两段：① [toolRegistry] 初始化块中的大量内置工具；② [init] 中依赖其他组件的迟注册工具与后台任务。
 * 分区注释见类内；架构总览见项目根目录 `docs/ARCHITECTURE.md`。
 */
class AppContainer(context: Context) {

    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val apiJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    val database = AppDatabase.getInstance(context)
    val userPreferences = UserPreferences(context)

    val httpClient = HttpClientFactory.create(enableLogging = true)
    val apiClient = OpenAIApiClient(httpClient, apiJson)
    val llmProviderFactory = LlmProviderFactory(httpClient, apiClient)

    val chatRepository = ChatRepository(database.sessionDao(), database.messageDao(), database.artifactDao())
    val artifactRepository = ArtifactRepository(database.artifactDao())
    val modelRepository = ModelRepository(database.modelConfigDao())

    // Voice & Call
    val voiceRecognitionService = VoiceRecognitionService(context)
    val textToSpeechService = TextToSpeechService(context)
    val volcanoVoiceClient = VolcanoVoiceClient(httpClient)
    val callControlService = CallControlService(context)
    val continuousListener = ContinuousListenerService(context)

    // Devices
    val deviceDiscovery = DeviceDiscovery(context)
    val usbHostManager = USBHostManager(context)
    val usbSerialDriver = USBSerialDriver(context)
    val sshClient = SSHClient()
    val vncClient = VNCClient()
    val feishuApiClient = FeishuApiClient(httpClient)

    // BLE
    val bleSessionManager = BluetoothGattSessionManager(context)

    // NFC
    val nfcSessionManager = NfcSessionManager()

    // Code execution
    val codeSandbox = CodeSandbox(context)
    val terminalSessionManager = TerminalSessionManager()

    // Skills & Plugins
    val skillManager = SkillManager(database.skillDao())
    val dynamicPluginLoader = DynamicPluginLoader(context)

    // Core model management
    val rateLimiter = RateLimiter()
    val modelManager = ModelManager(modelRepository)
    val smartModelDispatcher = SmartModelDispatcher(modelRepository)

    // Security
    val anomalyDetector = AnomalyDetector(database.auditLogDao())
    val biometricAuthManager = BiometricAuthManager(context)
    val operationRollback = OperationRollback()
    val privacyManager = PrivacyManager(context, database)

    val memoryManager = MemoryManager(database.memoryDao())
    val agentWorkspace = AgentWorkspace(context).also { it.initialize() }

    val reminderManager = ReminderManager(context, database.reminderDao())

    val openClawSkillRegistry = OpenClawSkillRegistry(context).also { it.initialize() }

    /**
     * 模型可见的工具注册表；大部分内置工具在下方 `apply { ... }` 中注册，另有部分在 `init` 中迟注册。
     */
    val toolRegistry = ToolRegistry().apply {
        // System tools
        register(SystemInfoTool())
        register(ClipboardTool(context))
        register(ShellCommandTool())
        register(TerminalTool(terminalSessionManager))
        register(AppManagerTool(context))
        register(FileManagerTool(context))
        register(IntentTool(context))

        // GUI tools
        register(GUIOperationTool())
        register(ScreenCaptureTool(context))

        // Network tools
        register(WebFetchTool(httpClient))
        register(WebSearchTool(httpClient))
        register(DownloadTool(context, httpClient))
        register(FTPTool())

        // Communication tools
        register(PhoneSmsTool(context))
        register(ContactsTool(context))
        register(NotificationTool())
        register(CallControlTool(callControlService))

        // Sensor & hardware tools
        register(LocationTool(context))
        register(CameraTool(context))
        register(AudioTool(context))
        register(SensorTool(context))
        register(ConnectivityTool(context))
        register(PowerTool(context))
        register(MediaStoreTool(context))

        // Voice
        register(VoiceTool(voiceRecognitionService, textToSpeechService))

        // Memory
        register(MemoryTool(memoryManager))

        // Code execution（canonical: code_execution；code_execute 由 ToolExecutor 映射）
        register(CodeExecutionTool(codeSandbox))

        // BLE & NFC
        register(BleTool(context, bleSessionManager))
        register(NfcTool(context, nfcSessionManager))

        // Cross-device
        register(SSHTool())
        register(USBTool(usbHostManager))
        register(FeishuTool(feishuApiClient))
        register(RagSearchTool(context, database.ragChunkDao()))

        // Haptics / Alarm / Biometric / Reminder
        register(HapticsTool(context))
        register(AlarmTool(context))
        register(BiometricGateTool(biometricAuthManager))
        register(ReminderTool(context, reminderManager))

        // Calendar
        register(CalendarTool(context))

        // Workspace (self-evolution)
        register(WorkspaceTool(agentWorkspace))

        // Rich HTML canvas (WebView)
        register(CanvasTool())

        // Tool creation (lets the LLM create custom shell/python tools)
        register(CreateToolTool(database.customToolDao(), this, codeSandbox))
    }

    val pipelineManager = PipelineManager(toolRegistry)
    val toolExecutor = ToolExecutor(toolRegistry, database.auditLogDao())

    val skillExecutor = SkillExecutor(toolExecutor)
    val skillLearner = SkillLearner(skillManager)

    val notificationProcessor = NotificationProcessor(
        apiClient = apiClient,
        modelRepository = modelRepository,
        noteDao = database.noteDao(),
        userPreferences = userPreferences
    )

    val autoReplyManager = AutoReplyManager(context, userPreferences)

    val agentEngine = AgentEngine(
        apiClient = apiClient,
        toolRegistry = toolRegistry,
        toolExecutor = toolExecutor,
        memoryManager = memoryManager,
        chatRepository = chatRepository,
        modelRepository = modelRepository,
        smartModelDispatcher = smartModelDispatcher,
        skillLearner = skillLearner,
        userPreferences = userPreferences,
        llmProviderFactory = llmProviderFactory,
        agentWorkspace = agentWorkspace,
        openClawSkillRegistry = openClawSkillRegistry
    )

    val communicationBus = AgentCommunicationBus()
    val subAgentManager = SubAgentManager(
        apiClient = apiClient,
        masterToolRegistry = toolRegistry,
        toolExecutor = toolExecutor,
        memoryManager = memoryManager,
        chatRepository = chatRepository,
        modelRepository = modelRepository,
        communicationBus = communicationBus,
        llmProviderFactory = llmProviderFactory
    )

    val pluginManager = PluginManager(toolRegistry)

    val mcpServerRepository = McpServerRepository(database.mcpServerConfigDao())
    val mcpManager = McpManager(toolRegistry, httpClient)

    val openClawPluginManager = OpenClawPluginManager(toolRegistry)

    val guiActionParser = GuiActionParser()
    val guiActionExecutor = GuiActionExecutor(context)
    val guiAgent = GuiAgent(context, apiClient, modelRepository, guiActionParser, guiActionExecutor)

    val recipeManager = RecipeManager(context)
    val recipeExecutor = RecipeExecutor(guiAgent)

    val apiAuth = ApiAuth(userPreferences)
    val apiRouter = ApiRouter(
        agentEngine = agentEngine,
        chatRepository = chatRepository,
        toolRegistry = toolRegistry,
        toolExecutor = toolExecutor,
        apiAuth = apiAuth
    )

    val companionServer = CompanionServer(
        context = context,
        agentEngine = agentEngine,
        chatRepository = chatRepository,
        modelRepository = modelRepository,
        feishuApiClient = feishuApiClient,
        userPreferences = userPreferences
    )

    val feishuLarkWsClient = FeishuLarkWsClient(companionServer.feishuEventHandler)

    val scheduleManager = ScheduleManager(context)

    val configExporter = com.orizon.openkiwi.core.security.ConfigExporter(
        modelRepository = modelRepository,
        userPreferences = userPreferences,
        skillManager = skillManager,
        memoryManager = memoryManager
    )

    init {
        // —— 跨模块引用（非工具注册）——
        companionServer.apiRouter = apiRouter
        codeSandbox.companionServer = companionServer

        notificationProcessor.agentEngine = agentEngine
        notificationProcessor.chatRepository = chatRepository
        notificationProcessor.calendarTool = toolRegistry.getTool("calendar") as? CalendarTool

        // —— 迟注册工具（依赖 subAgent / guiAgent / pipeline / OpenClaw 等）——
        toolRegistry.register(SubAgentTool(subAgentManager))
        toolRegistry.register(SkillTool(skillManager, skillExecutor))
        toolRegistry.register(ScheduledTaskTool(database.scheduledTaskDao(), scheduleManager))
        toolRegistry.register(GuiAgentTool(guiAgent))
        toolRegistry.register(ParasiticQueryTool(guiAgent))
        toolRegistry.register(AppReplyBotTool(guiAgent))
        toolRegistry.register(RecipeTool(recipeManager, recipeExecutor))
        toolRegistry.register(PipelineManagementTool(pipelineManager, toolRegistry))
        toolRegistry.register(OpenClawTool(openClawPluginManager, userPreferences))
        toolRegistry.register(OpenClawSkillsTool(openClawSkillRegistry))

        // —— 异步：从数据库加载用户自定义 DynamicTool ——
        Thread {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    database.customToolDao().getEnabledTools().forEach { entity ->
                        toolRegistry.register(DynamicTool(entity, codeSandbox))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppContainer", "Failed to load dynamic tools", e)
                }
            }
        }.start()

        // —— 动态插件 JAR/DEX（manifest 扫描）——
        dynamicPluginLoader.scanWithManifests().forEach { (plugin, manifest) ->
            pluginManager.loadPlugin(plugin, manifest)
        }

        // —— 网络与设备：Companion 发现、USB ——
        companionServer.start()
        deviceDiscovery.registerOpenKiwiCompanionService(companionServer.port)
        usbHostManager.startMonitoring()

        // —— 异步：飞书首次认证 ——
        Thread {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val appId = userPreferences.getString("feishu_app_id")
                    val appSecret = userPreferences.getString("feishu_app_secret")
                    if (appId.isNotBlank() && appSecret.isNotBlank()) {
                        feishuApiClient.authenticate(
                            FeishuConfig(appId = appId, appSecret = appSecret)
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppContainer", "Feishu authentication failed", e)
                }
            }
        }.start()

        // —— 协程：飞书长连接 / 语音唤醒 / 剪贴板监听 / 定时任务同步 / MCP / OpenClaw Gateway 恢复 ——
        containerScope.launch {
            combine(
                userPreferences.feishuDirectLongConnection,
                userPreferences.feishuAppId,
                userPreferences.feishuAppSecret
            ) { direct, id, secret -> Triple(direct, id, secret) }
                .collect { (direct, id, secret) ->
                    if (direct && id.isNotBlank() && secret.isNotBlank()) {
                        runCatching {
                            feishuApiClient.authenticate(FeishuConfig(appId = id, appSecret = secret))
                        }
                        delay(400)
                        feishuLarkWsClient.start(id, secret)
                    } else {
                        feishuLarkWsClient.stop()
                    }
                }
        }

        containerScope.launch {
            combine(
                userPreferences.voiceWakeEnabled,
                userPreferences.voiceWakeWord
            ) { enabled, word ->
                enabled to (word.ifBlank { "hey kiwi" })
            }.collect { (enabled, phrase) ->
                continuousListener.stop()
                if (enabled) {
                    continuousListener.setWakeWord(phrase)
                    continuousListener.start()
                } else {
                    continuousListener.setWakeWord(null)
                }
            }
        }

        containerScope.launch {
            continuousListener.recognizedText.collect { cmd ->
                if (cmd.isNotBlank()) VoiceWakeCommandBus.tryEmit(cmd.trim())
            }
        }

        containerScope.launch {
            userPreferences.clipboardMonitorEnabled.collect { on ->
                if (on) ClipboardMonitorService.start(context) else ClipboardMonitorService.stop(context)
            }
        }

        containerScope.launch {
            scheduleManager.syncAll(database.scheduledTaskDao())
        }

        containerScope.launch {
            try {
                val mcpConfigs = mcpServerRepository.getEnabledConfigs()
                mcpManager.connectAll(mcpConfigs)
            } catch (e: Exception) {
                android.util.Log.w("AppContainer", "Failed to start MCP servers", e)
            }
        }

        containerScope.launch {
            try {
                val savedGateways = userPreferences.getString("openclaw_gateways")
                if (savedGateways.isNotBlank()) {
                    val entries = Json.decodeFromString<List<OpenClawSavedGateway>>(savedGateways)
                    for (entry in entries) {
                        openClawPluginManager.connect(entry.id, entry.url, entry.token)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AppContainer", "Failed to restore OpenClaw connections", e)
            }
        }
    }
}
