# Session handoff — 0.71 read, and the answer it gave

Written 2026-07-30, evening, immediately after the session that wrote 0.71.
**No code was written this session and nothing was pushed.** It is entirely
device work: 0.71 was installed, its report read twice, and the oldest and
most-felt item on the bug board was diagnosed by flipping one boolean.

Named for the release it *reads* rather than one it ships, because it ships
none. Companion to `SESSION_HANDOFF_0.71.md`, which asked for exactly this
measurement and is now answered.

---

## 0. Read this before touching anything

**1. The Downloaded badge is currently OFF on the device.** It was turned off as
the diagnostic in §2 and left off. `LibraryPrefs.badgeDownloaded` defaults to
`true`, so this is a deliberate deviation from every default and it is invisible
from the code. Either turn it back on and accept a twenty-second cold start
until 0.72 lands, or leave it off knowingly. Do not spend a session wondering
why library covers have no download badges.

**2. 0.71 built, merged, installed and works.** The report screen rendered on
device, which retires 0.71 §0's merge question — CI only builds `main`, so an
installed build proves `1595fe4` got there — and its entire §4 suspect list: the
brace in `SeriesIndex.all()`, `once` not being `inline`, `String.format`'s
missing locale, and the private `StringBuilder` extension. All fine.
`Process.getStartElapsedRealtime()` returns a sane value and the offsets are
meaningful, so the tool is reusable as-is for anything measured later.

**3. Nothing is fixed.** The diagnosis is complete; the fix is not written. §5 is
the plan.

---

## 1. The measurement

Force-stop, open, wait out the blank screen, Settings › Advanced › Startup
timings. Twice, ten minutes apart, with one setting changed between them.

| Mark | Run A — badge on | Run B — badge off |
|---|---|---|
| Prefs first read | 0 ms (at +1373) | 0 ms (at +1681) |
| Library parse | 62 ms (at +1373) | 62 ms (at +1681) |
| SeriesIndex parse | 80 ms (at +21360) | 64 ms (at +1764) |
| Measured total | 142 ms | 126 ms |

`manga_reader.xml` is 2.5 MB in both. Largest keys, unchanged:
`library_json` 764.6 KB, `series_index_json` 384.1 KB,
`category_assign_json` 252.9 KB, `history_json` 16.3 KB, `sources_json` 223 B.

**The number that matters is not printed.** It is the gap between one mark
finishing and the next beginning:

```
Run A:  21360 − (1373 + 62)  =  19925 ms
Run B:   1764 − (1681 + 62)  =     21 ms
```

Both marks sit in `LibraryTab` — `Library.list` at `LibraryScreens.kt:99`,
`SeriesIndex.all` at `:152` — in one composition pass on the main thread. The
interval is bounded by construction: nothing outside those fifty lines can be
inside it.

The +1373 → +1681 shift on the first mark is run-to-run noise. Don't read it.

---

## 2. The diagnosis

**`DownloadIndex.list(context)` at `LibraryScreens.kt:135` is 19.9 seconds of a
cold start.** One toggle, no build, no CI round trip.

Three costs, all cold on a cold start, all on external storage where every stat
crosses FUSE:

- **`Downloads.isComplete` per record.** The `completion` memo is a process-lifetime
  `ConcurrentHashMap` and is empty. Each miss runs `dirFor`, which stats the tree
  path and then probes every legacy root.
- **`Downloads.sizeOf` per record.** Same story with `sizes`; each miss is a full
  directory walk of a chapter folder.
- **`needsRecovery` → `ChapterCache.load` for all 3571 library entries**, plus an
  `isComplete` per chapter in each list. This is the scan `PROJECT_HANDOFF.md` §0
  records as fixed for the Downloads tab. It was never fixed; it was gated, and
  the gate leaks (§5 item 2).

It runs because `badgeDownloaded` defaults to `true` (`LibraryPrefs.kt:143`).
The comment at `LibraryScreens.kt:133` says the index is only asked for when
something on screen depends on it. That is true and it is not enough — the thing
that depends on it is on by default, for everybody, on every cold start.

### The sweep made this worse, and nothing would have connected them

0.67 step 6 writes a chapter list to `ChapterCache` for every series it counts.
Before the sweeps, the recovery scan read the handful of series that had ever
been opened. After sweeps 1 and 2 completed on the night of 2026-07-29/30 it
reads roughly 2900 files. Same code, an order of magnitude more work, caused by
a feature in a different subsystem two releases earlier.

This is a prediction, not a measurement: if the open felt noticeably worse after
that night, this is why. Worth a moment's memory before 0.72 is written, because
it changes how urgent item 2 in §5 is.

### Method note, worth keeping

0.71's report did not name the culprit. It measured two things and found both
cheap. What identified the culprit was the **offset** field, which turned
"142 ms measured, 30 s felt" into a bounded 19.9-second interval between two
known lines of one file — at which point reading fifty lines was enough.
Duration alone would have said only "it's elsewhere". Keep the offsets on every
mark added from here.

---

## 3. What this establishes, and what it does not

**Established:**

- The shared 2.5 MB prefs XML is not the problem. Its load is inside the
  unmeasured 1.4 s prefix and is therefore capped by it (§4 item 1).
- Neither JSON parse is the problem: 62 ms and 64–80 ms.
- `Categories.list`, sixteen `LibraryPrefs` getters and `Categories.seriesIn`
  over the 252.9 KB assignments blob are **≤ 21 ms combined** — that is Run B's
  whole gap. The category parse can be crossed off; it was a live suspect.
- `DownloadIndex.list` is 19.9 s of it.

**Not established, and the first one is important:**

- **Whether the app now opens fast.** The marks stop at +1764 ms; the complaint
  was ~30 s. Nobody has reported the felt time with the badge off. If it is two
  or three seconds, the board item closes outright. If it is still fifteen, then
  `DownloadIndex` was two-thirds of it and the rest is *after* line 152 —
  `arrange()`'s filter, sort and group over 3571 entries, and the first grid
  layout. Neither is marked. **One force-stop answers this.**
- Which of the three costs inside `DownloadIndex.list` dominates. Not needed to
  fix it; needed to know how much of §5 is actually required.

---

## 4. Two flaws in the 0.71 report itself

Both found by reading the numbers against the code. Fix them in the same commit
as any new mark.

**1. `Prefs first read: 0 ms` cannot mean what §3 of the 0.71 handoff says it
means.** `MainActivity.onCreate` calls `SourceManager.migrateLegacy`
(`SourceManager.kt:62` → `manga_reader`) and `AppTheme.load` before
`setContent`, so the XML is loaded and parsed before either marked site runs.
The mark records the cost having *already been paid*, not it being free. Row 1
of that table — "Prefs first read dominates" — can never fire as written. It
needs a mark in `onCreate` around the first prefs touch.

What the 0 does still buy is a bound: the load happened somewhere inside the
1.4 s before the first mark, alongside `Application.onCreate`, `Injekt`,
`DownloadQueue.restore` and the whole first composition down to line 99. That is
enough to clear it as the suspect, which is what mattered.

**2. The largest-keys list ranks String values only** —
`(it.value as? String)?.length ?: 0`. `ReadState` stores per-chapter flags as
individual booleans (`read:<chapterKey>`), so several thousand entries are
ranked at zero and are invisible. The five strings sum to ~1.42 MB against a
2.5 MB file; XML escaping and those booleans are the remainder.

This matters because the obvious action on that list is "split the big blobs out
into plain files", and doing only that would leave the thing that actually makes
a `SharedPreferences` file slow to load: **entry count, not byte size**. Add a
total key count and a non-String count to the report.

---

## 5. 0.72, in the order the work should go

**1. Stop asking a Downloads-tab question from the library screen.**
`LibraryScreens.kt:137` is `DownloadIndex.list(context).map { it.seriesId }.toSet()`.
It wants a set of ids and pays for a size walk per chapter and a size-descending
sort to get it. A `DownloadIndex.seriesIds(context)` that keeps the
`isComplete` filter and drops sizing and recovery is most of the win at the
least risk.

**Keep `isComplete`, and this is the trap.** `Downloads.delete` → `forget()` →
`DownloadIndex.invalidate()` drops the memo but **never prunes the record** from
`downloads_index.json`. `MainActivity.kt:977` and `:985` — the series-screen
delete paths — are exactly that call. Only `DownloadIndex.deleteSeries` prunes,
and only `DownloadQueueScreen.kt:153` calls it. So a `seriesIds()` that reads
records without checking disk would leave a download badge on a series whose
downloads you just deleted. The self-healing filter is load-bearing for an
in-app path, not just for out-of-band deletes.

**2. `needsRecovery` is a leaky gate over a one-time migration.**
`DownloadPaths.knownChapterIds(context).any { it !in known }` is true whenever a
chapter was ever assigned a path and is not a currently-complete download — one
cancelled download does it, permanently. The second branch is true if any
directory is left in a legacy flat root. Behind that gate is an O(library) scan.

What it recovers is downloads made before the index existed. That is a
migration, not a routine check: run it once, stamp it done in prefs, stop
re-deciding it on every cold cache. Item 1 alone removes it from the library
path; this removes it from the Downloads tab too.

**3. Then move what is left off the composition thread.** `produceState` on IO,
badge simply absent until it arrives. **Do this last.** Done first it hides the
cost rather than removing it, and the badges would still take twenty seconds to
show up.

**Carry in the same commit**, both already asked for and both cheap:

- Surface `resumed` in the finished summary row, and persist the five counters
  (`SESSION_HANDOFF_0.69.md` §7 item 1).
- A `Map<String, Int>` of sourceId → failures, incremented in
  `LibraryRefresh.noteFailure` (`SESSION_HANDOFF_0.71.md` §5). Without it, sweep
  3 costs a full 3571-entry pass and returns one error string.

**And three new marks**, since the harness is proven and each is one line:
around `DownloadIndex.list` (proves the fix), around `arrange()` (§3's
unmeasured tail), and in `MainActivity.onCreate` around the first prefs touch
(§4 item 1).

---

## 6. The bug board

| Item | State |
|---|---|
| Roku Hentai has no covers | **Closed** — 0.70, verified on device |
| Extensions tab reloads every time | **Closed** — 0.70, verified on device |
| Elite Babes chapters have no pages | Untouched, not investigated |
| The app takes some time to open | **Diagnosed and measured** — `DownloadIndex.list`, 19.9 s. Fix unwritten. |

Still open from the 0.67 review, on the seven-item numbering in
`SESSION_HANDOFF_0.68.md` §5: **bug 3** (counter race) and **bug 6** (`done`
doesn't reconcile). Sweep 2 is short by **547**, split between them in unknown
proportion.

Still open, older: the manifest theme (`SESSION_HANDOFF_0.69.md` §4 — not a
one-line fix), and open thread 1, the reader, now **nine sessions** untouched.

---

## 7. State of the tree

**No commits this session.** `1595fe4` — 0.71, the startup diagnostic — is still
the head, and is now proven to have built, merged to `main` and installed.

Doc debt, both one commit:

- **0.70's device verification is still not in `PROJECT_HANDOFF.md`.** §0 and §8
  there describe 0.70 as unrun. `SESSION_HANDOFF_0.71.md` §7 called this the
  cheapest thing on the page and it is still outstanding.
- **This file's result is not in `PROJECT_HANDOFF.md` either**, and §0 there
  still describes the slow open as a hypothesis reached by reading. It isn't
  anymore.

Library is **3571** entries.

**Device state: the Downloaded badge is OFF** (§0 item 1).

---

## 8. If you only do one thing

Force-stop Yomu with the badge still off, open it, and count the seconds. That
one number decides whether §5 is the whole fix or two-thirds of it, and it is
the only input to 0.72 that nobody has.
