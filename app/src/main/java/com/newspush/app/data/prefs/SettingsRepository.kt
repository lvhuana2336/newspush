package com.newspush.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.newspush.app.data.model.AppSettings
import com.newspush.app.data.model.Category
import com.newspush.app.data.source.RssFeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        val storedVersion = preferences[Keys.SCHEMA_VERSION] ?: 1
        val storedCategories = preferences[Keys.ENABLED_CATEGORIES]
        AppSettings(
            syncIntervalMinutes = preferences[Keys.SYNC_INTERVAL] ?: 30,
            notificationsEnabled = preferences[Keys.NOTIFICATIONS_ENABLED] ?: true,
            enabledCategories = when {
                storedCategories == null -> Category.selectable().map { it.id }.toSet()
                storedVersion < SCHEMA_VERSION -> storedCategories + CATEGORIES_ADDED_AFTER_V1
                else -> storedCategories
            },
            importanceThreshold = preferences[Keys.IMPORTANCE_THRESHOLD] ?: 0.45f,
            customFeeds = decodeFeeds(preferences[Keys.CUSTOM_FEEDS]),
            deepSeekApiKey = preferences[Keys.DEEPSEEK_API_KEY] ?: "",
            translateEnglishSummaries = preferences[Keys.TRANSLATE_ENGLISH] ?: false,
            pushWindowEnabled = preferences[Keys.PUSH_WINDOW_ENABLED] ?: true,
            pushWindowStartMinutes = preferences[Keys.PUSH_WINDOW_START] ?: (8 * 60),
            pushWindowEndMinutes = preferences[Keys.PUSH_WINDOW_END] ?: (22 * 60),
            blacklistKeywords = (preferences[Keys.BLACKLIST_KEYWORDS] ?: emptySet()).sorted(),
            blacklistSources = preferences[Keys.BLACKLIST_SOURCES] ?: emptySet(),
            socialApiUrl = preferences[Keys.SOCIAL_API_URL] ?: DEFAULT_SOCIAL_API_URL,
            socialInviteCode = preferences[Keys.SOCIAL_INVITE_CODE] ?: "",
            socialApiKey = preferences[Keys.SOCIAL_API_KEY] ?: "",
            socialDeviceId = preferences[Keys.SOCIAL_DEVICE_ID] ?: "",
            socialCursor = preferences[Keys.SOCIAL_CURSOR] ?: 0L,
            socialLastError = preferences[Keys.SOCIAL_LAST_ERROR] ?: ""
        )
    }

    /**
     * 把老版本存下来的分区开关补齐（新增的分区默认打开），并记录设置版本。
     */
    suspend fun migrateIfNeeded() {
        dataStore.edit { preferences ->
            val version = preferences[Keys.SCHEMA_VERSION] ?: 1
            if (version < SCHEMA_VERSION) {
                val current = preferences[Keys.ENABLED_CATEGORIES]
                if (current != null) {
                    preferences[Keys.ENABLED_CATEGORIES] = current + CATEGORIES_ADDED_AFTER_V1
                }
                preferences[Keys.SCHEMA_VERSION] = SCHEMA_VERSION
            }
        }
    }

    suspend fun setSyncInterval(minutes: Int) {
        dataStore.edit { it[Keys.SYNC_INTERVAL] = minutes.coerceIn(15, 720) }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setCategoryEnabled(categoryId: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.ENABLED_CATEGORIES]
                ?: Category.selectable().map { it.id }.toSet()
            preferences[Keys.ENABLED_CATEGORIES] = if (enabled) current + categoryId else current - categoryId
        }
    }

    suspend fun setImportanceThreshold(threshold: Float) {
        dataStore.edit { it[Keys.IMPORTANCE_THRESHOLD] = threshold.coerceIn(0f, 1f) }
    }

    suspend fun setDeepSeekApiKey(apiKey: String) {
        dataStore.edit { it[Keys.DEEPSEEK_API_KEY] = apiKey.trim() }
    }

    suspend fun setTranslateEnglishSummaries(enabled: Boolean) {
        dataStore.edit { it[Keys.TRANSLATE_ENGLISH] = enabled }
    }

    suspend fun setPushWindowEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.PUSH_WINDOW_ENABLED] = enabled }
    }

    suspend fun setPushWindowStart(minutes: Int) {
        dataStore.edit { it[Keys.PUSH_WINDOW_START] = minutes.coerceIn(0, 1439) }
    }

    suspend fun setPushWindowEnd(minutes: Int) {
        dataStore.edit { it[Keys.PUSH_WINDOW_END] = minutes.coerceIn(0, 1439) }
    }

    suspend fun addBlacklistKeyword(keyword: String) {
        val word = keyword.trim()
        if (word.isEmpty()) return
        dataStore.edit { preferences ->
            val current = preferences[Keys.BLACKLIST_KEYWORDS] ?: emptySet()
            preferences[Keys.BLACKLIST_KEYWORDS] = current + word
        }
    }

    suspend fun removeBlacklistKeyword(keyword: String) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.BLACKLIST_KEYWORDS] ?: emptySet()
            preferences[Keys.BLACKLIST_KEYWORDS] = current - keyword
        }
    }

    suspend fun setSourceBlocked(sourceName: String, blocked: Boolean) {
        val name = sourceName.trim()
        if (name.isEmpty()) return
        dataStore.edit { preferences ->
            val current = preferences[Keys.BLACKLIST_SOURCES] ?: emptySet()
            preferences[Keys.BLACKLIST_SOURCES] = if (blocked) current + name else current - name
        }
    }

    suspend fun setSocialApiUrl(url: String) {
        dataStore.edit { it[Keys.SOCIAL_API_URL] = url.trim() }
    }

    suspend fun setSocialInviteCode(code: String) {
        dataStore.edit { it[Keys.SOCIAL_INVITE_CODE] = code.trim() }
    }

    suspend fun setSocialApiKey(key: String) {
        dataStore.edit { it[Keys.SOCIAL_API_KEY] = key.trim() }
    }

    suspend fun setSocialDeviceId(id: String) {
        dataStore.edit { it[Keys.SOCIAL_DEVICE_ID] = id.trim() }
    }

    suspend fun setSocialCursor(cursor: Long) {
        dataStore.edit { it[Keys.SOCIAL_CURSOR] = cursor }
    }

    suspend fun setSocialLastError(message: String) {
        dataStore.edit { it[Keys.SOCIAL_LAST_ERROR] = message.take(300) }
    }

    suspend fun addCustomFeed(feed: RssFeed) {
        dataStore.edit { preferences ->
            val feeds = decodeFeeds(preferences[Keys.CUSTOM_FEEDS]).toMutableList()
            if (feeds.none { it.url == feed.url }) feeds.add(feed)
            preferences[Keys.CUSTOM_FEEDS] = encodeFeeds(feeds)
        }
    }

    suspend fun removeCustomFeed(url: String) {
        dataStore.edit { preferences ->
            val feeds = decodeFeeds(preferences[Keys.CUSTOM_FEEDS]).filterNot { it.url == url }
            preferences[Keys.CUSTOM_FEEDS] = encodeFeeds(feeds)
        }
    }

    private fun encodeFeeds(feeds: List<RssFeed>): String {
        val array = JSONArray()
        feeds.forEach { feed ->
            array.put(
                JSONObject().apply {
                    put("url", feed.url)
                    put("categoryId", feed.categoryId)
                    put("sourceName", feed.sourceName)
                    put("authorityWeight", feed.authorityWeight.toDouble())
                }
            )
        }
        return array.toString()
    }

    private fun decodeFeeds(json: String?): List<RssFeed> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                RssFeed(
                    url = obj.getString("url"),
                    categoryId = obj.getString("categoryId"),
                    sourceName = obj.optString("sourceName", "自定义"),
                    authorityWeight = obj.optDouble("authorityWeight", 1.0).toFloat()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private object Keys {
        val SCHEMA_VERSION = intPreferencesKey("settings_schema_version")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval_minutes")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val ENABLED_CATEGORIES = stringSetPreferencesKey("enabled_categories")
        val IMPORTANCE_THRESHOLD = floatPreferencesKey("importance_threshold")
        val CUSTOM_FEEDS = stringPreferencesKey("custom_feeds")
        val DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        val TRANSLATE_ENGLISH = booleanPreferencesKey("translate_english_summaries")
        val PUSH_WINDOW_ENABLED = booleanPreferencesKey("push_window_enabled")
        val PUSH_WINDOW_START = intPreferencesKey("push_window_start_minutes")
        val PUSH_WINDOW_END = intPreferencesKey("push_window_end_minutes")
        val BLACKLIST_KEYWORDS = stringSetPreferencesKey("blacklist_keywords")
        val BLACKLIST_SOURCES = stringSetPreferencesKey("blacklist_sources")
        val SOCIAL_API_URL = stringPreferencesKey("social_api_url")
        val SOCIAL_INVITE_CODE = stringPreferencesKey("social_invite_code")
        val SOCIAL_API_KEY = stringPreferencesKey("social_api_key")
        val SOCIAL_DEVICE_ID = stringPreferencesKey("social_device_id")
        val SOCIAL_CURSOR = longPreferencesKey("social_cursor")
        val SOCIAL_LAST_ERROR = stringPreferencesKey("social_last_error")
    }

    private companion object {
        const val DEFAULT_SOCIAL_API_URL = "https://belts-accurately-apache-fares.trycloudflare.com"
        const val SCHEMA_VERSION = 2
        val CATEGORIES_ADDED_AFTER_V1 = setOf(Category.PROGRAMMING.id)
    }
}
