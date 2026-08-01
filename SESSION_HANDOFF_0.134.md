# Session handoff — 0.123 to 0.134, bookmarks finished, 18+ everywhere, and the reader

Written 2026-08-01, late. Nothing here supersedes anything.
`SESSION_HANDOFF_0.83.md`, `SESSION_HANDOFF_0.87.md`, `SESSION_HANDOFF_0.106.md`,
`SESSION_HANDOFF_0.120.md` and `SESSION_HANDOFF_0.122.md` all remain live
reference. This file continues straight on from 0.122, which ended with
minification reverted.

**Twelve releases, every one exercised on device before the next was written.**
One red CI run, from two identifiers I assumed rather than checked.

---

## 0. Read this before touching anything

**1. `headRows` is 1 in every reader mode now, and it used to be strip-only.**
§4. Paged mode is where read-marking has always worked, so it is the mode with
something to lose, and the failure is silent. Every index that crosses between
page numbering and list/pager numbering adds it.

**2. `SourceNsfw` is `SourceNames` one field over, and the shape is now a
pattern rather than a one-off.** §3. Anything that needs to know something about
a *source* from a screen that cannot afford to classload extensions gets
answered this way: a record written where the information already exists.

**3. The scroll handle takes four numbers, not a state object**, so the second
caller is three lines rather than a second copy. §5.

**4. Long-strip zoom is done but not finished.** Two follow-ups are on the
board and both are real: double-tap zoom lands instantly and should animate, and
there is no fling on the pan. §6.

---

## 1. The releases

| | What | Commit | State |
|---|---|---|---|
| 0.123 | Bookmarked filter row; `DownloadChoice.BOOKMARKED` | `4cdf405` | Verified |
| 0.124 | Swipe right-to-left to bookmark | `dfad862` | Verified |
| 0.125 | Accent colour setting | `462e933` | Verified |
| 0.126 | Hide 18+ sources and extensions | `024b317` | Verified |
| 0.127 | 18+ filter in the library | `06e2926` | Verified |
| 0.128 | Classify unclassified sources by hand | `340bb65` | Verified |
| 0.129 | Pull to refresh the chapter list | `9b9a656` | Verified |
| 0.130 | Chapter transitions in paged mode | `6156211` | Verified |
| 0.131 | Long-strip pinch zoom | `ca4f10d` | **CI red** — two bad identifiers |
| 0.131 | The import package and the opt-in | `dc8ac61` | Verified |
| 0.132 | Double-tap zoom; wider pan angle | `689a2f0` | Verified |
| 0.133 | Library scroll handle; diagonal panning | `99e0cf7` | Verified |
| 0.134 | Handle wider, lingering, and conflated | `f2449d5` | Verified |

---

## 2. Bookmarks, finished

0.120 shipped the store and left two things unbuilt. Both are now in.

- **`ChapterPrefs.filterBookmarked`**, tri-state on `ch_f_bm`, in
  `anyFilterActive` so the funnel still tints honestly, applied in
  `visibleChapters`.
- **`DownloadChoice.BOOKMARKED`**, and it **ignores read state on purpose.**
  Every other choice is a form of "what should I read next", so unread is part
  of the question. A bookmark survives both reading a chapter and marking it
  unread — that independence is 0.120's design — so filtering bookmarks by
  unread would silently skip the chapter someone bookmarked *because* they had
  read it. Not-already-downloaded still applies to every choice.
- **Swiping right-to-left bookmarks** (0.124). Both directions used to mark
  read. Different container colours, not just different glyphs, because the
  icon is only visible mid-drag.

**Three notes that claimed bookmarks didn't exist are gone**, and a fourth was
wrong in a more interesting way. `LibraryOptionsSheet` listed *Bookmarked*
beside Lewd and Language as "no backing field". The field exists now — but a
*library* filter asks "does this series have any bookmarked chapter", and
`Bookmarks` is keyed by chapter with no index the other way. Answering that for
3575 entries means walking every `bm:` key on a per-interaction path. **It moved
from blocked-on-a-model to blocked-on-an-index**, like the counts before
`SeriesIndex`, and the KDoc there now says so.

---

## 3. 18+, and `SourceNsfw`

Three releases, and the middle one is the reusable part.

**0.126 — hiding sources.** `SourcePrefs.showNsfw`, default **true**. Folded
into `isVisible` rather than checked beside it: that predicate gates both the
Sources list and the global-search fan-out, and a source hidden from one has to
be hidden from the other. An extra `&& showNsfw` at each call site is §5's "two
predicates answering one question will disagree", and the way it would have
disagreed is results arriving from a source you cannot see. Extensions are
filtered too — an 18+ extension left listed while its sources are hidden is a
one-tap route back to what was switched off.

**0.127 — filtering the library.** A separate tri-state row, *not* an extension
of the Browse switch. Filtering a library belongs to the library (§4), and a
Settings switch silently removing rows from the Library tab reads as data loss.

**`SourceNsfw.kt` is why that was possible.** `LibraryEntry` stores `seriesId`,
`sourceId`, `title`, `cover`, `addedAt` — nothing about content. So a saved
series can only be classified by its source, and asking a source means
`listAllSources()`, which classloads extension APKs and cannot happen in
composition. That is the wall that blocked Group → Sources, and it has a solved
shape: **a record written at the point the information exists.** It learns from
`listAllSources` and from the repo index, exactly where `SourceNames` does.

- **A map, not a set of the 18+ ones.** A set collapses "known safe" into
  "never seen", and this store has the usual three states. Only a map can record
  a source that stops being flagged.
- **Unknown reads as "the condition doesn't hold"** — Include hides it, Exclude
  keeps it. Chosen direction: a source nothing has classified should not make a
  saved series vanish from a library someone is looking at.
- **It classifies a source, not a series.** A mixed-content source marks
  everything saved from it. Series-level needs a field `LibraryEntry` has
  nowhere to put, which is the model decision this sidesteps.

**0.128 — the gap that showed up immediately.** Two sources in the library were
neither installed nor in any index, so nothing could classify them and Exclude
left them showing. Settings → Browse → **Unclassified sources** lists every
library source with no entry and lets one be set by hand. The list is computed
once on open and does not update as switches move — classifying is what removes
a row, so a live list deletes each row from under the finger that tapped it. Only
touched switches are recorded: an untouched one must not store "not 18+", which
is a claim nobody made.

**One of those two was identified and it is worth writing down.** `tachi:6901`
is **E-Hentai** — TachiyomiSY's `source-api/.../exh/source/SourceIds.kt` declares
`LEWD_SOURCE_SERIES = 6900` and `EH_SOURCE_ID = LEWD_SOURCE_SERIES + 1`. A fork
built-in, which is precisely why no index has it. The other, ending `831257`, is
**unidentified**: not an SY built-in, not in the 1367-extension Keiyoushi index,
and not recoverable from the id — Tachiyomi computes a source id as MD5 of
`name/lang/versionId` (verified: that formula reproduces 995 of 1043 live ids),
and a 7,200-candidate sweep found no match. **The only place it survives is the
user's own backup, field 101.** `TachiyomiImport` records names from that field
now, but that code landed in 0.94–0.96 and this library was imported at
`931cf5f`, long before — so it never ran. A re-import would name it; it would
also remove non-favourite entries, so it is not free. A "learn source names from
a backup" action that reads field 101 and nothing else was offered and declined.

---

## 4. The reader

### Chapter transitions in paged mode (0.130)

The pager now carries a transition page at each end, the pair the strip has had
since 0.104. **`headRows` stopped being strip-only** and is 1 everywhere.

- **`settledPage`, not `currentPage`, decides when to advance.** A fling from
  page 3 to the tail reports every page it passes through on `currentPage`;
  `settledPage` moves once, when the scroll stops. Reading the live value would
  change chapter mid-fling from a page nobody stopped on — §5's library tab row
  in a new place. It was already used in `LibraryScreens`, so it is a proven
  identifier here rather than an assumed one.
- **The pager's page count is `pages.size + headRows * 2`, not a literal.** It
  was written with a `2` first; two copies of one offset drift the moment either
  is edited, which is what 0.100 fixed for the refresh cursor.
- A latent crash closed on the way past: the strip's `currentPage` used
  `coerceIn(0, pages.lastIndex)`, which throws on an empty list.

### Long-strip zoom (0.131–0.134)

**Scales the whole `LazyColumn`, not a page** — SY scales its whole
`WebtoonRecyclerView`, and zooming an item fights the scroll the mode exists
for. `graphicsLayer` scales the rendering and leaves layout alone, so the strip
overflows its box and needs `clipToBounds`.

- **`Modifier.transformable`'s `canPan` overload is what makes this possible on
  a scrollable list.** Refusing pan at scale 1 leaves the LazyColumn's drags
  untouched, so at rest the strip behaves exactly as before. **It exists in
  foundation 1.7** — I predicted it wouldn't and was wrong; the compiler never
  objected to the parameter, only to it being experimental.
- **Max 3x and the clamp `halfWidth * (scale - 1)`** are SY's numbers exactly,
  arrived at independently and then confirmed against `WebtoonRecyclerView`.
- **Double-tap goes to 2x centred on the tap**, using SY's arithmetic
  `(halfWidth - tapX) * (scale - 1)` so the thing you tapped lands in the
  middle. Cost: a detector that handles double taps must wait out the timeout
  before reporting a single one, so raising the bars in long strip is slightly
  delayed. SY pays this too.
- **The pan angle took two goes and the first was a wrong *rule*, not a wrong
  number.** 0.131 refused the whole gesture when a drag was more vertical than
  horizontal, so diagonal drags did nothing. SY discards the vertical
  *component* instead: `dy = if (atFirstPosition || atLastPosition) y - downY
  else 0`. 0.133 does better than discarding it — it hands the vertical half to
  `listState.scrollBy`, so a diagonal pans and scrolls at once. Panning still
  wins at the ends of the list, the only place with vertical overhang.

---

## 5. The scroll handle

`ScrollHandle` in `Ui.kt`, on the library grid.

- **It takes four integers and a callback, not a state object.**
  `LazyGridState` and `LazyListState` share no supertype exposing what it needs,
  and the card is "*all* scrolls should have a scroll handle". The next caller
  is three lines.
- **It seeks by item index, not pixels.** Grid rows are not a fixed height — a
  Comfortable cell carries a title, Cover-only doesn't, Adaptive columns change
  how many items a row holds. Index means the same thing in every display mode.
- **The thumb is derived from the scroll, never driven alongside it.** Dragging
  calls back and waits to be told where it landed. A private thumb position kept
  in step with a scroll position is two things driving one value.
- **`rememberRestoredGridState` is hoisted** so the handle and the grid share one
  state object. Two would give the handle one that never scrolls, and the
  symptom is a handle that never moves — which reads as bad arithmetic rather
  than as two objects. The 0.109 top bar, one screen over.
- **0.134 fixed drag lag at the cause.** Every drag delta launched its own
  `scrollToItem`, and those queue on the grid's scroll mutex and run *in order*,
  so the grid was always finishing a jump to where the finger had been. The
  target is now held in state and scrolled from a keyed `LaunchedEffect`, so
  Compose cancels the superseded one. **Adding gain would have been the wrong
  fix**: the mapping is already 1:1 with the track, so a gain overshoots the end
  before the thumb reaches the bottom.

---

## 6. Wanted next on the zoom, both real

1. **Double-tap zoom should animate.** It lands instantly at the moment. SY runs
   an `AnimatorSet` over scale and both translations, 200ms on a
   `DecelerateInterpolator` (`ANIMATOR_DURATION_TIME` in `WebtoonRecyclerView`).
   The Compose shape is an `Animatable` per value, or `animateFloatAsState`
   gated so a pinch stays instant — a pinch must not be animated or it lags the
   fingers, so this needs a flag distinguishing the two sources of a scale
   change, not a blanket animation.
2. **The pan has no fling.** SY's `zoomFling` coasts with a 0.4 distance-time
   factor over 400ms, and that is why its panning feels fast where this one
   feels like dragging. Compose's `transformable` does not expose the velocity
   this needs, so it means a custom gesture detector — and 0.101 and 0.102 are
   two custom detectors that never fired once. **Read §5 of
   `SESSION_HANDOFF_0.106.md` before starting.**

---

## 7. Two identifiers, one red build

0.131 failed on:

```
e: ReaderScreen.kt:14:36 Unresolved reference 'clipToBounds'.
e: ReaderScreen.kt:420:14 This foundation API is experimental ...
```

`clipToBounds` is in `androidx.compose.ui.draw`, not `androidx.compose.foundation`,
and `transformable`'s `canPan` overload needs `ExperimentalFoundationApi` in the
opt-in. **Both were one grep away** — `SourceBrowseScreens.kt` imports
`ExperimentalFoundationApi` on line 14. Fifth CI failure in this project from an
identifier assumed rather than checked, and it happened in the same session that
avoided the same trap three times by checking (`PullToRefreshBox` living in a
sub-package the `material3.*` wildcard doesn't reach; `settledPage` proven in
`LibraryScreens`; `MAX_STRIP_ZOOM` placed after the imports rather than between
an annotation and its declaration).

**What worked, repeatedly: check the file's own import list, not the tree.**

---

## 8. The board

Done this session: bookmarks filter/action, bookmark swipe, accent colour,
show/hide 18+, paged chapter transitions, long-strip zoom, library remembers its
tab, and the series-screen pass's leftovers.

Still open:

- **Minification** — parked with a full plan; `SESSION_HANDOFF_0.122.md` §6.
- **Scroll to refresh** — done on the series screen only; Library, Browse,
  History and Downloads have no refresh action to hang it on.
- **All scrolls should have a scroll handle** — the library grid has one; every
  other list is three lines away.
- **Animate the double-tap zoom** and **fling the zoom pan** — §6.
- **A feed tab in Browse** — still blocked on the sweep recording what changed
  rather than overwriting counts.
- **AHottie** is the only actionable bug: no covers, no tabs, chapters load
  forever. It is `PROJECT_HANDOFF.md` §7 item 14 coming true — the one lib 1.6
  extension, flagged there as "may fail at runtime". The "no tabs" half has the
  shape of 0.86's Roku Hentai fix, so it may be two faults.
- Elite Babes and BeeHentai remain upstream.

---

## 9. State of the tree

Head is `f2449d5` (0.134). Build environment unchanged from 0.122.

New files: `SourceNsfw.kt`. `ScrollHandle` is in `Ui.kt`.

**Verified on device this session:** the Bookmarked filter in all three states
and the Bookmarked download action including read-but-bookmarked chapters; the
bookmark swipe, including that it does nothing during multi-select; all six
accent colours through a theme change and a force-stop; 18+ hidden from Sources,
Extensions and global search with the counts agreeing; the library 18+ filter;
the unclassified-sources dialog; pull to refresh on a series; paged chapter
transitions in both directions including RTL, **with read-marking checked
explicitly**; long-strip pinch zoom, scrolling while zoomed, diagonal panning,
double-tap to a tapped point; and the scroll handle in every display mode,
including that it stays hidden when a category fits on screen.

**Revoke the tokens.** The one used this session and the one
`SESSION_HANDOFF_0.120.md` flagged, which may still be live.
