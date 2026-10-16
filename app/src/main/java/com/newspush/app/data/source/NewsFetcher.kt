package com.newspush.app.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class NewsFetcher(private val client: OkHttpClient) {
    suspend fun fetch(feed: RssFeed): List<ParsedItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(feed.url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            RssParser().parse(body)
        }
    }

    companion object {
        private const val USER_AGENT = "NewsPush/1.0 (Android; RSS reader)"
    }
}
