package com.harithkavish.newsfeed.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.harithkavish.newsfeed.R
import com.harithkavish.newsfeed.data.Category
import com.harithkavish.newsfeed.data.Topic

/**
 * The feed list.
 *
 * Kept deliberately plain: one view type, no images to load, no nested
 * recycling. That is what makes a swipe onto the -1 screen land on a drawn
 * frame instead of on a spinner.
 */
internal class TopicAdapter(
    private val onClick: (Topic) -> Unit,
) : RecyclerView.Adapter<TopicAdapter.TopicHolder>() {

    private val items = mutableListOf<Topic>()

    init {
        // Topic ids are stable, so RecyclerView can keep a scroll position
        // across a refresh instead of jumping to the top.
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun getItemCount(): Int = items.size

    fun submit(next: List<Topic>) {
        val diff = DiffUtil.calculateDiff(Diff(items.toList(), next))
        items.clear()
        items.addAll(next)
        diff.dispatchUpdatesTo(this)
    }

    fun append(more: List<Topic>) {
        if (more.isEmpty()) return
        val start = items.size
        // Paging can overlap when the engine ingests between requests, so drop
        // anything already on screen rather than showing a story twice.
        val known = items.mapTo(HashSet()) { it.id }
        val fresh = more.filter { it.id !in known }
        if (fresh.isEmpty()) return
        items.addAll(fresh)
        notifyItemRangeInserted(start, fresh.size)
    }

    fun itemsSnapshot(): List<Topic> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_topic, parent, false)
        return TopicHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: TopicHolder, position: Int) = holder.bind(items[position])

    internal class TopicHolder(
        view: View,
        private val onClick: (Topic) -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        private val category: TextView = view.findViewById(R.id.category)
        private val developing: TextView = view.findViewById(R.id.developing)
        private val title: TextView = view.findViewById(R.id.title)
        private val meta: TextView = view.findViewById(R.id.meta)

        private var bound: Topic? = null

        init {
            view.setOnClickListener { bound?.let(onClick) }
        }

        fun bind(topic: Topic) {
            bound = topic
            category.text = Category.of(topic.category).label
            developing.visibility = if (topic.isDeveloping) View.VISIBLE else View.GONE
            title.text = topic.title
            meta.text = metaLine(topic)
        }

        private fun metaLine(topic: Topic): String {
            val res = itemView.resources
            val sep = res.getString(R.string.meta_separator)
            val parts = mutableListOf<String>()

            parts += if (topic.outletCount == 1) {
                res.getString(R.string.meta_outlets_one)
            } else {
                res.getString(R.string.meta_outlets_many, topic.outletCount)
            }

            // Entry count is the number of distinct developments, which is the
            // number worth showing -- article count double-counts every outlet
            // that reported the same thing.
            if (topic.entryCount > 1) {
                parts += res.getString(R.string.meta_updates_many, topic.entryCount)
            }

            RelativeTime.short(topic.lastUpdatedAt).takeIf { it.isNotBlank() }?.let { parts += it }

            return parts.joinToString(sep)
        }
    }

    private class Diff(
        private val old: List<Topic>,
        private val new: List<Topic>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(o: Int, n: Int) = old[o].id == new[n].id
        override fun areContentsTheSame(o: Int, n: Int) = old[o] == new[n]
    }
}
