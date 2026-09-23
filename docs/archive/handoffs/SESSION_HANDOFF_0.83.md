# Session handoff — 0.73 to 0.83, the night the API caught up

Written 2026-07-31, ~04:00. **This file replaces `SESSION_HANDOFF_0.67` through
`SESSION_HANDOFF_0.81`, all of which were deleted after being folded into
`PROJECT_HANDOFF.md`.** If you need them, they are in git history.

Eleven releases and six red CI runs in one sitting, starting from one board item
that turned out not to be a bug in this app at all.

---

## 0. Read this before touching anything

**1. The bug board is empty.** First time in this project's recorded history.

**2. The headline is not the board item.** About **one extension in eight** had
outgrown this app's vendored API. They were failing silently or with a crash, and
would have arrived one baffling report at a time over the coming months. §2 is
what was added; §5 is the scan that says nothing else is missing.

**3. There is now a way to check before shipping.** §5. An extension's APK can
be decompiled in about ten minutes and diffed against what `:source-api`
provides. Four of tonight's failures were sitting in that list.

---

## 1. What was wrong, in one paragraph

An extension update broke Elite Babes. Chasing it revealed that **every pinned
dependency and the vendored source API were behind what current extensions are
built against** — and that this had nothing to do with Elite Babes. OkHttp 4.12
(effectively 5.0.0-alpha.12), Kotlin 2.0.20, kotlinx-serialization 1.7.3, no
`getMangaUpdate`, no `SManga.memo`. Each gap broke a different set of sources and
each was invisible until an extension happened to need it. All are fixed. The
original board item turned out to be an upstream extension bug (§4).

---

## 2. What was added to the API

### `getMangaUpdate` and `SMangaUpdate` (0.80)

Current extensions fetch a series' details and chapter list in **one** request:

```kotlin
class SMangaUpdate(val manga: SManga?, val chapters: List<SChapter>?)

open suspend fun getMangaUpdate(
    manga: SManga, chapters: List<SChapter>,
    fetchDetails: Boolean, fetchChapters: Boolean,
): SMangaUpdate
```

Sources that adopt it declare the legacy `chapterListRequest`/`chapterListParse`
and `mangaDetailsRequest`/`mangaDetailsParse` as
`throw UnsupportedOperationException()`. Before 0.80 they browsed perfectly and
failed on every series — the request/parse pair was a stub either way.

`HttpSource.getMangaUpdate`'s **default body is the old Rx path**, so extensions
that have never heard of it are unaffected. `getChapterList` and
`getMangaDetails` route through it.

**The constructor signature is fixed by binary compatibility.** Extensions call
`SMangaUpdate.<init>(SManga, List)` directly; a changed shape means
`NoSuchMethodError` in every one of them. It was read out of a dex.

### `SManga.memo` / `SChapter.memo` (0.83)

`var memo: JsonObject?` on both. Scratch space the extension owns — Asura Scans
stashes the JSON it parsed a series from and reads it back in `getMangaUpdate`
rather than re-fetching. Without it: `NoSuchMethodError: No interface method
setMemo(...)`, presenting as a source that lists nothing.

**Null is normal on anything this app rebuilds** rather than receives —
`restoreSeries` for a library entry, `rehydrateChapter` for a queued download.
Only the extension can set one. **If a source works from Browse and fails from
Library, this is the first thing to suspect.** Not yet observed.

### The per-series lock (0.81)

An extension may refuse two concurrent `getMangaUpdate` calls for the same manga,
and Elite Babes does. This app opens a series by fetching details and chapters
*simultaneously*, so they raced and the same series worked or didn't depending on
timing. `HttpSource` now serialises them with a `Mutex` keyed on `manga.url`.

**The better fix is one call, not two serialised ones.**
`getMangaUpdate(fetchDetails = true, fetchChapters = true)` is what the API is
shaped for and halves the requests. It needs the app's own `Source` interface to
grow a way to ask for both at once. **This is the best-value contained task left
in the project.**

### `LinkageError` at the boundary (0.77)

An extension built against a newer API raises `NoClassDefFoundError` /
`NoSuchMethodError` — `Error`, not `Exception` — which `catch (e: Exception)`
lets through to a process death.

0.75 widened eight call sites; 0.76 widened both services and added
`CoroutineExceptionHandler`s; **it still crashed from a path neither found, and
reading every `launch` site did not turn it up.** 0.77 converts `LinkageError` to
`IOException` inside `TachiyomiSourceAdapter.onSourceThread` — the one boundary
every extension call crosses — which makes the missed path irrelevant.

**Enumerating the ways out of a hazard is unbounded; closing the one way in is a
single edit.** Reach for the boundary first when foreign code is involved.

---

## 3. Dependencies, and the cascade

| | Was | Now | Why forced |
|---|---|---|---|
| OkHttp | 4.12.0 declared, 5.0.0-alpha.12 resolved | **5.4.0** | Extensions construct `okhttp3.CompressionInterceptor` (OkHttp 5.2.0+) and `okhttp3.zstd.Zstd` (`okhttp-zstd`) |
| Kotlin | 2.0.20 | **2.2.21** | Every OkHttp ≥5.2.0 is built with Kotlin 2.2.x; a 2.0 compiler refuses 2.2 metadata |
| compileSdk | 34 | **36** | `okhttp-android:5.4.0`'s AAR metadata demands it |
| kotlinx-serialization | 1.7.3 | **1.9.0** | From 1.8 the runtime gives `GeneratedSerializer.typeParametersSerializers()` a default, so the plugin stopped emitting it; on 1.7.3 it is still abstract → `AbstractMethodError` |

Four things worth keeping:

- **`:app` had been running OkHttp 5 while its build file claimed 4.** Gradle
  resolves conflicts to the highest and `:source-api` declared the 5.0.0-alpha
  BOM. Anyone reading the network code against the 4.x docs was reading the
  wrong docs.
- **All three Kotlin plugin versions move together.** `kotlin.android` and
  `kotlin.plugin.compose` are in the root build file; **`kotlin.plugin.serialization`
  is in `source-api/build.gradle.kts`** and is easy to miss.
- **serialization 1.9.0 specifically, not the newest.** Releases are pinned to a
  Kotlin version: 1.9.0 is built on Kotlin 2.2.0 and reads under 2.2.21; 1.10.0
  is built on Kotlin 2.3.0 and does not. Same wall, one layer up.
- **compileSdk 36 exceeds what AGP 8.5.2 was tested against**, acknowledged by
  `android.suppressUnsupportedCompileSdk=36`. If a build ever fails inside AAPT2
  or resource linking rather than in our own code, that suppression has stopped
  covering it and the real upgrade is AGP 8.11+ with Gradle 8.13+, which also
  moves the `gradle-version` pin in `.github/workflows`.

Also fixed along the way: three OkHttp jars ship an identical
`META-INF/versions/9/OSGI-INF/MANIFEST.MF`, excluded by exact path in `:app`'s
`packaging` block — deliberately not a glob over `META-INF`, which would silently
drop service-loader registrations later.

---

## 4. Elite Babes — closed, upstream

**The board item that started the night was never an app bug.** It was
reproduced by browsing **Popular**:

| What the extension does | State |
|---|---|
| Popular page 1 → `https://www.elitebabes.com` (site homepage) | **Stale** — `.list-gallery` there is category tiles now, not galleries |
| Popular page 2+ → `…/archive/…` | Fine |
| Latest → `…/updates/sort/newest/mpage/N/` | Fine |
| Browse parse `.list-gallery:not(.static) figure:not(:has(a[href*=/video/]))` | Fine on listing pages |
| Pagination `document.selectFirst(".pagination-a li.next") != null` | **Stale** — nothing pages past one or two |
| Page list `.list-gallery a[href^=https://cdn.]` | **Works** |

Every "series" opened from Popular was a **category page** carrying one "Gallery"
chapter pointing back at itself, and a category page has no images. The nav
sidebar rendering as a description was the same thing showing through.

**Verified: reading works via Latest**, pages render. Browsing is capped at
roughly one page by the stale pagination selector.

Not fixable here — URLs and selectors live in the extension. The table above is a
complete bug report for Keiyoushi. **Re-check when the extension updates; there
is no update as of tonight.**

---

## 5. The scan — how to answer "what else is missing" in ten minutes

An extension APK's `classes.dex` can be decompiled. This is how everything in §2
was found, and it is the single most reusable thing from this session.

```bash
pip install --break-system-packages androguard
curl -sL https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json -o idx.json
grep -o '"apkUrl": *"[^"]*"' idx.json | cut -d'"' -f4 > urls.txt
# jsdelivr URLs also resolve under raw.githubusercontent.com/keiyoushi/extensions/repo/
curl -sL "<url>" -o ext.apk && unzip -o ext.apk 'classes*.dex' -d ext
```

```python
from loguru import logger; logger.remove()
from androguard.core.dex import DEX
d = DEX(open("ext/classes.dex","rb").read())
for m in d.get_methods():                     # every model member it calls
    cn = str(m.get_class_name())
    if "/source/model/" in cn or "/source/online/HttpSource" in cn:
        print(cn.split("/")[-1], m.get_name(), m.get_descriptor())

from androguard.misc import AnalyzeDex        # and the decompiled bodies
h, d, dx = AnalyzeDex("ext/classes.dex")
for m in dx.get_methods():
    em = m.get_method()
    if "<source-package>" in str(em.get_class_name()):
        print(em.get_name()); print(em.get_source())
```

Anything printed that `SManga.kt` / `SChapter.kt` / `HttpSource.kt` doesn't
provide is a source that will fail. **Grepping the dex is not the same thing** —
strings show that a name is *present*, not whether a method is a stub that
throws.

### Result of running it, 2026-07-31

**23 extensions sampled at random from 1365.** Every model member they call is
now provided.

- **`SMangaUpdate`: 3 of 23 (~13%)** — extrapolating, roughly **175 extensions**
  would have failed the way Elite Babes and Asura Scans did.
- **`memo`: 0 of 23.** Asura uses it, so it sits below what a 1.7% sample
  catches. A gap at that frequency could still be hiding.
- Everything else — `setUrlWithoutDomain` (20/23), `prepareNewChapter`,
  `Filter.TriState`, `Filter.Sort`, `update_strategy`, `scanlator`,
  `chapter_number` — already present.

**Widen the sample if you want more confidence.** It costs minutes and it is the
only way to find the next `memo` before a user does.

---

## 6. Two mistakes worth not repeating

**1. 0.79 was a wrong fix that shipped.** It changed the fetch path for all seven
entry points across every source, on the theory that the request/parse pair was
what extensions override. Both routes end at the same stub, so it fixed nothing
and risked everything; 0.80 reverted it. **A change whose blast radius is every
source must not ride along with a fix for one source.**

**2. Twice, something was called unfixable while the answer sat in a file already
downloaded.** First "the extension's page parse returns nothing, nothing to be
done" — the parse was fine, Popular's URL was wrong. Then "Popular can't work" —
only page 1 is broken. Both times the extension's code was on disk and unread.
**When the artifact is in hand, read it before concluding.**

Related, and cheaper: **the crash was only diagnosed once the stack trace was
read.** MIUI's "keeps stopping" dialog has a **View summary** link containing the
full trace. Tap that, not Report.

---

## 7. Also shipped tonight

- **Cold start 30 s → 3 s** (0.72). The library screen was asking the Downloads
  tab's question — `DownloadIndex.list()` sizes every downloaded chapter and
  scans the whole library — to decide which covers get a badge. 19.9 s of every
  cold start.
- **Downloads tab no longer hangs** (0.74). A 0.72 regression: the library screen
  had been warming those memos by accident. The recovery scan is now stamped as a
  one-time migration and the read is on IO.
- **Source errors name their exception type** (0.78). `Could not list chapters —
  UnsupportedOperationException` is the string that unlocked everything after it.
  Before, a null message rendered identically to a series that genuinely had none.
- **A startup icon** instead of a blank rectangle (0.72).

---

## 8. State of the tree

Head is `c892ee8` (0.83) plus whatever docs commit carries this file.

Build environment: Kotlin **2.2.21**, AGP 8.5.2, Gradle 8.9, JDK 17,
compileSdk **36**, targetSdk 34, minSdk 24, OkHttp **5.4.0**,
kotlinx-serialization **1.9.0**, Compose BOM 2024.09.03, Coil 2.7.0.

Library **3571** entries. Device state: defaults.

**Verified on device tonight:** 3 s cold start with badges on; Downloads tab
responsive then instant; Elite Babes reading via Latest; SpyFakku browsing;
Asura Scans browsing; an unrelated source still working after the OkHttp move.

---

## 9. What's next

**The board is empty**, so this is a genuine choice rather than a queue.

1. **Open thread 1 — the reader.** Twelve sessions untouched and still the
   largest untested surface: paged right-to-left, grayscale and invert together,
   the chapter picker, whether settings survive reopening a chapter. Nothing
   there is a *known* bug, which is exactly the problem.
2. **One `getMangaUpdate` call instead of two** (§2). Contained, halves the
   requests on every series open, removes the lock.
3. **Widen the extension scan** (§5). Minutes, and it is the only proactive
   defence against the next `memo`.
4. **The refresh backlog** — bug 3 (counter race) and bug 6 (`done` doesn't
   reconcile), 547 unaccounted between them. Deliberately low priority: the
   sweeps have run and the capability they were blocking is delivered.
5. **The manifest theme.** `res/values/themes.xml` now exists, which makes it
   cheaper and still not one line — light/dark is an in-app preference, not the
   system setting, so a `values-night` variant cannot see it.

---

## 10. If you only do one thing

Nothing is urgent. If you want the highest value per hour, it is the reader —
it has been the largest untested surface in this app for twelve sessions, and
with the board clear there is nothing left to hide behind.
