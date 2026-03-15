package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class WebFetchTool(private val httpClient: OkHttpClient) : Tool {
    override val definition = ToolDefinition(
        name = "web_fetch",
        description = "Fetch content from a URL and return the response body as text. Supports HTTP/HTTPS.",
        category = ToolCategory.NETWORK.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "url" to ToolParamDef("string", "The URL to fetch", true),
            "method" to ToolParamDef("string", "HTTP method", false, "GET", listOf("GET", "HEAD"))
        ),
        requiredParams = listOf("url")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val url = params["url"]?.toString() ?: return@withContext ToolResult(
            toolName = definition.name, success = false, output = "", error = "Missing url"
        )

        runCatching {
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()?.take(50_000) ?: ""
            val plainText = body
                .replace(Regex("<script[^>]*>[\\s\\S]*?</script>"), "")
                .replace(Regex("<style[^>]*>[\\s\\S]*?</style>"), "")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            ToolResult(
                toolName = definition.name, success = true,
                output = "Status: ${response.code}\nContent (${plainText.length} chars):\n${plainText.take(20_000)}"
            )
        }.getOrElse { e ->
            ToolResult(toolName = definition.name, success = false, output = "", error = e.message)
        }
    }
}
