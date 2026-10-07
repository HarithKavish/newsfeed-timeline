package com.harithkavish.newsfeed.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.harithkavish.newsfeed.ui.FeedView

/**
 * The -1 screen itself: one window, owned by the launcher, holding the feed.
 *
 * The window is a **sub-window of the launcher's own window**. That is the
 * whole trick of this protocol, and the reason it needs no overlay permission:
 * the launcher hands over its window token in `windowAttached`, and a window
 * added against that token is a child of the launcher's, drawn in its task and
 * torn down with it.
 *
 * Scroll progress runs 0 (fully closed, off to the left) to 1 (fully open).
 * While the reader is dragging, the launcher drives it and we follow. When the
 * panel opens or closes on its own, we drive it and the launcher follows, via
 * [OverlayCallback.overlayScrollChanged] -- that is what keeps the home screen
 * and the panel moving as one surface instead of two.
 *
 * Every method here runs on the main thread; [OverlayBinder] guarantees it.
 */
internal class OverlayPanel(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var feedView: FeedView? = null
    private var callback: OverlayCallback? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var animator: ValueAnimator? = null

    private var attached = false
    private var progress = 0f
    private var dragging = false

    /** Launcher is attaching its window. Build the panel and report readiness. */
    fun attach(attrs: WindowManager.LayoutParams?, cb: OverlayCallback?) {
        detach(isChangingConfigurations = false)
        callback = cb

        if (attrs == null || attrs.token == null) {
            // Without the launcher's token there is no window to parent to.
            // Report "not connected" so the launcher disables the swipe rather
            // than leaving the reader swiping into nothing.
            cb?.overlayStatusChanged(0)
            return
        }

        val view = FeedView(context).apply {
            // Starts closed: the launcher will scroll it in.
            alpha = 0f
            visibility = View.GONE
            onCloseRequested = { close(animate = true) }
        }

        val lp = WindowManager.LayoutParams().apply {
            copyFrom(attrs)
            type = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            windowAnimations = 0
        }

        try {
            windowManager.addView(view, lp)
        } catch (_: Exception) {
            // A stale token (the launcher's window went away between the call
            // and this post) throws here. Same honest answer as a missing one.
            cb?.overlayStatusChanged(0)
            return
        }

        feedView = view
        layoutParams = lp
        attached = true
        progress = 0f

        // Bit 0 set is what makes the launcher enable the -1 swipe at all.
        cb?.overlayStatusChanged(OverlayCallback.STATUS_CONNECTED)

        // Warm the feed now rather than when the panel opens: the swipe is the
        // deadline, and a cache read plus a request started here is usually
        // finished before the panel is halfway across.
        view.prepare()
    }

    fun detach(isChangingConfigurations: Boolean) {
        animator?.cancel()
        animator = null

        feedView?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
            view.release()
        }
        feedView = null
        layoutParams = null
        attached = false
        progress = 0f
        dragging = false

        if (!isChangingConfigurations) {
            callback = null
        }
    }

    fun onScrollStarted() {
        if (!attached) return
        animator?.cancel()
        dragging = true
        feedView?.onPanelWillOpen()
    }

    /** The launcher is dragging. Follow it; do not echo back, or we fight it. */
    fun onScroll(value: Float) {
        if (!attached) return
        applyProgress(value.coerceIn(0f, 1f))
    }

    fun onScrollEnded() {
        dragging = false
        // The launcher settles the position itself and then calls openOverlay
        // or closeOverlay, so there is nothing to decide here.
    }

    fun open(animate: Boolean) {
        if (!attached) return
        feedView?.onPanelWillOpen()
        animateTo(1f, animate)
    }

    fun close(animate: Boolean) {
        if (!attached) return
        animateTo(0f, animate)
    }

    fun onPause() {
        feedView?.onPanelPaused()
    }

    fun onResume() {
        feedView?.onPanelResumed()
    }

    /**
     * The launcher reporting its own lifecycle. Bit 0 is "started", bit 1 is
     * "resumed"; with neither set there is no point holding a refresh timer.
     */
    fun onActivityState(state: Int) {
        if (state and ACTIVITY_RESUMED != 0) feedView?.onPanelResumed() else feedView?.onPanelPaused()
    }

    fun destroy() = detach(isChangingConfigurations = false)

    private fun animateTo(target: Float, animate: Boolean) {
        animator?.cancel()

        if (!animate) {
            applyProgress(target)
            callback?.overlayScrollChanged(target)
            return
        }

        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = (ANIM_MS * Math.abs(target - progress)).toLong().coerceAtLeast(1L)
            interpolator = DecelerateInterpolator(DECELERATION)
            addUpdateListener { a ->
                val v = a.animatedValue as Float
                applyProgress(v)
                // Drive the launcher so the workspace moves with the panel.
                callback?.overlayScrollChanged(v)
            }
            start()
        }
    }

    private fun applyProgress(value: Float) {
        progress = value
        val view = feedView ?: return

        if (value <= 0f) {
            view.visibility = View.GONE
            view.alpha = 0f
            view.translationX = 0f
            setFocusable(false)
            view.onPanelClosed()
            return
        }

        if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE

        // Slide in from the left with a little parallax, and fade: the panel
        // travels a fraction of its width so it trails the workspace slightly
        // rather than moving lockstep with it.
        val width = if (view.width > 0) view.width.toFloat() else PARALLAX_FALLBACK_PX
        view.translationX = -(1f - value) * width * PARALLAX
        view.alpha = value

        // Focusable only once essentially open, so the launcher keeps input
        // while the panel is mid-swipe and the back key reaches us when it is
        // the surface actually in front of the reader.
        setFocusable(value >= FOCUS_THRESHOLD && !dragging)

        if (value >= 1f) view.onPanelOpened()
    }

    /**
     * Toggle window focusability in place.
     *
     * A focusable panel is what lets the back key close it; a non-focusable one
     * is what lets the launcher stay interactive while the panel is only
     * partly on screen.
     */
    private fun setFocusable(focusable: Boolean) {
        val lp = layoutParams ?: return
        val view = feedView ?: return

        val wanted = if (focusable) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (wanted == lp.flags) return

        lp.flags = wanted
        runCatching { windowManager.updateViewLayout(view, lp) }

        if (focusable) {
            view.isFocusableInTouchMode = true
            view.requestFocus()
            view.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    close(animate = true)
                    true
                } else {
                    false
                }
            }
        } else {
            view.setOnKeyListener(null)
        }
    }

    private companion object {
        const val ANIM_MS = 220f
        const val DECELERATION = 1.6f
        const val PARALLAX = 0.28f

        /** Used only before the view has been measured once. */
        const val PARALLAX_FALLBACK_PX = 1080f

        const val FOCUS_THRESHOLD = 0.98f

        /** Bit 1 of the launcher activity state: resumed. */
        const val ACTIVITY_RESUMED = 2
    }
}
