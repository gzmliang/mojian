package com.example.mdreader.ui.screens

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mdreader.ui.components.*
import com.example.mdreader.ui.components.HeadingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabletLayout(
    modifier: Modifier = Modifier,
    filePath: String?,
    displayName: String = "墨笺",
    markdownContent: String,
    isLoading: Boolean = false,
    fontSize: Int = 16,
    onFontSizeChange: (Int) -> Unit = {},
    isTtsPlaying: Boolean = false,
    onTtsPlay: () -> Unit = {},
    onTtsStop: () -> Unit = {},
    onWebViewReady: ((WebView) -> Unit)? = null,
    onFileOpenClick: () -> Unit = {},
    isSearchVisible: Boolean = false,
    onToggleSearch: () -> Unit = {},
    onAddBookmark: () -> Unit = {},
    onShowBookmarks: () -> Unit = {},
    onShowHighlights: () -> Unit = {},
    onTranslate: () -> Unit = {},
    onTranslateSettings: () -> Unit = {},
    currentTheme: String = "light",
    onThemeChange: (String) -> Unit = {},
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onSelectionChanged: ((String) -> Unit)? = null,
) {
    val headings = remember(markdownContent) { extractHeadings(markdownContent) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            AnimatedVisibility(visible = !isFullscreen) {
                TopAppBar(
                    title = { Text(displayName, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = onFileOpenClick) { Icon(Icons.Default.FolderOpen, "打开") }
                    },
                    actions = {
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "全屏")
                        }
                    },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = !isFullscreen) {
                ReadingToolbar(
                    webView = webView, fontSize = fontSize, onFontSizeChange = onFontSizeChange,
                    isPlaying = isTtsPlaying, onTtsPlay = onTtsPlay, onTtsStop = onTtsStop,
                    isSearchVisible = isSearchVisible, onToggleSearch = onToggleSearch,
                    onAddBookmark = onAddBookmark, onShowBookmarks = onShowBookmarks,
                    onShowHighlights = onShowHighlights,
                    onTranslate = onTranslate, onTranslateSettings = onTranslateSettings,
                    currentTheme = currentTheme, onThemeChange = onThemeChange,
                    isFullscreen = isFullscreen, onToggleFullscreen = onToggleFullscreen,
                )
            }
        },
    ) { innerPadding ->
        val effectivePadding = if (isFullscreen) PaddingValues(0.dp) else innerPadding
        Row(Modifier.fillMaxSize().padding(effectivePadding)) {
            AnimatedVisibility(visible = !isFullscreen) {
                Surface(Modifier.width(280.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Text("大纲", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        LazyColumn {
                            items(headings) { h ->
                                TextButton(onClick = {
                                    val esc = h.title.replace("\\", "\\\\").replace("'", "\\'")
                                    webView?.evaluateJavascript("scrollToHeading('$esc')", null)
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text(h.title, fontSize = (16 - h.level).coerceAtLeast(12).sp,
                                        modifier = Modifier.padding(start = (h.level * 10).dp))
                                }
                            }
                        }
                    }
                }
            }
            if (!isFullscreen) VerticalDivider(Modifier.fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else MarkdownWebView(
                    markdownContent = markdownContent, modifier = Modifier.fillMaxSize(),
                    onWebViewReady = { wv -> webView = wv; onWebViewReady?.invoke(wv) },
                    onSelectionChanged = onSelectionChanged,
                )
            }
        }
    }
}
