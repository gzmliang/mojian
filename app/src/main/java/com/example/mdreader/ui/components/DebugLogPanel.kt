package com.example.mdreader.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mdreader.DebugLog
import kotlinx.coroutines.delay

/**
 * 可折叠的调试日志面板。
 *
 * - 显示在页面底部，默认折叠
 * - 点击标题栏展开/折叠
 * - 支持一键复制到剪贴板
 * - 自动刷新（每秒拉取新日志）
 */
@Composable
fun DebugLogPanel(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf(DebugLog.getLogs()) }

    // 定时刷新日志
    LaunchedEffect(expanded) {
        if (expanded) {
            while (true) {
                delay(1000)
                logText = DebugLog.getLogs()
            }
        }
    }

    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        // ---- 标题栏 ----
        Surface(
            onClick = { expanded = !expanded },
            color = Color(0xFF263238),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) "▼ 调试日志" else "▶ 调试日志",
                    color = Color(0xFF80CBC4),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                // 复制按钮
                IconButton(
                    onClick = { copyToClipboard(context, logText) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = Color(0xFF80CBC4),
                        modifier = Modifier.size(16.dp),
                    )
                }
                // 清空按钮
                IconButton(
                    onClick = {
                        DebugLog.clear()
                        logText = ""
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "清空",
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // ---- 日志内容 ----
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF37474F)),
            ) {
                val vScroll = rememberScrollState()
                val hScroll = rememberScrollState()

                SelectionContainer {
                    Text(
                        text = logText.ifEmpty { "（暂无日志）" },
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(vScroll)
                            .horizontalScroll(hScroll),
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clip = ClipData.newPlainText("debug_log", text)
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(clip)
}
