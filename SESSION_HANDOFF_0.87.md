# Session handoff — 0.84 to 0.87, the nullable that deleted an extension's error handling

Written 2026-07-31, ~16:30. **This file does not replace `SESSION_HANDOFF_0.83.md`**
— that session's §3 dependency chain and §5 scan procedure are still live
reference. It does **correct** 0.83 §2, which is now wrong; see §1 below.

Four releases. One root cause that three of them circled before anyone read the
extension's source.

---

## 0. Read this before touching anything

**1. The bug board is not empty, and both handoffs said it was.** It opened this
session at six and is at four. `PROJECT_HANDOFF.md`'s opening paragraph and
`SESSION_HANDOFF_0.83.md` §0 both lead with "the bug board is empty, for the
first time in this project's recorded history". That was true when written and
was already false by the time this session started. §6 has the current board.

**2. `SManga.memo` and `SChapter.memo` are non-null now, and that is the whole
fix.** 0.83 vendored them as `JsonObject?`. extensions-lib 1.6 declares
`JsonObject`. Everything in §1 follows from that one character.

**3. The Keiyoushi *source* is fetchable and beats decompiling.** §5. Three
`curl`s answered a question that two sessions of inference got wrong twice.

---

## 1. What was actually wrong

**The vendored API declared `memo` nullable. Upstream declares it non-null.**

```kotlin
// extensions-lib 6e0c96cea8, which every 1.6 extension is compiled against
var memo: JsonObject      // SManga.kt:37, SChapter.kt:28
```

Extension code therefore carries **no null check**. Asura Scans' own code:

```kotlin
override fun getChapterUrl(chapter: SChapter): String {
    val randomSlug = chapter.memo["mangaSlug"]?.string
        ?: throw Exception("Refresh Chapter List")
```

That `?.` applies to the *lookup result*, not to `memo`. It compiles to a bare
`getMemo().get("mangaSlug")`, so a null memo threw
`NullPointerException: ... JsonObject.get(Object) on a null object reference`
before the author's own `?:` could run.

**The series side is where it actually bit.** Asura's `getMangaUrl`:

```kotlin
val randomSlug = manga.memo["slug"]?.string
    ?: run { /* look up its stored slug map, or derive from manga.url */ }
```

`restoreSeries` builds an `SManga` from a stored url and has no memo to offer.
That fallback exists for exactly this case and would have handled it. It threw
first. So `listChapters` NPE'd on every library open, `chaptersWithFallback`
silently served the cache, and every chapter on screen carried a rebuilt handle —
which then failed to read *and* to download. Browse worked throughout, because
the catalogue parse sets the memo itself.

**The general form, and it is the reusable part: a vendored API that *relaxes* a
type is a contract change to everything compiled against it.** Widening to
nullable cost nothing locally, compiled clean, and silently converted every
guarded fallback on the other side of the boundary into dead code. Nothing
reports this — not the compiler, not the loader, not `LIB_VERSION_MAX`. It
surfaces as a platform NPE from inside the extension, which reads as the
extension's bug.

`memo` now defaults to `JsonObject(emptyMap())` on both impls. **Binary
compatible**: the JVM signature is `getMemo()Lkotlinx/serialization/json/JsonObject;`
either way, since Kotlin nullability is metadata, not shape. No app code reads
the field, so the change is contained to `:source-api`.

---

## 2. The releases

| | What | Commit | State |
|---|---|---|---|
| 0.84 | `DownloadService.genuineChapter` — re-list a series when the rebuilt chapter handle isn't enough | `4264dea` | Correct, and did nothing on its own |
| 0.85 | `memo` non-null on both interfaces and both impls | `21628af` | **Verified** |
| 0.86 | Stop scoped to one series; Sources scroll; Popular chip | `0db1971` | 3 of 4 checks passed |
| 0.87 | `lastUsedId` out of the Sources scroll signature | `62f412d` | **Verified** |

**0.84 was necessary but not sufficient, and it shipped in the wrong order.**
`genuineChapter` is still the mechanism that fixes downloads — the queue stores
ids and rebuilds the `SChapter`, and no rebuild can ever produce
`memo["mangaSlug"]`, so re-listing the series is the only way to get a real one.
But its first move is `restoreSeries` + `listChapters`, which was itself NPE'ing.
It swallowed that, found nothing better, and rethrew the original error
unchanged — which looked exactly like the fix having done nothing.

It costs one extra request per series, cached per drain including failed
lookups, and only fires when the failure happened at or before `getPageList`
(`blamesTheHandle`). A `ChapterDownloadException` carrying a page count means the
extension accepted the chapter and the images failed, which is a network problem
— re-running the whole fetch for that is the manhwatoon mistake in §5.

---

## 3. 0.86's three board fixes

- **Stop cancelled the entire queue.** `downloadingAll` was
  `DownloadQueue.items.isNotEmpty()`, so *every* series screen read "Stop"
  whenever anything anywhere was downloading, and tapping it ran `clear()` +
  `ACTION_CANCEL_ALL`. Now `DownloadQueue.hasSeries(id)` for the label and
  `removeSeries(id)` for the action; it returns the removed chapter ids so the
  in-flight chapter gets an explicit `ACTION_SKIP`, which the service re-checks
  against `activeId` so a chapter that finished in the gap isn't cancelled by
  mistake. The old global `cancelDownloads()` is **deleted** — it was dead once
  the queue screen's Cancel all went straight to the service, and a
  queue-wiping helper left lying around is a trap.
- **Roku Hentai had no Popular chip.** It declares `supportsLatest = false` and a
  non-empty `getFilterList()`. The Popular chip was nested inside
  `if (supportsLatest)`, so the row rendered with Filter alone and filtering was
  a one-way trip. Popular now shows whenever the row shows at all.
- **The Sources list lost its scroll.** Fifth instance of the §5 hoisting bug.

---

## 4. The scroll signature, and the rule that should have existed

**0.86 shipped the Sources scroll fix and it did nothing at all.** The signature
handed to `ScrollMemory.sync` included `lastUsedId` — and `openSource()` calls
`SourcePrefs.setLastUsed()`. So the signature changed on exactly the trip the
store exists to survive, `sync` cleared every position, and the list opened at
the top every time. The store was working; the fix invalidated it itself.

§4 already records this precise trade for the library grid, where `counts` is
deliberately kept out of the signature because it moves on every finished
chapter. Same shape, same answer. What it costs is one row moving between the
Last used section and its language group — a stale anchor, not the wholesale
reorder a position genuinely can't survive.

**That is the third scroll signature got wrong in this project, and all three
were the same error.** Proposed rule, worth putting next to `ScrollMemory.sync`:

> **Nothing the act of leaving this screen can change belongs in its signature.**

Check the primary action of the screen first — what does opening a row *write*?
For Library it was read counts, for Sources it was last-used. Both are things
the screen displays *and* the departure mutates.

The remaining Sources signature is `(rows.size, pinnedIds, hiddenIds,
enabledLangs)` — all `Int` or `Set<String>`, all structural equality, none
touched by opening a source.

---

## 5. How to answer "what does this extension actually do" in three minutes

`SESSION_HANDOFF_0.83.md` §5 decompiles an APK's `classes.dex`. That tells you
*which* symbols an extension touches. **The source tells you why, and it is on an
allowed host.**

```bash
curl -sL https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json -o idx.json
# entry -> packageName gives the src path; language dir is the first segment
curl -sL "https://raw.githubusercontent.com/keiyoushi/extensions-source/main/src/en/asurascans/src/eu/kanade/tachiyomi/extension/en/asurascans/AsuraScans.kt"

# whole tree, 67 MB, if you want to grep across all 1367
curl -sL "https://codeload.github.com/keiyoushi/extensions-source/tar.gz/refs/heads/main" -o src.tgz

# and the API stub extensions are compiled against — this is the authority on
# what a declaration is supposed to look like. Version comes from
# gradle/libs.versions.toml: tachiyomi-lib-v16
curl -sL "https://codeload.github.com/keiyoushi/extensions-lib/tar.gz/6e0c96cea8" -o lib.tgz
```

**Diffing `:source-api`'s declarations against `extensions-lib` is the check that
would have caught this**, and it takes minutes. Doing it found:

- `memo` nullable vs non-null on both `SManga` and `SChapter` — the bug.
- **`SMangaUpdate(manga: SManga?, chapters: List<SChapter>?)` vs upstream's
  non-null pair.** Currently harmless, because extensions *construct* it and we
  only consume it, so we handle nulls that never arrive. It would bite the moment
  we hand one back into extension code. Worth a comment; not worth a change.
- `Page` is fine — ours is a superset with the identical constructor.

### Scan results, 2026-07-31, all 1367 extensions

- **19 read `memo[...]`** — `all/kagane`, `all/onisaga`, `en/allanime`,
  `en/asurascans`, `en/batcave`, `en/mangalix`, `ja/bookwalkerjp`,
  `ja/pixivcomic`, `pt/kivaratoons`, `pt/mangalivreorg`, `pt/onereader`,
  `uk/honeymanga`, `vi/ariverse`, `vi/seikowo`, `vi/truyentvn`, `vi/yurineko`,
  `zh/hcomic`, `zh/jcomic`, `zh/noyacg`. Every one indexes `memo` directly, so
  every one would have NPE'd. These are the sources 0.85 unblocks.
- **120 extend `KeiSource`**, Keiyoushi's own base class, bundled per APK. It
  contains:

  ```kotlin
  check(updatesInFlight.putIfAbsent(manga.url, true) == null) {
      "getMangaUpdate must not be called concurrently for same manga"
  }
  ```

  **So the concurrent-update refusal 0.81's per-series mutex works around is not
  an Elite Babes quirk — it is enforced identically across ~9% of the
  catalogue.** That upgrades `SESSION_HANDOFF_0.83.md` §9 item 2 from a tidy-up
  to the shape 120 extensions are built for.

  Loose end: the source tree says **Elite Babes is not a `KeiSource`**, so 0.81's
  attribution of that refusal to it specifically is probably wrong even though
  the mutex was right. Unresolved, low value.

---

## 6. The board, as of 16:26

Four bugs. Two are new this session and **they are almost certainly the same
bug**, which is the most useful thing in this section.

1. **Some library covers show HTTP 404; the cover loads when the series is
   opened.** The card's screenshot is `Ui.kt`'s debug overlay (§6 of
   `PROJECT_HANDOFF.md`) doing its job — the failing entry is *Pick Me Up,
   Infinite Gacha* and the URL tail is
   `…ges/covers/pick-me-up-infinite-gacha-8c5d12.webp`. **The trailing
   `-8c5d12` looks like a content hash**, which is the detail that makes the
   staleness reading below more than a guess: a source that re-encodes a cover
   gets a new hash, the old path 404s, and the stored string still points at it.
2. **Elite Babes 1.6.3 waiting for a newer version.** Still 1.6.3 in the
   Keiyoushi index as of today. Upstream; nothing to do here.
3. **Resume/scroll should load the current page and forward.** See §7.
4. **SpyFakku covers in the library stay grey until opened.**

### Why 1 and 4 are one bug — read this before starting either

`LibraryEntry` stores a cover string at add time. `Library.healCover` replaces it
on the first open, and **fires only when the stored cover is blank or loopback**
(§4, "Loopback covers"). That condition is too narrow:

- **SpyFakku (bug 4)** imported with its loopback covers blanked, so `healCover`
  fires — but only per series, on open. "Grey until opened" is the documented
  behaviour working exactly as written, one series at a time, across a 3571-entry
  library.
- **The 404s (bug 1)** are a stored cover that is neither blank nor loopback, but
  *stale* — the source moved or re-hashed the file. `healCover` will never
  replace it, because it only asks "is this absent or obviously wrong", never "did
  this work".

So both are: **the library grid renders a stored cover and nothing bulk-repairs
it.** The series screen looks right because it renders the freshly fetched
`thumbnail_url`.

The obvious home for a fix is the library refresh sweep — it already visits every
series and already holds the fetched `SManga`, so writing a corrected cover
alongside the counts is close to free, and it is the only thing in the app that
runs over the whole library. Watch the write cost: `Library` is one JSON string
and `healCover` per series would be quadratic, so this wants `mergeAll`, the same
way `recordAll` answered it for `SeriesIndex` (§7 item 1). §7 item 3's "a
re-import is needed to fix covers" stops being true once that exists.

**Unverified.** This is read off the code, not reproduced. Confirm by checking
whether a 404'ing entry's stored cover differs from what the series screen
fetches.

---

## 7. What's next

1. **The reader.** Still the largest untested surface, now thirteen sessions
   untouched, and board item 3 lands directly in it. Doing that item is a way
   into the reader with a user-visible payoff attached.
2. **Board item 3, specifically.** `loadPagesProgressively` fetches in source
   order and the adapter is never told where the reader will open — that is a
   deliberate, documented choice (§4, "Reading, downloads, and offline") that
   this item asks to reverse. It wants a `startAt` parameter defaulting to 0 so
   the download path is unaffected, and pages fetched from the resume position
   forward before wrapping. **Held back deliberately this session**: it changes
   page fetch ordering on the reader path for every source, and §5 says a change
   with that blast radius must not ride along with fixes for something else.
3. **Covers (§6).** Two board items for one fix, and it wants the sweep.
4. **One `getMangaUpdate` call instead of two.** Now known to be what 120
   extensions are shaped for, not one.
5. **`ExtensionsScreen` scroll.** Same hoisting bug as Sources, deliberately not
   fixed in 0.86: its list is fetched async and empty on first composition, so a
   seeded `LazyListState` clamps to 0 before the data lands and restores nothing.
   It needs the state built after the first non-empty list, not another
   parameter. Passing scroll into it would look fixed and do nothing.
6. **The refresh backlog** — bug 3 (counter race) and bug 6 (`done` doesn't
   reconcile), 547 unaccounted. Unchanged, still bottom.

The "To add" column is 19 feature requests and was not touched. §8 records the
eleven that are legible in the board screenshots, including the reference UI on
their attachments; seven of them are one pass over `SeriesScreen`.

---

## 8. The "To add" column — 19 items, 11 legible

Recorded here because the cards carry **reference screenshots of Mihon's UI**
that are not written down anywhere else, and a screenshot on a Trello card is a
poor place for the only copy. Nothing here was worked on. The column is scrolled,
so **8 of the 19 are not visible and are not recorded** — read them off the board
before planning against this list.

**Seven of the eleven are the series screen**, which is the cluster worth
noticing: taken together they are one pass over `SeriesScreen`, not seven tickets.

| # | Item | What the attachment shows |
|---|---|---|
| 1 | Add download options | A menu: **Next chapter / Next 5 / Next 10 / Next 25 / Unread** |
| 2 | Tapping the series name does a global search | — |
| 3 | Tapping the artist name does a global search | — |
| 4 | Add filter, sort and display options | Three tabs; Filter holds **Downloaded / Unread / Bookmarked / Scanlator** |
| 5 | An Options icon | A menu: **Refresh / Edit categories / Migrate / Share / Merge / Edit info** |
| 6 | Add download, filter and options icons to the top bar | The three icons in the top bar, right-aligned |
| 7 | Make the top bar move while scrolling, and add a scroll handle | A long chapter list with a drag handle on the right edge |

And four elsewhere:

| # | Item | Note |
|---|---|---|
| 8 | Add a feed tab in Browse | This is §7 item 17 / the Feed tab. Blocked on the same thing it has always been blocked on: the sweep overwrites counts rather than recording what changed |
| 9 | Add a setting to show/hide +18 content | `LoadResult.isNsfw` and `Source.isNsfw` already exist and drive the badge; this is a filter over them, next to `SourcePrefs`' visibility stores |
| 10 | Change the download icon | This is §7 item 22, icon debt: `KeyboardArrowDown` is standing in because `material-icons-core` has ~40 icons and no download glyph. One of three such stand-ins, and `material-icons-extended` fixes all three at once for roughly a megabyte |
| 11 | Swipe on a chapter to mark as read/unread | Multi-select already does this; this is the gesture |

**Two of these need a data model decision before any UI, and §4 already says so.**
*Bookmarked* in item 4's filter has **no backing field anywhere in the app** — it
is listed in §4 alongside Lewd, Language and Group→Status as blocked on a model
decision, not on an index. *Migrate* in item 5's menu is a whole feature (§7 item
17, "No Feed / Migrate tabs"), not a menu row. *Scanlator* is fine — `Chapter`
carries it already.

Worth doing 1–7 as one piece of work rather than picking one: they share a top
bar, a menu host and a bottom sheet, and the sheet is the same shape as
`LibraryOptionsSheet`, which already exists and can be read for the pattern.

---

## 9. State of the tree

Head is `62f412d` (0.87).

Build environment unchanged from 0.83: Kotlin 2.2.21, AGP 8.5.2, Gradle 8.9,
JDK 17, compileSdk 36, targetSdk 34, minSdk 24, OkHttp 5.4.0,
kotlinx-serialization 1.9.0, Compose BOM 2024.09.03, Coil 2.7.0.

**Verified on device this session:** Asura Scans reads and downloads from both
Library and Browse; Elite Babes and SpyFakku still work after the `:source-api`
change; Stop on one series leaves other queued series running; a third series
screen reads "Download all" while another downloads; Roku Hentai shows Popular;
the Sources list holds its scroll across opening a source, and still resets on
pin.

**Not verified:** 0.84 in isolation — it was inert until 0.85 landed under it.
