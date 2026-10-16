package com.newspush.app.domain

import androidx.core.text.HtmlCompat
import com.newspush.app.data.model.Category
import com.newspush.app.data.model.NewsArticle
import com.newspush.app.data.remote.DeepSeekClient
import com.newspush.app.data.source.ParsedItem
import com.newspush.app.data.source.RssFeed
import com.newspush.app.util.md5

/**
 * 把抓到的原始条目转换成应用内的新闻实体：清洗、摘要、分类、重要度评分。
 * 摘要与分类优先走 DeepSeek，未配置 Key 或调用失败时回退到本地算法。
 */
class NewsProcessor(private val deepSeekClient: DeepSeekClient?) {
    suspend fun toArticle(
        item: ParsedItem,
        feed: RssFeed,
        apiKey: String,
        fetchedAt: Long = System.currentTimeMillis()
    ): NewsArticle {
        val title = cleanHtml(item.title)
        val rawText = item.content.ifBlank { item.description }
        val plainText = cleanHtml(rawText.ifBlank { title })

        var summary = Summarizer.summarize(plainText.ifBlank { title })
        var category = Classifier.classify("$title $plainText", Category.fromId(feed.categoryId))

        if (apiKey.isNotBlank() && deepSeekClient != null) {
            val enriched = try {
                deepSeekClient.summarizeAndClassify(apiKey, title, plainText)
            } catch (_: Exception) {
                null
            }
            enriched?.let {
                it.summary.takeIf { text -> text.isNotBlank() }?.let { text -> summary = text }
                category = it.category
            }
        }

        val importance = ImportanceScorer.score(title, plainText, feed.authorityWeight, category)
        val link = item.link.ifBlank { item.guid.orEmpty() }.ifBlank { title }
        return NewsArticle(
            id = md5(link),
            title = title,
            summary = summary,
            content = plainText,
            category = category.id,
            sourceName = feed.sourceName,
            sourceUrl = link,
            imageUrl = item.imageUrl,
            publishDate = item.pubDate ?: fetchedAt,
            importanceScore = importance,
            fetchedAt = fetchedAt
        )
    }

    private fun cleanHtml(html: String): String {
        val decoded = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        return decoded
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        fun itemId(item: ParsedItem): String =
            md5(item.link.ifBlank { item.guid.orEmpty() }.ifBlank { item.title })
    }
}
