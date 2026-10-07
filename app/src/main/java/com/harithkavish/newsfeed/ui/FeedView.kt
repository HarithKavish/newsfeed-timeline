package com.harithkavish.newsfeed.ui

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.harithkavish.newsfeed.R
import com.harithkavish.newsfeed.data.Category
import com.harithkavish.newsfeed.data.FeedRepository
import com.harithkavish.newsfeed.data.Prefs
import com.harithkavish.newsfeed.data.Topic

/**
 * The feed surface, used in both places it is wanted: inside the -1 screen
 * panel, and inside the settings preview. One view, so the two can never drift
 * into being two different feeds.
 *
 * The panel drives it through the lifecycle hooks below rather than through a
 * real Activity lifecycle, because on the -1 screen there is no Activity --
 * the window belongs to the launcher.
 */
class FeedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val repo = FeedRepository.get(context)
    private val prefs = Prefs(context)

    private val list: RecyclerView
    private val chips: LinearLayout
    private val state: TextView

    private val adapter = TopicAdapter(::openTopic)

    private var selected: String? = prefs.selectedCategory
    private var loadedOnce = false
    private var loadingMore = false
    private var exhausted = false

    /** Set by the overlay panel so the feed can ask to be dismissed. */
    var onCloseRequested: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_feed, this, true)

        list = findViewById(R.id.list)
        chips = findViewById(R.id.chips)
        state = findViewById(R.id.state)

        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.setHasFixedSize(false)
        // The feed is one flat list of equal-weight cards; a bigger cache means
        // a flung scroll re-binds instead of re-inflating.
        list.setItemViewCacheSize(12)

        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = lm.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - PREFETCH_DISTANCE) loadMore()
            }
        })

        buildChips()
    }

    /**
     * Start work before the panel is on screen.
     *
     * Called as soon as the launcher attaches its window, which is typically
     * well before the reader swipes. Doing it here rather than on open is the
     * difference between a feed that is already drawn and one that appears.
     */
    fun prepare() {
        if (loadedOnce) return
        loadedOnce = true
        repo.refreshCategories { buildChips() }
        load()
    }

    fun onPanelWillOpen() {
        prepare()
    }

    fun onPanelOpened() {
        // Anything older than this is worth replacing while the reader looks
        // at it; anything newer would be a request for no reason.
        if (loadedOnce) load(quiet = true)
    }

    fun onPanelClosed() = Unit

    fun onPanelPaused() = Unit

    fun onPanelResumed() = Unit

    fun release() {
        list.adapter = null
    }

    private fun load(quiet: Boolean = false) {
        exhausted = false
        if (!quiet && adapter.itemCount == 0) showState(R.string.state_loading)

        repo.load(
            category = selected,
            onResult = { topics, fresh ->
                val visible = topics.filter { prefs.isFollowed(it.category) }
                adapter.submit(visible)
                when {
                    visible.isNotEmpty() -> hideState()
                    fresh -> showState(R.string.state_empty)
                    else -> Unit // cached copy was empty; wait for the network
                }
            },
            onError = {
                if (adapter.itemCount == 0) {
                    showState(R.string.state_error_bare)
                } else {
                    // Something is already on screen. Replacing a readable feed
                    // with an error would be a worse answer than keeping it.
                    showState(null)
                }
            },
        )
    }

    private fun loadMore() {
        if (loadingMore || exhausted || adapter.itemCount == 0) return
        loadingMore = true

        repo.loadMore(
            category = selected,
            offset = adapter.itemCount,
            onResult = { topics, hasMore ->
                loadingMore = false
                exhausted = !hasMore
                adapter.append(topics.filter { prefs.isFollowed(it.category) })
            },
            onError = {
                loadingMore = false
                // Stop paging on a failure rather than retrying on every
                // scroll event, which would hammer the engine while offline.
                exhausted = true
            },
        )
    }

    private fun buildChips() {
        chips.removeAllViews()
        val inflater = LayoutInflater.from(context)

        fun chip(label: String, id: String?) {
            val view = inflater.inflate(R.layout.item_chip, chips, false) as TextView
            view.text = label
            view.isSelected = selected == id
            view.setOnClickListener {
                if (selected == id) return@setOnClickListener
                selected = id
                prefs.selectedCategory = id
                buildChips()
                adapter.submit(emptyList())
                load()
            }
            chips.addView(view)
        }

        chip(context.getString(R.string.feed_all), null)
        repo.categories
            .filter { prefs.isFollowed(it.id) }
            .forEach { category -> chip(category.label, category.id) }
    }

    private fun showState(resId: Int?) {
        if (resId == null) {
            state.visibility = View.GONE
            return
        }
        state.setText(resId)
        state.visibility = View.VISIBLE
        list.visibility = if (adapter.itemCount == 0) View.INVISIBLE else View.VISIBLE
    }

    private fun hideState() {
        state.visibility = View.GONE
        list.visibility = View.VISIBLE
    }

    private fun openTopic(topic: Topic) {
        // From the -1 screen there is no Activity to start from, so this needs
        // its own task. NO_ANIMATION keeps the handoff from the panel quiet.
        val intent = Intent(context, TopicActivity::class.java)
            .putExtra(TopicActivity.EXTRA_TOPIC_ID, topic.id)
            .putExtra(TopicActivity.EXTRA_TOPIC_TITLE, topic.title)
            .putExtra(TopicActivity.EXTRA_TOPIC_CATEGORY, topic.category)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(intent) }
        onCloseRequested?.invoke()
    }

    private companion object {
        /** How close to the end a scroll gets before the next page is asked for. */
        const val PREFETCH_DISTANCE = 5
    }
}

/** Category label for a topic, used by the detail screen as well as the list. */
internal fun labelFor(categoryId: String): String = Category.of(categoryId).label
