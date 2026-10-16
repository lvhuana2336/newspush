package com.newspush.app.data.source

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal object DateParser {
    private val patterns = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    fun parse(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
        val utcPattern = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        SimpleDateFormat(utcPattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.runCatching { parse(cleaned)?.time }.getOrNull()?.let { return it }
        for (pattern in patterns.filter { it != utcPattern }) {
            SimpleDateFormat(pattern, Locale.US)
                .runCatching { parse(cleaned)?.time }
                .getOrNull()
                ?.let { return it }
        }
        return null
    }
}
