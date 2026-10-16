package com.newspush.app.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.newspush.app.NewsPushApp
import com.newspush.app.data.model.NewsArticle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val repository = (application as NewsPushApp).container.repository
    private val articleId: String = savedStateHandle.get<String>("articleId").orEmpty()

    val article: StateFlow<NewsArticle?> = repository.observeArticle(articleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (articleId.isNotEmpty()) {
            viewModelScope.launch { repository.markRead(articleId) }
        }
    }

    fun toggleStarred() {
        if (articleId.isNotEmpty()) {
            viewModelScope.launch { repository.toggleStarred(articleId) }
        }
    }
}
