package com.orizon.openkiwi.ui.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.orizon.openkiwi.network.VolcanoVoiceClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(viewModel: VoiceViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(error, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            VoiceConfigSection(
                config = uiState.config,
                onUpdate = { viewModel.updateConfig(it) }
            )

            TTSTestSection(
                testText = uiState.testText,
                ttsState = uiState.ttsState,
                onTextChange = { viewModel.setTestText(it) },
                onTest = { viewModel.testTTS() },
                onStop = { viewModel.stopPlayback() }
            )

            ASRTestSection(
                asrState = uiState.asrState,
                asrText = uiState.asrText,
                onToggle = { viewModel.toggleASR() }
            )
        }
    }
}

@Composable
private fun VoiceConfigSection(config: VoiceConfig, onUpdate: (VoiceConfig) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, null, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("火山引擎配置", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedTextField(
                value = config.appId,
                onValueChange = { onUpdate(config.copy(appId = it)) },
                label = { Text("App ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = config.accessToken,
                onValueChange = { onUpdate(config.copy(accessToken = it)) },
                label = { Text("Access Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = config.cluster,
                onValueChange = { onUpdate(config.copy(cluster = it)) },
                label = { Text("Cluster") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = config.voiceType,
                onValueChange = { onUpdate(config.copy(voiceType = it)) },
                label = { Text("发音人") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("如 BV001_streaming, BV002_streaming") }
            )

            Text("语速: ${"%.1f".format(config.speedRatio)}x", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = config.speedRatio,
                onValueChange = { onUpdate(config.copy(speedRatio = it)) },
                valueRange = 0.5f..2.0f,
                steps = 5
            )

            Text("音调: ${"%.1f".format(config.pitchRatio)}x", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = config.pitchRatio,
                onValueChange = { onUpdate(config.copy(pitchRatio = it)) },
                valueRange = 0.5f..2.0f,
                steps = 5
            )

            Text("音量: ${"%.1f".format(config.volumeRatio)}x", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = config.volumeRatio,
                onValueChange = { onUpdate(config.copy(volumeRatio = it)) },
                valueRange = 0.5f..2.0f,
                steps = 5
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI 回复自动播报", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = config.autoTts,
                    onCheckedChange = { onUpdate(config.copy(autoTts = it)) }
                )
            }
        }
    }
}

@Composable
private fun TTSTestSection(
    testText: String,
    ttsState: VolcanoVoiceClient.TTSState,
    onTextChange: (String) -> Unit,
    onTest: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.RecordVoiceOver, null, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("语音合成测试 (TTS)", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedTextField(
                value = testText,
                onValueChange = onTextChange,
                label = { Text("测试文本") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTest,
                    enabled = ttsState == VolcanoVoiceClient.TTSState.IDLE,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(when (ttsState) {
                        VolcanoVoiceClient.TTSState.IDLE -> "播放"
                        VolcanoVoiceClient.TTSState.LOADING -> "合成中..."
                        VolcanoVoiceClient.TTSState.PLAYING -> "播放中..."
                    })
                }
                if (ttsState == VolcanoVoiceClient.TTSState.PLAYING) {
                    OutlinedButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (ttsState == VolcanoVoiceClient.TTSState.LOADING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ASRTestSection(
    asrState: VolcanoVoiceClient.ASRState,
    asrText: String,
    onToggle: () -> Unit
) {
    val isListening = asrState == VolcanoVoiceClient.ASRState.LISTENING

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Mic, null, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("语音识别测试 (ASR)", style = MaterialTheme.typography.titleMedium)
            }

            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "scale"
            )

            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(64.dp).then(
                    if (isListening) Modifier.scale(pulseScale) else Modifier
                ),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    null, modifier = Modifier.size(28.dp)
                )
            }

            Text(
                when (asrState) {
                    VolcanoVoiceClient.ASRState.IDLE -> "点击开始录音"
                    VolcanoVoiceClient.ASRState.LISTENING -> "正在聆听..."
                    VolcanoVoiceClient.ASRState.PROCESSING -> "处理中..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (asrText.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        asrText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
