package com.orizon.openkiwi.ui.tool

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(viewModel: ToolViewModel, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val allTools by viewModel.allTools.collectAsState()
    val customTools by viewModel.customTools.collectAsState()
    var expandedTool by remember { mutableStateOf<String?>(null) }

    val builtInTools = allTools.filter { it.isBuiltIn }
    val customToolInfos = allTools.filter { !it.isBuiltIn }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工具") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("内置工具")
                            if (builtInTools.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("${builtInTools.size}")
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("自定义工具")
                            if (customToolInfos.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Badge { Text("${customToolInfos.size}") }
                            }
                        }
                    }
                )
            }

            val displayTools = if (selectedTab == 0) builtInTools else customToolInfos

            if (displayTools.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (selectedTab == 0) Icons.Outlined.Build else Icons.Outlined.AddCircleOutline,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (selectedTab == 0) "暂无内置工具" else "暂无自定义工具",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        if (selectedTab == 1) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "在聊天中让 AI 为你编写工具",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayTools, key = { it.name }) { tool ->
                        ToolCard(
                            tool = tool,
                            isExpanded = expandedTool == tool.name,
                            onToggleExpand = {
                                expandedTool = if (expandedTool == tool.name) null else tool.name
                            },
                            onToggleEnabled = { enabled ->
                                viewModel.toggleCustomTool(tool.name, enabled)
                            },
                            onDelete = {
                                viewModel.deleteCustomTool(tool.name)
                                expandedTool = null
                            }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: ToolInfo,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor by animateColorAsState(
        when (tool.category) {
            "SYSTEM" -> MaterialTheme.colorScheme.primary
            "NETWORK" -> MaterialTheme.colorScheme.tertiary
            "FILE" -> MaterialTheme.colorScheme.secondary
            "CODE_EXECUTION" -> MaterialTheme.colorScheme.error
            "DEVICE" -> MaterialTheme.colorScheme.primary
            "COMMUNICATION" -> MaterialTheme.colorScheme.tertiary
            "SEARCH" -> MaterialTheme.colorScheme.secondary
            "GUI" -> MaterialTheme.colorScheme.primary
            "CUSTOM" -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        },
        label = "category"
    )

    Card(
        onClick = onToggleExpand,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(categoryColor, RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                CategoryLabel(tool.category)

                if (tool.isBuiltIn) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "内置",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "自定义",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (isExpanded) {
                Spacer(Modifier.height(10.dp))

                if (!tool.isBuiltIn) {
                    if (tool.script.isNotBlank()) {
                        Text(
                            "脚本实现",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                tool.script,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "启用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Switch(
                            checked = tool.isEnabled,
                            onCheckedChange = onToggleEnabled,
                            modifier = Modifier.height(24.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        if (tool.createdAt > 0) {
                            Text(
                                formatTime(tool.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Outlined.Delete, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLabel(category: String) {
    val label = when (category) {
        "SYSTEM" -> "系统"
        "GUI" -> "界面"
        "NETWORK" -> "网络"
        "FILE" -> "文件"
        "COMMUNICATION" -> "通信"
        "DEVICE" -> "设备"
        "CODE_EXECUTION" -> "代码"
        "SEARCH" -> "搜索"
        "CUSTOM" -> "自定义"
        else -> category
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        else -> SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
