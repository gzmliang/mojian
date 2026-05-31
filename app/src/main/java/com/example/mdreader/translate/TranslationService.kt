package com.example.mdreader.translate

import android.content.Context
import com.example.mdreader.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI 翻译服务。OpenAI 兼容接口，默认 DeepSeek。
 *
 * 支持自定义端点、API Key、模型名称。
 * 配置持久化在 SharedPreferences。
 */
object TranslationService {
    private const val TAG = "Translation"
    private const val PREFS_NAME = "inknote_translate"

    // 默认配置
    private const val DEFAULT_ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
    private const val DEFAULT_MODEL = "deepseek-chat"
    private const val DEFAULT_KEY = ""

    data class Config(
        val endpoint: String = DEFAULT_ENDPOINT,
        val apiKey: String = DEFAULT_KEY,
        val model: String = DEFAULT_MODEL,
    )

    fun getConfig(context: Context): Config {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Config(
            endpoint = prefs.getString("endpoint", DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT,
            apiKey = prefs.getString("api_key", DEFAULT_KEY) ?: DEFAULT_KEY,
            model = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL,
        )
    }

    fun saveConfig(context: Context, config: Config) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("endpoint", config.endpoint)
            .putString("api_key", config.apiKey)
            .putString("model", config.model)
            .apply()
    }

    fun isConfigured(context: Context): Boolean {
        val config = getConfig(context)
        return config.apiKey.isNotBlank()
    }

    /**
     * 翻译文本。自动检测语言 → 中文。
     */
    suspend fun translate(
        context: Context,
        text: String,
        targetLang: String = "中文",
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = getConfig(context)

        if (config.apiKey.isBlank()) {
            return@withContext Result.failure(Exception("请先设置 API Key"))
        }

        // 检测是否已经是中文（简单启发式）
        val isChinese = text.any { it in '\u4e00'..'\u9fff' }
        val direction = if (isChinese) "英文" else "中文"

        val prompt = "请将以下文本翻译成$direction，只返回翻译结果，不要加任何解释：\n\n$text"

        try {
            val result = callApi(config.endpoint, config.apiKey, config.model, prompt)
            DebugLog.i(TAG, "翻译成功: ${text.take(30)}... → ${result.take(30)}...")
            Result.success(result.trim())
        } catch (e: Exception) {
            DebugLog.e(TAG, "翻译失败: ${e.message}")
            Result.failure(e)
        }
    }

    private fun callApi(endpoint: String, apiKey: String, model: String, prompt: String): String {
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "你是一个专业的翻译助手。只返回翻译结果，不要加任何解释、注释或额外文字。")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
            put("max_tokens", 4096)
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val code = conn.responseCode
        val responseText = if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
            throw Exception("API 错误 ($code): $err")
        }

        val json = JSONObject(responseText)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
}
