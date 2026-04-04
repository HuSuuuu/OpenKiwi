package com.orizon.openkiwi.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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

// ── Syntax highlight palette (VSCode Dark+ inspired) ─────────────────────────
private val HL_KEYWORD  = Color(0xFF569CD6)  // blue
private val HL_STRING   = Color(0xFFCE9178)  // orange
private val HL_COMMENT  = Color(0xFF6A9955)  // green
private val HL_NUMBER   = Color(0xFFB5CEA8)  // light green
private val HL_ANNOT    = Color(0xFFDDCE9F)  // yellow-ish (annotations/decorators)
private val HL_TYPE     = Color(0xFF4EC9B0)  // teal (capitalised identifiers)
private val HL_DEFAULT  = Color(0xFFD4D4D4)  // light grey
private val CODE_BG     = Color(0xFF1E1E1E)  // editor background
private val CODE_HDR_BG = Color(0xFF252526)  // header bar

private const val MAX_PARSE_LENGTH = 60_000
private const val MAX_HIGHLIGHT_LENGTH = 12_000

// ── Keywords ──────────────────────────────────────────────────────────────────
private val KOTLIN_KW = setOf(
    "fun", "val", "var", "class", "object", "interface", "data", "sealed",
    "abstract", "open", "override", "suspend", "inline", "reified",
    "private", "public", "protected", "internal", "companion",
    "return", "if", "else", "when", "for", "while", "do",
    "break", "continue", "try", "catch", "finally", "throw",
    "null", "true", "false", "is", "as", "in", "by",
    "this", "super", "import", "package", "typealias", "enum", "init",
    "constructor", "lateinit", "const", "operator", "crossinline",
    "noinline", "vararg", "actual", "expect", "external", "it"
)

private val PYTHON_KW = setOf(
    "def", "class", "import", "from", "return", "if", "elif", "else",
    "for", "while", "break", "continue", "pass", "try", "except", "finally",
    "raise", "with", "as", "and", "or", "not", "in", "is",
    "True", "False", "None", "lambda", "yield", "global", "nonlocal",
    "assert", "del", "async", "await", "print", "len", "range"
)

private val JS_KW = setOf(
    "function", "const", "let", "var", "class", "return",
    "if", "else", "for", "while", "do", "break", "continue",
    "try", "catch", "finally", "throw", "new", "this", "super",
    "import", "export", "from", "of", "in", "instanceof", "typeof",
    "null", "undefined", "true", "false", "async", "await", "yield",
    "switch", "case", "default", "delete", "void", "extends", "static",
    "interface", "type", "enum", "abstract", "implements", "readonly",
    "public", "private", "protected", "override", "namespace", "declare"
)

private val JAVA_KW = setOf(
    "public", "private", "protected", "class", "interface", "extends", "implements",
    "import", "package", "return", "if", "else", "for", "while", "do",
    "break", "continue", "try", "catch", "finally", "throw", "throws", "new",
    "this", "super", "static", "final", "abstract", "synchronized", "volatile",
    "null", "true", "false", "void", "int", "long", "double", "float",
    "boolean", "char", "byte", "short", "enum", "switch", "case", "default"
)

private val SHELL_KW = setOf(
    "if", "then", "else", "elif", "fi", "for", "do", "done",
    "while", "until", "case", "esac", "function", "in",
    "echo", "exit", "return", "export", "local", "set", "unset",
    "grep", "sed", "awk", "cat", "ls", "cd", "mkdir", "rm", "cp", "mv"
)

private fun keywordsFor(lang: String) = when (lang.lowercase().trim()) {
    "kotlin", "kt"                             -> KOTLIN_KW
    "python", "py"                             -> PYTHON_KW
    "javascript", "js"                         -> JS_KW
    "typescript", "ts", "tsx"                  -> JS_KW
    "java"                                     -> JAVA_KW
    "shell", "bash", "sh", "zsh"              -> SHELL_KW
    else                                       -> null
}

private fun isHashComment(lang: String)  =
    lang.lowercase() in setOf("python", "py", "shell", "bash", "sh", "zsh", "ruby", "rb", "r", "yaml", "yml", "toml")
private fun isSlashComment(lang: String) =
    lang.lowercase() in setOf("kotlin", "kt", "java", "javascript", "js", "typescript", "ts", "tsx", "cpp", "c", "go", "rust", "rs", "swift", "css", "c#", "cs")

// ── Syntax highlighter ────────────────────────────────────────────────────────
private fun syntaxHighlight(lang: String, code: String): AnnotatedString {
    if (code.length > MAX_HIGHLIGHT_LENGTH) return AnnotatedString(code)
    val keywords = keywordsFor(lang)
    val hashComment  = isHashComment(lang)
    val slashComment = isSlashComment(lang)

    return buildAnnotatedString {
        var i = 0
        val n = code.length
        while (i < n) {
            val ch = code[i]

            // Block comment /* ... */
            if (slashComment && i + 1 < n && ch == '/' && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                withStyle(SpanStyle(color = HL_COMMENT)) { append(code, i, end) }
                i = end; continue
            }
            // Line comment //
            if (slashComment && i + 1 < n && ch == '/' && code[i + 1] == '/') {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                withStyle(SpanStyle(color = HL_COMMENT)) { append(code, i, end) }
                i = end; continue
            }
            // Line comment #
            if (hashComment && ch == '#') {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                withStyle(SpanStyle(color = HL_COMMENT)) { append(code, i, end) }
                i = end; continue
            }
            // Annotation / decorator  @Xyz
            if (ch == '@' && i + 1 < n && code[i + 1].isLetter()) {
                var end = i + 1
                while (end < n && (code[end].isLetterOrDigit() || code[end] == '_')) end++
                withStyle(SpanStyle(color = HL_ANNOT)) { append(code, i, end) }
                i = end; continue
            }
            // Triple-quoted string  """ ... """
            if (i + 2 < n && ch == '"' && code[i + 1] == '"' && code[i + 2] == '"') {
                val end = code.indexOf("\"\"\"", i + 3).let { if (it < 0) n else it + 3 }
                withStyle(SpanStyle(color = HL_STRING)) { append(code, i, end) }
                i = end; continue
            }
            // Double-quoted string
            if (ch == '"') {
                var end = i + 1
                while (end < n && code[end] != '"' && code[end] != '\n') {
                    if (code[end] == '\\') end++
                    end++
                }
                if (end < n && code[end] == '"') end++
                withStyle(SpanStyle(color = HL_STRING)) { append(code, i, end) }
                i = end; continue
            }
            // Single-quoted string
            if (ch == '\'') {
                var end = i + 1
                while (end < n && code[end] != '\'' && code[end] != '\n') {
                    if (code[end] == '\\') end++
                    end++
                }
                if (end < n && code[end] == '\'') end++
                withStyle(SpanStyle(color = HL_STRING)) { append(code, i, end) }
                i = end; continue
            }
            // Word: keyword, type (Capital), or plain identifier
            if (ch.isLetter() || ch == '_') {
                var end = i
                while (end < n && (code[end].isLetterOrDigit() || code[end] == '_')) end++
                val word = code.substring(i, end)
                when {
                    keywords != null && word in keywords ->
                        withStyle(SpanStyle(color = HL_KEYWORD, fontWeight = FontWeight.SemiBold)) { append(word) }
                    word.isNotEmpty() && word[0].isUpperCase() ->
                        withStyle(SpanStyle(color = HL_TYPE)) { append(word) }
                    else ->
                        withStyle(SpanStyle(color = HL_DEFAULT)) { append(word) }
                }
                i = end; continue
            }
            // Number
            if (ch.isDigit()) {
                var end = i
                while (end < n && (code[end].isDigit() || code[end] == '.' ||
                            code[end] == 'L' || code[end] == 'f' || code[end] == 'x' ||
                            code[end] in 'a'..'f' || code[end] in 'A'..'F')) end++
                withStyle(SpanStyle(color = HL_NUMBER)) { append(code, i, end) }
                i = end; continue
            }
            // Default
            withStyle(SpanStyle(color = HL_DEFAULT)) { append(ch) }
            i++
        }
    }
}

// ── Block model ───────────────────────────────────────────────────────────────
private sealed class MdBlock {
    data class Heading(val level: Int, val content: String) : MdBlock()
    data class Code(val lang: String, val code: String, val isOpen: Boolean = false) : MdBlock()
    data class Paragraph(val content: String) : MdBlock()
    data class BulletList(val items: List<String>) : MdBlock()
    data class NumberedList(val items: List<String>) : MdBlock()
    data class Quote(val content: String) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
    data object Divider : MdBlock()
}

// ── Parser ────────────────────────────────────────────────────────────────────
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
                var closed = false
                while (i < lines.size) {
                    if (lines[i].trim().startsWith("```")) { closed = true; i++; break }
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MdBlock.Code(lang, codeLines.joinToString("\n"), isOpen = !closed))
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

            trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().let { it.startsWith("|") && it.endsWith("|") }) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                if (tableLines.size >= 2) {
                    val headerCells = tableLines[0].split("|").drop(1).dropLast(1).map { it.trim() }
                    val startRow = if (tableLines.size >= 2 &&
                        tableLines[1].replace("|", "").replace("-", "").replace(":", "").isBlank()) 2 else 1
                    val dataRows = tableLines.drop(startRow).map { row ->
                        row.split("|").drop(1).dropLast(1).map { it.trim() }
                    }
                    blocks.add(MdBlock.Table(headerCells, dataRows))
                } else {
                    blocks.add(MdBlock.Paragraph(tableLines.joinToString("\n")))
                }
            }

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

// ── Inline annotated string (bold, italic, inline-code, link) ─────────────────
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
                        append("\u2006"); append(text, pos + 1, end); append("\u2006")
                    }
                    pos = end + 1
                } else { append(ch); pos++ }
            }
            ch == '*' && pos + 2 < len && text[pos + 1] == '*' && text[pos + 2] == '*' -> {
                val end = text.indexOf("***", pos + 3)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { append(text, pos + 3, end) }
                    pos = end + 3
                } else { append(ch); pos++ }
            }
            ch == '*' && pos + 1 < len && text[pos + 1] == '*' -> {
                val end = text.indexOf("**", pos + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text, pos + 2, end) }
                    pos = end + 2
                } else { append(ch); pos++ }
            }
            ch == '*' -> {
                val end = findSingleMarkerEnd(text, pos + 1, '*')
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text, pos + 1, end) }
                    pos = end + 1
                } else { append(ch); pos++ }
            }
            ch == '~' && pos + 1 < len && text[pos + 1] == '~' -> {
                val end = text.indexOf("~~", pos + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(text, pos + 2, end) }
                    pos = end + 2
                } else { append(ch); pos++ }
            }
            ch == '[' -> {
                val cb = text.indexOf(']', pos + 1)
                if (cb != -1 && cb + 1 < len && text[cb + 1] == '(') {
                    val cp = text.indexOf(')', cb + 2)
                    if (cp != -1) {
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(text, pos + 1, cb) }
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
        if (text[i] == marker && (i + 1 >= len || text[i + 1] != marker) && i > start) return i
        i++
    }
    return -1
}

// ── Blinking cursor ───────────────────────────────────────────────────────────
@Composable
private fun BlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Text(
        text = "▋",
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        color = HL_KEYWORD.copy(alpha = alpha)
    )
}

// ── Syntax code block ─────────────────────────────────────────────────────────
@Composable
private fun SyntaxCodeBlock(
    block: MdBlock.Code,
    showCursor: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val highlighted = remember(block.lang, block.code) {
        syntaxHighlight(block.lang, block.code)
    }
    val langLabel = block.lang.ifBlank { "code" }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CODE_BG,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // ── Header bar ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CODE_HDR_BG)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = langLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF858585),
                    fontFamily = FontFamily.Monospace
                )
                if (showCursor) {
                    Text(
                        text = "生成中…",
                        style = MaterialTheme.typography.labelSmall,
                        color = HL_KEYWORD.copy(alpha = 0.8f)
                    )
                } else {
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(block.code)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy, null,
                            modifier = Modifier.size(13.dp),
                            tint = Color(0xFF858585)
                        )
                    }
                }
            }
            // ── Code body ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = highlighted,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                )
                if (showCursor) {
                    BlinkingCursor()
                }
            }
        }
    }
}

// ── Public composable ─────────────────────────────────────────────────────────
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isStreaming: Boolean = false
) {
    val stableKey = remember(markdown) {
        if (markdown.length < 500) markdown
        else "${markdown.length}_${markdown.hashCode()}"
    }

    val blocks by produceState(initialValue = emptyList<MdBlock>(), key1 = stableKey) {
        value = withContext(Dispatchers.Default) { parseBlocks(markdown) }
    }

    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val linkColor      = MaterialTheme.colorScheme.primary
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (blocks.isEmpty() && markdown.isNotBlank()) {
            Text(text = markdown.take(800), style = style, color = color.copy(alpha = 0.9f))
            return@Column
        }

        blocks.forEachIndexed { index, block ->
            val isLastBlock = index == blocks.lastIndex
            when (block) {
                is MdBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1    -> style.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        2    -> style.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        3    -> style.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        else -> style.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = buildInlineAnnotated(block.content, codeBackground, linkColor),
                        style = headingStyle, color = color,
                        modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 2.dp)
                    )
                }

                is MdBlock.Code -> {
                    SyntaxCodeBlock(
                        block = block,
                        showCursor = isStreaming && block.isOpen && isLastBlock
                    )
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = buildInlineAnnotated(block.content, codeBackground, linkColor),
                        style = style, color = color
                    )
                }

                is MdBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        block.items.forEach { item ->
                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                Text("•", style = style, color = color, modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    text = buildInlineAnnotated(item, codeBackground, linkColor),
                                    style = style, color = color, modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MdBlock.NumberedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        block.items.forEachIndexed { idx, item ->
                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                Text("${idx + 1}.", style = style, color = color, modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    text = buildInlineAnnotated(item, codeBackground, linkColor),
                                    style = style, color = color, modifier = Modifier.weight(1f)
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

                is MdBlock.Table -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                block.headers.forEach { header ->
                                    Text(
                                        text = header,
                                        style = style.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                        color = color,
                                        modifier = Modifier.widthIn(min = 60.dp).padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = color.copy(alpha = 0.15f), thickness = 1.dp)
                            block.rows.forEachIndexed { idx, row ->
                                val rowBg = if (idx % 2 == 1) codeBackground.copy(alpha = 0.3f) else Color.Transparent
                                Row(modifier = Modifier.fillMaxWidth().then(
                                    if (rowBg != Color.Transparent) Modifier.background(rowBg) else Modifier
                                )) {
                                    row.forEach { cell ->
                                        Text(
                                            text = cell,
                                            style = style.copy(fontSize = 13.sp),
                                            color = color.copy(alpha = 0.9f),
                                            modifier = Modifier.widthIn(min = 60.dp).padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
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
