package com.orizon.openkiwi.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.ExtensionOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orizon.openkiwi.core.plugin.DynamicPluginLoader
import com.orizon.openkiwi.core.plugin.PluginInfo
import com.orizon.openkiwi.core.plugin.PluginManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginScreen(
    pluginManager: PluginManager,
    dynamicPluginLoader: DynamicPluginLoader,
    onBack: () -> Unit
) {
    val pluginInfos by pluginManager.pluginInfos.collectAsState()
    var showPermissionDialog by remember { mutableStateOf<PluginInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("插件管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Folder, contentDescription = "插件目录")
                    }
                }
            )
        }
    ) { padding ->
        if (pluginInfos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ExtensionOff, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text("暂无已安装插件", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("将 .apk/.dex 插件放入插件目录即可加载",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    Spacer(Modifier.height(16.dp))
                    OutlinedCard(modifier = Modifier.padding(horizontal = 32.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("插件目录", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(dynamicPluginLoader.getPluginDirectory().absolutePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("已安装 ${pluginInfos.size} 个插件",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(pluginInfos, key = { it.id }) { info ->
                    PluginCard(
                        info = info,
                        onToggle = { enabled ->
                            if (enabled) pluginManager.enablePlugin(info.id)
                            else pluginManager.disablePlugin(info.id)
                        },
                        onDelete = {
                            pluginManager.unloadPlugin(info.id)
                        },
                        onShowPermissions = { showPermissionDialog = info }
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("插件目录", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(dynamicPluginLoader.getPluginDirectory().absolutePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        showPermissionDialog?.let { info ->
            AlertDialog(
                onDismissRequest = { showPermissionDialog = null },
                icon = { Icon(Icons.Outlined.Security, contentDescription = null) },
                title = { Text("${info.name} 权限") },
                text = {
                    if (info.requiredPermissions.isEmpty()) {
                        Text("此插件未声明特殊权限。")
                    } else {
                        Column {
                            Text("此插件需要以下权限:", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            info.requiredPermissions.forEach { perm ->
                                Text("  - $perm", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPermissionDialog = null }) { Text("确定") }
                }
            )
        }
    }
}

@Composable
private fun PluginCard(
    info: PluginInfo,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onShowPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Extension, contentDescription = null,
                    tint = if (info.isEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(info.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium)
                    Text("v${info.version}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = info.isEnabled, onCheckedChange = onToggle)
            }

            if (info.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(info.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            Row {
                if (info.requiredPermissions.isNotEmpty()) {
                    TextButton(onClick = onShowPermissions, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${info.requiredPermissions.size} 项权限", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "卸载",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
