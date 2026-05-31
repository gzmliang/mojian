package com.example.mdreader

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内调试日志系统。
 *
 * 使用方式：
 * - DebugLog.i("消息")   → 普通信息
 * - DebugLog.e("消息")   → 错误信息
 * - DebugLog.getLogs()   → 获取全部日志文本
 * - DebugLog.clear()     → 清空日志
 *
 * 用户可在应用内长按日志区域一键复制，发送给开发者排查问题。
 */
object DebugLog {
    private val dateFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val buffer = StringBuilder()
    private const val MAX_LINES = 500

    fun i(tag: String, msg: String) {
        append("I", tag, msg)
    }

    fun w(tag: String, msg: String) {
        append("W", tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        append("E", tag, msg)
        throwable?.let {
            buffer.append("  ${it.javaClass.simpleName}: ${it.message}\n")
        }
        trimIfNeeded()
    }

    fun getLogs(): String = buffer.toString()

    fun clear() {
        buffer.clear()
    }

    private fun append(level: String, tag: String, msg: String) {
        val ts = dateFmt.format(Date())
        buffer.append("[$ts] $level/$tag: $msg\n")
        trimIfNeeded()
    }

    private fun trimIfNeeded() {
        val lines = buffer.lines()
        if (lines.size > MAX_LINES) {
            buffer.clear()
            buffer.append(lines.takeLast(MAX_LINES / 2).joinToString("\n"))
        }
    }
}
