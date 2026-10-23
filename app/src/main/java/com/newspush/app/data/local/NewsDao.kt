package com.newspush.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.newspush.app.data.model.NewsArticle
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    @Upsert
    suspend fun upsertAll(articles: List<NewsArticle>)

    @Query("SELECT * FROM news_articles WHERE isDeleted = 0 ORDER BY publishDate DESC, importanceScore DESC")
    fun observeAll(): Flow<List<NewsArticle>>

    @Query(
        "SELECT * FROM news_articles WHERE isDeleted = 0 AND category = :category " +
            "ORDER BY publishDate DESC, importanceScore DESC"
    )
    fun observeByCategory(category: String): Flow<List<NewsArticle>>

    @Query(
        "SELECT * FROM news_articles WHERE isDeleted = 0 AND isReadLater = 1 " +
            "ORDER BY publishDate DESC, importanceScore DESC"
    )
    fun observeReadLater(): Flow<List<NewsArticle>>

    @Query("SELECT * FROM news_articles WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<NewsArticle>>

    @Query("SELECT * FROM news_articles WHERE id = :id")
    fun observeById(id: String): Flow<NewsArticle?>

    @Query("SELECT COUNT(*) FROM news_articles")
    suspend fun count(): Int

    @Query("SELECT id FROM news_articles")
    suspend fun getAllIds(): List<String>

    @Query("UPDATE news_articles SET isRead = 1, isReadLater = 0 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE news_articles SET isStarred = CASE isStarred WHEN 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleStarred(id: String)

    @Query("UPDATE news_articles SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: Long)

    @Query("UPDATE news_articles SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("UPDATE news_articles SET isReadLater = :readLater WHERE id = :id")
    suspend fun setReadLater(id: String, readLater: Boolean)

    @Query(
        "SELECT * FROM news_articles WHERE isNotified = 0 AND isDeleted = 0 AND importanceScore >= :threshold " +
            "AND publishDate >= :since ORDER BY importanceScore DESC, publishDate DESC LIMIT :limit"
    )
    suspend fun getUnnotifiedImportant(since: Long, threshold: Float, limit: Int): List<NewsArticle>

    @Query("UPDATE news_articles SET isNotified = 1 WHERE id IN (:ids)")
    suspend fun markNotified(ids: List<String>)

    @Query("DELETE FROM news_articles WHERE fetchedAt < :cutoff AND isStarred = 0 AND isDeleted = 0")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM news_articles WHERE publishDate < :cutoff AND isStarred = 0 AND isDeleted = 0")
    suspend fun deletePublishedBefore(cutoff: Long)

    @Query(
        "SELECT id FROM news_articles WHERE isStarred = 0 AND isDeleted = 0 " +
            "AND (title LIKE :pattern OR summary LIKE :pattern OR content LIKE :pattern)"
    )
    suspend fun findIdsMatching(pattern: String): List<String>

    @Query("DELETE FROM news_articles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM news_articles WHERE isStarred = 0 AND isDeleted = 0 AND sourceName IN (:sourceNames)")
    suspend fun deleteBySources(sourceNames: List<String>)
}
