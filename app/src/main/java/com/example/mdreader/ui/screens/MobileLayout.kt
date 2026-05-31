package com.example.mdreader.ui.screens

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mdreader.DebugLog
import com.example.mdreader.ui.components.MarkdownWebView
import com.example.mdreader.ui.components.ReadingToolbar
import com.example.mdreader.ui.components.TocDrawer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileLayout(
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                TocDrawer(
                    markdownContent = markdownContent,
                    onHeadingClick = { title ->
                        val esc = title.replace("\\", "\\\\").replace("'", "\\'")
                        webView?.evaluateJavascript("scrollToHeading('$esc')", null)
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
        gesturesEnabled = true,
    ) {
        Scaffold(
            modifier = modifier,
            topBar = {
                AnimatedVisibility(visible = !isFullscreen) {
                    TopAppBar(
                        title = { Text(displayName, style = MaterialTheme.typography.titleMedium) },
                        navigationIcon = {
                            IconButton(onClick = onFileOpenClick) {
                                Icon(Icons.Default.FolderOpen, "打开文件")
                            }
                        },
                        actions = {
                            IconButton(onClick = onToggleFullscreen) {
                                Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "全屏")
                            }
                            IconButton(onClick = {
                                scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() }
                            }) {
                                Icon(Icons.Default.List, "目录")
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
            val effectivePadding = if (isFullscreen) {
                PaddingValues(0.dp)
            } else innerPadding
            Box(Modifier.fillMaxSize().padding(effectivePadding)) {
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
