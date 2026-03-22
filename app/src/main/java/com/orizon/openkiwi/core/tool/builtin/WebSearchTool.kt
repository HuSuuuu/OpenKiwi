package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.network.HtmlExtractor
import okhttp3.OkHttpClient
import okhttp3.Request

class WebSearchTool(private val httpClient: OkHttpClient) : Tool {

    override val definition = ToolDefinition(
        name = "web_search",
        description = """Web search / fetch / extract. Prefer explicit action:
- action=search + query=关键词（若只传 query 则自动视为 search）
- action=fetch + url=完整链接
- action=extract + url=链接（抽取正文与表格）
search 需配置 search_api_url，否则返回提示；无 API 时用 web_fetch 或直接 fetch 已知 URL。""",
        category = ToolCategory.SEARCH.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "search | fetch | extract（可省略：仅有 query 时默认为 search，仅有 url 时默认为 fetch）",
                required = false, enumValues = listOf("search", "fetch", "extract")),
            "query" to ToolParamDef("string", "搜索关键词（action=search 时必填，除非只传本字段则等价于 search）"),
            "url" to ToolParamDef("string", "URL to fetch/extract (for fetch/extract actions)"),
            "search_api_url" to ToolParamDef("string", "Custom search API URL template with {query} placeholder"),
            "max_results" to ToolParamDef("string", "Maximum results (default 5)")
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
                    fetchUrl(reqUrl)
                } else {
                    ToolResult("web_search", false, "", "No search API configured. Use fetch action with a direct URL, or provide search_api_url.")
                }
            }
            "fetch" -> {
                val u = url ?: return ToolResult("web_search", false, "", "Missing url")
                fetchUrl(u)
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
