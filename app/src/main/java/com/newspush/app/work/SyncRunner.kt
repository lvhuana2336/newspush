package com.newspush.app.work

import com.newspush.app.data.local.NewsDao
import com.newspush.app.data.prefs.SettingsRepository
import com.newspush.app.data.repository.NewsRepository
import com.newspush.app.util.isWithinPushWindow
import kotlinx.coroutines.flow.first
import java.time.LocalTime

data class SyncResult(val fetchedCount: Int, val notifiedCount: Int)

/**
 * 统一的同步流程：抓取 -> 处理入库 -> 按阈值筛选 -> 发通知 -> 标记已通知。
 * 定时任务和手动刷新共用同一套逻辑。
 */
class SyncRunner(
    private val repository: NewsRepository,
    private val settingsRepository: SettingsRepository,
    private val dao: NewsDao,
    private val notificationHelper: NotificationHelper
) {
    suspend fun run(): SyncResult {
        val settings = settingsRepository.settings.first()
        val report = repository.sync()
        val candidates = report.candidates

        if (!settings.notificationsEnabled || candidates.isEmpty()) {
            return SyncResult(report.fetchedCount, 0)
        }

        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val inWindow = isWithinPushWindow(
            nowMinutes = nowMinutes,
            startMinutes = settings.pushWindowStartMinutes,
            endMinutes = settings.pushWindowEndMinutes,
            enabled = settings.pushWindowEnabled
        )
        if (!inWindow) {
            // 时段外：保持未通知状态，等时段内的下一次同步再推送
            return SyncResult(report.fetchedCount, 0)
        }

        notificationHelper.postNewsDigest(candidates)
        dao.markNotified(candidates.map { it.id })
        return SyncResult(report.fetchedCount, candidates.size)
    }
}
