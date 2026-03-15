package com.orizon.openkiwi.ui.task

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.data.local.dao.AuditLogDao
import com.orizon.openkiwi.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TaskUiState(
    val logs: List<AuditLogEntity> = emptyList(),
    val filter: String = "all"
)

class TaskViewModel(private val auditLogDao: AuditLogDao) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    init { loadLogs() }

    fun loadLogs(filter: String = "all") {
        viewModelScope.launch {
            val flow = if (filter == "all") auditLogDao.getRecentLogs(200) else auditLogDao.getLogsByType(filter, 200)
            flow.collect { logs -> _uiState.value = TaskUiState(logs = logs, filter = filter) }
        }
    }

    fun clearOldLogs(daysAgo: Int = 7) {
        viewModelScope.launch { auditLogDao.deleteLogsBefore(System.currentTimeMillis() - daysAgo * 86400000L) }
    }

    class Factory(private val auditLogDao: AuditLogDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TaskViewModel(auditLogDao) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务日志") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { viewModel.clearOldLogs() }) {
                        Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "全部", "TOOL_CALL" to "工具", "AGENT" to "Agent", "ERROR" to "错误").forEach { (key, label) ->
                    FilterChip(
                        selected = uiState.filter == key,
                        onClick = { viewModel.loadLogs(key) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            if (uiState.logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(uiState.logs) { log ->
                        LogCard(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: AuditLogEntity) {
    val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
    val isSuccess = log.result.startsWith("SUCCESS")

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(log.actionType, style = MaterialTheme.typography.labelMedium)
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            }
            Spacer(Modifier.height(2.dp))
            Text(log.actionDetail.take(200), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(
                log.result.take(100),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}
