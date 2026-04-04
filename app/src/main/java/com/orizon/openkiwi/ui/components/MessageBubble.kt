package com.orizon.openkiwi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.orizon.openkiwi.ui.theme.PaperGreen
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import com.orizon.openkiwi.ui.chat.ToolDisplayRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val parseMessageJson = Json { ignoreUnknownKeys = true; isLenient = true }

data class MessageUiModel(
    val id: Long = 0,
    val role: String,
    val content: String,
    val thinking: String = "",
    val isStreaming: Boolean = false,
    val turnId: Long = 0,
    val artifacts: List<ArtifactUiModel> = emptyList()
)

data class ArtifactUiModel(
    val id: Long = 0,
    val path: String,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null
)

data class ToolAction(
    val toolName: String,
    val status: String,
    val args: JsonObject? = null
)

@Composable
fun MessageBubble(
    message: MessageUiModel,
    onRetry: ((Long) -> Unit)? = null,
    onBranch: ((Long) -> Unit)? = null,
    onEditAsDraft: ((Long) -> Unit)? = null,
    onOpenArtifact: ((ArtifactUiModel) -> Unit)? = null,
    onShareArtifact: ((ArtifactUiModel) -> Unit)? = null,
    onOpenCanvas: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "USER"
    val clipboardManager = LocalClipboardManager.current
    val parsed = remember(message.content) { parseMessageContent(message.content) }
    val thinking = message.thinking.ifBlank { parsed.thinking }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Column(modifier = Modifier.widthIn(max = 300.dp)) {
                if (parsed.fileAttachments.isNotEmpty()) {
                    parsed.fileAttachments.forEach { file ->
                        FileAttachmentCard(
                            file = file,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        )
                    }
                }
                if (parsed.textContent.isNotBlank()) {
                    Row {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .heightIn(min = 20.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    RoundedCornerShape(2.dp)
                                )
                                .align(Alignment.CenterVertically)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = parsed.textContent,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .animateContentSize()
            ) {
                if (thinking.isNotBlank()) {
                    ThinkingSection(
                        thinking = thinking,
                        isStreaming = message.isStreaming
                    )
                    Spacer(Modifier.height(6.dp))
                }

                parsed.toolActions.forEach { action ->
                    ToolCallCard(
                        action = action,
                        onOpenCanvas = onOpenCanvas
                    )
                    Spacer(Modifier.height(6.dp))
                }

                parsed.codeResults.forEach { result ->
                    CodeResultCard(result = result, modifier = Modifier.padding(vertical = 4.dp))
                }

                if (parsed.streamChunks.isNotEmpty() && parsed.codeResults.isEmpty()) {
                    parsed.streamChunks.forEach { (tool, content) ->
                        ShellStreamCard(toolName = tool, content = content, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                if (parsed.textContent.isNotBlank()) {
                    MarkdownText(
                        markdown = parsed.textContent,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        isStreaming = message.isStreaming,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                if (!message.isStreaming) {
                    val artifacts = remember(parsed.textContent) { detectArtifacts(parsed.textContent) }
                    artifacts.forEach { artifact ->
                        var showPanel by remember { mutableStateOf(true) }
                        if (showPanel) {
                            ArtifactPanel(
                                artifact = artifact,
                                onDismiss = { showPanel = false },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                if (parsed.textContent.isNotBlank() && !message.isStreaming) {
                    Row(modifier = Modifier.padding(top = 2.dp)) {
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(parsed.textContent)) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Outlined.ContentCopy, null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        }
                        if (onRetry != null) {
                            IconButton(
                                onClick = { onRetry(message.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh, null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                )
                            }
                        }
                        if (onBranch != null) {
                            IconButton(
                                onClick = { onBranch(message.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.AccountTree, null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                )
                            }
                        }
                        if (onEditAsDraft != null) {
                            IconButton(
                                onClick = { onEditAsDraft(message.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit, null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                )
                            }
                        }
                    }
                }

                if (message.artifacts.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    message.artifacts.forEach { artifact ->
                        ArtifactChip(
                            artifact = artifact,
                            onOpen = { onOpenArtifact?.invoke(artifact) },
                            onShare = { onShareArtifact?.invoke(artifact) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactChip(
    artifact: ArtifactUiModel,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val ext = artifact.displayName.substringAfterLast('.', "").lowercase()
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    val isAudio = ext in listOf("mp3", "m4a", "wav", "ogg", "aac", "flac")
    val isText = ext in listOf("txt", "log", "json", "xml", "csv", "md", "kt", "java", "py", "js", "html", "css")
    var showTextPreview by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isImage) {
                val file = java.io.File(artifact.path)
                if (file.exists()) {
                    val bitmap = remember(artifact.path) {
                        runCatching { android.graphics.BitmapFactory.decodeFile(artifact.path) }.getOrNull()
                    }
                    bitmap?.let { bmp ->
                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = artifact.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clickable { onOpen() },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when {
                    isImage -> Icons.Default.Image
                    isAudio -> Icons.Default.AudioFile
                    else -> Icons.Default.Description
                }
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        artifact.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val detail = buildString {
                        artifact.mimeType?.takeIf { it.isNotBlank() }?.let { append(it) }
                        artifact.sizeBytes?.let {
                            if (isNotBlank()) append(" \u00B7 ")
                            append("${it / 1024}KB")
                        }
                    }
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isText) {
                    IconButton(
                        onClick = { showTextPreview = !showTextPreview },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            if (showTextPreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, modifier = Modifier.size(14.dp)
                        )
                    }
                }
                IconButton(onClick = onOpen, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                }
            }

            if (isText && showTextPreview) {
                val preview = remember(artifact.path) {
                    runCatching {
                        java.io.File(artifact.path).bufferedReader().useLines { lines ->
                            lines.take(20).joinToString("\n")
                        }
                    }.getOrDefault("Unable to read file")
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                ) {
                    Text(
                        preview,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(10.dp).horizontalScroll(rememberScrollState())
                    )
                }
            }

            if (isAudio) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onOpen, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier.weight(1f).height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileAttachmentCard(
    file: FileAttachment,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val ext = file.fileName.substringAfterLast('.', "").lowercase()
    val icon = when (ext) {
        in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg") -> Icons.Outlined.Image
        in listOf("mp3", "m4a", "wav", "ogg", "aac", "flac") -> Icons.Default.MusicNote
        in listOf("mp4", "avi", "mov", "mkv", "webm") -> Icons.Default.Videocam
        in listOf("pdf") -> Icons.Default.Description
        in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Default.Folder
        in listOf("kt", "java", "py", "js", "ts", "c", "cpp", "go", "rs", "rb", "swift") -> Icons.Default.Code
        else -> Icons.Outlined.InsertDriveFile
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (file.preview.isNotBlank()) expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.fileName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    val detail = buildString {
                        if (file.mimeType.isNotBlank() && file.mimeType != "unknown") append(file.mimeType)
                        if (file.size.isNotBlank()) {
                            if (isNotBlank()) append(" · ")
                            append(file.size)
                        }
                    }
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (file.preview.isNotBlank()) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    file.preview,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun ThinkingSection(
    thinking: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember(isStreaming) { mutableStateOf(isStreaming) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isStreaming) "思考中..." else "已深度思考",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = thinking,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ToolCallCard(
    action: ToolAction,
    onOpenCanvas: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val summary = remember(action.toolName, action.args, action.status) {
        ToolDisplayRegistry.resolve(context, action.toolName, action.args)
    }
    var expanded by remember(action.toolName, action.status) { mutableStateOf(false) }
    val isSuccess = action.status == "success"
    val isFailed = action.status == "failed"
    val isRunning = action.status == "running"

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (action.args != null) {
                            Modifier.clickable { expanded = !expanded }
                        } else Modifier
                    )
            ) {
                Text(
                    summary.emoji,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        summary.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    summary.detailLine?.let { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                when {
                    isRunning -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    isSuccess -> Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = PaperGreen)
                    isFailed -> Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    else -> Icon(Icons.Default.Code, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (expanded && action.args != null) {
                Text(
                    action.args.toString(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (action.toolName.equals("canvas", ignoreCase = true) && isSuccess && onOpenCanvas != null) {
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(
                    onClick = onOpenCanvas,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("打开画布", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** @deprecated 使用 [ToolCallCard] */
@Composable
fun ToolCallChip(action: ToolAction) {
    ToolCallCard(action = action, onOpenCanvas = null)
}

data class CodeResult(
    val toolName: String,
    val command: String = "",
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int? = null,
    val executionTime: String = ""
)

data class FileAttachment(
    val fileName: String,
    val mimeType: String,
    val size: String,
    val preview: String = ""
)

data class ParsedMessage(
    val textContent: String,
    val toolActions: List<ToolAction>,
    val thinking: String = "",
    val codeResults: List<CodeResult> = emptyList(),
    val streamChunks: List<Pair<String, String>> = emptyList(),
    val fileAttachments: List<FileAttachment> = emptyList()
)

@Composable
fun CodeResultCard(result: CodeResult, modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    val codeBg = Color(0xFF161616)
    val headerBg = Color(0xFF1E1E1E)
    val mutedText = Color(0xFF999999)
    val codeText = Color(0xFFE0E0E0)
    val cmdColor = Color(0xFF79B8FF)
    val errColor = Color(0xFFE57373)

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = codeBg,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(headerBg).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    result.toolName,
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedText,
                    fontFamily = FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (result.executionTime.isNotBlank()) {
                        Text(
                            result.executionTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedText.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    result.exitCode?.let { code ->
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = if (code == 0) Color(0xFF4CAF7D) else errColor
                        ) {
                            Text(
                                "exit $code",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(result.stdout + result.stderr)) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(12.dp), tint = mutedText)
                    }
                }
            }
            if (result.command.isNotBlank()) {
                Text(
                    "$ ${result.command}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = cmdColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            if (result.stdout.isNotBlank()) {
                Text(
                    result.stdout.take(5000),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = codeText,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            if (result.stderr.isNotBlank()) {
                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                Text(
                    "stderr",
                    style = MaterialTheme.typography.labelSmall,
                    color = errColor,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                )
                Text(
                    result.stderr.take(2000),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = errColor.copy(alpha = 0.8f),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun ShellStreamCard(toolName: String, content: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color(0xFF161616),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                toolName,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                content.takeLast(3000),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Color(0xFFE0E0E0),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

private val thinkRegex = Regex("<think>([\\s\\S]*?)</think>")
private val fileHeaderRegex = Regex("""\[文件: (.+?) \| 类型: (.+?) \| 大小: (.+?)]""")
private val fileBlockRegex = Regex(
    """\[文件: (.+?) \| 类型: (.+?) \| 大小: (.+?)]([\s\S]*?)(?=\[文件: |\z)""",
)
private val unreadableFileRegex = Regex("""\[用户发送了文件: (.+?)，但无法读取内容]""")

private fun extractFileAttachments(text: String): Pair<String, List<FileAttachment>> {
    val attachments = mutableListOf<FileAttachment>()
    var remaining = text

    fileBlockRegex.findAll(text).forEach { match ->
        val name = match.groupValues[1]
        val type = match.groupValues[2]
        val size = match.groupValues[3]
        val body = match.groupValues[4].trim()
        val preview = when {
            body.startsWith("```") -> {
                val inner = body.removePrefix("```").trimStart('\n')
                    .let { if (it.contains("```")) it.substringBefore("```") else it }
                    .trim()
                inner.lines().take(5).joinToString("\n")
            }
            else -> body.take(200)
        }
        attachments.add(FileAttachment(name, type, size, preview))
        remaining = remaining.replace(match.value, "")
    }

    unreadableFileRegex.findAll(text).forEach { match ->
        attachments.add(FileAttachment(match.groupValues[1], "unknown", "", ""))
        remaining = remaining.replace(match.value, "")
    }

    return remaining.trim() to attachments
}

fun parseMessageContent(content: String): ParsedMessage {
    val content = com.orizon.openkiwi.util.VendorResponseSanitizer.stripPseudoFunctionCallBlocks(content)
    val thinkMatch = thinkRegex.find(content)
    val thinking = thinkMatch?.groupValues?.get(1)?.trim() ?: ""
    val contentWithoutThink = if (thinkMatch != null) content.replace(thinkRegex, "").trim() else content

    val (contentAfterFiles, fileAttachments) = extractFileAttachments(contentWithoutThink)

    val toolActions = mutableListOf<ToolAction>()
    val codeResults = mutableListOf<CodeResult>()
    val streamChunks = mutableListOf<Pair<String, String>>()
    val textParts = StringBuilder()

    val streamRegex = Regex("""\[stream:(.+?)](.*)""")

    val lines = contentAfterFiles.split("\n")
    var i = 0
    var currentStreamTool: String? = null
    val currentStreamContent = StringBuilder()

    while (i < lines.size) {
        val line = lines[i].trim()
        val callingMatch = Regex("""\[Calling tool: (.+)]""").find(line)
        val resultMatch = Regex("""\[Tool result: (.+)]""").find(line)
        val streamMatch = streamRegex.find(line)

        when {
            streamMatch != null -> {
                val tool = streamMatch.groupValues[1]
                val chunk = streamMatch.groupValues[2]
                if (currentStreamTool != tool) {
                    if (currentStreamTool != null) {
                        streamChunks.add(currentStreamTool!! to currentStreamContent.toString())
                        buildCodeResultFromStream(currentStreamTool!!, currentStreamContent.toString())?.let { codeResults.add(it) }
                    }
                    currentStreamTool = tool
                    currentStreamContent.clear()
                }
                currentStreamContent.append(chunk)
            }
            callingMatch != null -> {
                val toolName = callingMatch.groupValues[1]
                var nextIdx = i + 1
                var argsJson: JsonObject? = null
                if (nextIdx < lines.size) {
                    val maybeArgs = lines[nextIdx].trim()
                    if (maybeArgs.startsWith("{")) {
                        argsJson = runCatching { parseMessageJson.parseToJsonElement(maybeArgs).jsonObject }.getOrNull()
                        nextIdx++
                    }
                }
                if (nextIdx < lines.size) {
                    val resultLine = lines[nextIdx].trim()
                    val nextResult = Regex("""\[Tool result: (.+)]""").find(resultLine)
                    if (nextResult != null) {
                        toolActions.add(ToolAction(toolName, nextResult.groupValues[1], argsJson))
                        i = nextIdx + 1
                        continue
                    }
                }
                toolActions.add(ToolAction(toolName, "running", argsJson))
                i = nextIdx
                continue
            }
            resultMatch != null -> {
                if (toolActions.isNotEmpty() && toolActions.last().status == "running") {
                    val last = toolActions.removeAt(toolActions.lastIndex)
                    toolActions.add(last.copy(status = resultMatch.groupValues[1]))
                }
            }
            line.isNotBlank() -> {
                if (textParts.isNotEmpty()) textParts.append("\n")
                textParts.append(lines[i])
            }
        }
        i++
    }

    if (currentStreamTool != null) {
        streamChunks.add(currentStreamTool!! to currentStreamContent.toString())
        buildCodeResultFromStream(currentStreamTool!!, currentStreamContent.toString())?.let { codeResults.add(it) }
    }

    return ParsedMessage(
        textContent = textParts.toString().trim(),
        toolActions = toolActions,
        thinking = thinking,
        codeResults = codeResults,
        streamChunks = streamChunks,
        fileAttachments = fileAttachments
    )
}

private fun buildCodeResultFromStream(toolName: String, content: String): CodeResult? {
    if (content.isBlank()) return null
    val lines = content.lines()
    val command = lines.firstOrNull()?.removePrefix("$ ")?.trim() ?: ""
    val stderrIdx = lines.indexOfFirst { it.trim() == "--- stderr ---" }
    val exitLine = lines.lastOrNull { it.contains("exit=") } ?: ""
    val exitMatch = Regex("""exit=(-?\d+)""").find(exitLine)
    val timeMatch = Regex("""time=(\d+)ms""").find(exitLine)

    val stdoutLines = if (stderrIdx >= 0) lines.subList(1, stderrIdx) else lines.drop(1).dropLast(if (exitLine.isNotBlank()) 1 else 0)
    val stderrLines = if (stderrIdx >= 0) lines.subList(stderrIdx + 1, lines.size).dropLast(if (exitLine.isNotBlank()) 1 else 0) else emptyList()

    return CodeResult(
        toolName = toolName,
        command = command,
        stdout = stdoutLines.joinToString("\n"),
        stderr = stderrLines.joinToString("\n"),
        exitCode = exitMatch?.groupValues?.get(1)?.toIntOrNull(),
        executionTime = timeMatch?.let { "${it.groupValues[1]}ms" } ?: ""
    )
}

private val codeBlockRegex = Regex("```(\\w*)\\n([\\s\\S]*?)```")

private fun detectArtifacts(text: String): List<ArtifactContent> {
    return codeBlockRegex.findAll(text).mapNotNull { match ->
        val lang = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        detectArtifactFromCodeBlock(lang, code)
    }.toList()
}
