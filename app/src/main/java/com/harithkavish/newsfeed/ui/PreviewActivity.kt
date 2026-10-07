package com.harithkavish.newsfeed.ui

import android.app.Activity
import android.os.Bundle
import com.harithkavish.newsfeed.R

/**
 * The feed, in an ordinary window.
 *
 * Two jobs. It lets the feed be read on a launcher that offers no -1 screen at
 * all, and it makes the panel debuggable: the same [FeedView] runs here, so a
 * rendering or data problem can be reproduced without a launcher in the loop.
 */
class PreviewActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)
        findViewById<FeedView>(R.id.feed).prepare()
    }
}
