package com.harithkavish.newsfeed.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_700_000_000_000L // 2023-11-14T13:46:40Z (fixed timestamp for deterministic tests)

    @Test
    fun short_justNow_whenFutureTimestamp() {
        val future = now + 100_000
        val result = RelativeTime.short(Instant.ofEpochMilli(future).toString(), now)
        assertEquals("just now", result)
    }

    @Test
    fun short_justNow_whenLessThanMinute() {
        val ts = now - 30_000 // 30 seconds ago
        val result = RelativeTime.short(Instant.ofEpochMilli(ts).toString(), now)
        assertEquals("just now", result)
    }

    @Test
    fun short_minutesAgo_whenLessThanHour() {
        val ts = now - 5 * 60_000 // 5 minutes ago
        val result = RelativeTime.short(Instant.ofEpochMilli(ts).toString(), now)
        assertEquals("5m ago", result)
    }

    @Test
    fun short_hoursAgo_whenLessThanDay() {
        val ts = now - 3 * 60 * 60_000 // 3 hours ago
        val result = RelativeTime.short(Instant.ofEpochMilli(ts).toString(), now)
        assertEquals("3h ago", result)
    }

    @Test
    fun short_daysAgo_whenLessThanWeek() {
        val ts = now - 4 * 24 * 60 * 60_000 // 4 days ago
        val result = RelativeTime.short(Instant.ofEpochMilli(ts).toString(), now)
        assertEquals("4d ago", result)
    }

    @Test
    fun short_blankInput_returnsEmpty() {
        val result = RelativeTime.short("", now)
        assertEquals("", result)
    }

    @Test
    fun short_invalidIso_returnsEmpty() {
        val result = RelativeTime.short("not-a-date", now)
        assertEquals("", result)
    }
}