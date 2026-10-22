package com.newspush.app

import android.content.Context
import com.newspush.app.data.local.AppDatabase
import com.newspush.app.data.prefs.SettingsRepository
import com.newspush.app.data.remote.DeepSeekClient
import com.newspush.app.data.remote.SocialApiClient
import com.newspush.app.data.repository.NewsRepository
import com.newspush.app.data.source.NewsFetcher
import com.newspush.app.domain.NewsProcessor
import com.newspush.app.work.NotificationHelper
import com.newspush.app.work.SyncRunner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 简单的手写依赖容器，避免引入额外框架。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database = AppDatabase.getInstance(appContext)

    val dao = database.newsDao()
    val settingsRepository = SettingsRepository(appContext)
    val notificationHelper = NotificationHelper(appContext)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val fetcher = NewsFetcher(okHttpClient)
    private val deepSeekHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val deepSeekClient = DeepSeekClient(deepSeekHttpClient)
    private val processor = NewsProcessor(deepSeekClient)
    private val socialApiClient = SocialApiClient(okHttpClient)
    val repository = NewsRepository(dao, settingsRepository, fetcher, processor, socialApiClient)
    val syncRunner = SyncRunner(repository, settingsRepository, dao, notificationHelper)
}
