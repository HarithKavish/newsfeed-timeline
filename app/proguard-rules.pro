# The launcher binds to LauncherOverlayService by the action in the manifest and
# talks to it over raw Binder transactions, so nothing in the app references
# these classes by name. R8 would otherwise be free to remove or rename them.
-keep class com.harithkavish.newsfeed.overlay.LauncherOverlayService { *; }
-keep class com.harithkavish.newsfeed.overlay.OverlayBinder { *; }

# Views inflated from XML are constructed reflectively.
-keep class com.harithkavish.newsfeed.ui.** { public <init>(android.content.Context, android.util.AttributeSet); }
