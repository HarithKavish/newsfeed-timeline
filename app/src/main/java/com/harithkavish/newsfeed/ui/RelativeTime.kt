package com.harithkavish.newsfeed.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Timestamps as a reader reads them.
 *
 * The engine serves ISO-8601 in UTC. A feed is scanned, not studied, so recent
 * items get a relative form ("2h ago") and older ones get a date -- the point
 * at which "203h ago" stops being easier to parse than "12 Sep".
 */
internal object RelativeTime {

    private val sameYear = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val otherYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val withTime = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())

    /** "just now" / "14m ago" / "2h ago" / "3d ago" / "12 Sep". */
    fun short(iso: String, now: Long = System.currentTimeMillis()): String {
        val instant = parse(iso) ?: return ""
        val deltaMs = now - instant.toEpochMilli()

        return when {
            deltaMs < 0 -> "just now" // clock skew; never show a future age
            deltaMs < MINUTE -> "just now"
            deltaMs < HOUR -> "${deltaMs / MINUTE}m ago"
            deltaMs < DAY -> "${deltaMs / HOUR}h ago"
            deltaMs < WEEK -> "${deltaMs / DAY}d ago"
            else -> date(instant)
        }
    }

    /** An absolute date and time, for a thread entry where order is the point. */
    fun stamp(iso: String): String {
        val instant = parse(iso) ?: return ""
        return withTime.format(instant.atZone(ZoneId.systemDefault()))
    }

    private fun date(instant: Instant): String {
        val local = instant.atZone(ZoneId.systemDefault())
        val thisYear = Instant.now().atZone(ZoneId.systemDefault()).year
        return (if (local.year == thisYear) sameYear else otherYear).format(local)
    }

    private fun parse(iso: String): Instant? =
        if (iso.isBlank()) null else runCatching { Instant.parse(iso) }.getOrNull()

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY
}
