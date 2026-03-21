package com.orizon.openkiwi.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_PARSE_LENGTH = 60_000

private sealed class MdBlock {
    data class Heading(val level: Int, val content: String) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    data class Paragraph(val content: String) : MdBlock()
    data class BulletList(val items: List<String>) : MdBlock()
    data class NumberedList(val items: List<String>) : MdBlock()
    data class Quote(val content: String) : MdBlock()
    data object Divider : MdBlock()
}

private fun parseBlocks(markdown: String): List<MdBlock> {
    val input = if (markdown.length > MAX_PARSE_LENGTH)
        markdown.substring(0, MAX_PARSE_LENGTH) + "\n\n...(内容过长，已截断)..."
    else markdown

    val blocks = mutableListOf<MdBlock>()
    val lines = input.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MdBlock.Code(lang, codeLines.joinToString("\n")))
                if (i < lines.size) i++
            }

            isHeading(trimmed) -> {
                val (level, content) = parseHeading(trimmed)
                blocks.add(MdBlock.Heading(level, content))
                i++
            }

            isDivider(trimmed) -> {
                blocks.add(MdBlock.Divider)
                i++
            }

            isBulletItem(trimmed) -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (!isBulletItem(t)) break
                    items.add(t.substring(2))
                    i++
                }
                blocks.add(MdBlock.BulletList(items))
            }

            isNumberedItem(trimmed) -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    val after = extractNumberedContent(t)
                    if (after == null) break
                    items.add(after)
                    i++
                }
                blocks.add(MdBlock.NumberedList(items))
            }

            trimmed.startsWith(">") -> {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").removePrefix(" "))
                    i++
                }
                blocks.add(MdBlock.Quote(quoteLines.joinToString("\n")))
            }

            trimmed.isEmpty() -> i++

            else -> {
                val paraLines = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (l.isEmpty() || l.startsWith("#") || l.startsWith("```") ||
                        isDivider(l) || l.startsWith(">") ||
                        isBulletItem(l) || isNumberedItem(l)
                    ) break
                    paraLines.add(lines[i])
                    i++
                }
                if (paraLines.isNotEmpty()) {
                    blocks.add(MdBlock.Paragraph(paraLines.joinToString("\n")))
                }
            }
        }
    }
    return blocks
}

private fun isHeading(line: String): Boolean {
    if (line.isEmpty() || line[0] != '#') return false
    var hashes = 0
    while (hashes < line.length && hashes < 6 && line[hashes] == '#') hashes++
    return hashes in 1..6 && hashes < line.length && line[hashes] == ' '
}

private fun parseHeading(line: String): Pair<Int, String> {
    var level = 0
    while (level < line.length && level < 6 && line[level] == '#') level++
    return Pair(level, line.substring(level).trimStart())
}

private fun isDivider(line: String): Boolean {
    if (line.length < 3) return false
    val ch = line[0]
    if (ch != '-' && ch != '*' && ch != '_') return false
    return line.all { it == ch }
}

private fun isBulletItem(line: String): Boolean {
    if (line.length < 2) return false
    val ch = line[0]
    return (ch == '-' || ch == '*' || ch == '+') && line[1] == ' '
}

private fun isNumberedItem(line: String): Boolean {
    var i = 0
    while (i < line.length && line[i].isDigit()) i++
    return i > 0 && i < line.length - 1 && line[i] == '.' && line[i + 1] == ' '
}

private fun extractNumberedContent(line: String): String? {
    var i = 0
    while (i < line.length && line[i].isDigit()) i++
    if (i == 0 || i >= line.length - 1 || line[i] != '.' || line[i + 1] != ' ') return null
    return line.substring(i + 2)
}

private fun buildInlineAnnotated(
    text: String,
    codeBackground: Color,
    linkColor: Color
): AnnotatedString = buildAnnotatedString {
    val len = text.length
    var pos = 0
    while (pos < len) {
        val ch = text[pos]
        when {
            ch == '`' && (pos + 1 >= len || text[pos + 1] != '`') -> {
                val end = text.indexOf('`', pos + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, letterSpacing = 0.sp)) {
                        append("\u2006")
                        append(text, pos + 1, end)
                        append("\u2006")
                    }
                    pos = end + 1
                } else { append(ch); pos++ }
            }

            ch == '*' && pos + 2 < len && text[pos + 1] == '*' && text[pos + 2] == '*' -> {
                val end = text.indexOf("***", pos + 3)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(text, pos + 3, end)
                    }
                    pos = end + 3
                } else { append(ch); pos++ }
            }

            ch == '*' && pos + 1 < len && text[pos + 1] == '*' -> {
                val end = text.indexOf("**", pos + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text, pos + 2, end)
                    }
                    pos = end + 2
                } else { append(ch); pos++ }
            }

            ch == '*' -> {
                val end = findSingleMarkerEnd(text, pos + 1, '*')
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text, pos + 1, end)
                    }
                    pos = end + 1
                } else { append(ch); pos++ }
            }

            ch == '~' && pos + 1 < len && text[pos + 1] == '~' -> {
                val end = text.indexOf("~~", pos + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text, pos + 2, end)
                    }
                    pos = end + 2
                } else { append(ch); pos++ }
            }

            ch == '[' -> {
                val cb = text.indexOf(']', pos + 1)
                if (cb != -1 && cb + 1 < len && text[cb + 1] == '(') {
                    val cp = text.indexOf(')', cb + 2)
                    if (cp != -1) {
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            append(text, pos + 1, cb)
                        }
                        pos = cp + 1
                    } else { append(ch); pos++ }
                } else { append(ch); pos++ }
            }

            else -> { append(ch); pos++ }
        }
    }
}

private fun findSingleMarkerEnd(text: String, start: Int, marker: Char): Int {
    var i = start
    val len = text.length
    while (i < len) {
        if (text[i] == marker && (i + 1 >= len || text[i + 1] != marker) && i > start) {
            return i
        }
        i++
    }
    return -1
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val stableKey = remember(markdown) {
        if (markdown.length < 500) markdown
        else "${markdown.length}_${markdown.hashCode()}"
    }

    val blocks by produceState(initialValue = emptyList<MdBlock>(), key1 = stableKey) {
        value = withContext(Dispatchers.Default) {
            parseBlocks(markdown)
        }
    }
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val codeSurfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val linkColor = MaterialTheme.colorScheme.primary
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (blocks.isEmpty() && markdown.isNotBlank()) {
            Text(
                text = markdown.take(800),
                style = style,
                color = color.copy(alpha = 0.9f)
            )
            return@Column
        }
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> style.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        2 -> style.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        3 -> style.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        else -> style.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = buildInlineAnnotated(block.content, codeBackground, linkColor),
                        style = headingStyle,
                        color = color,
                        modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 2.dp)
                    )
                }

                is MdBlock.Code -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = codeSurfaceColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = block.lang.ifBlank { "code" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color.copy(alpha = 0.5f)
                                )
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(block.code)) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentCopy, null,
                                        modifier = Modifier.size(13.dp),
                                        tint = color.copy(alpha = 0.4f)
                                    )
                                }
                            }
                            Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 18.sp
                                ),
                                color = color.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                            )
                        }
                    }
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = buildInlineAnnotated(block.content, codeBackground, linkColor),
                        style = style,
                        color = color
                    )
                }

                is MdBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        block.items.forEach { item ->
                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                Text("•", style = style, color = color, modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    text = buildInlineAnnotated(item, codeBackground, linkColor),
                                    style = style,
                                    color = color,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MdBlock.NumberedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        block.items.forEachIndexed { idx, item ->
                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    "${idx + 1}.",
                                    style = style,
                                    color = color,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = buildInlineAnnotated(item, codeBackground, linkColor),
                                    style = style,
                                    color = color,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MdBlock.Quote -> {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row {
                            Surface(
                                modifier = Modifier.width(3.dp).fillMaxHeight(),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            ) {}
                            Text(
                                text = buildInlineAnnotated(block.content, codeBackground, linkColor),
                                style = style.copy(fontStyle = FontStyle.Italic),
                                color = color.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                is MdBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = color.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}
