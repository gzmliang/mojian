package com.example.mdreader.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.mdreader.DebugLog
import java.io.File
import java.io.FileOutputStream

/**
 * 导出服务：PDF（系统打印 API）+ HTML 文件
 */
object ExportService {

    private const val TAG = "Export"

    /**
     * 通过 Android PrintManager 导出 PDF。
     * 会弹出系统打印对话框，用户可选择「另存为 PDF」。
     */
    fun exportPdfViaPrint(context: Context, fileName: String, webView: WebView) {
        // 切回流式布局，让 PrintDocumentAdapter 看到完整内容
        webView.evaluateJavascript("if(window._prepareForPrint)_prepareForPrint()", null)

        val printMgr = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = fileName.replace(".md", "").replace(".html", "").replace(".txt", "")
        val adapter = webView.createPrintDocumentAdapter(jobName)
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printMgr.print(jobName, adapter, attrs)
        DebugLog.i(TAG, "打印 PDF: $jobName")

        // 延迟恢复固定布局（给打印系统时间捕捉内容）
        webView.postDelayed({
            webView.evaluateJavascript("if(window._restoreLayout)_restoreLayout()", null)
        }, 3000)
    }

    /**
     * 导出 HTML 文件到 Downloads 目录。
     * 用完整 HTML 模板包裹 Markdown 渲染内容。
     */
    fun exportHtml(
        context: Context,
        htmlBody: String,
        title: String,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            val fullHtml = buildHtmlDocument(htmlBody, title)
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${title.replace(".md", "").replace(".html", "")}.html")
            FileOutputStream(file).use { it.write(fullHtml.toByteArray(Charsets.UTF_8)) }
            DebugLog.i(TAG, "导出 HTML: ${file.absolutePath} (${file.length()} bytes)")
            onComplete(file)
        } catch (e: Exception) {
            DebugLog.e(TAG, "导出 HTML 失败: ${e.message}")
            onError(e.message ?: "未知错误")
        }
    }

    /**
     * 从 WebView 获取渲染后的 HTML 内容（用于导出）。
     */
    fun getRenderedHtml(webView: WebView, onReady: (String) -> Unit) {
        webView.evaluateJavascript(
            "document.getElementById('content').innerHTML",
        ) { raw ->
            val html = raw?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
            onReady(html)
        }
    }

    private fun buildHtmlDocument(bodyHtml: String, title: String): String {
        return """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>$title</title>
<style>
  body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans SC", sans-serif;
    font-size: 16px; line-height: 1.8; color: #1a1a1a;
    max-width: 800px; margin: 40px auto; padding: 0 20px;
  }
  h1 { font-size: 1.8em; border-bottom: 2px solid #e0e0e0; padding-bottom: 0.3em; }
  h2 { font-size: 1.5em; }
  h3 { font-size: 1.25em; }
  pre { background: #f5f5f5; border-radius: 8px; padding: 12px 16px; overflow-x: auto; font-size: 14px; }
  code { font-family: "JetBrains Mono", "Fira Code", monospace; font-size: 0.9em; }
  blockquote { border-left: 4px solid #1A73E8; padding: 8px 16px; background: #f0f4ff; color: #444; }
  table { border-collapse: collapse; width: 100%; }
  th, td { border: 1px solid #ddd; padding: 8px 12px; }
  th { background: #f5f5f5; }
  img { max-width: 100%; }
  .katex-display { overflow-x: auto; }
</style>
</head>
<body>
$bodyHtml
</body>
</html>"""
    }
}
