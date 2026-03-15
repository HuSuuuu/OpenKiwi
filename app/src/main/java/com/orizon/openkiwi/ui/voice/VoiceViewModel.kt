package com.orizon.openkiwi.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.data.preferences.UserPreferences
import com.orizon.openkiwi.network.VolcanoVoiceClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class VoiceConfig(
    val appId: String = "",
    val accessToken: String = "",
    val cluster: String = "volcano_tts",
    val voiceType: String = "BV001_streaming",
    val speedRatio: Float = 1.0f,
    val pitchRatio: Float = 1.0f,
    val volumeRatio: Float = 1.0f,
    val autoTts: Boolean = false
)

data class VoiceUiState(
    val config: VoiceConfig = VoiceConfig(),
    val ttsState: VolcanoVoiceClient.TTSState = VolcanoVoiceClient.TTSState.IDLE,
    val asrState: VolcanoVoiceClient.ASRState = VolcanoVoiceClient.ASRState.IDLE,
    val testText: String = "你好，我是OpenKiwi语音助手。",
    val asrText: String = "",
    val error: String? = null
)

class VoiceViewModel(
    private val voiceClient: VolcanoVoiceClient,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = VoiceConfig(
                appId = prefs.getString("volcano_app_id"),
                accessToken = prefs.getString("volcano_access_token"),
                cluster = prefs.getString("volcano_cluster").ifBlank { "volcano_tts" },
                voiceType = prefs.getString("volcano_voice_type").ifBlank { "BV001_streaming" },
                speedRatio = prefs.getFloat("volcano_speed_ratio", 1.0f),
                pitchRatio = prefs.getFloat("volcano_pitch_ratio", 1.0f),
                volumeRatio = prefs.getFloat("volcano_volume_ratio", 1.0f),
                autoTts = prefs.getBoolean("volcano_auto_tts")
            )
            _uiState.value = _uiState.value.copy(config = config)
        }

        viewModelScope.launch {
            voiceClient.ttsState.collect { state ->
                _uiState.value = _uiState.value.copy(ttsState = state)
            }
        }

        viewModelScope.launch {
            voiceClient.asrState.collect { state ->
                _uiState.value = _uiState.value.copy(asrState = state)
            }
        }

        viewModelScope.launch {
            voiceClient.asrResult.collect { text ->
                _uiState.value = _uiState.value.copy(
                    asrText = _uiState.value.asrText + text
                )
            }
        }
    }

    fun updateConfig(config: VoiceConfig) {
        _uiState.value = _uiState.value.copy(config = config)
        viewModelScope.launch {
            prefs.setString("volcano_app_id", config.appId)
            prefs.setString("volcano_access_token", config.accessToken)
            prefs.setString("volcano_cluster", config.cluster)
            prefs.setString("volcano_voice_type", config.voiceType)
            prefs.setFloat("volcano_speed_ratio", config.speedRatio)
            prefs.setFloat("volcano_pitch_ratio", config.pitchRatio)
            prefs.setFloat("volcano_volume_ratio", config.volumeRatio)
            prefs.setBoolean("volcano_auto_tts", config.autoTts)
        }
    }

    fun testTTS() {
        val config = _uiState.value.config
        if (config.appId.isBlank() || config.accessToken.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请先配置 App ID 和 Access Token")
            return
        }
        viewModelScope.launch {
            val result = voiceClient.synthesize(
                text = _uiState.value.testText,
                appId = config.appId,
                accessToken = config.accessToken,
                cluster = config.cluster,
                voiceType = config.voiceType,
                speedRatio = config.speedRatio,
                volumeRatio = config.volumeRatio,
                pitchRatio = config.pitchRatio
            )
            result.onSuccess { voiceClient.playAudio(it) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun toggleASR() {
        val config = _uiState.value.config
        if (config.appId.isBlank() || config.accessToken.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请先配置 App ID 和 Access Token")
            return
        }
        if (_uiState.value.asrState == VolcanoVoiceClient.ASRState.LISTENING) {
            voiceClient.stopASR()
        } else {
            _uiState.value = _uiState.value.copy(asrText = "")
            voiceClient.startASR(config.appId, config.accessToken, config.cluster)
        }
    }

    fun stopPlayback() { voiceClient.stopPlayback() }

    fun setTestText(text: String) {
        _uiState.value = _uiState.value.copy(testText = text)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    class Factory(
        private val voiceClient: VolcanoVoiceClient,
        private val prefs: UserPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VoiceViewModel(voiceClient, prefs) as T
    }
}
