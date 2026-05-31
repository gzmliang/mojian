package com.example.mdreader.ui.components

import android.webkit.WebView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mdreader.DebugLog

@Composable
fun ReadingToolbar(
    webView: WebView?,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    isPlaying: Boolean = false,
    onTtsPlay: () -> Unit = {},
    onTtsStop: () -> Unit = {},
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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 主题切换
            IconButton(
                onClick = {
                    val themes = listOf("light", "dark", "sepia")
                    val next = themes[(themes.indexOf(currentTheme) + 1) % themes.size]
                    onThemeChange(next)
                    callJs(webView, "setTheme('$next')")
                },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    when (currentTheme) {
                        "dark" -> Icons.Default.DarkMode
                        "sepia" -> Icons.Default.BrightnessMedium
                        else -> Icons.Default.LightMode
                    },
                    contentDescription = "主题",
                    modifier = Modifier.size(18.dp),
                )
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp))

            // 字体大小弹出菜单
            var showFontMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showFontMenu = true }, modifier = Modifier.size(34.dp)) {
                    Text("Aa", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = showFontMenu,
                    onDismissRequest = { showFontMenu = false },
                ) {
                    Text("字体大小: ${fontSize}px", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { v ->
                            val newSize = v.toInt()
                            onFontSizeChange(newSize)
                            callJs(webView, "setFontSize($newSize)")
                        },
                        valueRange = 8f..24f,
                        steps = 15,
                        modifier = Modifier.padding(horizontal = 8.dp).width(120.dp),
                    )
                }
            }

            // TTS
            IconButton(onClick = { if (isPlaying) onTtsStop() else onTtsPlay() },
                modifier = Modifier.size(34.dp), enabled = webView != null) {
                Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    if (isPlaying) "停止" else "朗读", Modifier.size(20.dp),
                    tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }

            // 书签
            IconButton(onClick = onAddBookmark, modifier = Modifier.size(30.dp), enabled = webView != null) {
                Icon(Icons.Default.BookmarkAdd, "加书签", Modifier.size(16.dp))
            }
            IconButton(onClick = onShowBookmarks, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Bookmarks, "书签列表", Modifier.size(16.dp))
            }

            // 高亮列表
            IconButton(onClick = onShowHighlights, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Star, "高亮列表", Modifier.size(16.dp))
            }

            // 搜索
            IconButton(onClick = onToggleSearch, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Search, "搜索", Modifier.size(16.dp),
                    tint = if (isSearchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }

            // 翻译
            IconButton(onClick = onTranslate, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Translate, "翻译", Modifier.size(16.dp))
            }
            IconButton(onClick = onTranslateSettings, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Settings, "设置", Modifier.size(16.dp))
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp))

            // 翻页
            IconButton(onClick = { callJs(webView, "scrollPage('up')") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "上", Modifier.size(18.dp))
            }
            IconButton(onClick = { callJs(webView, "scrollPage('down')") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "下", Modifier.size(18.dp))
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp))

            // 全屏
            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(30.dp)) {
                Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    if (isFullscreen) "退出全屏" else "全屏", Modifier.size(16.dp))
            }
        }
    }
}

private fun callJs(webView: WebView?, js: String) {
    if (webView == null) return
    DebugLog.i("Toolbar", "JS: $js")
    webView.evaluateJavascript(js, null)
}
