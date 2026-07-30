# Session handoff — 0.72, the slow open is closed

Written 2026-07-30, late evening, straight after the session that wrote
`SESSION_HANDOFF_0.71_RESULT.md`. **0.72 is written, built, installed and
verified on device.** Six checks, all passed. The oldest and most-felt item on
the bug board is closed.

Companion to `SESSION_HANDOFF_0.71_RESULT.md`, which diagnosed it. That file is
now a closed record; everything it left as a plan is either done or explicitly
deferred below.

---

## 0. Read this before touching anything

**1. The Downloaded badge is back ON.** It was turned off as the diagnostic in
0.71_RESULT §2 and left off overnight. It was turned back on as check 1 of this
session and the device is now on defaults again. The deviation warning at the
top of the previous handoff is retired — delete it from your mental model.

**2. The library refresh has been deprioritised.** Not abandoned, not broken —
deliberately moved down the list by the user. §5 is the new ordering and §5.1 is
the one consequence that isn't obvious. Do not open the sweep because the
backlog looks like it points there; it doesn't any more.

**3. One number was not captured**, and it is the only gap in an otherwise clean
session. See §3. It costs one dialog to fix and nobody has to redo any work for
it.

---

## 1. What shipped

`dd2e00e` — "Stop asking a Downloads-tab question from the library screen; show
the icon at startup". 13 files, 350 insertions, 25 deletions. `versionCode`
71 → 72, `versionName` "0.72", `WhatsNew` entry.

Built on the first try, which is worth recording: it is the first release in
this project to add `res/` files, and the first to touch `AndroidManifest.xml`
since 0.67's `dbbe38a` broke a build by doing so.

### `DownloadIndex.seriesIds()` — the fix

`LibraryScreens.kt:137` used to be
`DownloadIndex.list(context).map { it.seriesId }.toSet()`. `list()` is the
Downloads tab's question: it sizes every downloaded chapter, sorts
size-descending, and — through `needsRecovery` — reads `ChapterCache` for every
entry in the library. All of it on the composition thread, before the first
frame, to answer a question about ids.

`seriesIds()` is the same answer with the Downloads tab's work removed. It
**keeps the `isComplete` filter**, which is load-bearing and not an
optimisation: `Downloads.delete` → `forget()` calls `DownloadIndex.invalidate()`
but never prunes the record from `downloads_index.json`, so a records-only read
would leave a badge on a series whose downloads you had just deleted. Only
`deleteSeries` prunes, and only `DownloadQueueScreen` calls it. Check 5 in §2
exists to test exactly this.

### The rest

- **Two marks.** `Downloaded ids` at the call site, and `Prefs load (onCreate)`
  around `SourceManager.migrateLegacy` — the true first prefs touch in the
  process, which is what 0.71_RESULT §4 item 1 said the report could not see.
  No `arrange()` mark: `once()` records only the first call and `arrange` runs
  per group, so it would have measured one tab and read like the whole screen.
- **The report's blind spot**, 0.71_RESULT §4 item 2. It now prints total entry
  count and a string/non-string split. The five big blobs are ~1.42 MB of a
  2.5 MB file; the rest is XML escaping and several thousand `read:<chapterKey>`
  booleans that rank at zero in a list sorted by string length.
- **The sweep summary persists**, as one JSON key rather than eight — entry
  count is what makes a `SharedPreferences` file slow to load, so eight keys to
  store six numbers would have been paying in the scarce currency. `resumed` is
  now in the finished row, so `counted + failed + skipped == total − resumed` is
  self-contained instead of inviting the circular reconstruction that
  `SESSION_HANDOFF_0.69.md` §2 warns about.
- **A per-source failure tally**, `ConcurrentHashMap<String, AtomicInteger>`,
  shown under the summary. **This does not fix bug 3.** `failed++` still races.
  It means the *tally* doesn't — so if the two disagree at the end of a sweep,
  the gap is a free measurement of bug 3. See §5.1: this is now built and
  unused.
- **A startup icon.** `Theme.Yomu.Splash` on the launcher activity, swapped for
  `Theme.Yomu` by `setTheme()` in `onCreate` before `super`. Pre-31 gets a
  layer-list window background; 31+ gets the system splash pointed at the same
  colour and mark. This is the project's first `res/values/themes.xml` — see §6
  for what that does and does not do to the manifest-theme item.

---

## 2. Verified on device — six checks, all passed

1. **Downloaded badge turned back on.** Default state restored.
2. **Cold start is 3 seconds**, with badges on, on the second launch after
   install. Was about thirty.
3. **Startup timings renders** with five marks and the new `Entries:` line.
4. **Badges appear** on the library grid.
5. **The delete trap holds.** Downloads deleted from a series screen, back to
   the library, badge gone. The `isComplete` filter is doing its job.
6. **The icon shows** during startup instead of a blank rectangle.

### What check 2 establishes, precisely

Badge **off** on 0.71 was 3 s. Badge **on** on 0.72 is 3 s. The two are the same
number, which means **`seriesIds` costs nothing perceptible at this download
volume** — the fix is not merely better than `list()`, it is free.

That settles two open questions from 0.71_RESULT §3 outright:

- **The unmeasured tail is small.** The last mark lands at +1764 ms and the felt
  total is ~3 s, so `arrange()` over 3571 entries plus the first grid layout is
  about a second. It was a live suspect; it is not one now.
- **§5 item 3 is unnecessary.** Moving the badge lookup to `produceState` on IO
  would hide a cost that is no longer there, at the price of badges arriving
  late. **Do not write it.** It is struck from the plan rather than deferred.

### What check 4 establishes, and it is more than it looks

`seriesIds` drops the recovery scan, so downloads made before the index existed
are invisible to it until the Downloads tab is opened once in a given process.
That was the one behaviour change in the release and the one thing that could
have made this a bad trade. **Badges appeared without opening the Downloads
tab**, which means this device has nothing that only the scan could find. The
concern is real in principle and empty in practice here.

### The arithmetic that doesn't quite close, recorded so it isn't quoted later

19.9 s was measured. ~30 s was the complaint. Removing the 19.9 left 3 s, not
10. So **"about thirty seconds" was always an eyeball figure**, and the true
cold start before this release was probably ~23 s. Do not let 30 s be repeated
as a measurement; only the 19.9 and the 3 are measurements.

---

## 3. The one thing this session failed to capture

**Neither new mark's value was read off the report.** Check 3 confirms the
screen renders and the marks are present; nobody wrote down what
`Downloaded ids` or `Prefs load (onCreate)` actually say.

Why it matters, in order:

- **`Downloaded ids`** is the fix measuring itself. §2 establishes it is
  imperceptible, which is the answer that matters, but not *why*. If it is
  ~50 ms the per-chapter `isComplete` stats are cheap at this download volume;
  if it is ~800 ms they are not, and the next person to download a few thousand
  chapters will find this regressing. One number tells you which.
- **`Prefs load (onCreate)`** is the first honest look at what loading the
  2.5 MB XML costs. It has never been measured — 0.71 reported `0 ms` for it,
  and 0.71_RESULT §4 item 1 explains that the 0 meant "already paid", not
  "free". It is bounded above by the ~1.4 s prefix and that is all anyone knows.
  If it turns out to be most of that prefix, splitting the big blobs out of
  `manga_reader` becomes a real piece of work rather than a hypothesis.

**Fix: open Settings › Advanced › Startup timings and write down five numbers.**
No force-stop needed if the app has been opened since boot — the marks are
process-lifetime and still there. If it has been killed since, force-stop and
open once first.

---

## 4. The bug board

| Item | State |
|---|---|
| Roku Hentai has no covers | **Closed** — 0.70, verified on device |
| Extensions tab reloads every time | **Closed** — 0.70, verified on device |
| The app takes some time to open | **Closed** — 0.72, verified on device at 3 s |
| Elite Babes chapters have no pages | Untouched, not investigated |

**One item left, and it has never been looked at.** Four sessions described the
slow open before anyone measured it; Elite Babes has not had even that. It is
the whole board now.

Still open from the 0.67 review, on the seven-item numbering in
`SESSION_HANDOFF_0.68.md` §5: **bug 3** (counter race) and **bug 6** (`done`
doesn't reconcile). Sweep 2 is short by **547**, split between them in unknown
proportion. Both are now low priority — §5.

Still open, older: the manifest theme (§6), and open thread 1, the reader, now
**ten sessions** untouched.

---

## 5. What comes next, reordered

**The library refresh is deprioritised at the user's instruction.** The
justification is better than "because it was asked for", and it is worth
writing down so the decision survives contact with the next backlog review:

- **It has already delivered.** Two full sweeps ran on the night of 2026-07-29/30
  and the index now answers for roughly 3024 of 3571 series. The badges,
  filters and sorts that four backlog items were waiting on all work. What is
  left on the refresh is *accounting* — knowing precisely which 547 are missing
  and why — not capability.
- **Five consecutive releases went into it.** 0.67 built it, 0.68 made it
  resumable, 0.69 serialised its writes, 0.70 and 0.71 were written around its
  measurements. Nothing else in the app has had a session in that time.

New order:

1. **Elite Babes chapters have no pages.** The only open board item, never
   investigated, and something the user hits directly. Same family as the covers
   bug and bug 6 — an extension returning data the app can't use — so start by
   reading what the source actually returns rather than by guessing which layer
   is wrong. `CoverImage`'s debug overlay is the precedent: the lesson from 0.70
   §1 is that a symptom match is not an observation.
2. **Open thread 1 — the reader.** Ten sessions untouched and still the largest
   untested surface in the app: paged right-to-left, grayscale and invert
   together, the chapter picker, whether settings survive reopening a chapter.
   Nothing here is a known bug; that is the problem.
3. **The manifest theme's remaining half.** See §6 — genuinely cheaper now, and
   still not one line.
4. **The refresh backlog.** Bugs 3 and 6, sweep 3, the 547. Unchanged in
   content, moved to the bottom.

**The Feed / Updates tab comes down with it**, and this is worth being explicit
about because it was the named next piece of work for three handoffs. It needs a
*diff* of what a sweep changed, so it is built on top of the refresh and cannot
be more important than its foundation. It stays the largest gap between what the
app knows and what it can tell you; it is no longer the next thing.

### 5.1 One consequence of deprioritising, stated so it isn't a surprise

`SESSION_HANDOFF_0.70.md` §5 laid out a sequencing argument: fix bug 6, run
sweep 3, read the failure list, then work the board against real data. That
route is how Elite Babes would have been *found* rather than investigated by
hand — a sweep that counted empty chapter lists as failures would have named
every broken source across all 3571 entries in one pass.

Deprioritising the refresh closes that route. Item 1 above is therefore a manual
investigation of one source, not a data-driven pass over all of them. That is
the right trade for one source and the wrong one for ten, so **if a second or
third source turns out to be broken, this decision should be revisited** — at
that point bug 6 plus a sweep is cheaper than investigating each by hand.

Note also that **the failure tally shipped in 0.72 and is currently unused.**
It is the missing third step of that argument (`SESSION_HANDOFF_0.71.md` §5),
built and sitting there. It costs nothing to leave; it will be waiting if the
priority comes back.

---

## 6. The manifest theme — cheaper now, still not one line

`res/values/themes.xml` exists for the first time, because the splash needed
somewhere to hang a window background. Four handoffs have called this item "one
line"; `SESSION_HANDOFF_0.69.md` §4 established that it isn't, and creating this
file does **not** change that.

What is unchanged: light/dark is an in-app preference (`AppPrefs.ThemeMode`,
defaulting to `DARK`), not the system setting. `Theme.Material.Light` breaks the
default case; `DayNight` follows the system and is wrong for anyone who chose
against it; a `values-night` variant has the same limit, because resources can
only see the system setting. `Theme.Yomu`'s parent is the dark variant, which is
what the default already produced, so 0.72 changed nothing for anyone.

What is cheaper: the *shape* of the fix. Adding `values-night/themes.xml` is now
one file rather than a file plus a manifest restructure, and the status-bar half
(runtime, via the insets controller, from the answer `AppTheme.load` already has
in `onCreate`) is unaffected and still the smaller half.

---

## 7. State of the tree

- `1595fe4` — 0.71, the startup diagnostic. Verified.
- `dac6c4b` — docs. Two docs commits landed between 0.71 and this session; they
  are why the base was not `1595fe4`.
- `dd2e00e` — **0.72**, 13 files. **Built, installed, six checks passed.**

Library is **3571** entries. Sweep 2 is short by **547**.

**Doc debt: cleared.** `PROJECT_HANDOFF.md` has been brought up to date in the
same commit as this file — header, §0 lead, a Closed entry for 0.71 and 0.72,
the reprioritisation, and a note in §1 about heredocs breaking an `&&` chain.
The two outstanding items named in `SESSION_HANDOFF_0.71_RESULT.md` §7 — 0.70's
device verification and the 0.71_RESULT diagnosis — are both folded in.

**Device state: defaults.** No deliberate deviations outstanding.

---

## 8. Two lessons from this arc, both cheap and both about method

**1. One toggle beat four sessions of reading.** The slow open was described in
0.68, 0.69, 0.70 and 0.71 without being measured. What closed it was the
*offset* field on the marks — which turned "142 ms measured, 30 s felt" into a
bounded 19.9-second interval between two known lines of one file — followed by
flipping one boolean on the device. No trace, no PC, no build. **Keep offsets on
every mark added from here**, and reach for a setting that turns a suspect off
before reaching for a release that measures it.

**2. A heredoc ends an `&&` chain.** 0.72's push needed resource files, which
the `*.kt` copy loop cannot reach, so the command grew four
`cat > file <<'EOF'` blocks. After the first `EOF` the shell starts a fresh
command list, so everything after it — including `git commit && git push` — runs
regardless of whether the earlier steps succeeded. The delivered command was
split into three pastes for this reason. **Resource files will come up again;
put the heredocs in their own paste and keep the `&&` chain for the steps that
must not half-apply.** Now also recorded in `PROJECT_HANDOFF.md` §1.

---

## 9. If you only do one thing

Open Settings › Advanced › Startup timings and write down the five numbers (§3).
It is one tap, no force-stop, and it is the only measurement this otherwise
complete session left on the table.
