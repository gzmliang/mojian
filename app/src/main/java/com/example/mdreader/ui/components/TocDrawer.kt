package com.example.mdreader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 手机端右侧滑出的标题大纲抽屉。
 *
 * 从 Markdown 原文中提取 `# ~ ######` 标题，点击触发 WebView 滚动。
 */
@Composable
fun TocDrawer(
    markdownContent: String,
    onHeadingClick: (String) -> Unit,
) {
    val headings = remember(markdownContent) { extractHeadings(markdownContent) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = "大纲目录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        if (headings.isEmpty()) {
            Text(
                text = "暂无标题",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(headings) { heading ->
                    TextButton(
                        onClick = { onHeadingClick(heading.title) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = heading.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = (heading.level * 8).dp),
                            fontWeight = if (heading.level <= 2) FontWeight.Medium else FontWeight.Normal,
                            fontSize = (16 - heading.level).coerceAtLeast(12).sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 标题数据结构
 */
data class HeadingItem(
    val id: String,    // 生成的锚点 ID
    val title: String, // 标题文本
    val level: Int,    // 1-6 层级
)

fun extractHeadings(markdown: String): List<HeadingItem> {
    val regex = Regex("""^(#{1,6})\s+(.+)$""", RegexOption.MULTILINE)
    return regex.findAll(markdown).map { match ->
        val level = match.groupValues[1].length
        val title = match.groupValues[2].trim()
        // 生成安全的锚点 ID：转为小写 + 空格转连字符 + 去除非单词字符
        val id = title.lowercase()
            .replace(Regex("""\s+"""), "-")
            .replace(Regex("""[^\w\-]"""), "")
        HeadingItem(id = id, title = title, level = level)
    }.toList()
}
