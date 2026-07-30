# Session handoff — 0.73 to 0.78, one extension update and everything it moved

Written 2026-07-31, roughly 02:30, at the end of the same sitting that produced
0.72. Six releases and eleven commits, four of which exist only because CI went
red. All of it started from one board item — "Elite Babes chapters have no
pages" — and none of it has fixed that item.

Companion to `SESSION_HANDOFF_0.72.md`, which is a closed record. Everything it
says still stands; this file is what happened next.

---

## 0. Read this before touching anything

**1. The app is in good shape. One source is broken.** Cold start is 3 s, the
Downloads tab no longer hangs, the toolchain is current, and a failing extension
can no longer close the app. Elite Babes lists no chapters. That is the whole
outstanding problem and it is one source out of 95.

**2. 0.78 is pushed and its CI result was never seen.** `b5181d4`. It is a
one-function diagnostic change and low risk, but nobody watched the run. Check
it before assuming the tree is green.

**3. Do not start by guessing at Elite Babes.** §4 is a method note that cost
this session several hours and two unnecessary releases. Read it first.

---

## 1. What actually happened, in order

The user updated the Elite Babes extension. That is the entire cause. Everything
below is consequence.

| Release | Commit | What it did | Outcome |
|---|---|---|---|
| 0.73 | `ea7867c` | Wire `UncaughtExceptionInterceptor` into the shared client | Built. Error message *changed* — partial fix confirmed |
| 0.74 | `4c71d4b` | Real `UserAgentInterceptor`; recovery scan stamped as one-time; Downloads tab on IO | Built. Downloads tab fixed. Elite Babes now **crashed** instead of erroring |
| 0.75 | `7664a77` | `catch (Throwable)` at 8 source-call sites | Built. **Still crashed** |
| 0.76 | `11fb020` | OkHttp 5.4.0 + service catches + `CoroutineExceptionHandler` | CI red — Kotlin metadata |
| 0.76 | `4991127` | Kotlin 2.0.20 → 2.2.21 | CI red — AAR metadata wants compileSdk 36 |
| 0.76 | `1c04024` | compileSdk 34 → 36 | CI red — duplicate OSGi metadata in three jars |
| 0.76 | `2a86c4d` | Packaging exclude | **Green.** Crash moved from `CompressionInterceptor` to `Zstd` |
| 0.77 | `7e6b0a5` | `LinkageError` → `IOException` at the adapter boundary; `okhttp-zstd` | CI red — orphaned `return@withContext` labels |
| 0.77 | `a7b9489` | Relabel | **Green. Browse works, no crash** |
| 0.78 | `b5181d4` | Name the exception type when a message is null | **CI result unknown** |

---

## 2. The five assertions, and how they were found

The updated extension checks the shape of the app's default OkHttp client, by
`javaClass.simpleName`, inside its own lazy `client` property:

| Assertion | State |
|---|---|
| `UncaughtExceptionInterceptor` must be present | Fixed, 0.73 |
| `UserAgentInterceptor` must be present | Fixed, 0.74 |
| `CloudflareInterceptor` must be present | Already satisfied |
| `BrotliInterceptor` must **not** be present | Already satisfied |
| `IgnoreGzipInterceptor` must **not** be present | Already satisfied |

`UncaughtExceptionInterceptor` had been vendored in `:source-api` since the API
was imported and **never wired up** — zero references in the tree. Its own KDoc
said it should be the first interceptor in the client. `UserAgentInterceptor`
was vendored too and deliberately *not* used: `NetworkHelper` inlined the same
logic as a lambda, with a comment explaining that this avoided depending on the
class's constructor signature. A lambda's `simpleName` is not
`UserAgentInterceptor`, so the check could not see it. 0.74 uses the real class
and keeps the clearance-UA and Accept-header logic in a separate interceptor
placed *before* it, which reproduces the old behaviour exactly.

**All five were visible at once, in the extension's own APK**, and reading them
that way is §4.

---

## 3. The toolchain cascade

Getting past the assertions let the extension reach classes the app did not
ship. Each fix forced the next:

1. **`okhttp3.CompressionInterceptor`** — the extension builds its client as
   `CompressionInterceptor(Zstd, Gzip)`. That class landed in **OkHttp 5.2.0**
   (2025-10-07).
2. **OkHttp ≥ 5.2.0 is built with Kotlin 2.2.x**, and a Kotlin 2.0 compiler
   refuses 2.2 metadata outright. There is no version of OkHttp that has the
   class and is readable by Kotlin 2.0, so **Kotlin 2.0.20 → 2.2.21** was
   forced, not chosen. All three Kotlin plugin versions move together — the
   serialization one is declared in `source-api/build.gradle.kts`, not the root.
3. **`okhttp-android:5.4.0` requires compileSdk 36.** AGP 8.5.2's maximum
   *recommended* compileSdk is 34, which is a warning rather than a limit, so
   **compileSdk 34 → 36** with `android.suppressUnsupportedCompileSdk=36` in
   `gradle.properties`. `targetSdk` stays 34 — no runtime behaviour changed.
   AGP and Gradle did **not** have to move. If a future bump fails inside AAPT2
   or resource linking, that suppression has stopped covering it and the real
   upgrade is AGP 8.11+ with Gradle 8.13+, which also moves the `gradle-version`
   pin in `.github/workflows`.
4. **Three OkHttp jars ship an identical
   `META-INF/versions/9/OSGI-INF/MANIFEST.MF`** — logging-interceptor,
   okhttp-brotli, okhttp-dnsoverhttps — and the merge task refuses to pick one.
   Excluded by exact path in `:app`'s `packaging` block. Deliberately not a glob
   over `META-INF`: a broad exclude would silently drop service-loader
   registrations the next time a dependency lands.
5. **`okhttp3.zstd.Zstd`** — a separate `okhttp-zstd` artifact, added to
   `:source-api`. Nothing in this app wants zstd; an extension names it.

**An important thing learned on the way:** `:app` declared `okhttp:4.12.0` while
`:source-api` declared `okhttp-bom:5.0.0-alpha.12`. Gradle resolves version
conflicts to the **highest**, so the app had been running OkHttp 5 for a long
time while its build file claimed 4. Anyone reading the network code against the
4.x docs was reading the wrong docs. Both modules now pin `5.4.0` and the
duplication is commented in both.

---

## 4. The method note. Read this before the next extension problem.

**Everything in §2 and §3 was readable from the extension's APK in one pass,
before writing a single line of code.** It was instead discovered one crash at a
time across six releases.

The technique, which needs no root, no adb and no device:

```bash
curl -sL https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json -o idx.json
grep -o '"apkUrl": *"[^"]*"' idx.json | grep -i <source>
curl -sL "<the url>" -o ext.apk
unzip -o ext.apk 'classes*.dex' -d ext
grep -ao "[ -~]\{6,\}" ext/classes.dex | grep -i interceptor | sort -u
grep -ao "Lokhttp3/[A-Za-z0-9/$]*;" ext/classes.dex | sort -u
grep -ao "Leu/kanade/tachiyomi/[A-Za-z0-9/$_]*;" ext/classes.dex | sort -u
```

The APK must be unzipped first — strings live in `classes.dex` and grepping the
zip finds nothing. A dex lists **every class it references**, so diffing that
against what the app actually ships produces the whole gap at once.

Doing this at the start would have shown all five assertions, both missing
OkHttp classes, and `SMangaUpdate` together. It would have collapsed 0.73
through 0.77 into one release.

Two related failures worth naming, because they are the same mistake:

- **I inferred `SMangaUpdate` was the crash and said so with confidence.** It
  was a reasonable inference from the dex and it was wrong — the actual stack
  trace named `CompressionInterceptor`. `SMangaUpdate` is genuinely missing from
  `:source-api` and genuinely referenced by the extension; it simply is not what
  fired. **An inference from available evidence is not an observation**, and
  0.70 §1 already records this exact lesson about the covers fix.
- **The crash was diagnosed only when the stack trace was read.** MIUI's "keeps
  stopping" dialog has a **View summary** link containing the full trace. Tap
  that, not Report — Report sends it to Xiaomi and nobody else sees it.

---

## 5. The boundary, and why chasing catch sites was the wrong shape

An extension is a separately-compiled APK loaded through a `PathClassLoader`
against a vendored copy of the API. When it was built against a newer API, the
mismatch does not arrive as an exception — it arrives as a **`LinkageError`**:
`NoClassDefFoundError`, `NoSuchMethodError`, `AbstractMethodError`. Those are
`Error`, not `Exception`, so `catch (e: Exception)` lets them through and the
process dies.

0.75 widened eight catch sites in `MainActivity` and `BrowseScreen`. It still
crashed. 0.76 widened `DownloadService` and `LibraryRefreshService` and added a
`CoroutineExceptionHandler` to both scopes. **It still crashed**, from a
`StandaloneCoroutine` on `Dispatchers.IO` — and reading every `launch` site in
both modules did not find which one.

**That path was never located, and 0.77 made it irrelevant.**
`TachiyomiSourceAdapter` is the single boundary every extension call crosses.
Its nine `withContext(Dispatchers.IO)` blocks are now `onSourceThread`, which
converts `LinkageError` into `IOException` right there. An extension is now
*unable* to raise a non-`Exception` into app code, so every ordinary
`catch (e: Exception)` in the tree is correct again — including the one nobody
found.

**The lesson is about shape, not about this bug.** Enumerating the ways out of a
hazard is unbounded and was wrong twice; closing the one way in is a single
edit. Reach for the boundary first when foreign code is involved.

The catches added in 0.75 and 0.76 are kept. They are correct, they cost
nothing, and they are the right thing if a future path bypasses the adapter.

---

## 6. Where Elite Babes actually stands

**Browse works. Covers load. No crash.** Opening a series shows its cover,
title and "Add to library", then **"Could not list chapters" and 0 chapters**.

That message is the *fallback* string, which means the exception carried a null
message — and it is not the boundary's `IOException`, which always has text. So
something else is throwing and the UI was saying nothing about what. 0.78 fixes
exactly that: errors now render as `Could not list chapters — <Type>` and
include the cause when there is one.

**The next step is to read that type.** Open the series on 0.78 and screenshot
the red line.

**A timeline fact that matters and should not be lost:** before the extension
was updated, Elite Babes **listed chapters fine** and only pages were empty.
That is the original board item. After the update, chapter listing fails too. So
the newer extension is strictly worse against this app, and the original bug is
now *behind* a newer one rather than beside it.

**The cheapest experiment nobody has run: downgrade the extension.** If the
previous Elite Babes version lists chapters on 0.78, the problem is entirely in
the new extension and not in the app, and the whole question becomes whether to
implement lib 1.6 properly or to pin the source to an older build.

---

## 7. Still claimed and not implemented

`ExtensionLoader.LIB_VERSION_MAX` is **1.6**, and the comment above it says to
widen it only once the API surface is implemented. It is not implemented. The
extension's dex references, among others:

- `eu.kanade.tachiyomi.source.model.SMangaUpdate` — **absent from
  `:source-api`**
- `getMangaUpdate`, `getMangaByUrl`, `fetchRelatedMangaList`,
  `getDisableRelatedMangas`

It is deliberately still 1.6. Narrowing to 1.5 would also refuse 1.6 extensions
that work — at least one on this device updated to 1.6 and was fine — and the
gate reads what an extension *claims*, so it can refuse an honest mismatch and
cannot see a dishonest one. With the boundary conversion in place, an
unsupported extension now degrades to a named error instead of a crash, which is
what the version gate was trying to achieve anyway.

**`SMangaUpdate` is not in `keiyoushi/extensions-lib` on `main`.** That repo was
checked. Keiyoushi are building against something not published there, so
implementing §7 means finding the real definition first — inventing one produces
a *different* `LinkageError` that looks identical.

---

## 8. What else shipped, and is verified

Not everything this session was Elite Babes.

- **The Downloads tab no longer hangs.** 0.72 stopped the library screen calling
  `DownloadIndex.list()`, which had been warming the memos by accident; the
  Downloads tab was then left paying the full cost cold, on the composition
  thread. 0.74 stamps the recovery scan as a one-time migration and moves the
  read to IO behind "Reading the download folder…". **Verified: first open
  responsive, second instant.**
- **A sanity check on an unrelated source passed** after the OkHttp move —
  browsing and covers both work on 5.4.0. Coil rides the same client and was the
  main risk of that bump.
- **Another extension was updated and kept working**, which is what kept the
  five assertions scoped to Elite Babes rather than to everything.

---

## 9. The bug board

| Item | State |
|---|---|
| Roku Hentai has no covers | **Closed** — 0.70 |
| Extensions tab reloads every time | **Closed** — 0.70 |
| The app takes some time to open | **Closed** — 0.72, 3 s verified |
| Downloads tab hangs on open | **Closed** — 0.74, verified (a 0.72 regression) |
| Elite Babes chapters have no pages | **Open, and now behind a second failure** — §6 |

Still open from the 0.67 review: **bug 3** (counter race) and **bug 6** (`done`
doesn't reconcile), 547 unaccounted between them. Low priority per
`SESSION_HANDOFF_0.72.md` §5.

Still open, older: the manifest theme (0.72 §6 — `res/values/themes.xml` now
exists, which makes it cheaper and still not one line), and **open thread 1, the
reader, now eleven sessions untouched**.

---

## 10. State of the tree

- `70f56a8` — docs, 0.72.
- `ea7867c` `4c71d4b` `7664a77` — 0.73, 0.74, 0.75.
- `11fb020` `4991127` `1c04024` `2a86c4d` — 0.76 across four commits; only the
  last built.
- `7e6b0a5` `a7b9489` — 0.77; only the last built.
- `b5181d4` — **0.78. CI result never seen.**

Build environment moved this session: **Kotlin 2.2.21** (was 2.0.20),
**compileSdk 36** (was 34), **OkHttp 5.4.0** (was a 4.12.0 declaration
resolving to 5.0.0-alpha.12). AGP 8.5.2, Gradle 8.9, JDK 17 and minSdk 24 are
unchanged.

Library is **3571** entries. Device state: defaults, badge on.

---

## 11. Delivery notes, all learned the hard way tonight

For `PROJECT_HANDOFF.md` §1, and partly folded in there already:

- **A heredoc ends an `&&` chain.** After the first `EOF` the shell starts a
  fresh command list, so `git commit && git push` runs whether or not the
  earlier steps worked. Keep heredocs in their own paste.
- **`sed -i` is not idempotent.** A retry wants `git checkout <file>` first.
- **Multi-module pushes need per-file destinations.** `:source-api` and `res/`
  are outside the `*.kt` loop's target directory, so name files explicitly
  rather than globbing.
- **Termux has no `/tmp`** — it is `$TMPDIR`, or just use `~`. A failed `cd /tmp`
  leaves downloads in the repo, where `git add -A` will happily commit them.
- **Termux cannot see other packages.** `pm list packages` returns nothing and
  `pm path` fails: no `QUERY_ALL_PACKAGES` for the shell's UID. The app has it;
  the shell does not. Download the APK from the repo instead (§4).
- **`index.min.json` is a two-entry stub** — "Outdated App" and "Update to
  Mihon". The real index is `index.json`. This is already documented in
  `ExtensionManager.parseIndex`'s KDoc and was walked into anyway.
- **After any mechanical lambda rename, grep for `return@`.** 0.77 renamed nine
  `withContext(Dispatchers.IO)` headers to `onSourceThread` and left ten
  `return@withContext` labels pointing at a builder that no longer existed. One
  grep, one CI round trip saved.

---

## 12. If you only do one thing

Open an Elite Babes series on 0.78 and read the red line. It names the exception
type now. Everything about what to do with that source — implement lib 1.6,
downgrade the extension, or drop it — depends on that one string, and it is the
only input nobody has.
