package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.network.HtmlExtractor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WebSearchTool(private val httpClient: OkHttpClient) : Tool {

    override val definition = ToolDefinition(
        name = "web_search",
        description = "Search the web, fetch a URL, or extract page content. Actions: search (needs search_api_url), fetch, extract.",
        category = ToolCategory.SEARCH.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "search, fetch, or extract",
                required = false, enumValues = listOf("search", "fetch", "extract")),
            "query" to ToolParamDef("string", "Search keywords"),
            "url" to ToolParamDef("string", "URL to fetch or extract"),
            "search_api_url" to ToolParamDef("string", "Search API URL template with {query}"),
            "max_results" to ToolParamDef("string", "Max results (default 5)")
        ),
        requiredParams = emptyList(),
        returnDescription = "Search results or extracted web content",
        timeoutMs = 30_000
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val actionRaw = params["action"]?.toString()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val query = params["query"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val url = params["url"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val action = actionRaw ?: when {
            query != null -> "search"
            url != null -> "fetch"
            else -> null
        } ?: return ToolResult(
            "web_search", false, "",
            "缺少参数：请传 action=search 且 query=关键词，或只传 query；或传 action=fetch 且 url=链接。"
        )

        return when (action.lowercase()) {
            "search" -> {
                val q = query ?: return ToolResult("web_search", false, "", "Missing query")
                val apiUrl = params["search_api_url"]?.toString()
                if (apiUrl != null) {
                    val reqUrl = apiUrl.replace("{query}", java.net.URLEncoder.encode(q, "UTF-8"))
                    val maxResults = params["max_results"]?.toString()?.toIntOrNull()?.coerceIn(1, 25) ?: 10
                    fetchUrl(reqUrl, maxResults)
                } else {
                    ToolResult("web_search", false, "", "No search API configured. Use fetch action with a direct URL, or provide search_api_url.")
                }
            }
            "fetch" -> {
                val u = url ?: return ToolResult("web_search", false, "", "Missing url")
                val maxResults = params["max_results"]?.toString()?.toIntOrNull()?.coerceIn(1, 25) ?: 10
                fetchUrl(u, maxResults)
            }
            "extract" -> {
                val u = url ?: return ToolResult("web_search", false, "", "Missing url")
                val fetchResult = fetchRawHtml(u) ?: return ToolResult("web_search", false, "", "Failed to fetch URL")
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

    private fun fetchUrl(url: String, maxResults: Int = 10): ToolResult {
        return try {
            val body = fetchRawHtml(url) ?: return ToolResult("web_search", false, "", "Fetch failed")
            val fromJson = formatSearxngOrGenericSearchJson(body, maxResults)
            if (fromJson != null) return ToolResult("web_search", true, fromJson)
            val extracted = HtmlExtractor.extractMainContent(body)
            val title = HtmlExtractor.extractTitle(body)
            val output = buildString {
                if (title.isNotBlank()) appendLine("Title: $title\n---")
                append(extracted.take(15_000))
            }
            ToolResult("web_search", true, output)
        } catch (e: Exception) {
            ToolResult("web_search", false, "", "Error: ${e.message}")
        }
    }

    /**
     * SearXNG `format=json` returns `{ "query": "...", "results": [ { "title","url","content",... } ] }`.
     * If parsing fails or shape differs, returns null and caller falls back to HTML extraction.
     */
    private fun formatSearxngOrGenericSearchJson(body: String, maxResults: Int): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return null
        return try {
            val root = JSONObject(trimmed)
            val results = root.optJSONArray("results") ?: return null
            if (results.length() == 0) {
                return "（搜索无结果）query=${root.optString("query", "")}"
            }
            val n = minOf(maxResults, results.length(), 25)
            buildString {
                val q = root.optString("query", "")
                if (q.isNotBlank()) appendLine("Query: $q")
                appendLine("---")
                for (i in 0 until n) {
                    val o = results.optJSONObject(i) ?: continue
                    val title = o.optString("title", "").trim()
                    val link = o.optString("url", "").trim()
                    val snippet = o.optString("content", o.optString("snippet", "")).trim()
                    appendLine("${i + 1}. $title")
                    if (link.isNotBlank()) appendLine("   URL: $link")
                    if (snippet.isNotBlank()) appendLine("   $snippet")
                    appendLine()
                }
            }.trimEnd()
        } catch (_: Exception) {
            null
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
