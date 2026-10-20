package com.harithkavish.newsfeed.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun outlet_fromJson_parsesAllFields() {
        val json = JSONObject("""
            {
                "id": "outlet-1",
                "name": "Test Outlet",
                "homepage": "https://example.com",
                "region": "US",
                "category": "international"
            }
        """.trimIndent())

        val outlet = Outlet.from(json)

        assertEquals("outlet-1", outlet.id)
        assertEquals("Test Outlet", outlet.name)
        assertEquals("https://example.com", outlet.homepage)
        assertEquals("US", outlet.region)
        assertEquals("international", outlet.category)
    }

    @Test
    fun outlet_fromJson_missingFields_defaultToEmpty() {
        val json = JSONObject()

        val outlet = Outlet.from(json)

        assertEquals("", outlet.id)
        assertEquals("", outlet.name)
        assertEquals("", outlet.homepage)
        assertEquals("", outlet.region)
        assertEquals("", outlet.category)
    }

    @Test
    fun topic_fromJson_parsesAllFields() {
        val json = JSONObject("""
            {
                "id": "topic-1",
                "title": "Test Topic",
                "category": "international",
                "firstSeenAt": "2026-01-01T00:00:00Z",
                "lastUpdatedAt": "2026-01-02T00:00:00Z",
                "outletCount": 3,
                "articleCount": 10,
                "entryCount": 5,
                "status": "developing"
            }
        """.trimIndent())

        val topic = Topic.from(json)

        assertEquals("topic-1", topic.id)
        assertEquals("Test Topic", topic.title)
        assertEquals("international", topic.category)
        assertEquals("2026-01-01T00:00:00Z", topic.firstSeenAt)
        assertEquals("2026-01-02T00:00:00Z", topic.lastUpdatedAt)
        assertEquals(3, topic.outletCount)
        assertEquals(10, topic.articleCount)
        assertEquals(5, topic.entryCount)
        assertEquals("developing", topic.status)
    }

    @Test
    fun topic_isDeveloping_trueWhenStatusDeveloping() {
        val json = JSONObject("""{"status": "developing"}""".trimIndent())
        val topic = Topic.from(json)
        assertTrue(topic.isDeveloping)
    }

    @Test
    fun topic_isDeveloping_falseWhenStatusNotDeveloping() {
        val json = JSONObject("""{"status": "stable"}""".trimIndent())
        val topic = Topic.from(json)
        assertFalse(topic.isDeveloping)
    }

    @Test
    fun topic_listFrom_parsesArray() {
        val arr = JSONArray("""
            [
                {"id": "t1", "title": "Topic 1"},
                {"id": "t2", "title": "Topic 2"}
            ]
        """.trimIndent())

        val topics = Topic.listFrom(arr)

        assertEquals(2, topics.size)
        assertEquals("t1", topics[0].id)
        assertEquals("t2", topics[1].id)
    }

    @Test
    fun article_fromJson_parsesAllFields() {
        val json = JSONObject("""
            {
                "id": "art-1",
                "outletId": "outlet-1",
                "outletName": "Test Outlet",
                "title": "Original Title",
                "titleEn": "English Title",
                "url": "https://example.com/article",
                "publishedAt": "2026-01-01T00:00:00Z",
                "summary": "A summary"
            }
        """.trimIndent())

        val article = Article.from(json)

        assertEquals("art-1", article.id)
        assertEquals("outlet-1", article.outletId)
        assertEquals("Test Outlet", article.outletName)
        assertEquals("Original Title", article.title)
        assertEquals("English Title", article.titleEn)
        assertEquals("https://example.com/article", article.url)
        assertEquals("2026-01-01T00:00:00Z", article.publishedAt)
        assertEquals("A summary", article.summary)
    }

    @Test
    fun article_displayTitle_usesTitleEnWhenPresent() {
        val article = Article(
            id = "1",
            outletId = "o1",
            outletName = "Outlet",
            title = "Original",
            titleEn = "English",
            url = "https://example.com",
            publishedAt = "2026-01-01T00:00:00Z",
            summary = null
        )
        assertEquals("English", article.displayTitle)
    }

    @Test
    fun article_displayTitle_fallsBackToTitleWhenTitleEnNull() {
        val article = Article(
            id = "1",
            outletId = "o1",
            outletName = "Outlet",
            title = "Original",
            titleEn = null,
            url = "https://example.com",
            publishedAt = "2026-01-01T00:00:00Z",
            summary = null
        )
        assertEquals("Original", article.displayTitle)
    }

    @Test
    fun article_displayTitle_fallsBackToTitleWhenTitleEnBlank() {
        val article = Article(
            id = "1",
            outletId = "o1",
            outletName = "Outlet",
            title = "Original",
            titleEn = "   ",
            url = "https://example.com",
            publishedAt = "2026-01-01T00:00:00Z",
            summary = null
        )
        assertEquals("Original", article.displayTitle)
    }

    @Test
    fun article_fromJson_handlesNullTitleEn() {
        val json = JSONObject("""
            {"id": "1", "outletId": "o1", "outletName": "Outlet", "title": "Original", "titleEn": null, "url": "u", "publishedAt": "now", "summary": null}
        """.trimIndent())

        val article = Article.from(json)
        assertNull(article.titleEn)
        assertEquals("Original", article.displayTitle)
    }

    @Test
    fun threadEntry_fromJson_parsesArticles() {
        val json = JSONObject("""
            {
                "id": "entry-1",
                "occurredAt": "2026-01-01T00:00:00Z",
                "headline": "Something happened",
                "articles": [
                    {"id": "a1", "outletId": "o1", "outletName": "Outlet 1", "title": "Title 1", "titleEn": "English 1", "url": "u1", "publishedAt": "2026-01-01T00:00:00Z", "summary": "Summary 1"},
                    {"id": "a2", "outletId": "o2", "outletName": "Outlet 2", "title": "Title 2", "titleEn": null, "url": "u2", "publishedAt": "2026-01-01T01:00:00Z", "summary": "Summary 2"}
                ]
            }
        """.trimIndent())

        val entry = ThreadEntry.from(json)

        assertEquals("entry-1", entry.id)
        assertEquals("2026-01-01T00:00:00Z", entry.occurredAt)
        assertEquals("Something happened", entry.headline)
        assertEquals(2, entry.articles.size)
        assertEquals("a1", entry.articles[0].id)
        assertEquals("a2", entry.articles[1].id)
    }

    @Test
    fun threadEntry_fromJson_emptyArticles_returnsEmptyList() {
        val json = JSONObject("""
            {"id": "entry-1", "occurredAt": "now", "headline": "Headline", "articles": []}
        """.trimIndent())

        val entry = ThreadEntry.from(json)
        assertEquals(0, entry.articles.size)
    }

    @Test
    fun topicDetail_fromJson_parsesTopicThreadOutlets() {
        val json = JSONObject("""
            {
                "id": "topic-1",
                "title": "Test Topic",
                "category": "international",
                "firstSeenAt": "2026-01-01T00:00:00Z",
                "lastUpdatedAt": "2026-01-02T00:00:00Z",
                "outletCount": 2,
                "articleCount": 5,
                "entryCount": 3,
                "status": "developing",
                "thread": [
                    {"id": "e1", "occurredAt": "now", "headline": "Headline 1", "articles": []}
                ],
                "outlets": [
                    {"id": "o1", "name": "Outlet 1", "homepage": "h1", "region": "US", "category": "international"}
                ]
            }
        """.trimIndent())

        val detail = TopicDetail.from(json)

        assertNotNull(detail.topic)
        assertEquals("topic-1", detail.topic.id)
        assertEquals(1, detail.thread.size)
        assertEquals("e1", detail.thread[0].id)
        assertEquals(1, detail.outlets.size)
        assertEquals("o1", detail.outlets[0].id)
    }

    @Test
    fun category_of_knownId_returnsKnownLabel() {
        val cat = Category.of("international")
        assertEquals("international", cat.id)
        assertEquals("International", cat.label)
    }

    @Test
    fun category_of_unknownId_prettifies() {
        val cat = Category.of("custom-category")
        assertEquals("custom-category", cat.id)
        assertEquals("Custom Category", cat.label)
    }

    @Test
    fun category_sort_ordersByEnginePriorityThenAlphabetically() {
        val ids = listOf("city", "international", "unknown", "national", "state", "district", "another")
        val sorted = Category.sort(ids)

        assertEquals("international", sorted[0].id)
        assertEquals("national", sorted[1].id)
        assertEquals("state", sorted[2].id)
        assertEquals("district", sorted[3].id)
        assertEquals("city", sorted[4].id)
        assertEquals("another", sorted[5].id)
        assertEquals("unknown", sorted[6].id)
    }

    @Test
    fun category_sort_removesDuplicates() {
        val ids = listOf("international", "national", "international", "city")
        val sorted = Category.sort(ids)
        assertEquals(3, sorted.size)
    }
}