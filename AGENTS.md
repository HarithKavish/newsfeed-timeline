# Agent Instructions

This repository is part of the **HarithKavish ecosystem**.

**Before changing anything**, read
[AGENT_BOOTSTRAP.md](https://github.com/HarithKavish/harithkavish-governance/blob/main/AGENT_BOOTSTRAP.md)
and follow it. See [GOVERNANCE.md](GOVERNANCE.md) for what governs this repository.

Do not begin implementation work before discovery is complete.

## Hard stops

A reminder, not the rule. These restate doctrine articles so an agent that reads nothing
else still has the guardrails. Governance is authoritative; if these ever disagree with
it, governance wins.

- Do not commit to the production branch (Article 6).
- Do not commit secrets or credentials (Article 5, SECURITY).
- Do not redefine design foundations locally (Article 4).
- Do not copy governance or the design system into this repository (Article 3).
- Do not act outside the scope you were given (Article 9).

## About this repository

An Android app that puts a Discover-style news feed on the launcher's −1 screen —
the panel to the left of the first home screen. It has no app-drawer icon and is
reached from Settings, because it is a feed provider rather than something you
open. Every story it shows comes from the Timeline news engine.

## Working here

**This repository owns no news logic, and must not grow any.** Ingestion, dedup,
clustering, narrative generation and translation all live in `workers/news` in
[HarithKavish/Timeline](https://github.com/HarithKavish/Timeline). This app is a
second *reader* of that one engine, alongside timeline.harithkavish.com. If a feed
behaviour needs changing — which outlets are pulled, how topics cluster, what a
category means — the change belongs in Timeline. Adding an RSS parse, a scraper or
a second data source here would fork the engine, which is the one thing this
codebase is arranged to prevent.

`data/Models.kt` mirrors `src/types/news.ts` in Timeline field for field. When that
file changes there, change it here; do not let the two drift into different ideas
of what a topic is.

**Categories come from the engine, not from a constant.** The app derives the
category list from `/outlets` at runtime and falls back to the five tiers only when
that fails. A category added in the worker must reach this app without a release,
so do not replace that with a hardcoded enum — `Category` is a data class rather
than an enum for exactly this reason.

**The overlay protocol is load-bearing and untyped.** `overlay/OverlayBinder.kt`
implements Google's `ILauncherOverlay` by raw binder transaction code. The codes
are method positions in an interface this repository does not own: reordering,
inserting or removing a case breaks the −1 screen **silently**, with no build
error and no crash — the launcher simply stops offering the panel. Treat that file
as a wire format, not as ordinary Kotlin.

**Weight is a feature.** The release APK is ~163 KB with one dependency
(RecyclerView). `core-ktx` was removed deliberately because it pulls
`lifecycle-runtime` and with it the whole coroutines runtime. Before adding any
dependency, check `./gradlew :app:dependencies` for what it drags in, and say in
the pull request what it cost.

**Verifying the −1 screen needs a device.** There is no emulator path for this:
it requires a launcher that offers a feed-provider setting (Lawnchair, Omega, and
other Launcher3 forks), with this app selected in it. `PreviewActivity` runs the
same `FeedView` in an ordinary window, so feed and data problems can be reproduced
without a launcher — but window attachment, scroll hand-off and focus behaviour
cannot.
