package com.orizon.openkiwi.ui.memory

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
import com.orizon.openkiwi.core.memory.MemoryEntry
import com.orizon.openkiwi.core.memory.MemoryManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MemoryUiState(
    val memories: List<MemoryEntry> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false
)

class MemoryViewModel(private val memoryManager: MemoryManager, private val memoryDao: com.orizon.openkiwi.data.local.dao.MemoryDao) : ViewModel() {
    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init { loadAll() }

    private fun loadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            val all = memoryDao.getAllMemories(200).map { entity ->
                com.orizon.openkiwi.core.memory.MemoryEntry(
                    id = entity.id,
                    type = runCatching { com.orizon.openkiwi.core.memory.MemoryType.valueOf(entity.type) }.getOrDefault(com.orizon.openkiwi.core.memory.MemoryType.LONG_TERM),
                    key = entity.key,
                    content = entity.content,
                    category = entity.category,
                    importance = entity.importance,
                    accessCount = entity.accessCount,
                    scope = entity.scope
                )
            }
            _uiState.value = _uiState.value.copy(memories = all, isSearching = false)
        }
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, isSearching = true)
        viewModelScope.launch {
            val results = if (query.isBlank()) {
                memoryDao.getAllMemories(200).map { entity ->
                    com.orizon.openkiwi.core.memory.MemoryEntry(
                        id = entity.id,
                        type = runCatching { com.orizon.openkiwi.core.memory.MemoryType.valueOf(entity.type) }.getOrDefault(com.orizon.openkiwi.core.memory.MemoryType.LONG_TERM),
                        key = entity.key,
                        content = entity.content,
                        category = entity.category,
                        importance = entity.importance,
                        accessCount = entity.accessCount,
                        scope = entity.scope
                    )
                }
            } else memoryManager.searchMemories(query, 50)
            _uiState.value = _uiState.value.copy(memories = results, isSearching = false)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryManager.deleteMemory(id)
            search(_uiState.value.searchQuery)
        }
    }

    class Factory(private val memoryManager: MemoryManager, private val memoryDao: com.orizon.openkiwi.data.local.dao.MemoryDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MemoryViewModel(memoryManager, memoryDao) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(viewModel: MemoryViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(searchText) {
        kotlinx.coroutines.delay(400)
        if (searchText.isNotBlank()) viewModel.search(searchText)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索记忆...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = ""; viewModel.search("") }) {
                            Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            when {
                uiState.isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                uiState.memories.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchText.isBlank()) "暂无记忆" else "未找到相关记忆",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.memories) { memory ->
                            MemoryCard(memory = memory, onDelete = { viewModel.deleteMemory(memory.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryCard(memory: MemoryEntry, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(memory.key, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        memory.type.name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                memory.content.take(200),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "重要度 ${"%.1f".format(memory.importance)} / 访问 ${memory.accessCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}
