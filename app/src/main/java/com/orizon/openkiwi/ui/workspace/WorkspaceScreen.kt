package com.orizon.openkiwi.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orizon.openkiwi.core.agent.AgentWorkspace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    workspace: AgentWorkspace,
    onBack: () -> Unit
) {
    var files by remember { mutableStateOf(workspace.list()) }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var editContent by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

    fun openFile(name: String) {
        selectedFile = name
        editContent = workspace.read(name) ?: ""
        isEditing = false
    }

    fun saveFile() {
        selectedFile?.let { workspace.write(it, editContent) }
        isEditing = false
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            if (selectedFile != null) selectedFile!! else "工作区",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedFile != null) {
                                selectedFile = null
                                isEditing = false
                                files = workspace.list()
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        if (selectedFile != null) {
                            if (isEditing) {
                                IconButton(onClick = { saveFile() }) {
                                    Icon(Icons.Default.Check, "保存",
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                IconButton(onClick = { isEditing = true }) {
                                    Icon(Icons.Default.Edit, "编辑")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                HorizontalDivider(thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    ) { innerPadding ->
        if (selectedFile == null) {
            FileListView(
                files = files,
                modifier = Modifier.padding(innerPadding),
                onFileClick = { openFile(it) }
            )
        } else {
            FileEditorView(
                content = editContent,
                isEditing = isEditing,
                onContentChange = { editContent = it },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

private val FILE_DESCRIPTIONS = mapOf(
    AgentWorkspace.AGENTS to "AI 行为规则 · AI 每次对话自动读取",
    AgentWorkspace.USER to "用户画像 · AI 会记录你的偏好",
    AgentWorkspace.TOOLS to "工具笔记 · AI 总结的工具使用经验",
    AgentWorkspace.MEMORY to "长期记忆 · 跨会话重要信息",
    AgentWorkspace.SKILLS to "学习技能 · AI 掌握的操作流程",
    AgentWorkspace.HEARTBEAT to "周期任务 · 每次会话检查"
)

@Composable
private fun FileListView(
    files: List<String>,
    modifier: Modifier = Modifier,
    onFileClick: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Text(
                "AI 的自进化工作区",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
            Text(
                "AI 通过工具读写这些文件来进化自己的行为。你也可以直接编辑。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
            )
        }

        items(files) { fileName ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFileClick(fileName) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(fileName, style = MaterialTheme.typography.bodyMedium)
                        FILE_DESCRIPTIONS[fileName]?.let { desc ->
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileEditorView(
    content: String,
    isEditing: Boolean,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isEditing) {
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            modifier = modifier
                .fillMaxSize()
                .padding(12.dp),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp
            ),
            shape = RoundedCornerShape(8.dp)
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text(
                    content.ifBlank { "(空文件)" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    ),
                    color = if (content.isBlank())
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
