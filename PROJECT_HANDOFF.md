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

As of commit `646d959`, verified on device:

- **26/26 extensions load, 95 sources total.**
- Browsing, chapter lists, and page rendering work end to end.
- Library with categories works.
- Per-source search + pagination + `getSeries` work.
- Extension index filter and cross-source global search work.
- **Extension sources are cached** between calls (`3e28e81`).
- **Sources and Extensions tabs reworked** to match Mihon's layout: icons,
  pinning, Last used, language groups, 18+ badges (`646d959`).

`MainActivity.kt` is now **2368 lines**.

---

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
| `ExtensionManager.kt` | Repo index fetch + APK install. `loadInstalledSources()` in it is **dead code** — do not use. |
| `TachiyomiSourceAdapter.kt` | Wraps `CatalogueSource` → app's `Source`. Also holds `langLabel()`. |
| `Source.kt` | App's own `Source` interface + `Series`/`Chapter`/`SeriesPage`. |
| `SourceManager.kt` | `listAllSources()` = local configs + cached adapter-wrapped extensions. |
| `SourcePrefs.kt` | Pinned source ids + last-used source id. |
| `App.kt` | `Application` subclass; registers Injekt bindings. |
| `Library.kt` | Saved-series store (JSON in SharedPreferences). |
| `Categories.kt` | Categories + series→category assignments. |
| `MainActivity.kt` | **2368 lines**, all Compose UI in one file. |

### The app's `Source` interface
Beyond `id`/`name` and the browse/chapter/page methods, it carries three
display-only properties, all defaulted so `LocalSource` needs no changes:

```kotlin
val lang: String get() = ""        // "English", "Multi" — blank hides the line
val iconPkg: String? get() = null  // extension package, for the launcher icon
val isNsfw: Boolean get() = false  // drives the 18+ badge
```

`TachiyomiSourceAdapter` sets `name = delegate.name` and
`lang = langLabel(delegate.lang)` — **the language is no longer baked into the
name string**. `langLabel()` maps ISO codes plus Tachiyomi's `all` → "Multi",
falling back to the uppercased code.

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
**Installed** and **Available** sections, each row showing icon, name,
`<lang> <version>` and an 18+ badge. Not-yet-installed rows have no package to
read an icon from, so they fall back to initials.

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

**Only `material-icons-core` is on the classpath.** Its set is roughly 40 icons
(Search, MoreVert, Star, Settings, Delete, …). `Icons.Default.PushPin` and the
other extended icons **do not exist here** — the pin affordance is a filled vs
dimmed `Star` for exactly this reason. Adding `material-icons-extended` is a
large artifact; decide deliberately.

**"Feature X is missing" may just be placement.** Search *was* shipped and
working; it lives on the per-source browse screen, and the Browse top bar simply
had no `actions`. Check where a feature is wired before assuming the build failed.

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

**Downloads arrive as `.kt.txt`.** Android's download manager renames by MIME
type; the files are served as `text/plain` and `.kt` isn't in its table, so it
appends `.txt`. Contents are untouched. Strip it during the copy — see the loop
in §1. This bites on *every* handoff of a `.kt` file.

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

1. **Per-source settings.** Extensions implementing `ConfigurableSource` expose
   preferences (language, mirror, image quality). No UI reaches them yet; some
   sources won't behave correctly until they're set. Mihon puts this behind the
   gear icon on each installed extension row — that row already exists
   (`ExtensionRow`), it just has no gear. *Top pick.*
2. **Global search polish.** Page 1 only (no paging within a row), results are
   lost on app restart, and every searchable source is queried. Now that pinning
   exists (`SourcePrefs.pinned()`), restricting the fan-out to pinned sources is
   a small change that would cut a 95-source query to a handful.
3. **Cloudflare.** Deliberately removed. Sources behind Cloudflare's challenge
   will fail. Restoring needs a WebView flow + the interceptor.
4. **Sort/filter for search.** `getFilterList()` is available on every
   `CatalogueSource` and is currently unused — `searchSeries` passes an empty
   `FilterList()`.
5. **`OBSOLETE` badge.** Mihon marks installed extensions that no longer appear
   in the repo index. The Extensions list is built from the index only, so
   installed-but-absent packages aren't visible at all; they'd have to be merged
   in from `ExtensionLoader` first.
6. **`CategoryAssignDialog` is orphaned.** Still defined in `MainActivity.kt` but
   nothing calls it. Could be revived as a long-press action on library items.
7. **One extension is lib 1.6** (`AHottie`, v1.6.4). It's inside the accepted
   version range but built against the newer API; it may fail at runtime.
8. **`HttpException.kt`** was not present in the vendored network package. Some
   extensions catch `eu.kanade.tachiyomi.network.HttpException` by name — if a
   `NoClassDefFoundError` for it appears at runtime, it's a ~3-line class to add.
9. **`MainActivity.kt` is 2368 lines.** Splitting the screens into separate files
   would make future edits far less risky, but every handoff so far has assumed
   one file — do it deliberately, not incidentally.
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
```

Komga support was removed entirely (`KomgaSource.kt` deleted); only local
folders and extensions remain as source types.
