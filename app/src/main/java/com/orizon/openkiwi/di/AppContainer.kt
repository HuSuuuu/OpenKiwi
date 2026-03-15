package com.orizon.openkiwi.di

import android.content.Context
import com.orizon.openkiwi.core.agent.AgentCommunicationBus
import com.orizon.openkiwi.core.agent.AgentEngine
import com.orizon.openkiwi.core.agent.SubAgentManager
import com.orizon.openkiwi.core.code.CodeSandbox
import com.orizon.openkiwi.core.code.TerminalSessionManager
import com.orizon.openkiwi.core.device.DeviceDiscovery
import com.orizon.openkiwi.core.device.SSHClient
import com.orizon.openkiwi.core.device.USBHostManager
import com.orizon.openkiwi.core.device.USBSerialDriver
import com.orizon.openkiwi.core.device.VNCClient
import com.orizon.openkiwi.core.memory.MemoryManager
import com.orizon.openkiwi.core.model.ModelManager
import com.orizon.openkiwi.core.model.RateLimiter
import com.orizon.openkiwi.core.model.SmartModelDispatcher
import com.orizon.openkiwi.core.notification.NotificationProcessor
import com.orizon.openkiwi.core.plugin.DynamicPluginLoader
import com.orizon.openkiwi.core.plugin.PluginManager
import com.orizon.openkiwi.core.security.AnomalyDetector
import com.orizon.openkiwi.core.security.BiometricAuthManager
import com.orizon.openkiwi.core.security.OperationRollback
import com.orizon.openkiwi.core.security.PrivacyManager
import com.orizon.openkiwi.core.skill.SkillExecutor
import com.orizon.openkiwi.core.skill.SkillLearner
import com.orizon.openkiwi.core.skill.SkillManager
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
import com.orizon.openkiwi.data.repository.ModelRepository
import com.orizon.openkiwi.network.CompanionServer
import com.orizon.openkiwi.network.FeishuApiClient
import com.orizon.openkiwi.network.HttpClientFactory
import com.orizon.openkiwi.network.OpenAIApiClient
import com.orizon.openkiwi.network.VolcanoVoiceClient
import com.orizon.openkiwi.service.CallControlService
import com.orizon.openkiwi.service.ContinuousListenerService
import com.orizon.openkiwi.service.TextToSpeechService
import com.orizon.openkiwi.service.VoiceRecognitionService
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {

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

    val chatRepository = ChatRepository(database.sessionDao(), database.messageDao())
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

    val toolRegistry = ToolRegistry().apply {
        // System tools
        register(SystemInfoTool())
        register(ClipboardTool(context))
        register(ShellCommandTool())
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

        // Code execution
        register(CodeExecutionTool(codeSandbox))

        // Cross-device
        register(SSHTool())
        register(USBTool(usbHostManager))
        register(FeishuTool(feishuApiClient))

        // Tool creation (lets the LLM create custom tools)
        register(CreateToolTool(database.customToolDao(), this))
    }

    val toolExecutor = ToolExecutor(toolRegistry, database.auditLogDao())

    val skillExecutor = SkillExecutor(toolExecutor)
    val skillLearner = SkillLearner(skillManager)

    val notificationProcessor = NotificationProcessor(
        apiClient = apiClient,
        modelRepository = modelRepository,
        noteDao = database.noteDao(),
        userPreferences = userPreferences
    )

    val agentEngine = AgentEngine(
        apiClient = apiClient,
        toolRegistry = toolRegistry,
        toolExecutor = toolExecutor,
        memoryManager = memoryManager,
        chatRepository = chatRepository,
        modelRepository = modelRepository,
        smartModelDispatcher = smartModelDispatcher,
        skillLearner = skillLearner
    )

    val communicationBus = AgentCommunicationBus()
    val subAgentManager = SubAgentManager(
        apiClient = apiClient,
        masterToolRegistry = toolRegistry,
        toolExecutor = toolExecutor,
        memoryManager = memoryManager,
        chatRepository = chatRepository,
        modelRepository = modelRepository
    )

    val pluginManager = PluginManager(toolRegistry)

    val guiActionParser = GuiActionParser()
    val guiActionExecutor = GuiActionExecutor(context)
    val guiAgent = GuiAgent(context, apiClient, modelRepository, guiActionParser, guiActionExecutor)

    val companionServer = CompanionServer(
        context = context,
        agentEngine = agentEngine,
        chatRepository = chatRepository,
        modelRepository = modelRepository,
        feishuApiClient = feishuApiClient,
        userPreferences = userPreferences
    )

    val configExporter = com.orizon.openkiwi.core.security.ConfigExporter(
        modelRepository = modelRepository,
        userPreferences = userPreferences,
        skillManager = skillManager,
        memoryManager = memoryManager
    )

    init {
        toolRegistry.register(SubAgentTool(subAgentManager))
        toolRegistry.register(SkillTool(skillManager, skillExecutor))
        toolRegistry.register(GuiAgentTool(guiAgent))

        Thread {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    database.customToolDao().getEnabledTools().forEach { entity ->
                        toolRegistry.register(DynamicTool(entity))
                    }
                } catch (_: Exception) {}
            }
        }.start()

        dynamicPluginLoader.scanAndLoadPlugins().forEach { plugin ->
            pluginManager.loadPlugin(plugin)
        }

        companionServer.start()

        Thread {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val appId = userPreferences.getString("feishu_app_id")
                    val appSecret = userPreferences.getString("feishu_app_secret")
                    if (appId.isNotBlank() && appSecret.isNotBlank()) {
                        feishuApiClient.authenticate(
                            com.orizon.openkiwi.network.FeishuConfig(appId = appId, appSecret = appSecret)
                        )
                    }
                } catch (_: Exception) {}
            }
        }.start()
    }
}
