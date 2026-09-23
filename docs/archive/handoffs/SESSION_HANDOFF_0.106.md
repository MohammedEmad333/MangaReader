# Session handoff — 0.88 to 0.106, the reader, covers, and reading the reference

Written 2026-07-31, late. **Supersedes `SESSION_HANDOFF_0.93.md`**, which covered
0.88–0.93 and is folded in here; delete it. `SESSION_HANDOFF_0.83.md` and
`SESSION_HANDOFF_0.87.md` remain live reference — 0.83 §3 (the dependency chain)
and §5 (the dex scan), 0.87 §5 (fetching extension source) and §8 (the "To add"
column). This file **corrects** `SESSION_HANDOFF_0.87.md` §6; see §4.

Nineteen releases in one sitting. Every one of them exercised on device except
0.104 and 0.105, which were superseded before install.

---

## 0. Read this before touching anything

**1. Open thread 1 is closed.** The reader was the largest untested surface in
this project for thirteen sessions. Everything on the untried list has now been
run. Two items were broken and are fixed.

**2. TachiyomiSY is now a first-class reference and this document previously
discouraged it.** §7 — this is the most reusable thing in this file and it cost
the most tonight.

**3. Two fixes were silently reverted by my own tooling and shipped that way.**
§6. The countermeasure is a marker audit before handing over any file.

**4. The strip edge gesture was attempted twice, never fired once, and is
deleted.** Do not attempt it a third time without reading §5 first — the shape is
wrong, not the tuning.

**5. `SESSION_HANDOFF_0.87.md` §6's central factual claim is wrong.** The refresh
sweep does *not* hold a fetched `SManga`. §4.

---

## 1. The releases

| | What | Commit | State |
|---|---|---|---|
| 0.88 | `startAt` page fetch; RTL slider; chapter picker | `bf2241d` | 3 of 6 checks |
| 0.89 | Per-load `pagesLoading`; descending wrap; RTL slider properly | `1b358a2` | Verified |
| 0.90 | `CoverRepair`; cover repair inside the sweep | `3189c8d` | Verified |
| — | Handoffs folded, open thread 1 closed | `6dcc166` | docs |
| 0.91 | Resume targets furthest chapter with progress; cover-size note | `5d26494` | Verified |
| 0.92 | Covers count shown after a stop, and persisted | `fca3ca3` | Verified |
| 0.93 | Marking unread clears the saved page | `ba1fd5d` | Verified |
| 0.94 | `SourceNames`; group library by source; named failure tally | `55e6144` | Verified — **reverted 0.90's cover reporting** |
| 0.95 | Names react to landing; names from the repo index | `c0cce75` | Verified |
| 0.96 | Title/author global search; names from a backup | `1de57e4` | Verified — **reverted 0.91's Resume fix** |
| 0.97 | Restores both reverts | `1b5064b` | Verified |
| 0.98 | Writer/artist split; refresh chosen sources | `a180424` | **CI red** — missing import |
| 0.98 | The missing `width` import | `f0b4f8a` | Verified |
| 0.99 | Refresh what's missing (un-counted scope) | `7506c00` | Verified |
| 0.100 | Swipe to mark read (Material3); scoped run stops claiming the cursor | `2307f9e` | Swipe unreliable |
| 0.101 | Strip edge gesture (post-scroll); higher swipe threshold | `dc1ce88` | **Gesture never fired** |
| 0.102 | Edge gesture on pre-scroll; no flash between chapters | `76edcc9` | Flash fixed, **gesture still dead** |
| 0.103 | Swipe rebuilt on `me.saket.swipe` | `a43348d` | Verified |
| 0.104 | Chapter transition rows | `f96cd14` | Verified (rows, read-marking) |
| 0.105 | Dead edge gesture deleted | `86d8f2d` | No behaviour change |
| 0.106 | Scroll a transition row into view to change chapter | `4cc33fb` | Verified |

**Untested:** a short chapter whose pages don't fill the screen, in 0.106. See
§5 — that is the case the off-screen-first guard exists for, and its failure mode
is the reader running away through several chapters at once.

---

## 2. The reader

### `startAt` — fetch from the resume position (0.88, 0.89)

`Source.loadPagesProgressively` grew `startAt: Int = 0`, before `onUpdate` so
existing call sites keep their trailing lambda. `TachiyomiSourceAdapter` reorders
*indices*:

```kotlin
private fun fetchOrder(count: Int, startAt: Int): List<Int> {
    if (count <= 0) return emptyList()
    val start = startAt.coerceIn(0, count - 1)
    if (start == 0) return (0 until count).toList()
    return (start until count) + (start - 1 downTo 0)
}
```

- **The index is the page's own number, not its position in the sequence.** It
  addresses the slot in `done` *and* the filename on disk.
- **Batch count, `BATCH_GAP_MS` and `CONNECTION_RECYCLE_BATCHES` are untouched** —
  requests-per-connection is the manhwatoon variable.
- **`start == 0` returns the plain sequence**, which is the guarantee that the
  download path is unchanged.
- The tail runs **descending** so the page one flick above the reader comes back
  early. 0.88 shipped it ascending, which put it last.

### The bug 0.88 caused (0.89)

Reopening a chapter showed **every page as "couldn't be loaded"** for ~5 seconds.

`stillLoading` read `isLoading`, a flag **six launch blocks in `YomuApp` write**.
That is wrong on its own but is not what fired:

> Cancelling a coroutine does not unwind it synchronously. Its `finally` runs
> when it next resumes, which is routinely **after** its replacement has started.

Before the reorder, reading to page 40 meant the job was long finished by the
time you backed out. After it, being at page 40 is exactly the condition under
which pages 0–39 are outstanding — so leaving reliably cancels a *live* job, and
its `finally` cleared the flag the new load had set.

Fixed with `pagesLoading` owned by the reader, plus a token claimed **before**
the launch so only the load that set the flag may clear it. `onClose` retires it
too.

**One wrong diagnosis is recorded on purpose.** The first explanation was
`openFromHistory`. It died on a one-word answer ("series screen"). The question
that then split the field was *whether the resumed page itself was blank or only
the ones before it* — one answer implicates the flag, the other the fetch order.

### RTL slider, mirrored twice (0.88, 0.89)

`reverseLayout` flips the pager while `currentPage` stays logical, so the slider
increased rightwards while the chapter advanced leftwards. 0.88 mirrored the
*value*: direction right, fill backwards. 0.89 mirrors the *widget* —
`LocalLayoutDirection provides Rtl` around that Slider alone, which Material3
honours for track, thumb and drag-to-value together. Scoped to the slider; the
button row keeps reading order.

### Chapter picker (0.88)

Opened at chapter 1. Now seeded via `rememberLazyListState(initialFirst…)` with
two rows of lead-in — seeded rather than scrolled from an effect, so no jump.

### Chapter transitions (0.104, 0.106) — and `headRows`

A transition row at each end of the strip, modelled on SY's: two labelled lines
("Finished: X" / "Next: Y"), or a fallback when there is no neighbour. Tappable.

**The dangerous part is not the rows, it is that they shift every list index.**
Four places assumed list index == page index: the seeded scroll position,
`currentPage`, seeking, and `atStripEnd`. There is now one `headRows` value and
all four add it. `atStripEnd` also stopped taking "the last visible item" and
matches the last *page* by index instead, so it stays correct as rows are added
around the pages.

**`atStripEnd` is what marks a chapter read.** Get the offset wrong and chapters
silently stop being marked — §5 of `PROJECT_HANDOFF.md`'s 0.57 bug from a new
direction. It was tested explicitly and passes.

0.106 opens the next chapter when a transition row is scrolled **fully into
view**. A row only counts once it has been off screen first: without that, a
chapter short enough to fit its row on the first frame would open and immediately
skip onward, and a one-page chapter would be unreadable.

---

## 3. Covers (0.90, 0.92) — and the measurement

Board items 1 and 4, one mechanism: **the grid renders a cover string stored at
add time and nothing ever re-asks.** `Library.healCover` fires once per series,
on open, and only when the stored cover is blank or loopback.

`CoverRepair.kt` holds the candidate list; the repair rides the existing sweep.
Candidates come from two places with very different prices:

- **Blank or loopback** (SpyFakku) — visible in stored data, free to find.
- **Watched to fail** (the 404s) — a stale cover is well-formed and simply 404s.
  `CoverImage` already draws every library cover and already holds Coil's error,
  so a definite 404/410 is recorded against the series id. **Detection is billed
  to rendering that was happening anyway.**

Only 404/410 — a timeout or 403 is about the network, and treating those as
staleness would have one scroll in airplane mode queue the whole library.

Three decisions that differ from §6's sketch: the repair costs a real
`loadDetails` and takes its own `REQUEST_SPACING_MS`; cover failures are caught
separately from the counts, or a bad cover records a failure against a series
that counted perfectly; and `coversRepaired` sits **outside** the
`counted + failed + skipped` arithmetic, because the same series can be counted
*and* repaired.

### The measurement

**233 covers fixed across 510 series visited — 46%.** The sweep ran at
**21.5/min against 42.7/min on record**, which is what two requests and two 250ms
gaps per candidate predicts. The yield is what justifies that cost. The rate rose
to 28/min in the tail as SpyFakku's contiguous block of blanks was exhausted,
which is also why a later 510-series stretch fixed only 1.

**What the number does not say.** `coversRepaired` counts *successes*, not
*attempts*. `CoverRepair` models present and needs-repair but not **asked and got
nothing**, so a candidate that yields nothing is re-asked on every future sweep.
An attempts counter would settle how much that costs. The evidence so far
suggests it is minor.

**Where the count was unreadable (0.92).** 0.90 surfaced it only in the
`finishedAt > 0` arm of the settings row's `when`. `unfinished` precedes it, and a
stopped sweep always renders `unfinished` — so the number was invisible in
exactly the state anyone reads it from, and it was process-lifetime so an update
erased it. Now in both arms and persisted.

---

## 4. The correction to `SESSION_HANDOFF_0.87.md` §6

It says, of putting cover repair in the sweep: *"it already visits every series
and already holds the fetched `SManga`, so writing a corrected cover alongside
the counts is close to free"*.

**It does not hold one.** `LibraryRefresh` calls `restoreSeries`, which makes
**no network call** — its whole purpose, documented three times in §4 of
`PROJECT_HANDOFF.md`. A cover costs a second request per series. Believed, the
obvious implementation would have doubled a 79-minute sweep to repair a minority
of entries. **Checking it cost one `grep`.**

- **A note calling something "close to free" is asserting a cost, and costs rot
  faster than mechanisms.**
- **Two symptoms sharing a mechanism need not share a cost.** §6's "why 1 and 4
  are one bug" was true and hid the only fact that determined the design.

---

## 5. The strip edge gesture — two attempts, zero firings, deleted

**Do not attempt this again without reading this section.**

- **0.101, `onPostScroll`.** Chosen because a non-zero `available` there *is* the
  at-an-edge condition, so there is no edge test to get wrong. It never fires:
  the overscroll effect consumes the leftover delta before a parent connection is
  offered it. The elegance was the bug.
- **0.102, `onPreScroll`** with an explicit `atStripEnd` test and drag-only
  filtering. Also never fired. Cause not established.

Deleted in 0.105, with a comment where it stood.

**Why the shape was wrong: TachiyomiSY does not detect an edge at all.**
`WebtoonAdapter.setChapters` builds one list spanning three chapters —

```
prevChapter.pages + ChapterTransition.Prev + currPages + ChapterTransition.Next + nextChapter.pages
```

— so scrolling simply continues into the neighbour. There is no gesture to get
right because there is no gesture. The transition rows are the visible half of
that design.

**0.106 reaches the same place from the other end**: it asks the layout whether
a row is fully on screen, which is the same question `atStripEnd` answers — and
that one demonstrably works, since it marks chapters read. **A layout query
succeeded where two gesture hooks failed**, which is the transferable part.

---

## 6. Two silent reverts, and the audit that now catches them

**0.94 reverted 0.90. 0.96 reverted 0.91.** Both because a staging copy of a file
was refreshed from an older snapshot before being edited, overwriting newer work.
A whole-file workflow makes this invisible: a whole file is always a whole file,
and the push looks normal.

- **0.96 → 0.91**: Resume went back to first-unread. Verified an hour earlier.
- **0.94 → 0.90**: the library grid stopped passing `seriesId` to `CoverImage`,
  so no 404 was ever recorded and **the stale-cover half of the repair was dead
  from 0.94 to 0.97**. Invisible — blank/loopback repair kept working.

Restored in 0.97.

Two countermeasures, both now in use:

1. **A marker audit before handing over any file** — grep each file for a short
   list of known-good identifiers from every earlier change. It caught nothing
   further across the other 27 changes, and would have caught both of these.
2. **Read the diff stat.** A restore is mostly `+`. 0.96 showed `-47` where it
   should have shown `-4`, and that was visible before the push.

---

## 7. Read TachiyomiSY

**This document told me not to.** §1 lists `mihon-ref` as "modern; mostly a dead
end" and §5 says "Do not vendor from modern Mihon". Both are true **about the
vendored API** — Mihon's `source-api` is suspend-only against Rx-based 1.4
extensions. That warning was over-generalised into "don't look at Mihon", and it
is wrong for everything above the API layer.

| Question | Reference |
|---|---|
| Vendored API, `source-api` | `tachiyomi-ref` (0.15.x). Mihon genuinely is a dead end here |
| Extension behaviour | `keiyoushi/extensions-source`, `extensions-lib` at the pinned tag |
| **App UI, gestures, screen structure, which library to use** | **Mihon or TachiyomiSY** |

Both are fetchable without a local clone:

```bash
# list paths, then fetch the file — beats guessing at names
curl -sL "https://api.github.com/repos/jobobby04/TachiyomiSY/git/trees/master?recursive=1"
curl -sL "https://raw.githubusercontent.com/jobobby04/TachiyomiSY/master/<path>"
```

Two things tonight came straight out of it:

- **The chapter swipe.** Mihon and SY both use **`me.saket.swipe:swipe:1.3.0`**,
  not Material3. `SwipeToDismissBox` exists to *remove* a row, so using it as an
  action means refusing its own state change on every swipe — which is why it
  misfired while scrolling *and* failed to fire when meant. Those are one
  symptom, not two. Two tuning rounds were spent before reading the reference.
- **Chapter transitions.** §5.

**The rule: when a piece of UI feels like it should be a solved problem, it is.
Check SY before building or tuning.**

---

## 8. Smaller pieces

- **Resume aimed at the wrong chapter (0.91).** `resumeIndex` was the first
  *unread* chapter; with imported read state full of holes that is usually near
  the start. Now the furthest chapter with **any progress** — read state or a
  stored page — resuming there if unfinished, or the next unread after it.
  `History` cannot answer this: **capped at 40 entries for the whole app**.
- **Which made a dormant bug matter (0.93).** Nothing ever cleared `pos:`.
  Harmless while a saved page only fed the reader; wrong the moment Resume read
  it as progress. `ReadState.setRead(false)` now clears it — in `ReadState`, not
  at the call sites, so a third one cannot miss it. Removed, not zeroed:
  `savedPage > 0` must keep meaning "started". **Existing stale positions are not
  cleaned retroactively.**
- **`SourceNames` (0.94–0.96).** `LibraryEntry` stores a `sourceId` and never a
  name, which made the failure tally print `tachi:6202325652827735606 ×12` and
  blocked Group → Sources. Names are recorded from three places, in ascending
  order of desperation: `SourceManager.listAllSources` (what is installed), the
  repo index `sources[].id`/`name` (what is installable — **ids are bare there
  and need the `tachi:` prefix**), and a Tachiyomi backup's field 101 (the only
  place a fork's built-in source survives). Never pruned: an uninstalled
  extension's name is when it is worth most.
- **Writer and artist (0.98).** `SManga` has both; `toSeries()` was joining them
  with `", "` and `Series` had no artist field. That is *why* 0.96 had to guess at
  a split. Now separate fields, shown as *Story ·* / *Art ·* when they differ,
  each searching for that name alone.
- **Scoped refreshes (0.98, 0.99, 0.100).** Two new scopes: chosen sources, and
  series the index has no counts for ("Refresh what's missing"). Neither touches
  `RefreshCursor` — it stores a timestamp and no filter, so a stopped scoped run
  would be indistinguishable from a stopped full one and Resume would carry on
  with a scope it cannot know.

  **0.100 fixed a bug in that design that is worth the entry.** `ownsCursor`
  correctly refused to let a scoped run *clear* the cursor, but the line that
  *takes* one still tested `scope == null` — and an un-counted run leaves
  `scopeIds` null. So it claimed the shared cursor and was then forbidden from
  releasing it: a resume point for a full sweep that never ran. **Two predicates
  answering one question.** There is now one.
- **No flash between chapters (0.102).** `openChapter` cleared `pages` up front,
  which fails the reader's routing condition until the next chapter's first
  publish. It now holds the outgoing pages through the gap and clears them only
  on failure, so an unopenable chapter still drops to the series screen where the
  error shows.

---

## 9. A CI failure worth one line

0.98 failed on `Unresolved reference 'width'`. `SettingsScreens.kt` uses a
curated import list; `SourceBrowseScreens.kt` and `ReaderScreen.kt` use the
wildcard block. §5 of `PROJECT_HANDOFF.md` already says to take the wildcard
block, and the new code was written into the one file that doesn't have it.

**Check new identifiers against the file's own import list**, not just against
the tree. The marker audit in §6 catches reverts; it does not catch this.

---

## 10. The board

1. **BeeHentai returns nothing — upstream, and the probe proved it in one run.**
   200, h2, no `cf-mitigated`, and the body is **ToonTop**. `beehentai.com` now
   serves `toontop.io`, so the extension's selectors match nothing. Check the
   extension's settings for a base-URL override first; otherwise it is a
   Keiyoushi report and the probe output is a complete one.
2. **Elite Babes 1.6.3** — upstream, nothing to do.
3. **Long strip has no zoom.** SY scales the whole `WebtoonRecyclerView`; the
   Compose equivalent is `graphicsLayer` + `transformable` on the list, which has
   to coexist with the tap detector and the transition rows. Its own release with
   its own test pass. §7 item 8 of `PROJECT_HANDOFF.md` calls this deliberate;
   that entry should now read "next", not "declined".
4. **All scrolls should have a scroll handle** — feature; overlaps
   `SESSION_HANDOFF_0.87.md` §8 item 7.
5. **Seek does not reprioritise the fetch.** Dragging to page 50 while pages 1–10
   download does not reorder the queue. Cannot be a parameter — the fetch is
   already in flight. Needs a reorderable work queue.
6. **`CoverRepair` has no "asked and got nothing" state.** §3. Only worth
   building after an attempts counter says how much is being wasted.
7. **A stopped scoped refresh leaves no summary.** `start()` clears it and the
   run is short, so its counts vanish on leaving the screen. Minor.
8. **The refresh backlog** — bug 3 (counter race) and bug 6 (`done` doesn't
   reconcile). **Bug 3 was measured clean this session**: the failure tally
   (`×12 + ×1`) agreed exactly with `failed = 13`, and §0 of `PROJECT_HANDOFF.md`
   says a disagreement between those two measures bug 3. The end-of-sweep
   arithmetic was short by **38 of 820**, against 547 of 3571 on record, which
   points the remainder at bug 6 — series whose source returns an empty chapter
   list. **"Refresh what's missing" now measures that directly**: whatever count
   survives a run of it is that set.

---

## 11. The named next piece of work: the three-chapter reader

This is the largest remaining gap between this reader and SY's, and it is the
thing both the deleted gesture and the transition rows were approximating.

**What SY does.** `WebtoonAdapter.setChapters` puts the previous chapter's pages,
a transition row, the current pages, a transition row, and the next chapter's
pages into one list, and diffs it on change. Scrolling is continuous because the
neighbour is already there.

**What it costs here.** `pages: List<File?>` in `YomuApp` is one chapter, and
`onProgress`, read-marking, resume, the page counter and the slider all assume
that. Making the list span three chapters means every one of those needs to know
which chapter a given scroll position belongs to. That is a data-model change to
the reader, not an increment.

**Do not start it without writing the design down first.** Three of tonight's
bugs came from changes I was confident about, and this one touches the code path
that marks chapters read — the failure mode is silent.

A cheaper intermediate, if it is wanted: prefetch the *next* chapter's page list
while the current one is being read, so tapping or scrolling the transition row
opens instantly. That needs no model change and removes most of the perceived
gap.

---

## 12. State of the tree

Head is `4cc33fb` (0.106). Build environment unchanged from 0.83 except one
addition: **`me.saket.swipe:swipe:1.3.0`** in `app/build.gradle.kts` (0.103).
Kotlin 2.2.21, AGP 8.5.2, Gradle 8.9, JDK 17, compileSdk 36, targetSdk 34,
minSdk 24, OkHttp 5.4.0, kotlinx-serialization 1.9.0, Compose BOM 2024.09.03,
Coil 2.7.0.

Library **3575** entries.

**Verified on device this session:** resuming a chapter loads from the current
page forward; scrolling back from a resume point is quick; downloads unchanged
across two edits to `fetchOrder`; backing out mid-load and reopening shows
spinners rather than failures; RTL paging and its slider; grayscale and invert
together; the chapter picker; reader settings across a reopen; SpyFakku covers
filling in without being opened; 233 covers repaired in one partial sweep; the
covers count surviving a stop; Resume landing on the chapter in progress; marking
a chapter unread moving Resume off it; source tabs and the failure tally showing
names; title and author search; the writer/artist split; a targeted refresh
leaving the full sweep's resume point alone; "Refresh what's missing"; the
chapter swipe on `me.saket.swipe`; transition rows at both ends; read-marking
with the rows in place; scrolling a row into view to change chapter; no flash
between chapters.

**Unverified:** a short chapter whose pages don't fill the screen, in 0.106.
