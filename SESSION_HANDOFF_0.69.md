# Session handoff — 0.69, and the first completed sweep

Written overnight on 2026-07-30. The first full library refresh **completed** —
§1 is the verdict and it is the good one. A second, accidental full sweep is
running as this is written. **0.69 is pushed, unverified and not installed.**

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

---

## 2. The arithmetic, and what it diagnoses

If you have the summary numbers, check:

```
counted + failed + skipped  ==  total - resumed
```

**Tonight the running sweep starts from zero, so this is simply `== 3571`.** In general it is against `total − resumed`, not against 3571. `done` is seeded from `resumed`,
so measuring against the library size looks wrong on every resumed run even when
nothing is broken. Tonight's run inherited 193.

- **Comes up short** → bug 3 (lost `++` increments across three coroutines) or
  bug 4 (an empty chapter list lands in `done` and in none of the other three),
  or both. The number alone won't say which; that is why those two fixes were
  deliberately not written tonight.
- **Series with no count while `failed = 0` and `skipped = 0`**, especially a
  clustered ~100 of them → the interleaved `recordAll` race. 0.69 fixes it, so
  this is the last sweep that can show it.

**If the summary is gone**, this measurement moves to the next full sweep — which
will be running 0.69, so it becomes a check on the fixed code rather than a
diagnosis of the broken code. Not a disaster; it just means bugs 3 and 4 stay
unquantified for another cycle.

---

## 3. 0.69 — pushed, compiled by nobody

`76bd284` — "Serialise index writes; dismiss the refresh summary". Five files,
131 insertions, 53 deletions, versionCode 68 → 69.

**Check CI before installing:** github.com/MohammedEmad333/MangaReader/actions,
or `gh run list --limit 3`. The `latest` release being replaced means it built.
This matters more than usual — `SeriesIndex.kt` took 147 changed lines including
a new nested class and five `synchronized` blocks, and it was verified by brace
counting and identifier greps, not by a type-checker. If anything broke, it broke
there.

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

**Untested, all of it.** Nothing in 0.69 has been run. The three things to try
after installing: a series opened during a sweep still gets its count; the
Dismiss button clears the row and the first-error note; and a sweep still writes
counts at all — the lock is on the only write path, so a mistake there is not
subtle.

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

---

## 6. State of the tree

- `2bac449` — 0.68, the resume. Seven device checks passed; see
  `SESSION_HANDOFF_0.68.md`.
- `5ac4ebe` — docs: handoff brought up to date through 0.68, `SESSION_HANDOFF_0.68.md`
  added.
- `76bd284` — 0.69, index write serialisation and the summary dismiss.
  **Unverified.**

Library is **3571** entries. Most of `PROJECT_HANDOFF.md` says 3567, which is the
import figure and still correct in every sentence about the import.

**Still open from the 0.67 review:** bug 3 (counter race) and bug 6 (`done`
doesn't reconcile), both deliberately left until tonight's arithmetic says which
is which. Bugs 1, 2, 4, 5 and 7 are closed.

**Still open, older:** the manifest theme (§4 above), and open thread 1 — the
reader is barely tested and has now survived six sessions untouched.

**Named next piece of work** stays the Feed / Updates tab, per
`PROJECT_HANDOFF.md` §0: the sweep now produces the whole library's chapter
counts, so the only missing input is a diff of what changed during a sweep.
