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

**5. 0.149 is verified and the tree is fully verified.** All nine checks in §7
passed on device, 2026-08-02 21:50. Nothing in 0.144–0.149 is now unexercised.

**6. The extension count is 20 / 37 and 26 / 95 is retired.** Read off the
diagnose screen in the same pass: 20 packages declaring `tachiyomi.extension`,
**Loaded OK 20/20**, 37 sources, 37 held in cache. The 20/20 is what settles it —
nothing is failing to load, so the gap was never a regression and never had
anything to do with R8. Whether six extensions were uninstalled after 0.122 or
26/95 was wrong when written is unresolved and is not worth another session.

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
| 0.149 | `ListScrollHandle`; chapter list is the first caller | `b3d681b` | Verified |

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

## 7. The test pass for 0.149 — ALL NINE PASSED

Run on device 2026-08-02 21:50. Kept as written because it is the list the next
scroll-handle caller should be run against, and items 3, 4 and 6 are the ones
that would have failed.

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

Also run: **Settings → diagnose.** 20 packages, 20/20 loaded, 37 sources. Card
72 closed — see §0 item 6.

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

**Verified on device this session:** all nine checks in §7 on 0.149 — the handle
appearing and lingering, staying hidden on a short series, reaching the last
chapter both unfiltered and under a filter, tracking a fast drag, the thumb
moving on an ordinary scroll, the row swipes and long-press multi-select still
working beside it, and the top bar fading correctly after a seek; the diagnose
screen reporting 20/20 extensions and 37 sources; 0.144 opening Asura Scans with R8 off;
0.145 crashing on Asura Scans under R8 with a logcat tombstone captured; 0.146
under R8 — launch, 20/20 extensions, browse, series, reader, source settings,
Asura Scans from Browse, Library and Downloads, global search pinned-only with
Asura Scans pinned; 0.147's double-tap in and out, pinch still instant, and a
pinch taking over mid-animation; 0.148 in both modes, both directions, with
read-marking checked explicitly.

**Nothing in this range is unverified.**

**Housekeeping.** The PAT used this session was pasted into a chat and should be
rotated. `debug.keystore` and `release.keystore` are still committed at the repo
root, and the release keystore is this app's signing identity.

---

## 9. AHottie, diagnosed from source and then corrected by three screenshots

No code shipped for this. It is here because the answer is three different
answers and one of them is "not a bug", which is worth more than a fix.

`extensions-source/src/all/ahottie`, 150 lines, v1.6.4, lib 1.6.
`@Source abstract class AHottie : KeiSource()` — the concrete class is
generated, which is the `.ExtensionGenerated` the diagnose screen shows.

**"No tabs" is this app working as designed, and it is not the 0.86 shape.**
AHottie declares `supportsLatest = false` and never overrides
`getFilterList(data)`; `KeiSource`'s default returns an empty `FilterList`, so
`supportsFilters` (`filterList.isNotEmpty()`) is false too.
`SourceBrowseScreens.kt:246` gates the whole chip row on
`supportsLatest || supportsFilters`, and its comment says why: a source with
neither would get a single chip that does nothing. **0.86's Roku Hentai fix was
the case where the row renders** — Roku *had* filters, so it drew a Filter chip
and no Popular, making a filter a one-way trip. AHottie has neither, so
suppressing the row is correct. Two sources, same symptom sentence, opposite
verdicts.

**CORRECTED at 22:22, and the correction is the point of this section.** Three
screenshots overturned two of the three conclusions below within an hour of
their being written. The overturned text is kept because the *method* that
produced it — read the artifact, don't guess — is right, and because what it got
wrong is instructive: **a plausible mechanism found in source is not a
diagnosis, and this is the second time this session that sentence has had to be
written** (§3 is the first, the renderer hypothesis). The corrections are in
§9a.

**"Chapters load forever" is the extension, and it is unbounded.**
`getPageList` is a `while (true)` following `a[rel=next]`, fetching a full HTML
document per iteration, with no page cap, no visited-set and no cycle guard.
`popularMangaParse` uses that *same* selector for listing pagination, so if a
gallery's `rel=next` points at the next gallery rather than the next page of
images, it walks the site. `getPageList` never returns, so
`loadPagesProgressively` never starts and the reader sits on spinners.

**This app has no ceiling on `getPageList` at all** — not a request count, a
wall clock, or a page count. That is its own card, deliberately: a ceiling
changes the fetch path for every source, and §6 of `SESSION_HANDOFF_0.83.md` is
0.79, a change with exactly that blast radius that rode along with a one-source
fix and was reverted the next release.

**"No covers" needs one on-device reading, and the app side is already ruled
out.** `mangaDetailsParse` sets `title` and `genre` and never `thumbnail_url`,
and `fetchMangaUpdate` returns it — but **nothing here blanks a good cover**:
`healCover` returns early on blank (`Library.kt:131`), `setCovers` skips blank
(`Library.kt:163`), and the series-open paths all read
`fetched.cover ?: entry.cover`. So the null is absorbed at every write. What is
left is either the listing parse (`.relative img` / `absUrl("src")` missing a
lazy-loaded attribute) or the image request itself, and **`CoverImage`'s debug
overlay separates those in one look** — URL absent versus URL present and
failing. That check is one series open and it has not been done.

### The Keiyoushi index changed shape, and the documented command is half-stale

`SESSION_HANDOFF_0.83.md` §5 and `SESSION_HANDOFF_0.87.md` §5 both fetch
`index.json` and treat it as a flat list. **It is now an object**: the entries
live at `extensionList.extensions`, alongside `name`, `badgeLabel`,
`signingKey` and `contact`. `len(json.load(...))` returns **5**, not 1368, and
reads as an empty repo rather than a changed shape.

The `grep -o '"apkUrl": *"[^"]*"'` line in 0.83 §5 still works, because it never
parsed the structure. The `python3 -c 'json.load'` idiom does not.

Entry shape now: `resources.apkUrl` / `iconUrl` / `jarUrl` (was a flat
`apkUrl`), plus `extensionLib`, `contentWarning`, and a `sources[]` array of
`{id, name, language, homeUrl}`. **1368 extensions as of 2026-08-02**, against
1365 in 0.83 and 1367 in 0.134.

---

## 9a. What the device said, and what it overturned

Three screenshots, 2026-08-02 22:22.

**Browse:** grey cells, correct titles, **no title initials and no debug
overlay**. **Series:** one chapter, `GALLERY`, dated 7 Apr 2026. **Reader:**
**"Page 1 of 36"**, spinners, network at 0.1 KB/s.

### The discriminator was a branch nobody was looking at

`CoverImage`'s `else` arm draws the **title's first two letters** when
`cover == null`. The cells are empty, so `cover` is **not** null. The overlay
only renders once Coil calls `onError`, and it is absent, so **no error has
fired yet**. Together: **the URL is present and the request is still pending.**
Not missing, not 403 — hanging.

That single observation is worth more than the 150 lines of extension source,
and it cost one screenshot.

### So it is one fault, not three

**AHottie's image requests never complete.** Every HTML path works — listing,
details, genre, chapter list, dates, and `getPageList` itself. Every *image*
hangs, in the browse grid and in the reader alike.

### What that overturns

- **"`getPageList` loops forever" was wrong.** It returned, at 36 pages. The
  code really is an unguarded `while (true)`, and that really is a hazard — it
  is simply not what is happening here. The ceiling card stays open with **no
  known instance**, and both cards now say so, because the trap is someone
  capping `getPageList` and believing they fixed AHottie.
- **"The listing parse misses a lazy-loaded attribute" was wrong.** The URL is
  there.
- **"No tabs is working as designed" stands.** That one was checked against
  `SourceBrowseScreens.kt:246`, not inferred from the extension. **The
  conclusion that survived is the one that was verified against this app's own
  code rather than reasoned from the artifact.**

### The general form, and it is now the third instance this session

**A mechanism that explains the symptom is not a diagnosis.** §3 has the
renderer-death version, which survived two releases. This is the same error made
from a better source — the artifact was in hand and read correctly, and the
reading still described a fault that was not occurring. The check that separates
them is always the same shape and always cheap: **find the branch whose presence
or absence distinguishes the candidates, and look at it.** Here it was two
letters of a title.

### Not yet separated

A challenge on the image host that hangs rather than 403s; an image host
differing from `baseUrl`, so the extension client's cookies and UA do not apply;
or `CloudflareInterceptor` burning its full 30s headless timeout per image (§7
item 6 of `PROJECT_HANDOFF.md`). Coil sending no `Referer` (§7 item 7) usually
403s fast rather than hanging, so it is the weakest of the four.

**Two readings settle it, both on instruments this app already has.** Leave
browse open until the requests time out, and the overlay prints the URL tail and
the reason. Or download the `GALLERY` chapter and read the queue — failed
downloads carry the page exception up instead of swallowing it, which turns a
silent hang into a named exception.

---

## 9b. The download queue said nothing, and why that is its own bug

The suggested reading in §9a — download the chapter and read the named failure —
was run. **The queue sat on "Starting" for eleven minutes and named nothing.**

### "Starting" is two states wearing one label

`DownloadQueueScreen.kt:410` and `:426` both branch on
`percent == null || percent == 0`, so the same label and the same indeterminate
bar cover:

1. **The page list has not come back.** `percent` is null.
2. **The page list came back and zero pages have landed.** `percent` is 0,
   because `DownloadService.fetchPages` stores `ready * 100 / total`.

The comment at `:406` describes only the first — "no percent yet means the page
list request hasn't come back". That is true of null and false of 0.

**The download queue is the screen that exists to say why a download is not
progressing, and it cannot distinguish the two states with different causes.**
The only reason we know AHottie is in state 2 is that the *reader*, a different
screen, separately reported "Page 1 of 36". Its own card is on the board.

**Store `ready` and `total`, not a percent.** "0 of 36" says what neither
"Starting" nor "0%" can, and a ratio cannot express "the denominator is not
known yet" while two integers can. `total` is already at the callback and is
thrown away. Fourth appearance of the three-state trap — present, said-zero,
never-recorded.

### Why nothing has failed yet, and the number that predicts when it will

`NetworkHelper` sets `connectTimeout` 30s, `readTimeout` 30s and
**`callTimeout` 2 minutes**. So no single call can hang forever — every image
request must end, one way or the other, within two minutes.

Which means the eleven minutes of silence is not one stuck request. It is
**36 page requests each burning up to their own timeout**, with `ready` pinned
at 0 the whole way, so the queue shows "Starting" throughout and
`ChapterDownloadException` is only raised at the *end*, once the adapter knows
which pages are missing. There is no per-page failure report on the way.

**The prediction, which is falsifiable: the download will fail on its own, with
a named exception, once every page has had its turn at the timeout.** If it
never does, `callTimeout` is not reaching this path and that is a much more
interesting finding.

### The cheap reading, and why the earlier attempt missed it

**Open AHottie's browse grid and do not navigate away for a full two minutes.**
`callTimeout` guarantees each cover request ends by then, `onError` fires, and
`CoverImage`'s overlay prints the URL tail and the reason — which is the one
fact that separates the remaining candidates, and in particular the **image
host**, which nobody has seen yet.

The 22:22 attempt could not have worked: browse was open for well under a minute
before moving to the series screen, and the timeout had not expired. **An
instrument that only reports on failure tells you nothing until the failure is
allowed to happen.**

---

## 9c. The answer: imgbox is unreachable, and the overlay said so in one line

Browse left open past the timeout, 22:43:

```
https://images2.imgbox.com/86/ca/gjf590ov_o.jpg
failed to connect to images2.imgbox.com/212.63.223.225 (port 443)
from /192.168.1.213 (port 44414) after 30000ms
```

Identical on every cell — different source ports, one destination IP.

**Neither an app bug nor an extension bug.** `ahottie.top` serves its images
from **imgbox**, and imgbox cannot be reached from this network.

### One string killed four candidates at once

- **DNS resolved.** There is an IP. Not a lookup failure.
- **It is `connectTimeout`** — 30000ms is `NetworkHelper`'s 30s — so **the TCP
  handshake never completed. Nothing was ever sent**, which makes every
  header-shaped explanation impossible in principle.
- **Not 403, not Cloudflare, not DDoS-Guard.** All three need a connection and
  return a response.
- **Not the missing `Referer`** (§7 item 7). It was already the weakest
  candidate and it is now dead: a `Referer` check cannot fire on a connection
  that never opens.
- **Not `CloudflareInterceptor`'s 30s headless timeout** (§7 item 6). The
  matching number is a coincidence; this is OkHttp's connect timeout on a plain
  image GET.

### Everything else falls out of it

**HTML worked throughout because `ahottie.top` is a different host and is
reachable.** Listing, details, genre, chapter list, dates and `getPageList` all
succeeded; every image failed. Two hosts, one blocked. That is the whole of
"AHottie has no covers and chapters load forever".

**The download sat on "Starting" for eleven minutes** because 36 pages each
burned a 30s connect timeout with `ready` pinned at 0. §9b's prediction holds
and the mechanism is confirmed.

**Blast radius, checked against all 1368 extensions:** AHottie does not hardcode
imgbox anywhere — the URLs come from the site at runtime. Only
`DarkLegacyComics` names it, for a single hardcoded thumbnail.

### What this cost, and the one line that would have saved it

Three rounds: a source read that produced a plausible wrong mechanism, a device
reading that overturned it, and a queue reading that named nothing. **The fact
that settled it was available in round two and was not collected**, because the
screen was left for under a minute and the instrument only speaks on failure.

**When an instrument reports only on failure, the failure has to be allowed to
happen, and the timeout is the number that says how long that takes.**
`callTimeout` 2 min and `connectTimeout` 30s were both in `NetworkHelper` the
whole time. Reading them first would have turned "leave it open" into "leave it
open for thirty seconds", and this would have been one round.

### Not closed yet

**Confirm whether it is this network.** Reopen AHottie on mobile data, or open
that URL in a browser on the same WiFi. Loading on cellular and not on WiFi
means an ISP or router level block on imgbox, and there is nothing here to fix.
Failing on both means imgbox is down or blocked further upstream. **Nothing
should be built until that is known** — and on either answer, the fix is not in
this app.

---

## 9d. The VPN attempt, and measuring the far end from somewhere else

**imgbox is up.** `images2.imgbox.com` resolves to `212.63.223.225`, `.226` and
`.227` from an unrelated network — **the same addresses the phone gets, so DNS
is not hijacked and the IP is genuine** — and all three accept TCP and complete
a TLS handshake with a valid certificate in **single-digit milliseconds**.

So the far end is fine and the failure is entirely in the path from this phone.

### The failure mode changed under VPN, and that is the finding

| | Message | Type |
|---|---|---|
| No VPN, 22:43 | `failed to connect to …/212.63.223.225 (port 443) from /192.168.1.213 (port 44414) after 30000ms` | `SocketTimeoutException` |
| VPN, 22:48 | `Failed to connect to images2.imgbox.com/212.63.223.225:443` | `ConnectException` |

The first prints a **source address and an elapsed timeout**: packets silently
dropped for a full 30 seconds, which is the signature of a firewall or DPI
blackhole rather than a refusal. The second has **neither** — it failed fast,
meaning something answered with a reset or the route was unavailable.

**Only the network path differed, so the difference is the network path.** A
drop became a rejection. That is worth more than either message alone, and it
is only visible because the overlay prints the exception's own text rather than
a friendly summary.

### What is ruled out now

DNS hijacking, imgbox being down, 403 / Cloudflare / DDoS-Guard, the missing
`Referer`, and `CloudflareInterceptor`'s timeout. Everything that needs a
connection to exist is dead, because no connection ever existed.

### The clean test is still not run

**Mobile data with the VPN off.** A VPN adds a variable rather than removing
one, and this one did not obviously carry the app's traffic. Cellular is a
genuinely different path to the same host and it is one toggle.

The second reading, if the first is ambiguous: open the failing URL in the
phone's **browser** with the VPN on. Browser failing too means the VPN is not
covering the device. **Browser succeeding while the app fails would be a new
finding and would move this back into the app** — per-app split tunnelling is
the first thing to check in that case.

### An app-side improvement this exposed, worth its own card if wanted

A chapter whose every page targets one dead host currently spends
**36 × 30 seconds** discovering that, one page at a time, with no user-visible
error until the end. Nothing remembers that the previous thirty-five
connections to that host all failed. A per-host circuit breaker for the
duration of a fetch — first N connect failures to one host, stop and fail the
chapter with the host named — would turn eighteen silent minutes into one
message. Not filed yet; the reporting half is already covered by the
"Starting" card.

---

## 9e. Closed: Chrome fails identically, so the app is exonerated

Chrome, VPN on, `images2.imgbox.com`:

```
This site can't be reached
images2.imgbox.com refused to connect.
ERR_CONNECTION_REFUSED
```

**A browser and this app, two entirely independent network stacks, fail the
same way on the same host.** That is device or network wide. Per-app split
tunnelling is ruled out and so is everything in this codebase. The card is
labelled Blocked/Upstream.

### Three attempts, two failure shapes

| Path | Result | Meaning |
|---|---|---|
| No VPN, app | `SocketTimeoutException` after 30000ms, source address printed | packets **silently dropped** — a blackhole |
| VPN, app | `ConnectException`, no elapsed time | failed **fast** |
| VPN, Chrome | `ERR_CONNECTION_REFUSED` | an actual **TCP RST** |

The shape tracks the path: a drop without the VPN, an active refusal with it.
Both are filtering, applied at different points. Meanwhile the host itself
answers TCP and completes TLS in single-digit milliseconds from an unrelated
network, on the same three IPs this phone resolves.

### The whole chain, and what each step cost

1. **Source read.** Produced a coherent wrong mechanism — an unbounded
   `getPageList` loop. Cost: a card that had to be walked back.
2. **Device screenshots.** Overturned it. `Page 1 of 36` and an absent
   `else`-branch initial were the two facts, and both were free.
3. **Download queue.** Named nothing, because "Starting" is two states in one
   label. Cost: a round, and produced a real card.
4. **The overlay, left long enough to fire.** Named the host and the exception
   in one line. **This was available at step 2 and was not collected**, because
   the instruction omitted how long to wait.
5. **Measuring the far end from elsewhere.** Killed DNS hijacking and
   host-is-down together.
6. **Chrome.** Killed the app.

**Five rounds, and step 4 could have been step 2.** The reusable form is in
§9a — find the branch that distinguishes the candidates and look at it — plus
its complement, learned here: **when the distinguishing instrument only reports
on failure, read the timeout that governs it before deciding how long to
watch.** `connectTimeout` 30s and `callTimeout` 2 min were in `NetworkHelper`
throughout.

### The only app-side residue

A chapter whose every page targets one dead host spends **36 × 30 seconds**
discovering that, one page at a time, and nothing remembers that the previous
thirty-five connections to the same host failed. A per-host circuit breaker for
the duration of a fetch — first N connect failures to one host, stop and fail
the chapter naming it — turns eighteen silent minutes into one message. Still
not filed; the reporting half is the "Starting" card, which is filed.
