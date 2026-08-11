# Session handoff — 0.201, two backlog cards, and a gap before it

Written 2026-08-11. Nothing here supersedes anything. `SESSION_HANDOFF_0.188.md`
(0.150–0.189) and every file under it remain live reference.

One release, two Backlog cards cleared: **History is one row per series** (103)
and **tapping a tag searches by genre** (100). Both shipped in 0.201 and are
**verified on device** — history collapses to one row per series, and the tag
chips search by genre — so both cards are in **Done**.

---

## 0. Read this before touching anything

**1. THERE IS A HANDOFF GAP: 0.190–0.200 was never written up.** The newest
session file before this one, `SESSION_HANDOFF_0.188.md`, covers through 0.189.
Head then reached 0.200 across releases no handoff describes. From the Trello
**Done** column they were: SY-style theme settings (0.195–0.199), animated
images (0.192), pull-to-refresh across screens (0.193–0.194), and
downloaded-chapter size on the series screen (0.200). If you need detail on any
of those, the Trello cards carry it and `git log` has the commits — this file
cannot, because the session that shipped them left no notes and this one did not
do the work. **Do not read the jump from 0.189 to 0.201 here as those releases
being reverted or missing; they shipped, they are just undocumented.**

**2. HEAD IS 0.201, VERIFIED ON DEVICE.** Both card fixes were installed and
checked: History shows one row per series (existing multi-chapter history
collapsed too), and tapping a tag searches by genre. Both cards are in Done.

**3. `History.touch` CHANGED CONTRACT: it upserts BY SERIES now, not by
chapter.** §1. Anything that writes a `HistoryEntry` should know that a second
chapter of a series already in the list now REPLACES that series' row rather than
adding beside it. One-off single-file opens (blank `seriesId`) are unaffected.

**4. TAG CHIPS SEARCH BY GENRE NOW, via a new `Source.applyGenreFilter`.** §2.
The default is `false`; only `TachiyomiSourceAdapter` overrides it.

---

## 1. History is one row per series now (card 103)

The card: *"show the last read chapter and page of a series, not multiple
chapters of the same series."* History was keyed per chapter — `touch` upserted
by `chapterKey` — so reading five chapters of one series left five rows, and the
40-entry cap could fill with one heavily-read series and evict everything else.

Two changes, in `History.kt`:

- **`touch` upserts by series.** It now drops any existing entry whose
  `seriesId` matches the incoming one (as well as the same `chapterKey`) before
  inserting at the front. So a series present in history is replaced, not
  duplicated, and its row always shows the latest chapter and page. The 40-cap
  now holds **40 series**.
- **`forDisplay()` collapses the read side too.** `touch` only fixes history
  written *after* this release; a file written before it still holds several
  chapters per series. `forDisplay` is `list().distinctBy { series-or-chapter
  key }`, and because `list` is newest-first, `distinctBy` keeps the most recent
  chapter of each series. The History screen reads this; **legacy history
  collapses immediately, not only as the user re-reads.**

**`list()` stays the raw store.** `touch` and `remove` key off every stored row,
and Clear-all in `MainActivity` iterates `list()` to remove each `chapterKey` —
`forDisplay` there would leave hidden legacy duplicates behind. Only the five
reads that feed the `history` UI state were switched to `forDisplay`; the
Clear-all iteration was deliberately left on `list`.

**Blank `seriesId` is the one thing not collapsed.** One-off single-file opens
carry no series, and folding them by their empty id would merge every unrelated
single file into one row. Both `touch` and `forDisplay` fall back to `chapterKey`
for those, the rule the model already documented.

**The Tachiyomi import path gets this for free.** `TachiyomiImport` builds up to
40 `HistoryEntry`s and calls `touch` oldest-first; per-series upsert means an
import now lands one row per series (the newest chapter) instead of several.

Files: `History.kt`, `MainActivity.kt` (six reads, one left as `list`).

## 2. Tapping a tag searches by genre, not by title text (card 100)

The card: *"when I tap a tag to search I want it to search for tags not names."*
The tag chips' "Search &lt;source&gt;" action ran the genre string as a **title
query** (`openSource(src, tag, browseMode)`), so tapping *Romance* searched for
series with "Romance" in the title, not series tagged Romance.

The fix mirrors Tachiyomi/Mihon's genre click. A new interface method:

```kotlin
// Source.kt — default matches nothing (LocalSource, sources without filters)
fun applyGenreFilter(genre: String): Boolean = false
```

`TachiyomiSourceAdapter` overrides it: it resets the source's live `filterList`
to defaults, then looks for the tag by name (case-insensitive) in the two shapes
a source exposes genres as —

- a **`Filter.Group`** of per-genre `Filter.TriState` / `Filter.CheckBox`
  children (the common case): set the child to INCLUDE / true;
- a single **`Filter.Select`** whose `values` are genre names: set `state` to
  that index.

— and returns whether it found one. `MainActivity.onSearchTag` then branches:
match → `openSource(src, "", BrowseMode.FILTER)`, which runs a filtered browse
off the now-set filter; no match → the old title search, since many sources
still match a genre inside their text query.

**Three things load-bearing here:**

- **The reset uses the shared live `filterList`, not a fresh one.** The search
  reads that same cached instance (`getSearchManga(page, "", filterList)`), so
  mutating it *is* how the genre reaches the query — the same reason the filter
  sheet edits it in place. `applyGenreFilter` resetting to defaults first means a
  tapped tag searches for exactly that tag, nothing a manual filter set left on.
- **The override MUST be `override`.** Without the keyword the interface default
  (`false`) runs through the `Source` reference in `onSearchTag`, genre search
  never fires, and it looks like the feature does nothing. This was caught in
  review before it shipped; it is the kind of thing no test here would flag.
- **Global search stays a text query, deliberately.** `onGlobalSearchTag` is
  unchanged: genre filters differ per source and can't be applied uniformly
  across a 95-source fan-out, which is also what Mihon does. Only the per-source
  "Search &lt;source&gt;" action became a genre search.

Files: `Source.kt`, `TachiyomiSourceAdapter.kt` (imports `Filter`),
`MainActivity.kt`.

## 3. State of the tree

Head is 0.201. Build environment unchanged from 0.134: Kotlin 2.2.21, AGP 8.5.2,
Gradle 8.9, JDK 17, compileSdk 36, targetSdk 34, minSdk 24, OkHttp 5.4.0,
kotlinx-serialization 1.9.0, Compose BOM 2024.09.03, Coil 2.7.0,
`me.saket.swipe:swipe:1.3.0`. `isMinifyEnabled = true` on debug and working.

Files changed this session: `History.kt`, `MainActivity.kt`, `Source.kt`,
`TachiyomiSourceAdapter.kt`, `WhatsNew.kt` (a `ReleaseNote` for 201),
`app/build.gradle.kts` (200 → 201). No new files.

**0.201 is verified on device** — both cards passed their checks and are in
Done. Needs verifying is back to the three cards that predate this session — 73
(`onRenderProcessGone`), 77 (`getPageList` ceiling) and the 0.200 chapter-size
card — none of which can be exercised without adb, a Cloudflare challenge, or a
runaway source that does not exist.

**Housekeeping, unchanged and still true:** `debug.keystore` and
`release.keystore` are committed at the repo root, and the release keystore is
this app's signing identity.
