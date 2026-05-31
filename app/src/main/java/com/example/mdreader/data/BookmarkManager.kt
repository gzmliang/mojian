package com.example.mdreader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 书签管理。按文件路径存储书签列表。
 */
object BookmarkManager {
    private const val PREFS_NAME = "inknote_bookmarks"

    data class Bookmark(
        val id: String,
        val paragraphIndex: Int,
        val snippet: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    fun getBookmarks(context: Context, filePath: String): List<Bookmark> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(sanitizeKey(filePath), "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Bookmark(
                    id = obj.getString("id"),
                    paragraphIndex = obj.getInt("idx"),
                    snippet = obj.getString("snippet"),
                    timestamp = obj.optLong("ts", 0),
                )
            }.sortedBy { it.paragraphIndex }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addBookmark(context: Context, filePath: String, paragraphIndex: Int, snippet: String) {
        val bookmarks = getBookmarks(context, filePath).toMutableList()
        // 去重（同一段落不重复加）
        if (bookmarks.any { it.paragraphIndex == paragraphIndex }) return
        val id = "bm_${System.currentTimeMillis()}"
        bookmarks.add(Bookmark(id, paragraphIndex, snippet, System.currentTimeMillis()))
        save(context, filePath, bookmarks)
    }

    fun removeBookmark(context: Context, filePath: String, id: String) {
        val bookmarks = getBookmarks(context, filePath).toMutableList()
        bookmarks.removeAll { it.id == id }
        save(context, filePath, bookmarks)
    }

    private fun save(context: Context, filePath: String, bookmarks: List<Bookmark>) {
        val arr = JSONArray()
        bookmarks.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("idx", b.paragraphIndex)
                put("snippet", b.snippet)
                put("ts", b.timestamp)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(sanitizeKey(filePath), arr.toString()).commit()
    }

    private fun sanitizeKey(path: String) = path.replace("/", "_").replace(":", "_")
}
