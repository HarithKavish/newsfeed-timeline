package com.harithkavish.newsfeed.data

import com.harithkavish.newsfeed.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * The read side of the Timeline news engine.
 *
 * Deliberately `HttpURLConnection` and `org.json`: both ship with Android, so
 * the feed costs no dependency bytes and no extra class loading on a cold
 * open. The engine's responses are small (a page of topics is a few KB) and
 * the routes are plain GETs, so nothing here earns a client library.
 *
 * Every route below exists in `workers/news/src/api.ts` in HarithKavish/Timeline
 * and is the same route timeline.harithkavish.com calls. No route is invented
 * here, and no news is fetched from anywhere else.
 */
object NewsApi {

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    /** Thrown for anything the caller should show as "couldn't refresh". */
    class ApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

    /** `GET /topics/by-category?limit=` — the latest [limit] topics in each category. */
    fun topicsByCategory(limit: Int): Map<String, List<Topic>> {
        val body = get("/topics/by-category?limit=$limit")
        val root = JSONObject(body)
        val out = LinkedHashMap<String, List<Topic>>()
        for (key in root.keys()) {
            val arr = root.optJSONArray(key) ?: continue
            out[key] = Topic.listFrom(arr)
        }
        return out
    }

    /** One page of the flat topic list, newest first, optionally scoped to a category. */
    fun topics(category: String? = null, offset: Int = 0, limit: Int = 30): TopicPage {
        val params = buildString {
            append("?sort=newest&limit=").append(limit).append("&offset=").append(offset)
            if (!category.isNullOrBlank()) {
                append("&category=").append(URLEncoder.encode(category, "UTF-8"))
            }
        }
        val root = JSONObject(get("/topics$params"))
        val items = root.optJSONArray("items")?.let { Topic.listFrom(it) } ?: emptyList()
        return TopicPage(
            items = items,
            total = root.optInt("total", items.size),
            offset = root.optInt("offset", offset),
            limit = root.optInt("limit", limit),
        )
    }

    /** `GET /topics/:id` — a topic with its full thread. */
    fun topic(id: String): TopicDetail =
        TopicDetail.from(JSONObject(get("/topics/${URLEncoder.encode(id, "UTF-8")}")))

    /**
     * `GET /outlets` — every source the engine ingests.
     *
     * This is also where the app learns which categories exist, rather than
     * hardcoding them: the set of distinct `category` values across the
     * outlets *is* the category list, so adding one in the worker makes it
     * appear here with no app change.
     */
    fun outlets(): List<Outlet> {
        val arr = org.json.JSONArray(get("/outlets"))
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Outlet.from(it) }
        }
    }

    data class TopicPage(
        val items: List<Topic>,
        val total: Int,
        val offset: Int,
        val limit: Int,
    ) {
        val hasMore: Boolean get() = offset + items.size < total
    }

    private fun get(path: String): String {
        val url = URL(BuildConfig.NEWS_API_BASE + path)
        var conn: HttpURLConnection? = null
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "gzip")
                // No Origin header: the worker's CORS allowlist is a browser
                // concern. A native client is not subject to it, and sending a
                // fake origin would be dishonest about what this client is.
                setRequestProperty("User-Agent", "newsfeed-timeline/${BuildConfig.VERSION_NAME} (Android)")
                instanceFollowRedirects = true
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                throw ApiException("HTTP $code from $path")
            }

            val raw = conn.inputStream
            val stream = if (conn.contentEncoding?.equals("gzip", ignoreCase = true) == true) {
                GZIPInputStream(raw)
            } else {
                raw
            }
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Could not reach the news engine", e)
        } finally {
            conn?.disconnect()
        }
    }
}
