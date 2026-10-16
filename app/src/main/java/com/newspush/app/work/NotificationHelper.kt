package com.newspush.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.newspush.app.MainActivity
import com.newspush.app.R
import com.newspush.app.data.model.Category
import com.newspush.app.data.model.NewsArticle

class NotificationHelper(private val context: Context) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "重要新闻",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "抓取到重要新闻时提醒"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun postNewsDigest(articles: List<NewsArticle>) {
        if (articles.isEmpty()) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannel()
        // 注意：NotificationCompat.Style 自带成员方法 apply(...)，会遮蔽 kotlin 的 apply 扩展函数
        val style = NotificationCompat.InboxStyle()
        articles.take(8).forEach { article ->
            val category = Category.fromId(article.category).displayName
            style.addLine("【$category】${article.title}")
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_news)
            .setContentTitle("抓到 ${articles.size} 条重要新闻")
            .setContentText(articles.first().title)
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "news_digest"
        private const val NOTIFICATION_ID = 1001
    }
}
