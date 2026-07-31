# Session handoff — 0.88 to 0.93, the reader, and covers that repair themselves

Written 2026-07-31, late evening. Supersedes `SESSION_HANDOFF_0.90.md`, which was
written mid-run and called 0.90 unverified; it is verified, and the number that
verified it is in §3. **This file does not replace `SESSION_HANDOFF_0.87.md`** —
its §5 source-fetching procedure and §8 "To add" column are still live reference.
It does **correct** `SESSION_HANDOFF_0.87.md` §6; see §4.

Six releases, all six exercised on device before the next was written. That is
not this project's base rate and it is worth noticing why: every one of them was
small, and each was pushed while something slow was already running, so there was
nothing to do but test the last one.

---

## 0. Read this before touching anything

**1. Open thread 1 is closed.** The reader was the largest untested surface in
this project for thirteen sessions. Paged right-to-left, grayscale and invert
together, the chapter picker, and settings surviving a reopen have all now been
run. Two were broken and are fixed. Nothing in the tree has an untested tail.

**2. Board item 3 is half closed and must not be recorded as closed.** Resume is
done. Reprioritising a fetch already in flight is not, and it cannot be a
parameter — see §6.

**3. `SESSION_HANDOFF_0.87.md` §6's central claim is wrong.** It says the refresh
sweep "already holds the fetched `SManga`". It does not; `restoreSeries` makes no
network call. §4.

**4. Covers repair at about 46% yield.** 233 fixed out of 510 series visited. That
is the measurement that justifies the doubled sweep cost, and §3 has the caveat
on what the number does and does not count.

---

## 1. The releases

| | What | Commit | State |
|---|---|---|---|
| 0.88 | `startAt` on `loadPagesProgressively`; RTL slider; chapter picker | `bf2241d` | 3 of 6 checks |
| 0.89 | Per-load `pagesLoading`; descending wrap; RTL slider properly | `1b358a2` | **Verified** |
| 0.90 | `CoverRepair`; cover repair inside the sweep | `3189c8d` | **Verified** |
| 0.91 | Resume targets the furthest chapter with progress | `5d26494` | **Verified** |
| 0.92 | Covers count shown after a stop, and persisted | `fca3ca3` | **Verified** |
| 0.93 | Marking a chapter unread clears its saved page | `ba1fd5d` | **Verified** |

Docs commit `6dcc166` sits between 0.90 and 0.91.

---

## 2. The reader — 0.88 and 0.89

### `startAt`

`Source.loadPagesProgressively` grew `startAt: Int = 0`, before `onUpdate` so both
call sites keep their trailing lambda. `TachiyomiSourceAdapter` reorders
*indices*, never the page list:

```kotlin
private fun fetchOrder(count: Int, startAt: Int): List<Int> {
    if (count <= 0) return emptyList()
    val start = startAt.coerceIn(0, count - 1)
    if (start == 0) return (0 until count).toList()
    return (start until count) + (start - 1 downTo 0)
}
```

Three properties it rests on:

- **The index is the page's own number, not its position in the sequence.** It
  addresses the slot in `done` *and* the filename on disk. The old code derived
  it as `base + offset` from the batch, which is exactly what stops being true.
- **Batch count, `BATCH_GAP_MS` and `CONNECTION_RECYCLE_BATCHES` are untouched.**
  Requests-per-connection is the manhwatoon variable and a reorder must not move
  it.
- **`start == 0` returns the plain sequence**, which is the guarantee that the
  download path is unchanged.

**The tail runs descending; 0.88 shipped it ascending.** Ascending is the obvious
way to write a wrap and it puts the page one flick *above* the reader last in the
queue — the slowest page to arrive is the one a resuming reader most likely wants
next. Nobody reported it; it was noticed while writing an unrelated fix.

### The bug 0.88 caused, which is the one worth reading

Reopening a chapter showed **every page as "couldn't be loaded"** for about five
seconds, then all of them appeared.

`stillLoading` is the only thing separating a pending page from a failed one, and
it read `isLoading` — a flag **six launch blocks in `YomuApp` write**. That is
wrong on its own but is not what fired. The cause was narrower:

> Cancelling a coroutine does not unwind it synchronously. Its `finally` runs
> whenever it next resumes, which is routinely **after** its replacement has
> started and set the flag.

Backing out cancels the page job; reopening starts a new one; the old one's
`finally` then lands and puts the light out on the new one.

**0.88 is what made this reachable.** Before the reorder, reading to page 40 meant
pages 0–40 had long since arrived and the job was over by the time anyone left.
After it, being at page 40 is precisely the condition under which pages 0–39 are
outstanding — so leaving reliably cancels a *live* job. Same shape as `4264dea`:
correct in isolation, broken only in combination.

Fixed in two parts, both needed: `pagesLoading` owned by the reader, and a token
claimed **before** the launch so only the load that set the flag may clear it.
Claimed before rather than after, so the comparison never depends on when
`pageJob` is assigned. `onClose` retires the token too.

**One wrong diagnosis is recorded here on purpose.** The first explanation was
`openFromHistory`, which calls `openChapter` and then sets `isLoading = false` on
the next line — a real defect, and not this one. It died on a one-word answer:
"series screen". The question that then split the remaining candidates was
*whether the resumed page itself was blank or only the pages before it* — one
answer implicates the flag, the other the fetch order. Four plausible causes,
one discriminating question, minutes rather than an evening.

### The RTL slider, mirrored twice

`reverseLayout` flips the pager while `currentPage` stays logical, so the slider
increased rightwards while the chapter advanced leftwards. Found by reading, not
reported — thirteen sessions of nobody opening RTL.

**0.88 mirrored the value and got half of it.** Direction became right and the
fill became wrong: page 1 arrived as the maximum, so the track was full at the
start of the chapter. 0.89 mirrors the *widget* — `LocalLayoutDirection provides
Rtl` around that Slider alone — which Material3 honours for track, thumb and
drag-to-value together. Scoped to the slider; the button row keeps reading order.

### The chapter picker

Opened at chapter 1 of the series, hundreds of rows from anything a reader wants.
Now seeded via `rememberLazyListState(initialFirstVisibleItemIndex)` with two rows
of lead-in — seeded rather than scrolled from an effect, so there is no jump.

---

## 3. Covers — 0.90, and what it measured

Board items 1 and 4, one mechanism: **the grid renders a cover string stored at
add time and nothing ever re-asks.** `Library.healCover` fires once per series, on
open, and only when the stored cover is blank or loopback.

`CoverRepair.kt` holds the candidate list; the repair rides the existing sweep
rather than a second foreground service, because the `dataSync` budget is ~6h/day
and already shared with downloads.

**Candidates come from two places with very different prices:**

- **Blank or loopback** (bug 4, SpyFakku) — visible in stored data, free to find.
- **Watched to fail** (bug 1, the 404s) — a stale cover is well-formed and simply
  404s, so nothing in the string gives it away. `CoverImage` already draws every
  library cover and already holds Coil's error, so a definite 404 or 410 is
  recorded against the series id. **Detection is billed to rendering that was
  happening anyway.**

Only 404/410. A timeout, an unresolvable host or a 403 is about the network, and
treating those as staleness would have one scroll in airplane mode queue the
entire library.

Three decisions that differ from §6's sketch: the repair costs a real
`loadDetails` and so takes its own `REQUEST_SPACING_MS`; cover failures are caught
separately from the counts, or a bad cover records a failure against a series that
counted perfectly; and `coversRepaired` sits **outside** the
`counted + failed + skipped` arithmetic, because the same series can be counted
*and* repaired.

Writes batch through `Library.setCovers` on the `FLUSH_EVERY = 100` cadence.
`setCovers` may overwrite a cover that is present and looks fine — that is the
point, and why `healCover` was left alone rather than widened. `mergeAll` could
not be reused: it replaces whole entries, and a repair has a cover and nothing
else, so it would blank `addedAt` and the stored title.

### The measurement

**233 covers fixed across 510 series visited — 46%.** The sweep ran at
**21.5/min against the 42.7/min on record**, almost exactly half, which is what
two requests and two 250 ms gaps per candidate predicts. The yield says that cost
is buying real repairs rather than being burned, and that is the whole
justification for doubling a 79-minute sweep.

**What the number does not say.** `coversRepaired` counts *successes*, not
*attempts*. If all 510 were candidates then 277 were requests that produced
nothing and will be retried on every future sweep — `CoverRepair` models present
and needs-repair but not **asked and got nothing**, which is the same three-state
gap §5 of `PROJECT_HANDOFF.md` records against `SeriesIndex`. If only ~250 were
candidates there is no waste at all. A second counter for attempts would settle
it and nothing else will.

### Where the count was unreadable, and why that mattered

0.90 surfaced `coversRepaired` only in the `finishedAt > 0` arm of the settings
row's `when`. **`unfinished` precedes it**, and a stopped sweep always renders
`unfinished` — so the number was invisible in exactly the state anyone reads it
from. It was also process-lifetime, so installing an update erased it. The count
for the first 2245-series run is gone for good.

0.92 renders it in both arms and persists it with the other counters. The comment
that had justified leaving it out — "a restored summary describes a sweep that has
already finished" — got it backwards: a stopped sweep is when the number is most
wanted and least recoverable.

---

## 4. The correction to `SESSION_HANDOFF_0.87.md` §6

It says, of putting cover repair in the sweep:

> it already visits every series and already holds the fetched `SManga`, so
> writing a corrected cover alongside the counts is close to free

**It does not hold one.** `LibraryRefresh` calls `restoreSeries`, which builds a
stub from url and title and makes **no network call** — that is its whole purpose
and it is documented three times in `PROJECT_HANDOFF.md` §4. `thumbnail_url` on
that stub is null. A cover costs a second request per series.

Believed, the obvious implementation — details for all 3571 — would have doubled
the sweep to repair a minority of entries. **Checking it cost one `grep`.**

- **A note calling something "close to free" is asserting a cost, and costs rot
  faster than mechanisms.** The mechanism was right and the price was wrong.
- **Two symptoms sharing a mechanism need not share a cost.** §6's "why 1 and 4
  are one bug" is true and useful, and it hid the only fact that determined the
  design: one is free to detect and the other is not.

---

## 5. 0.91 to 0.93 — three small ones

### The Resume button was aiming at the wrong chapter (0.91)

`resumeIndex` was the first *unread* chapter. On an imported library read state
arrives from the backup full of holes, so a series read to chapter 50 with a few
early ones unmarked put Resume at chapter 3.

It now takes the furthest chapter with **any progress** — read state or a stored
page — and resumes there if unfinished, or the next unread after it if finished.
`History` cannot answer this: **capped at 40 entries for the whole app**, so on
3575 series almost nothing has one. `ReadState` and `savedPage` are per chapter
and uncapped. One pass, read flags kept rather than re-queried.

### Which immediately made a dormant bug matter (0.93)

`ReadState.setRead(false)` cleared the read tick and left `pos:` alone. Nothing
in the app ever removed a saved page. That was untidy and harmless — until 0.91
made `savedPage > 0` a *progress signal*. After it, marking a chapter unread to
read it again left that chapter as the furthest one touched, and Resume aimed
straight back at the page you had just asked it to forget.

`setRead` now clears the page when read is false. Put in `ReadState` rather than
at the two call sites so a third cannot miss it, with `clearPage` next to
`savePage` so the `pos:` key format stays in one file. **Removed, not zeroed** —
`savedPage > 0` has to keep meaning "started".

**Existing stale positions are not cleaned retroactively**, which is the 0.57
write-side lesson repeating: a fix to the writer does not repair what is stored.

### Cover size (0.91)

The Settings note claimed the control applied to the library grid and per-source
browsing. It only drives browsing — `SourceBrowseScreens.kt:124` — while the grid
uses items-per-row from its own options sheet. `PROJECT_HANDOFF.md` §0 said the
pref was dead; one `grep` said otherwise. Both corrected.

---

## 6. The board

1. **BeeHentai returns nothing.** **Upstream, and the probe proved it in one
   run**: status 200, h2, `cf-mitigated` absent, no challenge — and the body is
   **ToonTop**. `beehentai.com` now serves `toontop.io`, so the extension's
   selectors match nothing. Before filing, check the extension's settings for a
   base-URL override and try `toontop.io`; if the engine is the same the
   selectors may still hit. Otherwise it is a Keiyoushi report and the probe
   output is a complete one. Third time the probe has settled in one run what
   inference was about to spend an evening on.
2. **Elite Babes 1.6.3** — upstream, nothing to do.
3. **Resume/scroll should load the current page forward** — *resume half closed*.
   The seek half is open: dragging to page 50 while pages 1–10 download does not
   reprioritise. It cannot be a parameter, because the fetch is already in
   flight; it needs a reorderable work queue or a cancel-and-restart that
   discards progress.
4. **Long strip still has no zoom** — already documented as deliberate,
   `PROJECT_HANDOFF.md` §7 item 8. Needs the viewport zoomed rather than an item.
   Real work, not a flag. Worth annotating the card so it stops being
   rediscovered.
5. **All scrolls should have a scroll handle** — new, a feature rather than a bug,
   and it overlaps `SESSION_HANDOFF_0.87.md` §8 item 7.
6. **The failure tally prints raw source ids.** `tachi:6202325652827735606 ×12`
   tells you a source is failing consistently and not which one. **This is the
   same missing map that blocks Group → Sources** (§4, "What the index still
   can't tell you"): `LibraryEntry` stores `sourceId` and never a display name.
   One `sourceId → name` map written whenever sources are listed closes both, and
   it is now visible in two places rather than one.
7. **`CoverRepair` has no "asked and got nothing" state** — see §3. Only worth
   building after an attempts counter says how much is being wasted.
8. **The refresh backlog** — bug 3 (counter race) and bug 6 (`done` doesn't
   reconcile), 547 unaccounted. Unchanged, still bottom.

---

## 7. Lessons

**A change that lengthens a job's lifetime wakes races that were always there.**
Ask of any change to ordering or duration: what used to be finished by the time
the user could act, and is it still? The change need not touch concurrency.

**A flag must be cleared by the operation that set it.** Use a token claimed
before the launch, not a job reference compared by identity — the reference is
assigned after the launch and the race is about that window.

**A shared flag cannot answer a question only one screen asks.** `isLoading` is
written by six operations and was the reader's pending-vs-failed signal.

**Mirroring has two halves and they fail independently.** Value mirroring gets
direction right and fill backwards. Mirror the widget. 0.64's "a symmetric mistake
looks correct at rest", with a second edge: the first fix passed the obvious test.

**A stated cost rots faster than a stated mechanism.** When a note says something
is free, that is the sentence to re-derive.

**Repurposing a signal gives it obligations it did not have.** A saved page that
nothing cleared was harmless while it only fed the reader. The moment Resume
treated it as progress, mark-unread became a bug — in code nobody touched. When
promoting an incidental value to a decision input, enumerate what is *supposed* to
reset it and check that anything does.

**A number surfaced only in the branch you don't read is not surfaced.** The
covers count went into the finished arm of a `when` whose unfinished arm wins
after every stop.

**When a report kills your diagnosis, ask the question that splits what remains.**
Not the one that defends the dead theory.

---

## 8. State of the tree

Head is `ba1fd5d` (0.93). Build environment unchanged from 0.83: Kotlin 2.2.21,
AGP 8.5.2, Gradle 8.9, JDK 17, compileSdk 36, targetSdk 34, minSdk 24, OkHttp
5.4.0, kotlinx-serialization 1.9.0, Compose BOM 2024.09.03, Coil 2.7.0.

Library **3575** entries, up from 3571.

**Verified on device this session:** resuming a chapter loads from the current
page forward; scrolling back from a resume point is quick; downloads unchanged
across two edits to `fetchOrder`; backing out mid-load and reopening shows
spinners rather than failures; RTL paging and its slider; grayscale and invert
together; the chapter picker; reader settings across a reopen; SpyFakku covers
filling in without being opened; 233 covers repaired in one partial sweep; the
count surviving a stop; Resume landing on the chapter in progress; marking a
chapter unread moving Resume off it.

**Nothing in the tree is unverified.**

### What's next

No item is urgent and the reader is no longer the answer by default — that slot
has been filled for thirteen sessions and is now empty, which is worth noticing
before something quietly inherits it.

The cheapest real work is the `sourceId → name` map (board item 6): small, and it
closes a documented §4 gap and a visible diagnostic wart at once. The largest gap
between what the app knows and what it can tell you is still the Feed / Updates
tab, blocked as ever on the sweep overwriting counts rather than recording what
changed.
