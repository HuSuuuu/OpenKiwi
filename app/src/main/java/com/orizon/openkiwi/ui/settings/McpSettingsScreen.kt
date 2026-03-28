package com.orizon.openkiwi.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orizon.openkiwi.core.mcp.ConnectionStatus
import com.orizon.openkiwi.core.mcp.McpManager
import com.orizon.openkiwi.core.mcp.McpServerConfig
import com.orizon.openkiwi.core.mcp.TransportType
import com.orizon.openkiwi.data.repository.McpServerRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSettingsScreen(
    mcpManager: McpManager,
    mcpServerRepository: McpServerRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val configs by mcpServerRepository.getAllConfigs().collectAsState(initial = emptyList())
    val serverStates by mcpManager.serverStates.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MCP 服务器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }
            )
        }
    ) { padding ->
        if (configs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text("暂无 MCP 服务器", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("点击右上角 + 添加", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(configs, key = { it.id }) { config ->
                    val state = serverStates[config.id]
                    McpServerCard(
                        config = config,
                        status = state?.status ?: ConnectionStatus.DISCONNECTED,
                        toolCount = state?.toolCount ?: 0,
                        error = state?.error,
                        onToggle = { enabled ->
                            scope.launch {
                                mcpServerRepository.toggleEnabled(config.id, enabled)
                                if (enabled) mcpManager.connectServer(config)
                                else mcpManager.disconnectServer(config.id)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                mcpManager.disconnectServer(config.id)
                                mcpServerRepository.deleteConfig(config.id)
                            }
                        },
                        onReconnect = {
                            mcpManager.disconnectServer(config.id)
                            mcpManager.connectServer(config)
                        }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddMcpServerDialog(
                onDismiss = { showAddDialog = false },
                onSave = { config ->
                    scope.launch {
                        val id = mcpServerRepository.saveConfig(config)
                        if (config.isEnabled) {
                            mcpManager.connectServer(config.copy(id = id))
                        }
                        showAddDialog = false
                    }
                }
            )
        }
    }
}

@Composable
private fun McpServerCard(
    config: McpServerConfig,
    status: ConnectionStatus,
    toolCount: Int,
    error: String?,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onReconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (status) {
                        ConnectionStatus.CONNECTED -> Icons.Outlined.Cloud
                        ConnectionStatus.ERROR -> Icons.Outlined.Error
                        else -> Icons.Outlined.CloudOff
                    },
                    contentDescription = null,
                    tint = when (status) {
                        ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(config.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (status) {
                            ConnectionStatus.CONNECTED -> "已连接 · $toolCount 个工具"
                            ConnectionStatus.CONNECTING -> "连接中..."
                            ConnectionStatus.ERROR -> "连接失败"
                            ConnectionStatus.DISCONNECTED -> "未连接"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = config.isEnabled, onCheckedChange = onToggle)
            }

            if (error != null && status == ConnectionStatus.ERROR) {
                Spacer(Modifier.height(8.dp))
                Text(error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onReconnect) { Text("重试") }
            }

            Spacer(Modifier.height(8.dp))
            Row {
                Text("${config.transportType.name} · ${config.url.ifBlank { config.command }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onSave: (McpServerConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var transportType by remember { mutableStateOf(TransportType.SSE) }
    var url by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 MCP 服务器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("传输方式:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = transportType == TransportType.SSE,
                        onClick = { transportType = TransportType.SSE },
                        label = { Text("SSE") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = transportType == TransportType.STDIO,
                        onClick = { transportType = TransportType.STDIO },
                        label = { Text("Stdio") }
                    )
                }
                if (transportType == TransportType.SSE) {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("服务器 URL") },
                        placeholder = { Text("http://localhost:3000/sse") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = command, onValueChange = { command = it },
                        label = { Text("启动命令") },
                        placeholder = { Text("npx @modelcontextprotocol/server") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(McpServerConfig(
                            name = name,
                            transportType = transportType,
                            url = url,
                            command = command
                        ))
                    }
                }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
