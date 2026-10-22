package com.newspush.app.data.model

/**
 * 新闻分区。GENERAL 表示“全部 / 综合”，不参与关键词分类，仅作为兜底分类。
 */
enum class Category(
    val id: String,
    val displayName: String,
    val keywords: List<String>
) {
    GENERAL("general", "综合", emptyList()),
    DOMESTIC(
        "domestic", "国内",
        listOf("中国", "国内", "北京", "国务院", "中央", "全国", "习近平", "政府", "省份", "政协", "人大")
    ),
    INTERNATIONAL(
        "international", "国际",
        listOf("国际", "美国", "俄罗斯", "欧盟", "联合国", "海外", "全球", "世界", "白宫", "北约", "中东", "乌克兰")
    ),
    FINANCE(
        "finance", "财经",
        listOf("财经", "股市", "A股", "经济", "央行", "人民币", "美元", "GDP", "金融", "基金", "汇率", "降息", "通胀", "房地产")
    ),
    TECH(
        "tech", "科技",
        listOf("科技", "人工智能", "AI", "芯片", "手机", "互联网", "软件", "华为", "苹果", "特斯拉", "机器人", "5G", "大模型", "半导体")
    ),
    PROGRAMMING(
        "programming", "编程",
        listOf(
            "编程", "代码", "程序员", "开发者", "开源", "算法", "架构", "前端", "后端",
            "数据库", "编译", "框架", "重构", "调试", "部署", "微服务", "云原生", "接口",
            "GitHub", "Python", "Java", "Kotlin", "JavaScript", "TypeScript", "Golang",
            "Rust", "Linux", "Docker", "Kubernetes", "API", "SDK"
        )
    ),
    SPORTS(
        "sports", "体育",
        listOf("体育", "足球", "篮球", "NBA", "中超", "世界杯", "奥运会", "比赛", "冠军", "联赛")
    ),
    ENTERTAINMENT(
        "entertainment", "娱乐",
        listOf("娱乐", "电影", "电视剧", "明星", "综艺", "音乐", "演出", "票房", "导演", "演员")
    ),
    HEALTH(
        "health", "健康",
        listOf("健康", "医疗", "疾病", "疫苗", "医院", "医生", "疫情", "养生", "癌症", "病毒")
    ),
    MILITARY(
        "military", "军事",
        listOf("军事", "国防", "军队", "导弹", "航母", "战争", "演习", "武器", "战机", "海军")
    ),
    SCIENCE(
        "science", "科学",
        listOf("科学", "研究", "太空", "航天", "NASA", "天文", "物理", "化学", "生物", "考古")
    ),
    SOCIETY(
        "society", "社会",
        listOf("社会", "民生", "教育", "高考", "就业", "房价", "法律", "事故", "天气", "地震", "台风")
    );

    companion object {
        fun fromId(id: String): Category = entries.firstOrNull { it.id == id } ?: GENERAL

        fun fromDisplayName(name: String): Category =
            entries.firstOrNull { it.displayName == name } ?: GENERAL

        fun selectable(): List<Category> = entries.filter { it != GENERAL }
    }
}
