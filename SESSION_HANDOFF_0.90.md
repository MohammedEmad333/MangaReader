# Session handoff — 0.88 to 0.90, the reader, and two bugs that were one bug and two costs

Written 2026-07-31, evening. **This file does not replace `SESSION_HANDOFF_0.87.md`**
— its §5 source-fetching procedure and §8 "To add" column are still live reference.
It does **correct** `SESSION_HANDOFF_0.87.md` §6, whose central factual claim is
wrong; see §3 below.

Three releases. The reader stopped being the largest untested surface in this
project, and the thing that had been true for thirteen sessions is no longer the
reason to avoid it.

---

## 0. Read this before touching anything

**1. Open thread 1 is closed.** The reader was the largest untested surface for
thirteen sessions. Paged right-to-left, grayscale and invert together, the
chapter picker, and settings surviving a reopen have all now been exercised on
device. Two of them were broken and are fixed; grayscale+invert and settings
persistence were correct as written.

**2. 0.90 is unverified.** It was pushed while a library sweep was running, which
is the thing that has to be running to test it. `3189c8d` is on `main` and has
never been installed. §4 has the test plan and the two failure modes worth
telling apart.

**3. `SESSION_HANDOFF_0.87.md` §6 is wrong about where covers come from**, and it
is the load-bearing sentence in that section. It says the refresh sweep "already
holds the fetched `SManga`". It does not — `LibraryRefresh.kt` calls
`restoreSeries`, which makes no network request at all. §3 below.

**4. Board item 3 is half closed and must not be recorded as closed.** "Resume
and scroll should load the current page forward" is two features. Resume is done.
Seeking mid-download is not, and it is not a parameter — see §5.

---

## 1. The reader — 0.88 and 0.89

| | What | Commit | State |
|---|---|---|---|
| 0.88 | `startAt` on `loadPagesProgressively`; RTL slider; chapter picker | `bf2241d` | 3 of 6 checks passed |
| 0.89 | Per-load `pagesLoading`; descending wrap; RTL slider properly | `1b358a2` | **Verified, all checks** |

### `startAt` — what shipped

`Source.loadPagesProgressively` grew `startAt: Int = 0`, sitting before
`onUpdate` so both existing call sites keep their trailing lambda.
`TachiyomiSourceAdapter` reorders *indices*, never the page list:

```kotlin
private fun fetchOrder(count: Int, startAt: Int): List<Int> {
    if (count <= 0) return emptyList()
    val start = startAt.coerceIn(0, count - 1)
    if (start == 0) return (0 until count).toList()
    return (start until count) + (start - 1 downTo 0)
}
```

Three properties this rests on, all of which are easy to break later:

- **The index is the page's own number, not its position in the sequence.** It
  addresses the slot in `done` *and* the filename on disk. The old code computed
  it as `base + offset` from the batch number, which is exactly what stops being
  true once the order changes.
- **Batch count, `BATCH_GAP_MS` and `CONNECTION_RECYCLE_BATCHES` are untouched.**
  Requests-per-connection is the variable the manhwatoon 400s turned on (§5 of
  `PROJECT_HANDOFF.md`), and a reorder must not disturb it.
- **`start == 0` returns the plain sequence.** That is not an optimisation, it is
  the guarantee that `DownloadService`, which never passes a start, behaves as it
  always did.

**The tail runs descending, and 0.88 shipped it ascending.** Ascending is the
obvious way to write a wrap and it puts the page one flick *above* the reader
last in the queue — the slowest page in the chapter to arrive is the one someone
resuming is most likely to ask for next. Nobody reported this; it was noticed
while writing the fix for something else.

### The bug 0.88 caused, which is the one worth reading

Reopening a chapter showed **every page as "couldn't be loaded"** for about five
seconds, then all of them appeared.

`stillLoading` is the only thing separating a page that is pending from a page
that failed, and it was reading `isLoading` — a single flag that **six different
launch blocks in `YomuApp` write**. That alone is wrong, but it is not what
fired. The cause was narrower:

> Cancelling a coroutine does not unwind it synchronously. Its `finally` runs
> whenever it next resumes, which is routinely **after** its replacement has
> started and set the flag.

Backing out of a chapter cancels the page job; reopening starts a new one; the
old one's `finally` then landed and put the light out on the new one.

**0.88 is what made this reachable, and the mechanism is worth generalising.**
Before the reorder, reading to page 40 meant pages 0–40 had long since been
fetched and the job was over by the time you left. After it, pages 0–39 are
outstanding *because* you are at page 40, so backing out reliably cancels a live
job. **A change that lengthens a job's lifetime can wake a race that was always
there.** Same shape as `4264dea`: correct in isolation, broken only in
combination with what was already in the tree.

The fix is two parts, and both are needed:

- `pagesLoading`, owned by the reader, so five unrelated operations can no longer
  answer a question only the reader asks.
- A token (`pageLoadSeq`), claimed before the job is launched, so **only the load
  that set the flag may clear it**. Claimed before the launch rather than after,
  so the comparison never depends on when `pageJob` happens to be assigned.
  `onClose` retires the token too, because the cancelled job's `finally` is still
  to come.

### The RTL slider, mirrored twice

`HorizontalPager` gets `reverseLayout` for `PAGED_RTL`, but `currentPage` stays
logical. So the horizontal slider increased rightwards while the chapter advanced
leftwards: dragging the thumb the way the pages were going walked backwards.
Found by reading, not reported — thirteen sessions of nobody opening RTL.

**0.88 mirrored the value and got half of it.** Direction became correct and the
fill became wrong: page 1 arrived at the slider as its maximum, so the track was
full at the start of the chapter and empty at the end. 0.89 mirrors the *widget*
instead — `LocalLayoutDirection provides Rtl` around that Slider alone — which
Material3 honours for track, thumb and drag-to-value together, so the value
handed to it stays an ordinary page number. Scoped to the slider: the button row
keeps its reading order, because Prev belongs where the hand that has been
tapping it expects.

### The chapter picker

`ChapterPickerSheet` opened at chapter 1 of the series. On a long series that is
hundreds of rows from anything a reader wants, and stepping to a neighbouring
chapter — the one thing the picker is *for* — was the hardest thing to do with
it. Now seeded via `rememberLazyListState(initialFirstVisibleItemIndex)` with two
rows of lead-in. Seeded rather than scrolled from an effect, so there is no jump.

---

## 2. Covers — 0.90 (`3189c8d`, **unverified**)

Board items 1 and 4, which `SESSION_HANDOFF_0.87.md` §6 correctly identified as
one mechanism: **the library grid renders a cover string stored at add time, and
nothing ever re-asks.** `Library.healCover` fires once per series, on open, and
only when the stored cover is blank or loopback.

New file `CoverRepair.kt` holds the candidate list. The repair itself rides the
existing refresh sweep rather than a second foreground service — §7 item 5 of
`PROJECT_HANDOFF.md` is why: the `dataSync` budget is ~6 hours a day and already
shared with downloads.

**Candidates come from two places with very different prices:**

- **Blank or loopback** (bug 4, SpyFakku). Visible in the stored data with no
  network at all. Free to find.
- **Watched to fail** (bug 1, the 404s). A stale cover is well-formed and simply
  404s, so nothing in the stored string gives it away. `CoverImage` already draws
  every library cover and already has Coil's error in hand, so a definite 404 or
  410 there is recorded against the series id. **Detection is paid by rendering
  that was happening anyway.**

Only a definite 404/410 counts. A timeout, an unresolvable host or a 403 is about
the network or the source, not about the stored string — treating those as
staleness would have one scroll in airplane mode queue the entire library.

Three decisions that differ from §6's sketch, each for a reason:

1. **The repair costs a real `loadDetails` request**, so it takes its own
   `REQUEST_SPACING_MS`. A repair pass is not a reason to halve the gap between
   requests to one host.
2. **Cover failures are caught separately from the counts.** A failed cover says
   nothing about whether the chapter list landed, and letting it reach the
   existing handler would record a failure against a series that counted
   perfectly — inflating the one tally whose job is to point at genuinely broken
   sources.
3. **`coversRepaired` sits outside the `counted + failed + skipped` arithmetic.**
   The same series can be counted *and* repaired, so folding it in would break
   the identity that row exists to let you check.

Writes batch through a new `Library.setCovers` on the same `FLUSH_EVERY = 100`
cadence as `SeriesIndex.recordAll`. `setCovers` may overwrite a cover that is
present and looks fine — that is the whole point, and it is why `healCover` was
left alone rather than widened. `mergeAll` could not be reused: it replaces whole
entries, and a repair has a cover and nothing else, so merging through it would
blank `addedAt` and the stored title.

**`flush()` now has two stores and neither may return early for the other.** The
old `if (pending.isEmpty()) return` inside the first block would strand a batch of
covers on any sweep of an already-counted library, which is most of them.

---

## 3. The correction to `SESSION_HANDOFF_0.87.md` §6

It says, of putting cover repair in the sweep:

> it already visits every series and already holds the fetched `SManga`, so
> writing a corrected cover alongside the counts is close to free

**It does not hold one.** `LibraryRefresh.kt` calls `restoreSeries`, which builds
a stub `SMangaImpl` from url and title, marks it `initialized = true`, and makes
**no network call** — that is the entire point of `restoreSeries` and it is
documented three places in `PROJECT_HANDOFF.md` §4. `thumbnail_url` on that stub
is null, so `series.cover` is null for every series a sweep visits. A fresh cover
needs `loadDetails`, a second request per series.

Had that been believed, the obvious implementation — fetch details for all 3571 —
would have roughly doubled a 79-minute sweep against a shared 6-hour daily
budget, to repair a minority of entries. **Checking it cost one `grep`.**

Two things generalise:

- **A handoff entry that calls something "close to free" is asserting a cost, and
  costs are the part that rots.** The mechanism it described was right and its
  price was wrong. Prices depend on what the surrounding code does today.
- **Two symptoms sharing a mechanism need not share a cost.** §6's "why 1 and 4
  are one bug" is true and useful — and it hid that one of them is free to detect
  and the other is not, which is the only fact that determines what gets built.

---

## 4. Testing 0.90

Nothing about it has been run. The order matters:

1. **Scroll the library past a known-404 cover first.** The 404 half only fires
   as covers actually draw. Blank covers need no scrolling.
2. Settings › Library › Chapter counts.
3. SpyFakku entries should fill in without being opened individually, and the row
   should end with "N covers fixed".

**Two failure modes worth telling apart before touching anything:**

- **SpyFakku fills in but the count ignores 404s** → the HTTP code is read out of
  Coil's error *message* rather than by catching its exception type, deliberately,
  because `Ui.kt` already proves `state.result.throwable.message` compiles and an
  unfamiliar import is a CI round trip with no compiler in the loop. If this is
  what happened, it is isolated to `isMissingImage` and the real fix is probably
  `coil.network.HttpException.response.code`.
- **Nothing fills in at all** → `needsRepair` or the flush, not the detection.

Also watch the sweep's rate. It should stay near the recorded 42.7/min; markedly
slower means more entries are being treated as candidates than intended.

---

## 5. Lessons

**A change that lengthens a job's lifetime can wake a race that was always
there.** Reordering the page fetch meant backing out of a chapter reliably left a
live job to cancel, and a cancelled coroutine's `finally` is asynchronous. Ask of
any change to *ordering* or *duration*: what was previously finished by the time
the user could act, and is it still?

**A flag must be cleared by the operation that set it.** The general fix is a
token claimed before launch and compared in `finally`, not a nullable job
reference compared by identity — the reference is assigned after the launch and
the race is precisely about what happens in between.

**A shared flag cannot answer a question only one screen asks.** `isLoading` is
written by six operations and was being read as the reader's pending-vs-failed
signal. This is the same shape as §5's "a lazily-populated store has three
states" arriving from the other end: the store was never wrong, the consumer was.

**Mirroring has two halves and they fail independently.** Mirroring a value gets
direction right and fill backwards. Mirror the widget. A symmetric mistake looks
correct at rest — 0.64's vertical slider, now with a second edge on it.

**When a report kills your diagnosis, the next question should split the
remaining candidates, not defend the dead one.** "Couldn't be loaded on every
page" had four plausible causes. The question that settled it was whether the
*resumed* page was also blank or only the ones before it — one answer implicated
the flag, the other implicated the fetch order. Guessing between four was the
documented mistake; asking one discriminating question was minutes.

**Detection can sometimes be paid by work already happening.** The library grid
draws every cover it shows and already has the failure in hand. That turned the
expensive half of the covers bug into an existing side effect.

---

## 6. State of the tree

Head is `3189c8d` (0.90). Build environment unchanged from 0.83: Kotlin 2.2.21,
AGP 8.5.2, Gradle 8.9, JDK 17, compileSdk 36, targetSdk 34, minSdk 24, OkHttp
5.4.0, kotlinx-serialization 1.9.0, Compose BOM 2024.09.03, Coil 2.7.0.

**Verified on device this session (0.89):** resuming a chapter loads from the
current page forward; scrolling back from a resume point is quick; downloads
unchanged after two edits to `fetchOrder`; backing out mid-load and reopening
shows spinners rather than failures; RTL paging and its slider; grayscale and
invert together; the chapter picker; reader settings surviving a reopen.

**Not verified:** all of 0.90.

### The board

1. **Covers** — fix shipped in 0.90, unverified. Items 1 and 4 of the 0.87 board
   are one fix and this is it.
2. **Elite Babes 1.6.3** — upstream, nothing to do here.
3. **Resume/scroll should load the current page forward** — *resume half closed
   in 0.88/0.89*. The seek half is open: dragging the slider to page 50 while
   pages 1–10 are downloading does not reprioritise. It cannot be a parameter —
   the fetch is already in flight, so it needs a reorderable work queue or a
   cancel-and-restart that discards progress. Genuinely different work.
4. **The Resume FAB targets the first unread chapter, not the last read one.**
   New this session, found while testing. `SourceBrowseScreens.kt:454` computes
   `resumeIndex` as the first unread; with imported read state that is usually
   chapter 1, so a button labelled Resume opens the beginning of the series. It
   should read `History` for this series first and fall back to first-unread.
   Contained, series-screen work, not reader work.
5. **The refresh backlog** — bug 3 (counter race) and bug 6 (`done` doesn't
   reconcile), 547 unaccounted. Unchanged, still bottom.

### What's next

Verify 0.90 first; it is the only unverified thing in the tree.

After that the cheapest real item is the Resume FAB (4), which is a small fix to
a visibly wrong button. The largest remaining gap between what the app knows and
what it can tell you is still the Feed / Updates tab, still blocked on the same
thing: the sweep overwrites counts rather than recording what changed.

**The reader is no longer the answer to "what should I do".** That slot has been
filled by open thread 1 for thirteen sessions and is now empty, which is worth
noticing before something else quietly inherits it.
