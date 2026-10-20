package com.harithkavish.newsfeed.overlay

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * What a launcher binds to when you pick this app as the -1 screen.
 *
 * The launcher looks for a service answering the action in the manifest,
 * binds with a data URI of the form `app://<launcher-package>:<uid>`, and
 * expects back a binder speaking `ILauncherOverlay` ([OverlayBinder]).
 *
 * One binder per bind. Launchers rebind on configuration change and after
 * their own process restarts, and each of those wants a panel attached to the
 * new window rather than the old one's leftovers.
 */
class LauncherOverlayService : Service() {

    private val binders = mutableListOf<OverlayBinder>()

    override fun onBind(intent: Intent?): IBinder {
        val binder = OverlayBinder(this)
        binders += binder
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Let onBind run again on the next connect rather than reusing a
        // binder whose window token belongs to a launcher window that is gone.
        binders.forEach { it.destroy() }
        binders.clear()
        return false
    }

    override fun onDestroy() {
        binders.forEach { it.destroy() }
        binders.clear()
        super.onDestroy()
    }
}
