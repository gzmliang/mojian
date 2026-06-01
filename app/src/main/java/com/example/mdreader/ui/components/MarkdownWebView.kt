package com.example.mdreader.ui.components

import android.annotation.SuppressLint
import android.util.Base64
import android.view.MotionEvent
import android.view.ViewGroup
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
import org.json.JSONTokener

/**
 * WebView 组件：阅读 + 编辑双模式。
 *
 * ## 注入策略
 * - loadUrl 加载 HTML 模板（file:// origin，CDN 可加载）
 * - onPageFinished 时注入 base64 内容
 * - 编辑模式通过 JS bridge 切换，CM6 按需加载
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownWebView(
    markdownContent: String,
    modifier: Modifier = Modifier,
    isHtmlFile: Boolean = false,
    onWebViewReady: ((WebView) -> Unit)? = null,
    onSelectionChanged: ((String) -> Unit)? = null,
    onEditModeChanged: ((Boolean) -> Unit)? = null,
    onSaveContent: ((String) -> Unit)? = null,
) {
    var lastInjected by remember { mutableStateOf("") }
    var pageLoaded by remember { mutableStateOf(false) }
    val selectionCallback by rememberUpdatedState(onSelectionChanged)
    val editModeCallback by rememberUpdatedState(onEditModeChanged)
    val saveCallback by rememberUpdatedState(onSaveContent)
    val context = LocalContext.current

    val currentContent by rememberUpdatedState(markdownContent)
    val currentIsHtml by rememberUpdatedState(isHtmlFile)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            DebugLog.i("WebView", "创建 WebView 实例")
            WebView(ctx).apply {
                @SuppressLint("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportZoom(true)
                settings.defaultFontSize = 16
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onSelectionChanged(infoJson: String) {
                        selectionCallback?.invoke(infoJson)
                    }
                }, "Android")

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

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        DebugLog.i("WebView", "HTML 模板加载完成")
                        pageLoaded = true
                        if (currentIsHtml) {
                            injectHtmlContent(view, currentContent)
                        } else {
                            injectContentBase64(view, currentContent)
                        }
                        lastInjected = currentContent  // 标记已注入，防止 update 重复注入
                    }
                }

                // 阻止父控件拦截触摸事件，让 WebView 获得完整滑动手势
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        // 禁止 Compose 父容器拦截触摸 — fling 动量直接给 WebView
                        (parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                    }
                    false  // 不消费事件，交给 WebView 自己的处理
                }
                // 确保超滚动（overscroll）特效启用，提升手感反馈
                overScrollMode = WebView.OVER_SCROLL_ALWAYS

                onWebViewReady?.invoke(this)

                DebugLog.i("WebView", "加载 HTML 模板")
                loadUrl("file:///android_asset/www/index.html")
            }
        },
        update = { webView ->
            // 仅在页面已加载完成后才注入（避免 onPageFinished 前的竞态）
            if (pageLoaded && markdownContent.isNotEmpty() && markdownContent != lastInjected) {
                if (isHtmlFile) {
                    injectHtmlContent(webView, markdownContent)
                } else {
                    injectContentBase64(webView, markdownContent)
                }
                lastInjected = markdownContent
            }
        },
    )
}

// ========== 公开方法 ==========

/** 切换到编辑模式 */
fun switchToEdit(webView: WebView?) {
    webView?.evaluateJavascript("switchToEdit()", null)
    DebugLog.i("WebView", "切换到编辑模式")
}

/** 切换到阅读模式（保存编辑内容） */
fun switchToRead(webView: WebView?, onContent: (String) -> Unit) {
    webView?.evaluateJavascript("switchToRead(true); getEditorContent()") { raw ->
        val content = decodeJsResult(raw)
        DebugLog.i("WebView", "切换到阅读模式，内容: ${content.length} 字符")
        onContent(content)
    }
}

/** 获取编辑器内容 */
fun getEditorContent(webView: WebView?, onContent: (String) -> Unit) {
    webView?.evaluateJavascript("getEditorContent()") { raw ->
        val content = decodeJsResult(raw)
        onContent(content)
    }
}

/** 获取渲染后的 HTML（用于导出） */
fun getRenderedHtml(webView: WebView?, onHtml: (String) -> Unit) {
    webView?.evaluateJavascript("document.getElementById('content').innerHTML") { raw ->
        val html = decodeJsResult(raw)
        onHtml(html)
    }
}

/** 正确解码 evaluateJavascript 返回的 JSON 字符串，保留 \\n \\t 等转义 */
private fun decodeJsResult(raw: String?): String {
    if (raw == null || raw == "null" || raw.length < 2) return ""
    return try {
        JSONTokener(raw).nextValue().toString()
    } catch (e: Exception) {
        DebugLog.e("WebView", "JSON 解码失败: ${e.message}, raw前80字: ${raw.take(80)}")
        raw.removeSurrounding("\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}

// ========== 私有方法 ==========

private fun injectContentBase64(webView: WebView?, content: String) {
    if (webView == null || content.isEmpty()) return
    val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    DebugLog.i("WebView", "注入 Markdown: ${content.length} 字符")
    webView.evaluateJavascript(
        "if (typeof renderMarkdownBase64 === 'function') { renderMarkdownBase64('$encoded'); }",
        null,
    )
}

private fun injectHtmlContent(webView: WebView?, content: String) {
    if (webView == null || content.isEmpty()) return
    val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    DebugLog.i("WebView", "注入 HTML: ${content.length} 字符")
    webView.evaluateJavascript(
        "if (typeof renderMarkdownBase64 === 'function') {" +
        "  var binary=atob('$encoded');" +
        "  var parts=[];" +
        "  for(var i=0;i<binary.length;i++){" +
        "    var h=binary.charCodeAt(i).toString(16);" +
        "    parts.push('%'+(h.length===1?'0':'')+h);" +
        "  }" +
        "  renderHtmlContent(decodeURIComponent(parts.join('')));" +
        "}",
        null,
    )
}
