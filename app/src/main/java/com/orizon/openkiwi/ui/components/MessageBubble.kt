package com.orizon.openkiwi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
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
import androidx.compose.foundation.BorderStroke

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
    val status: String
)

@Composable
fun MessageBubble(
    message: MessageUiModel,
    onRetry: ((Long) -> Unit)? = null,
    onBranch: ((Long) -> Unit)? = null,
    onEditAsDraft: ((Long) -> Unit)? = null,
    onOpenArtifact: ((ArtifactUiModel) -> Unit)? = null,
    onShareArtifact: ((ArtifactUiModel) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "USER"
    val clipboardManager = LocalClipboardManager.current
    val parsed = remember(message.content) { parseMessageContent(message.content) }
    val thinking = message.thinking.ifBlank { parsed.thinking }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                color = com.orizon.openkiwi.ui.theme.LuminaGlassUser,
                border = BorderStroke(1.dp, com.orizon.openkiwi.ui.theme.LuminaGlassBorderHighlight),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Text(
                    text = parsed.textContent,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                color = com.orizon.openkiwi.ui.theme.LuminaGlassLight,
                border = BorderStroke(1.dp, com.orizon.openkiwi.ui.theme.LuminaGlassBorder),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
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
                    ToolCallChip(action)
                    Spacer(Modifier.height(3.dp))
                }

                if (parsed.textContent.isNotBlank()) {
                    MarkdownText(
                        markdown = parsed.textContent,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
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
}

@Composable
private fun ArtifactChip(
    artifact: ArtifactUiModel,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    artifact.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                val detail = buildString {
                    artifact.mimeType?.takeIf { it.isNotBlank() }?.let { append(it) }
                    artifact.sizeBytes?.let {
                        if (isNotBlank()) append(" · ")
                        append("${it / 1024}KB")
                    }
                }
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
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
    }
}

@Composable
fun ThinkingSection(
    thinking: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember(isStreaming) { mutableStateOf(isStreaming) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
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
}

@Composable
fun ToolCallChip(action: ToolAction) {
    val isSuccess = action.status == "success"
    val isFailed = action.status == "failed"
    val isRunning = action.status == "running"

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            isSuccess -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            isFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                isRunning -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                isSuccess -> Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                isFailed -> Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                else -> Icon(Icons.Default.Code, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                action.toolName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

data class ParsedMessage(
    val textContent: String,
    val toolActions: List<ToolAction>,
    val thinking: String = ""
)

private val thinkRegex = Regex("<think>([\\s\\S]*?)</think>")

fun parseMessageContent(content: String): ParsedMessage {
    val content = com.orizon.openkiwi.util.VendorResponseSanitizer.stripPseudoFunctionCallBlocks(content)
    val thinkMatch = thinkRegex.find(content)
    val thinking = thinkMatch?.groupValues?.get(1)?.trim() ?: ""
    val contentWithoutThink = if (thinkMatch != null) content.replace(thinkRegex, "").trim() else content

    val toolActions = mutableListOf<ToolAction>()
    val textParts = StringBuilder()

    val lines = contentWithoutThink.split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        val callingMatch = Regex("""\[Calling tool: (.+)]""").find(line)
        val resultMatch = Regex("""\[Tool result: (.+)]""").find(line)

        when {
            callingMatch != null -> {
                val toolName = callingMatch.groupValues[1]
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    val nextResult = Regex("""\[Tool result: (.+)]""").find(nextLine)
                    if (nextResult != null) {
                        toolActions.add(ToolAction(toolName, nextResult.groupValues[1]))
                        i += 2
                        continue
                    }
                }
                toolActions.add(ToolAction(toolName, "running"))
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

    return ParsedMessage(
        textContent = textParts.toString().trim(),
        toolActions = toolActions,
        thinking = thinking
    )
}
