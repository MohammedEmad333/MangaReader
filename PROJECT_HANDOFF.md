# Yomu / MangaReader — Project Handoff

Context document for continuing work in a fresh chat. Last updated 2026-07-28
(late), after the settings / backup / storage-location session (supersedes the
earlier version of this file).

---

## 0. Where this was left — read this first

The last session built the Settings screen and everything under it: a real
backup and restore, a user-chosen storage location, and a readable download
tree. **All of that is verified on device** (builds #121–#125), which makes it
the first stretch of work in several sessions that isn't carrying an untested
tail. Two older threads are still open and neither was touched.

One thing the new work quietly created: the app now has a **light theme**, and
the reader was written against dark only. See thread 3.

### Open thread 1 — the reader rewrite is barely tested

The reader was rebuilt from 152 lines to ~630 (§4) at the very end of the
session, and the session stopped before most of it was exercised.

- **Known working:** it builds, a chapter opens, tapping raises the bars, and the
  settings sheet opens and applies. That is all that was actually seen.
- **Never tried:** paged right-to-left, long strip, the colour filters
  (especially grayscale + invert together, which uses its own precomputed
  matrix and can be wrong while each alone looks right), the chapter picker, and
  whether settings survive reopening a chapter.
- **The last commit is unverified.** Enabling Rotation → Landscape put the app in
  a loop: every chapter open bounced straight back to the Library. The fix —
  `android:configChanges` on `MainActivity` — was written and pushed but never
  run. If the loop is still there, that is where to look. See §5, because the
  underlying fragility is worth understanding before touching anything that
  might recreate the Activity.

### Open thread 2 — manhwatoon 400s (`Secret Class`)

A subset of pages of every chapter fails with HTTP 400. Four theories were tried;
the full account is in §5, and it's worth reading before touching this, because
three of the four were wrong in instructive ways.

- **Current state:** `recycleConnections()` in `TachiyomiSourceAdapter` has now
  **been run**, and it helped a lot without fixing it: Secret Class chapter 5
  failed 1 page of 36, chapter 6 failed 3 of 39. Against 12 of 36 and 7 of 39
  before it, that is roughly 33% → 18% → 3-8%.
- **This is the fourth reduction, not a fix**, and the handoff's own rule applies:
  *zero* failed pages is the bar. Three previous attempts each moved the rate and
  each looked like progress. Tuning `CONNECTION_RECYCLE_BATCHES` down further is
  the obvious next move and is the same move that has now failed four times.
- **Do the thing nobody has done: capture the 400 response body.** A CDN that
  rejects a request usually says why in plain text. `awaitSuccess()` closes the
  response before anyone can read it, so this needs a peek at the body before
  it's discarded. Everything else about this bug has been inferred from failure
  *rates*; the server has been trying to say what's wrong the whole time.
- The user deferred this deliberately. It is the oldest thread here and the only
  one that is a genuine unknown rather than untested work.

### Open thread 3 — the light theme meets the untested reader

`AppTheme` added Light and Follow-system alongside Dark, and `ReaderScreen` has
two hardcoded `Color.White` text draws — the page-number overlay (~line 163) and
the failed-page message (~line 317). With `ReaderBackground.THEME` on a light
scheme both are white on white. The bug already existed for
`ReaderBackground.WHITE`; the theme setting is what makes it reachable by
default. Trivial to fix — take the colour from the resolved background — but it
sits inside the file thread 1 says nobody has exercised, so fix it in the same
pass as the paged-RTL and grayscale+invert checks rather than on its own.

Also unaddressed: `AndroidManifest.xml` still declares
`@android:style/Theme.Material.NoActionBar`, which is the *dark* variant. In
light mode that means a dark flash on cold start before Compose paints, and a
permanently dark status bar over a light app.

### Closed last session — Cloudflare, covers, and two bugs

Recorded here because the reasoning matters more than the diff; details in §4
and the lessons in §5.

- **HentaiSco browses.** The blocker was never the missing UI. It was that the
  headless WebView was being told to claim the app's *desktop* User-Agent so it
  would match what OkHttp sends — inside an Android WebView, where every other
  signal a challenge reads says phone. That contradiction is unpassable by
  construction. The WebView now keeps its own UA and the winning string is
  recorded per host in `ClearanceUserAgents` for OkHttp to reuse.
- **A visible WebView exists anyway** (`WebViewScreen.kt`), reached from an
  "Open in WebView" action on the browse error. It was written for the
  interactive checkbox and is still the answer for genuinely interactive
  challenges — and the natural home for logging in to sources that need an
  account.
- **Covers load.** Coil had its own OkHttpClient: no cookie jar, no UA, no
  Cloudflare interceptor. `App` is now an `ImageLoaderFactory` handing Coil the
  extension client.
- **SpyFakku works.** Its mirrors return image URLs pointing at `127.0.0.1`;
  `TachiyomiSourceAdapter` repoints loopback hosts at the source's `baseUrl`.
- **The reader stopped reopening itself** when backing out mid-download, and the
  "Downloads" nav label stopped wrapping onto two lines.

### Still broken, low value — the `fakku.cc` mirror

SpyFakku's mirror list offers three. `hentalk.pw` works. `fakku.cc` times out
after 30s connecting to 162.255.119.128:443 — dead or blocked upstream, nothing
to fix in this app. `fakkuonion.airdns.org:4096` serves titles and the same
loopback image URLs, which the repoint now handles. **Leave the mirror on
`hentalk.pw`.**

### Cheap wins if you want something self-contained

The Downloads tab has still never been checked in airplane mode, which is the
only thing it exists for. Failed-download retry is likewise only lightly
exercised.

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

Termux — **the user strips any `.txt` suffix themselves before running this**, so
the loop copies `*.kt` as-is. Don't put `basename ... .txt` in it. This handles
any number of files at once:
```
cd ~/MangaReader && for f in ~/storage/downloads/*.kt; do cp -v "$f" app/src/main/java/com/mangareader/app/; done && git diff --stat && git add -A && git commit -m "message" && git push
```

`cp -v` prints each copy so the count can be eyeballed before committing, and
the `git diff --stat` in the middle prints the expected insertion count; if a
copy silently failed the `&&` chain aborts at the empty commit instead of
pushing nothing.

**Careful with the glob**: `*.kt` takes *everything* in Downloads, including
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

As of the source-visibility commit, verified on device:

- **26/26 extensions load, 95 sources total.**
- Browsing, chapter lists, and page rendering work end to end.
- Library with categories works.
- Per-source search + pagination work.
- Extension index filter and cross-source global search work.
- Extension sources are cached between calls (`3e28e81`).
- Sources and Extensions tabs match Mihon's layout (`646d959`).
- Per-source settings from `ConfigurableSource` (`03cabb3`).
- Global search limited to pinned sources, with a toggle (`0023c81`).
- The Compose UI is split across nine files (`4fc763e`) — see §4.
- Extension **updates** are detected and offered (`e1913c2`).
- Series screen: cover backdrop, metadata, chapter dates, Start/Resume (`364d1ce`).
- Back from a series returns where you came from (`7ff563f`).
- The reader opens before the chapter finishes downloading.
- **Chapter downloads** (`44ae521`) and **offline reading** (`eca6caa`) work —
  download a chapter, go into airplane mode, read it.
- **Source visibility** screen + global search scope chips (`1f60936`).
- **Downloads run in a foreground service** with a persistent queue, so they
  survive the app being backgrounded or swiped away.
- **Failed downloads say why** — the page exception is carried up instead of
  swallowed — and can be retried from the queue screen.
- **Downloads tab** lists series with chapters saved on device, and opens them
  offline.
- **Cloudflare challenges are solved** — JS ones headlessly, interactive ones
  through a visible WebView the user taps through. HentaiSco browses.
- **Covers and page images go through the extension's OkHttp client**, so they
  carry cookies, the User-Agent and the Cloudflare interceptor.
- **Sources that hand out loopback image URLs work** (SpyFakku).
- Backing out of a chapter mid-download no longer reopens it.
- **The reader has overlay bars, a chapter picker and a settings sheet** — but
  see §0 before trusting any of it.

Added this session, all verified on device:

- **A Settings screen** reached from More, with eight sections (§4).
- **Backup and restore** — every SharedPreferences store, as JSON.
- **Automatic backups** on a WorkManager schedule.
- **A user-chosen storage location** for downloads and backups, via
  `MANAGE_EXTERNAL_STORAGE` rather than SAF (§5 — this is the load-bearing
  decision of the session).
- **A readable download tree**: `<picked>/Yomu/downloads/<Source>/<Series>/<Chapter>`,
  with a reorganise action for chapters in the old flat layout.
- **One back button.** `Ui.BackButton` replaced three different affordances.

`MainActivity.kt` is **1096 lines** — the Activity, `YomuApp`, and the shared
prefs helpers. `ReaderScreen.kt` is **634**, `SettingsScreens.kt` is **~1335**.

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
coil `2.7.0`, telephoto zoomable-image, okhttp 4.12, documentfile,
**`androidx.work:work-runtime-ktx:2.9.1`**.

WorkManager self-initialises through `androidx.startup` — there is no
`Configuration.Provider` and no manifest entry. It exists solely so automatic
backups fire on a schedule rather than when the app happens to be opened. It
does drag Room and `androidx.sqlite` in transitively, which is worth knowing
given that the backup design rests on this app *not* having a database.

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
- **Deleted:** `WebViewInterceptor.kt`, `JavaScriptEngine.kt`,
  `NetworkPreferences.kt` — they pulled in moko-resources (`MR`), WebView utils,
  QuickJS, and `PreferenceStore` from Tachiyomi's `:core`.
- **`CloudflareInterceptor.kt` was deleted with them and later written back**, as
  a much smaller class that doesn't need any of that. Don't restore the original;
  don't delete this one either.
- **Added:** `ClearanceUserAgents.kt` — a per-host record of which User-Agent
  earned Cloudflare clearance. Not part of the vendored API; it exists because
  the UA interceptor in `NetworkHelper` has to consult it.
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
| `Downloads.kt` | Page store on disk (cache vs download) + `ChapterCache` + `formatBytes()`. |
| `DownloadQueue.kt` | Process-wide download queue: Compose state + JSON persistence. |
| `DownloadService.kt` | Foreground service that drains the queue. Actions: pause / resume / skip / cancel-all. |
| `DownloadIndex.kt` | Maps downloaded chapters back to their series, so the Downloads tab can exist. |
| `MainActivity.kt` | Activity, `YomuApp` (all shared screen state), prefs helpers. |
| `AppPrefs.kt` | `ThemeMode`, `AppTheme` (theme + FLAG_SECURE), `yomuColorScheme()`. |
| `Backup.kt` | Whole-prefs backup/restore, `BackupFrequency`, `AutoBackupWorker`. |
| `StorageLocation.kt` | The chosen folder, All-files-access checks, tree-URI→path, the mover. |
| `DownloadPaths.kt` | chapterId → `<Source>/<Series>/<Chapter>`, plus `reorganiseDownloads()`. |

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
| `ReaderScreen.kt` | `ReaderScreen`, its overlay bars, chapter picker and settings sheet |
| `ReaderPrefs.kt` | `ReaderSettings` + the enums + load/save. Not a screen |
| `WebViewScreen.kt` | `ChallengeWebViewScreen` — the visible Cloudflare WebView |
| `MoreScreens.kt` | `MoreTab`, `HistoryScreen`, `ExtensionReposDialog`, `CategoryManagerDialog`, `SourceDialog` |
| `DownloadQueueScreen.kt` | `DownloadsTab` and `DownloadQueueScreen` — both read `DownloadQueue` directly rather than taking it as parameters |
| `SettingsScreens.kt` | `SettingsScreen` and all eight section pages. Which section is open is local state, not a routing branch |

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

suspend fun loadPages(chapter: Chapter): List<File>
suspend fun loadPagesProgressively(
    chapter: Chapter,
    persist: Boolean = false,
    onUpdate: suspend (List<File?>) -> Unit
)
val supportsDownload: Boolean get() = false
fun rehydrateChapter(chapter: Chapter): Chapter = chapter
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

### Reading, downloads, and offline

Three separate mechanisms, easy to confuse:

**1. Progressive page loading.** `loadPagesProgressively` publishes a list of
empty slots first — one per page, all null — which is everything the reader needs
to open. Pages then download **4 at a time in source order**, republishing after
each batch. Before this the reader waited for the last byte of the last page,
which on a 30MB chapter is a long stare at nothing.

`pages` is therefore `List<File?>`, and the reader distinguishes three states:
present, pending, failed. `stillLoading` is what tells pending from failed — a
blank page mid-download shows a spinner, the same blank after loading finishes
says the page couldn't be loaded.

**2. Cache vs download** (`Downloads.kt`). Reading writes pages under `cacheDir`,
which Android evicts when it wants space. Downloading writes them under
`filesDir`, which only the user reclaims. `loadPagesProgressively(persist = ...)`
picks. A chapter that is fully downloaded is served **straight off disk** — no
page-list request, no image requests.

A download counts as complete only when every page succeeded and a `.complete`
marker is written. A partial download stays unmarked on purpose, so it's never
trusted; since `downloadPage` skips files already present, restarting resumes.

Directories are named by an MD5 of the chapter id. The earlier code used
`chapter.id.hashCode()` — 32 bits, which collides far too readily to key stored
files on once they're permanent.

**3. `ChapterCache`.** Downloading pages is *not* enough to read offline: opening
a series calls `listChapters`, a network request that fails first. `ChapterCache`
keeps the last chapter list a source returned, as JSON under
`filesDir/chapterlists/`. `YomuApp.chaptersWithFallback()` wraps all three open
paths and only errors when the fetch fails *and* nothing is cached.

`Chapter.handle` (the extension's `SChapter`) can't be serialised, so only
app-owned fields are stored and `Source.rehydrateChapter()` rebuilds a handle from
the chapter id — the same trick `restoreSeries` uses for `SManga`. Without it a
cached chapter still *reads* when downloaded (the page store is checked before
the handle is) but couldn't be fetched once back online.

**4. The download queue and service** (`DownloadQueue.kt`, `DownloadService.kt`).
Downloads used to run in `YomuApp`'s coroutine scope and died with the Activity;
they now run in a foreground service that outlives it.

`DownloadQueue` is a process-wide singleton holding Compose snapshot state —
`items`, `progress`, `activeId`, `paused`, `tick`. The service writes to it from a
background thread and any composable reading it recomposes; both are in the same
process, so there is no flow, binder or broadcast anywhere in this path. The queue
is mirrored to `filesDir/download_queue.json` on every change and reloaded by
`App.onCreate`, which runs for *any* process entry point — including the system
restarting the service on its own (it's `START_STICKY`).

A `DownloadItem` stores only ids: `Source` and `SChapter` don't serialise, so the
service resolves the source via `SourceManager.listAllSources` and rebuilds the
handle with `Source.rehydrateChapter`, exactly as `ChapterCache` does.

Two details worth keeping:
- **Failures leave the queue** into `DownloadQueue.failed`, with the reason. A
  chapter whose source was uninstalled would otherwise sit at the head blocking
  everything behind it. Its partial pages stay on disk, so Retry resumes.
- **The page exception is carried up.** `loadPagesProgressively` used to wrap
  each page in `runCatching { }.getOrNull()`, which made a 403, a timeout and a
  missing image URL indistinguishable — the download path could only report
  "some pages failed". It now keeps the *first* failure (with four pages in
  flight, one 429 usually takes its neighbours with it) and throws
  `ChapterDownloadException` when `persist = true`, so the queue screen can say
  "3 of 18 pages failed — HTTP error 429". The reader path is untouched: a bad
  page there is still drawn as a broken slot with the rest readable.
- **A wake lock is held while fetching.** A foreground service keeps the *process*
  alive but not the CPU; without it the device suspends mid-transfer with the
  screen off. It's released while paused and on a 4-hour timeout as a backstop.

`DownloadService.start()` is only ever called from a visible Activity on a user
action, which is what keeps the foreground-service start legal on Android 12+.

### `CloudflareInterceptor` (in `:source-api`)

Answers Cloudflare's JavaScript challenge in a headless WebView and retries the
request. It is much shorter than it sounds, for one reason: **`AndroidCookieJar`
is backed by `android.webkit.CookieManager`**, the same store a WebView writes to.
There is no cookie plumbing — the WebView solves the challenge, `cf_clearance`
lands in the browser cookie store, and OkHttp picks it up because it was already
reading from there.

Five things not to break:

- **The WebView keeps its own User-Agent.** This is the one that took a session
  to learn, and it reads backwards until you see why. `cf_clearance` is bound to
  the UA that earned it, so the WebView and OkHttp must agree — and the first
  attempt made them agree by forcing the WebView to claim the app's default,
  which is a *desktop Chrome* string. A challenge evaluated inside an Android
  WebView weighs platform, touch support, screen and renderer alongside the UA.
  They contradicted each other, which is precisely what an interactive challenge
  exists to catch, so it was unpassable: the visible version looped on the
  checkbox forever. Agreement is now reached from the other end — the WebView is
  honest, and whatever string passes is recorded in `ClearanceUserAgents`.
- **`ClearanceUserAgents` overrides even an extension's own UA**, for hosts it
  has an entry for. That looks rude and is nevertheless right: clearance is
  rejected under any other string, so honouring the extension's preference would
  throw away a challenge the user just solved by hand. It is scoped per host and
  walks up the domain, because clearance issued for `example.com` covers
  `cdn.example.com`.
- **The retry rebuilds the request.** The UA interceptor runs *before* this one,
  so the request in hand still carries the pre-clearance UA. Retrying it
  unchanged presents a different string than the one that just passed and is
  rejected — which would have made a successful solve look like a failure.
- **On failure it returns the original 403** rather than throwing. That response
  still carries the headers `awaitSuccess()` reads to say "blocked by Cloudflare",
  which is more use than anything the interceptor could invent. The browse screen
  turns that message into the "Open in WebView" action.
- **It blocks an OkHttp thread and polls the cookie store, one host at a time.**
  A challenge involves several navigations, so `onPageFinished` is not the signal
  — the cookie appearing is. There's a main-thread guard so this can never
  deadlock. The per-host lock exists because **image loading runs through this
  client too**: a browse grid can 403 twenty times at once, and unsynchronised
  that is twenty WebViews and twenty separate thirty-second waits.

### The visible WebView (`WebViewScreen.kt`)

`ChallengeWebViewScreen` is the same idea with a user attached, opened from the
"Open in WebView" action on the browse error. It polls for the cookie for the
same reason the interceptor does, records the UA that passed, and closes itself.

Two details that are easy to get wrong:

- **It only auto-closes on a cookie that appears while it is open.** One that was
  already there proves nothing — the request 403'd while holding it — so closing
  on it would bounce straight back to the same error. The "Done" button covers
  that case manually.
- **`onSolved` re-runs the browse**, it doesn't just close. The clearance cookie
  is in the store OkHttp already reads, so the retry is ordinary.

### Images and Coil

`App` implements `coil.ImageLoaderFactory` and hands Coil
`Injekt.get<NetworkHelper>().client`.

Coil builds its own OkHttpClient by default, and that client is a plain one: no
cookie jar, no User-Agent, no Cloudflare interceptor. So a protected source would
list its series correctly — catalogue HTML goes through the extension's client —
and then show a grid of empty placeholders, because every cover went out naked
and 403'd. Sharing the client fixes covers, thumbnails and anything else Coil
fetches, and shares the connection pool and cache rather than duplicating them.

**Page images do not go through Coil.** `TachiyomiSourceAdapter.fetchPage` calls
`HttpSource.getImage(page)` directly, so anything that has to apply to both has
to be done in two places, or upstream of both.

`TachiyomiSourceAdapter.repointFromLoopback()` is one such thing. SpyFakku's
mirrors return covers and pages as `http://127.0.0.1/image/<hash>/<n>` — the
site's application building absolute URLs from its own bind address instead of
the public host it's proxied behind. The path and query are correct; only the
origin is wrong, so it swaps in the source's `baseUrl`. It runs for every source
because no legitimate source can mean loopback (that address is the phone), and
it leaves a genuinely loopback `baseUrl` alone rather than rewriting a correct
port into a wrong one.

**5. `DownloadIndex`** is what the Downloads tab is built on. `Downloads` names
each chapter directory after an MD5 of the chapter id, and that hash is one-way —
given the folder there is no way back to the series it belonged to. So the service
writes a record (`chapterId -> sourceId, seriesId, title, cover`) as each chapter
completes.

It is a **cache, not the truth**: `list()` drops any record whose chapter is no
longer complete on disk, which keeps it correct across the delete paths that don't
know it exists (a series screen's "Delete downloads", "Delete all" in More, the
user clearing app storage). It also **backfills from the library** — for a saved
series, `ChapterCache` is keyed by the same hash of the series id, so its stored
chapter list can be re-checked against disk. Chapters downloaded before this index
existed, for series never saved to the library, stay invisible; nothing short of
re-downloading recovers those.

Opening from this tab goes through `openFromDownloads`, not `openFromLibrary`:
that one starts with `restoreSeries`, a network call, and the point of the tab is
that it works in airplane mode. The details fetch is allowed to fail into a
title-and-cover-only `Series`, with chapters read straight from `ChapterCache` —
enough for every downloaded chapter to open, since the page store is consulted
before the handle is.

### Storage location, backups, and the download tree

Three things that landed together and are easiest to understand in that order.

**`StorageLocation`** owns the folder the user picked. It is a **real filesystem
path**, not a SAF tree — see §5, this is the decision the whole session turns
on. `base()` resolves to `<picked>/Yomu`, memoised against the stored string
because `Downloads.dirFor` is called once per page and resolving involves a
permission check and a write probe. It falls back to `filesDir` whenever the
chosen folder can't be written (permission revoked, card unmounted, folder
deleted by a file manager) rather than failing: a download that errors because a
card is missing is a bug report, one that quietly lands in internal storage is a
full library and a wrong-looking settings row.

The picker is still `ACTION_OPEN_DOCUMENT_TREE`, because it's the folder UI
people know; `pathFromTreeUri()` maps `primary:Manga/Yomu` back to
`/storage/emulated/0/Manga/Yomu`, and returns null for providers like Drive that
have no path behind them.

**`Backup`** serialises **every SharedPreferences store, verbatim** — the app
store `manga_reader` plus every `source_<id>` written by `SourceSettings`. This
app has no database: the library, categories, history, read flags, resume
positions, reader settings, theme, pinned sources, hidden languages, repo list
and per-source logins are all JSON strings or scalars in those files. Copying
them copies all of it with no per-model serialiser to write and, more to the
point, none to forget to update when a field is added. Each value carries a type
tag, because `getAll()` erases whether a number was an Int or a Long and a wrong
guess throws `ClassCastException` on a value that looks fine in the file.

Two rules in there worth keeping:

- **Restore validates the entire payload before clearing anything.** Wiping and
  then discovering the file was truncated turns a bad file into data loss, which
  is the exact thing the feature exists to prevent. It also filters store names
  to `manga_reader` / `source_*`, so a hand-edited backup can't name arbitrary
  pref files and have them overwritten.
- **The storage-location key is the one thing excluded from the payload.** A
  path is device-local even when it looks portable — `/storage/1A2B-3C4D/Manga`
  is a card that exists in one phone. Restore keeps whatever the local install
  had, then re-arms the WorkManager schedule to match the restored frequency.

Restore ends by calling `recreate()`. Per §5 that is normally the thing to avoid
at all costs; here a full reset to the Library is precisely the intent, because
every bit of `YomuApp`'s state was built from prefs that have just been replaced.

**`DownloadPaths`** is what makes downloads readable. `Downloads` names a chapter
directory after an MD5 of its id, which is one-way — fine while downloads were
only reached through the app, useless once they live in a folder the user opens
in a file manager. So a chapter gets `downloads/<Source>/<Series>/<Chapter>`, and
this stores which. It has to be a stored mapping: a sanitised title can't be
turned back into a chapter id, and `dirFor` is handed nothing else.

`DownloadService` calls `register()` at the line where it resolves the source —
the only point where the source's *display name* is known, since the queue
stores ids and the extension may be uninstalled by the time anything else asks.

Three things keep it from being a single point of failure:

1. **Every chapter folder carries a `.chapterid` file**, so the tree describes
   itself. `DownloadPaths.load()` scans and rebuilds from those markers when its
   index file is missing — which covers clearing app storage while the downloads
   sit safely outside it. Losing the index costs a directory walk, not a library.
2. **It lives in `filesDir`**, so a file manager can't delete it and moving the
   storage location doesn't disturb it.
3. **`Downloads.dirFor` reads both layouts**, preferring the registered path and
   falling back to the flat `<md5>` directory. A half-finished reorganisation is
   therefore a valid state rather than a broken one, and chapters downloaded
   before the tree existed keep working indefinitely with nothing migrated.

`reorganiseDownloads()` files the old ones. It can only place a chapter the app
can still name, and `DownloadIndex` recovers more than it was written with — for
any series in the library it re-derives the mapping from `ChapterCache`. Whatever
it can't name is left where it is and keeps working. **Run it with the queue
empty**: it moves folders the service writes into.

Folder names are sanitised against *Windows'* illegal set, not Linux's, because
the point is opening these on a PC — a `?` in a title Android took happily is a
folder that can't be copied off the phone. Collisions get a short id hash rather
than a counter, so a rebuild in a different order can't hand two series the same
folder.

### The reader

Controls are hidden until a tap, which then raises a top bar (back, series,
chapter) and a bottom bar (Prev · Chapters · Settings · Next). Settings open in a
three-tab sheet: **Layout / Screen / Colour**.

`ReaderPrefs` is one global store, not per-series. Mihon scopes reading mode and
rotation per manga over a global default, which is better for a library mixing
manga and webtoons — it needs a second store keyed by series id and a "use
default" state distinct from every real value. The enums carry a `key`, so
adding that overlay later is additive rather than a rewrite.

**Settings are saved on every edit**, not on dismiss. The sheet can be swiped
away and the screen can be left by the system; a setting lost because the sheet
was closed the wrong way is a bug nobody reports.

**What is deliberately not there.** Crop borders, split wide pages, rotate wide
pages, and tap-zone layouts. The first three need to inspect and cut the bitmap
and the last needs a gesture model this screen doesn't have — they are page
pipeline work, not switches. Shipping them as toggles that do nothing is worse
than their absence, because it costs a build cycle to discover.

**Modes.** Paged uses `HorizontalPager` with `reverseLayout` for right-to-left;
long strip is a `LazyColumn` whose items are `fillMaxWidth` with no height, so
pages size to their own aspect ratio. Giving them a fixed height letterboxes
every page and reinstates the gaps the mode exists to remove.

**Grayscale + invert is a precomputed matrix**, not two filters composed:
composing needs an operator whose argument order is easy to get backwards, and
inverted Rec. 709 luminance is short enough to write out. It is also the one
combination most likely to be wrong while each alone looks right.

**Fullscreen is keyed on the controls as well as the setting.** Raising the bars
while the status bar stays hidden puts the title under the clock.

**`me.saket.telephoto:zoomable-image-coil` is in `build.gradle.kts` and unused.**
Pinch-zoom is one swapped composable away and was left out only to avoid
introducing an unfamiliar API inside an already-large uncompiled change.

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

`App` also implements `coil.ImageLoaderFactory` and returns an `ImageLoader`
built on `Injekt.get<NetworkHelper>().client` — see §4. The lambda form defers
building `NetworkHelper` until the first image is requested, so this doesn't drag
network setup into `onCreate`.

### UI structure
Bottom nav, 5 tabs:
0. **Library** — saved series grid, category filter chips
1. **Browse** — sub-tabs *Sources* and *Extensions* (below)
2. **History**
3. **Downloads** — series with chapters on device, openable offline
4. **More** — incognito, download queue, Categories, **Settings**

#### Settings (`SettingsScreens.kt`)
Reached from More. An index of eight sections, each opening onto its own page:
Appearance, Library, Reader, Downloads, Browse, Data and storage, Security and
privacy, Advanced.

**Which section is open is local state inside `SettingsScreen`, not a branch of
`YomuApp`'s routing chain.** That chain already encodes real navigation rules in
an `if / else if`; adding eight arms to it for the inside of one screen would
put them in the worst possible place. The chain gained one boolean instead, and
back pops the section before it pops the screen.

**Rows are led by emoji, not icons** — `material-icons-core` has none of
palette / storage / shield / sliders, and the bottom nav already does this.

Incognito deliberately appears in **both** More and Settings → Security: it's
the one preference that gets switched on for a few chapters and back off, and
that shouldn't be three taps deep. Both read the same key, and the More tab is
disposed while Settings is open, so it re-reads rather than going stale.

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

### Source visibility

Two stores in `SourcePrefs`, and they work in **opposite directions** — which is
the thing to get right before editing either:

| Store | Holds | Empty means |
|---|---|---|
| `hiddenSources` | ids switched **off** | nothing hidden |
| `enabledLangs` | languages switched **on** | everything hidden |

`hiddenSources` is negative so a source added by a new extension appears without
anyone enabling it. `enabledLangs` is positive because it needs a *default*:
`DEFAULT_LANGS = {Local, Multi, English}`. 95 sources across thirty-odd languages
is unusable out of the box, and almost none of them are readable by one person.
"Local" is in the set because it isn't really a language — it's the local-folder
group, and hiding that by default would be baffling.

The unset-vs-empty distinction carries real meaning: `getStringSet(key, null)`
returning null means "never chosen" and yields the default, while a stored empty
set means the user switched everything off and is preserved. Don't collapse those.

The cost of the positive store: a genuinely new language — installing the first
Korean extension, say — arrives switched off.

`SourcePrefs.isVisible(id, lang, hidden, enabledLangs)` is the single predicate.
It gates the Sources list **and** the global search fan-out — hiding a source
should stop it being queried, not just stop it being listed.

`SourceFilterScreen` (in `BrowseScreen.kt`) is the UI: an "All sources" master
switch with an *N of M shown* count, a switch per language, a checkbox per
source. It renders inside `BrowseTab` behind a flag rather than as a branch of
the routing chain — that chain is delicate enough already.

### Routing chain in `YomuApp` — order is load-bearing

A single `if / else if` chain, in this order:

1. `challengeUrl != null` → `ChallengeWebViewScreen`
2. reader (`activeChapterIdx` + pages)
3. `activeSeries != null` → `SeriesScreen`
4. `globalSearchOpen` → `GlobalSearchScreen`
5. `activeSource != null` → `LibraryScreen` (this is the **per-source browse**
   screen, despite the name — the Library *tab* is `LibraryTab`)
6. `downloadsOpen` → `DownloadQueueScreen`
7. `settingsOpen` → `SettingsScreen`
8. else → `Scaffold` with the bottom nav

**6 sits above 7 on purpose and the ordering does the work.** The queue is
reachable from More *and* from Settings → Downloads. Leaving `settingsOpen` set
while the queue renders means backing out of the queue falls through to
whichever of the two it was opened from — two booleans read in order, instead of
a "where did I come from" flag.

Known wrinkle: `SettingsScreen`'s `openSection` is `rememberSaveable`, and the
composable leaves the composition entirely while the queue is up, so returning
from the queue lands on the Settings index rather than the Downloads page.
Hoisting that one string next to `settingsOpen` fixes it without adding a branch.

**Branch 1 was safe to put at the top**, which is worth understanding before
adding anything else there. It is gated on state that is null in every other
flow, and clearing that state drops back onto whatever was underneath with
nothing else touched — no branch below it has to know it exists. A branch that
needed to *coordinate* with the ones under it would not be safe in that
position.

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
- Three chips: **Pinned** / **All** are one scope choice; **Has results** is a
  display filter. Sources returning nothing are *kept* in `globalResults` and
  shown as a "No results" row when that chip is off — that's what distinguishes
  "found nothing" from "wasn't searched". The counter reads *N with results*.

---

## 5. Hard-won lessons — don't repeat these

### A 400 from `cdn.manhwatoon.me` is an HTTP/2 problem, not throttling

`cdn.manhwatoon.me` (WP-manga / Madara) answers **HTTP 400** on a minority of
page requests. Symptoms: well-formed URLs, a scattered subset of any chapter
failing (1 of 21, 9 of 45, 12 of 36), and the same URL succeeding later.

**This was misdiagnosed twice before it was solved — both dead ends are worth
knowing.**

1. *"It's rate limiting."* Plausible, because retrying by hand minutes later
   walked a chapter to completion a few pages at a time. So page fetches got 4
   attempts with exponential backoff. It made downloads dramatically slower and
   failed anyway — 12 of 36 pages after every retry.
2. *"The URLs are malformed."* Ruled out by carrying the URL up in
   `PageDownloadException`: plain ASCII, nothing to encode.

3. *"It's HTTP/2 multiplexing."* Closer — forcing HTTP/1.1 and halving
   `PAGE_CONCURRENCY` cut the failure rate from 12 of 36 to 7 of 39 — but it
   didn't stop it, because OkHttp still pools and **reuses** connections.

The cause appears to be **per-connection request limits**: the CDN starts
answering 400 once a single connection has carried enough requests. The failure
rate tracks requests-per-connection and nothing else — 36 requests on one shared
HTTP/2 connection gave 33% failures, 39 across two HTTP/1.1 connections gave 18%
— and it explains the observation that killed every other theory: an immediate
retry fails because it lands on the *same pooled socket*, while a manual retry
minutes later gets a fresh one.

The fix that followed — `recycleConnections()` in `TachiyomiSourceAdapter`:
`client.connectionPool.evictAll()` before every retry and every
`CONNECTION_RECYCLE_BATCHES` batches, so no connection carries more than about 8
requests — has since been run, and took the failure rate from 18% to roughly
3-8% without reaching zero. `.protocols(listOf(Protocol.HTTP_1_1))` and
`PAGE_CONCURRENCY = 2` are kept from the previous attempt; all three help, none
is sufficient. That is now **four** partial fixes on one bug, which is the
signal to stop tuning and read the response body — see §0.

General lessons:

- **An immediate retry that fails where a later one succeeds is telling you about
  connection state**, not about rate — and "connection state" survives switching
  protocol version, because pooling is orthogonal to it.
- **Each fix that partly worked was evidence, not success.** The drop from 33% to
  18% was the clue that identified the real variable; treating it as a near-miss
  and tuning the same knob harder would have missed it.
- **400 is still in `TRANSIENT_HTTP_CODES`** — cheap insurance, not the fix.

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

**Offline needs more than the pages.** Downloading a chapter's images looked
like it was enough; it wasn't, because opening the series calls `listChapters`
first and that's a network request. Anything that should work offline has to have
*every* step on its path checked, not just the obvious one.

**Don't key persistent files on `hashCode()`.** It's 32 bits. Fine for a
throwaway cache directory, not for something the user is told they have
downloaded. `Downloads` uses an MD5 hex of the id.

**Compose state must be written from the main thread.** The page loader publishes
from its IO context, so `openChapter` hops with `withContext(Dispatchers.Main)`
before touching `pages`. A callback that crosses dispatchers is easy to miss.

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

The reader rewrite ignored this in favour of a tight hand-picked list, and the
only compile error in 630 new lines was a missing
`androidx.compose.foundation.verticalScroll` — an import the *old* version of the
file had. A curated list is cleaner and has no safety margin; the wildcard block
is ugly and cannot fail this way. With no compiler in the loop, take the ugly
one.

**Watch for missing braces when editing `MainActivity.kt`.** A dropped `}` in a
`DisposableEffect` produced ~40 cascading errors ("Modifier 'private' is not
applicable to 'local function'"). That signature = unclosed lambda earlier.

**When replacing a whole function in `MainActivity.kt`, check the line above it.**
Replacing `@Composable` + `private fun BrowseTab(` left the function's
`@OptIn(ExperimentalMaterial3Api::class)` stranded on top of the next
declaration. Annotations sit above the `@Composable`, outside the obvious
boundary.

**CRLF warnings on every push are benign** (LF in repo, CRLF in working copy).

### A client that lies about what it is cannot pass an interactive challenge

Forcing the WebView to claim the app's desktop User-Agent so it matched OkHttp
made the checkbox unsolvable, because everything else the challenge reads said
Android phone. **The symptom was a loop, not a refusal** — verify, pass, get
asked again, forever — and a loop reads as a broken bypass rather than as a
rejected client, which is why it wasn't obvious.

The general form: when two things must agree, check *which* value they agree on
is defensible, not just that they agree. Here the fix was to make the honest side
win and record what it said, rather than making the honest side repeat a lie.

A corollary worth keeping: the headless interceptor had been setting that same
desktop UA all along, so the JS challenge had probably been failing for this
reason too. One bad assumption was producing two unrelated-looking symptoms.

### Coil is a second HTTP client

Everything the app teaches its own OkHttp client — cookie jar, User-Agent,
interceptors — Coil knows nothing about until told. A protected source therefore
listed its titles perfectly and showed no covers, which looks like a parsing bug
and is a networking one. `App` is now an `ImageLoaderFactory`; see §4.

Ask of any new network behaviour: does this need to apply to images too? There
are **three** paths, not one — the extension's client for catalogue and chapter
HTML, Coil for covers, and `fetchPage` for reader pages.

### A failure that renders as an empty box is unfixable by guessing

"No covers on this source" was consistent with a 403, a 404, an unresolvable
host, and an undecodable format — four different fixes. Two speculative rounds
went nowhere. Printing the URL and Coil's error into the placeholder in debug
builds (§6) produced the actual answer on the next screenshot: the URLs pointed
at `127.0.0.1`, which was in *none* of the four guesses.

This is the same lesson §0 records for the manhwatoon 400s, where the response
body still hasn't been read. **When a slow build/install loop meets an ambiguous
symptom, spend the cycle on making the symptom specific, not on a candidate fix.**

### Don't re-assert UI state from an async callback

`openChapter`'s progressive loader ran `if (activeChapterIdx != index)
activeChapterIdx = index` on every partial publish. That reads as "make sure the
reader is showing" and behaves as "put it back if the user closed it" — so
backing out of a chapter mid-download reopened it, repeatedly. It *looked* fine
whenever the download had already finished, which is why the close button seemed
to work and the back button didn't: same handler, different timing.

Fixes that both matter: the reader opens on the **first** publish only, and
closing **cancels the job**. Cancellation then has to be rethrown rather than
caught as a failure, and `isLoading` has to move into a `finally`, or a cancelled
load leaves the spinner up forever.

### This app cannot survive an Activity recreation

**Read this before adding anything that touches the window, the orientation, or
the manifest.** Every piece of `YomuApp`'s state is in `remember`, not
`rememberSaveable` — and much of it *can't* be saveable, because the active
source, the series handle and the loaded page files are objects that don't go in
a Bundle. So a recreation is not a blip that Compose smooths over. It is a full
reset to the Library tab, discarding whatever was being read.

The reader's rotation setting walked straight into this. Setting
`requestedOrientation` **is** a configuration change, and with no
`android:configChanges` on the Activity, Android destroys and recreates it. The
result was a loop: open chapter → reader composes → sets landscape → recreation
→ Library. Every time, with no way to reach the setting and turn it off, because
the reader exited before it could be tapped.

The manifest now declares
`orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode`,
which makes those changes something Compose absorbs by recomposing. That also
fixed the older, quieter version of the same bug: rotating the phone mid-chapter
already lost your place, and nobody had connected the two.

Two things to carry forward:

- **The reviewing question was the wrong one.** The rotation effect was checked
  for whether it restored the orientation on the way out — it did — and never for
  what setting it does to an Activity holding all its state in `remember`. Local
  correctness said fine; the blast radius was the whole app.
- **Recreation is still possible** for reasons the manifest can't cover: low
  memory, "don't keep activities", a locale change. Hoisting state into a
  `ViewModel` or a saveable holder is the real fix and is §7's structural item.

### Sideloading is a design input, not just a distribution choice

Letting the user pick any folder on Android 11+ looks like it forces SAF, and
SAF would have forced a page to stop being a `java.io.File` and become a `Uri` —
a type that lives in `Source.loadPages`, `loadPagesProgressively`,
`TachiyomiSourceAdapter`, `LocalSource`, `DownloadIndex`, `YomuApp`'s reader
state and `ReaderScreen`'s parameter list. Six files, every read and write
rerouted through `ContentResolver`, with no compiler in the loop.

`MANAGE_EXTERNAL_STORAGE` gets the identical user-visible result — any folder,
visible in a file manager, surviving a reinstall — and **`Downloads.root()` was
the entire change**. Nothing below it moved.

The only reason Mihon can't do this is that Google restricts the permission on
Play. This app ships from GitHub Releases. The general lesson: constraints
inherited from a reference implementation are worth re-deriving rather than
assuming, because they may be *its* constraints and not yours. The cost here was
three lines of manifest and a permission prompt.

### A store addressed by a hash can't grow a human-readable layout for free

`Downloads` keys chapter directories on `MD5(chapterId)`, which the handoff has
described as one-way since it was written. That was a stated property, not a
hidden one, and it still wasn't obvious that it made `<Source>/<Series>/<Chapter>`
a *stored mapping* problem rather than a path-formatting one — `dirFor` receives
a chapter id and nothing else, and no amount of care with names recovers the
other two levels.

What made it tractable was that `DownloadItem` already carried `sourceId`,
`chapterName`, `seriesTitle` and `seriesId` at enqueue time, for `DownloadIndex`'s
benefit. Worth checking what the existing types already know before adding a
lookup: the data was one line away from where it was needed.

The general form: **when a new feature needs the inverse of an existing one-way
function, the answer is a record written at the point the information exists**,
and the follow-up question is what happens when that record is lost. Here it's a
marker file inside each folder, which makes the store self-describing and turns
index loss into a directory scan.

### Don't wipe before you've finished reading

Restore originally cleared each SharedPreferences store and then decoded into it,
discovering a truncated or malformed payload partway down — turning a bad file
into exactly the data loss the feature exists to prevent. Validating the whole
payload first and only then clearing is four extra lines.

Same shape as the `move` fallback in `StorageLocation`: `renameTo` across mount
points fails by *returning false* rather than throwing, so a copy-then-delete
fallback is mandatory or an internal→SD-card move silently moves nothing.

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

**The `*.kt` loop only serves `:app`.** It copies into
`app/src/main/java/com/mangareader/app/`, and a growing share of changes now land
in `:source-api` — `network/`, and `network/interceptor/` below it. A file dropped
in the wrong module fails the build with an unresolved reference, which reads as
a code error rather than a copy error. Name the files explicitly and give each
destination its own `cp` when a change spans modules.

Non-Kotlin files have their own destinations again: `AndroidManifest.xml` goes to
`app/src/main/`, and this file to the repo root. Neither is matched by any `*.kt`
glob, so both are easy to leave sitting in Downloads.

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

**A cover that fails to load prints why, in debug builds.** `CoverImage` renders
the tail of the URL and Coil's error into the placeholder when
`BuildConfig.DEBUG` is set, in red. This exists because a failed cover and a
cover the source never supplied are the same grey box otherwise — see §5. CI
builds debug on push, so the installed APK always has it; release builds don't.

---

## 7. Known limitations / next steps

Roughly in order of value:

1. **Downloader gaps.** The foreground service, queue, retry and Downloads tab
   are done (§4) but only lightly exercised — see §0. What's left versus Mihon: no reordering in the queue (strictly
   FIFO), no per-series grouping in the queue screen, and no auto-download of
   new chapters. Retry/backoff settings are global, not per-source — see the
   note on manhwatoon in §5 for why that might eventually need to change.
   Android 14 also caps `dataSync` foreground services at ~6 hours a day, which a
   queue left paused indefinitely would burn through; pausing releases the wake
   lock but not the service.
2. **Cloudflare costs 30 seconds before the error appears.** A source behind an
   interactive challenge burns `CloudflareInterceptor`'s full timeout on the
   headless attempt that cannot succeed, and only then shows the error carrying
   the "Open in WebView" action. Shortening the timeout once a host is known to
   need a human — or skipping the headless attempt for hosts with a
   `ClearanceUserAgents` entry that still 403 — would make that instant.
3. **Coil still sends no `Referer`.** Images now go through the extension's
   client, so they carry cookies and the UA, but a source whose CDN checks
   `Referer` will still 403 its covers while its pages load fine — `fetchPage`
   goes through `HttpSource.getImage`, which applies the source's headers.
   Nothing has hit this yet. If it does, the fix is a Coil `Fetcher` or an
   interceptor on a derived image client, not a change to the shared one.
4. **Reader features that need the page pipeline.** Crop borders, split wide
   pages, rotate wide pages to fit, and tap-zone layouts are all absent by
   decision (§4), as is pinch-zoom — for which the dependency is already
   present and unused. Zoom is the cheapest of these by a wide margin.
5. **Covers are not cached for offline.** A library entry still shows a grey box
   in airplane mode. Coil's disk cache is on by default and may already cover
   most of this now that images share the client; it hasn't been checked.
6. **Global search paging and persistence.** Pinned-only fan-out is done
   (`0023c81`). Each row still shows page 1 only, and results are lost on restart.
7. **Sort/filter for search.** `getFilterList()` is available on every
   `CatalogueSource` and unused — `searchSeries` passes an empty `FilterList()`.
8. **`OBSOLETE` badge.** Mihon marks installed extensions absent from the index.
   The list is built from the index only, so those packages aren't visible at
   all. Update detection (`e1913c2`) already does the version half.
9. **Per-source settings only reach `ConfigurableSource` basics.** Toggles,
   lists, multi-select and text are rendered; other `Preference` subclasses are
   skipped rather than shown as dead rows.
10. **One extension is lib 1.6** (`AHottie`, v1.6.4) — inside the accepted range
   but built against the newer API; may fail at runtime.
11. ~~`HttpException.kt` isn't in the vendored network package.~~ **Wrong — it is**,
    at the bottom of `network/OkHttpExtensions.kt`:
    `class HttpException(val code: Int) : IllegalStateException("HTTP error $code")`.
    `awaitSuccess()` throws it on any non-2xx, which is what makes the download
    failure messages in the queue screen useful. Nothing to add here.
12. **`YomuApp` is ~800 lines, and none of its state survives recreation.** Screens are split out, but all state and every
    handler still lives in one composable, and the routing chain plus
    `SeriesOrigin` encode real navigation rules in `if / else if`. Hoisting into a
    state holder, or adopting a nav library, is the next structural step — and
    unlike the file split it is *not* mechanical. The manifest's `configChanges`
    (§5) covers rotation, but low memory and "don't keep activities" still drop
    the user back at the Library mid-chapter.
13. **No Feed / Migrate tabs.** Mihon has four sub-tabs under Browse; this has two.
14. **Storage location doesn't move the reading cache or app data.** Chapter
    lists (`filesDir/chapterlists`), the download queue and the path index stay
    in internal storage by design — they're app data, not user data — but it
    does mean "everything Yomu wrote" isn't quite one folder.
15. **Automatic backups are silent when no folder is set.** They land inside app
    storage, where a file manager can't reach them: fine as a safety net,
    useless for moving to another phone. The settings note says so; nothing
    warns more loudly.
16. **Reorganise can't place a chapter whose series was never in the library.**
    `ChapterCache` only holds a list fetched while online, so there's nothing to
    match the hash against. Those stay in the flat layout and keep working.
17. **Icon debt from `material-icons-core`.** Three places now use an
    approximate glyph because the core set is ~40 icons: a filled/dimmed `Star`
    for pinning (no `PushPin`), `KeyboardArrowDown` for download, and `Menu` for
    source visibility. Adding `material-icons-extended` fixes all three at once —
    it's a chunky artifact, so decide it deliberately rather than working around
    it a fourth time.

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
Return to the right screen when backing out of a series   7ff563f  verified OK
Update handoff through the series screen rework
Open the reader before the chapter finishes downloading            verified OK
Add chapter downloads for offline reading                 44ae521  verified OK
Cache chapter lists so downloaded chapters open offline   eca6caa  verified OK
Add source visibility screen and global search scope chips 1f60936  verified OK
Default to Multi and English only
Move downloads into a foreground service with a persistent queue         9782a75  verified OK
Report why a download failed, add retry, and add a Downloads tab        52865df  verified OK
Report the failing image URL on page download errors                    bb1db95  verified OK
Retry transient page failures with backoff                              5f8a5c5  did NOT fix the 400s
Refresh the default UA, send browser headers, name Cloudflare blocks    ca59da9  detection works
Force HTTP/1.1 and halve page concurrency to stop CDN 400s              aaa3d85  33% -> 18%, not fixed
Solve Cloudflare JS challenges in a headless WebView; force HTTP/1.1    1c2c8c7  HentaiSco still 403
Recycle pooled connections to stop per-connection CDN 400s                       33%/18% -> 3-8%, not fixed
Fix KDoc block terminated early in NetworkHelper                        f1b5eea  build fix
Add visible WebView screen for interactive Cloudflare challenges        fbe6bfe  UI only, looped
Solve Cloudflare challenges under the WebView's own UA                  28a4524  verified OK
Load covers through the extension client; stop the reader reopening     7a397b4  verified OK
Show why covers fail in debug builds; stop the Downloads label wrapping 4cb4f6c  verified OK
Repoint loopback image URLs at the source's base URL                    867fba8  verified OK
Update handoff through the Cloudflare and image-pipeline session
Rebuild the reader with overlay bars, chapter picker and settings        fc2aee8  did not compile
Add missing verticalScroll import                                       d395222  builds; reader barely tested
Handle configuration changes instead of being recreated by them                  UNVERIFIED
Add a Settings screen reached from More                                 912707a  verified OK
Add backup, restore and a storage location under Data and storage       10ae342  verified OK
Let downloads and backups live in a folder the user picks               b9b4206  verified OK
File downloads under source, series and chapter in a Yomu folder        5590fec  verified OK
Add a reorganise action for downloads in the old flat layout            8e43778  verified OK
Use one back button everywhere; update the handoff
```

"verified OK" means it was exercised on device; the annotations on the rest are
deliberately not that. Note `fbe6bfe` — the visible WebView shipped and did *not*
work, because the UA bug underneath it was still there; `28a4524` is what made it
pass. A screen landing and a screen working are separate events.

`fc2aee8` and `d395222` are open thread 1 in §0: the reader builds and is barely
exercised. Everything from `912707a` down was checked on device as it landed —
each push was installed and used before the next was written, which is why that
run carries no untested tail.

Komga support was removed entirely (`KomgaSource.kt` deleted); only local
folders and extensions remain as source types.
