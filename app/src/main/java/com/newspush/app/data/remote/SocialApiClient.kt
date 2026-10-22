package com.newspush.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class SocialFeedItem(
    val id: Long,
    val postId: String,
    val accountName: String,
    val category: String,
    val title: String,
    val description: String,
    val url: String,
    val coverUrl: String?,
    val publishedAtTs: Long
)

data class SocialFeedPage(
    val items: List<SocialFeedItem>,
    val nextCursor: Long
)

class SocialApiException(
    val status: Int,
    val code: String?,
    message: String
) : Exception(message)

/**
 * NewsPush 社交源后端的客户端。后端负责 B 站采集，App 只拉统一格式的 feed。
 */
class SocialApiClient(private val client: OkHttpClient) {
    suspend fun register(
        baseUrl: String,
        deviceId: String,
        inviteCode: String,
        appVersion: String
    ): String = withContext(Dispatchers.IO) {
        val base = normalize(baseUrl)
        if (base.isBlank()) throw SocialApiException(0, null, "服务器地址为空")

        val payload = JSONObject()
            .put("device_id", deviceId)
            .put("app_version", appVersion)
        if (inviteCode.isNotBlank()) payload.put("invite_code", inviteCode)

        val request = Request.Builder()
            .url("$base/v1/devices/register")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw parseError(response.code, body)
            val key = JSONObject(body).optString("api_key").trim()
            if (key.isBlank()) throw SocialApiException(response.code, null, "服务器未返回 api_key")
            key
        }
    }

    suspend fun fetchFeed(
        baseUrl: String,
        apiKey: String,
        cursor: Long,
        limit: Int,
        latest: Boolean = false
    ): SocialFeedPage = withContext(Dispatchers.IO) {
        val base = normalize(baseUrl)
        val safeLimit = limit.coerceIn(1, 100)
        val request = Request.Builder()
            .url("$base/v1/feed?cursor=$cursor&limit=$safeLimit&latest=" + if (latest) "true" else "false")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw parseError(response.code, body)
            val json = JSONObject(body)
            val items = mutableListOf<SocialFeedItem>()
            val array = json.optJSONArray("items") ?: JSONArray()
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val postId = obj.optString("post_id")
                val url = obj.optString("url")
                if (postId.isBlank() || url.isBlank()) continue
                items.add(
                    SocialFeedItem(
                        id = obj.optLong("id"),
                        postId = postId,
                        accountName = obj.optString("account_name"),
                        category = obj.optString("category"),
                        title = obj.optString("title"),
                        description = obj.optString("description"),
                        url = url,
                        coverUrl = if (obj.isNull("cover_url")) {
                            null
                        } else {
                            obj.optString("cover_url").takeIf { it.isNotBlank() && it != "null" }
                        },
                        publishedAtTs = obj.optLong("published_at_ts")
                    )
                )
            }
            SocialFeedPage(
                items = items,
                nextCursor = json.optLong("next_cursor", cursor)
            )
        }
    }

    private fun normalize(url: String): String = url.trim().trimEnd('/')

    private fun parseError(status: Int, body: String): SocialApiException {
        var code: String? = null
        var message = "HTTP $status"
        try {
            val json = JSONObject(body)
            val detail = json.opt("detail")
            when (detail) {
                is JSONObject -> {
                    code = detail.optString("code").ifBlank { null }
                    message = detail.optString("message").ifBlank { message }
                }
                is String -> message = detail
            }
        } catch (_: Exception) {
            if (body.isNotBlank()) message = body.take(200)
        }
        return SocialApiException(status, code, message)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
