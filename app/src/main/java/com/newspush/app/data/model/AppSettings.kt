package com.newspush.app.data.model

import com.newspush.app.data.source.RssFeed

data class AppSettings(
    val syncIntervalMinutes: Int = 30,
    val notificationsEnabled: Boolean = true,
    val pushWindowEnabled: Boolean = true,
    val pushWindowStartMinutes: Int = 8 * 60,
    val pushWindowEndMinutes: Int = 22 * 60,
    val enabledCategories: Set<String> = Category.selectable().map { it.id }.toSet(),
    val importanceThreshold: Float = 0.45f,
    val customFeeds: List<RssFeed> = emptyList(),
    val deepSeekApiKey: String = "",
    val translateEnglishSummaries: Boolean = false,
    val blacklistKeywords: List<String> = emptyList(),
    val blacklistSources: Set<String> = emptySet(),
    val socialApiUrl: String = "https://belts-accurately-apache-fares.trycloudflare.com",
    val socialInviteCode: String = "",
    val socialApiKey: String = "",
    val socialDeviceId: String = "",
    val socialCursor: Long = 0,
    val socialLastError: String = "",
    val themeMode: String = "system",
    val customIconPath: String = "",
    val customBackgroundPath: String = "",
    val hapticsEnabled: Boolean = true
)
