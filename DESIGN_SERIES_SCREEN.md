# Design — the series screen pass

Written before any code, for the reason `SESSION_HANDOFF_0.106.md` §11 gives about
the three-chapter reader: the risky part of this change is **silent**, and three
of that session's bugs came from changes I was confident about.

Covers the Trello "To add" cluster: download options, filter/sort/display,
Options menu, the three top-bar icons, and a top bar that moves while scrolling.
Seven cards, one pass — they share a top bar, a menu host and a bottom sheet.

Reference read this session, from `jobobby04/TachiyomiSY` at `master`:
`MangaToolbar.kt`, `DownloadDropdownMenu.kt`, `ChapterSettingsDialog.kt`,
`MangaScreenModel.kt` (`runDownloadAction`, `getUnreadChaptersSorted`).

---

## 0. The thing that will break, if anything does

**`onOpen` takes a list index, and so does the reader.**

```kotlin
onOpen = { openChapter(it) }          // SourceBrowseScreens.kt:1174 side
fun openChapter(index: Int)           // MainActivity.kt:689
onPrev = { openChapter(chapterIdx - 1) }
onNext = { openChapter(chapterIdx + 1) }
```

`openChapter` indexes into `chapterList` in `YomuApp`. The series screen renders
`chapters` — **the same list, same order** — so an index means the same chapter on
both sides today. That is the invariant this whole feature breaks.

Filter or re-sort the chapter list on the series screen and index 3 on the screen
is no longer index 3 in `YomuApp`. The failure mode is not a crash:

- the FAB opens the wrong chapter,
- Prev/Next in the reader walk the *unfiltered* order,
- `resumeIndex` points somewhere plausible and wrong,
- and read-marking marks the wrong chapter.

This is `headRows` from 0.104/0.106 arriving in a second place — four call sites
that assumed list index == page index — and it took an explicit test to catch
there.

**Decision: the screen filters and sorts what it *draws*, and `YomuApp` keeps the
canonical list. Every crossing of that boundary goes by chapter id.**

Concretely:
- `SeriesScreen` computes `visibleChapters` for rendering only.
- `onOpen` changes signature from `(Int) -> Unit` to `(String) -> Unit`, taking a
  chapter id; `YomuApp` resolves it with `chapterList.indexOfFirst { it.id == id }`
  and calls the existing `openChapter(index)` unchanged.
- `resumeIndex` keeps working off the **full** list, because Resume means the
  furthest chapter with progress and that is a fact about the series, not about
  the current filter. A Resume target hidden by the active filter still opens.
- Prev/Next in the reader are untouched and stay in source order.

That last point is a deliberate divergence from SY, which walks the filtered
order. Matching it means teaching the reader about the filter, which is a change
to the reader on a release about the series screen — exactly the blast-radius
rule `SESSION_HANDOFF_0.83.md` §6 records. **Not now.** Write it down as the
known gap: with "Unread" filtered on, Next in the reader still goes to the next
chapter in source order.

`chapterKeyOf(sourceId, chapter)` is already id-based, so read state, saved pages
and the index are all unaffected by any of this.

---

## 1. What the reference actually does

`MangaToolbar` puts **Download** and **Filter** in the bar as icons, and
everything else in the overflow:

| Slot | Contents |
|---|---|
| Icon | Download → dropdown |
| Icon | Filter → tabbed dialog. **Tinted when any filter is active** |
| Overflow | Refresh, Edit categories, Migrate, Share, Notes, (SY: Merge, Edit info, Recommends) |

`DownloadDropdownMenu`: Next 1 / Next 5 / Next 10 / Next 25 / Unread /
Bookmarked.

`runDownloadAction` is the part worth copying exactly:

```kotlin
NEXT_5_CHAPTERS -> getUnreadChaptersSorted().take(5)
// getUnreadChapters(): !chapter.read && dlStatus == NOT_DOWNLOADED
```

**"Next 5" means the next five that are unread *and not already on disk*.** Not
"the next five rows". Getting this wrong makes the menu re-queue chapters the
user already has, which looks like the button doing nothing.

`ChapterSettingsDialog`: three tabs — Filter (Downloaded / Unread / Bookmarked /
Scanlator), Sort (source / number / upload date / alphabetical), Display (title
vs chapter number) — plus an overflow with "Set as default" and "Reset".

---

## 2. What of that this app can have

| Reference item | Here | Why |
|---|---|---|
| Filter · Downloaded | **yes** | `Downloads.isComplete` already per row |
| Filter · Unread | **yes** | `ReadState.isRead` already per row |
| Filter · Bookmarked | **no** | No backing field anywhere in the app. §4 lists it beside Lewd/Language/Status as blocked on a data model decision, not on an index |
| Filter · Scanlator | **yes** | `Chapter.scanlator` exists and is already rendered |
| Sort · source order | **yes** | The list as fetched |
| Sort · upload date | **yes** | `Chapter.dateUploaded` |
| Sort · alphabetical | **yes** | `Chapter.name` |
| Sort · chapter number | **needs a field** | §3 |
| Display · title vs number | **needs the same field** | §3 |
| Overflow · Refresh | **yes** | Re-run the chapter fetch |
| Overflow · Edit categories | **yes** | `CategoryAssignDialog`, already wired to the action row |
| Overflow · Share | **yes** | `Intent.ACTION_SEND` with the series url |
| Overflow · Migrate | **no** | §7 item 17 — a whole feature, not a menu row |
| Overflow · Merge / Edit info / Recommends | **no** | SY-only, each its own subsystem |
| "Set as default" / "Reset" | **see §4** | Depends on the per-series decision |

Three of the eleven reference rows are not buildable here and two of those were
already recorded as blocked. That is the value of having read the attachments.

---

## 3. `Chapter.chapterNumber` — the one model change

`SChapter.chapter_number: Float` exists in the vendored API
(`source-api/.../model/SChapter.kt:14`). The app's `Chapter` (`Source.kt:211`)
drops it.

Without it there is no "sort by chapter number" and no Display tab at all — SY's
Display tab offers exactly one choice, title vs number, and with no number it has
nothing to say.

```kotlin
data class Chapter(
    val id: String,
    val name: String,
    val handle: Any? = null,
    val dateUploaded: Long = 0L,
    val scanlator: String? = null,
    /** From `SChapter.chapter_number`; -1f when the source didn't publish one. */
    val number: Float = -1f,
)
```

Three things this touches, and the third is the one that bites:

1. `TachiyomiSourceAdapter` populates it where it builds a `Chapter`.
2. `LocalSource` leaves the default.
3. **`ChapterCache` serialises app-owned fields.** Every cached chapter list on
   every device predates this field. It must default rather than throw, and the
   default must be the "source didn't say" value — **not 0f**, which sorts as a
   real chapter zero and would put every cached list's chapters in a heap at the
   top under the number sort. This is `SeriesIndex`'s three-state trap (§5) in a
   new store: present, said-zero, never-recorded.

Sorting rule: `number < 0` sorts last under the number sort, never among the
real ones.

**This ships on its own** (§6, stage 1). It is a model field crossing the
adapter, the cache and the local source, and `SESSION_HANDOFF_0.83.md` §6's rule
is that a change with that blast radius does not ride along with a feature.

---

## 4. Per-series or global?

SY stores chapter filter/sort/display **per manga**, with a global default and a
"set as default" action.

`ReaderPrefs` faced this exact choice and went global, and §4 records why: a
per-series store needs a second store keyed by series id *and* a "use default"
state distinct from every real value, and the enums carrying a `key` make adding
that overlay later additive rather than a rewrite.

**Decision: global, same shape as `ReaderPrefs`, in a new `ChapterPrefs` object.**
Enums carry `key`. No "set as default" row, because with one store every change
already is the default — a row that does nothing is worse than its absence
(§4, on the reader's missing toggles).

The cost, written down so it is a known gap rather than a surprise: turning
"Unread" on for a webtoon leaves it on for the next series opened. If that
becomes annoying, the overlay is the fix and this design does not block it.

`ChapterPrefs` reuses `FilterState` (`LibraryPrefs.kt:67`) rather than declaring a
second tri-state enum.

---

## 5. The top bar

There is no top bar today. `BackButton` sits in a `Row` inside the first
`LazyColumn` item (`SourceBrowseScreens.kt:565-572`), so it scrolls away with the
cover backdrop. Both "add the icons to the top bar" and "make the top bar move
while scrolling" need one to exist first.

SY's approach, and it suits this screen better than `enterAlwaysScrollBehavior`:
the bar is **always present**, and title alpha and background alpha are driven by
scroll position, so it is invisible over the backdrop and fades in as the cover
leaves. `titleAlphaProvider` / `backgroundAlphaProvider` in `MangaToolbar`.

Here that reads off the existing list state:

```kotlin
val listState = rememberRestoredListState(scroll, "series", series.id)
val barAlpha by remember {
    derivedStateOf {
        if (listState.firstVisibleItemIndex > 0) 1f
        else (listState.firstVisibleItemScrollOffset / 400f).coerceIn(0f, 1f)
    }
}
```

`derivedStateOf`, not a plain read: without it every pixel of scroll recomposes
the whole screen.

**Two hazards in this specific change.**

- The root becomes a `Scaffold`. The FAB and `ChapterSelectionBar` are currently
  aligned inside a `Box` (`Modifier.align(Alignment.BottomEnd)` / `BottomCenter`)
  — that alignment is only available inside `BoxScope`, so the `Box` stays
  *inside* the Scaffold's content rather than being replaced by it.
- `rememberRestoredListState` must be hoisted above the Scaffold so both the bar
  and the list read the same state object. It is currently constructed inline at
  the `LazyColumn` (line 537). Constructing it twice gives the bar a state that
  never scrolls, and the symptom is a bar that simply never fades in — which
  reads as the alpha maths being wrong.

**Not in this pass: the scroll handle.** "All scrolls should have a scroll
handle" is its own Trello card covering every list in the app, Compose has no
built-in, and SY's `MangaScreen` at master doesn't use one — so there is no
reference to copy and it needs its own investigation. Splitting it out of card 7
leaves that card as "top bar moves while scrolling", which is done here.

---

## 6. Release split

Six releases. Each is separately installable and separately checkable, which is
what the 0.88–0.93 run did and what `SESSION_HANDOFF_0.106.md` §8 credits for
that run carrying no untested tail.

| | What | Blast radius | Check |
|---|---|---|---|
| **1** | `Chapter.number` through the adapter, `LocalSource` and `ChapterCache` | Model — every source | Nothing visibly changes; a cached list still opens offline |
| **2** | `material-icons-extended` | +~1 MB APK | Download, pin and source-visibility glyphs are real icons |
| **3** | Top bar: Scaffold, alpha fade, back moves into it | Layout root of one screen | Scroll fades the bar in; FAB and selection bar still land; the list keeps its scroll across opening a chapter |
| **4** | `onOpen` by id + Filter/Sort/Display sheet | **The §0 hazard** | Filter to Unread, open a chapter from the filtered list, confirm it is the right one; Resume still lands correctly |
| **5** | Download dropdown | One screen | "Next 5" skips what is already downloaded |
| **6** | Options overflow: Refresh, Edit categories, Share | One screen | Each acts; Categories only shows in-library |

Stage 2 is optional and independent — it is board card "Change the download
icon" and §7 item 22, and it fixes three stand-in glyphs at once. Doing it
*before* stage 5 means the download menu ships with a real download icon rather
than `KeyboardArrowDown` standing in for the third time.

**Stage 4 is the one to be slow about.** It is the only stage that can produce
wrong behaviour that looks right.

---

## 7. Marker audit for this work

Per §5, before handing over any of these files, grep for identifiers from every
earlier change in the same file. For `SourceBrowseScreens.kt` the list is:

`me.saket.swipe`, `SwipeableActionsBox`, `swipeThreshold = 96.dp`, `isUndo`,
`lastTouched`, `credits`, `"Art"`, `rememberRestoredListState`,
`scroll.sync(series.id)`, `confirmDeleteSelection`, `CoverViewer`,
`onGlobalSearchTag(series.title)`.

`SourceBrowseScreens.kt` uses **individually imported icons** — not the wildcard
block. Every new icon needs its own import line, which is the 0.98 CI failure
(`SESSION_HANDOFF_0.106.md` §9) waiting to happen a second time.

The row composables to reuse — `TriFilterRow`, `SortRow`, `CheckRow`,
`SheetHeader` (`LibraryOptions.kt:305-388`) — are top-level `private`, which is
file-scoped in Kotlin. They need to become `internal` to be called from
`SourceBrowseScreens.kt`. Same package, so no import is needed.
