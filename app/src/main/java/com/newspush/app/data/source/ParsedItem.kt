package com.newspush.app.data.source

data class ParsedItem(
    val guid: String?,
    val title: String,
    val link: String,
    val description: String,
    val content: String,
    val pubDate: Long?,
    val imageUrl: String?
)
