package com.newspush.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newspush.app.NewsPushApp
import com.newspush.app.data.model.NewsArticle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as NewsPushApp).container

    /**
     * 一次取出全部新闻，由界面按分区过滤。
     * 这样左右滑动时每一页都有内容，不会在切换动画中闪出别的分区。
     */
    val articles: StateFlow<List<NewsArticle>> = container.repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val hapticsEnabled: StateFlow<Boolean> = container.settingsRepository.settings
        .map { it.hapticsEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun refresh() {
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

    fun delete(id: String) {
        viewModelScope.launch { container.repository.markDeleted(id) }
    }

    fun toggleStarred(id: String) {
        viewModelScope.launch { container.repository.toggleStarred(id) }
    }

    fun setReadLater(id: String, readLater: Boolean) {
        viewModelScope.launch { container.repository.setReadLater(id, readLater) }
    }
}
