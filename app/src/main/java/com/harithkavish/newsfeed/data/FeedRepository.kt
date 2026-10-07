package com.harithkavish.newsfeed.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Everything the feed needs, from one engine.
 *
 * The repository owns the only background thread in the app. One thread is
 * enough -- the panel makes one request at a time -- and a fixed single thread
 * costs less than a pool that will never have two things to do.
 *
 * Results arrive on the main thread, because every caller is a view.
 */
class FeedRepository private constructor(context: Context) {

    private val app = context.applicationContext
    private val cache = FeedCache(app)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor(
        ThreadFactory { r -> Thread(r, "newsfeed-io").apply { isDaemon = true } },
    )

    /** Categories the engine currently serves, learned from `/outlets`. */
    @Volatile
    var categories: List<Category> = FALLBACK_CATEGORIES
        private set

    private val refreshing = AtomicBoolean(false)

    /**
     * Topics for [category] (null = every category, newest first), served from
     * cache immediately and then from the engine.
     *
     * [onResult] may therefore be called twice: once with `fresh = false` for
     * the cached copy, once with `fresh = true` when the network answers. It is
     * not called with the cached copy when there is none.
     */
    fun load(
        category: String?,
        onResult: (topics: List<Topic>, fresh: Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val key = cacheKey(category)

        cache.read(key)?.let { cached ->
            runCatching { parseTopics(cached) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { onResult(it, false) }
        }

        if (!refreshing.compareAndSet(false, true)) return

        io.execute {
            try {
                val page = NewsApi.topics(category = category, offset = 0, limit = PAGE_SIZE)
                cache.write(key, JSONObject().put("items", topicsToJson(page.items)).toString())
                main.post {
                    refreshing.set(false)
                    onResult(page.items, true)
                }
            } catch (t: Throwable) {
                main.post {
                    refreshing.set(false)
                    onError(t)
                }
            }
        }
    }

    /** One more page, appended by the caller. Never served from cache. */
    fun loadMore(
        category: String?,
        offset: Int,
        onResult: (topics: List<Topic>, hasMore: Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        io.execute {
            try {
                val page = NewsApi.topics(category = category, offset = offset, limit = PAGE_SIZE)
                main.post { onResult(page.items, page.hasMore) }
            } catch (t: Throwable) {
                main.post { onError(t) }
            }
        }
    }

    /** A topic's full thread, for the detail view. */
    fun topic(id: String, onResult: (TopicDetail) -> Unit, onError: (Throwable) -> Unit) {
        io.execute {
            try {
                val detail = NewsApi.topic(id)
                main.post { onResult(detail) }
            } catch (t: Throwable) {
                main.post { onError(t) }
            }
        }
    }

    /**
     * Refresh the category list from the engine.
     *
     * Cheap and rarely changing, so it is cached aggressively and only
     * re-fetched once a day. The fallback list is the five tiers the engine
     * serves today; if the fetch succeeds the engine's answer wins, which is
     * how a category added in the worker reaches this app without a release.
     */
    fun refreshCategories(onChanged: (List<Category>) -> Unit) {
        cache.read(KEY_CATEGORIES)?.let { cached ->
            runCatching { parseCategories(cached) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { categories = it; onChanged(it) }
        }

        if (cache.ageOf(KEY_CATEGORIES) < CATEGORIES_TTL_MS) return

        io.execute {
            try {
                val ids = NewsApi.outlets().map { it.category }.filter { it.isNotBlank() }
                val resolved = Category.sort(ids)
                if (resolved.isNotEmpty()) {
                    cache.write(KEY_CATEGORIES, JSONArray(resolved.map { it.id }).toString())
                    main.post {
                        categories = resolved
                        onChanged(resolved)
                    }
                }
            } catch (_: Throwable) {
                // A category list that failed to refresh is not an error worth
                // showing: the cached or fallback list still renders a usable
                // feed, and the topics request will surface any real outage.
            }
        }
    }

    private fun cacheKey(category: String?) = "topics-" + (category ?: "all")

    private fun parseTopics(body: String): List<Topic> =
        JSONObject(body).optJSONArray("items")?.let { Topic.listFrom(it) } ?: emptyList()

    private fun parseCategories(body: String): List<Category> {
        val arr = JSONArray(body)
        return Category.sort((0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() })
    }

    private fun topicsToJson(topics: List<Topic>): JSONArray {
        val arr = JSONArray()
        topics.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("category", t.category)
                    .put("firstSeenAt", t.firstSeenAt)
                    .put("lastUpdatedAt", t.lastUpdatedAt)
                    .put("outletCount", t.outletCount)
                    .put("articleCount", t.articleCount)
                    .put("entryCount", t.entryCount)
                    .put("status", t.status),
            )
        }
        return arr
    }

    companion object {
        const val PAGE_SIZE = 25
        private const val KEY_CATEGORIES = "categories"
        private const val CATEGORIES_TTL_MS = 24L * 60 * 60 * 1000

        private val FALLBACK_CATEGORIES = Category.sort(
            listOf("international", "national", "state", "district", "city"),
        )

        @Volatile
        private var instance: FeedRepository? = null

        fun get(context: Context): FeedRepository =
            instance ?: synchronized(this) {
                instance ?: FeedRepository(context).also { instance = it }
            }
    }
}
