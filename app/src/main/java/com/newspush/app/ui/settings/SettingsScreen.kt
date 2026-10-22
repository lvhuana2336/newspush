package com.newspush.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newspush.app.data.model.Category
import com.newspush.app.data.source.DefaultFeeds
import com.newspush.app.util.formatClockMinutes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var feedName by remember { mutableStateOf("") }
    var feedUrl by remember { mutableStateOf("") }
    var threshold by remember(settings.importanceThreshold) { mutableStateOf(settings.importanceThreshold) }
    var apiKey by remember(settings.deepSeekApiKey) { mutableStateOf(settings.deepSeekApiKey) }
    var blockedWord by remember { mutableStateOf("") }

    val knownSources = (
        DefaultFeeds.list.map { it.sourceName } +
            settings.customFeeds.map { it.sourceName } +
            settings.blacklistSources
        ).distinct().sorted()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Button(
                onClick = viewModel::refreshNews,
                enabled = !isRefreshing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在刷新…")
                } else {
                    Text("立即刷新新闻")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("推送通知", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "抓取到重要新闻时提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("推送时段", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "只在设定时段内推送；时段外抓到的新闻会先存着，等到时段内再推",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.pushWindowEnabled,
                    onCheckedChange = viewModel::setPushWindowEnabled
                )
            }
            if (settings.pushWindowEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField(
                        label = "开始",
                        minutes = settings.pushWindowStartMinutes,
                        modifier = Modifier.weight(1f),
                        onChange = viewModel::setPushWindowStart
                    )
                    TimeField(
                        label = "结束",
                        minutes = settings.pushWindowEndMinutes,
                        modifier = Modifier.weight(1f),
                        onChange = viewModel::setPushWindowEnd
                    )
                }
            }

            Text("抓取间隔", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120).forEach { minutes ->
                    FilterChip(
                        selected = settings.syncIntervalMinutes == minutes,
                        onClick = { viewModel.setSyncInterval(minutes) },
                        label = { Text(intervalLabel(minutes)) }
                    )
                }
            }

            Text("重要度阈值", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                onValueChangeFinished = { viewModel.setImportanceThreshold(threshold) },
                valueRange = 0f..1f
            )
            Text(
                "当前 ${(threshold * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("DeepSeek API", style = MaterialTheme.typography.titleMedium)
            Text(
                "用于新闻摘要与分类；留空则使用本地算法兜底。密钥仅保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.setDeepSeekApiKey(apiKey) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存 API Key")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("翻译英文摘要", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.deepSeekApiKey.isBlank()) {
                            "需要先保存 DeepSeek API Key"
                        } else {
                            "把英文新闻的摘要翻译成中文（对新抓取的新闻生效）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.translateEnglishSummaries,
                    onCheckedChange = viewModel::setTranslateEnglishSummaries,
                    enabled = settings.deepSeekApiKey.isNotBlank()
                )
            }

            Text("新闻分区", style = MaterialTheme.typography.titleMedium)
            Category.selectable().chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { category ->
                        FilterChip(
                            selected = category.id in settings.enabledCategories,
                            onClick = { viewModel.toggleCategory(category) },
                            label = { Text(category.displayName) }
                        )
                    }
                }
            }

            Text("黑名单", style = MaterialTheme.typography.titleMedium)
            Text(
                "屏蔽词命中标题、摘要或正文即过滤；来源被屏蔽后该源的内容不再入库，已入库的也会清掉（收藏除外）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = blockedWord,
                onValueChange = { blockedWord = it },
                label = { Text("屏蔽词") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    viewModel.addBlacklistKeyword(blockedWord)
                    blockedWord = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("添加屏蔽词")
            }
            if (settings.blacklistKeywords.isEmpty()) {
                Text(
                    "还没有屏蔽词",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                settings.blacklistKeywords.forEach { word ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            word,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.removeBlacklistKeyword(word) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除屏蔽词")
                        }
                    }
                }
            }

            Text("屏蔽来源", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                knownSources.forEach { name ->
                    FilterChip(
                        selected = name in settings.blacklistSources,
                        onClick = { viewModel.toggleSourceBlocked(name) },
                        label = { Text(name) }
                    )
                }
            }

            Text("自定义 RSS 订阅源", style = MaterialTheme.typography.titleMedium)
            settings.customFeeds.forEach { feed ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(feed.sourceName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            feed.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { viewModel.removeFeed(feed.url) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }

            OutlinedTextField(
                value = feedName,
                onValueChange = { feedName = it },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = feedUrl,
                onValueChange = { feedUrl = it },
                label = { Text("RSS 地址") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    viewModel.addFeed(feedName.trim(), feedUrl.trim())
                    feedName = ""
                    feedUrl = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("添加订阅源")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    minutes: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { showDialog = true }, modifier = modifier) {
        Text("$label ${formatClockMinutes(minutes)}")
    }

    if (showDialog) {
        val state = rememberTimePickerState(
            initialHour = minutes / 60,
            initialMinute = minutes % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = { TimeInput(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(state.hour * 60 + state.minute)
                        showDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("取消") }
            }
        )
    }
}

private fun intervalLabel(minutes: Int): String = when (minutes) {
    15 -> "15 分钟"
    30 -> "30 分钟"
    60 -> "1 小时"
    120 -> "2 小时"
    else -> "$minutes 分钟"
}
