package com.orizon.openkiwi.ui.skills

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.core.openclaw.OpenClawSkill
import com.orizon.openkiwi.core.openclaw.OpenClawSkillRegistry
import com.orizon.openkiwi.core.skill.SkillManager
import com.orizon.openkiwi.data.local.entity.SkillEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SkillsUiState(
    val workflowSkills: List<SkillEntity> = emptyList(),
    val openClawSkills: List<OpenClawSkill> = emptyList(),
    val isLoading: Boolean = true
)

class SkillsViewModel(
    private val skillManager: SkillManager,
    private val openClawSkillRegistry: OpenClawSkillRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillsUiState())
    val uiState: StateFlow<SkillsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                skillManager.getAllSkills(),
                openClawSkillRegistry.skillsFlow
            ) { workflow, openClaw ->
                SkillsUiState(
                    workflowSkills = workflow,
                    openClawSkills = openClaw,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun toggleWorkflowSkill(id: String, enabled: Boolean) {
        viewModelScope.launch { skillManager.toggleSkill(id, enabled) }
    }

    fun deleteWorkflowSkill(id: String) {
        viewModelScope.launch { skillManager.deleteSkill(id) }
    }

    fun toggleOpenClawSkill(id: String, enabled: Boolean) {
        openClawSkillRegistry.setEnabled(id, enabled)
    }

    fun deleteOpenClawSkill(id: String) {
        openClawSkillRegistry.remove(id)
    }

    fun importOpenClawFromUri(uri: android.net.Uri): OpenClawSkill? =
        openClawSkillRegistry.importFromUri(uri)

    class Factory(
        private val skillManager: SkillManager,
        private val openClawSkillRegistry: OpenClawSkillRegistry
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SkillsViewModel(skillManager, openClawSkillRegistry) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(viewModel: SkillsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pickSkillFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val skill = viewModel.importOpenClawFromUri(uri)
            if (skill != null) {
                snackbarHostState.showSnackbar("已导入：${skill.name}")
            } else {
                snackbarHostState.showSnackbar("导入失败：请确认是带 YAML 头（含 description）的 SKILL.md")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("技能") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("工作流") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("OpenClaw") }
                )
            }

            when (selectedTab) {
                0 -> WorkflowSkillsTab(
                    uiState = uiState,
                    onToggle = { id, en -> viewModel.toggleWorkflowSkill(id, en) },
                    onDelete = { viewModel.deleteWorkflowSkill(it) }
                )
                1 -> OpenClawSkillsTab(
                    skills = uiState.openClawSkills,
                    onToggle = { id, en -> viewModel.toggleOpenClawSkill(id, en) },
                    onDelete = { viewModel.deleteOpenClawSkill(it) },
                    onImportClick = {
                        pickSkillFile.launch(
                            arrayOf("text/plain", "text/markdown", "application/octet-stream", "*/*")
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkflowSkillsTab(
    uiState: SkillsUiState,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        uiState.workflowSkills.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("暂无工作流技能", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Agent 多步执行成功后会自动学习；也可在对话里用 skill 工具创建。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.workflowSkills) { skill ->
                    SkillCard(
                        skill,
                        onToggle = { onToggle(skill.id, !skill.isEnabled) },
                        onDelete = { onDelete(skill.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenClawSkillsTab(
    skills: List<OpenClawSkill>,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onImportClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    "内置 SKILL.md 已与 AI 系统提示联动；导入新技能后同样生效。也可在对话中让 AI 调用 openclaw_skills 工具。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = onImportClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("从文件导入 SKILL.md")
        }

        if (skills.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "未加载到 OpenClaw 技能（assets 可能未打包）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(skills, key = { it.id }) { skill ->
                    OpenClawSkillCard(
                        skill = skill,
                        onToggle = { onToggle(skill.id, !skill.isEnabled) },
                        onDelete = { onDelete(skill.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenClawSkillCard(
    skill: OpenClawSkill,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val bundled = skill.source.startsWith("bundled:")
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${skill.emoji ?: ""} ${skill.name}".trim(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (skill.description.isNotBlank()) {
                        Text(
                            skill.description.take(120),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                    Text(
                        "id: ${skill.id} · ${skill.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(checked = skill.isEnabled, onCheckedChange = { onToggle() })
                if (!bundled) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillCard(skill: SkillEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(skill.name, style = MaterialTheme.typography.titleMedium)
                    if (skill.description.isNotBlank()) {
                        Text(
                            skill.description.take(80),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                Switch(checked = skill.isEnabled, onCheckedChange = { onToggle() })
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) {
                    Text(skill.type, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (skill.category.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)) {
                        Text(skill.category, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}
