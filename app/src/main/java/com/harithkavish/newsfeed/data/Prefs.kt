package com.harithkavish.newsfeed.data

import android.content.Context

/**
 * What the reader has chosen. Small enough to be SharedPreferences, and read
 * on the path that opens the panel, so it stays synchronous and tiny.
 *
 * [followedCategories] is the seed of the follow model: the engine already
 * carries a category per topic, so choosing which ones appear is a client
 * decision that needs nothing new from the worker. An empty set means "follow
 * everything", which is also the correct behaviour on first run and the
 * correct behaviour when the engine adds a category this install has never
 * seen -- a new category shows up rather than being silently excluded.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("newsfeed", Context.MODE_PRIVATE)

    /** Category ids the reader follows. Empty = follow everything. */
    var followedCategories: Set<String>
        get() = sp.getStringSet(KEY_FOLLOWED, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_FOLLOWED, value).apply()

    /** The category tab last open in the panel, or null for "Top stories". */
    var selectedCategory: String?
        get() = sp.getString(KEY_SELECTED, null)
        set(value) = sp.edit().putString(KEY_SELECTED, value).apply()

    fun isFollowed(categoryId: String): Boolean {
        val followed = followedCategories
        return followed.isEmpty() || categoryId in followed
    }

    fun toggleFollow(categoryId: String, allCategoryIds: Collection<String>) {
        // An empty set means "all", so the first unfollow has to be expanded
        // into an explicit set of everything else -- otherwise unfollowing one
        // category would read as unfollowing nothing.
        val current = followedCategories.ifEmpty { allCategoryIds.toSet() }
        val next = if (categoryId in current) current - categoryId else current + categoryId
        followedCategories = if (next.toSet() == allCategoryIds.toSet()) emptySet() else next
    }

    private companion object {
        const val KEY_FOLLOWED = "followed_categories"
        const val KEY_SELECTED = "selected_category"
    }
}
