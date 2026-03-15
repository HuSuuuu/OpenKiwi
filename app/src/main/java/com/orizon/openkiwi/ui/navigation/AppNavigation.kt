package com.orizon.openkiwi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.orizon.openkiwi.OpenKiwiApp
import com.orizon.openkiwi.ui.chat.ChatScreen
import com.orizon.openkiwi.ui.chat.ChatViewModel
import com.orizon.openkiwi.ui.devices.DeviceScreen
import com.orizon.openkiwi.ui.devices.DeviceViewModel
import com.orizon.openkiwi.ui.logs.AuditLogScreen
import com.orizon.openkiwi.ui.logs.AuditLogViewModel
import com.orizon.openkiwi.ui.memory.MemoryScreen
import com.orizon.openkiwi.ui.memory.MemoryViewModel
import com.orizon.openkiwi.ui.model.ModelConfigScreen
import com.orizon.openkiwi.ui.model.ModelConfigViewModel
import com.orizon.openkiwi.ui.settings.SettingsScreen
import com.orizon.openkiwi.ui.skills.SkillsScreen
import com.orizon.openkiwi.ui.skills.SkillsViewModel
import com.orizon.openkiwi.ui.task.TaskScreen
import com.orizon.openkiwi.ui.task.TaskViewModel
import com.orizon.openkiwi.ui.terminal.TerminalScreen
import com.orizon.openkiwi.ui.terminal.TerminalViewModel
import com.orizon.openkiwi.ui.note.NoteScreen
import com.orizon.openkiwi.ui.note.NoteViewModel
import com.orizon.openkiwi.ui.tool.ToolScreen
import com.orizon.openkiwi.ui.tool.ToolViewModel
import com.orizon.openkiwi.ui.voice.VoiceScreen
import com.orizon.openkiwi.ui.voice.VoiceViewModel

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val MODEL_CONFIG = "model_config"
    const val TASKS = "tasks"
    const val MEMORY = "memory"
    const val SKILLS = "skills"
    const val DEVICES = "devices"
    const val AUDIT_LOG = "audit_log"
    const val TERMINAL = "terminal"
    const val VOICE = "voice"
    const val NOTES = "notes"
    const val TOOLS = "tools"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val container = OpenKiwiApp.instance.container

    NavHost(navController = navController, startDestination = Routes.CHAT) {
        composable(Routes.CHAT) {
            val vm: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(
                    container.agentEngine,
                    container.chatRepository,
                    container.modelRepository,
                    OpenKiwiApp.instance.applicationContext
                )
            )
            ChatScreen(
                viewModel = vm,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNavigateToModelConfig = { navController.navigate(Routes.MODEL_CONFIG) { launchSingleTop = true } },
                onNavigateToTasks = { navController.navigate(Routes.TASKS) { launchSingleTop = true } },
                onNavigateToMemory = { navController.navigate(Routes.MEMORY) { launchSingleTop = true } },
                onNavigateToSkills = { navController.navigate(Routes.SKILLS) { launchSingleTop = true } },
                onNavigateToDevices = { navController.navigate(Routes.DEVICES) { launchSingleTop = true } },
                onNavigateToAuditLog = { navController.navigate(Routes.AUDIT_LOG) { launchSingleTop = true } },
                onNavigateToTerminal = { navController.navigate(Routes.TERMINAL) { launchSingleTop = true } },
                onNavigateToVoice = { navController.navigate(Routes.VOICE) { launchSingleTop = true } },
                onNavigateToNotes = { navController.navigate(Routes.NOTES) { launchSingleTop = true } },
                onNavigateToTools = { navController.navigate(Routes.TOOLS) { launchSingleTop = true } }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToModelConfig = { navController.navigate(Routes.MODEL_CONFIG) }
            )
        }

        composable(Routes.MODEL_CONFIG) {
            val vm: ModelConfigViewModel = viewModel(
                factory = ModelConfigViewModel.Factory(container.modelManager)
            )
            ModelConfigScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.TASKS) {
            val vm: TaskViewModel = viewModel(
                factory = TaskViewModel.Factory(container.database.auditLogDao())
            )
            TaskScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.MEMORY) {
            val vm: MemoryViewModel = viewModel(
                factory = MemoryViewModel.Factory(container.memoryManager, container.database.memoryDao())
            )
            MemoryScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.SKILLS) {
            val vm: SkillsViewModel = viewModel(
                factory = SkillsViewModel.Factory(container.skillManager)
            )
            SkillsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.DEVICES) {
            val vm: DeviceViewModel = viewModel(
                factory = DeviceViewModel.Factory(container.deviceDiscovery, container.usbHostManager)
            )
            DeviceScreen(viewModel = vm, onBack = { navController.popBackStack() }, companionServer = container.companionServer)
        }

        composable(Routes.AUDIT_LOG) {
            val vm: AuditLogViewModel = viewModel(
                factory = AuditLogViewModel.Factory(container.database.auditLogDao())
            )
            AuditLogScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.TERMINAL) {
            val vm: TerminalViewModel = viewModel(factory = TerminalViewModel.Factory())
            TerminalScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.VOICE) {
            val vm: VoiceViewModel = viewModel(
                factory = VoiceViewModel.Factory(container.volcanoVoiceClient, container.userPreferences)
            )
            VoiceScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.NOTES) {
            val vm: NoteViewModel = viewModel(
                factory = NoteViewModel.Factory(container.database.noteDao())
            )
            NoteScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.TOOLS) {
            val vm: ToolViewModel = viewModel(
                factory = ToolViewModel.Factory(
                    container.database.customToolDao(),
                    container.toolRegistry
                )
            )
            ToolScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
