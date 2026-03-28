package com.orizon.openkiwi.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.orizon.openkiwi.service.overlay.DeviceOverlayService
import com.orizon.openkiwi.service.overlay.MemoryOverlayService
import com.orizon.openkiwi.service.overlay.NotificationOverlayService
import com.orizon.openkiwi.service.overlay.TerminalOverlayService
import com.orizon.openkiwi.service.overlay.VoiceOverlayService
import androidx.core.app.NotificationManagerCompat
import com.orizon.openkiwi.ui.components.isAccessibilityServiceEnabled
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToModelConfig: () -> Unit
) {
    val context = LocalContext.current
    val prefs = com.orizon.openkiwi.OpenKiwiApp.instance.container.userPreferences
    val scope = rememberCoroutineScope()

    var themeMode by remember { mutableStateOf("system") }
    var enableStreaming by remember { mutableStateOf(true) }
    var dynamicToolRetrieval by remember { mutableStateOf(false) }
    var confirmDangerous by remember { mutableStateOf(true) }
    var enableAuditLog by remember { mutableStateOf(true) }
    var maxContextMessages by remember { mutableIntStateOf(50) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val tokenSummary by com.orizon.openkiwi.core.model.TokenTracker.summary.collectAsState()

    LaunchedEffect(Unit) {
        prefs.themeMode.collect { themeMode = it }
    }
    LaunchedEffect(Unit) {
        prefs.enableStreaming.collect { enableStreaming = it }
    }
    LaunchedEffect(Unit) {
        prefs.dynamicToolRetrieval.collect { dynamicToolRetrieval = it }
    }
    LaunchedEffect(Unit) {
        prefs.confirmDangerousOps.collect { confirmDangerous = it }
    }
    LaunchedEffect(Unit) {
        prefs.enableAuditLog.collect { enableAuditLog = it }
    }
    LaunchedEffect(Unit) {
        prefs.maxContextMessages.collect { maxContextMessages = it }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("将永久删除所有本地数据（对话、记忆、日志），此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val privacy = com.orizon.openkiwi.core.security.PrivacyManager(
                            context, com.orizon.openkiwi.OpenKiwiApp.instance.container.database
                        )
                        privacy.deleteAllUserData()
                        showDeleteConfirm = false
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("\u8BBE\u7F6E", style = MaterialTheme.typography.titleLarge, letterSpacing = 1.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            PermissionSettingsSection()

            VoiceWakeSettingsSection()

            ClipboardMonitorSettingsSection()

            SettingsSection(title = "模型") {
                SettingsItem(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "模型配置",
                    subtitle = "添加和管理 LLM 服务商",
                    onClick = onNavigateToModelConfig
                )
                SettingsSwitchItem(
                    icon = Icons.Outlined.Stream,
                    title = "流式输出",
                    subtitle = "实时显示生成内容",
                    checked = enableStreaming,
                    onCheckedChange = { enableStreaming = it; scope.launch { prefs.setEnableStreaming(it) } }
                )
                SettingsSwitchItem(
                    icon = Icons.Outlined.ManageSearch,
                    title = "工具检索后动态加载",
                    subtitle = "用本机 BM25 按对话选一批工具定义再请求模型，省 token；关则每次下发全部已启用工具",
                    checked = dynamicToolRetrieval,
                    onCheckedChange = {
                        dynamicToolRetrieval = it
                        scope.launch { prefs.setDynamicToolRetrieval(it) }
                    }
                )
            }

            SettingsSection(title = "外观") {
                // Font picker
                var currentFont by remember { mutableStateOf("default") }
                LaunchedEffect(Unit) { prefs.fontFamily.collect { currentFont = it } }
                var fontExpanded by remember { mutableStateOf(false) }

                Box {
                    SettingsItem(
                        icon = Icons.Outlined.TextFields,
                        title = "字体",
                        subtitle = com.orizon.openkiwi.ui.theme.AppFonts.labels[currentFont] ?: "默认",
                        onClick = { fontExpanded = true }
                    )
                    DropdownMenu(expanded = fontExpanded, onDismissRequest = { fontExpanded = false }) {
                        com.orizon.openkiwi.ui.theme.AppFonts.labels.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            label,
                                            fontFamily = com.orizon.openkiwi.ui.theme.AppFonts.fromKey(key)
                                        )
                                        if (key == currentFont) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Default.CheckCircle, null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                },
                                onClick = {
                                    currentFont = key
                                    scope.launch { prefs.setFontFamily(key) }
                                    fontExpanded = false
                                }
                            )
                        }
                    }
                }

                // Accent color picker
                var currentAccent by remember { mutableStateOf("green") }
                LaunchedEffect(Unit) { prefs.accentColor.collect { currentAccent = it } }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Palette, null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Spacer(Modifier.width(16.dp))
                        Text("配色", style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        com.orizon.openkiwi.ui.theme.AccentColors.all.forEach { (key, color) ->
                            val selected = key == currentAccent
                            Surface(
                                onClick = {
                                    currentAccent = key
                                    scope.launch { prefs.setAccentColor(key) }
                                },
                                shape = CircleShape,
                                color = color,
                                border = if (selected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface)
                                else BorderStroke(1.dp, color.copy(alpha = 0.3f)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (selected) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        com.orizon.openkiwi.ui.theme.AccentColors.labels[currentAccent] ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // Language picker
                var currentLang by remember { mutableStateOf("zh") }
                LaunchedEffect(Unit) {
                    val saved = prefs.getString("app_language")
                    if (saved.isNotBlank()) currentLang = saved
                }
                var langExpanded by remember { mutableStateOf(false) }
                val languages = listOf("zh" to "中文", "en" to "English")

                Box {
                    SettingsItem(
                        icon = Icons.Outlined.Language,
                        title = "语言 / Language",
                        subtitle = languages.firstOrNull { it.first == currentLang }?.second ?: "中文",
                        onClick = { langExpanded = true }
                    )
                    DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        languages.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label)
                                        if (key == currentLang) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Default.CheckCircle, null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                },
                                onClick = {
                                    currentLang = key
                                    scope.launch { prefs.setString("app_language", key) }
                                    langExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            SettingsSection(title = "智能体") {
                SettingsSliderItem(
                    icon = Icons.Outlined.Memory,
                    title = "上下文长度",
                    subtitle = "最大消息数: $maxContextMessages",
                    value = maxContextMessages.toFloat(),
                    valueRange = 10f..200f,
                    steps = 18,
                    onValueChange = { maxContextMessages = it.toInt(); scope.launch { prefs.setMaxContextMessages(it.toInt()) } }
                )
            }

            SettingsSection(title = "寄生模式") {
                var parasiticEnabled by remember {
                    mutableStateOf(com.orizon.openkiwi.core.agent.ParasiticQueryTool.enabled)
                }
                val hostApps = com.orizon.openkiwi.core.agent.ParasiticQueryTool.SUPPORTED_HOSTS
                var selectedHost by remember {
                    mutableStateOf("豆包")
                }

                SettingsSwitchItem(
                    icon = Icons.Outlined.Adb,
                    title = "启用寄生模式",
                    subtitle = if (parasiticEnabled) "通过 $selectedHost App 获取 AI 回复（无需 API Key）"
                    else "自动操作其他 AI App 来获取回复",
                    checked = parasiticEnabled,
                    onCheckedChange = {
                        parasiticEnabled = it
                        com.orizon.openkiwi.core.agent.ParasiticQueryTool.enabled = it
                    }
                )

                if (parasiticEnabled) {
                    var expanded by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Outlined.Apps,
                        title = "宿主应用",
                        subtitle = selectedHost,
                        onClick = { expanded = true }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        hostApps.forEach { (name, config) ->
                            DropdownMenuItem(
                                text = { Text("${config.displayName} (${config.packageName})") },
                                onClick = {
                                    selectedHost = name
                                    com.orizon.openkiwi.core.agent.ParasiticQueryTool.enabled = true
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            SettingsSection(title = "安全") {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Security,
                    title = "危险操作确认",
                    subtitle = "执行高风险动作前需确认",
                    checked = confirmDangerous,
                    onCheckedChange = { confirmDangerous = it; scope.launch { prefs.setConfirmDangerousOps(it) } }
                )
                SettingsSwitchItem(
                    icon = Icons.Outlined.Description,
                    title = "操作日志",
                    subtitle = "记录所有智能体操作",
                    checked = enableAuditLog,
                    onCheckedChange = { enableAuditLog = it; scope.launch { prefs.setEnableAuditLog(it) } }
                )
            }

            NotificationModelSection()

            OverlaySettingsSection()

            SettingsSection(title = "外观") {
                SettingsRadioGroup(
                    icon = Icons.Outlined.Palette,
                    title = "主题",
                    options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                    selected = themeMode,
                    onSelected = { themeMode = it; scope.launch { prefs.setThemeMode(it) } }
                )
            }

            SettingsSection(title = "用量统计") {
                SettingsItem(
                    icon = Icons.Outlined.Analytics,
                    title = "Token 消耗",
                    subtitle = "总计 ${tokenSummary.totalTokens} tokens / ${tokenSummary.requestCount} 次请求"
                )
                if (tokenSummary.byModel.isNotEmpty()) {
                    tokenSummary.byModel.values.forEach { model ->
                        SettingsItem(
                            icon = Icons.Outlined.AutoAwesome,
                            title = model.modelName,
                            subtitle = "${model.totalTokens} tokens (${model.requestCount}次)"
                        )
                    }
                }
            }

            RagSettingsSection()

            FeishuSettingsSection()

            SettingsSection(title = "数据") {
                SettingsItem(
                    icon = Icons.Outlined.Backup,
                    title = "导出数据",
                    subtitle = "备份对话、记忆和配置（GDPR合规）",
                    onClick = {
                        scope.launch {
                            val privacy = com.orizon.openkiwi.core.security.PrivacyManager(
                                context, com.orizon.openkiwi.OpenKiwiApp.instance.container.database
                            )
                            val file = privacy.exportAllUserData()
                            android.widget.Toast.makeText(context, "已导出到: ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Outlined.Restore,
                    title = "导入数据",
                    subtitle = "从备份恢复"
                )
                SettingsItem(
                    icon = Icons.Outlined.DeleteForever,
                    title = "清除所有数据",
                    subtitle = "永久删除全部本地数据",
                    isDangerous = true,
                    onClick = { showDeleteConfirm = true }
                )
            }

            SettingsSection(title = "关于") {
                SettingsItem(
                    icon = Icons.Outlined.Code,
                    title = "开发者",
                    subtitle = "燃冰万象/Traintime"
                )
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "版本",
                    subtitle = "OpenKiwi v0.0.1alpha"
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    isDangerous: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDangerous) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = if (subtitle.isNotBlank()) {
            {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        } else null,
        leadingContent = {
            Icon(
                icon, null,
                modifier = Modifier.size(20.dp),
                tint = if (isDangerous) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        },
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp)
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (subtitle.isNotBlank()) {
            {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        } else null,
        leadingContent = {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
private fun SettingsSliderItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = if (subtitle.isNotBlank()) {
                {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            } else null,
            leadingContent = {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun SettingsRadioGroup(
    icon: ImageVector,
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            leadingContent = {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            },
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Row(
            modifier = Modifier.padding(start = 60.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun VoiceWakeSettingsSection() {
    val prefs = com.orizon.openkiwi.OpenKiwiApp.instance.container.userPreferences
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var wakeWord by remember { mutableStateOf("hey kiwi") }
    val listening by com.orizon.openkiwi.OpenKiwiApp.instance.container.continuousListener.isListening.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        prefs.voiceWakeEnabled.collect { enabled = it }
    }
    LaunchedEffect(Unit) {
        prefs.voiceWakeWord.collect { wakeWord = it }
    }

    SettingsSection(title = "语音唤醒") {
        SettingsSwitchItem(
            icon = Icons.Outlined.Mic,
            title = "持续聆听 + 唤醒词",
            subtitle = "说出唤醒词后继续说指令，将自动发送到当前对话。需麦克风权限。",
            checked = enabled,
            onCheckedChange = { on ->
                scope.launch { prefs.setVoiceWakeEnabled(on) }
            }
        )
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            OutlinedTextField(
                value = wakeWord,
                onValueChange = {
                    wakeWord = it
                    scope.launch { prefs.setVoiceWakeWord(it) }
                },
                label = { Text("唤醒词（英文小写）") },
                placeholder = { Text("hey kiwi") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            if (enabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (listening) "状态：正在聆听…" else "状态：准备中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun ClipboardMonitorSettingsSection() {
    val prefs = com.orizon.openkiwi.OpenKiwiApp.instance.container.userPreferences
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        prefs.clipboardMonitorEnabled.collect { enabled = it }
    }
    SettingsSection(title = "剪贴板") {
        SettingsSwitchItem(
            icon = Icons.Outlined.ContentPaste,
            title = "剪贴板快捷操作",
            subtitle = "开启后常驻通知：复制文本后可点「AI 分析 / 搜索摘要 / 翻译」发到对话（需通知权限）。",
            checked = enabled,
            onCheckedChange = { on ->
                scope.launch { prefs.setClipboardMonitorEnabled(on) }
            }
        )
    }
}

@Composable
private fun OverlaySettingsSection() {
    val context = LocalContext.current
    var hasOverlayPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }
    var terminalRunning by remember { mutableStateOf(TerminalOverlayService.isRunning()) }
    var voiceRunning by remember { mutableStateOf(VoiceOverlayService.isRunning()) }
    var notifRunning by remember { mutableStateOf(NotificationOverlayService.isRunning()) }
    var memoryRunning by remember { mutableStateOf(MemoryOverlayService.isRunning()) }
    var deviceRunning by remember { mutableStateOf(DeviceOverlayService.isRunning()) }

    SettingsSection(title = "悬浮窗") {
        if (!hasOverlayPerm) {
            SettingsItem(
                icon = Icons.Outlined.Warning,
                title = "需要悬浮窗权限",
                subtitle = "请先在上方「权限」中开启悬浮窗权限",
                isDangerous = true
            )
        } else {
            SettingsSwitchItem(
                icon = Icons.Outlined.Terminal,
                title = "终端执行悬浮窗",
                subtitle = "Shell/代码执行时自动显示",
                checked = terminalRunning,
                onCheckedChange = {
                    if (it) TerminalOverlayService.start(context) else TerminalOverlayService.stop(context)
                    terminalRunning = it
                }
            )
            SettingsSwitchItem(
                icon = Icons.Outlined.RecordVoiceOver,
                title = "语音状态悬浮窗",
                subtitle = "语音识别/播放状态指示",
                checked = voiceRunning,
                onCheckedChange = {
                    if (it) VoiceOverlayService.start(context) else VoiceOverlayService.stop(context)
                    voiceRunning = it
                }
            )
            SettingsSwitchItem(
                icon = Icons.Outlined.Notifications,
                title = "通知解析悬浮窗",
                subtitle = "实时显示通知，自动提取验证码",
                checked = notifRunning,
                onCheckedChange = {
                    if (it) NotificationOverlayService.start(context) else NotificationOverlayService.stop(context)
                    notifRunning = it
                }
            )
            SettingsSwitchItem(
                icon = Icons.Outlined.Psychology,
                title = "记忆/上下文悬浮窗",
                subtitle = "显示 Token 用量和记忆检索",
                checked = memoryRunning,
                onCheckedChange = {
                    if (it) MemoryOverlayService.start(context) else MemoryOverlayService.stop(context)
                    memoryRunning = it
                }
            )
            SettingsSwitchItem(
                icon = Icons.Outlined.Devices,
                title = "设备连接悬浮窗",
                subtitle = "USB/SSH/VNC 连接状态",
                checked = deviceRunning,
                onCheckedChange = {
                    if (it) DeviceOverlayService.start(context) else DeviceOverlayService.stop(context)
                    deviceRunning = it
                }
            )
        }
    }
}

@Composable
private fun PermissionSettingsSection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAccessibilityPermission by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }
    var hasNotificationAccess by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
                hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
                hasNotificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSection(title = "权限") {
        PermissionStatusItem(
            icon = Icons.Outlined.Accessibility,
            title = "无障碍服务",
            subtitle = "核心能力，用于读取屏幕和执行操作",
            isGranted = hasAccessibilityPermission,
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )
        PermissionStatusItem(
            icon = Icons.Outlined.PictureInPicture,
            title = "悬浮窗",
            subtitle = "任务状态、触摸指示器、思考气泡",
            isGranted = hasOverlayPermission,
            onOpenSettings = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }
        )
        PermissionStatusItem(
            icon = Icons.Outlined.NotificationsActive,
            title = "通知读取",
            subtitle = "获取验证码、快递、消息等通知",
            isGranted = hasNotificationAccess,
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )
    }
}

@Composable
private fun PermissionStatusItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onOpenSettings: () -> Unit
) {
    val grantedColor = MaterialTheme.colorScheme.primary
    val deniedColor = MaterialTheme.colorScheme.error

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = if (isGranted) grantedColor.copy(alpha = 0.12f) else deniedColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isGranted) "已开启" else "未开启",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGranted) grantedColor else deniedColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        },
        leadingContent = {
            Icon(
                icon, null,
                modifier = Modifier.size(20.dp),
                tint = if (isGranted) grantedColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        },
        trailingContent = {
            if (isGranted) {
                Icon(
                    Icons.Default.CheckCircle, null,
                    modifier = Modifier.size(20.dp),
                    tint = grantedColor
                )
            } else {
                TextButton(onClick = onOpenSettings) {
                    Text("去开启", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        modifier = Modifier
            .then(if (!isGranted) Modifier.clickable(onClick = onOpenSettings) else Modifier)
            .padding(horizontal = 8.dp)
    )
}

@Composable
private fun NotificationModelSection() {
    val container = com.orizon.openkiwi.OpenKiwiApp.instance.container
    val prefs = container.userPreferences
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(false) }
    var selectedModelId by remember { mutableStateOf("") }
    var autoReply by remember { mutableStateOf(false) }
    var autoReplyTemplate by remember { mutableStateOf("收到") }
    var autoReplyPkgs by remember { mutableStateOf("") }
    val models by container.modelRepository.getAllConfigs().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        prefs.notificationProcessing.collect { enabled = it }
    }
    LaunchedEffect(Unit) {
        prefs.notificationModelId.collect { selectedModelId = it }
    }
    LaunchedEffect(Unit) {
        prefs.notifAutoReplyEnabled.collect { autoReply = it }
    }
    LaunchedEffect(Unit) {
        prefs.notifAutoReplyTemplate.collect { autoReplyTemplate = it }
    }
    LaunchedEffect(Unit) {
        prefs.notifAutoReplyPackages.collect { autoReplyPkgs = it }
    }

    val smallModels = remember(models) { models.filter { it.isSmallModel } }
    val modelNames = remember(smallModels) {
        smallModels.associate {
            it.id to it.name.ifBlank { it.modelName }
        }
    }

    SettingsSection(title = "通知处理") {
        SettingsSwitchItem(
            icon = Icons.Outlined.NotificationsActive,
            title = "智能通知处理",
            subtitle = "用小模型分析通知重要性并生成摘要",
            checked = enabled,
            onCheckedChange = { enabled = it; scope.launch { prefs.setNotificationProcessing(it) } }
        )
        if (enabled) {
            var expanded by remember { mutableStateOf(false) }
            val currentName = modelNames[selectedModelId] ?: "未选择"

            ListItem(
                headlineContent = { Text("处理模型", style = MaterialTheme.typography.bodyLarge) },
                supportingContent = {
                    Text(
                        "仅显示标记为「小模型」的模型，请在模型配置中打标签",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Outlined.SmartToy, null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                },
                trailingContent = {
                    Box {
                        FilledTonalButton(
                            onClick = { expanded = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(currentName, style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            smallModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.name.ifBlank { model.modelName }) },
                                    onClick = {
                                        selectedModelId = model.id
                                        scope.launch { prefs.setNotificationModelId(model.id) }
                                        expanded = false
                                    },
                                    trailingIcon = {
                                        if (model.id == selectedModelId) {
                                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                            if (smallModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("请先在「模型配置」中将模型标记为小模型", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                                    onClick = { expanded = false }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        SettingsSwitchItem(
            icon = Icons.Outlined.Reply,
            title = "通知快捷自动回复",
            subtitle = "对支持「内联回复」的消息类通知发送固定文案（微信/短信等）。留空包名列表则使用内置白名单。每小时每应用有次数上限。",
            checked = autoReply,
            onCheckedChange = { on ->
                autoReply = on
                scope.launch { prefs.setNotifAutoReplyEnabled(on) }
            }
        )
        if (autoReply) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = autoReplyTemplate,
                    onValueChange = {
                        autoReplyTemplate = it
                        scope.launch { prefs.setNotifAutoReplyTemplate(it) }
                    },
                    label = { Text("回复文案") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = autoReplyPkgs,
                    onValueChange = {
                        autoReplyPkgs = it
                        scope.launch { prefs.setNotifAutoReplyPackages(it) }
                    },
                    label = { Text("包名白名单（可选，逗号分隔）") },
                    placeholder = { Text("留空=微信/QQ/短信等默认列表") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )
            }
        }
    }
}

@Composable
private fun RagSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ragDao = com.orizon.openkiwi.OpenKiwiApp.instance.container.database.ragChunkDao()
    var status by remember {
        mutableStateOf("扫描「文档」「下载」目录中的文本与常见办公文件，供工具 local_rag 检索。")
    }
    var busy by remember { mutableStateOf(false) }

    SettingsSection(title = "本地知识库 (RAG)") {
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Button(
            onClick = {
                if (busy) return@Button
                busy = true
                scope.launch {
                    status = runCatching {
                        com.orizon.openkiwi.core.rag.LocalFileIndexer.refresh(context, ragDao)
                    }.fold(
                        onSuccess = { it },
                        onFailure = { e -> "索引失败: ${e.message}" }
                    )
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(if (busy) "索引中…" else "重建索引")
        }
    }
}

@Composable
private fun FeishuSettingsSection() {
    val prefs = com.orizon.openkiwi.OpenKiwiApp.instance.container.userPreferences
    val feishuClient = com.orizon.openkiwi.OpenKiwiApp.instance.container.feishuApiClient
    val feishuWs = com.orizon.openkiwi.OpenKiwiApp.instance.container.feishuLarkWsClient
    val scope = rememberCoroutineScope()

    var feishuAppId by remember { mutableStateOf("") }
    var feishuAppSecret by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(feishuClient.isAuthenticated()) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var feishuDirectLc by remember { mutableStateOf(false) }
    val wsThreadActive by feishuWs.active.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        prefs.feishuAppId.collect { feishuAppId = it }
    }
    LaunchedEffect(Unit) {
        prefs.feishuAppSecret.collect { feishuAppSecret = it }
    }
    LaunchedEffect(Unit) {
        prefs.feishuDirectLongConnection.collect { feishuDirectLc = it }
    }

    SettingsSection(title = "飞书") {
        SettingsItem(
            icon = Icons.Outlined.Info,
            title = "接入说明",
            subtitle = "在飞书开放平台创建应用，获取 App ID 和 App Secret"
        )

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = feishuAppId,
                onValueChange = {
                    feishuAppId = it
                    scope.launch { prefs.setFeishuAppId(it) }
                },
                label = { Text("App ID") },
                placeholder = { Text("cli_xxxxxxxxxx") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = feishuAppSecret,
                onValueChange = {
                    feishuAppSecret = it
                    scope.launch { prefs.setFeishuAppSecret(it) }
                },
                label = { Text("App Secret") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                visualTransformation = if (showSecret) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(
                            if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            null, modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (feishuAppId.isBlank() || feishuAppSecret.isBlank()) {
                            testResult = "请填写 App ID 和 App Secret"
                            return@Button
                        }
                        isTesting = true
                        testResult = null
                        scope.launch {
                            val start = System.currentTimeMillis()
                            Log.i("FeishuSettings", "test connection start on ${Thread.currentThread().name}")
                            try {
                                val result = feishuClient.authenticate(
                                    com.orizon.openkiwi.network.FeishuConfig(
                                        appId = feishuAppId,
                                        appSecret = feishuAppSecret
                                    )
                                )
                                result.fold(
                                    onSuccess = {
                                        isConnected = true
                                        testResult = "连接成功"
                                        Log.i(
                                            "FeishuSettings",
                                            "test connection success in ${System.currentTimeMillis() - start}ms"
                                        )
                                    },
                                    onFailure = {
                                        isConnected = false
                                        testResult = "连接失败: ${it.message}"
                                        Log.e(
                                            "FeishuSettings",
                                            "test connection failed in ${System.currentTimeMillis() - start}ms: ${it.message}",
                                            it
                                        )
                                    }
                                )
                            } finally {
                                isTesting = false
                            }
                        }
                    },
                    enabled = !isTesting && feishuAppId.isNotBlank() && feishuAppSecret.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isTesting) "连接中..." else "测试连接")
                }
                if (isConnected) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("已连接", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            testResult?.let { result ->
                Spacer(Modifier.height(6.dp))
                Text(
                    result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.startsWith("连接成功")) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(12.dp))
            SettingsSwitchItem(
                icon = Icons.Outlined.Smartphone,
                title = "手机直连长连接",
                subtitle = "在本机建立飞书 WebSocket，无需 PC 转发。开发者后台：事件订阅 → 使用长连接接收事件（需本开关开启且凭证正确）",
                checked = feishuDirectLc,
                onCheckedChange = { on ->
                    scope.launch { prefs.setFeishuDirectLongConnection(on) }
                }
            )
            if (feishuDirectLc) {
                Text(
                    if (wsThreadActive) "直连：长连接线程运行中"
                    else "直连：等待启动（保存 App ID/Secret 后会自动连接）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(12.dp))

            Text(
                "事件订阅（接收飞书消息）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "方式一：开启上方「手机直连长连接」，或使用 Companion PC「飞书」页签启动长连接（勿同时开两个，以免重复处理）。\n" +
                "方式二：飞书开放平台 → 事件订阅 → Webhook，请求地址设为 http://<公网IP>:8765/api/feishu/event",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(8.dp))

            var verificationToken by remember { mutableStateOf("") }
            var encryptKey by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                prefs.feishuVerificationToken.collect { verificationToken = it }
            }
            LaunchedEffect(Unit) {
                prefs.feishuEncryptKey.collect { encryptKey = it }
            }

            OutlinedTextField(
                value = verificationToken,
                onValueChange = {
                    verificationToken = it
                    scope.launch { prefs.setFeishuVerificationToken(it) }
                },
                label = { Text("Verification Token") },
                placeholder = { Text("可选") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = encryptKey,
                onValueChange = {
                    encryptKey = it
                    scope.launch { prefs.setFeishuEncryptKey(it) }
                },
                label = { Text("Encrypt Key") },
                placeholder = { Text("可选") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "需订阅事件: im.message.receive_v1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
