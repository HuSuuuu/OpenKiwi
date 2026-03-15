package com.orizon.openkiwi.ui.logs

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

data class AuditLogUiState(
    val logs: List<AuditLogEntity> = emptyList(),
    val searchQuery: String = "",
    val typeFilter: String = "all"
)

class AuditLogViewModel(private val auditLogDao: AuditLogDao) : ViewModel() {
    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState.asStateFlow()

    init { loadLogs() }

    fun loadLogs(typeFilter: String = "all") {
        _uiState.value = _uiState.value.copy(typeFilter = typeFilter)
        viewModelScope.launch {
            val flow = if (typeFilter == "all") auditLogDao.getRecentLogs(500) else auditLogDao.getLogsByType(typeFilter, 500)
            flow.collect { logs ->
                val query = _uiState.value.searchQuery
                val filtered = if (query.isBlank()) logs else logs.filter { it.actionDetail.contains(query, true) || it.result.contains(query, true) }
                _uiState.value = _uiState.value.copy(logs = filtered)
            }
        }
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        loadLogs(_uiState.value.typeFilter)
    }

    fun deleteBefore(days: Int) {
        viewModelScope.launch { auditLogDao.deleteLogsBefore(System.currentTimeMillis() - days * 86400000L) }
    }

    class Factory(private val dao: AuditLogDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuditLogViewModel(dao) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(viewModel: AuditLogViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var searchText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("审计日志") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { viewModel.deleteBefore(30) }) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it; viewModel.search(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索日志...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
            Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "全部", "TOOL_CALL" to "工具", "AGENT" to "Agent", "ERROR" to "错误").forEach { (key, label) ->
                    FilterChip(
                        selected = uiState.typeFilter == key,
                        onClick = { viewModel.loadLogs(key) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            if (uiState.logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(uiState.logs) { log ->
                        val time = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
                        val isOk = log.result.startsWith("SUCCESS")
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOk) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(log.actionType, style = MaterialTheme.typography.labelMedium)
                                    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                                }
                                Text(log.actionDetail.take(200), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 2.dp))
                                Text(
                                    log.result.take(100),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
