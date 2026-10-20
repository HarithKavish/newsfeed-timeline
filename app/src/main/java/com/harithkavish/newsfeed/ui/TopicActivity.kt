package com.harithkavish.newsfeed.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.harithkavish.newsfeed.R
import com.harithkavish.newsfeed.data.FeedRepository
import com.harithkavish.newsfeed.data.ThreadEntry
import com.harithkavish.newsfeed.data.TopicDetail

/**
 * One story, in full.
 *
 * The engine's thread is the point of this screen: a topic is not a headline
 * but a chronological log of developments, each carrying the outlets that
 * corroborated it. Tapping a source opens the outlet's own page -- this app
 * never reproduces article text it did not write.
 *
 * No launcher category in the manifest, so it never appears in the app drawer.
 */
class TopicActivity : Activity() {

    private lateinit var thread: LinearLayout
    private lateinit var state: TextView
    private lateinit var threadHeading: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topic)

        thread = findViewById(R.id.thread)
        state = findViewById(R.id.state)
        threadHeading = findViewById(R.id.thread_heading)

        val topicId = intent.getStringExtra(EXTRA_TOPIC_ID)
        if (topicId.isNullOrBlank()) {
            finish()
            return
        }

        // Paint what the list already knew before the request returns, so the
        // screen opens with the story on it rather than with a spinner.
        findViewById<TextView>(R.id.title).text = intent.getStringExtra(EXTRA_TOPIC_TITLE).orEmpty()
        findViewById<TextView>(R.id.category).text =
            labelFor(intent.getStringExtra(EXTRA_TOPIC_CATEGORY).orEmpty())

        state.setText(R.string.state_loading)

        FeedRepository.get(this).topic(
            id = topicId,
            onResult = ::render,
            onError = { state.setText(R.string.state_error_bare) },
        )
    }

    private fun render(detail: TopicDetail) {
        findViewById<TextView>(R.id.title).text = detail.topic.title
        findViewById<TextView>(R.id.category).text = labelFor(detail.topic.category)
        findViewById<TextView>(R.id.meta).text = metaLine(detail)

        state.visibility = View.GONE
        thread.removeAllViews()

        if (detail.thread.isEmpty()) {
            state.visibility = View.VISIBLE
            state.setText(R.string.state_empty)
            return
        }

        threadHeading.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)
        // Newest first: opening a developing story should show what just
        // happened, not what happened when it started days ago.
        detail.thread.asReversed().forEach { entry -> thread.addView(entryView(inflater, entry)) }
    }

    private fun entryView(inflater: LayoutInflater, entry: ThreadEntry): View {
        val view = inflater.inflate(R.layout.item_thread_entry, thread, false)
        view.findViewById<TextView>(R.id.occurred).text = RelativeTime.stamp(entry.occurredAt)
        view.findViewById<TextView>(R.id.headline).text = entry.headline

        val sources = view.findViewById<LinearLayout>(R.id.sources)
        entry.articles.forEach { article ->
            val row = inflater.inflate(R.layout.item_source, sources, false) as TextView
            row.text = getString(
                R.string.source_row,
                article.outletName,
                article.displayTitle,
            )
            row.setOnClickListener { openUrl(article.url) }
            sources.addView(row)
        }
        return view
    }

    private fun metaLine(detail: TopicDetail): String {
        val sep = getString(R.string.meta_separator)
        val parts = mutableListOf<String>()

        parts += if (detail.topic.outletCount == 1) {
            getString(R.string.meta_outlets_one)
        } else {
            getString(R.string.meta_outlets_many, detail.topic.outletCount)
        }
        if (detail.thread.size > 1) {
            parts += getString(R.string.meta_updates_many, detail.thread.size)
        }
        RelativeTime.short(detail.topic.lastUpdatedAt).takeIf { it.isNotBlank() }?.let { parts += it }

        return parts.joinToString(sep)
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // No browser is a real state on a stripped device; failing quietly is
        // better than crashing out of a story the reader was reading.
        runCatching { startActivity(intent) }
    }

    companion object {
        const val EXTRA_TOPIC_ID = "topic_id"
        const val EXTRA_TOPIC_TITLE = "topic_title"
        const val EXTRA_TOPIC_CATEGORY = "topic_category"
    }
}
