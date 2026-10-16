package com.newspush.app.domain

import com.newspush.app.data.model.Category

/**
 * 粗略的重要度评分（0..1）。阈值可在设置中调整，属于启发式规则，
 * 后续可替换为模型打分。
 */
object ImportanceScorer {
    private val strongWords = listOf(
        "突发", "紧急", "重大", "重磅", "快讯", "首次", "历史", "突破", "官方", "发布会",
        "宣布", "暴跌", "暴涨", "疫情", "地震", "战争", "发射", "召回", "致癌", "死亡",
        "事故", "停火", "制裁", "降息", "加息"
    )

    fun score(title: String, text: String, sourceWeight: Float, category: Category): Float {
        var score = 0.25f
        score += sourceWeight.coerceIn(0.5f, 1.5f) * 0.12f

        val head = title + text.take(80)
        val strongHits = strongWords.count { head.contains(it) }
        score += strongHits.coerceAtMost(3) * 0.12f

        if (title.length in 10..42) score += 0.05f
        return score.coerceIn(0f, 1f)
    }
}
