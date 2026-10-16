package com.newspush.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newspush.app.NewsPushApp
import com.newspush.app.data.model.Category
import com.newspush.app.data.model.NewsArticle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as NewsPushApp).container
    private val repository = container.repository

    private val _selected = MutableStateFlow(Category.GENERAL)
    val selected: StateFlow<Category> = _selected.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val articles: StateFlow<List<NewsArticle>> = _selected
        .flatMapLatest { repository.observeByCategory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun select(category: Category) {
        _selected.value = category
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                container.syncRunner.run()
            } catch (_: Exception) {
                // 忽略单次刷新失败，下次定时任务会重试
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
