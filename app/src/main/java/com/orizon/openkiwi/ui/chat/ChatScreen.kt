package com.orizon.openkiwi.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
import com.orizon.openkiwi.ui.components.ArtifactUiModel
import com.orizon.openkiwi.OpenKiwiApp
import com.orizon.openkiwi.service.KiwiAccessibilityService
import com.orizon.openkiwi.ui.components.SetupGuideDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.orizon.openkiwi.util.ArtifactOpener
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor

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
    onNavigateToTools: () -> Unit = {},
    onNavigateToArtifacts: () -> Unit = {},
    onNavigateToSchedule: () -> Unit = {},
    onNavigateToRecipes: () -> Unit = {}
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
    var showDevWorkspace by remember { mutableStateOf(true) }
    var waitingSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.isProcessing) {
        waitingSeconds = 0
        if (uiState.isProcessing) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                waitingSeconds++
            }
        }
    }
    LaunchedEffect(uiState.streamingContent) {
        if (uiState.streamingContent.isNotBlank()) waitingSeconds = 0
    }

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
                onArtifactsClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToArtifacts() } },
                onScheduleClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToSchedule() } },
                onRecipesClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToRecipes() } },
                pendingNoteCount = pendingNoteCount
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(com.orizon.openkiwi.ui.theme.LuminaBackground)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x330A84FF),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.15f, h * 0.50f),
                            radius = w * 0.6f
                        ),
                        radius = w * 0.6f,
                        center = Offset(w * 0.15f, h * 0.50f)
                    )
                    
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x3334C759),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.85f, h * 0.30f),
                            radius = w * 0.6f
                        ),
                        radius = w * 0.6f,
                        center = Offset(w * 0.85f, h * 0.30f)
                    )
                }
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Surface(
                        color = com.orizon.openkiwi.ui.theme.LuminaGlassDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = com.orizon.openkiwi.ui.theme.LuminaGlassBorder,
                                shape = androidx.compose.ui.graphics.RectangleShape
                            )
                    ) {
                        // IconButton 默认最小触摸区 48dp，会与自定义小尺寸重叠；关闭后按真实 size 排版
                        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 状态栏 + 刘海/挖孔（避免右上角相机孔遮挡按钮）
                                    .windowInsetsPadding(
                                        WindowInsets.statusBars
                                            .union(WindowInsets.displayCutout)
                                            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                                    )
                                    // 右侧略多留空，兼容部分机型挖孔报告不全
                                    .padding(start = 18.dp, end = 22.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (!drawerState.isAnimationRunning) {
                                            scope.launch { drawerState.open() }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(com.orizon.openkiwi.ui.theme.LuminaGlassLight, CircleShape)
                                        .border(1.dp, com.orizon.openkiwi.ui.theme.LuminaGlassBorder, CircleShape)
                                ) {
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
                                        Icon(Icons.Default.Menu, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                Spacer(Modifier.width(20.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "OpenKiwi",
                                            maxLines = 1,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4285F4),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        "Always active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }

                                Spacer(Modifier.width(20.dp))

                                IconButton(
                                    onClick = onNavigateToModelConfig,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(com.orizon.openkiwi.ui.theme.LuminaGlassLight, CircleShape)
                                        .border(1.dp, com.orizon.openkiwi.ui.theme.LuminaGlassBorder, CircleShape)
                                ) {
                                    Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(Modifier.width(18.dp))
                                IconButton(
                                    onClick = { viewModel.createNewSession() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(com.orizon.openkiwi.ui.theme.LuminaGlassLight, CircleShape)
                                        .border(1.dp, com.orizon.openkiwi.ui.theme.LuminaGlassBorder, CircleShape)
                                ) {
                                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
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
                    onRetry = viewModel::retryFromMessage,
                    onBranch = viewModel::branchFromMessage,
                    onEditAsDraft = viewModel::editMessageAsDraft,
                    onUseStreamingAsDraft = viewModel::updateDraft,
                    showDevWorkspace = showDevWorkspace,
                    waitingSeconds = waitingSeconds,
                    onOpenArtifact = { artifact ->
                        ArtifactOpener.open(context, artifact.path, artifact.mimeType)
                            .onFailure { Toast.makeText(context, it.message ?: "无法打开文件", Toast.LENGTH_SHORT).show() }
                    },
                    onShareArtifact = { artifact ->
                        ArtifactOpener.share(context, artifact.path, artifact.mimeType)
                            .onFailure { Toast.makeText(context, it.message ?: "无法分享文件", Toast.LENGTH_SHORT).show() }
                    },
                    modifier = Modifier.weight(1f)
                )

                ChatInputBar(
                    isProcessing = uiState.isProcessing,
                    text = uiState.draftText,
                    onTextChange = viewModel::updateDraft,
                    onSend = { viewModel.sendMessage(uiState.draftText) },
                    onStop = {
                        EmergencyStop.activate()
                        viewModel.stopGeneration()
                    },
                    onMicClick = { viewModel.toggleVoiceInput() },
                    isListening = uiState.isListening,
                    onImageSelected = { uri -> viewModel.setImageAttachment(uri) },
                    onFileSelected = { uri -> viewModel.setFileAttachment(uri) },
                    onVideoSelected = { uri -> viewModel.setVideoAttachment(uri) }
                )
            }
        }
        }
    }
}

private fun extractLatestCodeBlock(text: String): String {
    if (text.isBlank()) return ""
    val regex = Regex("```(?:[a-zA-Z0-9_+-]+)?\\n([\\s\\S]*?)```")
    return regex.findAll(text).lastOrNull()?.groupValues?.getOrNull(1)?.trim().orEmpty()
}

@Composable
private fun MessageList(
    messages: List<MessageUiModel>,
    streamingContent: String,
    streamingThinking: String = "",
    isProcessing: Boolean,
    activeToolCalls: List<ToolCallStatus> = emptyList(),
    onRetry: (Long) -> Unit,
    onBranch: (Long) -> Unit,
    onEditAsDraft: (Long) -> Unit,
    onUseStreamingAsDraft: (String) -> Unit,
    showDevWorkspace: Boolean,
    waitingSeconds: Int,
    onOpenArtifact: (ArtifactUiModel) -> Unit,
    onShareArtifact: (ArtifactUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hasToolCalls = activeToolCalls.isNotEmpty()
    val hasStreaming = streamingContent.isNotBlank() || streamingThinking.isNotBlank() || hasToolCalls

    val groupedTurns = messages
        .groupBy { it.turnId }
        .toSortedMap()
        .values
        .toList()
    val reversedTurns = groupedTurns.asReversed()

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

    LaunchedEffect(reversedMessages.size, reversedTurns.size) {
        if ((reversedMessages.isNotEmpty() || reversedTurns.isNotEmpty()) && !userScrolledUp) {
            listState.scrollToItem(0)
        }
    }

    if (reversedMessages.isEmpty() && reversedTurns.isEmpty() && !isProcessing) {
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
                        message.role == "THINKING_INDICATOR" -> ThinkingIndicator(waitingSeconds)
                        message.isStreaming && (hasToolCalls || message.thinking.isNotBlank()) ->
                            StreamingMessageWithToolCalls(
                                textContent = message.content,
                                thinking = message.thinking,
                                toolCalls = activeToolCalls,
                                onUseAsDraft = onUseStreamingAsDraft,
                                showDevWorkspace = showDevWorkspace
                            )
                        message.isStreaming -> MessageBubble(message = message)
                    }
                }
                items(reversedTurns, key = { turn -> turn.firstOrNull()?.turnId ?: -1L }) { turn ->
                    Column {
                        turn.forEach { message ->
                            MessageBubble(
                                message = message,
                                onRetry = if (message.role == "ASSISTANT") onRetry else null,
                                onBranch = if (message.role == "ASSISTANT") onBranch else null,
                                onEditAsDraft = onEditAsDraft,
                                onOpenArtifact = onOpenArtifact,
                                onShareArtifact = onShareArtifact
                            )
                        }
                        Spacer(Modifier.height(8.dp))
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
private fun ThinkingIndicator(waitingSeconds: Int = 0) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")

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
        if (waitingSeconds > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "${waitingSeconds}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun StreamingMessageWithToolCalls(
    textContent: String,
    thinking: String = "",
    toolCalls: List<ToolCallStatus>,
    onUseAsDraft: (String) -> Unit,
    showDevWorkspace: Boolean
) {
    val codeBlock = remember(textContent) { extractLatestCodeBlock(textContent) }
    val hasCode = codeBlock.isNotBlank()
    val hasTools = toolCalls.isNotEmpty()
    val shouldShowWorkspace = showDevWorkspace && (hasCode || hasTools)

    var editorText by remember(codeBlock) { mutableStateOf(codeBlock) }
    val terminalText = remember(textContent, toolCalls) { buildTerminalPreview(textContent, toolCalls) }

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
            if (shouldShowWorkspace) {
                InlineDevWorkspace(
                    editorText = editorText,
                    terminalText = terminalText,
                    onEditorChange = { editorText = it },
                    onUseCodeAsDraft = { onUseAsDraft("```python\n$editorText\n```") }
                )
                Spacer(Modifier.height(8.dp))
            }
            if (textContent.isNotBlank()) {
                MarkdownText(
                    markdown = textContent,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onUseAsDraft(textContent) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) { Text("继续编辑", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = {},
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) { Text("接受", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun InlineDevWorkspace(
    editorText: String,
    terminalText: String,
    onEditorChange: (String) -> Unit,
    onUseCodeAsDraft: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terminal, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("对话内工作区", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("编辑器 + 终端", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }

            Spacer(Modifier.height(8.dp))
            Text("Python 编辑器", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicTextField(
                    value = editorText,
                    onValueChange = onEditorChange,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row {
                FilledTonalButton(
                    onClick = onUseCodeAsDraft,
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text("把代码放到输入框", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("终端输出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = terminalText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun buildTerminalPreview(
    streamingText: String,
    toolCalls: List<ToolCallStatus>
): String {
    val statusLines = toolCalls.mapIndexed { idx, tc ->
        val symbol = when (tc.status) {
            "running" -> "..."
            "success" -> "ok"
            "failed" -> "xx"
            else -> "->"
        }
        "${idx + 1}. [$symbol] ${tc.name}"
    }
    val markerLines = streamingText
        .lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("[Calling tool:") || it.startsWith("[Tool result:") }
        .toList()
        .takeLast(8)

    val merged = (statusLines + markerLines).ifEmpty { listOf("等待工具执行输出...") }
    return merged.joinToString("\n")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    isProcessing: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicClick: () -> Unit = {},
    isListening: Boolean = false,
    onImageSelected: ((Uri) -> Unit)? = null,
    onFileSelected: ((Uri) -> Unit)? = null,
    onVideoSelected: ((Uri) -> Unit)? = null
) {
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var attachedFileUri by remember { mutableStateOf<Uri?>(null) }
    var attachedFileName by remember { mutableStateOf("") }
    var attachedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var attachedVideoName by remember { mutableStateOf("") }
    var parasiticEnabled by remember { mutableStateOf(com.orizon.openkiwi.core.agent.ParasiticQueryTool.enabled) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            attachedImageUri = it
            onImageSelected?.invoke(it)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            attachedFileUri = it
            val name = try {
                val cursor = context.contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx) else null
                    } else null
                } ?: it.lastPathSegment ?: "文件"
            } catch (_: Exception) { it.lastPathSegment ?: "文件" }
            attachedFileName = name
            onFileSelected?.invoke(it)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            attachedVideoUri = it
            val name = try {
                val cursor = context.contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx) else null
                    } else null
                } ?: it.lastPathSegment ?: "视频"
            } catch (_: Exception) { it.lastPathSegment ?: "视频" }
            attachedVideoName = name
            onVideoSelected?.invoke(it)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
    ) {
        Column {
            // Attachment indicators
            val hasAttachments = attachedImageUri != null || attachedFileUri != null || attachedVideoUri != null
            if (hasAttachments) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (attachedImageUri != null) {
                        AttachmentChip(
                            icon = Icons.Outlined.Image,
                            label = "图片已附加",
                            tint = MaterialTheme.colorScheme.primary,
                            onRemove = { attachedImageUri = null }
                        )
                    }
                    if (attachedFileUri != null) {
                        AttachmentChip(
                            icon = Icons.Outlined.InsertDriveFile,
                            label = attachedFileName.take(20),
                            tint = MaterialTheme.colorScheme.tertiary,
                            onRemove = { attachedFileUri = null; attachedFileName = "" }
                        )
                    }
                    if (attachedVideoUri != null) {
                        AttachmentChip(
                            icon = Icons.Outlined.Videocam,
                            label = attachedVideoName.take(20),
                            tint = MaterialTheme.colorScheme.secondary,
                            onRemove = { attachedVideoUri = null; attachedVideoName = "" }
                        )
                    }
                }
            }

            // Input bar with left-side menu
            var showMenu by remember { mutableStateOf(false) }

            Surface(
                color = com.orizon.openkiwi.ui.theme.LuminaGlassDark,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, com.orizon.openkiwi.ui.theme.LuminaGlassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left-side "+" menu trigger
                    Box {
                        IconButton(
                            onClick = { showMenu = !showMenu },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (showMenu) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = if (showMenu) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.width(160.dp)
                        ) {
                            InputMenuItem(
                                icon = Icons.Outlined.Image,
                                label = "图片",
                                onClick = { imagePickerLauncher.launch("image/*"); showMenu = false }
                            )
                            InputMenuItem(
                                icon = Icons.Outlined.Videocam,
                                label = "视频",
                                onClick = { videoPickerLauncher.launch("video/*"); showMenu = false }
                            )
                            InputMenuItem(
                                icon = Icons.Outlined.AttachFile,
                                label = "文件",
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")); showMenu = false }
                            )
                            InputMenuItem(
                                icon = if (isListening) Icons.Default.MicOff else Icons.Outlined.Mic,
                                label = if (isListening) "停止录音" else "语音输入",
                                active = isListening,
                                onClick = { onMicClick(); showMenu = false }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            InputMenuItem(
                                icon = Icons.Outlined.Adb,
                                label = "寄生模式",
                                active = parasiticEnabled,
                                showToggle = true,
                                onClick = {
                                    parasiticEnabled = !parasiticEnabled
                                    com.orizon.openkiwi.core.agent.ParasiticQueryTool.enabled = parasiticEnabled
                                }
                            )
                            InputMenuItem(
                                icon = Icons.Outlined.ChatBubbleOutline,
                                label = "微信/QQ 操控",
                                onClick = {
                                    val hint = "请调用 app_reply_bot：app=wechat 或 qq，instruction 写清要打开的聊天和要发的内容；mode=draft 可只打字不发送。"
                                    onTextChange(if (text.isBlank()) hint else "$text\n$hint")
                                    showMenu = false
                                }
                            )
                        }
                    }

                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(com.orizon.openkiwi.ui.theme.LuminaAccentGreen),
                        decorationBox = { innerTextField ->
                            Box {
                                if (text.isEmpty()) {
                                    Text(
                                        if (isListening) "正在聆听..." else "Message OpenKiwi...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (isProcessing) {
                        FilledIconButton(
                            onClick = onStop,
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                if (text.isNotBlank() || attachedImageUri != null || attachedFileUri != null || attachedVideoUri != null) {
                                    onSend()
                                    attachedImageUri = null
                                    attachedFileUri = null
                                    attachedFileName = ""
                                    attachedVideoUri = null
                                    attachedVideoName = ""
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            enabled = text.isNotBlank() || attachedImageUri != null || attachedFileUri != null || attachedVideoUri != null,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = com.orizon.openkiwi.ui.theme.LuminaAccentGreen,
                                disabledContainerColor = com.orizon.openkiwi.ui.theme.LuminaGlassLight
                            ),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun InputMenuItem(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    showToggle: Boolean = false,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    icon, null,
                    modifier = Modifier.size(18.dp),
                    tint = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (showToggle) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                }
            }
        },
        onClick = onClick,
        modifier = Modifier.height(40.dp)
    )
}

@Composable
private fun AttachmentChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    onRemove: () -> Unit
) {
    Surface(
        color = tint.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = tint)
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onArtifactsClick: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
    onRecipesClick: () -> Unit = {},
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
        DrawerMenuItem(Icons.Outlined.Schedule, "定时", onScheduleClick)
        DrawerMenuItem(Icons.Outlined.Checklist, "自动化配方", onRecipesClick)
        DrawerMenuItem(Icons.Outlined.Psychology, "记忆", onMemoryClick)
        DrawerMenuItem(Icons.Outlined.AutoFixHigh, "技能", onSkillsClick)
        DrawerMenuItem(Icons.Outlined.Devices, "设备", onDevicesClick)
        DrawerMenuItemWithBadge(Icons.Outlined.Notifications, "通知", onNotesClick, pendingNoteCount)
        DrawerMenuItem(Icons.Outlined.Build, "工具", onToolsClick)
        DrawerMenuItem(Icons.Outlined.InsertDriveFile, "生成文件", onArtifactsClick)
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
