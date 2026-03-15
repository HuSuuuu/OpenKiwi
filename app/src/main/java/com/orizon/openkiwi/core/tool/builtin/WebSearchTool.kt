package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.network.HtmlExtractor
import okhttp3.OkHttpClient
import okhttp3.Request

class WebSearchTool(private val httpClient: OkHttpClient) : Tool {

    override val definition = ToolDefinition(
        name = "web_search",
        description = "Search the web using configured search engine API, fetch and extract clean content from URLs. Supports multi-page results.",
        category = ToolCategory.SEARCH.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: search, fetch, extract",
                required = true, enumValues = listOf("search", "fetch", "extract")),
            "query" to ToolParamDef("string", "Search query (for search action)"),
            "url" to ToolParamDef("string", "URL to fetch/extract (for fetch/extract actions)"),
            "search_api_url" to ToolParamDef("string", "Custom search API URL template with {query} placeholder"),
            "max_results" to ToolParamDef("string", "Maximum results (default 5)")
        ),
        requiredParams = listOf("action"),
        returnDescription = "Search results or extracted web content",
        timeoutMs = 30_000
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return ToolResult("web_search", false, "", "Missing action")

        return when (action.lowercase()) {
            "search" -> {
                val query = params["query"]?.toString() ?: return ToolResult("web_search", false, "", "Missing query")
                val apiUrl = params["search_api_url"]?.toString()
                if (apiUrl != null) {
                    val url = apiUrl.replace("{query}", java.net.URLEncoder.encode(query, "UTF-8"))
                    fetchUrl(url)
                } else {
                    ToolResult("web_search", false, "", "No search API configured. Use fetch action with a direct URL, or provide search_api_url.")
                }
            }
            "fetch" -> {
                val url = params["url"]?.toString() ?: return ToolResult("web_search", false, "", "Missing url")
                fetchUrl(url)
            }
            "extract" -> {
                val url = params["url"]?.toString() ?: return ToolResult("web_search", false, "", "Missing url")
                val fetchResult = fetchRawHtml(url) ?: return ToolResult("web_search", false, "", "Failed to fetch URL")
                val extracted = HtmlExtractor.extractArticle(fetchResult)
                val output = buildString {
                    appendLine("Title: ${extracted.title}")
                    appendLine("---")
                    appendLine(extracted.text.take(10_000))
                    if (extracted.tables.isNotEmpty()) {
                        appendLine("\n--- Tables ---")
                        extracted.tables.forEachIndexed { i, table ->
                            appendLine("Table ${i + 1}:")
                            table.forEach { row -> appendLine(row.joinToString(" | ")) }
                        }
                    }
                    if (extracted.links.isNotEmpty()) {
                        appendLine("\n--- Links ---")
                        extracted.links.take(10).forEach { (url, text) -> appendLine("$text: $url") }
                    }
                }
                ToolResult("web_search", true, output)
            }
            else -> ToolResult("web_search", false, "", "Unknown action: $action")
        }
    }

    private fun fetchUrl(url: String): ToolResult {
        return try {
            val html = fetchRawHtml(url) ?: return ToolResult("web_search", false, "", "Fetch failed")
            val extracted = HtmlExtractor.extractMainContent(html)
            val title = HtmlExtractor.extractTitle(html)
            val output = buildString {
                if (title.isNotBlank()) appendLine("Title: $title\n---")
                append(extracted.take(15_000))
            }
            ToolResult("web_search", true, output)
        } catch (e: Exception) {
            ToolResult("web_search", false, "", "Error: ${e.message}")
        }
    }

    private fun fetchRawHtml(url: String): String? {
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (_: Exception) { null }
    }
}
