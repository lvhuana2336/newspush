package com.newspush.app.domain

import com.newspush.app.data.model.Category

/**
 * 基于关键词命中数的新闻分类。命中不到关键词时回退到订阅源本身的分类。
 */
object Classifier {
    fun classify(text: String, fallback: Category): Category {
        val scored = Category.selectable().map { category ->
            val hits = category.keywords.count { keyword -> text.contains(keyword, ignoreCase = true) }
            category to hits
        }
        val best = scored.maxByOrNull { it.second } ?: return fallback
        return if (best.second >= 1) best.first else fallback
    }
}
