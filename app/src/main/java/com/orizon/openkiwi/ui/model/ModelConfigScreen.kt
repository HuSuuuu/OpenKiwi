package com.orizon.openkiwi.ui.model

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.orizon.openkiwi.core.model.ModelConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    viewModel: ModelConfigViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isEditing) {
        ModelEditScreen(
            model = uiState.editingModel ?: ModelConfig(),
            isSaving = uiState.isSaving,
            onModelChange = { viewModel.updateEditingModel(it) },
            onSave = { viewModel.saveModel() },
            onBack = { viewModel.cancelEditing() }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("模型配置") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { viewModel.startEditing() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.Default.Add, null) }
            }
        ) { padding ->
            if (uiState.models.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "尚未配置模型",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "添加 OpenAI 兼容模型开始使用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.models, key = { it.id }) { model ->
                        ModelCard(
                            model = model,
                            onEdit = { viewModel.startEditing(model) },
                            onDelete = { viewModel.deleteModel(model) },
                            onSetDefault = { viewModel.setDefault(model.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, style = MaterialTheme.typography.titleMedium)
                        if (model.isDefault) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    "默认",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (model.isSmallModel) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    "小模型",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        model.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (!model.isDefault) {
                    TextButton(onClick = onSetDefault) { Text("设为默认", style = MaterialTheme.typography.labelMedium) }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除模型") },
            text = { Text("确定删除「${model.name}」？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEditScreen(
    model: ModelConfig,
    isSaving: Boolean,
    onModelChange: (ModelConfig) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (model.id.isBlank()) "添加模型" else "编辑模型") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Close, null) }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = model.name.isNotBlank() && model.apiBaseUrl.isNotBlank()
                                && model.apiKey.isNotBlank() && model.modelName.isNotBlank()
                                && !isSaving
                    ) {
                        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = model.name,
                onValueChange = { onModelChange(model.copy(name = it)) },
                label = { Text("显示名称") },
                placeholder = { Text("GPT-4o") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            OutlinedTextField(
                value = model.apiBaseUrl,
                onValueChange = { onModelChange(model.copy(apiBaseUrl = it)) },
                label = { Text("API 地址") },
                placeholder = { Text("https://api.openai.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                value = model.apiKey,
                onValueChange = { onModelChange(model.copy(apiKey = it)) },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
            OutlinedTextField(
                value = model.modelName,
                onValueChange = { onModelChange(model.copy(modelName = it)) },
                label = { Text("模型名") },
                placeholder = { Text("gpt-4o") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Text("高级设置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = model.maxTokens.toString(),
                    onValueChange = { onModelChange(model.copy(maxTokens = it.toIntOrNull() ?: 4096)) },
                    label = { Text("最大 Token") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = model.temperature.toString(),
                    onValueChange = { onModelChange(model.copy(temperature = it.toDoubleOrNull() ?: 0.7)) },
                    label = { Text("Temperature") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = model.timeoutSeconds.toString(),
                    onValueChange = { onModelChange(model.copy(timeoutSeconds = it.toIntOrNull() ?: 60)) },
                    label = { Text("超时(秒)") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = model.maxRetries.toString(),
                    onValueChange = { onModelChange(model.copy(maxRetries = it.toIntOrNull() ?: 3)) },
                    label = { Text("重试次数") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Text("能力", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = model.supportsVision, onClick = { onModelChange(model.copy(supportsVision = !model.supportsVision)) }, label = { Text("视觉") }, shape = RoundedCornerShape(8.dp))
                FilterChip(selected = model.supportsTools, onClick = { onModelChange(model.copy(supportsTools = !model.supportsTools)) }, label = { Text("工具") }, shape = RoundedCornerShape(8.dp))
                FilterChip(selected = model.supportsStreaming, onClick = { onModelChange(model.copy(supportsStreaming = !model.supportsStreaming)) }, label = { Text("流式") }, shape = RoundedCornerShape(8.dp))
                FilterChip(selected = model.isDefault, onClick = { onModelChange(model.copy(isDefault = !model.isDefault)) }, label = { Text("默认") }, shape = RoundedCornerShape(8.dp))
                FilterChip(selected = model.isSmallModel, onClick = { onModelChange(model.copy(isSmallModel = !model.isSmallModel)) }, label = { Text("小模型") }, shape = RoundedCornerShape(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = model.includeWebSearchTool,
                    onClick = {
                        val on = !model.includeWebSearchTool
                        onModelChange(
                            model.copy(
                                includeWebSearchTool = on,
                                webSearchExclusive = if (!on) false else model.webSearchExclusive
                            )
                        )
                    },
                    label = { Text("联网 web_search") },
                    shape = RoundedCornerShape(8.dp)
                )
                FilterChip(
                    selected = model.webSearchExclusive,
                    onClick = {
                        onModelChange(
                            model.copy(
                                webSearchExclusive = !model.webSearchExclusive,
                                includeWebSearchTool = true
                            )
                        )
                    },
                    enabled = model.includeWebSearchTool,
                    label = { Text("仅联网") },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            if (model.includeWebSearchTool) {
                Text(
                    buildString {
                        appendLine("• 当前聊天走 Chat Completions：方舟要求联网项为 type=function + name=web_search，与文档里 Responses 的 {\"type\":\"web_search\",\"max_keyword\":…} 不是同一种请求。")
                        appendLine("• 文档中的 max_keyword、sources、user_location、limit 等仅适用于 Responses API（/responses）；本应用尚未接入该接口，无法在聊天里传这些字段。")
                        appendLine("• 请在方舟控制台开通「联网内容插件」。与本地 web_search 工具冲突时请开「仅联网」。")
                    }.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            Text("思考模式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("disabled" to "关闭", "low" to "低", "medium" to "中", "high" to "高").forEach { (value, label) ->
                    FilterChip(
                        selected = model.reasoningEffort == value,
                        onClick = { onModelChange(model.copy(reasoningEffort = value)) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}
