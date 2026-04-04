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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.orizon.openkiwi.core.security.EmergencyStop
import com.orizon.openkiwi.data.local.entity.SessionEntity
import com.orizon.openkiwi.ui.components.MarkdownText
import com.orizon.openkiwi.ui.components.MessageBubble
import com.orizon.openkiwi.ui.components.MessageUiModel
import com.orizon.openkiwi.ui.components.ThinkingSection
import com.orizon.openkiwi.ui.components.ToolAction
import com.orizon.openkiwi.ui.components.ToolCallCard
import com.orizon.openkiwi.ui.chat.ToolDisplayRegistry
import android.content.Context
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    reopenDrawer: Boolean = false,
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
    onNavigateToRecipes: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {},
    onNavigateToCanvas: () -> Unit = {}
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

    LaunchedEffect(reopenDrawer) {
        if (reopenDrawer) {
            drawerState.open()
        }
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
                onMcpClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToMcp() } },
                onWorkspaceClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToWorkspace() } },
                onCanvasClick = { scope.launch { drawerState.snapTo(DrawerValue.Closed); onNavigateToCanvas() } },
                pendingNoteCount = pendingNoteCount
            )
        }
    ) {
        Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(
                                        WindowInsets.statusBars
                                            .union(WindowInsets.displayCutout)
                                            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                                    )
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (!drawerState.isAnimationRunning) {
                                            scope.launch { drawerState.open() }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Menu, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }

                                Spacer(Modifier.weight(1f))

                                Text(
                                    "OpenKiwi",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(Modifier.weight(1f))

                                IconButton(
                                    onClick = { viewModel.createNewSession() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "\u7D27\u6025\u505C\u6B62\u5DF2\u6FC0\u6D3B",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { EmergencyStop.reset() }) { Text("\u6062\u590D") }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    )
                }

                if (!accessibilityRunning) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "\u65E0\u969C\u788D\u670D\u52A1\u672A\u5F00\u542F",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("\u53BB\u5F00\u542F") }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }

                uiState.error?.let { error ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.proactiveHint != null) {
                    uiState.proactiveHint?.let { hint ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { viewModel.clearProactiveHint() },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearProactiveHint() },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
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
                    onStopGeneration = {
                        EmergencyStop.activate()
                        viewModel.stopGeneration()
                    },
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
                    onOpenCanvas = onNavigateToCanvas,
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
    onStopGeneration: () -> Unit,
    showDevWorkspace: Boolean,
    waitingSeconds: Int,
    onOpenArtifact: (ArtifactUiModel) -> Unit,
    onShareArtifact: (ArtifactUiModel) -> Unit,
    onOpenCanvas: () -> Unit = {},
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 48.dp)
            ) {
                Text(
                    "OpenKiwi",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(
                    modifier = Modifier.width(60.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "\u6709\u4EC0\u4E48\u53EF\u4EE5\u5E2E\u4F60\uFF1F",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
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
                                onStopGeneration = onStopGeneration,
                                isProcessing = isProcessing,
                                showDevWorkspace = showDevWorkspace,
                                onOpenCanvas = onOpenCanvas
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
                                onShareArtifact = onShareArtifact,
                                onOpenCanvas = onOpenCanvas
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
    onStopGeneration: () -> Unit,
    isProcessing: Boolean,
    showDevWorkspace: Boolean,
    onOpenCanvas: () -> Unit = {}
) {
    val context = LocalContext.current
    val codeBlock = remember(textContent) { extractLatestCodeBlock(textContent) }
    val hasCode = codeBlock.isNotBlank()
    val hasTools = toolCalls.isNotEmpty()
    val shouldShowWorkspace = showDevWorkspace && (hasCode || hasTools)

    var editorText by remember(codeBlock) { mutableStateOf(codeBlock) }
    val terminalText = remember(textContent, toolCalls, context) { buildTerminalPreview(context, textContent, toolCalls) }

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
                ToolCallCard(
                    action = ToolAction(tc.name, tc.status, tc.args),
                    onOpenCanvas = onOpenCanvas
                )
                Spacer(Modifier.height(6.dp))
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
                    isStreaming = true,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onUseAsDraft(textContent) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) { Text("继续编辑", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = onStopGeneration,
                        enabled = isProcessing,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) { Text("停止并接受", style = MaterialTheme.typography.labelSmall) }
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
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terminal, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("\u5DE5\u4F5C\u533A", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
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
    context: Context,
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
        val s = ToolDisplayRegistry.resolve(context, tc.name, tc.args)
        "${idx + 1}. [$symbol] ${s.emoji} ${s.title}"
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 4.dp)
    ) {
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
                        label = "\u56FE\u7247\u5DF2\u9644\u52A0",
                        tint = MaterialTheme.colorScheme.primary,
                        onRemove = { attachedImageUri = null }
                    )
                }
                if (attachedFileUri != null) {
                    AttachmentChip(
                        icon = Icons.Outlined.InsertDriveFile,
                        label = attachedFileName.take(20),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onRemove = { attachedFileUri = null; attachedFileName = "" }
                    )
                }
                if (attachedVideoUri != null) {
                    AttachmentChip(
                        icon = Icons.Outlined.Videocam,
                        label = attachedVideoName.take(20),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onRemove = { attachedVideoUri = null; attachedVideoName = "" }
                    )
                }
            }
        }

        var showMenu by remember { mutableStateOf(false) }

        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.width(160.dp)
                    ) {
                        InputMenuItem(
                            icon = Icons.Outlined.Image,
                            label = "\u56FE\u7247",
                            onClick = { imagePickerLauncher.launch("image/*"); showMenu = false }
                        )
                        InputMenuItem(
                            icon = Icons.Outlined.Videocam,
                            label = "\u89C6\u9891",
                            onClick = { videoPickerLauncher.launch("video/*"); showMenu = false }
                        )
                        InputMenuItem(
                            icon = Icons.Outlined.AttachFile,
                            label = "\u6587\u4EF6",
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")); showMenu = false }
                        )
                        InputMenuItem(
                            icon = if (isListening) Icons.Default.MicOff else Icons.Outlined.Mic,
                            label = if (isListening) "\u505C\u6B62\u5F55\u97F3" else "\u8BED\u97F3\u8F93\u5165",
                            active = isListening,
                            onClick = { onMicClick(); showMenu = false }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        InputMenuItem(
                            icon = Icons.Outlined.Adb,
                            label = "\u5BC4\u751F\u6A21\u5F0F",
                            active = parasiticEnabled,
                            showToggle = true,
                            onClick = {
                                parasiticEnabled = !parasiticEnabled
                                com.orizon.openkiwi.core.agent.ParasiticQueryTool.enabled = parasiticEnabled
                            }
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (text.isEmpty()) {
                                    Text(
                                        if (isListening) "\u6B63\u5728\u8046\u542C\u2026" else "\u8F93\u5165\u6D88\u606F\u2026",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                }

                Spacer(Modifier.width(8.dp))

                if (isProcessing) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Stop, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(
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
                        enabled = text.isNotBlank() || attachedImageUri != null || attachedFileUri != null || attachedVideoUri != null,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward, null,
                            modifier = Modifier.size(20.dp),
                            tint = if (text.isNotBlank() || attachedImageUri != null || attachedFileUri != null || attachedVideoUri != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
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
    onMcpClick: () -> Unit = {},
    onWorkspaceClick: () -> Unit = {},
    onCanvasClick: () -> Unit = {},
    pendingNoteCount: Int = 0
) {
    var toolsExpanded by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding(),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onBackground,
        drawerShape = RoundedCornerShape(0.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "\u5BF9\u8BDD",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onNewChat, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (sessions.isEmpty()) {
                item {
                    Text(
                        "\u6682\u65E0\u5BF9\u8BDD",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp)
                    )
                }
            }
            items(sessions, key = { it.id }) { session ->
                val isSelected = session.id == currentSessionId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSessionClick(session.id) }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                            else Color.Transparent
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        session.title.ifBlank { "\u65B0\u5BF9\u8BDD" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { onDeleteSession(session.id) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { toolsExpanded = !toolsExpanded }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "\u529F\u80FD",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                if (toolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = toolsExpanded) {
            Column {
                DrawerToolRow("\u4EFB\u52A1", onTasksClick, "\u5B9A\u65F6", onScheduleClick)
                DrawerToolRow("\u81EA\u52A8\u5316", onRecipesClick, "MCP", onMcpClick)
                DrawerToolRow("\u8BB0\u5FC6", onMemoryClick, "\u6280\u80FD", onSkillsClick)
                DrawerToolRow(
                    if (pendingNoteCount > 0) "\u901A\u77E5($pendingNoteCount)" else "\u901A\u77E5",
                    onNotesClick,
                    "\u6587\u4EF6",
                    onArtifactsClick
                )
                DrawerToolRow("\u7EC8\u7AEF", onTerminalClick, "\u8BBE\u5907", onDevicesClick)
                DrawerToolRow("\u5DE5\u5177", onToolsClick, "\u8BED\u97F3", onVoiceClick)
                DrawerToolRow("\u65E5\u5FD7", onAuditLogClick, "\u5DE5\u4F5C\u533A", onWorkspaceClick)
                DrawerToolRow("\u753B\u5E03", onCanvasClick, null, null)
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSettingsClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text("\u8BBE\u7F6E", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DrawerToolRow(
    label1: String, onClick1: (() -> Unit)?,
    label2: String?, onClick2: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onClick1 != null) {
            Text(
                label1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick1)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (label2 != null && onClick2 != null) {
            Text(
                label2,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick2)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(6.dp))
}
