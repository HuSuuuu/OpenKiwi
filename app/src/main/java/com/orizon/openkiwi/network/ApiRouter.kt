package com.orizon.openkiwi.network

import com.orizon.openkiwi.core.agent.AgentEngine
import com.orizon.openkiwi.core.tool.ToolExecutor
import com.orizon.openkiwi.core.tool.ToolRegistry
import com.orizon.openkiwi.data.repository.ChatRepository
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.BufferedOutputStream

// RESTful API router extracted from CompanionServer.
// Handles /api/v1/ endpoints with token authentication.
class ApiRouter(
    private val agentEngine: AgentEngine,
    private val chatRepository: ChatRepository,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val apiAuth: ApiAuth
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String
    )

    suspend fun handleRequest(request: HttpRequest, output: BufferedOutputStream): Boolean {
        if (!request.path.startsWith("/api/v1/")) return false

        if (request.method == "OPTIONS") {
            sendCorsPreflightResponse(output)
            return true
        }

        val authHeader = request.headers["authorization"] ?: request.headers["x-api-key"]
        if (!apiAuth.validateToken(authHeader?.removePrefix("Bearer ")?.trim())) {
            sendJsonResponse(output, 401, "{\"error\":\"unauthorized\",\"message\":\"Invalid or missing API token\"}")
            return true
        }

        val subPath = request.path.removePrefix("/api/v1")

        when {
            request.method == "POST" && subPath == "/chat" -> handleChat(request, output)
            request.method == "POST" && subPath == "/chat/stream" -> handleChatStream(request, output)
            request.method == "GET" && subPath == "/tools" -> handleListTools(output)
            request.method == "POST" && subPath.startsWith("/tools/") && subPath.endsWith("/execute") ->
                handleToolExecute(subPath, request, output)
            request.method == "GET" && subPath == "/sessions" -> handleListSessions(output)
            request.method == "GET" && subPath == "/health" -> handleHealth(output)
            else -> {
                val msg = buildString {
                    append("{\"error\":\"not_found\",\"message\":\"Unknown endpoint: ")
                    append(request.method).append(" ").append(request.path)
                    append("\"}")
                }
                sendJsonResponse(output, 404, msg)
            }
        }
        return true
    }

    private suspend fun handleChat(request: HttpRequest, output: BufferedOutputStream) {
        val body = parseRequestBody(request.body)
        val message = body["message"] ?: body["content"] ?: ""
        val sessionId = body["session_id"]

        if (message.isBlank()) {
            sendJsonResponse(output, 400, "{\"error\":\"bad_request\",\"message\":\"message is required\"}")
            return
        }

        val sid = sessionId?.takeIf { it.isNotBlank() }
            ?: chatRepository.createSession(title = "API Request")
        val sb = StringBuilder()
        agentEngine.processMessage(sid, message).collect { sb.append(it) }

        val responseJson = buildString {
            append("{\"session_id\":")
            append(json.encodeToString(String.serializer(), sid))
            append(",\"content\":")
            append(json.encodeToString(String.serializer(), sb.toString()))
            append("}")
        }
        sendJsonResponse(output, 200, responseJson)
    }

    private suspend fun handleChatStream(request: HttpRequest, output: BufferedOutputStream) {
        val body = parseRequestBody(request.body)
        val message = body["message"] ?: body["content"] ?: ""
        val sessionId = body["session_id"]

        if (message.isBlank()) {
            sendJsonResponse(output, 400, "{\"error\":\"bad_request\",\"message\":\"message is required\"}")
            return
        }

        val sid = sessionId?.takeIf { it.isNotBlank() }
            ?: chatRepository.createSession(title = "API Stream")

        val sseHeaders = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: keep-alive\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: Authorization, Content-Type, X-Api-Key\r\n")
            append("\r\n")
        }
        output.write(sseHeaders.toByteArray())
        output.flush()

        agentEngine.processMessage(sid, message).collect { chunk ->
            val escaped = escapeJson(chunk)
            val event = buildString {
                append("data: {\"session_id\":\"").append(sid)
                append("\",\"content\":\"").append(escaped)
                append("\"}\n\n")
            }
            output.write(event.toByteArray())
            output.flush()
        }

        output.write("data: [DONE]\n\n".toByteArray())
        output.flush()
    }

    private fun escapeJson(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append('\\').append('\\')
                '"' -> sb.append('\\').append('"')
                '\n' -> sb.append('\\').append('n')
                '\r' -> sb.append('\\').append('r')
                '\t' -> sb.append('\\').append('t')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun handleListTools(output: BufferedOutputStream) {
        val tools = toolRegistry.getEnabledTools()
        val toolsJson = tools.joinToString(",") { tool ->
            val def = tool.definition
            val paramsJson = def.parameters.entries.joinToString(",") { (k, v) ->
                buildString {
                    append("{\"").append(k).append("\":{\"type\":\"")
                    append(v.type).append("\",\"description\":\"")
                    append(escapeJson(v.description)).append("\"}}")
                }
            }
            buildString {
                append("{\"name\":\"").append(escapeJson(def.name))
                append("\",\"description\":\"").append(escapeJson(def.description))
                append("\",\"category\":\"").append(def.category)
                append("\",\"parameters\":{").append(paramsJson).append("}}")
            }
        }
        val body = buildString {
            append("{\"tools\":[").append(toolsJson)
            append("],\"count\":").append(tools.size).append("}")
        }
        sendJsonResponse(output, 200, body)
    }

    private suspend fun handleToolExecute(subPath: String, request: HttpRequest, output: BufferedOutputStream) {
        val toolName = subPath.removePrefix("/tools/").removeSuffix("/execute")
        if (toolName.isBlank()) {
            sendJsonResponse(output, 400, "{\"error\":\"bad_request\",\"message\":\"tool name is required\"}")
            return
        }

        val params = parseRequestBody(request.body).mapValues { it.value as Any? }
        val result = toolExecutor.execute(toolName, params)

        val responseJson = buildString {
            append("{\"tool\":\"")
            append(escapeJson(toolName))
            append("\",\"success\":")
            append(result.success)
            append(",\"output\":")
            append(json.encodeToString(String.serializer(), result.output))
            result.error?.let {
                append(",\"error\":")
                append(json.encodeToString(String.serializer(), it))
            }
            append(",\"execution_time_ms\":")
            append(result.executionTimeMs)
            append("}")
        }
        sendJsonResponse(output, 200, responseJson)
    }

    private suspend fun handleListSessions(output: BufferedOutputStream) {
        val sessions = chatRepository.getAllSessionsOnce()
        val sessionsJson = sessions.joinToString(",") { s ->
            buildString {
                append("{\"id\":\"").append(s.id)
                append("\",\"title\":\"").append(escapeJson(s.title))
                append("\"}")
            }
        }
        val body = buildString {
            append("{\"sessions\":[").append(sessionsJson).append("]}")
        }
        sendJsonResponse(output, 200, body)
    }

    private fun handleHealth(output: BufferedOutputStream) {
        val toolCount = toolRegistry.getEnabledTools().size
        val body = buildString {
            append("{\"status\":\"ok\",\"tools\":").append(toolCount)
            append(",\"version\":\"1.0\"}")
        }
        sendJsonResponse(output, 200, body)
    }

    private fun parseRequestBody(body: String): Map<String, String> {
        return runCatching {
            val obj = json.parseToJsonElement(body) as? JsonObject ?: return emptyMap()
            obj.mapValues { (_, v) ->
                (v as? JsonPrimitive)?.content ?: v.toString()
            }
        }.getOrDefault(emptyMap())
    }

    private fun sendJsonResponse(output: BufferedOutputStream, code: Int, body: String) {
        val statusText = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"
            404 -> "Not Found"; 500 -> "Internal Server Error"; else -> "Error"
        }
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val resp = buildString {
            append("HTTP/1.1 $code $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: Authorization, Content-Type, X-Api-Key\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(resp.toByteArray())
        output.write(bodyBytes)
        output.flush()
    }

    private fun sendCorsPreflightResponse(output: BufferedOutputStream) {
        val resp = buildString {
            append("HTTP/1.1 204 No Content\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Authorization, Content-Type, X-Api-Key\r\n")
            append("Access-Control-Max-Age: 86400\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(resp.toByteArray())
        output.flush()
    }
}
