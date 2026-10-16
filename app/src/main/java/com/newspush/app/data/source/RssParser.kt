package com.newspush.app.data.source

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * 解析 RSS 2.0 与 Atom 两种格式，兼容常见的中文源。
 */
class RssParser {
    fun parse(xml: String): List<ParsedItem> {
        if (xml.isBlank()) return emptyList()
        val result = mutableListOf<ParsedItem>()
        val parser = Xml.newPullParser()
        try {
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            var builder: ItemBuilder? = null
            var field: String? = null
            var text = StringBuilder()

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name
                        when {
                            tag == "item" || tag == "entry" -> {
                                builder = ItemBuilder()
                                field = null
                                text = StringBuilder()
                            }

                            builder == null -> Unit

                            tag == "link" -> {
                                builder!!.linkRel = parser.getAttributeValue(null, "rel")
                                builder!!.linkHref = parser.getAttributeValue(null, "href")
                                field = "link"
                                text = StringBuilder()
                            }

                            tag in IMAGE_TAGS -> {
                                val url = parser.getAttributeValue(null, "url")
                                val type = parser.getAttributeValue(null, "type").orEmpty()
                                if (url != null &&
                                    (type.startsWith("image") || builder!!.imageUrl == null)
                                ) {
                                    builder!!.imageUrl = url
                                }
                            }

                            tag == "content" -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (url != null && builder!!.imageUrl == null) {
                                    builder!!.imageUrl = url
                                }
                                field = "content"
                                text = StringBuilder()
                            }

                            tag in FIELD_TAGS -> {
                                field = tag
                                text = StringBuilder()
                            }

                            else -> if (field != null) text.append(' ')
                        }
                    }

                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                        if (field != null) text.append(parser.text)
                    }

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name
                        when {
                            tag == "item" || tag == "entry" -> {
                                builder?.build()?.let { result.add(it) }
                                builder = null
                                field = null
                            }

                            builder != null && field == tag -> {
                                builder.append(tag, text.toString().trim())
                                field = null
                            }

                            builder != null && field != null && tag !in FIELD_TAGS ->
                                text.append(' ')
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return result
    }

    private class ItemBuilder {
        var title = ""
        var link = ""
        var linkRel: String? = null
        var linkHref: String? = null
        var description = ""
        var content = ""
        var pubDate: String? = null
        var imageUrl: String? = null
        var guid: String? = null

        fun append(field: String, value: String) {
            if (value.isEmpty()) return
            when (field) {
                "title" -> if (title.isEmpty()) title = value
                "link" -> {
                    if (linkRel == null || linkRel == "alternate") {
                        val url = linkHref?.takeIf { it.isNotBlank() } ?: value
                        if (link.isEmpty()) link = url
                    }
                }

                "description", "summary", "subtitle" -> if (description.isEmpty()) description = value
                "encoded", "content:encoded", "content" -> if (content.isEmpty()) content = value
                "pubDate", "published", "updated", "date", "lastBuildDate" ->
                    if (pubDate == null) pubDate = value

                "guid", "id" -> if (guid == null) guid = value
            }
        }

        fun build(): ParsedItem? {
            if (title.isBlank()) return null
            val resolvedLink = link.takeIf { it.isNotBlank() } ?: guid.orEmpty()
            return ParsedItem(
                guid = guid?.takeIf { it.isNotBlank() },
                title = title,
                link = resolvedLink,
                description = description,
                content = content,
                pubDate = DateParser.parse(pubDate),
                imageUrl = imageUrl
            )
        }
    }

    private companion object {
        val FIELD_TAGS = setOf(
            "title", "link", "description", "summary", "subtitle", "encoded",
            "content:encoded", "content", "pubDate", "published", "updated",
            "date", "lastBuildDate", "guid", "id"
        )
        val IMAGE_TAGS = setOf("enclosure", "media:content", "media:thumbnail", "thumbnail")
    }
}
