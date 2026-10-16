package com.newspush.app.data.source

/**
 * 默认 RSS 订阅源。源站可能随时调整地址，应用在设置页也支持增删自定义源。
 *
 * 这里的地址都在 2026-10 实测过：能拉到当天或前一天的内容。
 * 备注：人民网 politics.xml 的 RSS 已停更在 2025-06，新浪科技停在 2018，故未采用；
 * 36氪/虎嗅/果壳的旧地址已失效或返回空，也在设置页可自行加回。
 */
object DefaultFeeds {
    val list: List<RssFeed> = listOf(
        RssFeed("https://www.chinanews.com.cn/rss/scroll-news.xml", "domestic", "中国新闻网·滚动", 1.2f),
        RssFeed("https://www.chinanews.com.cn/rss/importnews.xml", "international", "中国新闻网·国际", 1.2f),
        RssFeed("https://www.ithome.com/rss/", "tech", "IT之家", 1.0f),
        RssFeed("https://www.solidot.org/index.rss", "tech", "Solidot", 0.9f),
        RssFeed("https://sspai.com/feed", "tech", "少数派", 0.9f),
        RssFeed("https://feed.cnblogs.com/blog/sitehome/rss", "programming", "博客园", 1.0f),
        RssFeed("https://www.oschina.net/news/rss", "programming", "开源中国", 1.0f),
        RssFeed("https://www.infoq.cn/feed", "programming", "InfoQ 中文", 1.1f),
        RssFeed("https://juejin.cn/rss", "programming", "掘金", 0.9f),
        RssFeed("https://tech.meituan.com/feed/", "programming", "美团技术团队", 1.1f),
        RssFeed("https://www.ruanyifeng.com/blog/atom.xml", "programming", "阮一峰的网络日志", 1.0f),
        RssFeed("https://hnrss.org/frontpage", "programming", "Hacker News", 1.0f),
        RssFeed("https://dev.to/feed", "programming", "DEV Community", 0.9f)
    )
}
