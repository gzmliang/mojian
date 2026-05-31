package com.example.mdreader.ui.components

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.mdreader.DebugLog

/**
 * 文档内搜索栏。悬浮在 WebView 上方。
 */
@Composable
fun SearchBar(
    webView: WebView?,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var query by remember { mutableStateOf("") }
    var matchInfo by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(visible) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { q ->
                    query = q
                    if (q.isNotEmpty()) {
                        webView?.evaluateJavascript("findText('${escapeJs(q)}')") { count ->
                            matchInfo = if (count != null) "${count}个" else ""
                        }
                    } else {
                        webView?.evaluateJavascript("clearSearchHighlights()", null)
                        matchInfo = ""
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("搜索文档…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { focusManager.clearFocus() }
                ),
            )

            if (matchInfo.isNotEmpty()) {
                Text(
                    text = matchInfo,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                IconButton(
                    onClick = {
                        webView?.evaluateJavascript("prevSearchMatch()", null)
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个", modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = {
                        webView?.evaluateJavascript("nextSearchMatch()") { info ->
                            matchInfo = info ?: matchInfo
                        }
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个", modifier = Modifier.size(18.dp))
                }
            }

            IconButton(onClick = {
                webView?.evaluateJavascript("clearSearchHighlights()", null)
                query = ""
                matchInfo = ""
                onDismiss()
            }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "关闭搜索", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun escapeJs(s: String): String {
    return s.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
}
