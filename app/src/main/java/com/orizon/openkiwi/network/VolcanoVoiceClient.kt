package com.orizon.openkiwi.network

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

@Serializable
data class VolcanoTTSRequest(
    val app: VolcanoApp,
    val user: VolcanoUser,
    val audio: VolcanoAudio,
    val request: VolcanoTTSPayload
)

@Serializable
data class VolcanoApp(
    val appid: String,
    val token: String = "access_token",
    val cluster: String
)

@Serializable
data class VolcanoUser(val uid: String = "openkiwi_user")

@Serializable
data class VolcanoAudio(
    val voice_type: String = "BV001_streaming",
    val encoding: String = "mp3",
    val speed_ratio: Float = 1.0f,
    val volume_ratio: Float = 1.0f,
    val pitch_ratio: Float = 1.0f
)

@Serializable
data class VolcanoTTSPayload(
    val reqid: String,
    val text: String,
    val operation: String = "query"
)

@Serializable
data class VolcanoTTSResponse(
    val code: Int = 0,
    val message: String = "",
    val data: String? = null
)

@Serializable
data class VolcanoASRResponse(
    val code: Int = 0,
    val message: String = "",
    val result: String = "",
    val is_final: Boolean = false
)

class VolcanoVoiceClient(private val httpClient: OkHttpClient) {

    companion object {
        private const val TAG = "VolcanoVoice"
        private const val TTS_URL = "https://openspeech.bytedance.com/api/v1/tts"
        private const val ASR_WS_URL = "wss://openspeech.bytedance.com/api/v2/asr"
        private const val SAMPLE_RATE = 16000
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _ttsState = MutableStateFlow(TTSState.IDLE)
    val ttsState: StateFlow<TTSState> = _ttsState

    private val _asrState = MutableStateFlow(ASRState.IDLE)
    val asrState: StateFlow<ASRState> = _asrState

    private val _asrResult = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 16)
    val asrResult: SharedFlow<String> = _asrResult

    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var asrWebSocket: WebSocket? = null
    private var recordJob: Job? = null

    enum class TTSState { IDLE, LOADING, PLAYING }
    enum class ASRState { IDLE, LISTENING, PROCESSING }

    suspend fun synthesize(
        text: String,
        appId: String,
        accessToken: String,
        cluster: String,
        voiceType: String = "BV001_streaming",
        speedRatio: Float = 1.0f,
        volumeRatio: Float = 1.0f,
        pitchRatio: Float = 1.0f
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        _ttsState.value = TTSState.LOADING
        runCatching {
            val reqBody = VolcanoTTSRequest(
                app = VolcanoApp(appid = appId, token = accessToken, cluster = cluster),
                user = VolcanoUser(),
                audio = VolcanoAudio(
                    voice_type = voiceType,
                    encoding = "mp3",
                    speed_ratio = speedRatio,
                    volume_ratio = volumeRatio,
                    pitch_ratio = pitchRatio
                ),
                request = VolcanoTTSPayload(
                    reqid = UUID.randomUUID().toString(),
                    text = text
                )
            )

            val bodyJson = json.encodeToString(VolcanoTTSRequest.serializer(), reqBody)
            val request = Request.Builder()
                .url(TTS_URL)
                .addHeader("Authorization", "Bearer;$accessToken")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: throw Exception("Empty TTS response")
            val ttsResp = json.decodeFromString(VolcanoTTSResponse.serializer(), respBody)

            if (ttsResp.code != 3000) throw Exception("TTS error ${ttsResp.code}: ${ttsResp.message}")
            val audioData = Base64.decode(ttsResp.data ?: "", Base64.DEFAULT)
            audioData
        }.also {
            if (it.isFailure) _ttsState.value = TTSState.IDLE
        }
    }

    fun playAudio(audioData: ByteArray) {
        stopPlayback()
        _ttsState.value = TTSState.PLAYING
        scope.launch {
            try {
                val bufSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(bufSize, audioData.size))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack?.write(audioData, 0, audioData.size)
                audioTrack?.play()

                val durationMs = audioData.size * 1000L / (SAMPLE_RATE * 2)
                delay(durationMs + 200)
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
            } finally {
                stopPlayback()
                _ttsState.value = TTSState.IDLE
            }
        }
    }

    fun stopPlayback() {
        audioTrack?.let {
            runCatching { it.stop(); it.release() }
        }
        audioTrack = null
    }

    @Suppress("MissingPermission")
    fun startASR(appId: String, accessToken: String, cluster: String) {
        stopASR()
        _asrState.value = ASRState.LISTENING

        val wsUrl = "$ASR_WS_URL?appid=$appId&cluster=$cluster&token=$accessToken"
        val request = Request.Builder().url(wsUrl).build()

        asrWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val resp = json.decodeFromString(VolcanoASRResponse.serializer(), text)
                    if (resp.result.isNotBlank()) {
                        scope.launch { _asrResult.emit(resp.result) }
                    }
                    if (resp.is_final) {
                        _asrState.value = ASRState.IDLE
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "ASR WebSocket failure", t)
                _asrState.value = ASRState.IDLE
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _asrState.value = ASRState.IDLE
            }
        })

        recordJob = scope.launch {
            val bufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2
            )
            audioRecord?.startRecording()

            val buffer = ByteArray(bufSize)
            while (isActive && _asrState.value == ASRState.LISTENING) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    val encoded = Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP)
                    val payload = """{"audio":"$encoded","is_last":false}"""
                    asrWebSocket?.send(payload)
                }
                delay(100)
            }

            asrWebSocket?.send("""{"audio":"","is_last":true}""")
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
    }

    fun stopASR() {
        _asrState.value = ASRState.PROCESSING
        recordJob?.cancel()
        recordJob = null
        asrWebSocket?.close(1000, "done")
        asrWebSocket = null
        audioRecord?.let { runCatching { it.stop(); it.release() } }
        audioRecord = null
        _asrState.value = ASRState.IDLE
    }

    fun destroy() {
        stopPlayback()
        stopASR()
        scope.cancel()
    }
}
