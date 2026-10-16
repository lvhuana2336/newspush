package com.newspush.app.domain

/**
 * 轻量抽取式摘要：按词频 + 句首位置给句子打分，选取若干关键句。
 * 全部在本地完成，无需联网或 API Key。
 */
object Summarizer {
    fun summarize(text: String, maxSentences: Int = 3, maxLength: Int = 160): String {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return ""
        if (cleaned.length <= maxLength) return cleaned

        val sentences = splitSentences(cleaned)
        if (sentences.isEmpty()) return cleaned.take(maxLength)

        if (sentences.size <= maxSentences) {
            val joined = sentences.joinToString("")
            return if (joined.length > maxLength) joined.take(maxLength) + "…" else joined
        }

        val frequency = tokenize(cleaned).groupingBy { it }.eachCount()
        val maxFrequency = (frequency.values.maxOrNull() ?: 0).coerceAtLeast(1)
        val scores = sentences.map { sentence ->
            val tokens = tokenize(sentence)
            if (tokens.isEmpty()) {
                0.0
            } else {
                tokens.sumOf { (frequency[it] ?: 0).toDouble() } / tokens.size / maxFrequency
            }
        }

        val ranked = sentences.indices.sortedByDescending { index ->
            scores[index] + if (index == 0) 0.12 else 0.0
        }
        val picked = ranked.take(maxSentences).sorted()
        val joined = picked.joinToString("") { sentences[it] }
        return if (joined.length > maxLength) joined.take(maxLength) + "…" else joined
    }

    private fun splitSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val builder = StringBuilder()
        for (ch in text) {
            builder.append(ch)
            if (ch in "。！？!?；;\n") {
                val sentence = builder.toString().trim()
                if (sentence.isNotEmpty()) result.add(sentence)
                builder.clear()
            }
        }
        if (builder.isNotBlank()) result.add(builder.toString().trim())
        return result
    }

    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        Regex("[a-zA-Z0-9]+").findAll(text).forEach { match ->
            tokens.add(match.value.lowercase())
        }
        val cjk = text.filter { it in '\u4e00'..'\u9fff' }
        for (i in 0 until cjk.length - 1) {
            tokens.add(cjk.substring(i, i + 2))
        }
        return tokens
    }
}
