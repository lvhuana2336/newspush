package com.newspush.app.data.remote

import com.newspush.app.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class EnrichedResult(
    val category: Category,
    val summary: String
)

/**
 * DeepSeek 的 Chat Completions 客户端（OpenAI 兼容协议）。
 */
class DeepSeekClient(private val client: OkHttpClient) {
    suspend fun summarizeAndClassify(
        apiKey: String,
        title: String,
        content: String
    ): EnrichedResult? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null

        val body = JSONObject()
            .put("model", MODEL)
            .put("temperature", 0.3)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", buildUserPrompt(title, content)))
            )

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val responseBody = response.body?.string() ?: return@withContext null
            parse(responseBody)
        }
    }

    private fun parse(responseBody: String): EnrichedResult? {
        return try {
            val root = JSONObject(responseBody)
            val content = root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            val json = extractJson(content)
            val category = Category.fromId(json.optString("category"))
                .takeIf { it != Category.GENERAL }
                ?: return null
            val summary = json.optString("summary").trim()
            EnrichedResult(category = category, summary = summary)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJson(text: String): JSONObject {
        val trimmed = text.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            JSONObject(trimmed.substring(start, end + 1))
        } else {
            JSONObject(trimmed)
        }
    }

    private fun buildUserPrompt(title: String, content: String): String {
        val categories = Category.selectable().joinToString("、") { "${it.displayName}(${it.id})" }
        return buildString {
            append("候选分类：").append(categories).append('\n')
            append("新闻标题：").append(title).append('\n')
            append("新闻内容：").append(content.take(MAX_CONTENT_CHARS))
        }
    }

    private companion object {
        const val BASE_URL = "https://api.deepseek.com"
        const val MODEL = "deepseek-chat"
        const val MAX_CONTENT_CHARS = 2_000
        const val SYSTEM_PROMPT =
            "你是中文新闻编辑助手。请阅读给定新闻，完成两项任务：1) 用不超过120个汉字概括核心事实，不要编造细节；" +
                "2) 从候选分类中选择最贴切的一个。必须只输出一个 JSON 对象，格式为 " +
                "{\"category\":\"分类id\",\"summary\":\"摘要\"}。"
    }
}
