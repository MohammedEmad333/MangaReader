# Session handoff — 0.69, and the first completed sweep

Written overnight on 2026-07-30, **amended the same day after sweep 2 finished
and 0.69 was installed.** The first full library refresh **completed** — §1 is
the verdict and it is the good one. **0.69 is now installed and its three checks
pass** (§3). **Sweep 2's summary was captured, and the §2 arithmetic came up 547
short** — the first real measurement of bugs 3 and 6, and the headline of this
amendment.

Companion to `SESSION_HANDOFF_0.68.md`, which covers the release this one fixes.

---

## 1. The verdict — the completion branch passed

**Resolved before this file was pushed.** The first full sweep reached its own end
at some point before 01:00 on 2026-07-30. The Settings row afterwards read
"Refresh library" **with no "Start over" button beside it.**

That absence is the proof. `Start over` only renders when `unfinished` is true,
which is `RefreshCursor.startedAt(context) > 0L` — so no button means the cursor
was cleared, which only happens inside the worker's `finally` when `completed` is
true. One observation closes the entire branch nobody had ever seen:

- `sweep()` returned normally, so `completed = currentCoroutineIsActive()` was true.
- The `finally` ran, which means the **final flush** happened — it is the line
  immediately before the clear.
- **`RefreshCursor.clear()` ran.** The app will not offer to resume a finished
  sweep.
- The notification tore itself down and the service stopped.
- **The wake lock held across the full run** — about 79 minutes against a 4-hour
  timeout, so with room to spare.

**0.68 is verified end to end.** Seven device checks in
`SESSION_HANDOFF_0.68.md` plus this.

### What wasn't captured, and why it doesn't matter much

The row was tapped before anyone read the `N counted • M failed • K skipped`
line, and that summary is process-lifetime Compose state — `finishedAt` and the
counters are never written to prefs. So the numbers are gone.

They are also reproducible, and the accident that lost them started a better test
than the one it destroyed. Tapping the row on a cleared cursor began a **fresh
full sweep from zero**, not a resume: `beginOrResume` stamped a new timestamp,
`sweptSince` matched nothing, and all 3571 entries went into `remaining`. It
started around 00:52 and, at sweep 1's measured 42.7/min, lands about **02:15** —
possibly sooner; see §5.

The sweep that was lost had `resumed = 193`, so its arithmetic needed the
`total − resumed` adjustment. This one starts from zero, so §2's check is simply
`counted + failed + skipped == 3571` — no offset, nothing to reason around.

**In the morning:** screenshot Settings → Library → Chapter counts **before
installing 0.69 or tapping anything**, then do §2. Installing restarts the process
and destroys the summary a second time.

**Done — the capture was made.** Sweep 2's finished row read
`2902 counted • 22 failed • 100 skipped` at 01:45, with the first-error note
showing a spyfakku `kotlinx.serialization` missing-field error. See §2 for what
those numbers say.

### Two costs of the restart

- **The `dataSync` budget is fine.** Two full sweeps at ~1h25m is under three
  hours against Android 14's ~6h daily cap, so downloads today are not at risk. An
  earlier revision of this file said the budget was spent; that was based on a
  wrong duration — see §5.
- **The bug 4 fingerprint is being overwritten.** Clustered gaps of ~100
  un-counted series in the index were the persisted evidence of the interleaved
  `recordAll` race, and the running sweep is filling them in. Not worth stopping
  it over — 0.69 fixes the race regardless, and a filled index is the point of the
  feature.

  **Amended — that fingerprint was probably never bug 4's.** Sweep 2 reported
  `skipped = 100`, and `skipped` is incremented on exactly one path: `src == null`,
  an uninstalled extension. `remaining` is grouped by `sourceId`, so a missing
  extension takes out a *contiguous block* of series by construction. A clustered
  ~100 un-counted gap is what an uninstalled extension looks like, with no race
  required. Both mechanisms produce the same shape, the innocent one is confirmed
  present at exactly the right magnitude, and **the race was never independently
  evidenced.** Don't cite clustered gaps as proof of bug 4 again; check `skipped`
  first, and identify which source those 100 belong to.

---

## 2. The arithmetic, and what it diagnoses

If you have the summary numbers, check:

```
counted + failed + skipped  ==  total - resumed
```

**Sweep 2 started from zero, so this was simply `== 3571`.** In general it is
against `total − resumed`, not against 3571. `done` is seeded from `resumed`, so
measuring against the library size looks wrong on every resumed run even when
nothing is broken. (An earlier revision of this section said "tonight's run
inherited 193" — that was the *lost* sweep, contradicting §1 two paragraphs
above. Sweep 2's `resumed` is 0: the cursor was cleared, `beginOrResume` stamped
a new timestamp, `sweptSince` matched nothing, and all 3571 went into
`remaining`, giving `alreadyDone = entries.size - remaining.size = 0`.)

### The result: 547 short

```
2902 + 22 + 100  ==  3024        against 3571        →  short by 547
```

Per the branches below, that is **bug 3 and/or bug 6**. Both are still open, and
this is the first time either has been quantified.

- **Comes up short** → bug 3 (lost `++` increments across three coroutines) or
  **bug 6** (an empty chapter list lands in `done` and in none of the other
  three), or both. The number alone won't say which; that is why those two fixes
  were deliberately not written that night.
  *(This bullet previously said "bug 4" for the empty-list case. Wrong — bug 4 is
  `recordAll` atomicity, closed in 0.69. §6 of this file had it right. The
  canonical numbering is the 0.67 review's seven, used in `SESSION_HANDOFF_0.68.md`
  §5; note that `PROJECT_HANDOFF.md` renumbers a four-item subset 1–4 and the two
  schemes disagree. See §7 below.)*
- **Series with no count while `failed = 0` and `skipped = 0`**, especially a
  clustered ~100 of them → the interleaved `recordAll` race. **Superseded — see
  the amendment in §1.** `skipped = 100` on this sweep means the innocent
  explanation is confirmed present, so clustering is no longer evidence of bug 4.

### There is exactly one silent path, and it is reachable

Confirmed by reading `refreshSource` against `SeriesIndex.countsFor`:

- `chapters.isEmpty()` → the `if (chapters.isNotEmpty())` block is skipped
  entirely, `done++` runs at the bottom, and none of counted/failed/skipped move.
  **This is bug 6, and it is the only silent path.**
- The inner `if (counts != null)` guard is **dead code**. `countsFor` returns null
  on exactly one condition — `if (chapters.isEmpty()) return null`, line 195 — and
  that case can't reach the guard because the outer `isNotEmpty()` check already
  excluded it. Harmless, but it reads as a second failure mode and isn't one;
  either drop it or turn it into the empty-list accounting fix.

So the 547 splits between bug 3 and bug 6 with no third contributor. Bug 6's share
is countable directly: it is the number of library series whose source returns an
empty chapter list. Whatever is left over is bug 3.

### Do not back-solve `resumed` — the check will always pass if you do

**`resumed` is not rendered in the finished summary.** In `SettingsScreens.kt` it
appears only inside the in-progress branch (`if (LibraryRefresh.resumed > 0)`,
line 251). The finished row — headline "Refresh library", supporting text
`N counted • M failed • K skipped` — has no path that prints it. So after a sweep
ends, the one input this check needs is invisible.

That makes it very easy to "verify" the identity by computing `resumed` as
`total − (counted + failed + skipped)` and observing that it balances. It will
always balance. That substitution turns the equation into `x == x` and destroys
the entire diagnostic value of §2. `resumed` must come from **outside** the three
counters: either read off the in-progress note while the sweep is running, or
derived from the cursor state as §1 does.

**This is a real gap, not just a discipline problem** — see §7 item 1 for the fix.

---

## 3. 0.69 — pushed, installed, three checks pass

`76bd284` — "Serialise index writes; dismiss the refresh summary". Five files,
131 insertions, 53 deletions, versionCode 68 → 69.

**It built and it runs.** CI is at
github.com/MohammedEmad333/MangaReader/actions or `gh run list --limit 3`. The
compile was the live question — `SeriesIndex.kt` took 147 changed lines including
a new nested class and five `synchronized` blocks, verified by brace counting and
identifier greps rather than by a type-checker — and it passed, so that risk is
retired.

**What it changes:**

- **Index writes are serialised** (bug 4 from the 0.67 review). A private
  `writeLock` now covers `record`, `recordAll`, `forget` and `save` — wider than
  the original prescription of `recordAll` + `save`, because that pair would have
  left the race you can actually trigger by hand: `record` from a series screen
  against `recordAll` from the sweep, both doing `all()` → merge → `save()`.
- **`all()` deliberately stays lock-free.** It is called during composition, so
  putting it behind the lock would park a library grid draw behind a whole-index
  serialisation. A reader can see the index as it was a moment ago — already this
  store's documented behaviour — but not a torn one. **Do not "fix" this by
  locking the read path.**
- **`countsFor` stays outside the lock**, because it walks every chapter and asks
  `ReadState` about each, and holding the monitor through that would stall the
  sweep's flushes behind one series screen.
- **The memo is one field, not two** (bug 7). `Memo(raw, items)` behind a single
  `@Volatile` reference, so both halves change at once and a reader can no longer
  match a new raw string against an old parsed map.
- **Bug 5, both halves.** `clearSummary()` is called from
  `LibraryRefreshService.start()`, which closes the window before `begin()` runs;
  and there is now a **Dismiss** button, which is the half that actually returns
  the row to plain "Fetch chapter lists". Note the interaction with §1: once 0.69
  is installed, Dismiss can also destroy a summary you wanted to keep.
- **A 0.69 changelog entry**, because `WhatsNew.kt`'s own KDoc says to add one in
  the commit that bumps `versionCode`, and the 0.66 gap is what happens otherwise.

**Tested — all three checks pass.** Run after installing 0.69:

1. **A series opened during a sweep still gets its count.** Opened a
   previously-unopened series mid-sweep, backed out, count present.
2. **Dismiss works.** The row returns to "Fetch chapter lists for all 3571
   series" and the first-error note clears with it.
3. **A sweep still writes counts.** `counted` climbs mid-sweep rather than
   sitting at 0. This was the one that mattered — the lock is on the only write
   path — and it holds.

**What check 1 does not cover.** It is the only one of the three that creates
contention, and it confirms the *series screen's* write landed — not that the
sweep's concurrent flush survived. Bug 4's shape was `record` and `recordAll`
both doing `all()` → merge → `save()`, and the batch that gets clobbered is the
**sweep's**, silently, a hundred series at a time. A pass on "my series got its
count" is consistent with the sweep's batch having been eaten.

To actually close it: note the series the sweep is displaying at the moment you
open one by hand, let the sweep finish, then check the index for a gap around
that title. Until then 0.69's lock is verified as *not obviously broken* rather
than verified as correct — which, given `SeriesIndex.kt` took 147 lines checked
by brace-counting and not a type-checker, is worth keeping straight.

---

## 4. The manifest line is not a one-line fix — four handoffs have been wrong

Recorded properly because this keeps getting re-promised as free.

`AndroidManifest.xml:65` declares `@android:style/Theme.Material.NoActionBar`, the
dark variant. The standing instruction has been "change one line." **You can't**,
and the reason is in `AppPrefs.kt`:

- **`ThemeMode` defaults to `DARK`**, and light/dark is an *in-app preference*
  with SYSTEM / LIGHT / DARK options — not the system setting.
- So `Theme.Material.Light.NoActionBar` breaks the default case, and
  `Theme.DeviceDefault.DayNight.NoActionBar` follows the system setting, which is
  wrong for anyone who chose DARK on a light phone or LIGHT on a dark one.
- There is **no `res/values/themes.xml`** — the app uses the platform theme
  directly. Only `res/values/colors.xml` exists, holding one launcher colour.

The real fix is two pieces of work:

1. **The status bar** is fixable at runtime and is the smaller half.
   `AppTheme.load(this)` already runs in `MainActivity.onCreate` before
   `setContent`, so the resolved light/dark answer is known there. Set
   `WindowCompat.getInsetsController(...).isAppearanceLightStatusBars` from it.
   Resolving `ThemeMode.SYSTEM` outside composition needs
   `resources.configuration.uiMode`, not `isSystemInDarkTheme()`.
   `ReaderScreen.kt:407` already uses the insets controller, so the API is proven
   in this project.
2. **The cold-start flash** needs `res/values/themes.xml` plus
   `res/values-night/themes.xml`, and can only ever track the *system* setting —
   so for a user who set LIGHT on a dark phone it is not fully fixable without
   mirroring the pref into something resources can see.

Decide it deliberately. It is not a line.

---

## 5. Measured rate — and a correction I got wrong first

**Sweep 1 ran 3114 series in 73 minutes: 42.7/min.** 457 at 23:37 → 3571 at
00:50. Working back from its inherited 193 puts its start at ~23:31, giving 3378
series in 79 minutes — the same rate, so the figure is self-consistent. **A full
3571-entry sweep is therefore ~1h25m.**

That means `PROJECT_HANDOFF.md`'s original "an hour or two" was **right**, and an
earlier revision of this file and of §7 item 5 replaced it with ~3h10m, which was
wrong. The bad figure came from computing a rate against an *assumed* timestamp:
a progress reading of "1815 of 3571" was treated as live at 00:50 when it was
actually a delayed message describing an earlier moment. Both documents are now
corrected.

**The lesson, since this file exists to carry them:** don't derive a rate from a
reading whose timestamp you inferred. One unlabelled data point produced a wrong
duration, a wrong "over half the daily budget" claim, and a wrong "the wake-lock
margin is thin" warning — three derived errors from one assumption, which is the
same failure shape as the CI round trips in §8 of the main handoff, just in prose
instead of Kotlin.

What survives the correction:

1. **The sweep is latency-bound, not spacing-bound.** 42.7/min across
   `SOURCE_CONCURRENCY = 3` is ~4 seconds per series within a source against a
   `REQUEST_SPACING_MS` of 250ms. **`SOURCE_CONCURRENCY` is the lever, not
   `REQUEST_SPACING_MS`** — weigh either against the manhwatoon lesson first.
2. **The `dataSync` budget is fine at this scale.** Two full sweeps in one night
   is under three hours against a ~6-hour cap. The hazard in §7 item 5 is a paused
   queue or a scheduled sweep, not two manual ones.
3. **The wake lock has room.** 4-hour timeout, ~1h25m sweep.

**Found while measuring, and worth its own line:** `NetworkHelper` gives the shared
client a 5 MiB disk cache and `Requests.kt` defaults every `GET` to
`maxAge(10, MINUTES)`. Two sweeps begun within ten minutes of each other can serve
chapter lists from cache instead of re-checking the source, which is not what a
refresh means. A full sweep outlasts the window and 5 MiB won't hold 3571 chapter
lists, so it mostly doesn't bite — but a "refresh this series now" button would sit
right inside it.

**Sweep 2** started ~00:52 and was around half done at 01:16, which is faster than
sweep 1. Too loose to call: "roughly 50%" spans a wide range, and the cache above
could account for part of it. If it matters, time it properly rather than
reconstructing it afterwards.

**Amended — there is now a bound, and it is a bound, not a rate.** The 01:45
screenshot shows sweep 2 already finished, so it ran 3571 series in **at most 53
minutes: ≥67/min**, against sweep 1's 42.7/min. Note what this is: 01:45 is when
the screenshot was taken, which caps the finish time without dating it. Quoting
"53 minutes" as sweep 2's duration would repeat this section's own mistake in the
opposite direction.

The bound agrees with the 01:16 half-done reading (~74/min), so the speedup is
real even if its size isn't pinned. Candidate causes, none confirmed: the HTTP
cache (weak — the 10-minute window and 5 MiB can only cover the tail of sweep 1),
the 100 skipped series (2.8%, no request at all, so also small), and **the
empty-list returns behind bug 6** — a source failing fast returns quicker than one
serving a full chapter list, so a run with 547 unaccounted series would finish
early for a reason that is a defect rather than a speedup. That last one is the
interesting hypothesis, and it predicts the rate falls back toward 42.7/min once
bug 6 is fixed. Worth timing sweep 3 properly to find out.

---

## 6. State of the tree

- `2bac449` — 0.68, the resume. Seven device checks passed; see
  `SESSION_HANDOFF_0.68.md`.
- `5ac4ebe` — docs: handoff brought up to date through 0.68, `SESSION_HANDOFF_0.68.md`
  added.
- `76bd284` — 0.69, index write serialisation and the summary dismiss.
  **Installed; three checks pass (§3), with the contention gap noted there.**

Library is **3571** entries. Most of `PROJECT_HANDOFF.md` says 3567, which is the
import figure and still correct in every sentence about the import.

**Still open from the 0.67 review:** bug 3 (counter race) and bug 6 (`done`
doesn't reconcile). Bugs 1, 2, 4, 5 and 7 are closed. The arithmetic has now run
and says **547 combined** — it still doesn't split them, but bug 6's share is
directly countable (§2), so subtracting gives bug 3's. Sweep 3 is no longer
needed to *find* this; it is needed to attribute it.

**Still open, older:** the manifest theme (§4 above), and open thread 1 — the
reader is barely tested and has now survived six sessions untouched.

**Named next piece of work** stays the Feed / Updates tab, per
`PROJECT_HANDOFF.md` §0: the sweep now produces the whole library's chapter
counts, so the only missing input is a diff of what changed during a sweep.

---

## 7. Opened by this amendment

Three items, all small, all found while checking §2 rather than while writing
code.

**1. The finished summary should show `resumed`.** §2's whole check needs it and
the finished row doesn't print it, so the number has to be remembered from the
in-progress note or reconstructed from the cursor — and the tempting
reconstruction is the circular one. Fix is one `listOfNotNull` entry in
`SettingsScreens.kt` beside the existing three, guarded on `resumed > 0` the same
way `failed` and `skipped` are. Cheap, and it makes the arithmetic
self-contained: `2902 counted • 22 failed • 100 skipped • 0 resumed` would have
been unambiguous with no reasoning required.

While in there: **the counters don't survive process death** (`finishedAt` and
all five are Compose state with nothing persisting them), which is what lost
sweep 1's summary and nearly lost sweep 2's. If §2 is going to stay the standing
end-of-sweep check, the five numbers belong in prefs next to the cursor.

**2. Dead `counts != null` guard** in `refreshSource` — see §2. Unreachable
because `countsFor`'s only null return is the empty-list case the outer
`isNotEmpty()` already excludes. Resolve it as part of the bug 6 fix rather than
separately, since that fix has to decide what an empty list counts as anyway
(`PROJECT_HANDOFF.md` says count it as a failure, on the grounds that from a live
source it usually means a broken parse — the spyfakku error in sweep 2's
first-error note is that exact shape).

**3. Two incompatible bug numberings are in circulation.** The canonical one is
the 0.67 review's seven, used in `SESSION_HANDOFF_0.68.md` §5 and in §3/§6 here:

| # | Bug | State |
|---|---|---|
| 1 | `updated` counted fetches, not changes | closed (renamed) |
| 2 | Stop could strand the notification | closed |
| 3 | The counters race | **open** |
| 4 | `recordAll` not atomic | closed in 0.69 |
| 5 | `clearSummary()` has no callers | closed in 0.69 |
| 6 | `done` doesn't reconcile (empty list) | **open** |
| 7 | Memo seeded in two steps | closed in 0.69 |

`PROJECT_HANDOFF.md` used to renumber the then-open four as 1–4, so its "bug 4"
was this table's bug 6 and its "bug 1" was this table's bug 4. That collision
produced the wrong label in §2 of this file. **Use the seven-item numbering
everywhere**; `PROJECT_HANDOFF.md` has been updated to match and now carries a
note saying so.
