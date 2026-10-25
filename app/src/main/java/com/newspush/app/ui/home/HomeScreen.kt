package com.newspush.app.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newspush.app.data.model.Category
import com.newspush.app.data.model.NewsArticle
import com.newspush.app.util.Haptics
import com.newspush.app.util.rememberHaptics
import com.newspush.app.util.relativeTime
import kotlinx.coroutines.launch

private data class HomeTab(
    val title: String,
    val category: Category? = null,
    val readLater: Boolean = false
)

private object NoOpHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenArticle: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel()
    val articles by viewModel.articles.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val haptics = rememberHaptics(hapticsEnabled)
    val context = LocalContext.current
    var actionArticle by remember { mutableStateOf<NewsArticle?>(null) }

    val tabs = remember {
        buildList {
            add(HomeTab("综合", Category.GENERAL))
            Category.selectable().forEach { add(HomeTab(it.displayName, it)) }
            add(HomeTab("稍后再看", readLater = true))
        }
    }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val chipListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        chipListState.animateScrollToItem(pagerState.currentPage)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("新闻推送") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CategoryChips(
                tabs = tabs,
                selectedIndex = pagerState.currentPage,
                listState = chipListState,
                onSelect = { index ->
                    if (index != pagerState.currentPage) {
                        haptics.tick()
                    }
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val tab = tabs[page]
                val pageArticles = remember(articles, tab) {
                    when {
                        tab.readLater -> articles.filter { it.isReadLater }
                        tab.category == Category.GENERAL -> articles
                        else -> articles.filter { it.category == tab.category?.id }
                    }
                }
                CategoryPage(
                    articles = pageArticles,
                    isRefreshing = isRefreshing,
                    emptyTitle = if (tab.readLater) "还没有稍后再看的新闻" else "还没有新闻",
                    onRefresh = viewModel::refresh,
                    onOpenArticle = onOpenArticle,
                    onLongPress = { actionArticle = it },
                    haptics = haptics
                )
            }
        }
    }

    actionArticle?.let { article ->
        NewsActionSheet(
            article = article,
            onDismiss = { actionArticle = null },
            onDelete = {
                haptics.reject()
                viewModel.delete(article.id)
                actionArticle = null
                Toast.makeText(context, "已移入回收站", Toast.LENGTH_SHORT).show()
            },
            onFavorite = {
                haptics.confirm()
                viewModel.toggleStarred(article.id)
                actionArticle = null
                Toast.makeText(
                    context,
                    if (article.isStarred) "已取消收藏" else "已收藏",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onShare = {
                haptics.tick()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("原文链接", article.sourceUrl))
                actionArticle = null
                Toast.makeText(context, "原文链接已复制", Toast.LENGTH_SHORT).show()
            },
            onReadLater = {
                haptics.tick()
                viewModel.setReadLater(article.id, !article.isReadLater)
                val message = if (article.isReadLater) "已移出稍后再看" else "已加入稍后再看"
                actionArticle = null
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun CategoryChips(
    tabs: List<HomeTab>,
    selectedIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(tabs) { index, tab ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                label = { Text(tab.title) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPage(
    articles: List<NewsArticle>,
    isRefreshing: Boolean,
    emptyTitle: String,
    onRefresh: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onLongPress: (NewsArticle) -> Unit,
    haptics: Haptics
) {
    var seenIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var newIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(articles) {
        val ids = articles.map { it.id }
        if (!initialized) {
            if (ids.isNotEmpty()) {
                seenIds = ids.toSet()
                initialized = true
            }
        } else {
            val fresh = ids.filter { it !in seenIds }.take(5).toSet()
            if (fresh.isNotEmpty()) {
                seenIds = seenIds + fresh
                newIds = fresh
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (articles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(isRefreshing, emptyTitle)
                    }
                }
            } else {
                items(articles, key = { it.id }) { article ->
                    NewsCard(
                        article = article,
                        animate = article.id in newIds,
                        onClick = { onOpenArticle(article.id) },
                        onLongPress = { onLongPress(article) },
                        haptics = haptics
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NewsCard(
    article: NewsArticle,
    animate: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    haptics: Haptics
) {
    val background = if (article.isRead) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    val displaySummary = article.translatedSummary ?: article.summary
    val progress = remember(article.id, animate) {
        Animatable(if (animate) 0f else 1f)
    }
    val pressScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(article.id, animate) {
        if (animate) {
            progress.animateTo(1f, animationSpec = tween(420))
        }
    }

    CompositionLocalProvider(LocalHapticFeedback provides NoOpHapticFeedback) {
        Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.longPress()
                    scope.launch {
                        pressScale.animateTo(0.94f, tween(110))
                        pressScale.animateTo(1f, tween(150))
                        onLongPress()
                    }
                }
            )
            .graphicsLayer {
                val p = progress.value
                translationX = -72.dp.toPx() * (1f - p)
                scaleY = 0.08f + 0.92f * p
                alpha = p
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            },
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (displaySummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = displaySummary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                CategoryBadge(article.category)
                if (article.platform == "bilibili") {
                    Spacer(modifier = Modifier.width(6.dp))
                    PlatformBadge()
                }
                if (!article.translatedSummary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    TranslatedBadge()
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = article.sourceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                if (article.isReadLater) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "稍后再看",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (article.isStarred) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "已收藏",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (article.importanceScore >= 0.6f) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "重要",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = relativeTime(article.publishDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsActionSheet(
    article: NewsArticle,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onReadLater: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text("新闻操作", style = MaterialTheme.typography.titleMedium)
            Text(
                article.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            ActionRow(
                icon = Icons.Default.Delete,
                title = "删除",
                subtitle = "移入回收站，可在设置中找回",
                onClick = onDelete
            )
            ActionRow(
                icon = if (article.isStarred) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                title = if (article.isStarred) "取消收藏" else "收藏",
                subtitle = null,
                onClick = onFavorite
            )
            ActionRow(
                icon = Icons.Default.Share,
                title = "分享",
                subtitle = "复制原文链接",
                onClick = onShare
            )
            ActionRow(
                icon = Icons.Default.Schedule,
                title = if (article.isReadLater) "移出稍后再看" else "稍后再看",
                subtitle = null,
                onClick = onReadLater
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryBadge(categoryId: String) {
    val category = Category.fromId(categoryId)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = category.displayName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun TranslatedBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = "译",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun PlatformBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Text(
            text = "B站",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun EmptyState(isRefreshing: Boolean, title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isRefreshing) "正在抓取新闻…" else title,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isRefreshing) "请稍候" else "在顶部下拉即可刷新，或检查网络与订阅源",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
