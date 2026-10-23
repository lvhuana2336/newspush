package com.newspush.app.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.newspush.app.MainActivity
import com.newspush.app.data.model.Category
import com.newspush.app.data.source.DefaultFeeds
import com.newspush.app.util.formatClockMinutes
import com.newspush.app.util.decodeSampledBitmap
import java.io.File

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
    var socialUrl by remember(settings.socialApiUrl) { mutableStateOf(settings.socialApiUrl) }
    var socialInvite by remember(settings.socialInviteCode) { mutableStateOf(settings.socialInviteCode) }
    val context = LocalContext.current
    val iconBitmap = remember(settings.customIconPath) {
        decodeSampledBitmap(settings.customIconPath, 512, 512)
    }
    val backgroundBitmap = remember(settings.customBackgroundPath) {
        decodeSampledBitmap(settings.customBackgroundPath, 1600, 1600)
    }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            copyImageToFiles(context, uri, "icon")?.let(viewModel::setCustomIconPath)
        }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            copyImageToFiles(context, uri, "background")?.let(viewModel::setCustomBackgroundPath)
        }
    }

    val knownSources = (
        DefaultFeeds.list.map { it.sourceName } +
            settings.customFeeds.map { it.sourceName } +
            settings.blacklistSources
        ).distinct().sorted()

    Scaffold(
        containerColor = Color.Transparent,
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

            Text("外观", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "system" to "跟随系统",
                    "light" to "浅色",
                    "dark" to "深色"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(label) }
                    )
                }
            }

            Text("应用图标", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { iconPicker.launch("image/*") }) {
                            Text("选择图片")
                        }
                        if (settings.customIconPath.isNotBlank()) {
                            TextButton(onClick = { viewModel.setCustomIconPath("") }) {
                                Text("恢复默认")
                            }
                        }
                    }
                    if (iconBitmap != null) {
                        TextButton(onClick = { pinCustomShortcut(context, iconBitmap) }) {
                            Text("创建桌面图标")
                        }
                    }
                }
            }
            Text(
                "Android 不允许应用在运行时替换桌面启动器图标。自定义图标会用于开屏和界面，可另外创建一个带该图标的桌面快捷方式。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("应用背景", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (backgroundBitmap != null) {
                    Image(
                        bitmap = backgroundBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { backgroundPicker.launch("image/*") }) {
                        Text("选择图片")
                    }
                    if (settings.customBackgroundPath.isNotBlank()) {
                        TextButton(onClick = { viewModel.setCustomBackgroundPath("") }) {
                            Text("恢复默认")
                        }
                    }
                }
            }
            Text(
                "背景图片会以半透明遮罩覆盖，保证文字可读性。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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

            Text("B 站社交源", style = MaterialTheme.typography.titleMedium)
            Text(
                "填写服务器地址和邀请码；首次连接会自动注册本机并领取设备 Key。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = socialUrl,
                onValueChange = { socialUrl = it },
                label = { Text("服务器地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = socialInvite,
                onValueChange = { socialInvite = it },
                label = { Text("邀请码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.connectSocial(socialUrl, socialInvite) },
                enabled = !isRefreshing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (settings.socialApiKey.isNotBlank()) "重新连接并同步" else "连接并同步")
            }
            Text(
                text = if (settings.socialApiKey.isNotBlank()) "状态：已连接" else "状态：未连接",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settings.socialLastError.isNotBlank()) {
                Text(
                    text = "错误：" + settings.socialLastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                "当前后端使用临时 Cloudflare 隧道；隧道重启后地址会变化，需要在这里更新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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

private fun copyImageToFiles(context: Context, uri: Uri, prefix: String): String? {
    return try {
        val dir = File(context.filesDir, "custom").apply { mkdirs() }
        val file = File(dir, prefix + "-" + System.currentTimeMillis() + ".img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun pinCustomShortcut(context: Context, bitmap: Bitmap) {
    val shortcut = ShortcutInfoCompat.Builder(context, "custom-news-icon")
        .setShortLabel("新闻推送")
        .setIcon(IconCompat.createWithAdaptiveBitmap(bitmap))
        .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
        .build()
    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
}
