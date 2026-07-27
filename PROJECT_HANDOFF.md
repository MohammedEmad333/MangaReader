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

Termux (files arrive in `~/storage/downloads` when saved from chat):
```
cd ~/MangaReader && cp ~/storage/downloads/FILE.kt app/src/main/java/com/mangareader/app/FILE.kt && git diff --stat && git add -A && git commit -m "message" && git push
```

The `git diff --stat` in the middle is deliberate: it prints the expected
insertion count before committing, and if the copy silently failed the `&&`
chain aborts at the empty commit instead of pushing nothing.

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

As of commit `e85abbe`, verified on device:

- **26/26 extensions load, 95 sources total.**
- Browsing, chapter lists, and page rendering work end to end.
- Library with categories works.
- **Per-source search + pagination + `getSeries`** work (this was the
  previously-unverified commit `602b212` — it was fine).
- **Extension index filter** and **cross-source global search** work (`e85abbe`).

`MainActivity.kt` is now **~2179 lines**.

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
| `ExtensionLoader.kt` | Discovers + loads extension APKs. Also has `diagnose()`. |
| `ExtensionManager.kt` | Repo index fetch + APK install. `loadInstalledSources()` in it is **dead code** — do not use. |
| `TachiyomiSourceAdapter.kt` | Wraps `CatalogueSource` → app's `Source`. |
| `Source.kt` | App's own `Source` interface + `Series`/`Chapter`/`SeriesPage`. |
| `SourceManager.kt` | `listAllSources()` = local configs + adapter-wrapped extensions. |
| `App.kt` | `Application` subclass; registers Injekt bindings. |
| `Library.kt` | Saved-series store (JSON in SharedPreferences). |
| `Categories.kt` | Categories + series→category assignments. |
| `MainActivity.kt` | **~2179 lines**, all Compose UI in one file. |

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
1. **Browse** — sub-tabs: *Sources* (local folders + extension sources, with
   add/edit/delete for local; **search icon in the top bar → global search**)
   and *Extensions* (repo index, install, **filter field**)
2. **History**
3. **More** — incognito, cover size, Categories, **Browse → Extension repos**

Saving to library: `SeriesScreen` has a `+ Library` button → `AddToLibraryDialog`
(checkbox list of categories, "Default" pre-ticked, inline new-category field).
`Categories.ensureDefault()` guarantees a "Default" category exists and
`Categories.remove()` refuses to delete it.

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

### Global search (added `e85abbe`)
- State is **hoisted into `YomuApp`**, not held inside `GlobalSearchScreen` —
  otherwise results are destroyed when branch 2 takes over the composition:
  `globalSearchOpen`, `globalQuery`, `globalResults`, `globalRunning`,
  `globalDone`, `globalTotal`, `globalJob`.
- `runGlobalSearch(query)` builds the target list (local configs via
  `SourceManager.build` + `extensionSources`, filtered on `supportsSearch`),
  then walks it in `chunked(GLOBAL_SEARCH_CONCURRENCY)` batches of `async` calls,
  publishing each batch as it lands so rows appear progressively.
  Per-source failures are swallowed via `runCatching`; empty results are dropped.
- Constants at the top of the file: `GLOBAL_SEARCH_CONCURRENCY = 6`,
  `GLOBAL_SEARCH_PER_SOURCE = 12`. Private holder class `GlobalResult`.
- `cancelGlobalSearch()` cancels `globalJob` ("Stop" button, and on back).
- `openGlobalResult(source, series)` adopts the source then opens the series;
  `openGlobalSource(source)` leaves the results and browses that source with the
  same query.
- It reuses the already-loaded `extensionSources`, so it does **not** re-classload
  the 26 APKs.

### Extension index filter (added `e85abbe`)
`ExtensionsScreen` holds `filter` + `installedOnly` and derives `shownExtensions`
in a `remember(available, filter, installedOnly)`. Pure client-side filter over
the already-fetched index — no refetch, no network. Matches `name` and `pkgName`,
case-insensitive, and shows an "N of M" counter.

`Icons.Default.Search` comes from `material-icons-core`, the same artifact that
already supplies `MoreVert` — no new dependency.

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

**"Feature X is missing" may just be placement.** Search *was* shipped and
working; it lives on the per-source browse screen, and the Browse top bar simply
had no `actions`. Check where a feature is wired before assuming the build failed.

**When handing over a file to download, watch the filename.** Files delivered as
e.g. `source-api-build.gradle.kts` must be **renamed** to `build.gradle.kts` —
this caused a broken build once.

**CRLF warnings on every push are benign** (LF in repo, CRLF in working copy).

**Watch for missing braces when editing `MainActivity.kt`.** A dropped `}` in a
`DisposableEffect` produced ~40 cascading errors ("Modifier 'private' is not
applicable to 'local function'"). That signature = unclosed lambda earlier.

### Termux-specific (learned 2026-07-27)

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

**Confirm what a "modified" file actually is before `git checkout --`.** A file
showing as modified right after a copy is probably the *new* content, not
corruption — `git diff --stat` first. Discarding it costs a re-download.

---

## 6. Diagnostics

Browse → Extensions → **"Why isn't my extension showing?"** runs
`ExtensionLoader.diagnose(context)` and reports, per package: label, declared
class, versionName, and either the source count or the full exception cause
chain. This is the fastest way to triage extension problems.

For search coverage, the global search screen prints
"Searched X of Y sources · N with results" — a quick read on how many of the 95
sources actually honor `searchSeries`.

---

## 7. Known limitations / next steps

Roughly in order of value:

1. **Cache loaded sources.** `SourceManager.listAllSources()` classloads all 26
   APKs on **every call**, and it's called from several places (including a
   lifecycle observer on every `ON_RESUME`). A `by lazy` or invalidatable cache
   would noticeably improve responsiveness. *Still the top pick.*
2. **Per-source settings.** Extensions implementing `ConfigurableSource` expose
   preferences (language, mirror, image quality). No UI reaches them yet; some
   sources won't behave correctly until they're set.
3. **Global search polish.** Page 1 only (no paging within a row), results are
   lost on app restart, and every searchable source is queried with no way to pin
   or exclude a subset. A "pinned sources" list would cut a 95-source fan-out to
   the handful actually used.
4. **Cloudflare.** Deliberately removed. Sources behind Cloudflare's challenge
   will fail. Restoring needs a WebView flow + the interceptor.
5. **Sort/filter for search.** `getFilterList()` is available on every
   `CatalogueSource` and is currently unused — `searchSeries` passes an empty
   `FilterList()`.
6. **`CategoryAssignDialog` is orphaned.** Still defined in `MainActivity.kt` but
   nothing calls it (the old "Tags" button was replaced). Could be revived as a
   long-press action on library items.
7. **One extension is lib 1.6** (`AHottie`, v1.6.4). It's inside the accepted
   version range but built against the newer API; it may fail at runtime.
8. **`HttpException.kt`** was not present in the vendored network package. Some
   extensions catch `eu.kanade.tachiyomi.network.HttpException` by name — if a
   `NoClassDefFoundError` for it appears at runtime, it's a ~3-line class to add.
9. **`MainActivity.kt` is 2179 lines.** Splitting the screens into separate files
   would make future edits far less risky, but every handoff so far has assumed
   one file — do it deliberately, not incidentally.

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
```

Komga support was removed entirely (`KomgaSource.kt` deleted); only local
folders and extensions remain as source types.
