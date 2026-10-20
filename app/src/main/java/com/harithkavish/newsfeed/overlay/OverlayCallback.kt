package com.harithkavish.newsfeed.overlay

import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException

/**
 * The launcher's end of the overlay conversation.
 *
 * The launcher hands us an `ILauncherOverlayCallback` binder when it attaches
 * its window. We only ever call two things on it, so rather than pull in the
 * AIDL and its generated proxy this writes the two transactions by hand --
 * which is exactly what the generated proxy would do, minus the generated
 * classes.
 *
 * The parcel layout is AIDL's, not ours: an interface token, then the
 * arguments in declaration order, sent one-way. Getting this wrong is silent
 * (the launcher simply stops following the scroll), so the two codes below are
 * the method positions in `ILauncherOverlayCallback`, which has exactly these
 * two methods in this order.
 */
internal class OverlayCallback(private val binder: IBinder) {

    /**
     * Tell the launcher where the overlay now sits, 0 (closed) to 1 (open).
     *
     * This is what makes the workspace track a drag that started inside the
     * panel, and what animates the home screen back when the panel closes
     * itself. Without it the launcher believes the overlay is still wherever
     * it last put it.
     */
    fun overlayScrollChanged(progress: Float) = oneway(TX_SCROLL_CHANGED) { it.writeFloat(progress) }

    /**
     * Tell the launcher whether the overlay is usable.
     *
     * Bit 0 is "connected": with it clear, the launcher treats the -1 screen as
     * unavailable and refuses the swipe entirely. Bit 1 is "active".
     */
    fun overlayStatusChanged(status: Int) = oneway(TX_STATUS_CHANGED) { it.writeInt(status) }

    private inline fun oneway(code: Int, writeArgs: (Parcel) -> Unit) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            writeArgs(data)
            binder.transact(code, data, null, IBinder.FLAG_ONEWAY)
        } catch (_: RemoteException) {
            // The launcher died or was reconfigured. Nothing to recover here --
            // it will bind again and hand us a new callback.
        } finally {
            data.recycle()
        }
    }

    companion object {
        const val DESCRIPTOR = "com.google.android.libraries.launcherclient.ILauncherOverlayCallback"

        private const val TX_SCROLL_CHANGED = IBinder.FIRST_CALL_TRANSACTION + 0
        private const val TX_STATUS_CHANGED = IBinder.FIRST_CALL_TRANSACTION + 1

        /** Overlay is connected and has content. Anything less disables the swipe. */
        const val STATUS_CONNECTED = 1
        const val STATUS_ACTIVE = 2
    }
}
