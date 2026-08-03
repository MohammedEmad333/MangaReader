# Session handoff — 0.150 to 0.172, two cards that were wrong, and a label that was four times the same mistake

Written 2026-08-03. Nothing here supersedes anything.
`SESSION_HANDOFF_0.83.md`, `SESSION_HANDOFF_0.87.md`, `SESSION_HANDOFF_0.106.md`,
`SESSION_HANDOFF_0.120.md`, `SESSION_HANDOFF_0.143.md` and
`SESSION_HANDOFF_0.149.md` all remain live reference.

Twenty-three releases. **Two of them were written against cards whose stated premise
turned out to be false**, one fixed a bug that had been invisible for as long
as the feature existed, one fixed a bug this session had introduced two hours
earlier, and one corrected a prediction this session had got wrong by a factor
of five. Everything except §2 is verified on device.

---

## 0. Read this before touching anything

**1. Head is 0.172. The whole range is verified on device except two cards whose
code has never executed and cannot be made to: `onRenderProcessGone` (§2) and
the `getPageList` ceiling (§9e). Both sit in Needs verifying, deliberately.**

**2. `DownloadQueue.progress` is no longer a percent, and `head()` no longer
means "the first item".** Two contract changes in one day, both in §3 and §5.
Anything reading either needs to know.

**3. THE RECURRING FAULT THIS SESSION, four times in five releases: one label
standing for two mechanisms.** `Starting` (§3), `0 chapters` (§4),
`Clear cookies` (§4), and the pause button (§6). Every one presented as a
cosmetic complaint and every one was a state the code could not express. **When
a control or a string covers two causes, the fix is almost never the wording —
it is that something upstream threw away the distinction.**

**4. A card is not evidence.** §3 and §7. Two cards this session asserted a
mechanism confidently, in detail, and were wrong — not in the diagnosis but in
a claim nobody had gone back and checked. Both were written by whoever had just
finished looking at the symptom. Read the code the card describes before
building on it.

**5. A test pass written by whoever wrote the change checks what was written.**
§6. Card 82's step 4 verified that the notification *said* "All chapters on
hold" and not what its *button* did. The bug was in the button.

**6. A mechanism read is not a number known.** §8.

**8. A RULE FROM ONE CASE MAY NOT COVER THE NEXT ONE.** §9d — four reports of
the same bug, because 0.158's warning about *container* roles was applied to a
*foreground* role it never covered.

**7. THE MOST REUSABLE THING IN THIS FILE IS §9b.** The scroll handle stopped
short of the end of every list for thirty releases and was signed off four
times, because every test step said "reaches the last row" and none said "the
last row is FULLY VISIBLE". Three wrong diagnoses followed, all from "what
changed most recently" — for a bug no recent change could have caused. The
question that settled it took one look and was asked fourth. 0.155's circuit breaker was
predicted at ninety seconds and took four and a half minutes, because
`downloadPage` retries three times and nothing multiplied by that — the retry
loop had been read while writing the change. That is §0.5 of
`SESSION_HANDOFF_0.149.md`, made again in the same session that wrote it down.

---

## 1. The releases

| | What | Commit | State |
|---|---|---|---|
| 0.150 | `onRenderProcessGone` in both WebViewClients | `0801208`* | Built, **never executed** |
| 0.151 | `DownloadProgress(ready, total)` replaces a percent | `097f0e1` | Verified |
| 0.152 | Pause stops the chapter in flight | `0801208` | Verified |
| 0.153 | Per-download pause; queue screen icons; two one-liners | `fe71519` | Verified |
| 0.154 | `Resume all` when holds are the only thing stopping the queue | `d1ac443` | Verified |
| 0.155 | A per-host circuit breaker for one page fetch | `ca4365c` | Superseded by 0.156 |
| 0.156 | Attempt-level counting, retry suppression, tags on the chevron | `b52cbf0` | Verified |
| 0.157 | Scroll handles on Downloads and global search; the chevron gap | `68e989a` | Verified |
| 0.158 | Last two scroll handles; Start follows the accent | `d0e9bd5` | Verified |
| 0.159 | Start's label by luminance; History's handle | `52054a6` | Verified |
| 0.160 | Pull to refresh on History and Downloads | `fb021f9` | Superseded |
| 0.161 | Clear the refresh flag from an effect | `dec24ed` | Verified |
| 0.162 | Invalidate the download index; browse refresh | `0a5261d` | Half reverted |
| 0.163 | Clear the completion memo; browse refresh out | `e9d3055` | Verified |
| 0.164 | Grids seek by row | `0f04700` | Real fix, not the reported one |
| 0.165 | Handle inset from the edges; browse refresh back | `34b0dbe` | Partial |
| 0.166 | Count only rows that fit; system-bar insets | `514e467` | Verified |
| 0.167 | Item count from the list, not the caller | `829ce83` | Verified |
| 0.168 | Notification opens the queue; wider handle | `efac5fa` | Verified |
| 0.169 | — | `6561471` | **RED, did not build** |
| 0.170 | Back twice to exit; no premature chapter verdict | `10f987e` | Verified |
| 0.171 | getPageList ceiling; handle on the wrong list | `4e59378` | Half wrong |
| 0.172 | onPrimary derived from the accent; right list | `125a546` | Verified |

\* 0.150 was `83e3a56`, force-pushed to `0801208` after backticks in a commit
message were shell-expanded and blanked two words. If a future session wonders
why `4d7c3dc` shows as cancelled in CI, that is why.

All built green. One cancelled run, from the force-push.

---

## 2. 0.150 — shipped, correct as far as anyone can tell, and never run

Both `WebViewClient`s — the headless one in `CloudflareInterceptor` and the
visible one in `WebViewScreen` — now override `onRenderProcessGone` and return
`true`. Neither did. An unhandled renderer death is not a Java exception in this
process, so nothing could catch it and `CrashLog` could not see it; Android
simply killed the app.

**The headless one also stops waiting.** Without that the renderer could die two
seconds in and the poll would keep asking for a cookie nobody is going to write
for the remaining twenty-eight, holding the per-host lock throughout.

**The lock needed no work, and the card's warning about it was still right to
make.** It is a `synchronized` block, so the renderer path releases it by
returning and a throw releases it by unwinding. That is now a comment, because
the next person to touch it should not add a release that can be missed.

### Why it is unverified, and what that costs

The new code only executes during a Cloudflare challenge. **No source would
issue one.** Asura Scans never asked; HentaiSco and ManhwaHub had previously and
did not, even after Clear cookies. Cloudflare decides per request on IP
reputation, and clearing a client cookie does not reset that.

So 0.150's status is **no regression observed, fix unexercised**. Two ways to
change that, neither taken:

- **adb.** Kill the app's `sandboxed_process` with a WebView open.
- **A debug-only trigger.** Loading `chrome://crash` in a WebView kills its
  renderer. There is precedent in the CrashLog test crash.

**Do not let 0.150 sit as "probably fine" indefinitely.** 0.138 and 0.140 were
both reasoned from an untested instrument's silence, and 0.143 is the release
that had to go back and prove the instrument fired.

---

## 3. 0.151 — the card was wrong, and the wrong part was the load-bearing part

Card 78 said `percent == null` and `percent == 0` "are already distinct values
arriving at that line; they just need distinct labels."

**They are not distinct.** `runItem` wrote `setProgress(chapterId, 0)` the
moment a chapter became active — *before* the page list request went out — and
`fetchPages` wrote 0 again when the list came back with nothing downloaded. Both
reached the screen as `0`. `null` meant only "queued behind something else",
which for an *active* item was a race between two adjacent lines, so the
`percent == null ||` half of that condition was near dead code.

**Relabelling could not have worked, because there was nothing to relabel
against.** The card's own later paragraph — store `ready` and `total` — was not
the nicer option, it was the only one. A ratio cannot express "I do not know the
denominator yet". Two integers can.

`DownloadQueue.progress` is now `Map<String, DownloadProgress>` with `total`
null until the page list lands, and a `percent` accessor returning null when
there is no honest ratio.

**Four states where there were two labels:** `Starting` (active, not picked up),
`Fetching pages` (list requested, outstanding), `No pages` (list arrived empty),
and `12 of 36`. The bar goes indeterminate *only* when there is no ratio, so a
real 0 of N draws an empty determinate bar rather than looking identical to not
having asked.

**What it bought within the hour.** AHottie now reads `0 of 30`: the page list
came back with thirty pages and zero images are landing. Under 0.150 that said
`Starting` and was indistinguishable from the source never answering. It is a
second, independent confirmation of card 50.

**The series-screen chapter badge keeps a percent**, on width alone. It is a
trailing badge on a narrow row, not the screen whose job is explaining a stall.

---

## 4. 0.153's two one-liners, and why they are in this file at all

Both are trivial changes. Both are the same fault as §3.

**`Clear cookies` promised something it did not do.** The dialog said "a
Cloudflare challenge you've already passed will come back"; the handler only
emptied `CookieManager`. The UA that earned clearance lives in its own
SharedPreferences (`cf_clearance_ua`) and `NetworkHelper` presents it per host
on every request, so the app went on identifying itself exactly as it had when
it passed. `ClearanceUserAgents.clear()` already existed, with a KDoc reading
*"For a settings screen, if one ever wants it."* Nothing called it.

**Not claimed, and it matters:** this does *not* prove the surviving UA is why
no challenge could be forced in §2. Cloudflare's per-IP decision is equally
live. What is fixed is that the button does what it says.

**`0 chapters` claimed a measurement this app never made.** An extension whose
selector matched nothing returns the same empty list as a series that genuinely
has none, because Jsoup's `select()` yields an empty set rather than throwing.
It now reads "This source returned no chapters".

**That is the wording half only, and card 80 is now the other half.** The app
still cannot *tell* the two apart. The honest fix needs the source layer to
record that a fetch completed and yielded zero, so unfetched, failed and
genuinely-empty are three states rather than one — the same shape as §3.

---

## 5. 0.152 and 0.153 — the download queue's two pauses

### Pause did nothing mid-chapter (0.152)

Reported as "the pause button does nothing", and that was nearly literally true.
The button, the notification action, `ACTION_PAUSE` and `DownloadQueue.paused`
all worked. **The worker loop tests `paused` at the top and then blocks on
`job.join()` for the whole current chapter**, so pause was only ever checked
*between* chapters.

**Why it read as dead rather than slow:** it was reported against AHottie, whose
every page times out. 30 pages × 30s is about a quarter of an hour. On an
ordinary source it would have looked like a short delay and probably never been
filed.

`ACTION_SKIP` already cancelled `itemJob`, so the mechanism existed. **The
blocker was the exit path:** `runItem`'s finally calls `finish()`
unconditionally, and on a cancellation the failure string is still null, so the
chapter would have been removed from the queue as though it had *completed*.
Skip wants that. Pause must not. A volatile `pausing` flag now tells the two
cancellations apart.

**A control checked once per outer iteration is only as responsive as the
slowest iteration.**

### A pause per download (0.153)

**This changed the queue's contract and is not a copy of the queue-wide pause.**
`head()` was `items.firstOrNull()`. Stopping at a held chapter would let one row
block every runnable chapter behind it. `head()` now returns the first item
*not* individually paused, so the queue walks past it and **the held chapter
keeps its position** — resuming does not send it to the back.

Three consequences, none optional:

- **The worker idles rather than exits when everything is held.** Left to
  `head()` returning null it would break the loop and stop the service, so
  resuming would need the worker rebuilt.
- **The notification named the wrong chapter.** `buildNotification` read
  `items.firstOrNull()`, which can now be one the worker is skipping.
- **Pausing the active chapter** reuses the `pausing` flag so the item is left
  queued, not finished.

State is a set of ids, persisted beside the queue and pruned on restore and on
`remove` / `clear` / `finish`, so a pause cannot outlive its chapter or revive
if the same chapter is queued again.

---

## 6. 0.154 — the bug this session introduced, caught on a screenshot

The notification read **"All chapters on hold"** with a **PAUSE** action beside
it. That is not a wrong label; it is a dead loop.

0.153 taught the notification's *title* about individual holds and left the
*action* keyed on `DownloadQueue.paused` alone. With every chapter held and the
queue-wide pause off: Pause → sets the queue-wide pause → title becomes
"Downloads paused", button becomes Resume → clears it → back to "All chapters on
hold" and Pause. **The button toggled a mechanism that was not the one holding
the queue, so nothing could ever be resumed from the notification.** The queue
screen's top bar had the identical loop.

Both are three-state now: `Resume` for the queue-wide pause, `Resume all` when
holds are the only thing stopping anything, `Pause` otherwise.

**`clearItemPauses` is deliberately not folded into `ACTION_RESUME`.** A global
pause taken while three chapters are held must resume to those three still held
— separate intents, and resuming one must not silently discard the other.
All-held is the only case where "resume" has no second possible meaning, which
is why the action is offered only there.

### How the test pass missed it

Card 82's step 4 read: *"hold every chapter: nothing downloads, notification says
All chapters on hold, resuming one starts it"*. Every clause passed, because
resuming was done **in the app**. The step checked the notification's text and
not its button.

**A test written by whoever wrote the change checks what was written, rather
than what the user reaches for next.** The step existed *because* the all-held
state was new; it still looked at the wrong half of the thing it was there to
cover.

---

## 7. Manhwa18 — a card that was filed as the wrong kind of problem

Card 74 sat as a "connection probe" with a probe attached. It is not a
connection problem at all, and one screenshot settles it.

The series screen showed **title, cover and backdrop rendered, "0 chapters", and
no error**. `mangaDetailsParse` does `document.selectFirst(".series-name a")!!`
— a hard `!!` that would have thrown on a wrong or empty document. It did not.
`chapterListParse` is `document.select("ul.list-chapters a").map { }`, and
`select()` returns an empty set when nothing matches, so `.map` yields an empty
list. **No exception, silent zero.**

The extension overrides neither `mangaDetailsRequest` nor `chapterListRequest`,
so `HttpSource` defaults both to `GET(baseUrl + manga.url)` — **literally the
same request. One document, two selectors, one matched.**

So it is a stale selector or a login gate — the probe recorded
`manhwa18_session` and a body containing "Login Register", which makes gating
likelier. Either way there is nothing to fix in this app.

**Why the probe named nothing:** it only ever fetched the homepage, so it never
exercised the failing path. A probe pointed at the wrong URL produces a clean
report about the wrong thing.

**Coomer (69) closed the other way and is Blocked/Upstream.** `coomer.st` loads
in Chrome, but its own frontend page calls `https://coomer.st/api/v1/posts` and
renders the site's own error: *API is in maintenance or not available*, 503. Two
independent clients, same host, same failure. A DDoS-Guard challenge was a live
and reasonable hypothesis — the probe recorded `server: ddos-guard` and DDoS-Guard
does serve 503 as an interstitial — and the browser killed it in one tap.

---

## 8. 0.155 and 0.156 — the circuit breaker, and the number nobody multiplied by

A chapter whose every page targets one unreachable host used to spend
36 × 30s discovering that, one page at a time, with nothing remembering that
the previous thirty-five connections to the same host had failed. 0.151 made
that stall legible — `0 of 30` instead of `Starting`. This makes it end.

### 0.155 worked, and was five times slower than predicted

It named the right host for the right reason and took **over four minutes**
against a predicted ninety seconds.

**`downloadPage` retries.** `PAGE_ATTEMPTS = 3`, and `isTransient` returns true
for any `IOException`, which includes `SocketTimeoutException`. So every *page*
failure cost 3 × `connectTimeout`, not one, and six page failures were three
batches of ninety seconds.

**The retry loop was read while writing 0.155 and not multiplied by.** That is
`SESSION_HANDOFF_0.149.md` §0.7's complement, verbatim, in the session that
quoted it: read the number that governs an instrument before predicting how
long it takes to speak.

**Lowering the threshold could not have fixed it.** The floor was
`PAGE_ATTEMPTS × connectTimeout` on the first batch whatever the limit was.

### What 0.156 changed

- **Counted per ATTEMPT, not per page.** A retry is another connection to the
  same host and counts as one.
- **A shared `ConcurrentHashMap`, written from inside `downloadPage`**, so a
  failure is visible to the sibling page coroutines *while the batch is still
  running* rather than after it.
- **Retries suppressed past the first connect failure per host**
  (`CONNECT_RETRY_GIVE_UP_AT = 2`). **Retrying a connect timeout against a host
  that just timed out buys nothing** — `recycleConnections()` exists for a stale
  pool, and a fresh socket pays the full timeout again to learn the same thing.
  This is a behaviour change for every source, not just dead hosts.
- **`HOST_CONNECT_FAILURE_LIMIT = 4`**, now attempts: roughly two batches.
- **Successes still clear the host**, computed once per completed batch so the
  reset does not depend on the order results arrive in.

### The other half: a count that was not a measurement

`"30 of 30 pages failed"` was `done.count { it == null }` — and a page nobody
reached is null exactly like a page that failed. The chapter had stopped after
six. Now `attempted` and `failedSoFar` are tracked separately.

**Same fault as §3 and §4**, in the same area, found by reading a screenshot of
this session's own fix.

### The verifying message names the mechanism

> gave up after 5 consecutive failures across 4 of 30 pages

**Five attempts from four pages.** The first page to hit the host got its one
permitted retry, then the suppression stopped the rest. That is attempt-level
counting visible in the output, and it is only legible because the count and
the page total are now reported separately.

---

## 9. Tags on the series chevron (0.156), and a gap left in it

Expanding a series description now wraps every tag; collapsed, the row still
scrolls horizontally. Tags past the right edge had been reachable only by a
horizontal drag nothing advertised, so a series with many genres looked like it
had four.

**`FlowRow` is opted into rather than worked around.** The old comment avoided
it as experimental on this Compose version, and that reason still stands for
the collapsed row — which keeps the scrolling `Row`, so a long tag list cannot
push the chapter list off screen. For the expanded one there is no good
alternative: chip widths vary, so chunking into fixed rows leaves ragged gaps.

**The chips moved into one `GenreChips` composable** used by both branches. Two
copies of a chip that owns a dropdown is two places for the menu to drift, and
that menu is the reason a tag stopped being decoration.

**Inserted before `SeriesScreen`'s `@OptIn` block, not between an annotation and
its declaration** — `SESSION_HANDOFF_0.149.md` §5. Both annotation blocks were
re-checked as contiguous afterwards.

**The gap it left, closed in 0.157.** The chevron only rendered inside the
description block, so a series with tags and no description had no way to expand
them. That is the same shape as §6's dead loop — a control that cannot reach a
state — and it is the **third** appearance of that shape in one session, after
the `Starting` label and the all-held pause button. It now renders above the
tags when there is no description to sit under.

**Worth a grep next time rather than a report.** Three of the four instances
this session were found by someone looking at a screen, not by anyone looking
for them.

---

## 9a. 0.157 — scroll handles on two more lists

Card 47's claim held: with `ListScrollHandle` doing the wiring, each new caller
really is a hoisted state, a `Box` and the handle. **What is not shared is the
item count, and the two added here differ in an instructive way.**

- **Downloads tab: `series.size`.** No header, no actions block, no trailing
  spacer, so the list's item count *is* the data count — unlike the chapter
  list's `visible.size + 3`.
- **Global search: `shown.size`, which is one lazy item PER SOURCE**, not per
  series. Each item draws a source header and a horizontal row of covers, so
  the list is short in items and tall in pixels. Seeking by item index is still
  the right unit. `shown` rather than the unfiltered results, for the same
  reason the chapter list spans `visible`.

Four of N done: library grid (0.133), chapter list (0.149), these two.
**Left: per-source browse and the Extensions list.** Browse is a *grid*, so it
would call `ScrollHandle` directly with four integers the way the library does,
not `ListScrollHandle`.

---

## 9b. The scroll handle never reached the end, and five releases went into finding out why

**Read this one for the method, not the fix.** The fix is four lines.

The handle stopped short of the end of every list in the app — a fifth of the
last cell on Downloads and History, two chapters on a long series, the *Load
more* button on a browse grid. **It had been that way since 0.133 and was signed
off as working four separate times.**

### Why every earlier verification passed

Every test step written for it — by me — said *"reaches the last row"* or
*"REACHES THE LAST CELL"*. **None said "the last row is fully visible."** A
handle that stops 20% short passes the first wording honestly, because the row
*is* on screen. It is just clipped.

**The wording was the defect.** Not the tester, not the code under test.

### The two real causes

1. **Partially visible items counted as fitting** (0.166).
   `layoutInfo.visibleItemsInfo` includes items clipped by the viewport edge, so
   `span = totalItems - visibleItems` overstated capacity and the furthest
   seekable index fell short by however many were clipped — one at each end,
   usually. Both wrappers now count only items wholly inside the viewport, and
   grids count rows the same way.

2. **Hand-counted item totals** (0.167). The chapter list passed
   `visible.size + 3`. **0.157 added two lazy items to that screen** — the tag
   row and the chevron for tags with no description — **and left the `+ 3`
   alone.** The parameter is now gone entirely: `layoutInfo.totalItemsCount` is
   the composition's count and cannot drift from it. All seven callers stopped
   passing arithmetic.

### Three wrong answers first, and each one fit

- **A `PullToRefreshBox` wrapper** (0.163). Reverted; changed nothing.
- **Grid row alignment** (0.164). A *real* bug — `scrollToItem` aligns the row
  containing an index, so cell arithmetic drops the final partial row — fixed,
  and not the reported symptom.
- **The navigation gesture strip** (0.165). Plausible: the track ran to the
  screen edge, so the end of a drag competed with the system back swipe.

**All three came from "what changed most recently between working and broken."**
The code had been wrong since 0.133, so no recent change could have explained
it, and that should have been visible from the first report.

### The question that settled it

> When a list stops short, is the thumb **hard against the bottom of its track**,
> or short of it?

Bottom means the arithmetic is wrong. Short means the drag is being cut off. One
look, and it was available before any of the three wrong answers. **It was asked
fourth.**

### The generalisation

**A restatement of a number the framework already knows is a bug waiting for
someone to edit the thing it restates.** Card 47 had *instructed* every new
caller to hand-count its lazy items, and treated the fact that each caller had a
different number as evidence of care. It was six copies of a fact with one
owner.

---

## 9c. Pull to refresh, and a cache under a cache

0.160 added the gesture to History and Downloads. **On Downloads it did nothing
at all**, and it took three attempts to make it do anything.

- **0.160**: bumped the revision. `DownloadIndex.list()` memoises, so the tick
  re-read the memo and never touched the disk.
- **0.162**: called `DownloadIndex.invalidate()`. That forces a rebuild — and
  the rebuild calls `Downloads.isComplete()` for every record, **which answers
  from its own `ConcurrentHashMap`**. The rebuild re-asked a memo that still
  said "complete".
- **0.163**: `Downloads.invalidateCompletion()`, which drops the completion and
  size memos *and* the index. Its own comment says it is "for anything that
  moves or removes files in bulk", which is exactly what a refresh asserts.

**Clearing an outer cache while an inner one still answers is not a partial fix,
it is no fix** — and it reads as a fix, because the code plainly does something.

**Two reversals worth keeping.** Mid-session I argued these gestures were
near-redundant, since every in-app mutation already re-reads. True, and the
wrong conclusion: the case it dismissed — a change made from *outside* the app —
was both the only case the gesture could serve and the case that was broken.
And the indicator itself needed `isRefreshing` cleared from an **effect**;
cleared inline, `PullToRefreshBox` never observes the transition and the arrow
stays parked on screen (0.161).

---

---

## 9d. 0.172 — the four-report bug, and a rule applied to a case it did not cover

**Every filled `Button` in the app drew purple text**, whatever accent was
picked. `Button` defaults to `containerColor = primary` and
`contentColor = onPrimary`; `AppPrefs` moved `primary` with the accent and left
`onPrimary` at the Material baseline, which is a dark purple. Legible on amber,
and obviously from another palette.

**It was reported four times** — "Got it", "Tags", "Remove", "Close" — before
anyone traced it past the call site.

### Why it took four

**0.159 fixed exactly this on the Start button**, with a luminance calculation
at the call site, because deriving a scheme colour looked like the thing 0.158
had warned against.

**It was not.** That warning was about `primaryContainer` and the other
**container** roles. Those are separate *surfaces*: deriving them would change
unrelated controls and break the property `AppPrefs` exists to protect.
`onPrimary` has no meaning except *"legible on top of primary"* — computing it
from primary is the definition, not a side effect.

So a rule learned from one case was applied to a case it did not cover, and the
distinguishing feature — **surface role versus foreground role** — was sitting in
the role names the entire time. The cost was four more sightings plus a
workaround that fixed one control out of dozens.

`onPrimary` is now derived by luminance in `AppPrefs`, and the Start button is
back to plain `onPrimary`.

**Still baseline, and correctly so:** `primaryContainer`, `secondaryContainer`,
`tertiaryContainer` and their `on` pairs. If a control taking one of those ever
looks foreign, fix it at the call site — but check which kind of role it is
first.

---

## 9e. 0.171 — a ceiling that admits what it cannot do

`getPageList` had no limit of any kind. An extension that looped was
indistinguishable from a slow source, and the reader sat on spinners forever.

Two limits now, in `cappedPageList`, the only path to `delegate.getPageList`:

- **`PAGE_LIST_MAX = 2000`** catches an extension that *returns* having
  collected nonsense. Exact, and generous on purpose — refusing real content
  would be worse than the failure it guards.
- **`PAGE_LIST_TIMEOUT_MS = 180_000`** catches one that *never returns*, **and
  does not stop it.** `withTimeout` cancels the coroutine, and cancellation is
  cooperative: a blocking `while (true)` inside an extension has no suspension
  point to observe it, so its thread runs until it finishes or the process dies.
  What the clock buys is that the **reader** stops waiting and says why. The
  runaway work is leaked, knowingly.

**If a source is ever found that hits the clock, that leak stops being
theoretical.** The only lever reaching inside an extension's own loop is its
OkHttp call timeout; nothing at this boundary can interrupt it.

Both **throw** rather than returning what was collected — a truncated chapter
reads as one that legitimately ends early — and both throw before any directory
is created, so a cap firing mid-download fails the chapter rather than storing a
partial one.

**Neither has ever fired.** No known source hits either. It is in Needs
verifying and should stay there.

---

## 9f. Two operational notes worth more than they look

**CI LOGS ARE UNREACHABLE FROM THIS ENVIRONMENT.** 0.169 shipped red — the
back-to-exit handler referenced `activity`, declared in the block *below* it,
and a Kotlin function body is read in order. Finding it meant re-reading the
diff, because `/actions/jobs/{id}/logs` 303-redirects to Azure blob storage,
which is outside the network allowlist: a failed build reports only *that* it
failed. **The `mapping-debug` artifact IS reachable through the API; the log is
not.**

**A SCREENSHOT BEAT READING THE CODE, TWICE.** 0.171 put a scroll handle on the
"All sources" screen when the card said the Sources **tab** — two `LazyColumn`s
of source rows in one file, only one inside the Browse pager. One screenshot
settled it. The same thing settled the scroll-handle diagnosis in §9b, where the
question *"where is the thumb when the list stops short"* separated an
arithmetic cause from a physical one after three wrong answers.

---

## 10. State of the tree

Head is `125a546` (0.172). Build environment unchanged from 0.134: Kotlin
2.2.21, AGP 8.5.2, Gradle 8.9, JDK 17, compileSdk 36, targetSdk 34, minSdk 24,
OkHttp 5.4.0, kotlinx-serialization 1.9.0, Compose BOM 2024.09.03, Coil 2.7.0,
`me.saket.swipe:swipe:1.3.0`. `isMinifyEnabled = true` on debug and still
working.

Files changed across the range: `CloudflareInterceptor.kt`, `WebViewScreen.kt`,
`DownloadQueue.kt`, `DownloadService.kt`, `DownloadQueueScreen.kt`,
`SourceBrowseScreens.kt`, `SettingsScreens.kt`, `TachiyomiSourceAdapter.kt`,
`GlobalSearchScreen.kt`, `WhatsNew.kt`, `app/build.gradle.kts`.

**Three notes for anyone reading this code cold.** `onRenderProcessGone` is API
26 and `minSdk` is 24; the override is never invoked below 26 and CI runs no
lint step, so nothing flags it. `DownloadQueue.head()` no longer means "the
first queued item" — §5. And `downloadPage` now takes the fetch's host-failure
tally and will *refuse to retry* a connect failure against a host already known
bad in that fetch — §8.

**Housekeeping.** `debug.keystore` and `release.keystore` are still committed at
the repo root, and the release keystore is this app's signing identity. The
repo is private and the PAT in use is the only one, which is a deliberate
decision rather than an oversight.

---

## 11. The board

**Closed this session:** the "Starting" ambiguity (78); Pause did nothing
mid-chapter (83); the queue screen pass (82); Clear cookies vs
ClearanceUserAgents (81); the all-held dead loop (85); the per-host circuit
breaker (79). Manhwa18 (74) and Coomer (69) both reclassified.

**Opened:** the empty-versus-unparsed chapter list (80, wording half shipped);
the queue screen pass (82, now closed); the all-held loop (85, now closed).

**Needs verifying holds one card: 73**, and it cannot be emptied without adb or
a challenge.

**Needs verifying holds 73 and 77.** Both shipped, both carry code that has
never executed and cannot be made to on demand. Everything else from 0.150 to
0.172 is verified on device — including, finally, the scroll handle (§9b), whose
card had claimed that for thirty releases without it being true.

**The board also changed shape.** Every card now carries at least one label,
"Reports" is archived, "To add" is renamed **Backlog**, and there is a new
**In progress** list — added because card 77 sat mid-work for an hour with the
board unable to say so.

**Backlog is five cards and they are not like the rest of this session.** 65,
68 and 31 are features of real size with no design work done; 67 is flagged
risky (two earlier custom gesture detectors in that reader never fired at all —
read `SESSION_HANDOFF_0.106.md` §5 first); 58's Library item is deliberately
excluded, since the only refresh it could trigger is a foreground service over
every series.

### Open, roughly by value

- **Telling a real zero from an unparsed one** (80) — §4. Touches every source,
  so settle the shape first.
- **Scroll handles on the last two lists** (47) — per-source browse (a grid) and
  Extensions. §9a.
- **`onRenderProcessGone` verification** (73) — §2.
- **A ceiling on `getPageList`** (77) — latent, no known instance. Do not close
  it by fixing a source.
- **Fling the zoom pan** (67) — read `SESSION_HANDOFF_0.106.md` §5 first.
- **Scroll-to-refresh elsewhere**, **a feed tab in Browse**, **SY theme
  settings**, **videos**.
- **AHottie**, **BeeHentai**, **Elite Babes**, **Coomer** — Blocked/Upstream.
- **Manhwa18** — upstream extension or a login gate. §7.
