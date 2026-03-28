package com.orizon.openkiwi.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

enum class ArtifactType {
    HTML, SVG, MARKDOWN, CODE
}

data class ArtifactContent(
    val type: ArtifactType,
    val title: String = "",
    val content: String,
    val language: String = ""
)

@Suppress("DEPRECATION")
@Composable
fun ArtifactPanel(
    artifact: ArtifactContent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMinimized by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when (artifact.type) {
                        ArtifactType.HTML -> Icons.Default.Language
                        ArtifactType.SVG -> Icons.Default.Image
                        ArtifactType.MARKDOWN -> Icons.Default.Description
                        ArtifactType.CODE -> Icons.Default.Code
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    artifact.title.ifBlank {
                        when (artifact.type) {
                            ArtifactType.HTML -> "HTML Preview"
                            ArtifactType.SVG -> "SVG Preview"
                            ArtifactType.MARKDOWN -> "Document"
                            ArtifactType.CODE -> artifact.language.ifBlank { "Code" }
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(artifact.content)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
                IconButton(
                    onClick = { isMinimized = !isMinimized },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (isMinimized) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }

            AnimatedVisibility(
                visible = !isMinimized,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                when (artifact.type) {
                    ArtifactType.HTML, ArtifactType.SVG -> {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    webViewClient = WebViewClient()
                                    settings.javaScriptEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                }
                            },
                            update = { webView ->
                                val html = if (artifact.type == ArtifactType.SVG) {
                                    "<html><body style='margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;background:#fff'>${artifact.content}</body></html>"
                                } else {
                                    artifact.content
                                }
                                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp)
                        )
                    }
                    ArtifactType.MARKDOWN -> {
                        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                            MarkdownText(
                                markdown = artifact.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    ArtifactType.CODE -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (artifact.language.isNotBlank()) {
                                Text(
                                    artifact.language,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                                )
                            }
                            val lineNumbers = artifact.content.lines().size
                            Row(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).horizontalScroll(rememberScrollState())) {
                                Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp).verticalScroll(rememberScrollState())) {
                                    for (i in 1..lineNumbers) {
                                        Text(
                                            "$i",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 18.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                            modifier = Modifier.widthIn(min = 28.dp).padding(end = 8.dp)
                                        )
                                    }
                                }
                                Text(
                                    artifact.content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp).verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun detectArtifactFromCodeBlock(lang: String, code: String): ArtifactContent? {
    val lowerLang = lang.lowercase()
    return when {
        lowerLang == "html" && code.length > 100 -> ArtifactContent(ArtifactType.HTML, "HTML", code)
        lowerLang == "svg" || (code.trimStart().startsWith("<svg")) -> ArtifactContent(ArtifactType.SVG, "SVG", code)
        lowerLang == "markdown" || lowerLang == "md" -> ArtifactContent(ArtifactType.MARKDOWN, "Document", code)
        code.lines().size > 10 -> ArtifactContent(ArtifactType.CODE, "", code, lang)
        else -> null
    }
}
