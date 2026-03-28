package com.orizon.openkiwi.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.core.code.TerminalSession
import com.orizon.openkiwi.core.code.TerminalSessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TerminalLine(val text: String, val isCommand: Boolean = false, val isError: Boolean = false)

data class TerminalUiState(
    val lines: List<TerminalLine> = listOf(TerminalLine("OpenKiwi Terminal v2.0")),
    val isRunning: Boolean = false,
    val sessions: List<TerminalSession> = emptyList(),
    val currentSessionId: String? = null,
    val cwd: String = "/"
)

class TerminalViewModel(
    private val sessionManager: TerminalSessionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.activeSessions.collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
        viewModelScope.launch {
            sessionManager.currentSessionId.collect { id ->
                _uiState.update { state ->
                    val session = id?.let { sessionManager.getSession(it) }
                    state.copy(currentSessionId = id, cwd = session?.workingDirectory ?: "/")
                }
            }
        }
        if (sessionManager.listSessions().isEmpty()) {
            sessionManager.createSession("Terminal 1")
        }
    }

    fun createNewSession() {
        val count = sessionManager.listSessions().size + 1
        sessionManager.createSession("Terminal $count")
    }

    fun switchSession(sessionId: String) {
        sessionManager.switchSession(sessionId)
        val session = sessionManager.getSession(sessionId)
        _uiState.update { it.copy(
            currentSessionId = sessionId,
            cwd = session?.workingDirectory ?: "/",
            lines = rebuildLines(session)
        )}
    }

    fun closeSession(sessionId: String) {
        sessionManager.removeSession(sessionId)
    }

    fun executeCommand(command: String) {
        if (command.isBlank()) return
        val sid = _uiState.value.currentSessionId ?: return

        addLine(TerminalLine("$ $command", isCommand = true))
        _uiState.update { it.copy(isRunning = true) }

        viewModelScope.launch {
            val result = sessionManager.executeInSession(sid, command)
            if (result.stdout.isNotBlank()) {
                result.stdout.lines().forEach { addLine(TerminalLine(it)) }
            }
            if (result.stderr.isNotBlank()) {
                result.stderr.lines().forEach { addLine(TerminalLine(it, isError = true)) }
            }
            if (result.exitCode != 0) {
                addLine(TerminalLine("[exit: ${result.exitCode}]", isError = true))
            }
            val session = sessionManager.getSession(sid)
            _uiState.update { it.copy(isRunning = false, cwd = session?.workingDirectory ?: it.cwd) }
        }
    }

    fun clear() {
        _uiState.update { it.copy(lines = listOf(TerminalLine("OpenKiwi Terminal v2.0"))) }
    }

    private fun addLine(line: TerminalLine) {
        val current = _uiState.value.lines.toMutableList()
        current.add(line)
        if (current.size > 2000) current.removeAt(0)
        _uiState.update { it.copy(lines = current) }
    }

    private fun rebuildLines(session: TerminalSession?): List<TerminalLine> {
        if (session == null) return listOf(TerminalLine("OpenKiwi Terminal v2.0"))
        val history = session.output
        if (history.isBlank()) return listOf(TerminalLine("OpenKiwi Terminal v2.0"))
        return history.lines().map { line ->
            when {
                line.startsWith("$ ") -> TerminalLine(line, isCommand = true)
                line.startsWith("Error:") -> TerminalLine(line, isError = true)
                else -> TerminalLine(line)
            }
        }
    }

    class Factory(private val sessionManager: TerminalSessionManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TerminalViewModel(sessionManager) as T
    }
}

private val termBg = Color(0xFF161616)
private val termText = Color(0xFFE0E0E0)
private val termCmd = Color(0xFF79B8FF)
private val termErr = Color(0xFFE57373)
private val termPrompt = Color(0xFF81C784)
private val termInputBg = Color(0xFF1E1E1E)
private val termTabBg = Color(0xFF1E1E1E)
private val termTabActive = Color(0xFF2A2A2A)

private val ansiColors = mapOf(
    30 to Color(0xFF1E1E1E), 31 to Color(0xFFF85149), 32 to Color(0xFF7EE787),
    33 to Color(0xFFFBBF24), 34 to Color(0xFF79C0FF), 35 to Color(0xFFBC8CFF),
    36 to Color(0xFF39D2C0), 37 to Color(0xFFE6EDF3)
)

private val ansiRegex = Regex("\u001B\\[(\\d+(?:;\\d+)*)m")

@Composable
private fun parseAnsi(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentColor = termText
        var isBold = false
        var lastIndex = 0
        ansiRegex.findAll(text).forEach { match ->
            if (match.range.first > lastIndex) {
                withStyle(SpanStyle(color = currentColor, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)) {
                    append(text.substring(lastIndex, match.range.first))
                }
            }
            val codes = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
            for (code in codes) {
                when {
                    code == 0 -> { currentColor = termText; isBold = false }
                    code == 1 -> isBold = true
                    code in 30..37 -> currentColor = ansiColors[code] ?: termText
                    code == 39 -> currentColor = termText
                }
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            withStyle(SpanStyle(color = currentColor, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)) {
                append(text.substring(lastIndex))
            }
        }
    }
}

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
                title = { Text("\u7EC8\u7AEF", fontFamily = FontFamily.Monospace, fontSize = 15.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { TextButton(onClick = { viewModel.clear() }) { Text("\u6E05\u7A7A", color = termText.copy(alpha = 0.6f)) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = termBg,
                    titleContentColor = termText,
                    navigationIconContentColor = termText
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(termBg)) {
            Row(
                modifier = Modifier.fillMaxWidth().background(termTabBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (session in uiState.sessions) {
                    val isActive = session.id == uiState.currentSessionId
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .background(
                                if (isActive) termTabActive else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { viewModel.switchSession(session.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            session.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (isActive) termText else termText.copy(alpha = 0.5f)
                        )
                        if (uiState.sessions.size > 1) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp).clickable { viewModel.closeSession(session.id) },
                                tint = termText.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
                IconButton(onClick = { viewModel.createNewSession() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, null, tint = termText.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
            }

            Text(
                uiState.cwd,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = termText.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(uiState.lines) { line ->
                    val annotated = parseAnsi(line.text)
                    val baseColor = when {
                        line.isCommand -> termCmd
                        line.isError -> termErr
                        else -> termText
                    }
                    if (annotated.spanStyles.isEmpty()) {
                        Text(
                            text = line.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = baseColor
                        )
                    } else {
                        Text(
                            text = annotated,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
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
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = termText),
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
