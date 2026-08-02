# Session handoff — 0.144 to 0.149, minification closed and the reader polished

Written 2026-08-02, evening. Nothing here supersedes anything.
`SESSION_HANDOFF_0.83.md`, `SESSION_HANDOFF_0.87.md`, `SESSION_HANDOFF_0.106.md`,
`SESSION_HANDOFF_0.120.md` and `SESSION_HANDOFF_0.143.md` all remain live
reference. `SESSION_HANDOFF_0.122.md` and `SESSION_HANDOFF_0.134.md` were
deleted this session and folded into `PROJECT_HANDOFF.md` §9.

Six releases. **The minification card closed** on a cause nothing in the build
file could have predicted, and the three releases after it are reader polish
taken off the board.

---

## 0. Read this before touching anything

**1. Minification is DONE and the tree is in a verified R8-on state.**
23,869,296 → 11,731,348 bytes. Verified on device: launch, 20/20 extensions,
browse, series, reader, source settings, Asura Scans from Browse, Library and
Downloads, and global search pinned-only with Asura Scans pinned. This
supersedes `SESSION_HANDOFF_0.143.md` §0's warning that the published APK was
expected to crash.

**2. The cause was a JNI `FindClass`, and four attempts were spent in the wrong
category.** §2. `okhttp-zstd` ships `libzstd-kmp.so`, whose static initialiser
names `com.squareup.zstd.ZstdCompressor` **from native code**. R8 reads dex and
cannot see that, so it deleted the class; ART treats a pending exception inside
a JNI call as fatal and calls `abort()`. Signal 6, no Java exception anywhere.

**3. A verified instrument's silence is evidence. That is what broke this
open.** `CrashLog` was proven to fire under R8 at 0.143. When it then came back
empty for the Asura Scans crash, the emptiness stopped being a gap and became a
finding — it ruled out the entire missing-keep category in one step, because
those surface as ordinary `Throwable`s and get caught. That is what sent this to
logcat. **Six releases of inference were built on this instrument before anyone
checked that it fires**; the whole of its value arrived in the release after it
was verified.

**4. The `onRenderProcessGone` hypothesis was wrong here and is still a real
bug.** §3. It explained the evidence well enough to survive two releases and it
was not the cause. It is now Trello card 73 on its own merits, and **the line on
that card blaming the Asura Scans crash should be struck** — leaving it there
sends the next session after a solved fault.

**5. 0.149 is UNTESTED.** §5. It is the only unverified thing in the tree.

---

## 1. The releases

| | What | Commit | State |
|---|---|---|---|
| 0.144 | R8 off; the crash is not a Java exception | `4d5247c` | Verified — Asura Scans opens |
| 0.144 | Unminified measurement: R8 *is* implicated | `60eac92` | Measurement only |
| 0.145 | R8 on again, purely to capture a logcat tombstone | `a3a8e34` | Crashed as intended |
| 0.146 | `-keep class com.squareup.zstd.** { *; }` | `9626e3e` | **Verified — card closed** |
| — | Handoff rewritten around the actual cause | `cae1fc4` | docs |
| 0.147 | Animate the double-tap zoom | `3b7470c` | Verified |
| 0.148 | Chapter turns wait for you | `0cb55ab` | Verified |
| 0.149 | `ListScrollHandle`; chapter list is the first caller | `b3d681b` | **UNTESTED** |

All six built green. No red CI this session.

---

## 2. The zstd finding

From a logcat tombstone, which is the only place it was ever going to appear:

```
JNI DETECTED ERROR IN APPLICATION: JNI FindClass called with pending exception
java.lang.ClassNotFoundException: Didn't find class
"com.squareup.zstd.ZstdCompressor"
```

**Why only one source of 37:** Asura Scans answers with `Content-Encoding: zstd`.
Nothing else reached the decompressor at all, which is also why every earlier
single-source test passed and why global search looked implicated — it is the
one screen that touches every source at once.

**Why `okhttp3.**` did not cover it:** that package holds the interceptor. The
implementation is under `com.squareup.zstd`, three packages from anything named
in `source-api/build.gradle.kts`.

**The generalisation, now written into `proguard-rules.pro`: the `api` list is
necessary and not sufficient.** A dependency that ships a native library can
name classes from JNI that appear nowhere in Java, so keep its whole
implementation package rather than the API surface the app compiles against.

**What cost the time:** the first two R8 failures were genuinely missing keeps,
so the category was never questioned. Four attempts treated a native abort as a
keep problem. The category was only abandoned when an instrument that had
finally been verified came back silent.

---

## 3. The hypothesis that was wrong, and why it is worth keeping

0.144 reasoned: the process dies with no Java exception; `CloudflareInterceptor`
drives a headless WebView; nothing in this codebase overrides
`onRenderProcessGone`; an unhandled renderer death takes the host app with it and
leaves no Java exception; Asura Scans is Cloudflare-protected. That is a good
chain and it was wrong.

It also briefly cast doubt on R8 being involved at all — correctly, since R8 does
not touch the renderer, which is Chrome in another process. **The measurement
that resolved it was cheap and had never been run**: open Asura Scans on an
unminified build. 0.144 with R8 off opens it normally, so the fault needs R8, and
the renderer story could not explain that.

**Two things follow.** A hypothesis that explains the evidence is not a diagnosis
— this one survived two releases on explanatory fit alone. And when a hypothesis
implicates one of two variables, the measurement that separates them is usually
cheaper than the one that confirms either.

`onRenderProcessGone` is still unhandled and this app is still one renderer
crash away from dying on any Cloudflare-protected source. It is Trello card 73.

---

## 4. The reader, 0.147 and 0.148

### Double-tap zoom animates (0.147)

200ms on `LinearOutSlowInEasing`, which is SY's `ANIMATOR_DURATION_TIME` and its
interpolator.

- **One 0..1 driver for scale and both translations, not three animations.** SY
  uses an `AnimatorSet` for the same reason: three independent springs let the
  content slide while it grows.
- **Driven explicitly from the double-tap branch.** An `animateFloatAsState` on
  `stripScale` would have been three lines, looked identical on a double tap, and
  put every *pinch* frame 200ms behind the fingers. **A blanket animation cannot
  tell the two sources of a scale change apart**, so this needed a flag, not a
  modifier.
- **A live pinch cancels the animation** (`zoomAnim` job). Without it there are
  two writers on one value and the pinch drifts.
- **Pan is clamped per frame against that frame's scale**, not interpolated to
  the target. The overhang grows with the zoom, so an unclamped pan outruns the
  content on the way in; on the way out the overhang shrinks to nothing and the
  clamp walks the pan back to centre instead of leaving an edge gap.

### Chapter turns wait for you (0.148)

Reported as "too instant". Two causes.

**1. The strip never got `settledPage`** — an asymmetry left by 0.130. Paged mode
reads `pagerState.settledPage` precisely so a fling cannot turn the chapter from
a page nobody stopped on. The strip's `rowFullyVisible` queries the **live**
layout, so a fling that carried the transition row on screen turned the chapter
while the list was still moving. `!listState.isScrollInProgress` is the strip's
`settledPage` — it says the same thing to a `LazyColumn` that `settledPage` says
to a pager.

**2. There was no dwell.** The turn fired the moment the row qualified, so the
transition row was never actually seen. `TRANSITION_DWELL_MS = 550`.

**The shape worth keeping: the dwell is a delay *inside* the `LaunchedEffect`,
not a timer beside it.** The effect is cancelled when its key changes, so
scrolling off the row before the wait is up cancels the turn with no bookkeeping
and no pending-turn flag to get wrong. Nothing has to remember that a turn was
pending.

Applied to **both** modes. The report was about the strip, but a pager settling
on a transition page has the same "you never saw it" problem, and one behaviour
beats two. If the timing wants tuning it is one constant in `ReaderScreen.kt`.

---

## 5. 0.149 — `ListScrollHandle`, and what has not been run

The card said three lines per caller. **It was five** — seek state, a keyed
effect, and four readings off the state — and five lines copied six times is five
chances to reintroduce the 0.133 drag lag. So `ListScrollHandle` in `Ui.kt` does
the wiring once and the next caller genuinely is three lines.

The core `ScrollHandle` is unchanged and still takes four integers rather than a
state object: `LazyGridState` and `LazyListState` share no supertype exposing
what it needs, and the library grid still calls it directly.

**First caller is the series chapter list**, the longest scroll in the app after
the library.

- **`totalItems` is `visible.size + 3`, counting the *list's* items rather than
  the chapters.** The cover header, the actions block and the trailing spacer are
  all lazy items and `scrollToItem` counts them. This is the arithmetic most
  likely to be wrong and its symptom is the handle not reaching the last chapter.
- **It spans `visible`, not `chapters`**, so the handle still reaches the end
  under a filter — same reasoning as the selection bar's select-all.

**Caught while writing the commit, and worth recording because this codebase
already warned about it:** inserting before `internal fun ScrollHandle` landed
**between its `@Composable` and the declaration**, leaving the annotation
attached to the new KDoc. That is §5's "an anchor-based edit can land inside a
declaration's annotations", verbatim, in the session that added a grep for it.
Both declarations are annotated now and the check greps for it.

**Nothing in 0.149 has been exercised on device.** §7 is the test pass.

---

## 6. The board

Closed this session: **Minification**, **Asura Scans crashed under R8**, **Does
the crash log work under R8**, **Animate the double-tap zoom** (the card is now
the fling only), **Scrolling to next/previous chapter was too instant**.

Opened this session: **`onRenderProcessGone`** (card 73), **Manhwa18 no
chapters** (card 74, a connection probe with no diagnosis yet).

Still open and untouched:

- **The 20/37 vs 26/95 discrepancy.** One tap of the diagnose screen settles it
  and it is the acceptance criterion on a card that cannot otherwise be met.
- **Fling the zoom pan.** `Modifier.transformable` does not expose gesture
  velocity, so it needs a custom detector — and 0.101 and 0.102 are two custom
  detectors in this reader that never fired once between them. **Read
  `SESSION_HANDOFF_0.106.md` §5 before starting.** The clamp and the 3x maximum
  are already SY's exact numbers.
- **Scroll handles on every other list** — the chapter list has one now, and
  `ListScrollHandle` makes the rest three lines each.
- **Scroll-to-refresh elsewhere**, **a feed tab in Browse**, **SY theme
  settings**, **tags on the series chevron**, **videos**.
- **AHottie**, **Coomer**, **all-filters-enabled**; BeeHentai and Elite Babes
  upstream.

---

## 7. The test pass for 0.149

The handle only. Everything else in this range is verified.

1. **It appears and lingers.** Scroll a long chapter list; the handle fades in
   while scrolling and lingers ~1.5s after the list settles.
2. **It stays hidden on a short series** whose chapters fit on screen.
3. **It reaches the last chapter.** Drag it to the bottom. This is the
   `visible.size + 3` arithmetic — three lazy items are not chapters, so the
   failure mode is stopping short of or overshooting the end.
4. **It reaches the end under a filter.** Filter to Unread or Downloaded, then
   drag to the bottom.
5. **It tracks a fast drag** rather than arriving where the finger was half a
   second ago. That is the 0.133 lag and the keyed-`LaunchedEffect` fix.
6. **The thumb moves when you scroll the list normally**, not only when dragging.
   A handle that never moves means two state objects rather than one hoisted one.
7. **The row swipes still work** — swipe a chapter left and right to mark read
   and to bookmark, near the handle's edge.
8. **Long-press multi-select still starts** with the handle visible.
9. **The top bar still fades** correctly after a handle seek, not just after a
   finger scroll.

While you are in there: **Settings → diagnose, and read the extension and source
counts.** That settles card 72 for free.

---

## 8. State of the tree

Head is `b3d681b` (0.149). Build environment unchanged from 0.134: Kotlin
2.2.21, AGP 8.5.2, Gradle 8.9, JDK 17, compileSdk 36, targetSdk 34, minSdk 24,
OkHttp 5.4.0, kotlinx-serialization 1.9.0, Compose BOM 2024.09.03, Coil 2.7.0,
`me.saket.swipe:swipe:1.3.0`.

**`isMinifyEnabled = true` on debug, and it works.** The comment above that flag
was stale for three releases — it still read *OFF AGAIN IN 0.139* and *DO NOT
FLIP THIS AGAIN WITHOUT READING THE CRASH LOG FIRST* while the flag was `true`.
Corrected this session. `proguard-rules.pro` carries the derivation and all five
attempts.

No new files. `ReaderScreen.kt`, `Ui.kt`, `SourceBrowseScreens.kt` and
`WhatsNew.kt` changed.

**Verified on device this session:** 0.144 opening Asura Scans with R8 off;
0.145 crashing on Asura Scans under R8 with a logcat tombstone captured; 0.146
under R8 — launch, 20/20 extensions, browse, series, reader, source settings,
Asura Scans from Browse, Library and Downloads, global search pinned-only with
Asura Scans pinned; 0.147's double-tap in and out, pinch still instant, and a
pinch taking over mid-animation; 0.148 in both modes, both directions, with
read-marking checked explicitly.

**Not verified: all of 0.149.** §7.

**Housekeeping.** The PAT used this session was pasted into a chat and should be
rotated. `debug.keystore` and `release.keystore` are still committed at the repo
root, and the release keystore is this app's signing identity.
