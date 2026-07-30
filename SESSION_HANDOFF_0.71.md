# Session handoff — 0.71, measurement instead of argument

Written 2026-07-30, evening, straight after the daytime session that wrote 0.70.
Short session and almost all of it was device work: 0.70 was installed and both
its claims tested, then 0.71 was written to settle the one board item that four
sessions have described and none have measured.

Companion to `SESSION_HANDOFF_0.70.md`, which is now a closed record — everything
it left hanging is answered below.

---

## 0. Read this before touching anything

**1. Confirm 0.71 is on `main`.**

```
git log --oneline -3
```

0.71 was committed as `1595fe4` and **pushed to a branch called
`startup-timings` by mistake**, from a stale `git checkout -b` earlier in the
session. CI is `on: push: branches: [main]`, so that push built nothing. It was
then merged to `main` and the branch deleted. If `git log` on `main` doesn't show
`1595fe4`, the merge is what didn't happen — not the commit. There is nothing to
rewrite, just:

```
git checkout main && git merge startup-timings && git push
```

**2. 0.71 is uncompiled.** Written in a sandbox with no Maven, same as 0.70.
Suspect list is §4.

**3. 0.71 changes no behaviour.** It adds one diagnostic screen. If it builds and
the report shows numbers, it has done its whole job.

---

## 1. 0.70 is verified. Both its items close.

This is the first thing 0.70's handoff asked for and it is now done.

**Covers — fixed.** Debug build confirmed via Settings › Advanced › About
(`Version 0.70 (70) · debug`), Roku Hentai's browse grid opened, and every cover
renders. No red overlay text anywhere, which combined with visible images means
Coil succeeded rather than failed silently.

Worth being precise about what that does and doesn't establish. 0.70's §1 laid
out a table of three overlay readings and the grid matched none of them, because
all three assumed the boxes would still be grey. **The 403 was never observed.**
What was observed is that a fix aimed at exactly one mechanism made the symptom
disappear. That is confirmation by effect, and it is good evidence, but if
another source shows grey covers later, do not assume the same cause — read the
overlay then, which is what it is for. The changelog entry's claim stands and
needs no amendment.

**Extensions tab — fixed.** Leaving and returning now shows the list in about a
second rather than refetching. Cause was confirmed by reading before the fix was
written, and the behaviour now matches.

**Not tested:** the covers fix on any source other than Roku Hentai, and the
ten-minute cache boundary (i.e. that it *does* refetch after ten minutes, and
that a failing repo falls back to its last good index).

---

## 2. "The app takes some time to open" is now one suspect, not two

The board item is real and larger than the wording suggests: **about 30 seconds**
on a cold start, with a blank screen for the whole of it.

`SESSION_HANDOFF_0.70.md` §4 named two suspects. A screenshot of the stall
eliminates one of them. **Neither the bottom navigation bar nor the top app bar
is drawn** during the wait — the frame is not being produced at all. That is a
blocked main thread, not slow data arriving into a drawn shell.

Extension classloading cannot do that. `MainActivity.kt:313` already wraps
`SourceManager.listAllSources` in `withContext(Dispatchers.IO)`, so it delays
content but cannot freeze the frame. It stays on the list as a contributor to how
long the *content* takes, not to the blank window.

That leaves the prefs read, and the shape of it is worse than §4 described:

- **Twelve call sites share one `getSharedPreferences("manga_reader")` file** —
  library, `SeriesIndex`, categories, history, read state, download index,
  scroll memory, app prefs. Android loads and parses that entire XML on first
  access, and every getter blocks until it finishes. Whichever store is touched
  first pays for all of them.
- **`LibraryScreens.kt:99`** is `remember(tick) { Library.list(context) }` — a
  JSON parse of 3571 entries on the main thread, inside composition.

Two full parses of a large payload before the first frame. That fits 30 seconds
in a way that "some time" never did.

**It is still a hypothesis.** It was reached by reading, which is exactly what
the last four sessions did with this item. 0.71 exists so the next session
doesn't have to argue about it.

---

## 3. What 0.71 changes

Six files. `versionCode` 70 → 71, `versionName` "0.71", `WhatsNew` entry.

### `StartupTimings.kt` (new)

`once(name) { block }` times a named block **the first time that name is seen and
never again**, and records both how long it took and how long after process start
it began. First-call-only is the point: `Library.list` and `SeriesIndex.all` are
both memoised, so averaging in the later calls would hide the one that blocked
the frame. The offset matters as much as the duration — a six-second parse
starting at +200 ms and one starting at +9 s are different bugs.

`report(context)` renders the marks, then stats
`shared_prefs/manga_reader.xml` and lists its five largest keys by size. The app
can read its own data dir, so this needs no root, no adb and no PC — which is the
whole reason it is a screen rather than a systrace.

### The marks

- `Library.kt` and `SeriesIndex.kt` each wrap their prefs read under the **same**
  mark name, `Prefs first read`. Only the first is recorded, so **which of the
  two shows up is itself the answer to who paid for the file load.**
- Each also wraps its own parse: `Library parse`, `SeriesIndex parse`.

### `SettingsScreens.kt`

A `Startup timings` row under Diagnostics, beside Extension diagnostics, reusing
the same dialog. The dialog's title was hardcoded and is now a
`diagnosticsTitle` state variable, set by both callers.

### Reading the result

Force-stop first or you measure a warm process.

| Report shows | Means |
|---|---|
| `Prefs first read` dominates | The single shared XML is the cost. Split the two big blobs into plain files, the way `DownloadQueue` already stores its queue. |
| A parse dominates, prefs read small | The file loads fine; JSON parsing 3571 entries is the cost. Move it off the main thread behind a loading state, or store it so drawing one screen doesn't need a full parse. |
| Both small, total nowhere near 30 s | The cost is somewhere nobody has looked, and the marks were the cheap way to learn that. Next suspect is what the IO-dispatched classload is holding up. |
| `Nothing recorded yet` | Neither path ran before Settings was opened. Open Library first. |

The largest-keys list is worth reading whichever row lands — it says which blob
is actually big rather than which one was assumed to be.

---

## 4. Why this build might break

Uncompiled, so in order of likelihood:

- **The brace in `SeriesIndex.all()`.** One lambda was opened around the existing
  parse and closed after it, with the body left at its old indentation on
  purpose — a reindent would have buried a one-line change in a fifteen-line
  diff. Braces balance in the file as written, but this is the first thing to
  check on a mismatch error.
- **`once` is not `inline`.** Neither wrapped block contains a `return`, so this
  is fine today. It will stop being fine the moment someone wraps a third site
  that does: a non-local return out of a non-inline lambda doesn't compile.
- **`String.format` in `formatSize`** takes no locale. If a lint rule objects,
  it wants `Locale.ROOT` as the first argument.
- **`StringBuilder.appendPrefsSize`** is a private extension on a stdlib type
  declared inside an object. Legal, unusual enough here to be worth a glance.
- `Process.getStartElapsedRealtime()` is API 24 and `minSdk` is 24, so it needs
  no version guard.

---

## 5. A gap in the sequencing argument from 0.70 §5

0.70's §5 argues: fix bug 6, run sweep 3, **read the failure list**, then work
the board against real data. The first two steps are right and the third does not
exist.

`LibraryRefresh.noteFailure` increments `failed` and keeps **only**
`firstError` — deliberately, and the KDoc's reasoning about first-vs-last is
sound for a single error string. But there is no per-source tally and no list
anywhere in the tree; `SettingsScreens.kt:321` prints that one string. So bug 6's
fix on its own converts 547 unaccounted series into a *count* of failures, not
into the names of the sources producing them, and "is Elite Babes in the list"
stays unanswerable.

**Add a `Map<String, Int>` of sourceId → failures, incremented in
`noteFailure`,** in the same commit as `SESSION_HANDOFF_0.69.md` §7 item 1
(surface `resumed`, persist the five counters). Both are the same work —
getting sweep state out of Compose state and into prefs — and without the tally
sweep 3 costs a full 3571-entry pass and returns one error string.

---

## 6. The bug board, as it stands

| Item | State |
|---|---|
| Roku Hentai has no covers | **Closed** — 0.70, verified on device (§1) |
| Extensions tab reloads every time | **Closed** — 0.70, verified on device (§1) |
| Elite Babes chapters have no pages | Untouched, not investigated |
| The app takes some time to open | Narrowed to one suspect (§2); 0.71 measures it |

Still open from the 0.67 review, on the seven-item numbering in
`SESSION_HANDOFF_0.68.md` §5: **bug 3** (counter race) and **bug 6** (`done`
doesn't reconcile). Sweep 2's arithmetic is short by **547**, split between them
in unknown proportion.

Still open, older: the manifest theme (`SESSION_HANDOFF_0.69.md` §4), and open
thread 1, the reader, now eight sessions untouched.

---

## 7. State of the tree

- `76bd284` — 0.69, index write serialisation and the summary dismiss. Verified.
- `44f6f00` — docs: the 547 shortfall recorded.
- `0eb8e7d` — docs: the 0.70 handoff.
- `1595fe4` — **0.71**, six files, the startup diagnostic. Uncompiled. Pushed to
  a branch first (§0), then merged to `main`.

0.70's code is verified on device; **its own device-verification is not yet
written into `PROJECT_HANDOFF.md`** — §0 and §8 there still describe 0.70 as
unrun. Fixing that is a docs commit and is the cheapest thing on this page.

Library is **3571** entries.

---

## 8. A note on how this session went, because it cost real time

`PROJECT_HANDOFF.md` was not read until the end. Two deliverables had to be
thrown away as a result: a `.patch` file, when §1 says plainly that complete
files to drop in are preferred and gives the exact `cp` loop they go through;
and a feature branch, when the workflow pushes straight to `main` and CI only
builds `main` — which is why 0.71's first push built nothing.

Both were recoverable in a minute. The point is that neither needed to happen:
§1 "How the user works" is four paragraphs and answers the question of what a
finished piece of work looks like here. **Read it before writing the first
file, not after the second one is wrong.**

---

## 9. If you only do one thing

Force-stop Yomu, open it, wait out the blank screen, then Settings › Advanced ›
**Startup timings**. Read the four numbers and the file size. Every plan for the
slowest-felt problem in the app currently rests on a hypothesis reached by
reading, and that screen replaces it with a measurement in about ninety seconds.
