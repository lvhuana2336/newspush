package com.newspush.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_articles")
data class NewsArticle(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val translatedSummary: String? = null,
    val content: String,
    val category: String,
    val sourceName: String,
    val sourceUrl: String,
    val imageUrl: String? = null,
    val publishDate: Long,
    val importanceScore: Float,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val isNotified: Boolean = false,
    val fetchedAt: Long
)
