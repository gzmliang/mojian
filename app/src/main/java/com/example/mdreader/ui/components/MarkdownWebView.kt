package com.example.mdreader.ui.components

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mdreader.DebugLog

/**
 * WebView 组件：加载本地 HTML 模板，注入 Markdown 内容进行渲染。
 *
 * ## 注入策略
 * - 用 loadUrl 加载 HTML 模板（保持 file:// origin，CDN 资源可加载）
 * - onPageFinished 时通过 evaluateJavascript 注入 base64 内容
 * - base64 仅含 A-Za-z0-9+/=，无 JS 字符串转义风险
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownWebView(
    markdownContent: String,
    modifier: Modifier = Modifier,
    onWebViewReady: ((WebView) -> Unit)? = null,
    onSelectionChanged: ((String) -> Unit)? = null,
) {
    var lastInjected by remember { mutableStateOf("") }
    val selectionCallback by rememberUpdatedState(onSelectionChanged)
    val context = LocalContext.current

    // 记住当前要注入的内容（Compose 重组时更新）
    val currentContent by rememberUpdatedState(markdownContent)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            DebugLog.i("WebView", "创建 WebView 实例")
            WebView(ctx).apply {
                // ---- JS Bridge ----
                @SuppressLint("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true

                // ---- 手势缩放 ----
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportZoom(true)
                settings.defaultFontSize = 16

                // ---- 安全 ----
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false

                // ---- JS → Kotlin 桥接 ----
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onSelectionChanged(infoJson: String) {
                        selectionCallback?.invoke(infoJson)
                    }
                }, "Android")

                // ---- JS 控制台 → DebugLog ----
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                        val level = when (msg.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR -> "E"
                            ConsoleMessage.MessageLevel.WARNING -> "W"
                            else -> "I"
                        }
                        DebugLog.i("WebView.JS", "[$level] ${msg.message()}")
                        return true
                    }
                }

                // ---- 页面加载完成后注入 base64 内容 ----
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        DebugLog.i("WebView", "HTML 模板加载完成")
                        injectContentBase64(view, currentContent)
                    }
                }

                // ---- 通知外部 ----
                onWebViewReady?.invoke(this)

                DebugLog.i("WebView", "加载 HTML 模板")
                loadUrl("file:///android_asset/www/index.html")
            }
        },
        update = { webView ->
            if (markdownContent.isNotEmpty() && markdownContent != lastInjected) {
                injectContentBase64(webView, markdownContent)
                lastInjected = markdownContent
            }
        },
    )
}

/**
 * 将 Markdown 内容 base64 编码后注入 WebView。
 * base64 仅含 A-Za-z0-9+/=，在 JS 单引号字符串中完全安全。
 */
private fun injectContentBase64(webView: WebView?, content: String) {
    if (webView == null || content.isEmpty()) return

    val encoded = Base64.encodeToString(
        content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP
    )
    DebugLog.i("WebView", "注入 Markdown: ${content.length} 字符 → base64: ${encoded.length}")
    webView.evaluateJavascript(
        "if (typeof renderMarkdownBase64 === 'function') { renderMarkdownBase64('$encoded'); }",
        null,
    )
}
