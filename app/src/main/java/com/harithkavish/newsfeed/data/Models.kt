package com.harithkavish.newsfeed.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The shapes the Timeline news engine serves.
 *
 * These mirror `src/types/news.ts` in HarithKavish/Timeline one field at a
 * time, on purpose: this app is a second reader of one engine, not a second
 * engine. When a field is added there it is added here, and nowhere else does
 * this app decide what a topic is.
 *
 * One deliberate divergence: `category` is a [String], not a closed enum. The
 * engine currently serves five geographic tiers, but the whole point of the
 * category column is that more can be added by appending to the worker's
 * outlet list. A closed enum here would turn "a new category shipped" into "an
 * old build shows nothing", so unknown categories flow through and render with
 * a prettified label until this app learns a nicer one.
 */

/** A news source, as the engine knows it. */
data class Outlet(
    val id: String,
    val name: String,
    val homepage: String,
    val region: String,
    val category: String,
) {
    companion object {
        fun from(o: JSONObject) = Outlet(
            id = o.optString("id"),
            name = o.optString("name"),
            homepage = o.optString("homepage"),
            region = o.optString("region"),
            category = o.optString("category"),
        )
    }
}

/** A cluster of articles the engine judged to be the same real-world story. */
data class Topic(
    val id: String,
    val title: String,
    val category: String,
    val firstSeenAt: String,
    val lastUpdatedAt: String,
    val outletCount: Int,
    val articleCount: Int,
    val entryCount: Int,
    val status: String,
) {
    val isDeveloping: Boolean get() = status == "developing"

    companion object {
        fun from(o: JSONObject) = Topic(
            id = o.optString("id"),
            title = o.optString("title"),
            category = o.optString("category"),
            firstSeenAt = o.optString("firstSeenAt"),
            lastUpdatedAt = o.optString("lastUpdatedAt"),
            outletCount = o.optInt("outletCount"),
            articleCount = o.optInt("articleCount"),
            entryCount = o.optInt("entryCount"),
            status = o.optString("status"),
        )

        fun listFrom(arr: JSONArray): List<Topic> =
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { from(it) }
            }
    }
}

/** One outlet's report of one development. */
data class Article(
    val id: String,
    val outletId: String,
    val outletName: String,
    /** The real source headline, in the outlet's own language. Never translated. */
    val title: String,
    /** English translation, when the outlet is not English and translation succeeded. */
    val titleEn: String?,
    val url: String,
    val publishedAt: String,
    val summary: String?,
) {
    /** What to show. The engine's own rule: `titleEn ?? title`. */
    val displayTitle: String get() = titleEn?.takeIf { it.isNotBlank() } ?: title

    companion object {
        fun from(o: JSONObject) = Article(
            id = o.optString("id"),
            outletId = o.optString("outletId"),
            outletName = o.optString("outletName"),
            title = o.optString("title"),
            titleEn = o.optString("titleEn").takeIf { it.isNotBlank() && it != "null" },
            url = o.optString("url"),
            publishedAt = o.optString("publishedAt"),
            summary = o.optString("summary").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}

/** One distinct development within a topic, with every outlet that corroborated it. */
data class ThreadEntry(
    val id: String,
    val occurredAt: String,
    val headline: String,
    val articles: List<Article>,
) {
    companion object {
        fun from(o: JSONObject): ThreadEntry {
            val arr = o.optJSONArray("articles") ?: JSONArray()
            return ThreadEntry(
                id = o.optString("id"),
                occurredAt = o.optString("occurredAt"),
                headline = o.optString("headline"),
                articles = (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { Article.from(it) }
                },
            )
        }
    }
}

/** A topic plus its full chronological thread. */
data class TopicDetail(
    val topic: Topic,
    val thread: List<ThreadEntry>,
    val outlets: List<Outlet>,
) {
    companion object {
        fun from(o: JSONObject): TopicDetail {
            val threadArr = o.optJSONArray("thread") ?: JSONArray()
            val outletArr = o.optJSONArray("outlets") ?: JSONArray()
            return TopicDetail(
                topic = Topic.from(o),
                thread = (0 until threadArr.length()).mapNotNull { i ->
                    threadArr.optJSONObject(i)?.let { ThreadEntry.from(it) }
                },
                outlets = (0 until outletArr.length()).mapNotNull { i ->
                    outletArr.optJSONObject(i)?.let { Outlet.from(it) }
                },
            )
        }
    }
}

/**
 * A feed category. The id is whatever the engine calls it; the label is what a
 * reader sees.
 *
 * [LABELS] carries the five the engine serves today, matching
 * `NEWS_CATEGORY_LABEL` in Timeline. Anything else gets its id title-cased,
 * which is a reasonable rendering of the kebab-case ids the worker generates
 * and keeps an older build useful against a newer engine.
 */
data class Category(val id: String, val label: String) {
    companion object {
        private val LABELS = mapOf(
            "international" to "International",
            "national" to "National",
            "state" to "State",
            "district" to "District",
            "city" to "City",
        )

        /** The engine's own ordering, broadest first. Unknown ids sort after these. */
        private val ORDER = listOf("international", "national", "state", "district", "city")

        fun of(id: String) = Category(id, LABELS[id] ?: prettify(id))

        private fun prettify(id: String) = id
            .split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .ifBlank { id }

        fun sort(ids: Collection<String>): List<Category> =
            ids.distinct()
                .sortedWith(compareBy({ ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: ORDER.size }, { it }))
                .map { of(it) }
    }
}
