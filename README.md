# newsfeed-timeline

A Discover-style news feed for the Android **−1 screen** — the panel you reach by
swiping right from the first home screen, where the Google app's Discover feed
normally sits.

It has **no icon in the app drawer**. That is deliberate: this is a feed provider,
not an app you open. You select it once in your launcher's settings, and after
that it is the −1 screen. Settings → Apps → Newsfeed Timeline still opens it, for
choosing categories and previewing the feed.

Every story comes from the **Timeline news engine** — the same backend that serves
[timeline.harithkavish.com](https://timeline.harithkavish.com). This app runs no
ingestion of its own.

## What you see

The engine does not serve a list of headlines. It ingests trusted RSS feeds across
geographic tiers, clusters them with multilingual embeddings into **topics** (one
real-world story), and writes each distinct development in a topic as a **thread
entry** in plain prose, with every outlet that corroborated it attached.

So a card is a story, not an article:

> **International** · *Developing*
> Ukrainians are losing more sleep, shopping for food is becoming harder and
> travel is often fraught with risk and delays.
> 4 outlets · 64 updates · 2h ago

Open it and you get the thread — what happened, in order, newest first — and the
source article behind each step, which opens at the outlet's own site.

## Categories

The engine tags every topic with a category. Today those are the five geographic
tiers it ingests (International, National, State, District, City); the app reads
that list from the engine at runtime rather than hardcoding it, so **a category
added in the worker appears here without an app release**.

Follow all of them or pick the ones you want, in Settings. An empty selection
means "everything", which is also what a newly added category falls into — a new
category shows up rather than being silently excluded.

## Where it lives

No hosted surface. It is an APK, and the news it reads is served by
[HarithKavish/Timeline](https://github.com/HarithKavish/Timeline).

## Build and install

Needs JDK 17+ and an Android SDK with platform 34.

```bash
git clone https://github.com/HarithKavish/newsfeed-timeline.git
cd newsfeed-timeline
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # forward slashes on Windows
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`./gradlew assembleRelease` produces the minified build (~163 KB, unsigned).

To point at a local engine instead of the deployed worker, change
`NEWS_API_BASE` in `app/build.gradle.kts` and run Timeline's
`npm --prefix workers/news run dev`.

## Selecting it as the −1 screen

This needs a launcher that lets you choose a feed provider. Launcher3 forks do —
**Lawnchair**, **Omega**, and others; the stock Pixel launcher is hardwired to the
Google app and cannot be repointed.

1. Install the APK. It will not appear in your app drawer — that is correct.
2. In your launcher's settings, find the feed / −1 screen / "minus one page"
   provider setting and choose **Newsfeed Timeline**.
3. Restart the launcher if it asks, then swipe right from the first home screen.

To change categories afterwards: **Settings → Apps → Newsfeed Timeline → Open**.

## How it is put together

| Piece | What it does |
|---|---|
| `data/` | The engine's shapes and the only code that talks to it |
| `overlay/` | The −1 screen: the launcher overlay binder, and the window it attaches |
| `ui/` | `FeedView` (used by both the panel and the preview), the cards, the story screen |

Two things are worth knowing before changing anything:

**`overlay/OverlayBinder.kt` is a wire format.** It implements Google's
`ILauncherOverlay` by raw binder transaction code, because there is no public SDK
interface for this. The codes are method positions in an interface this repository
does not own — reorder a case and the −1 screen stops working with no build error.

**There is one dependency.** RecyclerView. HTTP is `HttpURLConnection` and JSON is
`org.json`, both from the platform, which is why the release build is ~163 KB.
`core-ktx` was removed once it turned out to pull the entire coroutines runtime in
behind `lifecycle-runtime`.

## Status

Base version. Working: the feed, categories and following, the story thread,
source links, disk cache, paging, light/dark.

**Not yet verified on a device:** the overlay window attachment and scroll
hand-off. The binder protocol is implemented to the known contract, but it can
only be confirmed against a real launcher, and it had not been at the time this
was written. See [AGENTS.md](AGENTS.md).

Not built yet: per-topic follow, notifications, images (the engine serves none),
release signing.

## Ecosystem

Governed by HarithKavish Governance — see [GOVERNANCE.md](GOVERNANCE.md), and
[AGENTS.md](AGENTS.md) before making changes.
