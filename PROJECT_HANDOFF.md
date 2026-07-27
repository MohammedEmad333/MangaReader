# Yomu / MangaReader — Project Handoff

Context document for continuing work in a fresh chat. Written 2026-07-27.

---

## 1. What this project is

A native Kotlin/Compose Android manga reader (`com.mangareader.app`, app label
**Yomu**) that loads **Tachiyomi/Mihon extension APKs** as content sources.

- **GitHub:** https://github.com/MohammedEmad333/MangaReader
- **Local working copy:** `E:\MangaReader` (Windows, cmd.exe)
- **Builds via GitHub Actions**, not locally. No Android Studio in the loop.

### How the user works
They edit files locally in `E:\MangaReader`, then push; GitHub Actions builds
the APK and publishes it to the `latest` prerelease, which they install on
their phone. **They ask for a ready-to-paste push command at the end of any
change.** The command they use:

```
cd /d E:\MangaReader && git add -A && git commit -m "message" && git push
```

They generally prefer receiving **complete files to drop in** rather than
"find line N and change it" instructions, especially for `MainActivity.kt`.

### Reference clones on their machine
- `E:\tachiyomi-ref` — mirror of the original (deleted) `tachiyomiorg/tachiyomi`,
  from `https://github.com/ZhanZiyuan/tachiyomi`. **This is the canonical
  reference** for the vendored API.
- `E:\mihon-ref` — `mihonapp/mihon` (modern; mostly a dead end, see §5).

---

## 2. Current state — it works

As of the last verified build:

- **26/26 extensions load, 95 sources total.**
- Browsing, chapter lists, and page rendering all work end to end.
- Library with categories works.

The **last commit** (search + pagination + `getSeries`) was pushed but its build
result was **not confirmed** before the chat ended. Verify that first.

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
| `MainActivity.kt` | **~1500 lines**, all Compose UI in one file. |

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
   add/edit/delete for local) and *Extensions* (repo index, install)
2. **History**
3. **More** — incognito, cover size, Categories, **Browse → Extension repos**

Saving to library: `SeriesScreen` has a `+ Library` button → `AddToLibraryDialog`
(checkbox list of categories, "Default" pre-ticked, inline new-category field).
`Categories.ensureDefault()` guarantees a "Default" category exists and
`Categories.remove()` refuses to delete it.

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

**When handing over a file to download, watch the filename.** Files delivered as
e.g. `source-api-build.gradle.kts` must be **renamed** to `build.gradle.kts` —
this caused a broken build once.

**CRLF warnings on every push are benign** (LF in repo, CRLF in working copy).

**Watch for missing braces when editing `MainActivity.kt`.** A dropped `}` in a
`DisposableEffect` produced ~40 cascading errors ("Modifier 'private' is not
applicable to 'local function'"). That signature = unclosed lambda earlier.

---

## 6. Diagnostics

Browse → Extensions → **"Why isn't my extension showing?"** runs
`ExtensionLoader.diagnose(context)` and reports, per package: label, declared
class, versionName, and either the source count or the full exception cause
chain. This is the fastest way to triage extension problems.

---

## 7. Known limitations / next steps

Roughly in order of value:

1. **Cache loaded sources.** `SourceManager.listAllSources()` classloads all 26
   APKs on **every call**, and it's called from several places (including a
   lifecycle observer on every `ON_RESUME`). A `by lazy` or invalidatable cache
   would noticeably improve responsiveness. *This is the top pick.*
2. **Per-source settings.** Extensions implementing `ConfigurableSource` expose
   preferences (language, mirror, image quality). No UI reaches them yet; some
   sources won't behave correctly until they're set.
3. **Cloudflare.** Deliberately removed. Sources behind Cloudflare's challenge
   will fail. Restoring needs a WebView flow + the interceptor.
4. **Sort/filter for search.** `getFilterList()` is available on every
   `CatalogueSource` and is currently unused — `searchSeries` passes an empty
   `FilterList()`.
5. **`CategoryAssignDialog` is orphaned.** Still defined in `MainActivity.kt` but
   nothing calls it (the old "Tags" button was replaced). Could be revived as a
   long-press action on library items.
6. **One extension is lib 1.6** (`AHottie`, v1.6.4). It's inside the accepted
   version range but built against the newer API; it may fail at runtime.
7. **`HttpException.kt`** was not present in the vendored network package. Some
   extensions catch `eu.kanade.tachiyomi.network.HttpException` by name — if a
   `NoClassDefFoundError` for it appears at runtime, it's a ~3-line class to add.

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
Add search, pagination, and direct series lookup   <-- build result UNVERIFIED
```

Komga support was removed entirely (`KomgaSource.kt` deleted); only local
folders and extensions remain as source types.
