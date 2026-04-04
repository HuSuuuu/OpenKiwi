package com.orizon.openkiwi.ui.canvas

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.orizon.openkiwi.core.canvas.CanvasActionBus
import com.orizon.openkiwi.core.canvas.CanvasBus
import com.orizon.openkiwi.core.canvas.CanvasEvent
import com.orizon.openkiwi.core.canvas.CanvasSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val emptyCanvasHtml = """
<!DOCTYPE html>
<html><head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"/>
<style>
*{box-sizing:border-box}
body{margin:0;background:#121212;color:#e0e0e0;font-family:system-ui,-apple-system,sans-serif;
     display:flex;align-items:center;justify-content:center;min-height:100vh;padding:24px}
.hint{text-align:center;max-width:320px}
.hint h2{font-size:20px;font-weight:500;margin:0 0 12px;opacity:.7}
.hint p{font-size:14px;line-height:1.6;opacity:.45;margin:0}
.hint code{background:#ffffff12;padding:2px 6px;border-radius:4px;font-size:12px}
</style>
</head><body>
<div class="hint">
<h2>Canvas 画布</h2>
<p>等待 AI 通过 <code>canvas</code> 工具推送内容…</p>
<p style="margin-top:8px">你可以让 AI 生成表格、图表、仪表盘或交互式页面。</p>
</div>
</body></html>
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var bottomDraft by remember { mutableStateOf("") }
    var hasContent by remember { mutableStateOf(CanvasSnapshot.lastHtml != null) }
    var sentMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        CanvasBus.events.collect { ev ->
            val wv = webViewRef ?: return@collect
            withContext(Dispatchers.Main) {
                when (ev) {
                    is CanvasEvent.PushHtml -> {
                        CanvasSnapshot.lastHtml = ev.html
                        hasContent = true
                        wv.loadDataWithBaseURL(null, ev.html, "text/html", "UTF-8", null)
                    }
                    is CanvasEvent.EvalJs -> {
                        wv.evaluateJavascript(ev.js, null)
                    }
                    is CanvasEvent.Clear -> {
                        CanvasSnapshot.lastHtml = null
                        hasContent = false
                        wv.loadDataWithBaseURL(null, emptyCanvasHtml, "text/html", "UTF-8", null)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("画布") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            CanvasSnapshot.lastHtml = null
                            hasContent = false
                            webViewRef?.loadDataWithBaseURL(null, emptyCanvasHtml, "text/html", "UTF-8", null)
                        },
                        enabled = hasContent
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "清空")
                    }
                    IconButton(onClick = {
                        val html = CanvasSnapshot.lastHtml ?: emptyCanvasHtml
                        webViewRef?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重载")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = sentMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    sentMessage?.let { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "已发送: $msg",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
                Surface(tonalElevation = 2.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = bottomDraft,
                            onValueChange = { bottomDraft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("发消息给 AI…") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                        FilledIconButton(
                            onClick = {
                                val t = bottomDraft.trim()
                                if (t.isNotEmpty()) {
                                    CanvasActionBus.emitPrompt("[画布] $t")
                                    sentMessage = t
                                    bottomDraft = ""
                                }
                            },
                            enabled = bottomDraft.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                        }
                    }
                }
            }
        }
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun openKiwiAction(json: String) {
                            CanvasActionBus.emitPrompt("[画布交互] $json")
                        }
                    }, "OpenKiwiBridge")
                    val html = CanvasSnapshot.lastHtml ?: emptyCanvasHtml
                    hasContent = CanvasSnapshot.lastHtml != null
                    loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }

    LaunchedEffect(sentMessage) {
        if (sentMessage != null) {
            kotlinx.coroutines.delay(3000)
            sentMessage = null
        }
    }
}
