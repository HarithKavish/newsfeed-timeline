package com.harithkavish.newsfeed.overlay

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.view.WindowManager

/**
 * `ILauncherOverlay`, implemented directly on [Binder].
 *
 * This is the interface a launcher's -1 screen talks to. It is Google's, not a
 * public Android API: there is no SDK class to subclass, only a binder
 * descriptor and a fixed method order that launchers supporting the Google-app
 * feed protocol (Lawnchair, Omega, and the other Launcher3 forks) all speak.
 *
 * Two ways to implement it: vendor the AIDL under the
 * com.google.android.libraries.launcherclient package, or write onTransact by
 * hand. This takes the second, for three reasons -- the AIDL would have to
 * declare framework parcelables it does not own, the generated stub is dead
 * weight for an interface where most methods are no-ops here, and writing the
 * parcel reads out explicitly means the contract is visible in this file
 * instead of implied by a build step.
 *
 * The transaction codes are the method positions in the interface. They are
 * the contract: the launcher calls code 2 to mean onScroll, so reordering
 * anything below silently breaks the -1 screen rather than failing to build.
 *
 * Binder calls arrive on a pool thread. Everything that touches the window or
 * the views is posted to the main thread.
 */
internal class OverlayBinder(context: Context) : Binder() {

    private val panel = OverlayPanel(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())

    init {
        // Makes getInterfaceDescriptor() answer correctly, which is what the
        // launcher checks before it trusts this binder at all.
        attachInterface(null, DESCRIPTOR)
    }

    /** Method positions in ILauncherOverlay, in declaration order. */
    private object Tx {
        const val START_SCROLL = FIRST_CALL_TRANSACTION + 0
        const val ON_SCROLL = FIRST_CALL_TRANSACTION + 1
        const val END_SCROLL = FIRST_CALL_TRANSACTION + 2
        const val WINDOW_ATTACHED = FIRST_CALL_TRANSACTION + 3
        const val WINDOW_DETACHED = FIRST_CALL_TRANSACTION + 4
        const val CLOSE_OVERLAY = FIRST_CALL_TRANSACTION + 5
        const val ON_PAUSE = FIRST_CALL_TRANSACTION + 6
        const val ON_RESUME = FIRST_CALL_TRANSACTION + 7
        const val OPEN_OVERLAY = FIRST_CALL_TRANSACTION + 8
        const val REQUEST_VOICE_DETECTION = FIRST_CALL_TRANSACTION + 9
        const val GET_VOICE_SEARCH_LANGUAGE = FIRST_CALL_TRANSACTION + 10
        const val IS_VOICE_DETECTION_RUNNING = FIRST_CALL_TRANSACTION + 11
        const val HAS_OVERLAY_CONTENT = FIRST_CALL_TRANSACTION + 12
        const val WINDOW_ATTACHED_2 = FIRST_CALL_TRANSACTION + 13
        const val UNUSED_METHOD = FIRST_CALL_TRANSACTION + 14
        const val SET_ACTIVITY_STATE = FIRST_CALL_TRANSACTION + 15
        const val START_SEARCH = FIRST_CALL_TRANSACTION + 16
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            INTERFACE_TRANSACTION -> {
                reply?.writeString(DESCRIPTOR)
                return true
            }

            Tx.START_SCROLL -> {
                data.enforceInterface(DESCRIPTOR)
                main.post { panel.onScrollStarted() }
                reply?.writeNoException()
                return true
            }

            Tx.ON_SCROLL -> {
                data.enforceInterface(DESCRIPTOR)
                val progress = data.readFloat()
                main.post { panel.onScroll(progress) }
                reply?.writeNoException()
                return true
            }

            Tx.END_SCROLL -> {
                data.enforceInterface(DESCRIPTOR)
                main.post { panel.onScrollEnded() }
                reply?.writeNoException()
                return true
            }

            Tx.WINDOW_ATTACHED -> {
                data.enforceInterface(DESCRIPTOR)
                // AIDL writes a presence int before a nullable parcelable.
                val attrs = if (data.readInt() != 0) {
                    WindowManager.LayoutParams.CREATOR.createFromParcel(data)
                } else {
                    null
                }
                val callbackBinder = data.readStrongBinder()
                data.readInt() // client options, unused here
                main.post { panel.attach(attrs, callbackBinder?.let(::OverlayCallback)) }
                reply?.writeNoException()
                return true
            }

            Tx.WINDOW_ATTACHED_2 -> {
                data.enforceInterface(DESCRIPTOR)
                // The newer entry point. Same job, but the window attributes
                // and the launcher's configuration arrive inside a Bundle.
                val bundle = if (data.readInt() != 0) {
                    Bundle.CREATOR.createFromParcel(data).apply {
                        classLoader = WindowManager.LayoutParams::class.java.classLoader
                    }
                } else {
                    null
                }
                val callbackBinder = data.readStrongBinder()
                @Suppress("DEPRECATION")
                val attrs = bundle?.getParcelable<WindowManager.LayoutParams>(KEY_LAYOUT_PARAMS)
                main.post { panel.attach(attrs, callbackBinder?.let(::OverlayCallback)) }
                reply?.writeNoException()
                return true
            }

            Tx.WINDOW_DETACHED -> {
                data.enforceInterface(DESCRIPTOR)
                val isChangingConfigurations = data.readInt() != 0
                main.post { panel.detach(isChangingConfigurations) }
                reply?.writeNoException()
                return true
            }

            Tx.CLOSE_OVERLAY -> {
                data.enforceInterface(DESCRIPTOR)
                val closeFlags = data.readInt()
                main.post { panel.close(animate = closeFlags and FLAG_ANIMATE != 0) }
                reply?.writeNoException()
                return true
            }

            Tx.OPEN_OVERLAY -> {
                data.enforceInterface(DESCRIPTOR)
                val openFlags = data.readInt()
                main.post { panel.open(animate = openFlags and FLAG_ANIMATE != 0) }
                reply?.writeNoException()
                return true
            }

            Tx.ON_PAUSE -> {
                data.enforceInterface(DESCRIPTOR)
                main.post { panel.onPause() }
                reply?.writeNoException()
                return true
            }

            Tx.ON_RESUME -> {
                data.enforceInterface(DESCRIPTOR)
                main.post { panel.onResume() }
                reply?.writeNoException()
                return true
            }

            Tx.HAS_OVERLAY_CONTENT -> {
                data.enforceInterface(DESCRIPTOR)
                // Answering false here is how a provider says "nothing to show";
                // the launcher then disables the swipe. This one always has a
                // feed, even if that feed is a cached or empty state.
                reply?.writeNoException()
                reply?.writeInt(1)
                return true
            }

            Tx.SET_ACTIVITY_STATE -> {
                data.enforceInterface(DESCRIPTOR)
                val state = data.readInt()
                main.post { panel.onActivityState(state) }
                reply?.writeNoException()
                return true
            }

            // Voice and search belong to the Google app's version of this
            // interface. A news panel has nothing to offer them, but they must
            // still be answered in the shape the caller expects, or the binder
            // read fails and takes the connection down with it.
            Tx.REQUEST_VOICE_DETECTION -> {
                data.enforceInterface(DESCRIPTOR)
                data.readInt()
                reply?.writeNoException()
                return true
            }

            Tx.GET_VOICE_SEARCH_LANGUAGE -> {
                data.enforceInterface(DESCRIPTOR)
                reply?.writeNoException()
                reply?.writeString(null)
                return true
            }

            Tx.IS_VOICE_DETECTION_RUNNING -> {
                data.enforceInterface(DESCRIPTOR)
                reply?.writeNoException()
                reply?.writeInt(0)
                return true
            }

            Tx.START_SEARCH -> {
                data.enforceInterface(DESCRIPTOR)
                data.createByteArray()
                if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data)
                reply?.writeNoException()
                reply?.writeInt(0)
                return true
            }

            Tx.UNUSED_METHOD -> {
                data.enforceInterface(DESCRIPTOR)
                reply?.writeNoException()
                return true
            }
        }
        return super.onTransact(code, data, reply, flags)
    }

    /** Called when the service is being torn down. */
    fun destroy() {
        main.post { panel.destroy() }
    }

    companion object {
        const val DESCRIPTOR = "com.google.android.libraries.launcherclient.ILauncherOverlay"

        /** Key the launcher uses for its window attributes inside windowAttached2. */
        private const val KEY_LAYOUT_PARAMS = "layout_params"

        /** Bit 0 of the open/close flags: animate rather than jump. */
        private const val FLAG_ANIMATE = 1
    }
}
