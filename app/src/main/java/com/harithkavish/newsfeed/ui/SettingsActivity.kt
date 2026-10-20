package com.harithkavish.newsfeed.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.harithkavish.newsfeed.BuildConfig
import com.harithkavish.newsfeed.R
import com.harithkavish.newsfeed.data.FeedRepository
import com.harithkavish.newsfeed.data.Prefs

/**
 * The only screen this app has a way into, and it is reached from Settings.
 *
 * The manifest gives this activity MAIN + CATEGORY_INFO and deliberately no
 * CATEGORY_LAUNCHER. That pair is what produces the behaviour asked for: no
 * icon in the app drawer, while Settings > Apps still offers a front door,
 * because PackageManager.getLaunchIntentForPackage() looks for an INFO
 * activity before it looks for a launcher one.
 *
 * What belongs here is what a feed provider genuinely has to offer: which
 * categories to follow, how to select the app as the -1 screen, and what
 * engine the news comes from.
 */
class SettingsActivity : Activity() {

    private val prefs by lazy { Prefs(this) }
    private val repo by lazy { FeedRepository.get(this) }

    private lateinit var categoryList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        categoryList = findViewById(R.id.category_list)

        findViewById<TextView>(R.id.engine_detail).text = engineDetail()

        findViewById<View>(R.id.preview).setOnClickListener {
            startActivity(Intent(this, PreviewActivity::class.java))
        }

        buildCategories()
        // The engine is the source of truth for which categories exist, so the
        // list can grow without this screen being changed.
        repo.refreshCategories { buildCategories() }
    }

    private fun buildCategories() {
        categoryList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val all = repo.categories
        val allIds = all.map { it.id }

        all.forEach { category ->
            val row = inflater.inflate(R.layout.item_category_toggle, categoryList, false)
            val label = row.findViewById<TextView>(R.id.label)
            val toggle = row.findViewById<CheckBox>(R.id.toggle)

            label.text = category.label
            toggle.isChecked = prefs.isFollowed(category.id)

            row.setOnClickListener {
                prefs.toggleFollow(category.id, allIds)
                // Rebuild rather than flip one box: unfollowing the last
                // category, or re-following every one, changes what the whole
                // list means.
                buildCategories()
            }
            categoryList.addView(row)
        }
    }

    private fun engineDetail(): String = buildString {
        append("Stories come from the Timeline news engine, the same backend that serves ")
        append("timeline.harithkavish.com. It ingests trusted RSS feeds, clusters them ")
        append("into topics with multilingual embeddings, and writes each development as ")
        append("a thread entry.\n\n")
        append(BuildConfig.NEWS_API_BASE)
        append("\n\nVersion ")
        append(BuildConfig.VERSION_NAME)
    }
}
