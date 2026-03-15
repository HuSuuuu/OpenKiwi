package com.orizon.openkiwi.network

/**
 * Advanced HTML content extractor: strips ads, extracts article text,
 * parses tables into structured format.
 */
object HtmlExtractor {

    fun extractArticle(html: String): ExtractedContent {
        val title = extractTitle(html)
        val bodyText = extractMainContent(html)
        val tables = extractTables(html)
        val links = extractLinks(html)
        val images = extractImageUrls(html)

        return ExtractedContent(
            title = title,
            text = bodyText,
            tables = tables,
            links = links,
            images = images
        )
    }

    fun extractTitle(html: String): String {
        val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        return titleRegex.find(html)?.groupValues?.get(1)?.trim()?.decodeHtmlEntities() ?: ""
    }

    fun extractMainContent(html: String): String {
        var content = html

        // Remove non-content elements
        val removePatterns = listOf(
            Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            Regex("<nav[^>]*>.*?</nav>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            Regex("<header[^>]*>.*?</header>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            Regex("<footer[^>]*>.*?</footer>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            Regex("<aside[^>]*>.*?</aside>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex("<noscript[^>]*>.*?</noscript>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            // Common ad classes
            Regex("""<div[^>]*class="[^"]*(?:ad-|ads-|advert|banner|sidebar|cookie|popup|modal)[^"]*"[^>]*>.*?</div>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        )
        for (pattern in removePatterns) {
            content = pattern.replace(content, "")
        }

        // Try to find main article content
        val articleRegex = Regex("<article[^>]*>(.*?)</article>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val mainRegex = Regex("<main[^>]*>(.*?)</main>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val contentDiv = Regex("""<div[^>]*class="[^"]*(?:content|article|post|entry|text|body)[^"]*"[^>]*>(.*?)</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        val mainContent = articleRegex.find(content)?.groupValues?.get(1)
            ?: mainRegex.find(content)?.groupValues?.get(1)
            ?: contentDiv.find(content)?.groupValues?.get(1)
            ?: content

        // Convert block elements to newlines
        var text = mainContent
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("</?p[^>]*>"), "\n")
            .replace(Regex("</?div[^>]*>"), "\n")
            .replace(Regex("</?h[1-6][^>]*>"), "\n")
            .replace(Regex("</?li[^>]*>"), "\n• ")
            .replace(Regex("</?tr[^>]*>"), "\n")
            .replace(Regex("</?td[^>]*>"), " | ")

        // Strip remaining tags
        text = text.replace(Regex("<[^>]+>"), "")
        text = text.decodeHtmlEntities()

        // Clean up whitespace
        text = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

        return text.take(50_000)
    }

    fun extractTables(html: String): List<List<List<String>>> {
        val tables = mutableListOf<List<List<String>>>()
        val tableRegex = Regex("<table[^>]*>(.*?)</table>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val rowRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val cellRegex = Regex("<t[hd][^>]*>(.*?)</t[hd]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        for (tableMatch in tableRegex.findAll(html)) {
            val rows = mutableListOf<List<String>>()
            for (rowMatch in rowRegex.findAll(tableMatch.groupValues[1])) {
                val cells = cellRegex.findAll(rowMatch.groupValues[1]).map {
                    it.groupValues[1].replace(Regex("<[^>]+>"), "").trim().decodeHtmlEntities()
                }.toList()
                if (cells.isNotEmpty()) rows.add(cells)
            }
            if (rows.isNotEmpty()) tables.add(rows)
        }
        return tables
    }

    fun extractLinks(html: String): List<Pair<String, String>> {
        val linkRegex = Regex("""<a[^>]*href="([^"]*)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return linkRegex.findAll(html).map {
            val url = it.groupValues[1]
            val text = it.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            url to text
        }.filter { it.first.isNotBlank() && it.second.isNotBlank() }.toList().take(50)
    }

    fun extractImageUrls(html: String): List<String> {
        val imgRegex = Regex("""<img[^>]*src="([^"]*)"[^>]*>""", RegexOption.IGNORE_CASE)
        return imgRegex.findAll(html).map { it.groupValues[1] }
            .filter { it.isNotBlank() && !it.contains("data:image") }
            .toList().take(20)
    }

    private fun String.decodeHtmlEntities(): String {
        return this
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace(Regex("&#(\\d+);")) { String(Character.toChars(it.groupValues[1].toInt())) }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { String(Character.toChars(it.groupValues[1].toInt(16))) }
    }
}

data class ExtractedContent(
    val title: String = "",
    val text: String = "",
    val tables: List<List<List<String>>> = emptyList(),
    val links: List<Pair<String, String>> = emptyList(),
    val images: List<String> = emptyList()
)
