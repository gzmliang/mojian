package com.example.mdreader.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.mdreader.DebugLog
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Markdown Reader 精简 TTS 引擎。
 *
 * 基于墨阅 Moreader 的 TTS 开发经验（踩坑 1-16），
 * 保留核心稳定模式：全局单例 + QUEUE_FLUSH 停止 + 连续错误保护。
 */
class TtsEngine(private val appContext: Context) {

    companion object {
        private const val TAG = "TtsEngine"

        // 全局单例（坑10：避免频繁创建/销毁导致 Service Binding 冲突）
        @Volatile
        private var globalTts: TextToSpeech? = null

        @Volatile
        private var globalIsReady: Boolean = false

        // 回调映射（坑7：ConcurrentHashMap 线程安全）
        private val utteranceListeners = ConcurrentHashMap<String, TTSListener>()

        @Volatile
        private var counter: Int = 0

        fun fullDestroy() {
            globalTts?.stop()
            globalTts?.shutdown()
            globalTts = null
            globalIsReady = false
            utteranceListeners.clear()
        }
    }

    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 3
    private val mainHandler = Handler(Looper.getMainLooper())

    interface TTSListener {
        fun onStart(utteranceId: String)
        fun onDone(utteranceId: String)
        fun onError(utteranceId: String, error: String)
    }

    /**
     * 初始化 TTS 引擎。如果全局引擎已存在，重新绑定 listener。
     */
    @Synchronized
    fun ensureInitialized(callback: (Boolean) -> Unit) {
        if (globalTts != null && globalIsReady) {
            setupTts()
            mainHandler.post { callback(true) }
            return
        }

        try {
            DebugLog.i(TAG, "创建 TextToSpeech 实例...")
            globalTts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    globalIsReady = true
                    setupTts()
                    DebugLog.i(TAG, "TTS 引擎初始化成功")
                    mainHandler.post { callback(true) }
                } else {
                    globalIsReady = false
                    DebugLog.e(TAG, "TTS 引擎初始化失败: status=$status")
                    mainHandler.post { callback(false) }
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "创建 TTS 实例异常", e)
            mainHandler.post { callback(false) }
        }
    }

    private fun setupTts() {
        val tts = globalTts ?: return
        tts.language = Locale.CHINESE
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == null) return
                DebugLog.i(TAG, "onStart: $utteranceId")
                val listener = utteranceListeners[utteranceId]
                if (listener != null) {
                    mainHandler.post { listener.onStart(utteranceId) }
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == null) return
                DebugLog.i(TAG, "onDone: $utteranceId")
                val listener = utteranceListeners.remove(utteranceId)
                if (listener != null) {
                    mainHandler.post { listener.onDone(utteranceId) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == null) return
                DebugLog.e(TAG, "onError(deprecated): $utteranceId")
                val listener = utteranceListeners.remove(utteranceId)
                if (listener != null) {
                    mainHandler.post { listener.onError(utteranceId, "TTS error") }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == null) return
                DebugLog.e(TAG, "onError: $utteranceId, code=$errorCode")
                val listener = utteranceListeners.remove(utteranceId)
                if (listener != null) {
                    mainHandler.post { listener.onError(utteranceId, "TTS error code=$errorCode") }
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                // no-op
            }
        })
    }

    fun speak(text: String, listener: TTSListener) {
        val tts = globalTts
        if (tts == null || !globalIsReady) {
            DebugLog.e(TAG, "speak 失败：引擎未就绪")
            mainHandler.post { listener.onError("", "引擎未初始化") }
            return
        }

        if (text.isBlank()) {
            mainHandler.post { listener.onDone("") }
            return
        }

        val id = "u_${counter++}"
        utteranceListeners[id] = listener

        val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        DebugLog.i(TAG, "speak(id=$id, len=${text.length}): result=$result")

        if (result != TextToSpeech.SUCCESS) {
            utteranceListeners.remove(id)
            mainHandler.post { listener.onError(id, "speak() returned $result") }
        }
    }

    fun recordError(): Boolean {
        consecutiveErrors++
        return consecutiveErrors >= maxConsecutiveErrors
    }

    fun resetErrors() {
        consecutiveErrors = 0
    }

    fun stop() {
        DebugLog.i(TAG, "stop() — flush queue")
        globalTts?.stop()
        globalTts?.speak("", TextToSpeech.QUEUE_FLUSH, null, null)
        utteranceListeners.clear()
        consecutiveErrors = 0
    }

    fun destroy() {
        DebugLog.i(TAG, "destroy()")
        globalTts?.stop()
        utteranceListeners.clear()
        consecutiveErrors = 0
    }
}
