package com.newspush.app.data.repository

import com.newspush.app.data.local.NewsDao
import com.newspush.app.data.model.AppSettings
import com.newspush.app.data.model.Category
import com.newspush.app.data.model.NewsArticle
import com.newspush.app.data.prefs.SettingsRepository
import com.newspush.app.data.remote.SocialApiClient
import com.newspush.app.data.remote.SocialApiException
import com.newspush.app.data.source.DefaultFeeds
import com.newspush.app.data.source.NewsFetcher
import com.newspush.app.data.source.ParsedItem
import com.newspush.app.data.source.RssFeed
import com.newspush.app.domain.NewsProcessor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import java.util.UUID

data class SyncReport(val fetchedCount: Int, val candidates: List<NewsArticle>)

class NewsRepository(
    private val dao: NewsDao,
    private val settingsRepository: SettingsRepository,
    private val fetcher: NewsFetcher,
    private val processor: NewsProcessor,
    private val socialApiClient: SocialApiClient
) {
    fun observeAll(): Flow<List<NewsArticle>> = dao.observeAll()

    fun observeByCategory(category: Category): Flow<List<NewsArticle>> =
        if (category == Category.GENERAL) dao.observeAll() else dao.observeByCategory(category.id)

    fun observeArticle(id: String): Flow<NewsArticle?> = dao.observeById(id)

    fun observeDeleted(): Flow<List<NewsArticle>> = dao.observeDeleted()

    suspend fun markRead(id: String) = dao.markRead(id)

    suspend fun toggleStarred(id: String) = dao.toggleStarred(id)

    suspend fun markDeleted(id: String) = dao.markDeleted(id, System.currentTimeMillis())

    suspend fun restore(id: String) = dao.restore(id)

    suspend fun setReadLater(id: String, readLater: Boolean) = dao.setReadLater(id, readLater)

    suspend fun sync(): SyncReport {
        val settings = settingsRepository.settings.first()
        val feeds = buildFeeds(settings)
        val fetchedAt = System.currentTimeMillis()
        val fetched = coroutineScope {
            feeds.map { feed ->
                async {
                    val items = try {
                        fetcher.fetch(feed)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    items.map { it to feed }
                }
            }.awaitAll().flatten()
        }

        val deduped = fetched
            .groupBy { NewsProcessor.itemId(it.first) }
            .map { (_, list) -> list.maxByOrNull { (_, feed) -> feed.authorityWeight } ?: list.first() }

        val recencyCutoff = fetchedAt - RECENCY_WINDOW_MS
        val existingIds = dao.getAllIds().toSet()
        val blockedSources = settings.blacklistSources
        val blockedKeywords = settings.blacklistKeywords
        val fresh = deduped
            .filter { NewsProcessor.itemId(it.first) !in existingIds }
            .filter { (it.first.pubDate ?: fetchedAt) >= recencyCutoff }
            .filter { it.second.sourceName !in blockedSources }
            .filter { !containsBlockedKeyword(it.first, blockedKeywords) }

        // 走 DeepSeek 时限制每轮条数，控制费用与耗时；纯本地处理则放宽
        val maxProcess = if (settings.deepSeekApiKey.isBlank()) {
            MAX_LOCAL_PROCESS_PER_SYNC
        } else {
            MAX_LLM_PROCESS_PER_SYNC
        }
        val toProcess = fresh
            .sortedWith(
                compareByDescending<Pair<ParsedItem, RssFeed>> { it.first.pubDate ?: 0L }
                    .thenByDescending { it.second.authorityWeight }
            )
            .take(maxProcess)

        val semaphore = Semaphore(MAX_CONCURRENT_LLM)
        val articles = coroutineScope {
            toProcess.map { (item, feed) ->
                async {
                    semaphore.acquire()
                    try {
                        try {
                            processor.toArticle(
                                item = item,
                                feed = feed,
                                apiKey = settings.deepSeekApiKey,
                                translateEnglishSummaries = settings.translateEnglishSummaries,
                                fetchedAt = fetchedAt
                            )
                        } catch (_: Exception) {
                            null
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (articles.isNotEmpty()) dao.upsertAll(articles)
        applyBlacklistCleanup(blockedKeywords, blockedSources)
        dao.deletePublishedBefore(recencyCutoff)
        dao.deleteOlderThan(fetchedAt - RETENTION_MS)

        val socialCount = syncSocial(settings, fetchedAt)

        val threshold = settings.importanceThreshold
        val since = fetchedAt - RECENT_WINDOW_MS
        val candidates = dao.getUnnotifiedImportant(since, threshold, 5)
        return SyncReport(deduped.size + socialCount, candidates)
    }

    private suspend fun syncSocial(settings: AppSettings, fetchedAt: Long): Int {
        val baseUrl = settings.socialApiUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) return 0

        val deviceId = settings.socialDeviceId.ifBlank {
            UUID.randomUUID().toString().also { settingsRepository.setSocialDeviceId(it) }
        }

        var apiKey = settings.socialApiKey
        if (apiKey.isBlank()) {
            apiKey = registerSocial(baseUrl, deviceId, settings) ?: return 0
        }

        val maxProcess = if (settings.deepSeekApiKey.isBlank()) {
            MAX_LOCAL_PROCESS_PER_SYNC
        } else {
            MAX_LLM_PROCESS_PER_SYNC
        }

        val page = try {
            socialApiClient.fetchFeed(
                baseUrl = baseUrl,
                apiKey = apiKey,
                cursor = settings.socialCursor,
                limit = maxProcess,
                latest = settings.socialCursor == 0L
            )
        } catch (e: SocialApiException) {
            if (e.status == 401 && settings.socialInviteCode.isNotBlank()) {
                settingsRepository.setSocialApiKey("")
                val newKey = registerSocial(baseUrl, deviceId, settings)
                if (newKey == null) return 0
                try {
                    socialApiClient.fetchFeed(
                        baseUrl = baseUrl,
                        apiKey = newKey,
                        cursor = settings.socialCursor,
                        limit = maxProcess,
                        latest = settings.socialCursor == 0L
                    )
                } catch (retry: Exception) {
                    settingsRepository.setSocialLastError(socialErrorMessage(retry))
                    return 0
                }
            } else {
                settingsRepository.setSocialLastError(socialErrorMessage(e))
                return 0
            }
        } catch (e: Exception) {
            settingsRepository.setSocialLastError("连接失败：" + (e.message ?: "未知错误"))
            return 0
        }

        val existingIds = dao.getAllIds().toSet()
        val recencyCutoff = fetchedAt - RECENCY_WINDOW_MS
        val articles = mutableListOf<NewsArticle>()
        for (item in page.items) {
            val published = item.publishedAtTs * 1000L
            if (published <= 0L || published < recencyCutoff) continue
            val parsed = ParsedItem(
                guid = item.postId,
                title = item.title,
                link = item.url,
                description = item.description,
                content = item.description,
                pubDate = published,
                imageUrl = item.coverUrl
            )
            if (NewsProcessor.itemId(parsed) in existingIds) continue
            if (containsBlockedKeyword(parsed, settings.blacklistKeywords)) continue

            val sourceLabel = item.accountName + " · B站"
            if (sourceLabel in settings.blacklistSources || item.accountName in settings.blacklistSources) continue

            val category = Category.fromDisplayName(item.category)
            val feed = RssFeed(
                url = item.url,
                categoryId = category.id,
                sourceName = sourceLabel,
                authorityWeight = 1.0f
            )
            try {
                articles.add(
                    processor.toArticle(
                        item = parsed,
                        feed = feed,
                        apiKey = settings.deepSeekApiKey,
                        translateEnglishSummaries = settings.translateEnglishSummaries,
                        platform = "bilibili",
                        fetchedAt = fetchedAt
                    )
                )
            } catch (_: Exception) {
                // 单条处理失败不影响整页
            }
        }

        if (articles.isNotEmpty()) dao.upsertAll(articles)
        settingsRepository.setSocialCursor(page.nextCursor)
        settingsRepository.setSocialLastError("")
        return articles.size
    }

    private suspend fun registerSocial(
        baseUrl: String,
        deviceId: String,
        settings: AppSettings
    ): String? {
        if (settings.socialInviteCode.isBlank()) {
            settingsRepository.setSocialLastError("请先在设置里填写邀请码")
            return null
        }
        return try {
            val key = socialApiClient.register(baseUrl, deviceId, settings.socialInviteCode, APP_VERSION)
            settingsRepository.setSocialApiKey(key)
            settingsRepository.setSocialLastError("")
            key
        } catch (e: SocialApiException) {
            settingsRepository.setSocialLastError(socialErrorMessage(e))
            null
        } catch (e: Exception) {
            settingsRepository.setSocialLastError("连接失败：" + (e.message ?: "未知错误"))
            null
        }
    }

    private fun socialErrorMessage(error: Throwable): String = when {
        error is SocialApiException && error.code == "INVITE_REQUIRED" -> "邀请码错误或缺失"
        error is SocialApiException && error.status == 403 -> "邀请码错误或缺失"
        error is SocialApiException && error.status == 429 -> "请求太频繁，请稍后再试"
        error is SocialApiException && error.status == 401 -> "设备 Key 已失效，请重新连接"
        else -> error.message ?: "连接失败"
    }

    /**
     * 把新增黑名单前就已经入库的内容清掉，避免要等 14 天才消失。
     */
    private suspend fun applyBlacklistCleanup(keywords: List<String>, sources: Set<String>) {
        if (sources.isNotEmpty()) {
            dao.deleteBySources(sources.toList())
        }
        for (word in keywords) {
            val ids = dao.findIdsMatching("%$word%")
            if (ids.isNotEmpty()) dao.deleteByIds(ids)
        }
    }

    private fun containsBlockedKeyword(item: ParsedItem, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return false
        val haystack = item.title + " " + item.description + " " + item.content
        return keywords.any { haystack.contains(it, ignoreCase = true) }
    }

    private fun buildFeeds(settings: AppSettings): List<RssFeed> {
        val enabled = settings.enabledCategories
        val defaults = DefaultFeeds.list.filter { it.categoryId in enabled }
        val customs = settings.customFeeds.filter {
            it.categoryId == Category.GENERAL.id || it.categoryId in enabled
        }
        return defaults + customs
    }

    companion object {
        private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private const val RECENT_WINDOW_MS = 48L * 60 * 60 * 1000
        private const val RECENCY_WINDOW_MS = 14L * 24 * 60 * 60 * 1000
        private const val MAX_LLM_PROCESS_PER_SYNC = 20
        private const val MAX_LOCAL_PROCESS_PER_SYNC = 200
        private const val MAX_CONCURRENT_LLM = 3
        private const val APP_VERSION = "1.2"
    }
}
