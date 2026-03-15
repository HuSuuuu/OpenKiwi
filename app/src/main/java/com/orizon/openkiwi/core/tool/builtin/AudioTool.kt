package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class AudioTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "audio",
        description = "Record audio from microphone and play audio files",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: record_start, record_stop, play, stop_play, list_recordings", true,
                enumValues = listOf("record_start", "record_stop", "play", "stop_play", "list_recordings")),
            "duration_seconds" to ToolParamDef("string", "Max recording duration in seconds", false, "30"),
            "file_path" to ToolParamDef("string", "Audio file path for playback")
        ),
        requiredParams = listOf("action")
    )

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentRecordingPath: String? = null

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val action = params["action"]?.toString() ?: return@withContext errorResult("Missing action")
        runCatching {
            when (action) {
                "record_start" -> {
                    if (recorder != null) return@runCatching errorResult("Already recording")
                    val dir = File(context.filesDir, "recordings").also { it.mkdirs() }
                    val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
                    currentRecordingPath = file.absolutePath
                    recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(file.absolutePath)
                        prepare()
                        start()
                    }
                    val maxSec = params["duration_seconds"]?.toString()?.toIntOrNull() ?: 30
                    ToolResult(definition.name, true, "Recording started: ${file.name} (max ${maxSec}s)")
                }
                "record_stop" -> {
                    val r = recorder ?: return@runCatching errorResult("Not recording")
                    r.stop()
                    r.release()
                    recorder = null
                    val path = currentRecordingPath ?: ""
                    currentRecordingPath = null
                    ToolResult(definition.name, true, "Recording saved: $path")
                }
                "play" -> {
                    val path = params["file_path"]?.toString() ?: return@runCatching errorResult("Missing file_path")
                    player?.release()
                    player = MediaPlayer().apply {
                        setDataSource(path)
                        prepare()
                        start()
                    }
                    ToolResult(definition.name, true, "Playing: $path (duration: ${player?.duration?.div(1000)}s)")
                }
                "stop_play" -> {
                    player?.stop()
                    player?.release()
                    player = null
                    ToolResult(definition.name, true, "Playback stopped")
                }
                "list_recordings" -> {
                    val dir = File(context.filesDir, "recordings")
                    val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                    val sb = StringBuilder("Recordings (${files.size}):\n")
                    files.take(20).forEach { f ->
                        sb.appendLine("  ${f.name} (${f.length() / 1024}KB)")
                    }
                    ToolResult(definition.name, true, sb.toString())
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
