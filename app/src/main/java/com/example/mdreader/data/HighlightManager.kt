package com.example.mdreader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户高亮标记管理。按文件路径存储高亮列表。
 */
object HighlightManager {
    private const val PREFS_NAME = "inknote_highlights"

    data class Highlight(
        val id: String,
        val paraIdx: Int,
        val startOffset: Int,
        val endOffset: Int,
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    fun getHighlights(context: Context, filePath: String): List<Highlight> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(sanitizeKey(filePath), "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Highlight(
                    id = obj.getString("id"),
                    paraIdx = obj.getInt("paraIdx"),
                    startOffset = obj.getInt("startOffset"),
                    endOffset = obj.getInt("endOffset"),
                    text = obj.getString("text"),
                    timestamp = obj.optLong("ts", 0),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHighlight(context: Context, filePath: String, highlight: Highlight) {
        val list = getHighlights(context, filePath).toMutableList()
        // 去重（同段落同偏移）
        if (list.any { it.paraIdx == highlight.paraIdx && it.startOffset == highlight.startOffset && it.endOffset == highlight.endOffset }) return
        list.add(highlight)
        save(context, filePath, list)
    }

    fun removeHighlight(context: Context, filePath: String, startOffset: Int, endOffset: Int) {
        val list = getHighlights(context, filePath).toMutableList()
        list.removeAll { it.startOffset == startOffset && it.endOffset == endOffset }
        save(context, filePath, list)
    }

    fun getHighlightsJson(filePath: String, highlights: List<Highlight>): String {
        val arr = JSONArray()
        highlights.forEach { h ->
            arr.put(JSONObject().apply {
                put("paraIdx", h.paraIdx)
                put("startOffset", h.startOffset)
                put("endOffset", h.endOffset)
            })
        }
        return arr.toString()
    }

    private fun save(context: Context, filePath: String, highlights: List<Highlight>) {
        val arr = JSONArray()
        highlights.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id)
                put("paraIdx", h.paraIdx)
                put("startOffset", h.startOffset)
                put("endOffset", h.endOffset)
                put("text", h.text)
                put("ts", h.timestamp)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(sanitizeKey(filePath), arr.toString()).commit()
    }

    private fun sanitizeKey(path: String) = path.replace("/", "_").replace(":", "_")
}
