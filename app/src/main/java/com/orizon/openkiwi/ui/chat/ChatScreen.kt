package com.orizon.openkiwi.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.orizon.openkiwi.core.security.EmergencyStop
import com.orizon.openkiwi.data.local.entity.SessionEntity
import com.orizon.openkiwi.ui.components.MarkdownText
import com.orizon.openkiwi.ui.components.MessageBubble
import com.orizon.openkiwi.ui.components.MessageUiModel
import com.orizon.openkiwi.ui.components.ThinkingSection
import com.orizon.openkiwi.ui.components.ToolAction
import com.orizon.openkiwi.ui.components.ToolCallChip
import com.orizon.openkiwi.OpenKiwiApp
import com.orizon.openkiwi.service.KiwiAccessibilityService
import com.orizon.openkiwi.ui.components.SetupGuideDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToModelConfig: () -> Unit,
    onNavigateToTasks: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    onNavigateToDevices: () -> Unit = {},
    onNavigateToAuditLog: () -> Unit = {},
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToTools: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessions by viewModel.sessions.collectAsState(initial = emptyList())
    val drawerState = remember { DrawerState(DrawerValue.Closed) }
    val scope = rememberCoroutineScope()
    val emergencyStopped by EmergencyStop.isActive.collectAsState()
    val accessibilityRunning by KiwiAccessibilityService.isRunning.collectAsState()
    val noteDao = remember { OpenKiwiApp.instance.container.database.noteDao() }
    val pendingNoteCount by noteDao.getPendingCount().collectAsState(initial = 0)
    val context = LocalContext.current
    val prefs = OpenKiwiApp.instance.container.userPreferences
    var showSetupGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val setupDone = prefs.setupCompleted.first()
        if (!setupDone) showSetupGuide = true
    }

    if (showSetupGuide) {
        SetupGuideDialog(onDismiss = {
            showSetupGuide = false
            scope.launch { prefs.setSetupCompleted(true) }
        })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        drawerContent = {
            SessionDrawer(
                sessions = sessions,
                currentSessionId = uiState.currentSessionId,
                onSessionClick = { id ->
                    viewModel.switchSession(id)
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { viewModel.deleteSession(it) },
                onNewChat = {
                    viewModel.createNewSession()
                    scope.launch { drawerState.close() }
                },
                onSettingsClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToSettings() } },
                onTasksClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToTasks() } },
                onMemoryClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToMemory() } },
                onSkillsClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToSkills() } },
                onDevicesClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToDevices() } },
                onAuditLogClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToAuditLog() } },
                onTerminalClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToTerminal() } },
                onVoiceClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToVoice() } },
                onNotesClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToNotes() } },
                onToolsClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToTools() } },
                pendingNoteCount = pendingNoteCount
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            uiState.sessionTitle,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!drawerState.isAnimationRunning) {
                                scope.launch { drawerState.open() }
                            }
                        }) {
                            BadgedBox(
                                badge = {
                                    if (pendingNoteCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ) {
                                            Text(
                                                if (pendingNoteCount > 99) "99+" else pendingNoteCount.toString(),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Menu, null)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToModelConfig) {
                            Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { viewModel.createNewSession() }) {
                            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(20.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
            ) {
                if (emergencyStopped) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "紧急停止已激活",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { EmergencyStop.reset() }) { Text("恢复") }
                        }
                    }
                }

                if (!accessibilityRunning) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.AccessibilityNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "无障碍服务未开启，屏幕操作功能不可用",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }) { Text("去开启") }
                        }
                    }
                }

                uiState.error?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                MessageList(
                    messages = uiState.messages,
                    streamingContent = uiState.streamingContent,
                    streamingThinking = uiState.streamingThinking,
                    isProcessing = uiState.isProcessing,
                    activeToolCalls = uiState.activeToolCalls,
                    modifier = Modifier.weight(1f)
                )

                ChatInputBar(
                    isProcessing = uiState.isProcessing,
                    onSend = { viewModel.sendMessage(it) },
                    onStop = {
                        EmergencyStop.activate()
                        viewModel.stopGeneration()
                    },
                    onMicClick = { viewModel.toggleVoiceInput() },
                    isListening = uiState.isListening,
                    onImageSelected = { uri -> viewModel.setImageAttachment(uri) },
                    onFileSelected = { uri -> viewModel.setFileAttachment(uri) }
                )
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<MessageUiModel>,
    streamingContent: String,
    streamingThinking: String = "",
    isProcessing: Boolean,
    activeToolCalls: List<ToolCallStatus> = emptyList(),
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hasToolCalls = activeToolCalls.isNotEmpty()
    val hasStreaming = streamingContent.isNotBlank() || streamingThinking.isNotBlank() || hasToolCalls

    val reversedMessages = buildList {
        if (isProcessing && streamingContent.isBlank() && !hasToolCalls) {
            add(MessageUiModel(role = "THINKING_INDICATOR", content = "", id = -1L))
        }
        if (hasStreaming) {
            add(MessageUiModel(
                role = "ASSISTANT",
                content = streamingContent,
                thinking = streamingThinking,
                isStreaming = true
            ))
        }
        addAll(messages.asReversed())
    }

    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    var userScrolledUp by remember { mutableStateOf(false) }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) userScrolledUp = false
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAtBottom) {
            userScrolledUp = true
        }
    }

    LaunchedEffect(reversedMessages.size) {
        if (reversedMessages.isNotEmpty() && !userScrolledUp) {
            listState.scrollToItem(0)
        }
    }

    if (reversedMessages.isEmpty() && !isProcessing) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "K",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "有什么可以帮你？",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    } else {
        Box(modifier = modifier) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                reverseLayout = true
            ) {
                items(reversedMessages, key = { "${it.id}_${it.isStreaming}_${it.role}" }) { message ->
                    when {
                        message.role == "THINKING_INDICATOR" -> ThinkingIndicator()
                        message.isStreaming && (hasToolCalls || message.thinking.isNotBlank()) ->
                            StreamingMessageWithToolCalls(
                                textContent = message.content,
                                thinking = message.thinking,
                                toolCalls = activeToolCalls
                            )
                        else -> MessageBubble(message = message)
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = userScrolledUp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        userScrolledUp = false
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )

    Row(
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val delay = i * 150
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.2f, targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = delay), RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Surface(
                modifier = Modifier.size(6.dp).alpha(dotAlpha),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            ) {}
            if (i < 2) Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun StreamingMessageWithToolCalls(
    textContent: String,
    thinking: String = "",
    toolCalls: List<ToolCallStatus>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.92f)) {
            if (thinking.isNotBlank()) {
                ThinkingSection(thinking = thinking, isStreaming = true)
                Spacer(Modifier.height(6.dp))
            }
            toolCalls.forEach { tc ->
                ToolCallChip(ToolAction(tc.name, tc.status))
                Spacer(Modifier.height(4.dp))
            }
            if (textContent.isNotBlank()) {
                MarkdownText(
                    markdown = textContent,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    isProcessing: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onMicClick: () -> Unit = {},
    isListening: Boolean = false,
    onImageSelected: ((Uri) -> Unit)? = null,
    onFileSelected: ((Uri) -> Unit)? = null
) {
    var text by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            attachedImageUri = it
            onImageSelected?.invoke(it)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onFileSelected?.invoke(it) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (attachedImageUri != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Image, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("图片已附加", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { attachedImageUri = null },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column {
                    androidx.compose.foundation.text.BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp, max = 140.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (text.isEmpty()) {
                                    Text(
                                        if (isListening) "正在聆听..." else "输入消息...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onMicClick, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isListening) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Image, null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.AttachFile, null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        if (isProcessing) {
                            FilledIconButton(
                                onClick = onStop,
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            FilledIconButton(
                                onClick = {
                                    if (text.isNotBlank() || attachedImageUri != null) {
                                        onSend(text)
                                        text = ""
                                        attachedImageUri = null
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                                enabled = text.isNotBlank() || attachedImageUri != null,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            ) {
                                Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDrawer(
    sessions: List<SessionEntity>,
    currentSessionId: String?,
    onSessionClick: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onNewChat: () -> Unit,
    onSettingsClick: () -> Unit,
    onTasksClick: () -> Unit = {},
    onMemoryClick: () -> Unit = {},
    onSkillsClick: () -> Unit = {},
    onDevicesClick: () -> Unit = {},
    onAuditLogClick: () -> Unit = {},
    onTerminalClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onNotesClick: () -> Unit = {},
    onToolsClick: () -> Unit = {},
    pendingNoteCount: Int = 0
) {
    ModalDrawerSheet(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("对话", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onNewChat, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (sessions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无对话",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
            items(sessions, key = { it.id }) { session ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            session.title.ifBlank { "新对话" },
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    selected = session.id == currentSessionId,
                    onClick = { onSessionClick(session.id) },
                    badge = {
                        if (session.id == currentSessionId) {
                            IconButton(
                                onClick = { onDeleteSession(session.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp).height(40.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        Spacer(Modifier.height(4.dp))

        DrawerMenuItem(Icons.Outlined.Assignment, "任务", onTasksClick)
        DrawerMenuItem(Icons.Outlined.Psychology, "记忆", onMemoryClick)
        DrawerMenuItem(Icons.Outlined.AutoFixHigh, "技能", onSkillsClick)
        DrawerMenuItem(Icons.Outlined.Devices, "设备", onDevicesClick)
        DrawerMenuItemWithBadge(Icons.Outlined.Notifications, "通知", onNotesClick, pendingNoteCount)
        DrawerMenuItem(Icons.Outlined.Build, "工具", onToolsClick)
        DrawerMenuItem(Icons.Outlined.Terminal, "终端", onTerminalClick)
        DrawerMenuItem(Icons.Outlined.RecordVoiceOver, "语音", onVoiceClick)
        DrawerMenuItem(Icons.Outlined.Security, "日志", onAuditLogClick)

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        DrawerMenuItem(Icons.Outlined.Settings, "设置", onSettingsClick)

        val localIp = remember {
            try {
                java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                    ?.flatMap { it.inetAddresses.toList() }
                    ?.firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                    ?.hostAddress ?: "未连接"
            } catch (_: Exception) { "未知" }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Wifi, null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "IP: $localIp:8765",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DrawerMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.padding(horizontal = 12.dp).height(40.dp),
        shape = RoundedCornerShape(8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerMenuItemWithBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    badgeCount: Int
) {
    NavigationDrawerItem(
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (badgeCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(
                            if (badgeCount > 99) "99+" else badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        selected = false,
        onClick = onClick,
        icon = {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge(
                            modifier = Modifier.size(8.dp),
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            ) {
                Icon(icon, null, modifier = Modifier.size(18.dp))
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp).height(40.dp),
        shape = RoundedCornerShape(8.dp)
    )
}
