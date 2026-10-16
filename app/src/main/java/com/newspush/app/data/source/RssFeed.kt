package com.newspush.app.data.source

data class RssFeed(
    val url: String,
    val categoryId: String,
    val sourceName: String,
    val authorityWeight: Float = 1.0f
)
