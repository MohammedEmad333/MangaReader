# Yomu / MangaReader — Project Handoff

Context document for continuing work in a fresh chat. Last updated 2026-07-27
(supersedes the earlier version of this file).

---

## 1. What this project is

A native Kotlin/Compose Android manga reader (`com.mangareader.app`, app label
**Yomu**) that loads **Tachiyomi/Mihon extension APKs** as content sources.

- **GitHub:** https://github.com/MohammedEmad333/MangaReader
- **Builds via GitHub Actions**, not locally. No Android Studio in the loop.

### How the user works

There are now **two working copies**, either of which can push:

| Where | Path | Notes |
|---|---|---|
| Windows PC | `E:\MangaReader` | cmd.exe |
| Phone (Termux) | `~/MangaReader` | ext4, **not** `/sdcard` — see §5 |

Edit → push → GitHub Actions builds the APK and publishes it to the `latest`
prerelease → install on the phone.

**They ask for a ready-to-paste push command at the end of any change.**

Windows:
```
cd /d E:\MangaReader && git add -A && git commit -m "message" && git push
```

Termux — **files download as `.kt.txt`, so strip the suffix in the copy** (see
§5). This loop handles any number of files at once:
```
cd ~/MangaReader && for f in ~/storage/downloads/*.kt.txt; do cp -v "$f" "app/src/main/java/com/mangareader/app/$(basename "$f" .txt)"; done && git diff --stat && git add -A && git commit -m "message" && git push
```

`cp -v` prints each copy so the count can be eyeballed before committing, and
the `git diff --stat` in the middle prints the expected insertion count; if a
copy silently failed the `&&` chain aborts at the empty commit instead of
pushing nothing.

**Careful with the glob**: `*.kt.txt` takes *everything* in Downloads, including
stale files from an earlier session, which would quietly revert them. Check the
`cp -v` list.

**Whichever copy wasn't used last is now behind — `git pull` before editing there.**

They generally prefer receiving **complete files to drop in** rather than
"find line N and change it" instructions, especially for `MainActivity.kt`.

### Reference clones on the PC
- `E:\tachiyomi-ref` — mirror of the original (deleted) `tachiyomiorg/tachiyomi`,
  from `https://github.com/ZhanZiyuan/tachiyomi`. **This is the canonical
  reference** for the vendored API.
- `E:\mihon-ref` — `mihonapp/mihon` (modern; mostly a dead end, see §5).

---

## 2. Current state — it works

As of commit `7ff563f`, verified on device:

- **26/26 extensions load, 95 sources total.**
- Browsing, chapter lists, and page rendering work end to end.
- Library with categories works.
- Per-source search + pagination work.
- Extension index filter and cross-source global search work.
- Extension sources are cached between calls (`3e28e81`).
- Sources and Extensions tabs match Mihon's layout: icons, pinning, Last used,
  language groups, 18+ badges (`646d959`).
- Per-source settings from `ConfigurableSource` (`03cabb3`).
- Global search limited to pinned sources, with a toggle (`0023c81`).
- The Compose UI is split across nine files (`4fc763e`) — see §4.
- Extension **updates** are detected and offered (`e1913c2`).
- Series screen reworked: cover backdrop, author/status/description/genres,
  chapter dates, Start/Resume button (`364d1ce`).

`MainActivity.kt` is **750 lines** — the Activity, `YomuApp`, and the shared
prefs helpers.

## 3. Build environment

| Thing | Version |
|---|---|
| Kotlin | 2.0.20 |
| AGP | 8.5.2 |
| Gradle | 8.9 (via `gradle/actions/setup-gradle@v4`, no wrapper) |
| JDK | 17 (temurin) |
| compileSdk / targetSdk | 34 |
| minSdk | 24 |

Modules: `:app` and `:source-api`.

`settings.gradle.kts` **must** keep the jitpack repo — Injekt comes from there:
```kotlin
maven(url = "https://www.jitpack.io")
```

CI is `.github/workflows/build.yml`: builds **debug only** on push; release APK
is behind a `workflow_dispatch` input to keep builds fast (~2–4 min warm).
`gradle.properties` sets `-Xmx4g` and `org.gradle.caching=true`.

### `:app` dependencies that constrain UI work
Compose BOM `2024.09.03`, material3, **`material-icons-core` only** (see §5),
coil `2.7.0`, telephoto zoomable-image, okhttp 4.12, documentfile.

---

## 4. Architecture

### `:source-api` — vendored Tachiyomi API
Extensions are compiled against `eu.kanade.tachiyomi.source.*` as `compileOnly`
stubs; **the host app must supply the real implementations**. That was the
original bug: extensions installed fine but died with
`NoClassDefFoundError: HttpSource`.

This module is **Tachiyomi 0.15.x's `source-api`, flattened** from its Kotlin
Multiplatform layout (`commonMain` + `androidMain`) into a single
`src/main/kotlin`, **plus** `eu/kanade/tachiyomi/network/` copied from that
repo's `core` module into `src/main/java`.

Key dependency coordinates (from `E:\tachiyomi-ref\gradle\libs.versions.toml`):
```kotlin
api("com.github.mihonapp:injekt:91edab2317")   // NOT com.github.inorichi.injekt
api("io.reactivex:rxjava:1.3.8")               // RxJava 1.x, not 2/3
api("org.jsoup:jsoup:1.17.2")
api(platform("com.squareup.okhttp3:okhttp-bom:5.0.0-alpha.12"))
implementation("com.squareup.okio:okio:3.7.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
implementation("androidx.preference:preference-ktx:1.2.1")
implementation("com.squareup.logcat:logcat:0.1")
```
`kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"` is required
(`OkHttpExtensions.kt` uses context receivers).

**Modifications made to the vendored code** (don't "restore" these):
- **Deleted:** `CloudflareInterceptor.kt`, `WebViewInterceptor.kt`,
  `JavaScriptEngine.kt`, `NetworkPreferences.kt` — they pulled in moko-resources
  (`MR`), WebView utils, QuickJS, and `PreferenceStore` from Tachiyomi's `:core`.
- **`NetworkHelper.kt` rewritten** to a minimal version exposing `client`,
  `cloudflareClient` (== `client`), `cookieJar`, `defaultUserAgentProvider()`.
- **`util/RxExtension.kt` replaced** with a self-contained `awaitSingle()` for
  `rx.Observable` (the original was `expect`/`actual` + `:core` import).
- **`PreferenceScreen.kt`**: `actual` keyword stripped (no longer KMP).
- `CatalogueSource.kt` / `HttpSource.kt`: `awaitSingle` import repointed to
  `eu.kanade.tachiyomi.util.awaitSingle`.

### `:app` — key files
| File | Role |
|---|---|
| `ExtensionLoader.kt` | Discovers + loads extension APKs. `loadAllCached()`, `invalidate()`, `diagnose()`. |
| `ExtensionManager.kt` | Repo index fetch + APK install + `compareVersions()`. `loadInstalledSources()` in it is **dead code** — do not use. |
| `TachiyomiSourceAdapter.kt` | Wraps `CatalogueSource` → app's `Source`. Also holds `langLabel()`, `statusLabel()`, and the `safe*()` lateinit guards. |
| `Source.kt` | App's own `Source` interface + `Series`/`Chapter`/`SeriesPage`. |
| `SourceManager.kt` | `listAllSources()` = local configs + cached adapter-wrapped extensions. |
| `SourcePrefs.kt` | Pinned source ids, last-used source id, pinned-only-search flag. |
| `App.kt` | `Application` subclass; registers Injekt bindings. |
| `Library.kt` | Saved-series store (JSON in SharedPreferences). |
| `Categories.kt` | Categories + series→category assignments. |
| `SourceSettings.kt` | Reads `ConfigurableSource` preferences into a Compose-renderable model. |
| `MainActivity.kt` | Activity, `YomuApp` (all shared screen state), prefs helpers. |

### Compose UI file layout
`MainActivity.kt` used to hold every screen. It was split once it passed 2600
lines; the cut is by screen, and **all files are in the same package**, so moving
things between them needs no imports — only visibility changes (see §5).

| File | Holds |
|---|---|
| `MainActivity.kt` | `MainActivity`, `YomuApp`, `prefs`/`chapterKeyOf`/`savedPage`/`savePage`/`isIncognito`, `GlobalResult`, `ResumeTarget`, global-search constants |
| `Ui.kt` | `CoverImage`, `SourceIcon`, `SectionHeader`, `NsfwBadge`, `ErrorBanner` |
| `BrowseScreen.kt` | `BrowseTab`, `BrowseSourceRow`, `BrowseRow`, `langRank`, `ExtensionsScreen`, `ExtensionRow` |
| `SourceSettingsUi.kt` | `SourceSettingsDialog`, `SourcePrefRow` |
| `GlobalSearchScreen.kt` | `GlobalSearchScreen` |
| `SourceBrowseScreens.kt` | `LibraryScreen` (the **per-source browse** screen), `SeriesScreen` |
| `LibraryScreens.kt` | `LibraryTab`, `AddToLibraryDialog`, `CategoryAssignDialog` |
| `ReaderScreen.kt` | `ReaderScreen` |
| `MoreScreens.kt` | `MoreTab`, `HistoryScreen`, `ExtensionReposDialog`, `CategoryManagerDialog`, `SourceDialog` |

`YomuApp` still owns all cross-screen state and passes it down as parameters, so
the screens stay dumb. That's why the split was safe to do mechanically.

### The app's `Source` interface

Beyond `id`/`name` and the browse/chapter/page methods:

```kotlin
val lang: String get() = ""        // "English", "Multi" — blank hides the line
val iconPkg: String? get() = null  // extension package, for the launcher icon
val isNsfw: Boolean get() = false  // drives the 18+ badge

suspend fun getSeries(id: String): Series?
suspend fun restoreSeries(id: String, title: String): Series? = getSeries(id)
suspend fun loadDetails(series: Series): Series = series
```

Everything is defaulted so `LocalSource` needs no changes.

`TachiyomiSourceAdapter` sets `name = delegate.name` and
`lang = langLabel(delegate.lang)` — **the language is no longer baked into the
name string**. `langLabel()` maps ISO codes plus Tachiyomi's `all` → "Multi";
`statusLabel()` maps `SManga.status`'s int enum to a display string.

**`restoreSeries` vs `getSeries` — this distinction is load-bearing.**
`getSeries` synthesises a stub SManga from the id and calls `getMangaDetails` on
it. `restoreSeries` builds the url + title pair that Tachiyomi says a stored
entry is, marks it `initialized = true`, and makes **no network call at all**.
Library and History reopen through `restoreSeries`.

The reason: browsing hands `getChapterList` the fully-parsed SManga from the
catalogue page and never calls `getMangaDetails`. Reopening from the library used
to call it, and that one extra request was the *only* difference between the two
paths — so a source with a broken details endpoint could be browsed but not
reopened from the library. See §5.

`loadDetails` is that details request, moved somewhere it can fail harmlessly.
`YomuApp.enrichSeries()` fires it after the chapter list is already loading and
only applies the result if it succeeds *and* the user is still on the same
series. Metadata is a bonus; it never blocks reading.

### `Series` and `Chapter`

```kotlin
data class Series(id, title, cover, handle,
                  author: String?, description: String?,
                  genres: List<String>, status: String?)

data class Chapter(id, name, handle, dateUploaded: Long, scanlator: String?)
```

The metadata fields are populated by `loadDetails`, so they're empty on a series
that has only been restored, and fill in a moment later. `handle` carries the
real `SManga`/`SChapter` through, which is what extensions actually need.

### Source caching (added `3e28e81`)
`listAllSources()` used to classload all 26 APKs on **every call**, including
from a lifecycle observer on every `ON_RESUME`. Two layers now sit under it:

1. `ExtensionLoader.loadAllCached()` — runs the cheap
   `getInstalledPackages(GET_CONFIGURATIONS)` query, fingerprints the result as
   sorted `pkg|versionName|lastUpdateTime`, and only re-instantiates when that
   string changes. Install, uninstall, update and same-version reinstall all
   miss the cache, so the `ON_RESUME` rescan still picks up new packages.
2. `SourceManager.extensionSources()` — reference-compares the `LoadResult` list
   against last time and reuses the existing adapters when it's unchanged, so
   `Source` identities are stable instead of fresh objects per call.

Both are `@Synchronized`; Browse, global search and the lifecycle observer all
reach them concurrently from `Dispatchers.IO`.

**Adapters are built with `applicationContext`** — they're in a static cache now
and outlive any Activity. `SourceManager.invalidateExtensions()` exists but is
unwired; the fingerprint covers package changes on its own.

The **local** half of `listAllSources()` is deliberately *not* cached: it's a
SharedPreferences read plus a couple of constructions, and it has to reflect
edits from the Sources screen immediately.

`diagnose()` stays uncached and does not populate the cache — a diagnostic run
shouldn't swap out the instances Browse is holding.

### Extension loading contract (this is the part that was wrong originally)
Extensions are **not** found by intent filter. They are found by:
- `<uses-feature android:name="tachiyomi.extension"/>` — enumerate with
  `getInstalledPackages(GET_CONFIGURATIONS)` + check `reqFeatures`.
- Class name from **application** meta-data `tachiyomi.extension.class`,
  semicolon-separated, leading `.` means package-relative.
- **Instantiate, then type-check**: `is Source` → use it; `is SourceFactory` →
  call `createSources()`. There is no separate factory metadata key for this.
- Lib version is parsed from `versionName` (e.g. `1.4.13` → `1.4`), *not* from
  any metadata key. Supported range in `ExtensionLoader`: 1.4–1.6.
- `PathClassLoader(appInfo.sourceDir, null, context.classLoader)` — parent must
  be the app classloader so extensions see the vendored API.
- `tachiyomi.extension.nsfw` (int, 1 = adult) rides along on `LoadResult.isNsfw`.
- Manifest needs `QUERY_ALL_PACKAGES`.

### Injekt bindings (in `App.kt`)
Extensions look these up; without them they construct but fail at first use:
```kotlin
addSingleton<Application>(app)
addSingletonFactory { NetworkHelper(app) }
addSingletonFactory { Json { ignoreUnknownKeys = true; explicitNulls = false } }
```

### UI structure
Bottom nav, 4 tabs:
0. **Library** — saved series grid, category filter chips
1. **Browse** — sub-tabs *Sources* and *Extensions* (below)
2. **History**
3. **More** — incognito, cover size, Categories, **Browse → Extension repos**

Repo add/remove lives **only** in More → Browse → Extension repos
(`ExtensionReposDialog`). The Extensions tab used to have its own copy of that
editor; it was removed in `6728165`.

#### Sources sub-tab (reworked `646d959`)
Sections in order: **Last used** → **Pinned** → one per language. Language
groups sort Local first, Multi second, then A–Z (`langRank()`).

Local folder configs and extension sources are flattened into a private
`BrowseRow` before the list is built; `config != null` is what gates the
Edit/Delete overflow menu, so only local folders get one.

- Rows show the APK launcher icon (`SourceIcon`, `PackageManager.getApplicationIcon`
  remembered per package, rendered through Coil which accepts a `Drawable`
  model), name, language, and an 18+ badge.
- Pin toggle writes through `SourcePrefs.togglePin()`.
- **Pinned sources are lifted out of their language group**, not shown twice.
  Mihon duplicates them; this deliberately doesn't.
- `SourcePrefs.setLastUsed()` is called from `openSource()` in `YomuApp`, which
  covers local folders too (`openSourceConfig` → `openSource`).
- `pinnedIds` / `lastUsedId` are `remember`ed and re-read from prefs whenever the
  tab re-enters the composition — a bottom-nav switch always disposes it, so no
  explicit invalidation is needed.

#### Extensions sub-tab
**Update available**, **Installed** and **Available** sections, each row showing
icon, name, `<lang> <version>` and an 18+ badge. Not-yet-installed rows have no
package to read an icon from, so they fall back to initials.

`fetchAvailable()` keeps the installed `versionName` alongside the boolean, and
`Extension.hasUpdate` compares it against the index with `compareVersions()` —
numeric per dotted component, because a string compare puts `1.4.9` after
`1.4.10`. Non-numeric parts compare as 0, so a malformed version reports "no
update" rather than a false one. Updating reuses the install flow; the system
installer treats a higher versionCode on the same package as an upgrade.

#### Series screen (`SourceBrowseScreens.kt`, reworked `364d1ce`)
Faded cover backdrop under a gradient, 108dp cover, title, author,
`<status> \u2022 <source>`, an icon action row, tap-to-expand description, genre
chips, chapter count, then the chapter list. An `ExtendedFloatingActionButton`
reads Start or Resume and targets the first unread chapter.

- The action row has two items, not Mihon's four: **Add to library** and
  **Categories**. Download and Tracking don't exist in this app. Categories
  revives `CategoryAssignDialog`, which had been orphaned for several handoffs.
- Genre chips scroll horizontally rather than wrapping: `FlowRow` is still an
  experimental layout API on this Compose version.
- `formatChapterDate()` renders Today / Yesterday / `d MMM yyyy`, and returns
  null when the source published no date (`date_upload == 0`).

`ExtensionManager.fetchAvailable()` parses `lang` and `nsfw` out of the repo
index (they were always in the JSON, just unused) and strips the
`Tachiyomi: ` / `Mihon: ` name prefix.

The client-side filter field and "Installed only" chip are unchanged.

### Routing chain in `YomuApp` — order is load-bearing

A single `if / else if` chain, in this order:

1. reader (`activeChapterIdx` + pages)
2. `activeSeries != null` → `SeriesScreen`
3. `globalSearchOpen` → `GlobalSearchScreen`
4. `activeSource != null` → `LibraryScreen` (this is the **per-source browse**
   screen, despite the name — the Library *tab* is `LibraryTab`)
5. else → `Scaffold` with the bottom nav

Global search sits **below** `SeriesScreen` on purpose: tapping a result opens
the series (branch 2 wins), and backing out of it falls through to branch 3, so
the results are still there.

**The adopted-source trap.** Opening a series from Library, History, or a global
search hit has to set `activeSource` — chapters and pages are loaded through it.
But that means backing out of `SeriesScreen` drops into branch 4 and shows the
per-source browse screen, which has no results behind it ("Nothing found in this
source"). `SeriesOrigin` (BROWSE / LIBRARY / HISTORY / GLOBAL_SEARCH) records how
the series was reached, and the back handler only keeps the adopted source for
BROWSE. Leaving global search releases it too, for the same reason.

Note `openGlobalResult` calls `openSeries`, which sets the origin to BROWSE
unconditionally — so its own origin assignment has to come *after* that call.

### Global search
- State is **hoisted into `YomuApp`**, not held inside `GlobalSearchScreen` —
  otherwise results are destroyed when branch 2 takes over the composition.
- `runGlobalSearch(query)` walks the target list in
  `chunked(GLOBAL_SEARCH_CONCURRENCY)` batches of `async` calls, publishing each
  batch as it lands. Per-source failures are swallowed via `runCatching`.
- Constants at the top of the file: `GLOBAL_SEARCH_CONCURRENCY = 6`,
  `GLOBAL_SEARCH_PER_SOURCE = 12`.
- It reuses the already-loaded `extensionSources`, so it does **not** re-classload
  the 26 APKs.

---

## 5. Hard-won lessons — don't repeat these

**`tachiyomiorg` org was deleted.** Any JitPack coordinate under
`com.github.tachiyomiorg:*` will fail to resolve. Same for
`com.github.inorichi.injekt`. Use `com.github.mihonapp:*`.

**The extensions-lib stub shortcut does not work.** Three builds were burned
trying `com.github.tachiyomiorg:extensions-lib:1.4`,
`com.github.mihonapp:tachiyomix:1.4.4`, and
`com.github.mihonapp:extensions-lib:1.4.4`. None resolve. Vendoring is the path.

**Do not vendor from modern Mihon.** Its `source-api` needs
`kotlin.concurrent.atomics` (Kotlin 2.1.20+), a `mihon.*` package, moko-resources,
and `core/common` — an unbounded dependency chase. The installed extensions are
all **lib 1.4.x**, which is Rx-based; modern Mihon (1.6) is suspend-only and
would silently return empty results even if it compiled.

**There are two `Source.kt` files.** `com.mangareader.app.Source` (the app's own
interface) and `eu.kanade.tachiyomi.source.Source` (vendored). Always specify the
full path when discussing one.

**The vendored model classes use `lateinit`.** `SMangaImpl.url`, `.title` and
`SChapterImpl.url`, `.name` are all `lateinit var`. Reading one that was never
assigned throws `UninitializedPropertyAccessException`, which surfaced to the
user as "lateinit property title has not been initialized" — from a stub whose
details fetch had failed. **Never read those four directly**;
`TachiyomiSourceAdapter` has `safeUrl()` / `safeTitle()` / `safeName()` for it.
Anything new that touches `SManga` or `SChapter` needs the same care.

**A `runCatching` that swallows an error can turn it into a worse one.** The
above crashed one line *after* the real failure, reporting an uninitialized field
instead of the network error that caused it. When swallowing, log the original
(`Log.w` in the adapter does) and make sure the fallback object is actually
usable.

**Browsing and reopening take different paths through an extension.** Browse
passes the catalogue-parsed SManga straight to `getChapterList`; reopening used
to synthesise a stub and call `getMangaDetails` first. If a source works in
Browse but not from the Library, that asymmetry is the first place to look — see
§4's note on `restoreSeries`.

**`import androidx.compose.material.icons.filled.List` shadows nothing, but
don't.** It puts a property named `List` in file scope alongside `List<Foo>` type
usages. Different namespaces, so it resolves — but it's not worth the risk in a
CI-only build. Pick another icon.

**Only `material-icons-core` is on the classpath.** Its set is roughly 40 icons
(Search, MoreVert, Star, Settings, Delete, …). `Icons.Default.PushPin` and the
other extended icons **do not exist here** — the pin affordance is a filled vs
dimmed `Star` for exactly this reason. Adding `material-icons-extended` is a
large artifact; decide deliberately.

**"Feature X is missing" may just be placement.** Search *was* shipped and
working; it lives on the per-source browse screen, and the Browse top bar simply
had no `actions`. Check where a feature is wired before assuming the build failed.

**Top-level `private` is file-scoped in Kotlin.** This is the whole story of the
UI split: a `private fun LibraryTab(...)` moved to another file becomes invisible
to `YomuApp`. Every top-level declaration that crosses a file boundary is now
`internal`. Same package means no imports are needed between these files — only
the visibility keyword matters.

**When adding a new UI file, copy `MainActivity.kt`'s whole import block.** The
imports are mostly wildcards (`androidx.compose.foundation.layout.*`,
`material3.*`, `runtime.*`) and unused imports are warnings, never errors. This
is what made the split safe to do without a compiler.

**Watch for missing braces when editing `MainActivity.kt`.** A dropped `}` in a
`DisposableEffect` produced ~40 cascading errors ("Modifier 'private' is not
applicable to 'local function'"). That signature = unclosed lambda earlier.

**When replacing a whole function in `MainActivity.kt`, check the line above it.**
Replacing `@Composable` + `private fun BrowseTab(` left the function's
`@OptIn(ExperimentalMaterial3Api::class)` stranded on top of the next
declaration. Annotations sit above the `@Composable`, outside the obvious
boundary.

**CRLF warnings on every push are benign** (LF in repo, CRLF in working copy).

### Termux-specific

**A filename that already exists in Downloads gets `.txt` appended.** This is
the collision rule, worked out the hard way: the download manager doesn't
overwrite and doesn't number, it appends `.txt`. So the *same* file is
`MainActivity.kt` on a clean Downloads and `MainActivity.kt.txt` if a previous
copy is still sitting there — which is why the suffix appeared inconsistent
across sessions. `.md` behaves the same way.

Contents are untouched either way. Two consequences:

- **Clear Downloads after every push** (`rm ~/storage/downloads/*.kt`). This is
  the actual fix; everything else is working around it.
- **Prefer explicit filenames over a glob.** A `*.kt.txt` loop will happily copy
  a stale file from an earlier session over a newer one and silently revert it.

**Never keep the git repo on `/sdcard`.** A clone at
`~/storage/shared/MangaReader` (i.e. `/storage/emulated/0/...`) gives
`fatal: detected dubious ownership`, and FUSE has no real permission bits or
symlinks. Keep it at `~/MangaReader` on ext4. `git config core.fileMode false` is
then unnecessary.

**`git config --global user.name` / `user.email` must be set** or the commit is
rejected. `--global` works from any directory; a non-global `git config` outside a
repo fails with `fatal: not in a git directory` and will abort a `&&` chain.

**`find ~/storage -iname '...'` returns nothing** even when the file is there:
`~/storage/downloads` is a symlink and `find` won't descend into one without `-L`.
Saved-from-chat files land in `~/storage/downloads` (= `/sdcard/Download`) — just
`cp` that path directly.

**A new file won't show in `git diff --stat`** because it's untracked. `git add -A`
still picks it up and the *commit* line will show the higher count. Seven files
copied showed as "6 files changed" in the diff and "7 files changed" in the
commit — that's correct, not a failed copy.

**Confirm what a "modified" file actually is before `git checkout --`.** A file
showing as modified right after a copy is probably the *new* content, not
corruption — `git diff --stat` first. Discarding it costs a re-download.

---

## 6. Diagnostics

Browse → Extensions → **"Why isn't my extension showing?"** runs
`ExtensionLoader.diagnose(context)` and reports, per package: label, declared
class, versionName, and either the source count or the full exception cause
chain. This is the fastest way to triage extension problems.

It also prints a **`Cache:`** line showing how many sources the loader is
currently holding — a quick read on whether the cache is working or silently
missing on every call.

For search coverage, the global search screen prints
"Searched X of Y sources · N with results".

---

## 7. Known limitations / next steps

Roughly in order of value:

1. **Cloudflare.** Deliberately removed. Sources behind Cloudflare's challenge
   will fail. Restoring needs a WebView flow + the interceptor that was stripped
   out of the vendored API — the biggest single item left.
2. **Global search paging and persistence.** Restricting the fan-out to pinned
   sources is done (`0023c81`). Still open: each row shows page 1 only, with no
   way to load more within a source, and the whole result set is lost on app
   restart.
3. **Sort/filter for search.** `getFilterList()` is available on every
   `CatalogueSource` and is currently unused — `searchSeries` passes an empty
   `FilterList()`. This is also what the Browse top bar's missing filter icon
   would drive.
4. **`OBSOLETE` badge.** Mihon marks installed extensions that no longer appear
   in the repo index. The Extensions list is built from the index only, so
   installed-but-absent packages aren't visible at all; they'd have to be merged
   in from `ExtensionLoader` first. Update detection (`e1913c2`) already does the
   version half of this.
5. **Per-source settings only reach `ConfigurableSource` basics.** Toggles,
   single- and multi-select lists and text fields are rendered; any other
   `Preference` subclass is skipped rather than shown as a dead row. Extensions
   that do their real work in an `OnPreferenceChangeListener` are handled (the
   listener runs before the value is written), but nothing renders a
   `Preference` with no key.
6. **Covers can 403.** Some sources reject hotlinked thumbnails because Coil
   fetches them without the source's headers (Referer / User-Agent). The reader
   already downloads pages through the source's own OkHttp client
   (`TachiyomiSourceAdapter.downloadPage`); covers don't. A Coil `Fetcher` backed
   by the same client would fix it.
7. **One extension is lib 1.6** (`AHottie`, v1.6.4). It's inside the accepted
   version range but built against the newer API; it may fail at runtime.
8. **`HttpException.kt`** was not present in the vendored network package. Some
   extensions catch `eu.kanade.tachiyomi.network.HttpException` by name — if a
   `NoClassDefFoundError` for it appears at runtime, it's a ~3-line class to add.
9. **`YomuApp` is ~600 lines.** The screens are split out, but all state and
   every handler still lives in one composable, and the routing chain plus
   `SeriesOrigin` now encode real navigation rules in `if / else if`. Hoisting
   this into a state holder — or adopting a real nav library — is the next
   structural step, and unlike the file split it is *not* mechanical.
10. **No Feed / Migrate tabs.** Mihon has four sub-tabs under Browse; this app
    has two. Neither is started.

---

## 8. Recent commit history (newest last)

```
Fix SourceFactory detection: type-check instance, not metadata key
Vendor Tachiyomi 0.15 source-api, flattened to a single source set
Switch to mihonapp injekt fork
Strip WebView stack, replace NetworkHelper and awaitSingle
Register Injekt bindings for extension dependencies
Add CatalogueSource adapter and wire extensions into SourceManager
Fix CatalogueSource import and cover type handling
Add Browse tab, extension repos settings, remove Komga
Feed extension sources from the real loader
Fix brace in DisposableEffect
Add Library tab with category-on-save
Add search, pagination, and direct series lookup          602b212  verified OK
Add extension index filter and cross-source global search e85abbe  verified OK
Cache loaded extension sources between calls              3e28e81  verified OK
Remove repo editor from Extensions tab; it lives in More  6728165  verified OK
Rework Sources and Extensions tabs                        646d959  verified OK
Update handoff for source cache and Browse tab rework     362b003
Add per-source settings from ConfigurableSource           03cabb3  verified OK
Limit global search to pinned sources with a toggle       0023c81  verified OK
Split MainActivity into per-screen files                  4fc763e  verified OK
Fix lateinit crash when a source's details fetch fails    ddaed36  verified OK
Detect and offer extension updates                        e1913c2  verified OK
Reopen library entries without a details fetch            c5887f3  verified OK
Rework series screen with backdrop, metadata, resume      364d1ce  verified OK
Return to the right screen when backing out of a series   7ff563f
```

Komga support was removed entirely (`KomgaSource.kt` deleted); only local
folders and extensions remain as source types.
