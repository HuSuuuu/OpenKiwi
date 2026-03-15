package com.orizon.openkiwi.ui.skills

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.core.skill.SkillManager
import com.orizon.openkiwi.data.local.entity.SkillEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SkillsUiState(
    val skills: List<SkillEntity> = emptyList(),
    val isLoading: Boolean = true
)

class SkillsViewModel(private val skillManager: SkillManager) : ViewModel() {
    private val _uiState = MutableStateFlow(SkillsUiState())
    val uiState: StateFlow<SkillsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            skillManager.getAllSkills().collect { skills ->
                _uiState.value = SkillsUiState(skills = skills, isLoading = false)
            }
        }
    }

    fun toggleSkill(id: String, enabled: Boolean) {
        viewModelScope.launch { skillManager.toggleSkill(id, enabled) }
    }

    fun deleteSkill(id: String) {
        viewModelScope.launch { skillManager.deleteSkill(id) }
    }

    class Factory(private val skillManager: SkillManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SkillsViewModel(skillManager) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(viewModel: SkillsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("技能") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            uiState.skills.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂无技能", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Spacer(Modifier.height(4.dp))
                        Text("Agent 执行任务时会自动学习", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.skills) { skill ->
                        SkillCard(skill, onToggle = { viewModel.toggleSkill(skill.id, !skill.isEnabled) }, onDelete = { viewModel.deleteSkill(skill.id) })
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
