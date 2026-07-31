# Session handoff — 0.79 to 0.81, and Elite Babes closed

Written 2026-07-31, ~03:30, continuing straight on from `SESSION_HANDOFF_0.78.md`
in the same sitting. Three more releases, and the board item that started the
night is resolved — **as an upstream extension bug, with this app verified clean
around it at every layer.**

Read `SESSION_HANDOFF_0.78.md` first if you haven't. This file assumes it.

---

## 0. Read this before touching anything

**1. The board is clear.** Elite Babes is §3 and it is not actionable here.
Every other item is closed and verified.

**2. 0.80 is the real result of the whole night**, and it has nothing to do with
Elite Babes specifically. The vendored API now implements `getMangaUpdate`,
which is the entry point current extensions use for details and chapters. Any
source adopting it would have failed exactly the same way, and more will.

**3. 0.79 was a wrong fix that shipped.** It was reverted in 0.80. §2 explains,
because the failure mode is worth not repeating.

**4. 0.81's design is deliberately the lesser one.** §2.3 names the better
version and why it wasn't done at 3am.

---

## 1. What shipped

| Release | Commit | What |
|---|---|---|
| 0.79 | `352b61d` | Made the suspend API do the work instead of delegating to Rx. **Wrong diagnosis.** |
| 0.80 | `d7efbd9` | `SMangaUpdate` + `getMangaUpdate`; reverted 0.79 |
| 0.81 | `2654dd8` | Per-series `Mutex` around `getMangaUpdate` |

`b5181d4` (0.78, exception types in error messages) built and installed fine —
its CI result was unknown when 0.78's handoff was written. It is what made the
rest of this possible: `Could not list chapters — UnsupportedOperationException`
is the string that started the chain.

---

## 2. The three releases

### 2.1 — 0.79, and why it was wrong

`HttpSource.getChapterList` was `fetchChapterList(manga).awaitSingle()` — the
suspend API delegating to the deprecated Rx one. That looked like the bug:
modern extensions implement the suspend side and stub the Rx side. So 0.79 made
all seven entry points do request-await-parse directly.

**It didn't work, because the request/parse pair is stubbed too.** Elite Babes
declares `chapterListRequest`, `chapterListParse`, `mangaDetailsRequest`,
`mangaDetailsParse`, `pageListRequest` and `pageListParse` and **every one is
`throw UnsupportedOperationException()`**. Both routes end at the same stub.

Worse, 0.79 changed the fetch path for **all seven entry points across all 95
sources** on that hypothesis, verified by one spot check. An extension
overriding `fetchPopularManga` with anything other than request+parse would have
silently changed behaviour. That risk was unjustified and 0.80 reverted it.

**The lesson is not "check harder".** It is that a change whose blast radius is
every source should not ride along with a fix for one source. The revert was
possible because the two were separable; they should not have been in the same
commit.

### 2.2 — 0.80, the actual fix

Current extensions fetch details and chapters in **one** request and return both:

```kotlin
class SMangaUpdate(val manga: SManga?, val chapters: List<SChapter>?)

open suspend fun getMangaUpdate(
    manga: SManga, chapters: List<SChapter>,
    fetchDetails: Boolean, fetchChapters: Boolean,
): SMangaUpdate
```

**The constructor signature is fixed by binary compatibility.** Extensions are
compiled against their own copy and call `SMangaUpdate.<init>(SManga, List)`
directly, so reordering or adding a parameter gives every such extension a
`NoSuchMethodError`. It was read out of a real dex, not guessed — see §4.

`getChapterList` and `getMangaDetails` route through it. **The default
implementation is the old Rx path**, so an extension that has never heard of
`getMangaUpdate` keeps working untouched.

This is the piece that matters beyond tonight. It is not an Elite Babes fix; it
is the app catching up to an API change.

### 2.3 — 0.81, the race

0.80 worked, and then the same series would show chapters or an
`IllegalStateException` depending on timing. The extension guards with
*"getMangaUpdate must not be called concurrently for same manga"*, which is
reasonable — the whole point is one request for both halves.

This app opens a series by fetching details and chapters **at the same time**, so
it called `getMangaUpdate` twice concurrently for the same manga. 0.81 adds a
per-series `Mutex` keyed on `manga.url`.

**The better fix is one call, not two serialised ones.**
`getMangaUpdate(fetchDetails = true, fetchChapters = true)` is what the API is
shaped for and halves the requests. It needs the app's own `Source` interface to
grow a way to ask for both at once, which is a real piece of work and was not a
3am job. The lock is correct and wasteful; replacing it is a clean, contained
task for whoever picks this up.

---

## 3. Elite Babes — closed, upstream

**The original board item, "Elite Babes chapters have no pages", was never an app
bug.** It was reproduced by browsing **Popular**, and:

| What the extension does | State |
|---|---|
| Popular page 1 → `https://www.elitebabes.com` (site homepage) | **Stale** — `.list-gallery` there is category tiles now, not galleries |
| Popular page 2+ → `…/archive/…` | Fine |
| Latest → `…/updates/sort/newest/mpage/N/` | Fine |
| Browse parse: `.list-gallery:not(.static) figure:not(:has(a[href*=/video/]))` | Fine on listing pages |
| Pagination: `document.selectFirst(".pagination-a li.next") != null` | **Stale** — nothing paginates past a page or two |
| Page list: `.list-gallery a[href^=https://cdn.]` | **Works** — pages render |

So every "series" opened from Popular was a **category page**, carrying one
"Gallery" chapter pointing back at itself. A category page has no images, so the
page list was legitimately empty. The nav sidebar rendering as the description
and "Trending / Newest / Popular" appearing as genre chips were the same thing
showing through.

**Verified: reading works via Latest.** Galleries open and pages render. Browsing
is capped at roughly one page because of the pagination selector.

**Nothing here is fixable from this app.** URLs and selectors live in the
extension. The options are upstream: report it to Keiyoushi, patch and build the
extension, or use Latest. The table above is a complete bug report.

**Re-check when the extension updates.** There is no update as of tonight.

---

## 4. The method that finally worked, and the mistake it corrects

**An extension APK's `classes.dex` can be decompiled, and everything above came
from doing that.**

```bash
pip install --break-system-packages androguard
curl -sL https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json -o idx.json
grep -o '"apkUrl": *"[^"]*"' idx.json | grep -i <source>
curl -sL "<url>" -o ext.apk && unzip -o ext.apk 'classes*.dex' -d ext
```

```python
from loguru import logger; logger.remove()
from androguard.misc import AnalyzeDex
h, d, dx = AnalyzeDex("ext/classes.dex")
for m in dx.get_methods():
    em = m.get_method()
    if "<source>" in str(em.get_class_name()):
        print(em.get_name(), em.get_descriptor())
        print(em.get_source())          # decompiled body
```

This gives the exact method list, which ones are stubs, every selector, every
URL, and constructor signatures precise enough to implement against.

**Grep on the dex is not the same thing and misled this session twice.** Strings
show that a name is *present*, not whether a method throws. §2.1's wrong fix came
from grepping and assuming; the answer came from disassembling.

**And twice tonight something was called unfixable while the answer sat in a file
already downloaded.** First "the extension's page parse returns nothing, nothing
to be done" — the parse was fine, Popular's URL was wrong. Then "Popular can't
work" — only page 1 is broken. Both times the extension's code was on disk and
unread. **When the artifact is in hand, read it before concluding.**

---

## 5. The bug board

| Item | State |
|---|---|
| Roku Hentai has no covers | **Closed** — 0.70 |
| Extensions tab reloads every time | **Closed** — 0.70 |
| The app takes some time to open | **Closed** — 0.72, 3 s verified |
| Downloads tab hangs on open | **Closed** — 0.74 |
| Elite Babes chapters have no pages | **Closed — upstream** (§3) |

**The board is empty.** First time in this project's recorded history.

Still open, not board items: bug 3 (counter race) and bug 6 (`done` doesn't
reconcile), 547 unaccounted, low priority per `SESSION_HANDOFF_0.72.md` §5. The
manifest theme. And **open thread 1, the reader, now twelve sessions untouched**
— with the board clear, it is the obvious next thing.

---

## 6. State of the tree

- `b5181d4` — 0.78. Built and installed; its result was unknown when 0.78's
  handoff was written.
- `613a551` — docs through 0.78.
- `352b61d` — 0.79. **Reverted by 0.80.**
- `d7efbd9` — 0.80. `SMangaUpdate` + `getMangaUpdate`.
- `2654dd8` — 0.81. Per-series lock. **Verified: chapters list consistently,
  pages render.**

Build environment unchanged from 0.78: Kotlin 2.2.21, AGP 8.5.2, Gradle 8.9,
JDK 17, compileSdk 36, targetSdk 34, minSdk 24, OkHttp 5.4.0.

Library **3571** entries. Device state: defaults.

---

## 7. If you only do one thing

Nothing is urgent. The board is clear and the app is in the best shape it has
been in.

When you next sit down: **open thread 1, the reader.** Twelve sessions untouched,
still the largest untested surface in the app — paged right-to-left, grayscale
and invert together, the chapter picker, whether settings survive reopening a
chapter. Nothing there is a known bug, which is precisely the problem.

The contained alternative, if you want something smaller: replace 0.81's lock
with a single `getMangaUpdate(fetchDetails = true, fetchChapters = true)` call
(§2.3). One interface change, halves the requests on every series open.
