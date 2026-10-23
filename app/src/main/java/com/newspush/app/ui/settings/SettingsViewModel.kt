package com.newspush.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newspush.app.NewsPushApp
import com.newspush.app.data.model.AppSettings
import com.newspush.app.data.model.Category
import com.newspush.app.data.model.NewsArticle
import com.newspush.app.data.source.RssFeed
import com.newspush.app.work.WorkerScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val container = (application as NewsPushApp).container

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val trashItems: StateFlow<List<NewsArticle>> = container.repository.observeDeleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refreshNews() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                container.syncRunner.run()
            } catch (_: Exception) {
                // 单次刷新失败不影响后续定时任务
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun connectSocial(url: String, inviteCode: String) {
        viewModelScope.launch {
            container.settingsRepository.setSocialApiUrl(url)
            container.settingsRepository.setSocialInviteCode(inviteCode)
            container.settingsRepository.setSocialApiKey("")
            container.settingsRepository.setSocialCursor(0)
            container.settingsRepository.setSocialLastError("")
            refreshNews()
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setSyncInterval(minutes: Int) {
        viewModelScope.launch {
            container.settingsRepository.setSyncInterval(minutes)
            WorkerScheduler.schedule(app)
        }
    }

    fun setImportanceThreshold(value: Float) {
        viewModelScope.launch { container.settingsRepository.setImportanceThreshold(value) }
    }

    fun setDeepSeekApiKey(apiKey: String) {
        viewModelScope.launch { container.settingsRepository.setDeepSeekApiKey(apiKey) }
    }

    fun setTranslateEnglishSummaries(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setTranslateEnglishSummaries(enabled) }
    }

    fun setPushWindowEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setPushWindowEnabled(enabled) }
    }

    fun setPushWindowStart(minutes: Int) {
        viewModelScope.launch { container.settingsRepository.setPushWindowStart(minutes) }
    }

    fun setPushWindowEnd(minutes: Int) {
        viewModelScope.launch { container.settingsRepository.setPushWindowEnd(minutes) }
    }

    fun addBlacklistKeyword(keyword: String) {
        viewModelScope.launch { container.settingsRepository.addBlacklistKeyword(keyword) }
    }

    fun removeBlacklistKeyword(keyword: String) {
        viewModelScope.launch { container.settingsRepository.removeBlacklistKeyword(keyword) }
    }

    fun toggleSourceBlocked(sourceName: String) {
        viewModelScope.launch {
            val blocked = sourceName in settings.value.blacklistSources
            container.settingsRepository.setSourceBlocked(sourceName, !blocked)
        }
    }

    fun toggleCategory(category: Category) {
        viewModelScope.launch {
            val currentlyEnabled = category.id in settings.value.enabledCategories
            container.settingsRepository.setCategoryEnabled(category.id, !currentlyEnabled)
            WorkerScheduler.schedule(app)
        }
    }

    fun addFeed(name: String, url: String) {
        if (name.isBlank() || !url.startsWith("http")) return
        viewModelScope.launch {
            container.settingsRepository.addCustomFeed(
                RssFeed(url = url, categoryId = Category.GENERAL.id, sourceName = name)
            )
            WorkerScheduler.schedule(app)
        }
    }

    fun removeFeed(url: String) {
        viewModelScope.launch {
            container.settingsRepository.removeCustomFeed(url)
            WorkerScheduler.schedule(app)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { container.settingsRepository.setThemeMode(mode) }
    }

    fun setCustomIconPath(path: String) {
        viewModelScope.launch { container.settingsRepository.setCustomIconPath(path) }
    }

    fun setCustomBackgroundPath(path: String) {
        viewModelScope.launch { container.settingsRepository.setCustomBackgroundPath(path) }
    }

    fun restore(id: String) {
        viewModelScope.launch { container.repository.restore(id) }
    }
}
