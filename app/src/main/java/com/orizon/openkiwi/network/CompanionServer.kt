package com.orizon.openkiwi.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import android.os.Environment
import java.io.File
import org.json.JSONObject
import com.orizon.openkiwi.core.agent.AgentEngine
import com.orizon.openkiwi.data.repository.ChatRepository
import com.orizon.openkiwi.data.repository.ModelRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class WsMessage(val type: String, val content: String = "", val sessionId: String = "")

class CompanionServer(
    private val context: Context,
    private val agentEngine: AgentEngine,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val feishuApiClient: FeishuApiClient? = null,
    private val userPreferences: com.orizon.openkiwi.data.preferences.UserPreferences? = null,
    val port: Int = 8765,
    val feishuEventHandler: FeishuEventHandler = FeishuEventHandler(agentEngine, chatRepository, feishuApiClient),
    var apiRouter: ApiRouter? = null
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val wsClients = ConcurrentHashMap<String, WebSocketClient>()
    private var currentSessionId: String? = null
    private val pendingCodeResults = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<String>>()

    fun hasConnectedClients(): Boolean = wsClients.isNotEmpty()

    suspend fun executeOnPC(code: String, language: String, timeoutMs: Long = 30000): String? {
        val client = wsClients.values.firstOrNull() ?: return null
        val requestId = java.util.UUID.randomUUID().toString()
        val deferred = kotlinx.coroutines.CompletableDeferred<String>()
        pendingCodeResults[requestId] = deferred

        val codeJson = kotlinx.serialization.json.JsonPrimitive(code)
        val msg = """{"type":"code_execute","content":$codeJson,"sessionId":"","language":"$language","timeout":${timeoutMs / 1000},"request_id":"$requestId"}"""
        client.send(msg)

        return try {
            kotlinx.coroutines.withTimeout(timeoutMs + 5000) {
                deferred.await()
            }
        } catch (_: Exception) {
            null
        } finally {
            pendingCodeResults.remove(requestId)
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning.get()) {
                    runCatching {
                        val client = serverSocket?.accept() ?: return@launch
                        launch { handleClient(client) }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("CompanionServer", "Failed to start server on port $port", t)
                isRunning.set(false)
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        wsClients.values.forEach { it.close() }
        wsClients.clear()
        runCatching { serverSocket?.close() }
        scope.cancel()
    }

    fun isRunning(): Boolean = isRunning.get()

    fun getLocalIp(): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
    }

    fun getUrl(): String = "http://${getLocalIp()}:$port"

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        val input = BufferedReader(InputStreamReader(socket.getInputStream()))
        val output = BufferedOutputStream(socket.getOutputStream())

        val requestLine = input.readLine() ?: return@withContext
        val headers = mutableMapOf<String, String>()
        var line = input.readLine()
        while (!line.isNullOrBlank()) {
            val parts = line.split(": ", limit = 2)
            if (parts.size == 2) headers[parts[0].lowercase()] = parts[1].trim()
            line = input.readLine()
        }

        if (headers["upgrade"]?.lowercase() == "websocket") {
            handleWebSocket(socket, input, output, headers)
        } else {
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            val method = requestLine.split(" ").getOrNull(0) ?: "GET"

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyChars = if (contentLength > 0) {
                CharArray(contentLength).also { input.read(it, 0, contentLength) }
            } else null
            val bodyStr = bodyChars?.let { String(it) } ?: ""

            if (path.startsWith("/api/v1/")) {
                val router = apiRouter
                if (router != null) {
                    val apiRequest = ApiRouter.HttpRequest(method, path, headers, bodyStr)
                    router.handleRequest(apiRequest, output)
                    socket.close()
                    return@withContext
                }
            }

            if (method == "POST" && path == "/api/send") {
                handleApiSend(output, bodyStr)
            } else if (method == "POST" && path == "/api/feishu/event") {
                handleFeishuEvent(output, bodyStr)
            } else {
                val html = getWebConsoleHtml()
                val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n"
                output.write(response.toByteArray())
                output.write(html.toByteArray())
                output.flush()
            }
            socket.close()
        }
    }

    private suspend fun handleApiSend(output: BufferedOutputStream, body: String) {
        val msg = runCatching { json.decodeFromString(WsMessage.serializer(), body) }.getOrNull()
        val respJson = if (msg != null && msg.content.isNotBlank()) {
            val sessionId = ensureSession()
            val sb = StringBuilder()
            agentEngine.processMessage(sessionId, msg.content).collect { sb.append(it) }
            json.encodeToString(WsMessage.serializer(), WsMessage("response", sb.toString(), sessionId))
        } else {
            """{"type":"error","content":"empty message"}"""
        }
        val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${respJson.toByteArray().size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        output.write(resp.toByteArray())
        output.write(respJson.toByteArray())
        output.flush()
    }

    private suspend fun handleFeishuEvent(output: BufferedOutputStream, body: String) {
        val start = System.currentTimeMillis()
        val jsonObj = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
        }.getOrNull()

        if (jsonObj == null) {
            sendHttpResponse(output, 400, """{"error":"invalid json"}""")
            return
        }

        val type = jsonObj["type"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }

        if (type == "url_verification") {
            val challenge = jsonObj["challenge"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
            sendHttpResponse(output, 200, """{"challenge":"$challenge"}""")
            return
        }

        sendHttpResponse(output, 200, """{"code":0}""")

        val header = jsonObj["header"] as? kotlinx.serialization.json.JsonObject
        val eventType = header?.get("event_type")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        val event = jsonObj["event"] as? kotlinx.serialization.json.JsonObject ?: return

        if (eventType == "im.message.receive_v1") {
            val msgStart = System.currentTimeMillis()
            feishuEventHandler.handleIncomingEventAsync(event)
            android.util.Log.i(
                "CompanionServer",
                "Feishu message queued in ${System.currentTimeMillis() - msgStart}ms"
            )
        }
        android.util.Log.i(
            "CompanionServer",
            "Feishu event acked in ${System.currentTimeMillis() - start}ms, type=$eventType"
        )
    }

    private fun sendHttpResponse(output: BufferedOutputStream, code: Int, body: String) {
        val statusText = when (code) { 200 -> "OK"; 400 -> "Bad Request"; else -> "Error" }
        val resp = "HTTP/1.1 $code $statusText\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${body.toByteArray().size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        output.write(resp.toByteArray())
        output.write(body.toByteArray())
        output.flush()
    }

    private suspend fun handleWebSocket(socket: Socket, reader: BufferedReader, output: BufferedOutputStream, headers: Map<String, String>) {
        val key = headers["sec-websocket-key"] ?: return
        val acceptKey = Base64.encodeToString(
            MessageDigest.getInstance("SHA-1").digest("${key}258EAFA5-E914-47DA-95CA-C5AB0DC85B11".toByteArray()),
            Base64.NO_WRAP
        ).trim()
        val handshake = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $acceptKey\r\n\r\n"
        output.write(handshake.toByteArray())
        output.flush()

        val clientId = java.util.UUID.randomUUID().toString()
        val wsClient = WebSocketClient(socket, output)
        wsClients[clientId] = wsClient

        wsClient.send(json.encodeToString(WsMessage.serializer(), WsMessage("connected", "OpenKiwi PC 已连接", "")))

        try {
            val rawInput = socket.getInputStream()
            while (isRunning.get() && !socket.isClosed) {
                val frame = readWebSocketFrame(rawInput) ?: break
                when (frame.opcode) {
                    8 -> break // close
                    9 -> { // ping → reply pong
                        wsClient.sendFrame(10, frame.payload)
                    }
                    10 -> { /* pong, ignore */ }
                    1 -> {
                        val text = String(frame.payload)
                        handleWsMessage(clientId, text)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            wsClients.remove(clientId)
            runCatching { socket.close() }
        }
    }

    private suspend fun handleWsMessage(clientId: String, text: String) {
        val client = wsClients[clientId] ?: return
        val root = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
        }.getOrNull()
        val typePrim = root?.get("type")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        if (typePrim == "file_upload") {
            handleFileUpload(client, root)
            return
        }
        if (typePrim == "file_download") {
            handleFileDownload(client, root)
            return
        }

        val msg = runCatching { json.decodeFromString(WsMessage.serializer(), text) }.getOrNull() ?: return

        when (msg.type) {
            "chat", "chat_stream" -> {
                if (msg.content.isBlank()) return
                val sessionId = ensureSession()
                client.send(json.encodeToString(WsMessage.serializer(), WsMessage("thinking", "", sessionId)))
                val sb = StringBuilder()
                agentEngine.processMessage(sessionId, msg.content).collect { chunk ->
                    sb.append(chunk)
                    val streamJson = json.encodeToString(WsMessage.serializer(), WsMessage("stream", chunk, sessionId))
                    client.send(streamJson)
                    client.send(json.encodeToString(WsMessage.serializer(), WsMessage("chat_stream", chunk, sessionId)))
                }
                val full = sb.toString()
                client.send(json.encodeToString(WsMessage.serializer(), WsMessage("done", full, sessionId)))
                client.send(json.encodeToString(WsMessage.serializer(), WsMessage("chat_end", full, sessionId)))
            }
            "terminal" -> {
                if (msg.content.isBlank()) return
                scope.launch {
                    runCatching {
                        val process = ProcessBuilder("sh", "-c", msg.content)
                            .redirectErrorStream(true).start()
                        val output = process.inputStream.bufferedReader().use { it.readText() }
                        process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                        client.send(json.encodeToString(WsMessage.serializer(),
                            WsMessage("terminal_output", output.take(10_000))))
                    }.onFailure { e ->
                        client.send(json.encodeToString(WsMessage.serializer(),
                            WsMessage("terminal_output", "Error: ${e.message}")))
                    }
                }
            }
            "device_info" -> {
                val info = buildDeviceInfoJson()
                client.send(json.encodeToString(WsMessage.serializer(),
                    WsMessage("device_info_response", info)))
            }
            "sessions" -> {
                scope.launch {
                    val sessions = chatRepository.getAllSessionsOnce()
                    val list = sessions.joinToString(",") { """{"id":"${it.id}","title":"${it.title}"}""" }
                    client.send(json.encodeToString(WsMessage.serializer(),
                        WsMessage("session_list", "[$list]")))
                }
            }
            "code_result" -> {
                val requestId = runCatching {
                    val extra = kotlinx.serialization.json.Json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
                    extra?.get("request_id")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                }.getOrNull() ?: ""
                pendingCodeResults[requestId]?.complete(msg.content)
            }
            "ping" -> {
                client.send(json.encodeToString(WsMessage.serializer(), WsMessage("pong", "")))
            }
        }
    }

    private fun handleFileUpload(client: WebSocketClient, root: kotlinx.serialization.json.JsonObject) {
        scope.launch {
            val reqId = root["request_id"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
            val name = root["filename"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "upload.bin"
            val b64 = root["data"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)
            val result = runCatching {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val dir = File(context.filesDir, "companion_uploads").apply { mkdirs() }
                val out = File(dir, safeName)
                out.writeBytes(bytes)
                JSONObject().apply {
                    if (reqId.isNotEmpty()) put("request_id", reqId)
                    put("ok", true)
                    put("path", out.absolutePath)
                    put("bytes", bytes.size)
                }.toString()
            }.getOrElse { e ->
                JSONObject().apply {
                    if (reqId.isNotEmpty()) put("request_id", reqId)
                    put("ok", false)
                    put("error", e.message?.take(200) ?: "error")
                }.toString()
            }
            client.send(json.encodeToString(WsMessage.serializer(), WsMessage("file_data", result, "")))
        }
    }

    private fun handleFileDownload(client: WebSocketClient, root: kotlinx.serialization.json.JsonObject) {
        scope.launch {
            val reqId = root["request_id"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
            val path = root["path"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
            val f = safeFileForCompanion(path)
            val result = when {
                f == null || !f.exists() || !f.isFile ->
                    JSONObject().apply {
                        if (reqId.isNotEmpty()) put("request_id", reqId)
                        put("ok", false)
                        put("error", "invalid_path_or_missing")
                    }.toString()
                f.length() > 900_000 ->
                    JSONObject().apply {
                        if (reqId.isNotEmpty()) put("request_id", reqId)
                        put("ok", false)
                        put("error", "file_too_large")
                    }.toString()
                else -> {
                    val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                    JSONObject().apply {
                        if (reqId.isNotEmpty()) put("request_id", reqId)
                        put("ok", true)
                        put("filename", f.name)
                        put("data", b64)
                    }.toString()
                }
            }
            client.send(json.encodeToString(WsMessage.serializer(), WsMessage("file_data", result, "")))
        }
    }

    private fun safeFileForCompanion(path: String): File? {
        if (path.isBlank()) return null
        val f = try {
            File(path).canonicalFile
        } catch (_: Exception) {
            return null
        }
        val extRoot = runCatching { Environment.getExternalStorageDirectory().canonicalFile }.getOrNull()
        val dl = runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalFile }.getOrNull()
        val doc = runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).canonicalFile }.getOrNull()
        val appFiles = context.filesDir.canonicalFile
        val p = f.canonicalPath
        val allowed = listOfNotNull(extRoot, dl, doc, appFiles).any { root ->
            p.startsWith(root.path)
        }
        return if (allowed) f else null
    }

    private fun buildDeviceInfoJson(): String {
        val batteryIntent = context.registerReceiver(null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
        val batteryPct = if (scale > 0) level * 100 / scale else 0

        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val storageFree = (stat.availableBytes / (1024 * 1024)).toInt()
        val storageTotal = (stat.totalBytes / (1024 * 1024)).toInt()

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        return """{"battery_level":$batteryPct,"battery_charging":$isCharging,"storage_free_mb":$storageFree,"storage_total_mb":$storageTotal,"ram_free_mb":${memInfo.availMem / (1024*1024)},"ram_total_mb":${memInfo.totalMem / (1024*1024)},"ip_address":"${getLocalIp()}","android_version":"${android.os.Build.VERSION.RELEASE}","device_model":"${android.os.Build.MODEL}"}"""
    }

    private suspend fun ensureSession(): String {
        if (currentSessionId == null) {
            currentSessionId = chatRepository.createSession(title = "PC Companion")
        }
        return currentSessionId!!
    }

    private fun readWebSocketFrame(input: InputStream): WebSocketFrame? {
        val b1 = input.read()
        if (b1 == -1) return null
        val b2 = input.read()
        if (b2 == -1) return null

        val opcode = b1 and 0x0F
        val masked = (b2 and 0x80) != 0
        var length = (b2 and 0x7F).toLong()

        if (length == 126L) {
            length = ((input.read() shl 8) or input.read()).toLong()
        } else if (length == 127L) {
            length = 0
            for (i in 0 until 8) length = (length shl 8) or input.read().toLong()
        }

        val mask = if (masked) ByteArray(4).also { input.read(it) } else null
        val payload = ByteArray(length.toInt())
        var read = 0
        while (read < length) {
            val r = input.read(payload, read, (length - read).toInt())
            if (r == -1) break
            read += r
        }

        if (mask != null) {
            for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        }

        return WebSocketFrame(opcode, payload)
    }

    private data class WebSocketFrame(val opcode: Int, val payload: ByteArray)

    inner class WebSocketClient(private val socket: Socket, private val output: BufferedOutputStream) {
        @Synchronized
        fun send(text: String) {
            runCatching {
                val data = text.toByteArray(Charsets.UTF_8)
                val frame = buildWebSocketFrame(1, data)
                output.write(frame)
                output.flush()
            }
        }

        @Synchronized
        fun sendFrame(opcode: Int, payload: ByteArray) {
            runCatching {
                val frame = buildWebSocketFrame(opcode, payload)
                output.write(frame)
                output.flush()
            }
        }

        fun close() {
            runCatching {
                val frame = buildWebSocketFrame(8, ByteArray(0))
                output.write(frame)
                output.flush()
                socket.close()
            }
        }

        private fun buildWebSocketFrame(opcode: Int, payload: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(0x80 or opcode)
            when {
                payload.size < 126 -> out.write(payload.size)
                payload.size < 65536 -> {
                    out.write(126)
                    out.write(payload.size shr 8)
                    out.write(payload.size and 0xFF)
                }
                else -> {
                    out.write(127)
                    for (i in 7 downTo 0) out.write((payload.size.toLong() shr (8 * i)).toInt() and 0xFF)
                }
            }
            out.write(payload)
            return out.toByteArray()
        }
    }

    private fun getWebConsoleHtml(): String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>OpenKiwi PC Console</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#0f0f0f;color:#e0e0e0;height:100vh;display:flex;flex-direction:column}
.header{background:#1a1a2e;padding:16px 24px;display:flex;align-items:center;gap:12px;border-bottom:1px solid #2a2a3e}
.header h1{font-size:20px;color:#4fc3f7;font-weight:600}
.status{width:10px;height:10px;border-radius:50%;background:#f44;transition:background .3s}
.status.on{background:#4caf50}
.status-text{font-size:12px;color:#888}
.chat{flex:1;overflow-y:auto;padding:20px;display:flex;flex-direction:column;gap:12px}
.msg{max-width:75%;padding:12px 16px;border-radius:16px;line-height:1.6;font-size:14px;word-break:break-word;white-space:pre-wrap}
.msg.user{align-self:flex-end;background:#1565c0;color:#fff;border-bottom-right-radius:4px}
.msg.ai{align-self:flex-start;background:#1e1e2e;color:#e0e0e0;border-bottom-left-radius:4px}
.msg.system{align-self:center;background:#2a2a3e;color:#888;font-size:12px;padding:6px 14px;border-radius:20px}
.tool-chip{display:inline-flex;align-items:center;gap:6px;background:#2a2a3e;border:1px solid #3a3a4e;border-radius:8px;padding:4px 10px;margin:3px 0;font-size:12px}
.tool-chip.ok{border-color:#4caf50;color:#4caf50}.tool-chip.fail{border-color:#f44;color:#f44}.tool-chip.run{border-color:#ff9800;color:#ff9800}
.input-area{background:#1a1a2e;padding:16px 24px;display:flex;gap:12px;border-top:1px solid #2a2a3e}
input{flex:1;background:#0f0f0f;border:1px solid #3a3a4e;border-radius:24px;padding:12px 20px;color:#e0e0e0;font-size:14px;outline:none}
input:focus{border-color:#4fc3f7}
button{background:#1565c0;color:#fff;border:none;border-radius:24px;padding:12px 28px;font-size:14px;cursor:pointer;transition:background .2s}
button:hover{background:#1976d2}
button:disabled{background:#333;cursor:not-allowed}
.typing{color:#4fc3f7;font-size:13px;padding:4px 0}
@keyframes blink{0%,100%{opacity:1}50%{opacity:0}}
.dot{animation:blink 1s infinite}
</style>
</head>
<body>
<div class="header">
<div class="status" id="status"></div>
<h1>OpenKiwi PC Console</h1>
<span class="status-text" id="statusText">连接中...</span>
</div>
<div class="chat" id="chat"></div>
<div class="input-area">
<input id="input" placeholder="输入消息..." autocomplete="off">
<button id="send" onclick="sendMsg()">发送</button>
</div>
<script>
const chat=document.getElementById('chat'),input=document.getElementById('input'),statusEl=document.getElementById('status'),statusText=document.getElementById('statusText'),sendBtn=document.getElementById('send');
let ws,streaming='',streamEl=null;
function connect(){
ws=new WebSocket('ws://'+location.host+'/ws');
ws.onopen=()=>{statusEl.classList.add('on');statusText.textContent='已连接';addMsg('system','已连接到 OpenKiwi')};
ws.onclose=()=>{statusEl.classList.remove('on');statusText.textContent='已断开';addMsg('system','连接断开，3秒后重连...');setTimeout(connect,3000)};
ws.onerror=()=>{};
ws.onmessage=(e)=>{
try{const d=JSON.parse(e.data);
if(d.type==='stream'){
if(!streamEl){streamEl=document.createElement('div');streamEl.className='msg ai';chat.appendChild(streamEl);streaming=''}
streaming+=d.content;streamEl.innerHTML=renderContent(streaming);chat.scrollTop=chat.scrollHeight;
}else if(d.type==='done'){streamEl=null;streaming='';sendBtn.disabled=false;
}else if(d.type==='thinking'){addMsg('system','Agent 思考中...');sendBtn.disabled=true;
}else if(d.type==='connected'){addMsg('system',d.content);
}else if(d.type==='pong'){}
}catch(err){}
}}
function renderContent(t){
return t.replace(/\[Calling tool: (.+?)]/g,'<div class="tool-chip run">⚡ $1 执行中</div>')
.replace(/\[Tool result: success]/g,'<div class="tool-chip ok">✓ 完成</div>')
.replace(/\[Tool result: failed]/g,'<div class="tool-chip fail">✗ 失败</div>')}
function addMsg(type,text){const d=document.createElement('div');d.className='msg '+type;d.innerHTML=type==='ai'?renderContent(text):text;chat.appendChild(d);chat.scrollTop=chat.scrollHeight}
function sendMsg(){const t=input.value.trim();if(!t||!ws||ws.readyState!==1)return;addMsg('user',t);ws.send(JSON.stringify({type:'chat',content:t}));input.value='';sendBtn.disabled=true}
input.addEventListener('keydown',(e)=>{if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();sendMsg()}});
connect();
</script>
</body>
</html>
""".trimIndent()
}
