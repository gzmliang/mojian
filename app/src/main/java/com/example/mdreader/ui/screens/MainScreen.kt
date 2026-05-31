package com.example.mdreader.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mdreader.DebugLog
import com.example.mdreader.data.BookmarkManager
import com.example.mdreader.data.HighlightManager
import com.example.mdreader.data.RecentFilesManager
import com.example.mdreader.tts.TtsEngine
import com.example.mdreader.ui.components.DebugLogPanel
import com.example.mdreader.ui.components.ReadingToolbar
import com.example.mdreader.ui.components.SearchBar
import com.example.mdreader.ui.components.TranslationSettingsDialog
import com.example.mdreader.translate.TranslationService
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val widthSizeClass = calculateWindowSizeClass(context as Activity).widthSizeClass

    // ---- 文件 ----
    var currentFilePath by remember { mutableStateOf<String?>(null) }
    var currentFileName by remember { mutableStateOf("墨笺") }
    var currentMdContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var hasOpenedFile by remember { mutableStateOf(false) }

    // ---- 阅读设置 ----
    var fontSize by remember { mutableStateOf(16) }
    var theme by remember { mutableStateOf("light") }

    // ---- TTS ----
    var isTtsPlaying by remember { mutableStateOf(false) }
    var ttsParagraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var ttsCurrentIdx by remember { mutableStateOf(0) }
    var currentWebView by remember { mutableStateOf<WebView?>(null) }
    val ttsEngine = remember { TtsEngine(context) }

    // ---- 搜索 ----
    var isSearchVisible by remember { mutableStateOf(false) }

    // ---- 书签 ----
    var showBookmarkSheet by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf<List<BookmarkManager.Bookmark>>(emptyList()) }

    // ---- 全屏 ----
    var isFullscreen by remember { mutableStateOf(false) }

    // ---- 选中文字 + 高亮 ----
    var selectedText by remember { mutableStateOf<String?>(null) }
    var showSelectionMenu by remember { mutableStateOf(false) }
    data class SelInfo(val text: String, val paraIdx: Int, val startOffset: Int, val endOffset: Int)
    var selectionInfo by remember { mutableStateOf<SelInfo?>(null) }
    var highlights by remember { mutableStateOf<List<HighlightManager.Highlight>>(emptyList()) }
    var showHighlightPanel by remember { mutableStateOf(false) }
    var existingHighlight by remember { mutableStateOf<HighlightManager.Highlight?>(null) }

    // ---- 翻译 ----
    var showTranslateSettings by remember { mutableStateOf(false) }
    var showTranslateResult by remember { mutableStateOf(false) }
    var translateResult by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }

    // ---- 最近文件 ----
    var recentFiles by remember { mutableStateOf(RecentFilesManager.getRecentFiles(context)) }

    fun refreshBookmarks() {
        currentFilePath?.let { path ->
            bookmarks = BookmarkManager.getBookmarks(context, path)
        }
    }

    fun refreshHighlights() {
        val path = currentFilePath ?: return
        highlights = HighlightManager.getHighlights(context, path)
        if (highlights.isNotEmpty()) {
            val json = HighlightManager.getHighlightsJson(path, highlights)
            currentWebView?.evaluateJavascript("applyAllHighlights('${json.replace("'", "\\'")}')", null)
        }
    }

    // ---- 文件选择器 ----
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        if (isTtsPlaying) { ttsEngine.stop(); isTtsPlaying = false }
        isSearchVisible = false

        GlobalScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { readTextFromUri(context, uri) }
                // 诊断：检查反斜杠是否在文件读取阶段丢失
                val bsCount = content.count { it == '\\' }
                val hasFrac = content.contains("\\frac")
                DebugLog.i("MainScreen", "文件: ${content.length}字, 反斜杠: $bsCount, 含\\frac: $hasFrac")
                val path = uri.toString()
                val name = getDisplayName(context, uri) ?: uri.lastPathSegment ?: "untitled.md"
                currentMdContent = content
                currentFilePath = path
                currentFileName = name
                hasOpenedFile = true
                // 拿持久读取权限，避免重开时 SecurityException
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    DebugLog.e("MainScreen", "无法获取持久权限: ${e.message}")
                }
                RecentFilesManager.addRecentFile(context, path, name)
                recentFiles = RecentFilesManager.getRecentFiles(context)
                refreshBookmarks()
                refreshHighlights()
                DebugLog.i("MainScreen", "文件加载: $name (${content.length}字)")
            } catch (e: Exception) {
                DebugLog.e("MainScreen", "读取失败", e)
            } finally {
                isLoading = false
            }
        }
    }

    // ---- TTS ----
    fun wvHighlight(idx: Int) { currentWebView?.evaluateJavascript("highlightParagraph($idx)", null) }
    fun wvClearHighlight() { currentWebView?.evaluateJavascript("clearHighlight()", null) }

    // 书签跳转：高亮 + 滚动 + 1s 后消退
    fun scrollToBookmark(idx: Int) {
        currentWebView?.evaluateJavascript("highlightAndScroll($idx)", null)
    }

    fun playNext(idx: Int) {
        if (idx >= ttsParagraphs.size) {
            DebugLog.i("MainScreen", "TTS 完毕"); wvClearHighlight(); isTtsPlaying = false; return
        }
        val text = ttsParagraphs[idx]
        if (text.isBlank()) { playNext(idx + 1); return }
        ttsCurrentIdx = idx
        wvHighlight(idx)
        ttsEngine.speak(text, object : TtsEngine.TTSListener {
            override fun onStart(id: String) {}
            override fun onDone(id: String) { playNext(idx + 1) }
            override fun onError(id: String, err: String) {
                if (ttsEngine.recordError()) { wvClearHighlight(); isTtsPlaying = false }
                else playNext(idx + 1)
            }
        })
    }

    fun startTts() {
        val wv = currentWebView ?: return
        wv.evaluateJavascript("getParagraphsText()") { raw ->
            if (raw == null || raw == "null" || raw == "[]") return@evaluateJavascript
            val json = raw.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
            val arr = try { JSONArray(json) } catch (e: Exception) { return@evaluateJavascript }
            val paras = (0 until arr.length()).map { arr.getString(it) }
            if (paras.isEmpty()) return@evaluateJavascript
            ttsParagraphs = paras
            ttsEngine.ensureInitialized { ready ->
                if (!ready) return@ensureInitialized
                isTtsPlaying = true; ttsEngine.resetErrors(); playNext(0)
            }
        }
    }

    fun stopTts() { ttsEngine.stop(); wvClearHighlight(); isTtsPlaying = false }

    fun onTextSelected(infoJson: String) {
        try {
            val obj = org.json.JSONObject(infoJson)
            val text = obj.optString("text", "")
            if (text.isBlank()) return
            selectionInfo = SelInfo(text = text, paraIdx = obj.optInt("paraIdx", -1),
                startOffset = obj.optInt("startOffset", -1), endOffset = obj.optInt("endOffset", -1))
            selectedText = text; showSelectionMenu = true
            // 检查是否选中了已有高亮
            existingHighlight = highlights.find { hl ->
                hl.paraIdx == selectionInfo?.paraIdx &&
                hl.startOffset <= (selectionInfo?.startOffset ?: -1) &&
                hl.endOffset >= (selectionInfo?.endOffset ?: -1)
            }
            DebugLog.i("MainScreen", "选中: ${text.take(40)}..." + if (existingHighlight != null) " (已有高亮)" else "")
        } catch (_: Exception) {}
    }

    fun dismissSelectionMenu() { showSelectionMenu = false; selectedText = null; selectionInfo = null }

    fun addHighlight() {
        val info = selectionInfo ?: return; val path = currentFilePath ?: return
        val hl = HighlightManager.Highlight(id = "hl_${System.currentTimeMillis()}", paraIdx = info.paraIdx,
            startOffset = info.startOffset, endOffset = info.endOffset, text = info.text)
        HighlightManager.addHighlight(context, path, hl)
        highlights = HighlightManager.getHighlights(context, path)
        currentWebView?.evaluateJavascript("applyHighlight(${info.paraIdx}, ${info.startOffset}, ${info.endOffset}, '#FFE082')", null)
        dismissSelectionMenu()
    }

    fun removeHighlight() {
        val info = selectionInfo ?: return; val path = currentFilePath ?: return
        HighlightManager.removeHighlight(context, path, info.startOffset, info.endOffset)
        highlights = HighlightManager.getHighlights(context, path)
        val json = HighlightManager.getHighlightsJson(path, highlights)
        currentWebView?.evaluateJavascript("clearAndReapplyHighlights('${json.replace("'", "\\'")}')", null)
        existingHighlight = null
        dismissSelectionMenu()
        DebugLog.i("MainScreen", "移除高亮: p${info.paraIdx}[${info.startOffset}-${info.endOffset}]")
    }

    fun deleteHighlight(hl: HighlightManager.Highlight) {
        val path = currentFilePath ?: return
        HighlightManager.removeHighlight(context, path, hl.startOffset, hl.endOffset)
        highlights = HighlightManager.getHighlights(context, path)
        val json = HighlightManager.getHighlightsJson(path, highlights)
        currentWebView?.evaluateJavascript("clearAndReapplyHighlights('${json.replace("'", "\\'")}')", null)
        DebugLog.i("MainScreen", "删除高亮: p${hl.paraIdx}[${hl.startOffset}-${hl.endOffset}]")
    }

    fun navigateToHighlight(hl: HighlightManager.Highlight) {
        showHighlightPanel = false
        currentWebView?.evaluateJavascript("highlightAndScroll(${hl.paraIdx})", null)
    }
    fun readSelection(text: String) {
        dismissSelectionMenu()
        ttsEngine.ensureInitialized { ready ->
            if (!ready) return@ensureInitialized
            stopTts(); isTtsPlaying = true
            ttsEngine.speak(text, object : TtsEngine.TTSListener {
                override fun onStart(id: String) {}
                override fun onDone(id: String) { isTtsPlaying = false }
                override fun onError(id: String, err: String) { isTtsPlaying = false }
            })
        }
    }

    fun toggleFullscreen() { isFullscreen = !isFullscreen }

    // ---- 书签 ----
    fun addBookmark() {
        val wv = currentWebView ?: return
        val path = currentFilePath ?: return
        // 用视口中心定位当前段落
        wv.evaluateJavascript("getCurrentParagraphIndex()") { rawIdx ->
            val idx = (rawIdx ?: "0").removeSurrounding("\"").toIntOrNull() ?: 0
            wv.evaluateJavascript("getParagraphAt($idx)") { rawSnippet ->
                val snippet = (rawSnippet ?: "").removeSurrounding("\"").take(80)
                BookmarkManager.addBookmark(context, path, idx, snippet)
                refreshBookmarks()
                DebugLog.i("MainScreen", "书签: p$idx \"$snippet\"")
            }
        }
    }

    // ---- 翻译 ----
    fun execTranslate(text: String) {
        if (!TranslationService.isConfigured(context)) {
            showTranslateSettings = true
            return
        }
        DebugLog.i("MainScreen", "翻译: ${text.take(50)}...")
        isTranslating = true
        GlobalScope.launch {
            val result = TranslationService.translate(context, text)
            isTranslating = false
            result.fold(
                onSuccess = { t -> translateResult = t; showTranslateResult = true },
                onFailure = { e -> translateResult = "翻译失败: ${e.message}"; showTranslateResult = true },
            )
        }
    }

    fun doTranslate(text: String? = null) {
        if (text != null) { execTranslate(text); return }
        val wv = currentWebView ?: return
        wv.evaluateJavascript("(function(){var s=window.getSelection();return s?s.toString():'';})()") { raw ->
            val selected = (raw ?: "").removeSurrounding("\"").trim()
            if (selected.isEmpty() || selected == "null") {
                DebugLog.i("MainScreen", "翻译: 未选中文本")
                return@evaluateJavascript
            }
            execTranslate(selected)
        }
    }

    fun openFile(path: String) {
        isLoading = true
        GlobalScope.launch {
            try {
                val uri = Uri.parse(path)
                // 先尝试拿持久权限（重开最近文件时可能已过期）
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {}
                val content = withContext(Dispatchers.IO) { readTextFromUri(context, uri) }
                currentMdContent = content
                currentFilePath = path
                currentFileName = getDisplayName(context, uri) ?: path.substringAfterLast("/")
                hasOpenedFile = true
                isLoading = false
                refreshBookmarks()
                refreshHighlights()
            } catch (e: SecurityException) {
                DebugLog.e("MainScreen", "权限过期，请重新选择文件")
                hasOpenedFile = false
                isLoading = false
            } catch (e: Exception) {
                DebugLog.e("MainScreen", "打开失败", e)
                hasOpenedFile = false
                isLoading = false
            }
        }
    }

    // ---- UI ----
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!hasOpenedFile) {
                // 欢迎页 — 最近文件
                WelcomeScreen(
                    recentFiles = recentFiles,
                    onOpenFile = { filePicker.launch(arrayOf("text/markdown", "text/plain", "*/*")) },
                    onOpenRecent = { openFile(it.path) },
                )
                DebugLogPanel()
                return@Column
            }

            // 搜索栏
            AnimatedVisibility(visible = isSearchVisible && !isFullscreen) {
                SearchBar(
                    webView = currentWebView,
                    visible = isSearchVisible,
                    onDismiss = { isSearchVisible = false },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (widthSizeClass) {
                WindowWidthSizeClass.Compact -> MobileLayout(
                    filePath = currentFilePath,
                    displayName = currentFileName,
                    markdownContent = currentMdContent,
                        isLoading = isLoading,
                        fontSize = fontSize,
                        onFontSizeChange = { fontSize = it },
                        isTtsPlaying = isTtsPlaying,
                        onTtsPlay = { startTts() },
                        onTtsStop = { stopTts() },
                        onWebViewReady = { wv -> currentWebView = wv },
                        onFileOpenClick = { filePicker.launch(arrayOf("text/markdown", "text/plain", "*/*")) },
                        isSearchVisible = isSearchVisible,
                        onToggleSearch = { isSearchVisible = !isSearchVisible },
                    onAddBookmark = { addBookmark() },
                    onShowBookmarks = { refreshBookmarks(); showBookmarkSheet = true },
                    onShowHighlights = { refreshHighlights(); showHighlightPanel = true },
                    onTranslate = { doTranslate() },
                    onTranslateSettings = { showTranslateSettings = true },
                    currentTheme = theme,
                    onThemeChange = { theme = it },
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { toggleFullscreen() },
                    onSelectionChanged = { onTextSelected(it) },
                )
                else -> TabletLayout(
                    filePath = currentFilePath,
                    displayName = currentFileName,
                    markdownContent = currentMdContent,
                    isLoading = isLoading,
                    fontSize = fontSize,
                    onFontSizeChange = { fontSize = it },
                    isTtsPlaying = isTtsPlaying,
                    onTtsPlay = { startTts() },
                    onTtsStop = { stopTts() },
                    onWebViewReady = { wv -> currentWebView = wv },
                    onFileOpenClick = { filePicker.launch(arrayOf("text/markdown", "text/plain", "*/*")) },
                    isSearchVisible = isSearchVisible,
                    onToggleSearch = { isSearchVisible = !isSearchVisible },
                    onAddBookmark = { addBookmark() },
                    onShowBookmarks = { refreshBookmarks(); showBookmarkSheet = true },
                    onShowHighlights = { refreshHighlights(); showHighlightPanel = true },
                    onTranslate = { doTranslate() },
                    onTranslateSettings = { showTranslateSettings = true },
                    currentTheme = theme,
                    onThemeChange = { theme = it },
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { toggleFullscreen() },
                    onSelectionChanged = { onTextSelected(it) },
                )
                }
            }

            AnimatedVisibility(visible = !isFullscreen) { DebugLogPanel() }
        }

        // ---- 全屏退出浮动按钮 ----
        AnimatedVisibility(
            visible = isFullscreen && hasOpenedFile,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        ) {
            FloatingActionButton(
                onClick = { toggleFullscreen() },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Default.Close, "退出全屏", modifier = Modifier.size(20.dp))
            }
        }

        // ---- 选中文字菜单（overlay 浮动） ----
        AnimatedVisibility(
            visible = showSelectionMenu && selectedText != null && hasOpenedFile,
            modifier = Modifier.align(Alignment.BottomCenter).padding(
                bottom = if (isFullscreen) 72.dp else 56.dp,
                start = 8.dp, end = 8.dp,
            ),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (existingHighlight != null) {
                        TextButton(onClick = { removeHighlight() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("移除高亮", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(onClick = { addHighlight() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Icon(Icons.Default.Edit, null, Modifier.size(14.dp), tint = Color(0xFFE6A800))
                            Spacer(Modifier.width(4.dp))
                            Text("高亮标记", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { val t = selectedText; dismissSelectionMenu(); readSelection(t!!) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp), tint = Color(0xFF059669))
                        Spacer(Modifier.width(4.dp))
                        Text("朗读选中", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { val t = selectedText; dismissSelectionMenu(); doTranslate(t) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Icon(Icons.Default.Translate, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("翻译选中", fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        selectedText?.let { clipboardManager.setText(AnnotatedString(it)) }
                        dismissSelectionMenu()
                    }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制", fontSize = 11.sp)
                    }
                    TextButton(onClick = { dismissSelectionMenu() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    // ---- 书签弹窗 ----
    if (showBookmarkSheet) {
        AlertDialog(
            onDismissRequest = { showBookmarkSheet = false },
            title = { Text("书签 (${bookmarks.size})") },
            text = {
                if (bookmarks.isEmpty()) {
                    Text("暂无书签")
                } else {
                    LazyColumn {
                        items(bookmarks) { bm ->
                            ListItem(
                                headlineContent = { Text(bm.snippet, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.clickable {
                                    scrollToBookmark(bm.paragraphIndex)
                                    showBookmarkSheet = false
                                },
                                trailingContent = {
                                    IconButton(onClick = {
                                        currentFilePath?.let {
                                            BookmarkManager.removeBookmark(context, it, bm.id)
                                            refreshBookmarks()
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(18.dp))
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBookmarkSheet = false }) { Text("关闭") } },
        )
    }

    // ---- 高亮管理弹窗 ----
    if (showHighlightPanel) {
        AlertDialog(
            onDismissRequest = { showHighlightPanel = false },
            title = { Text("高亮标注 (${highlights.size})") },
            text = {
                if (highlights.isEmpty()) {
                    Text("暂无高亮标注")
                } else {
                    LazyColumn {
                        items(highlights) { hl ->
                            ListItem(
                                headlineContent = { Text(hl.text.take(60), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.clickable { navigateToHighlight(hl) },
                                trailingContent = {
                                    IconButton(onClick = { deleteHighlight(hl) }) {
                                        Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(18.dp))
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHighlightPanel = false }) { Text("关闭") } },
        )
    }

    // ---- 翻译结果弹窗 ----
    if (showTranslateResult) {
        AlertDialog(
            onDismissRequest = { showTranslateResult = false },
            title = { Text("翻译结果") },
            text = {
                Column {
                    if (isTranslating) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("翻译中…")
                        }
                    } else {
                        Text(translateResult)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTranslateResult = false }) { Text("关闭") } },
        )
    }

    // ---- 翻译设置 ----
    if (showTranslateSettings) {
        TranslationSettingsDialog(onDismiss = { showTranslateSettings = false })
    }
}

@Composable
private fun WelcomeScreen(
    recentFiles: List<RecentFilesManager.RecentFile>,
    onOpenFile: () -> Unit,
    onOpenRecent: (RecentFilesManager.RecentFile) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        // Logo
        Surface(
            modifier = Modifier.size(80.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("#", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("墨笺", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("InkNote — Markdown 阅读器", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

        Spacer(Modifier.height(32.dp))

        // 打开文件
        Button(onClick = onOpenFile, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("打开 Markdown 文件")
        }

        Spacer(Modifier.height(24.dp))

        // 最近文件
        if (recentFiles.isNotEmpty()) {
            Text("最近阅读", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(recentFiles.take(5)) { file ->
                    ListItem(
                        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.clickable { onOpenRecent(file) },
                        leadingContent = { Icon(Icons.Default.Description, null, modifier = Modifier.size(24.dp)) },
                    )
                }
            }
        }
    }
}

private fun readTextFromUri(context: android.content.Context, uri: Uri): String {
    val sb = StringBuilder()
    context.contentResolver.openInputStream(uri)?.use { input ->
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            var line = reader.readLine()
            while (line != null) { sb.appendLine(line); line = reader.readLine() }
        }
    } ?: throw IllegalStateException("无法打开文件: $uri")
    // 修复：某些编辑器/工具会把 LaTeX 转义序列转为控制字符
    // \f→0x0C \b→0x08 \a→0x07 \t→0x09 \r→0x0D
    var result = sb.toString()
    val before = result.count { it == '\\' }
    result = result
        .replace("\u000c", "\\f")   // form feed → \f (\frac, \fbox...)
        .replace("\u0008", "\\b")   // backspace → \b (\beta, \begin, \boldsymbol...)
        .replace("\u0007", "\\a")   // bell → \a (\alpha, \arg...)
        .replace("\u0009", "\\t")   // tab → \t (\theta, \tau, \times...)
        .replace("\u000d", "\\r")   // cr → \r (\right, \rho...)
    val after = result.count { it == '\\' }
    DebugLog.i("MainScreen", "readTextFromUri: ${result.length}字, 反斜杠: $before→$after")
    return result
}

private fun getDisplayName(context: android.content.Context, uri: Uri): String? {
    try {
        // SAF document URI — use DocumentsContract column
        context.contentResolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
    } catch (_: Exception) {}
    // Fallback: try OpenableColumns
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    } catch (_: Exception) {}
    return null
}
