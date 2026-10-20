package com.harithkavish.newsfeed.data

import android.content.Context
import java.io.File

/**
 * Last known good feed, on disk.
 *
 * This exists for one reason: the -1 screen is opened by a swipe, and a swipe
 * that lands on a spinner reads as a slow app no matter how quick the network
 * turns out to be. The panel paints whatever was cached, synchronously, and
 * the refresh then replaces it in place.
 *
 * It stores the engine's raw response rather than parsed objects -- the parse
 * is cheap, and raw JSON means a response that gained a field survives being
 * cached by an older build.
 */
class FeedCache(context: Context) {

    private val dir = File(context.applicationContext.cacheDir, "feed").apply { mkdirs() }

    fun read(key: String): String? = runCatching {
        val f = file(key)
        if (f.isFile && f.length() > 0) f.readText(Charsets.UTF_8) else null
    }.getOrNull()

    fun write(key: String, body: String) {
        runCatching {
            // Write-then-rename: a process killed mid-write leaves the previous
            // cache intact rather than a truncated file that fails to parse.
            val tmp = File(dir, "$key.tmp")
            tmp.writeText(body, Charsets.UTF_8)
            tmp.renameTo(file(key))
        }
    }

    /** Milliseconds since [key] was last written, or [Long.MAX_VALUE] if never. */
    fun ageOf(key: String): Long {
        val f = file(key)
        return if (f.isFile) System.currentTimeMillis() - f.lastModified() else Long.MAX_VALUE
    }

    private fun file(key: String) = File(dir, "$key.json")
}
