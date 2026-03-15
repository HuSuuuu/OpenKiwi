package com.orizon.openkiwi.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TerminalLine(val text: String, val isCommand: Boolean = false, val isError: Boolean = false)

data class TerminalUiState(val lines: List<TerminalLine> = listOf(TerminalLine("OpenKiwi Terminal v1.0")), val isRunning: Boolean = false)

class TerminalViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    fun executeCommand(command: String) {
        if (command.isBlank()) return
        addLine(TerminalLine("$ $command", isCommand = true))
        _uiState.value = _uiState.value.copy(isRunning = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    val stdout = process.inputStream.bufferedReader().readText()
                    val stderr = process.errorStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    if (stdout.isNotBlank()) stdout.lines().forEach { addLine(TerminalLine(it)) }
                    if (stderr.isNotBlank()) stderr.lines().forEach { addLine(TerminalLine(it, isError = true)) }
                    if (exitCode != 0) addLine(TerminalLine("[exit: $exitCode]", isError = true))
                }.onFailure { addLine(TerminalLine("Error: ${it.message}", isError = true)) }
            }
            _uiState.value = _uiState.value.copy(isRunning = false)
        }
    }

    fun clear() { _uiState.value = TerminalUiState() }

    private fun addLine(line: TerminalLine) {
        val current = _uiState.value.lines.toMutableList()
        current.add(line)
        if (current.size > 1000) current.removeAt(0)
        _uiState.value = _uiState.value.copy(lines = current)
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TerminalViewModel() as T
    }
}

private val termBg = Color(0xFF0D1117)
private val termText = Color(0xFFE6EDF3)
private val termCmd = Color(0xFF79C0FF)
private val termErr = Color(0xFFF85149)
private val termPrompt = Color(0xFF7EE787)
private val termInputBg = Color(0xFF161B22)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: TerminalViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.lines.size) {
        if (uiState.lines.isNotEmpty()) listState.animateScrollToItem(uiState.lines.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("终端", fontFamily = FontFamily.Monospace, fontSize = 15.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { TextButton(onClick = { viewModel.clear() }) { Text("清空", color = termText.copy(alpha = 0.6f)) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = termBg,
                    titleContentColor = termText,
                    navigationIconContentColor = termText
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(termBg)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(uiState.lines) { line ->
                    Text(
                        text = line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = when {
                            line.isCommand -> termCmd
                            line.isError -> termErr
                            else -> termText
                        }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(termInputBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$", fontFamily = FontFamily.Monospace, color = termPrompt, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = termText),
                    cursorBrush = SolidColor(termPrompt),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { if (input.isNotBlank()) { viewModel.executeCommand(input); input = "" } },
                    enabled = !uiState.isRunning && input.isNotBlank(),
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = termPrompt.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.ArrowUpward, null, tint = termPrompt, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
