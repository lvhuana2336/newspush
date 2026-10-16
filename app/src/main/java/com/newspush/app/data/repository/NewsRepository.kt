package com.newspush.app.data.repository

import com.newspush.app.data.local.NewsDao
import com.newspush.app.data.model.AppSettings
import com.newspush.app.data.model.Category
import com.newspush.app.data.model.NewsArticle
import com.newspush.app.data.prefs.SettingsRepository
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

data class SyncReport(val fetchedCount: Int, val candidates: List<NewsArticle>)

class NewsRepository(
    private val dao: NewsDao,
    private val settingsRepository: SettingsRepository,
    private val fetcher: NewsFetcher,
    private val processor: NewsProcessor
) {
    fun observeAll(): Flow<List<NewsArticle>> = dao.observeAll()

    fun observeByCategory(category: Category): Flow<List<NewsArticle>> =
        if (category == Category.GENERAL) dao.observeAll() else dao.observeByCategory(category.id)

    fun observeArticle(id: String): Flow<NewsArticle?> = dao.observeById(id)

    suspend fun markRead(id: String) = dao.markRead(id)

    suspend fun toggleStarred(id: String) = dao.toggleStarred(id)

    suspend fun sync(): SyncReport {
        val settings = settingsRepository.settings.first()
        val feeds = buildFeeds(settings)
        if (feeds.isEmpty()) return SyncReport(0, emptyList())

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
                            processor.toArticle(item, feed, settings.deepSeekApiKey, fetchedAt)
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

        val threshold = settings.importanceThreshold
        val since = fetchedAt - RECENT_WINDOW_MS
        val candidates = dao.getUnnotifiedImportant(since, threshold, 5)
        return SyncReport(deduped.size, candidates)
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
    }
}
