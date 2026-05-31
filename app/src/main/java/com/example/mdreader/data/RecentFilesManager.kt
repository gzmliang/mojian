package com.example.mdreader.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 最近阅读文件管理。
 * 存储最近 10 个打开过的文件路径及时间戳。
 */
object RecentFilesManager {
    private const val PREFS_NAME = "inknote_recent"
    private const val KEY_FILES = "recent_files"
    private const val MAX_FILES = 10

    data class RecentFile(
        val path: String,
        val name: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    fun getRecentFiles(context: Context): List<RecentFile> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FILES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RecentFile(
                    path = obj.getString("path"),
                    name = obj.getString("name"),
                    timestamp = obj.optLong("ts", 0),
                )
            }.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRecentFile(context: Context, path: String, name: String) {
        val files = getRecentFiles(context).toMutableList()
        // 去重
        files.removeAll { it.path == path }
        files.add(0, RecentFile(path, name, System.currentTimeMillis()))
        // 保留最近 MAX_FILES 条
        val trimmed = files.take(MAX_FILES)
        save(context, trimmed)
    }

    private fun save(context: Context, files: List<RecentFile>) {
        val arr = JSONArray()
        files.forEach { f ->
            arr.put(JSONObject().apply {
                put("path", f.path)
                put("name", f.name)
                put("ts", f.timestamp)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_FILES, arr.toString()).apply()
    }
}
