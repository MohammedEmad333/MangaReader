# Session handoff — 0.107 to 0.117, the series screen

Written 2026-08-01. `SESSION_HANDOFF_0.83.md`, `SESSION_HANDOFF_0.87.md` and
`SESSION_HANDOFF_0.106.md` all remain live reference — nothing here supersedes
them. `DESIGN_SERIES_SCREEN.md` in the repo root is the design this session was
built from and is still accurate except where §2 and §3 below correct it.

Eleven releases. Every one exercised on device. Two red CI runs, both from the
same class of mistake and neither caught by the existing pre-push checks.

---

## 0. Read this before touching anything

**1. The series screen pass is done.** Seven Trello cards, six planned releases,
eight actual. Top bar with download / filter / options, a Filter-Sort-Display
sheet, and chapter numbers that exist. `SESSION_HANDOFF_0.87.md` §8 items 1–7 are
closed.

**2. `SChapter.chapter_number` exists and almost nothing fills it.** §3. This is
the most reusable thing in this file: **a vendored field being present says
nothing about anything writing to it.** It cost a release that shipped a sort
with nothing to sort on.

**3. An id is the wrong `remember` key for anything derived from an object's
contents.** §7. All three series-open paths render a handle-less stub first and
replace it under the same id, so a key of `series.id` never sees the object fill
in. Share was dead on the whole Library path because of it.

**4. `onOpen` now takes a chapter id, not a list index.** §2. If anything ever
opens the wrong chapter, start there.

**5. The APK went 17 MB → 24 MB and that is not the icon pack's fault.**
`isMinifyEnabled = false` on release, and CI builds debug. Nothing is shrunk, so
`material-icons-extended` ships whole. §8 item 3.

---

## 1. The releases

| | What | Commit | State |
|---|---|---|---|
| 0.107 | `Chapter.number` through the adapter and `ChapterCache` | `53349ed` | Verified |
| 0.108 | `material-icons-extended`; real download/pin/filter glyphs | `ad0996d` | Verified |
| 0.109 | Series top bar, alpha fade on scroll | `2c2d72f` | Verified |
| 0.110 | `onOpen` by id; Filter/Sort/Display sheet | `38272a7` | **CI red** — declaration order |
| 0.111 | The same, declared before use | `9122c57` | Verified |
| 0.112 | `ChapterRecognition`; primary darkened | `b66a493` | Verified |
| 0.113 | Badges and dimming on browse and global search | `0d5909e` | Verified |
| 0.114 | The same on History and Downloads | `63078c4` | Verified |
| 0.115 | Download menu: next 1/5/10/25/unread | `e36d649` | Verified |
| 0.116 | Options overflow: Refresh, Edit categories, Share | `849ab53` | Share missing from Library |
| 0.117 | Share url keyed on the handle | `b205c99` | Verified |

---

## 2. The index boundary, and why it had to move

`onOpen` handed `YomuApp` a **list index** and `openChapter` indexed its own
`chapterList`. That was safe for exactly as long as the series screen drew that
list unchanged.

Filtering and sorting ended it. An index from the drawn list means a different
chapter on the other side, and **nothing reports it**: the wrong chapter opens,
the reader's Prev/Next walk the wrong order, and read state lands on the wrong
row. It is `SESSION_HANDOFF_0.106.md` §2's `headRows` bug in a second place, and
the failure mode is silent in both.

The screen now decides only what to **draw**:

- `visibleChapters(context, chapters, sourceId)` builds the rendered list.
- `onOpen: (String) -> Unit` takes a chapter id; `YomuApp` resolves it with
  `indexOfFirst` and calls the existing `openChapter(index)` untouched.
- `resumeIndex` still indexes the **full** list, because Resume is a fact about
  the series and not about the current filter. A target the filter is hiding
  still opens.
- Select all takes `visible`, not `chapters` — selecting rows a filter is hiding
  and then deleting them is not what the button looks like it does.

**A deliberate divergence from SY, written down so it isn't found as a bug:**
with a filter on, Next in the reader still goes to the next chapter in *source*
order, not the next visible one. Matching SY means teaching the reader about the
filter, which is a change to the reader on a release about the series screen.

---

## 3. `chapter_number` is declared and empty

0.112 exists because 0.107 was wrong.

`SChapter.chapter_number` is in the vendored API, defaults to `-1f`, and
`prepareNewChapter` is an empty hook. **Upstream Tachiyomi computes chapter
numbers in the app, from the chapter name**, and extensions were written against
that — so nearly every source leaves the field at its default.

0.107 wired `Chapter.number` straight from it on the strength of the field
existing. The result passed CI, installed cleanly, and shipped a "sort by chapter
number" that did nothing and a Display mode identical to the one beside it. Both
were the fallback behaving correctly on empty data, which is why it read as two
unrelated cosmetic faults rather than one missing feature.

`ChapterRecognition.kt` is SY's `ChapterRecognition` ported, behaviourally
identical — its regexes encode years of real chapter titles. Applied in three
places: the adapter (with the series title stripped first, so *One Piece 1052*
isn't chapter 1), `ChapterCache.load` (so a series read offline sorts like the
same series read online), and `LocalSource` (the filename is the chapter name).
A real `chapter_number` still wins when a source sets one.

Verified against eight titles before shipping: `Vol.1 Ch. 4` → 4, `Prison School
12 v.1 vol004` → 12, `Ch. 12.5` → 12.5, `Chapter 5 extra` → 5.99, `Chapter 7a` →
7.1, `Oneshot` → no number.

- **The check that would have caught this is one grep of the extension source
  for `chapter_number =`,** not a grep of the API for `chapter_number`.
- **`NO_NUMBER` is `-1f` and must not be `0f`.** Every `ChapterCache` file
  predating 0.107 lacks the field; defaulting to zero would pile every offline
  chapter at the top of a number sort. Third appearance of `SeriesIndex`'s
  three-state trap — present, said-zero, never-recorded.
- Unnumbered chapters sort **last in both directions**. A plain `compareBy` on
  the sentinel flips them to the top when the direction reverses, which reads as
  the sort losing chapters.

---

## 4. `EntryMarks` — badges on every series list

The unread count, the DL chip and read-dimming were private to
`LibraryScreens.kt`. They now render on per-source browse, global search, History
and Downloads as well.

`EntryBadges` and `MiniBadge` moved to `Ui.kt` as `internal`, and the copies in
`LibraryScreens.kt` are **deleted** — two implementations of one badge is how
they drift.

**The part that matters is `rememberEntryMarks(libraryTick)`.** Every field
behind it is a whole-store read: `DownloadIndex.seriesIds` parses an index file,
`SeriesIndex.all` parses a JSON string, `Categories.seriesIn` parses the
assignment map. It is called **once per screen**. Asking per row is §5's "an
import is a load test" — the category chips did exactly that and froze the app at
3567 entries, and a global search can put several hundred cells on screen.

**Anything new that lists series calls it the same way.**

Two judgements: no DL chip on the Downloads tab, where every row is downloaded by
definition and the badge would always be on and say nothing; and badges sit
*beside* the supporting text on History and Downloads rather than over the cover,
because 40dp and 44dp thumbnails can't carry a chip without hiding the art.

---

## 5. The top bar

There wasn't one. `BackButton` sat inside the first `LazyColumn` item and
scrolled away with the cover.

**Not `Scaffold`'s `topBar` slot**, which is what the design called for and what
was not built: it insets its content below the bar, and the point here is the
cover art running *behind* a transparent one. It would also have meant relocating
the FAB and the selection bar, both of which align against the existing `Box`. So
the `TopAppBar` is the last child of that `Box`, drawn over the list, and the
header reserves `TOP_BAR_HEIGHT` where the back button used to sit.

- **The alpha rides on the container colour, not the bar.** Fading the bar takes
  the back arrow with it, and the arrow has to stay visible against the art from
  the first frame.
- **`TOP_BAR_FADE_OVER` is a dp converted through `LocalDensity`.**
  `firstVisibleItemScrollOffset` is pixels; a raw pixel constant fades over a
  different distance on every phone.
- **`rememberRestoredListState` is hoisted** above the `Box` so the bar and the
  list share one state object. Two would give the bar a state that never scrolls,
  and the symptom is a bar that never fades in — which reads as the arithmetic
  being wrong rather than as two objects.
- **The scroll handle was split out**, into the existing "All scrolls should have
  a scroll handle" card. Compose has no built-in and SY's `MangaScreen` doesn't
  use one at master, so there is no reference to copy.

---

## 6. Two CI failures, and the check that now exists

**0.110 — `Unresolved reference 'optionsTick'`.** The `visible` block was
inserted above `downloadedCount`; `optionsTick` was declared beside `coverOpen`
three lines below it. Kotlin locals must be declared before use.

Neither existing countermeasure sees this. The brace-balance check passes. The
marker audit passes — `optionsTick` existed and was spelled correctly. This is
the fifth CI failure in the project from an identifier assumed rather than
checked, and **the first from ordering rather than existence.**

There is now a **declaration-before-use scan** in the pre-push checks: for each
local of interest, find its declaration line and look for any earlier line
mentioning it. It runs in about a second and found nothing else.

**0.116 — Share silently absent.** §7. Not a CI failure; it built and installed
and simply did nothing on one of the three open paths.

---

## 7. An id is stable across the transition that fills the object in

`seriesUrl` was resolved in `YomuApp` with `remember(activeSeries?.id,
activeSourceId)`.

All three open paths put a stub on screen before any network request — that is
deliberate and documented (§4, "the three ways a series gets opened"), and
`openFromLibrary`'s stub carries title and cover and **no handle**. The fetched
series replaces it *under the same id*. So the key never changed, the value was
computed once against an object with nothing to ask, and Share was missing on
every series opened from the Library. From Browse it worked, because that path
has a real handle from the start.

Keyed on `activeSeries?.handle` instead.

**The general form: an id is stable across exactly the transition that fills the
object in, so it is the wrong key for anything derived from the object's
contents.** Any future `remember(series.id) { something(series) }` in this app
has the same hole, and there is no compiler or test that will say so.

---

## 8. The board

1. **`isMinifyEnabled = false`, so nothing is shrunk.** The APK is 24 MB against
   17 before `material-icons-extended`; the ~1 MB figure quoted for that pack
   assumes R8. Turning minification on is its own release with its own risk —
   this app classloads extension APKs and R8 can strip what reflection needs.
2. **Bookmarks have no backing field**, which blocks SY's Bookmarked filter and
   its Bookmarked download action. There is now an "Add bookmarks" card; building
   it unblocks both.
3. **A scanlator filter needs a per-series store.** Scanlator names are
   series-specific, so the global `ChapterPrefs` is the wrong shape. Dropped from
   the sheet with a note in the UI saying so.
4. **Chapter filter/sort/display is global, like `ReaderPrefs`.** Filtering to
   Unread on a webtoon leaves it filtered on the next series opened. The enums
   carry a `key`, so a per-series overlay stays additive.
5. **Paged mode has no chapter transitions** — its own card, and the hazard is
   recorded on it: `headRows` would stop being strip-only and gain a fifth
   caller, in the mode where read-marking currently works.
6. Unchanged from `SESSION_HANDOFF_0.106.md` §10: BeeHentai and Elite Babes
   upstream, long-strip zoom, scroll handles, seek not reprioritising the fetch,
   `CoverRepair`'s missing "asked and got nothing" state, the refresh backlog.

---

## 9. State of the tree

Head is `b205c99` (0.117). Build environment unchanged from 0.106 except
`material-icons-extended` replacing `material-icons-core`. Kotlin 2.2.21, AGP
8.5.2, Gradle 8.9, JDK 17, compileSdk 36, targetSdk 34, minSdk 24, OkHttp 5.4.0,
kotlinx-serialization 1.9.0, Compose BOM 2024.09.03, Coil 2.7.0,
`me.saket.swipe:swipe:1.3.0`.

Two new files: `ChapterPrefs.kt` (the store, the sheet, `visibleChapters`,
`downloadTargets`) and `ChapterRecognition.kt`.

**Verified on device this session:** offline chapter lists surviving the model
change; the three swapped glyphs; the top bar fading in and the chapter list
keeping its scroll under it; opening a chapter from a filtered and re-sorted list
landing on the right one; every sort in both directions; unnumbered chapters
staying at the end; Display by number; badges and dimming on all four new
screens; the download menu skipping what is already on disk; Refresh from Library
and from Browse leaving Back where it was; Share from both paths.

**Note for whoever runs the next session:** CI status was read directly from the
GitHub Actions API this session using a short-lived token. That token was
scoped to one repo and should be revoked; it is not needed to continue.
