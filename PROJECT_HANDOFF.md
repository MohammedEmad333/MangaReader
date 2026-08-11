# Yomu / MangaReader — Project Handoff

**Consolidated 2026-08-04, at 0.189.** Three sections were deleted rather than
updated: a release-by-release account of 0.52–0.106 that was mostly headed
"Closed", a hand-maintained commit log that `git log` already owns, and a feature
checklist from roughly 0.60 that had been true for a hundred releases. Git
history has all of it.

**What is left is what does not go stale**: §4, how the thing is built; §5, the
lessons; §7, what is known broken. The banner below and
`SESSION_HANDOFF_0.188.md` carry anything time-sensitive, because they are
rewritten by the work rather than maintained beside it.

**Read `SESSION_HANDOFF_0.201.md` §0 first**, then `SESSION_HANDOFF_0.188.md`
§0. The 0.201 file is the newest state of the tree; the 0.188 file is the last
deep session before it. **Mind the gap the 0.201 file names: 0.190–0.200 has no
handoff** — those releases shipped (theme settings, animated images,
pull-to-refresh, chapter sizes) and are recorded only on Trello and in `git log`.

Live session files, newest first:

| File | Covers | Why it is still here |
|---|---|---|
| `SESSION_HANDOFF_0.201.md` | 0.201 | Newest. Two Backlog cards — History collapsed to one row per series (§1), tag chips search by genre via `Source.applyGenreFilter` (§2). §0 also names the undocumented 0.190–0.200 gap. Neither card verified on device |
| `SESSION_HANDOFF_0.188.md` | 0.150–0.189 | The download queue's two pauses, a per-host circuit breaker, §9b — a bug signed off four times because the test step was worded wrong — §9d, the same bug reported four times because a rule was applied past its edge — and §9g, ten releases spent on a symptom while the objective sat in reach |
| `SESSION_HANDOFF_0.149.md` | 0.144–0.149 | Minification closed, the reader polish pass |
| `SESSION_HANDOFF_0.143.md` | 0.135–0.146 | The five R8 attempts and their two root causes |
| `SESSION_HANDOFF_0.106.md` | 0.88–0.106 | §5 (why two edge gestures never fired) is cited by an open card; §11 is the three-chapter reader design |
| `SESSION_HANDOFF_0.120.md` | 0.107–0.120 | §3 `chapter_number`; the series screen pass |
| `SESSION_HANDOFF_0.87.md` | 0.84–0.87 | §5, fetching extension source in three minutes |
| `SESSION_HANDOFF_0.83.md` | 0.73–0.83 | §3 the dependency cascade; §5 the dex scan |
| `DESIGN_SERIES_SCREEN.md` | — | The design the 0.107–0.120 run was built from |

**Deleted, folded into §9 of this file on 2026-08-02:**
`SESSION_HANDOFF_0.122.md` (the 0.121 mystery — solved, and rewritten correctly
in `SESSION_HANDOFF_0.143.md` §2) and `SESSION_HANDOFF_0.134.md` (bookmarks,
18+, long-strip zoom — all shipped and all carried in more detail on their
Trello cards). `SESSION_HANDOFF_0.67`–`0.81` and `0.93` were folded and deleted
earlier. Git history has all of them.

---

## Current state at 0.149 — kept for the minification account

**Minification is on, working, and the card is closed.** 23,869,296 →
11,731,348 bytes on the published debug asset — a 12.14 MB saving with
optimisation and obfuscation forced off by AGP for a debuggable build, so that
is the floor rather than the ceiling. Five attempts, **two root causes that were
not guessable from the build file**: R8 full mode stripping `Signature` from
Injekt's anonymous `FullTypeReference` subclasses, and a JNI `FindClass` from
`libzstd-kmp.so` naming a class no Java code mentions.
`SESSION_HANDOFF_0.143.md` is the full account.

**The keep list is derivable and the derivation is the reusable part.** The
`api` entries in `source-api/build.gradle.kts` are the extensions' compile
classpath; anything on it not kept whole is a `NoClassDefFoundError` waiting for
the one extension that touches it. **Necessary and not sufficient** — a
dependency that ships a `.so` can name classes from JNI that appear nowhere in
dex, which is what cost four attempts. Adding a dependency to `source-api` means
adding a keep, and nothing checks this.

**Two claims this document still makes below are wrong rather than merely old:**

- **§7 item 1 and §5's account of minification are superseded.** Minification is
  done. It is no longer an open question.
- **"26 extensions / 95 sources" is retired. The figure is 20 / 37**, read off
  the diagnose screen on 0.149, 2026-08-02: 20 packages declaring
  `tachiyomi.extension`, **Loaded OK 20/20**, 37 sources, 37 held in cache. The
  20/20 is the part that mattered — nothing is failing to load, so the gap was
  never a regression and never had anything to do with R8. Whether six
  extensions were uninstalled after 0.122 or 26/95 was wrong when written is
  unresolved and no longer worth resolving. Trello card 72, closed.

**The whole tree is verified and the board's Needs verifying column is empty.**
0.149 passed its nine-point test pass on device on 2026-08-02, and every release
from 0.144 has been exercised. Head is 0.149; everything committed after it is
documentation and one comment block, with no version bump, so the published APK
matches what was verified.

**A source that looks broken may not be, and AHottie is the worked example.**
Its "no covers, no tabs, chapters load forever" report resolved into three
different answers: one non-bug (the chip row is correctly suppressed for a source
with neither a Latest listing nor filters), and two symptoms that were one cause
outside this app entirely — `ahottie.top` serves its images from **imgbox**, and
imgbox is unreachable from that device. `SESSION_HANDOFF_0.149.md` §9–§9e is the
full arc, including three mechanisms that explained the symptom and were not the
diagnosis. **Manhwa18 and Coomer were open cards of the same shape and both
closed on 2026-08-03**, in opposite directions: Coomer is Blocked/Upstream (its
own frontend gets the same 503 from its own API), and Manhwa18 was never a
connection problem at all — `chapterListParse` selects `ul.list-chapters` and
matched nothing, off the same document `mangaDetailsParse` parsed successfully.
`SESSION_HANDOFF_0.188.md` §7.

**The reader had a polish pass in 0.147–0.149.** The double-tap zoom animates,
chapter turns wait for you instead of firing mid-fling, and the chapter list has
a scroll handle. `SESSION_HANDOFF_0.149.md`.

**The download queue was rebuilt in 0.151–0.157 and two of its contracts
changed.** `DownloadQueue.progress` is no longer a percent — it is
`DownloadProgress(ready, total)` with `total` null until the page list lands —
and `head()` no longer means "the first queued item", because a chapter can now
be paused on its own and the worker walks past it. `downloadPage` also takes the
fetch's host-failure tally and will refuse to retry a connect failure against a
host already known bad. `SESSION_HANDOFF_0.188.md` §3, §5, §8, §9c.

**AN INSTRUMENT BEATS A THEORY, AND THE OBJECTIVE BEATS THE SYMPTOM.** Ten
releases went into making a video render inside a WebView; the goal was to watch
it, and the element's own address was readable throughout. Every real answer
came from a probe reporting what the page held; every wrong turn came from a
mechanism that merely fitted the symptom. `SESSION_HANDOFF_0.188.md` §9g.

**CI LOGS CANNOT BE READ FROM THE AGENT ENVIRONMENT.** `/actions/jobs/{id}/logs`
303-redirects to Azure blob storage, outside the network allowlist, so a red
build reports only that it failed. The `mapping-debug` artifact IS reachable.
Re-read the diff. `SESSION_HANDOFF_0.188.md` §9f.

**A TEST STEP THAT SAYS "reaches the last row" WILL PASS A HANDLE THAT STOPS 20%
SHORT.** The scroll handle never reached the end of any list from 0.133 to
0.167, through four separate sign-offs, because every step written for it
checked that the last row was ON SCREEN rather than FULLY VISIBLE. Three wrong
diagnoses followed. `SESSION_HANDOFF_0.188.md` §9b.

**THE FAULT THAT APPEARED FOUR TIMES IN ONE SESSION: one label standing for two
mechanisms.** `Starting` in the download queue, `0 chapters` on the series
screen, the `Clear cookies` dialog, and the pause button. Each arrived as a
cosmetic complaint and each was a state the code could not express. When a
string or a control covers two causes, the fix is almost never the wording — it
is that something upstream threw the distinction away.
`SESSION_HANDOFF_0.188.md` §0.3.

**Read TachiyomiSY.** §1's note that `mihon-ref` is "mostly a dead end" and §5's
"Do not vendor from modern Mihon" are true **about the vendored API only**, and
they were over-generalised into "don't look at Mihon", which cost most of one
evening. For app UI, gestures, screen structure and which third-party library to
reach for, Mihon and TachiyomiSY are the best reference available and are
fetchable without a clone. `SESSION_HANDOFF_0.106.md` §7 has the split and the
commands. Every reader feature since 0.130 came from reading SY first.

**The bug board lives on Trello, not here.** As of 2026-08-03 afternoon:
"Needs verifying" holds one card (73, `onRenderProcessGone` — shipped in 0.150
and impossible to exercise without adb or a Cloudflare challenge), and the open
bugs are Manhwa18 (upstream or login-gated), the all-filters-enabled report, the
start-button colour, and four Blocked/Upstream sources. Treat any statement
below about what is or is not on the board as a statement about 2026-07-31.

**The app runs against a real library** — 3575 entries, imported from Tachiyomi.
That single event exposed nine performance bugs and two correctness bugs in code
that had been fine for months at ~40 series. If you read one thing in §5, read
"An import is a load test". Cold start is ~3 seconds; it was ~30 before 0.72.

**Two things in this repo that should still be dealt with:** `debug.keystore`
and `release.keystore` are committed at the repo root. The release keystore is
the signing identity for this app. Any PAT pasted into a chat should be rotated.

---

## 0. What is still open from before 0.150

**Everything else that was here has been deleted.** It was a release-by-release
account of 0.52–0.106 — thirty-odd subsections, most of them headed "Closed" —
describing work that shipped, was verified, and is now just the behaviour of the
app. Git history has it, and `git log --follow` on this file reaches all of it.

**What was in it that mattered has already moved.** Mechanisms are in §4,
lessons in §5, and anything still contested is on a Trello card. A closed item
re-described in prose is a third copy that can disagree with the other two —
which is the failure §5 records against a "known-good" figure nobody re-measured.

Four things from that range are genuinely unfinished, and they are below.

### Open thread 2 — one line of manifest

**The text half is fixed.** `ReaderScreen`'s two hardcoded `Color.White` draws —
the page-number overlay and the failed-page message — took their colour from the
background's luminance in 0.64, and 0.65 outlined the page number as well, which
covers the case luminance can't: the number sits over the page, not the
background, so a dark panel in a bright scan defeats a colour chosen from the
background alone.

Still unaddressed: `AndroidManifest.xml` declares
`@android:style/Theme.Material.NoActionBar`, which is the *dark* variant. In
light mode that means a dark flash on cold start before Compose paints, and a
permanently dark status bar over a light app.

**It is not one line, and four handoffs said it was.**
the account below is the account: light/dark here is an in-app
preference (`AppPrefs.ThemeMode`, defaulting to `DARK`), not the system setting,
so `Theme.Material.Light` breaks the default case and `DayNight` follows the
system and is wrong for anyone who chose against it.

**0.72 made it cheaper without fixing it.** The splash needed somewhere to hang a
window background, so `res/values/themes.xml` now exists and the manifest points
at `@style/Theme.Yomu` rather than straight at a platform style. The parent is
still the dark variant, which is what the default `ThemeMode` already produced,
so nothing changed for anyone. What changed is the shape of the eventual fix:
adding `values-night/themes.xml` is one file rather than a file plus a manifest
restructure. The status-bar half is still separately fixable at runtime through
the insets controller, from the answer `AppTheme.load` already has in
`onCreate`.

The reason it keeps surviving is that the test device runs in dark mode, where
the bug is invisible — which is worth knowing about every other
appearance-in-light-mode item too.

### Still broken, low value — the `fakku.cc` mirror

SpyFakku's mirror list offers three. `hentalk.pw` works. `fakku.cc` times out
after 30s connecting to 162.255.119.128:443 — dead or blocked upstream, nothing
to fix in this app. `fakkuonion.airdns.org:4096` serves titles and the same
loopback image URLs, which the repoint now handles. **Leave the mirror on
`hentalk.pw`.**

### Still open from earlier sessions

- **Re-verify manhwatoon.** One clean chapter is not proof at a 3-8% failure
  rate; two or three more closes it.
- **AllPornComic on the second phone** was never re-probed after the HTTP/1.1
  removal. If it still returns 403 with `cf-mitigated: challenge` over h2, the
  structural fix is a minimal `WebViewInterceptor` (§7 item 15). Not urgent —
  removing HTTP/1.1 resolved the case that raised it.

### Cheap wins if you want something self-contained

Failed-download retry is only lightly exercised. (The reader was named here as
the largest untested surface for several sessions; that closed in 0.88–0.90.)

Two more, both small and both isolated to one file:

- **The library options icon is a hamburger**, because `material-icons-core` has
  no `FilterList` and the extended pack isn't a dependency. Adding
  `androidx.compose.material:material-icons-extended` fixes it at roughly a
  megabyte of APK — a judgement call, not a bug.
- **The `cover_size` pref is *not* dead — an earlier revision of this file said
  so and was wrong.** Items-per-row in the Display tab replaced it for the
  library grid, but `SourceBrowseScreens.kt:124` still reads it for per-source
  browsing. The Settings row's note claimed both until 0.91 corrected it, which
  is the more useful lesson: a control that half works reads as broken, and
  "nothing reads it" is a claim one `grep` settles.

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

**Every push now carries three things, not one:** the changed files, a
`versionCode`/`versionName` bump in `app/build.gradle.kts`, and a matching
`ReleaseNote` in `Changelog.notes` (`WhatsNew.kt`). The bump is not optional —
Android refuses an APK whose code doesn't increase, so it silently leaves the
old build on the phone. The `sed` for it belongs in the push command, because
`build.gradle.kts` is not a `.kt` and the Termux copy loop will not pick it up:

```
sed -i 's/versionCode = N/versionCode = N+1/; s/versionName = "0.N"/versionName = "0.N+1"/' app/build.gradle.kts
```

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

**Resource files can't come through the loop**, and 0.72 was the first release to
need any. `res/` and `AndroidManifest.xml` are outside
`app/src/main/java/com/mangareader/app/`, so they are written in place — a
`cat > file <<'EOF'` block for a new file, a `sed` for a one-line edit to an
existing one — exactly as `build.gradle.kts` already is.

**Three Termux facts, each of which cost a round trip.** There is no `/tmp` —
it is `$TMPDIR`, or just use `~`, and a failed `cd /tmp` leaves downloads sitting
in the repo where `git add -A` will commit them. `pm list packages` returns
nothing and `pm path` fails, because the shell's UID has no
`QUERY_ALL_PACKAGES` — the app has it, the shell does not, so to inspect an
extension download its APK from the repo instead. And **after any mechanical
lambda rename, grep for `return@`**: 0.77 renamed nine
`withContext(Dispatchers.IO)` headers and left ten `return@withContext` labels
pointing at a builder that no longer existed, which is one grep and one CI round
trip.

**A heredoc ends an `&&` chain, so keep them in a separate paste.** After the
first `EOF` the shell starts a fresh command list, which means everything after
it runs whether the earlier steps succeeded or not — including
`git add -A && git commit && git push`, which would then push a half-applied
tree. 0.72 was delivered as three pastes for this reason: `git pull`, then the
heredoc block, then one real `&&` chain for the copy loop, the `sed`s and the
commit. Note also that `sed -i` edits are not idempotent; a retry wants
`git checkout <file>` first.

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

**This section was a feature checklist "as of the source-visibility commit",
which is roughly 0.60.** Every line on it has been true for a hundred releases
and none of it distinguished a working build from a broken one, so it earned
nothing and could still go stale. Deleted.

What is actually current lives in two places that cannot drift, because they are
written by the work rather than beside it:

- **The banner at the top of this file**, revised whenever a release changes
  something a newcomer would trip over.
- **`SESSION_HANDOFF_0.188.md` §0 and §10**, which carry the head commit, what
  is verified on device, and what is not.

**The one measured figure worth keeping here**, because a wrong version of it
was carried across five handoffs as an acceptance criterion nobody could meet:
**20 extension packages, 20/20 loading, 37 sources, 37 cached.** Measured on
device 2026-08-02 via Settings → diagnose. The earlier "26/26, 95 sources" was
never re-measured and was probably wrong when written. Re-measuring cost one
tap. §5 has the full account.

---

## 3. Build environment

| Thing | Version |
|---|---|
| Kotlin | **2.2.21** |
| kotlinx-serialization | **1.9.0** |
| AGP | 8.5.2 |
| Gradle | 8.9 (via `gradle/actions/setup-gradle@v4`, no wrapper) |
| JDK | 17 (temurin) |
| compileSdk | **36** |
| targetSdk | 34 |
| minSdk | 24 |

**Kotlin, compileSdk and OkHttp all moved on 2026-07-31 and none of it was a
choice** — see `SESSION_HANDOFF_0.83.md` §3. Three things follow from it:

- **All three Kotlin plugin versions move together.** `kotlin.android` and
  `kotlin.plugin.compose` are in the root build file; `kotlin.plugin.serialization`
  is in `source-api/build.gradle.kts` and is easy to miss.
- **compileSdk 36 exceeds what AGP 8.5.2 was tested against.** That is a warning,
  not a limit, acknowledged by `android.suppressUnsupportedCompileSdk=36` in
  `gradle.properties`. If a build ever fails inside AAPT2 or resource linking
  rather than in our own code, the suppression has stopped covering it and the
  real upgrade is AGP 8.11+ with Gradle 8.13+ — which also moves the
  `gradle-version` pin in `.github/workflows`.
- **`-Xcontext-receivers` is on borrowed time.** Kotlin 2.2 deprecated context
  receivers in favour of context parameters. It still compiles; the entire usage
  is `parseAs` and `decodeFromJsonResponse` in `OkHttpExtensions.kt`, both
  `context(Json)`.

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
coil `2.7.0`, telephoto zoomable-image, **okhttp `5.4.0` via the BOM — pinned
identically in `:app` and `:source-api`, and both must move together**,
documentfile,
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
  It briefly forced `Protocol.HTTP_1_1` for every source; that line is **gone**
  and the comment where it was explains why it must not come back without
  measuring first (§5).
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
| `Categories.kt` | Categories + series→category assignments. `applyCategories()` is the batched add/remove used by bulk editing. |
| `LibraryPrefs.kt` | Library sort / display / group / filter settings, and the `LibrarySort`, `LibraryDisplay`, `LibraryGroup`, `FilterState` enums. |
| `SeriesIndex.kt` | Per-series `{ total, read, latestChapterAt, updatedAt }`. Written from `SeriesScreen`; read by the library's unread badge, its Unread/Started/Completed filters and its three index-backed sorts. |
| `WhatsNew.kt` | The changelog (`Changelog.notes`), the seen-version marker, and the post-update dialog. |
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
| `SourceFilters.kt` | Renders a source's `getFilterList()`. Browse-side twin of `SourceSettingsUi`. |
| `NetworkProbe.kt` | One request through the extension's own client, reported in full (§6). |

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
| `LibraryScreens.kt` | `LibraryTab`, `LibraryGrid`, `LibraryEmpty`, `MiniBadge`, `AddToLibraryDialog`, `CategoryAssignDialog`, `BulkCategoryDialog` |
| `LibraryOptions.kt` | `LibraryOptionsSheet` — the Filter / Sort / Display / Group bottom sheet, and its row composables |
| `ScrollMemory.kt` | `ScrollMemory` + `rememberRestoredGridState`/`rememberRestoredListState`. Lazy-list positions held by `YomuApp` so they outlive a routing branch |
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

### Importing a Tachiyomi backup (`TachiyomiImport.kt`, `931cf5f`)

Settings → Data and storage → **Import Tachiyomi backup**. Scan walks external
storage to depth 5 for `.tachibk` / `.proto.gz`, skipping `Android/`. A preview
dialog shows counts and the first four titles **before anything is written** —
that dialog is the safety mechanism, not a nicety, because the alternative to
"cancel" is unpicking several thousand merged entries by hand.

A `.tachibk` is gzipped protobuf. The reader is `PbReader`, about forty lines of
wire-format decoding at the bottom of the file: varint, length-delimited, skip
everything unrecognised. **This is deliberate.** The alternative,
`kotlinx-serialization-protobuf`, needs a new dependency and a full
`@ProtoNumber` schema mirroring Tachiyomi's model classes, and the schema would
then be a second thing to keep in sync with a format we don't control. Wire
format needs neither: unknown fields are skipped by construction, so a backup
from a newer Tachiyomi parses fine and just carries fields we ignore.

**The field numbers below were read off a real 4645-series SY backup, not
recalled.** If they ever need re-deriving, do it the same way — dump the wire
fields of one manga message and match them against visible values in the app.
That is an hour's work to recover, which is why they are written down here.

```
Backup           1 manga        2 category     101 source
BackupManga      1 source(varint)   2 url      3 title    9 thumbnailUrl
                13 dateAdded       16 chapter  17 categories(varint, repeated)
               100 favorite(bool)  104 history
BackupChapter    1 url          2 name         4 read(bool)   6 lastPageRead
BackupHistory    1 url          2 lastRead
BackupCategory   1 name         2 order
BackupSource     1 name         2 sourceId
```

**Field 100 (`favorite`) is the subtle one.** It defaults to `true` and the
encoder omits defaults, so it is *only ever written when false*. Absent means in
the library; present-and-zero means the series is not in the library and is only
carried so its reading progress survives. Miss this and you import a pile of
series the user never added — the first version did exactly that, and put 1078
extra entries in a 3567-series library.

What maps where:

- **Library** ← favourites only. Non-favourites are additionally *removed* from
  our library if present, which is what lets a re-import correct an earlier one.
  This is the only place in the app that deletes library entries wholesale.
- **Read state and saved pages** ← every series, favourite or not. It's keyed by
  chapter, costs nothing to hold, and is waiting if the series is ever added.
- **History** ← favourites only, and it caps at 40, so only the newest survive.
- **Categories** ← by `order`, matched to existing categories by name.

Every write is bulk (`Library.mergeAll`, `ReadState.setReadBulk`,
`savePageBulk`). Not an optimisation: `Library.add` rewrites the entire library
JSON per call, so several thousand of them is quadratic and takes minutes.

The import is a **merge** and **idempotent** — every write is keyed, so running
it twice changes nothing except the corrections above.

#### Loopback covers

SpyFakku is a self-hosted front end, so its backup stores covers as
`http://127.0.0.1/image/...`, which can never load on another device.
`isLoopback()` blanks those on import, and `Library.healCover` writes the real
cover back the first time a series is opened — it fires only when there is no
cover or a loopback one, because every write rewrites the whole library JSON and
doing that per series open would be a real cost for no gain.

### The library screen

Four axes, all stored in `LibraryPrefs` and all read once per tick in
`LibraryTab`:

| Axis | Values |
|---|---|
| Group | Categories, Ungrouped |
| Sort | Alphabetical, Date added, Last read, Unread count, Chapter count, Latest chapter, Random — each reversible |
| Display | Compact grid, Comfortable grid, Cover-only grid, List; Auto or fixed 1–10 columns |
| Filter | Downloaded, Local source, Read, Unread, Started, Completed — tri-state (off / require / exclude) |

The last three filters and three of the sorts read `SeriesIndex` rather than the
library itself, and it is only asked for when one of them — or the unread badge
— is actually on. Same rule as `DownloadIndex.list()` below it, for a cheaper
reason: this is one string read and one parse, not a directory walk.

`LibraryTab` builds a `List<Group>` of `(key, label, items)` and the pager runs
off that, so grouping mode changes the tab set without any other code caring.
The group key is a category id under Categories and the literal `"all"` under
Ungrouped; `activeCategory` in `YomuApp` holds whichever, and a key that no
longer resolves falls back to index 0.

**Filtering and sorting happen per group**, inside the same `remember` that
builds them, so each tab sorts within itself and the whole thing is one pass per
tick rather than one per page. Random is seeded from a stored int rather than
shuffled: a random order that reshuffles on every recomposition isn't a sort.

**The expensive reads are conditional.** `DownloadIndex.list()` walks the
download tree on a cold cache, so it is only called when the Downloaded badge or
the Downloaded filter is actually on. `History` is only grouped by series when
the sort is Last read.

**Scroll position is hoisted, like the tab was.** `ScrollMemory` lives in
`YomuApp` and each grid seeds a `LazyGridState`/`LazyListState` from it and
writes back on dispose. Its other half is `sync(ordering)`: a stored position
only means something against the ordering that produced it, so the store is
emptied when — and only when — sort, direction, seed, grouping, search or any
filter changes. That is also what makes a re-sort start at the top rather than
re-anchoring (§5). The library search is hoisted the same way and for the same
reason as `activeCategory`.

**Dimming "read" is category membership**, matched on the category *name*, not a
new field. It costs one `seriesIn()` set lookup. The alternative — a real
"every chapter read" test — is in the next section.

#### What the index still can't tell you

The index is **lagging** by construction. An entry is written when a series
screen resolves its chapter list, because that is the only moment the app holds
one — so on 0.65 it knew about series opened at least once since that build and
nothing else, which on the build it shipped in was none of them.

**0.67–0.68 added the second writer**, and it is the one that fills the index
rather than trickling into it: `LibraryRefreshService` fetches a chapter list for
every saved series and flushes through `recordAll`. Everything below is still
true — a series the sweep has never reached, or one whose fetch failed or came
back empty, still has no entry — but "no entry" is now the exception rather than
the rule. Do not let that soften the next paragraph: the null branch is what makes
a *partial* sweep safe to read, and a sweep is partial the whole time it is
running.

**Un-counted is not zero, and the whole feature rests on that distinction.** A
series with no entry has an *unknown* unread count, not a count of nought. Every
consumer takes the null branch on purpose: the badge draws nothing rather than
`0`, the filters read "the condition doesn't hold" (so INCLUDE hides an
un-counted series and EXCLUDE leaves it alone, which is the right shape both
ways round), and the sorts use `MIN_VALUE` so it lands at one end instead of
among the finished. Collapse that into `?: 0` anywhere and a freshly imported
library renders as a wall of covers announcing everything has been read. §5 has
the general form — it is the most reusable thing in this release.

The options sheet carries a note saying the same thing in the UI, which is the
other half of it: where a store's coldness is visible to the user, an empty
Unread tab has to explain itself or it reads as a broken filter.

**Closing the gap was a library refresh, not a change here** — built in 0.67 and
made resumable in 0.68 (the account was in §0 and went with the consolidation;
`git log` has it). It needed no new data model on this side, only a
bulk write (`recordAll`) and, for the resume, one new field (`sweptAt`) that
`updatedAt` could not stand in for. **What the index still can't tell you, after
all that, is what *changed*** — the sweep overwrites counts rather than recording
that a series gained three chapters, which is the one thing a Feed / Updates tab
needs and the reason it is §0's named next piece of work.

Two smaller absences remain, and neither is blocked on the index:

- **Bookmarked, Lewd, Language, and Group → Status** have no backing field
  anywhere in the app. They need a data model decision first, not an index.
- **Group → Sources** is blocked on names, not counts: `LibraryEntry` stores the
  `sourceId` it came from and never the source's display name, so the tabs would
  read `tachi:2499283573021220255`. `SourceManager.listAllSources()` has the
  names but classloads extension APKs, which is not something to do
  synchronously in composition. A `sourceId → name` map written whenever sources
  are listed would close it.

### The "What's new" dialog

`Changelog.notes` in `WhatsNew.kt` is the changelog, newest first, as Kotlin
source — so it ships with the APK it describes and can never disagree with it or
fail because the phone is offline. **Add a `ReleaseNote` in the same commit that
bumps `versionCode`.** A release with no entry isn't an error, it is just
invisible.

`WhatsNew.pending()` compares a stored marker against `BuildConfig.VERSION_CODE`
and has three cases: marker present is the normal diff; no marker but a
`library_json` exists means an existing install upgrading into the feature, so
it shows the running build's note only; no marker and no library is a fresh
install, which records and shows nothing. The marker is moved even when an
update has no note, or the next update would replay both.

### "Default" is not a category

Tachiyomi shows uncategorised series under **Default**. It is not a category
anything is filed under; it is the *absence* of one, and a backup stores no
assignment for those series. We do have a real `Categories.DEFAULT_ID`, because
the in-app category editor writes it explicitly rather than saving an empty set.

So the Library filter treats Default as **union of the two**: series with no
assignments at all, plus series explicitly filed there. Filtering it like any
other category — which is what it did originally — shows an empty screen for
what is usually the largest group in the library.

**`LibraryTab` is now the only screen that offers this filter.** The per-source
browse screen had category chips carrying the naive version, was corrected to
the union rule in 0.55, and had the whole row deleted in 0.56 — see §5. If a
Default filter is ever added somewhere new, it needs the union rule *and*, on
any screen listing something other than the library, an intersection with what
is actually saved: "has no category" is true of every catalogue result too.

### The three ways a series gets opened

All three now put something on screen **before** any network request, which is
worth preserving:

| entry point | has in hand | does |
| --- | --- | --- |
| `openSeries` (browse) | a full `Series` with a handle | one request: `listChapters` |
| `openFromLibrary` | a `LibraryEntry` — title, cover | renders a stub, shows cached chapters, then `restoreSeries` + `listChapters` |
| `openFromDownloads` | a `DownloadedSeries` | renders a stub, shows cached chapters, then tries the network and keeps the cache if it fails |

The stub carries **no handle**. `TachiyomiSourceAdapter.listChapters` returns an
empty list without one rather than throwing, so a stub is display-only and
anything needing a handle waits for the real fetch. `activeSource` is read
through `?.` on the series screen, so the moment before it resolves is safe.

Note `restoreSeries` is **not** cheap despite what its neighbours imply — it
calls `getSeries`, which calls `getMangaDetails`, which is a network request.
Opening from the library was therefore two sequential round trips with an empty
screen in front of them.

### What is cached, and what drops it

After a 3567-series library arrived, most of a night went into this. Anything
added here needs an invalidation path thought through at the same time, because
a stale entry here shows up as data that is silently wrong rather than slow.

| cache | holds | dropped by |
| --- | --- | --- |
| `Library.list()` | parsed library | keyed on the raw pref string; `save()` seeds it |
| `Categories.assignments()` | parsed assignments | keyed on the raw pref string; `invalidateAssignments()` |
| `Downloads.completion` | is a chapter downloaded | `forget(id)` from `markComplete`/`delete`; `invalidateCompletion()` |
| `Downloads.sizes` | bytes per chapter | same as above |
| `DownloadIndex.cached` | the whole Downloads tab listing | `record`, `deleteSeries`, and every `Downloads` invalidation |
| `SeriesIndex.all()` | per-series chapter and read counts | keyed on the raw pref string; `record` seeds it; `forget` from `Library.remove`/`removeAll`. Deliberately **not** dropped by Settings' clear-cached-data, which clears chapter lists and leaves the counts correct |
| `iconCache` (`Ui.kt`) | extension launcher icons | never — process lifetime, see §7 |

`Downloads.invalidateCompletion()` is also called by `reorganiseDownloads`,
which moves every chapter folder.

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

**`me.saket.telephoto:zoomable-image-coil` carries paged zoom** (0.62) and, since
0.65, the full-screen cover viewer on the series screen. Long strip is still
deliberately untouched: a pinch there fights the scroll the mode exists for, and
doing it properly means zooming the viewport rather than an item.

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

### A whole-file workflow makes a revert indistinguishable from an edit

Files reach this repo as whole files dropped into `app/src/main/java/...`. Twice
on 2026-07-31 a file was regenerated from an older snapshot before being edited,
which silently undid an earlier fix — 0.94 undid 0.90's cover reporting, 0.96
undid 0.91's Resume target. Both shipped. Neither was visible in the push,
because a whole file is always a whole file.

One was found by luck; the other, the cover reporting, was **invisible** — the
stale-cover half of the repair was simply dead for three releases while the
blank-cover half kept working.

Two countermeasures, in order of value:

- **Audit markers before pushing.** Grep each file for a short list of known-good
  identifiers from every earlier change. Run across all 29 changes that session
  it found nothing else, and it would have caught both.
- **Read the diff stat.** A restore or a feature is mostly `+`. 0.96 showed
  `-47` where it should have shown `-4`.

This is the same family as the stale-Downloads glob and the wrong-module copy
already recorded under "Termux-specific", seen from the authoring side rather
than the copying side.

### Two predicates answering one question will disagree

`LibraryRefreshService` gained a scoped run in 0.98 (chosen sources) and a second
kind in 0.99 (series with no counts). `ownsCursor` correctly refused to let
either *clear* the shared `RefreshCursor` — and the line that *takes* one still
tested `scope == null`, which an un-counted run satisfies. So it claimed a cursor
it was then forbidden to release: a resume point for a full sweep that never ran,
left behind permanently.

Neither predicate was wrong when written. The second kind of scope made them
disagree, and nothing connects them. **When a condition is asked in more than one
place, name it once** — `ownsCursor` existed already and the other site simply
didn't use it.

### A change that lengthens a job's lifetime wakes races that were always there

0.88 reordered the reader's page fetch to start at the resume position. Nothing
about concurrency changed. It nonetheless produced a reliable bug that had been
latent for the whole life of the download path.

The reason is that the reorder changed **what is still running when the user can
act**. Before it, reading to page 40 meant pages 0–40 had long since arrived and
the job was finished by the time anyone backed out. After it, being at page 40 is
exactly the condition under which pages 0–39 are outstanding — so leaving now
reliably cancels a *live* job.

And cancelling a coroutine does not unwind it synchronously. Its `finally` runs
whenever it next resumes, which is routinely after its replacement has started.
The old load's `finally` cleared `isLoading`, the new load had already set it, and
`stillLoading` is the only thing separating a pending page from a failed one — so
every page rendered "couldn't be loaded" until it arrived.

Three things carry:

- **Ask of any change to ordering or duration: what used to be finished by the
  time the user could act, and is it still?** The change itself need not touch
  concurrency to change the answer.
- **A flag must be cleared by the operation that set it.** The fix is a token
  claimed *before* the launch and compared in `finally` — not a job reference
  compared by identity, because the reference is assigned after the launch and
  the race is about precisely that window.
- **A shared flag cannot answer a question only one screen asks.** `isLoading` is
  written by six launch blocks in `YomuApp`; the reader now owns `pagesLoading`.
  This is "a lazily-populated store has three states" from the other end — the
  store was never wrong, the consumer was asking it something it couldn't know.

### Repurposing a signal gives it obligations it never had

Nothing in this app has ever cleared a chapter's stored `pos:` page. That was
harmless for the whole life of the code, because a saved page only fed the
reader: reopening a chapter you had marked unread put you back where you were,
which is arguably what you wanted.

0.91 made `savedPage(...) > 0` one of two *progress* signals driving the
Start/Resume button. From that moment the same untouched line was a bug —
marking a chapter unread to read it again left it as the furthest chapter
touched, so Resume aimed straight back at the page you had asked it to forget. No
code changed underneath it; its meaning did.

**When promoting an incidental value to a decision input, enumerate what is
supposed to reset it and check that anything actually does.** The question is not
"is this value correct" but "who is now responsible for keeping it correct, and
did they know". Here read state and resume position were two halves of one answer
to "where am I" that had never been written down as such, so 0.93 sets them
together in `ReadState.setRead` rather than at each call site — a third call site
cannot then miss it.

Note also what the fix does **not** do: existing stale positions stay until each
chapter is marked unread again. Same as the 0.57 reader bug — correcting a writer
never corrects what it already wrote.

### A stated cost rots faster than a stated mechanism

`SESSION_HANDOFF_0.87.md` §6 proposed putting cover repair in the refresh sweep,
on the grounds that it "already visits every series and already holds the fetched
`SManga`, so writing a corrected cover alongside the counts is close to free".

The mechanism was right and the price was wrong. `LibraryRefresh` calls
`restoreSeries`, which makes **no network request** — that is its entire purpose
and it is documented three times in §4 of this file. The sweep holds a stub with
a null `thumbnail_url`. A cover costs a second request per series, and the
obvious implementation would have doubled a 79-minute sweep against a shared
6-hour daily foreground budget in order to repair a minority of entries.

- **Prices depend on what the surrounding code does today; mechanisms don't.**
  When a note says something is free, that is the sentence to re-derive, and here
  it was one `grep`.
- **Two symptoms sharing a mechanism need not share a cost.** §6's "why 1 and 4
  are one bug" was true and useful, and it hid the only fact that determined what
  to build: blank covers are free to find in stored data, stale ones are not.
- **Detection can sometimes be billed to work already happening.** The grid draws
  every cover it shows and already holds Coil's error, so a 404 there is evidence
  nobody has to pay for.

### Mirroring has two halves, and they fail independently

The right-to-left page slider ran backwards: `reverseLayout` flips the pager while
`currentPage` stays logical, so the slider increased rightwards while the chapter
advanced leftwards. 0.88 fixed it by mirroring the *value* — which corrected the
direction and inverted the fill, leaving the track full at page one.

Mirror the widget, not the number: `LocalLayoutDirection provides Rtl` scoped to
the control, which Material3's Slider honours for track, thumb and drag-to-value
at once. This is 0.64's "a symmetric mistake looks correct at rest" with a second
edge on it — the first fix was verifiable by dragging and the fill needed looking
at, so a partial fix passed the obvious test.

### A vendored API that relaxes a type deletes the other side's error handling

`:source-api` declared `var memo: JsonObject?`. extensions-lib 1.6 declares
`var memo: JsonObject`. Nullable is the *weaker* claim, it compiled clean, it
looked like defensive vendoring, and it cost three releases.

Extensions are compiled against the non-null declaration, so their bytecode
carries no null check. Asura Scans' own line is

```kotlin
val randomSlug = manga.memo["slug"]?.string ?: run { /* recover the slug */ }
```

— a fallback written for precisely the case where the memo has nothing useful in
it. The `?.` is on the *lookup result*. Against a null `memo` the whole
expression is `getMemo().get("slug")` and it throws before the `?:` is reached.
The author's recovery path was unreachable, in a build that compiled without a
warning.

What makes this class of bug expensive:

- **Nothing reports it.** Not the compiler, not `ExtensionLoader`, not
  `LIB_VERSION_MAX`. The symbol is present and the *shape* is right — nullability
  is Kotlin metadata, not JVM signature, which is also why fixing it was binary
  compatible with every installed extension.
- **It surfaces as a platform NPE from inside the extension**, which reads as the
  extension's bug rather than the host's. Two sessions blamed the extension.
- **The direction matters and is easy to get backwards.** A type *we* hand to
  extension code must be at least as strict as upstream declares. A type
  extensions hand *us* can be safely widened — `SMangaUpdate` is nullable here
  and non-null upstream, and that one is harmless because extensions construct it
  and we only consume it. Ask which way the value flows before deciding a
  mismatch is safe.

**The check is minutes and should be routine after any vendoring change:** pull
`keiyoushi/extensions-lib` at the version in the extensions-source
`libs.versions.toml` (`tachiyomi-lib-v16`) and diff its declarations against
`:source-api`'s. `SESSION_HANDOFF_0.87.md` §5 has the commands and what the diff
turned up.

Related and cheaper than the dex procedure in `SESSION_HANDOFF_0.83.md` §5:
**the extension source itself is on `raw.githubusercontent.com`.** The dex tells
you which symbols an extension touches; the source tells you why. Three `curl`s
answered what two rounds of inference got wrong.

### Nothing the act of leaving a screen can change belongs in its scroll signature

Three scroll signatures have been got wrong in this project and all three were
this error.

`ScrollMemory.sync(ordering)` throws stored positions away when the ordering
changes, which is right — a position means nothing against a different order. So
the signature must not contain anything that the screen's *own primary action*
mutates, or it clears itself on exactly the trip it exists to survive.

- **The library grid** keeps `counts` out deliberately (§4): it moves whenever a
  chapter is finished, so including it would throw the scroll away on every
  return from the reader.
- **The Sources list** shipped in 0.86 with `lastUsedId` in, and `openSource()`
  calls `SourcePrefs.setLastUsed()`. The fix did nothing whatsoever; 0.87 removed
  it.

The test to apply before writing one: **what does opening a row from this screen
write?** If the screen also displays that value, it is a signature trap. What it
costs to leave out is a stale anchor — one or two rows moving — which is not the
wholesale reorder a position genuinely can't survive.

### A lazily-populated store has three states, and the third one is invisible

`SeriesIndex` (0.65) answers "how many unread chapters" for the library grid,
and it can only answer for series it has counts for — which on the build it
shipped in was none of them, and after a week of reading is still a minority of
a 3567-entry library.

The trap is that two of its states have the same shape at the call site. An
absent entry and a stored zero both arrive as "nothing to report", and the
idiomatic Kotlin for it — `counts[id]?.unread ?: 0` — silently converts the
first into the second. Written that way, every series nobody had opened would
have drawn a `0` badge, dropped out of the Unread filter, and sorted among the
finished. **None of that reads as a bug.** It reads as a library you have
completely read, which is a perfectly plausible state, so it would have been
believed rather than reported.

Getting it right cost three deliberate null branches, one per consumer, and they
are listed in §4. What is worth carrying is the shape of the mistake:

- **Any cache populated lazily has three states, not two:** present,
  known-empty, and never-looked. Only the first two have obvious values.
- **Write down what each consumer does with the third before adding the
  store**, not after. The consumers are where the collapse happens, not the
  store — `SeriesIndex` itself was never wrong.
- **Be most suspicious when the wrong answer is a plausible one.** A crash gets
  reported and a blank screen gets reported; a library that claims you have read
  everything gets shrugged at. The failure modes that survive are the ones that
  look like legitimate readings of the data.

The same question is worth asking of `ChapterCache` and `DownloadIndex`, both of
which are also populated lazily. Neither currently collapses unknown into a
value — `DownloadIndex.list()` filters on what is actually on disk, and
`chaptersWithFallback` errors rather than returning an empty list — but neither
was designed with this written down either.

### An import is a load test, and this app had never had one

The library went from ~40 series to 3567 in one action. **Nine** separate
performance bugs surfaced in the hours after, and every one of them had been in
the code for months behaving perfectly. They came in three shapes:

1. **O(n) work inside a filter over n items.** The category chips called
   `Categories.categoriesFor()` per entry, and each call re-parsed the whole
   assignment JSON — 3567 full parses per chip tap. Invisible at 40. A freeze at
   3567.
2. **Per-row I/O in a `LazyColumn`.** See below.
3. **Repair passes running on the happy path.** `DownloadIndex.list` scanned
   every library entry's chapter cache off disk to recover downloads the index
   had lost track of. It normally recovers nothing, and it ran on every open of
   the tab.

The general form: *code whose cost is proportional to library size, sitting
somewhere that runs on every interaction.* When adding anything that touches
library or download state, the question to ask is not "is this fast?" but "what
is this proportional to, and how often does it run?"

Worth knowing the library is still **one JSON string**. Reads are cached now, but
every write rewrites all 3567 entries. If a single add or remove ever feels slow,
that is the cause, and the fix is a different storage shape — not another cache.

### Two things driving one position will fight, and the symptom lies

Tapping library tab 4 from tab 1 landed on tab 3. Tapping tab 1 from tab 4
landed on tab 2. Always one short, always on the side it came from — which reads
like an off-by-one in an index and is nothing of the kind.

The tab row and the pager were synced both ways: an effect on
`pagerState.currentPage` pushed the position out to `activeCategory`, and an
effect on `activeCategory` pulled the pager to match. `animateScrollToPage(3)`
from page 0 animates *through* pages 1 and 2, and `currentPage` updates at each
one. Each intermediate value was reported outward, moved `activeCategory`, and
tripped the second effect into issuing its own `animateScrollToPage` — which
cancelled the first mid-flight. The distance never mattered; the animation was
being shot down as it passed.

The fix is not a guard flag. It is deciding which side owns the value: the pager
owns it, tab taps only call `animateScrollToPage`, and the one remaining effect
reads **`settledPage`** rather than `currentPage`, so a jump reports once, at the
end. The inward effect was deleted outright — restoring the tab after backing out
of a series is what `initialPage` is for, and that is read once, before any
effect runs.

Generalise it: if two `LaunchedEffect`s can each cause the other to fire, they
are one feedback loop wearing two hats, and no amount of comparing "is it already
equal" fixes it — the values genuinely differ at every intermediate step. Look
for a settled/committed variant of whatever state is being observed, and if the
API doesn't have one, the loop is the design and it needs cutting, not guarding.

### Layout state does not exist yet when your effect first reads it

0.57 fixed the strip counter with `!listState.canScrollForward` as the test for
"at the end of the chapter". It is a reasonable-looking test and it was wrong in
the one way that mattered: **a `LazyListState` reports `canScrollForward` as
false until its first measure.** `LaunchedEffect` bodies run after composition
and before that measure lands, so the very first thing every chapter did on open
was report its last page — which saved the wrong resume position and marked the
chapter read before a page had been looked at.

It also spread. The corrupted position meant reopening the chapter in *paged*
mode started on the last page, which legitimately marks read, so a bug that only
existed in one mode produced wrong state in both.

0.58 asks a question that cannot be answered without a layout: is the last
item's bottom edge inside the viewport?

```kotlin
val info = listState.layoutInfo
val last = info.visibleItemsInfo.lastOrNull()
pages.isNotEmpty() &&
    info.totalItemsCount == pages.size &&
    last != null && last.index == pages.lastIndex &&
    last.offset + last.size <= info.viewportEndOffset
```

An empty `visibleItemsInfo` is the proof that no layout has happened, so the
pre-measure frame answers false instead of true.

Carry forward:

- **Ask whether a state's default is a lie.** `canScrollForward`'s default of
  false means "no layout yet" and reads as "nothing below" — the two are
  opposite conclusions from the same value. Any layout-derived boolean read
  before first measure has this problem; prefer a query whose empty case is
  unambiguous.
- **A guard chosen for the wrong window protects nothing.** The 0.57 code did
  carry a guard — `!stillLoading` — aimed at a half-fetched chapter. The actual
  window was pre-measure, which has nothing to do with fetching, so an
  already-downloaded chapter walked straight through it. A guard is only worth
  what its condition names.
- **Write-side bugs outlive the fix.** This one wrote to `ReadState` and
  `savePage`, so correcting the reader does not correct the library. Any fix
  touching persisted state needs "what do we do about what's already stored"
  answered in the same breath.

### The reader's page counter could not count to the last page

Long strip reported `listState.firstVisibleItemIndex` as the current page. On a
strip, the last page is visible at the bottom of the screen long before it is
ever the *first* item on it — with pages taller than the viewport it never is.
So the counter stopped one or two short, and `page >= total - 1` in
`onProgress`, which is the only thing that marks a chapter read, could not fire.
Paged mode uses `pagerState.currentPage`, which does reach the end, so the bug
was invisible in the mode most likely to be tested.

The second reader bug shares a cause with the first's fix. An `AsyncImage` given
`fillMaxWidth()` and no height measures **zero** until its bitmap decodes, so
every page not yet decoded was a zero-height row — the chapter collapsed to a
few hundred pixels and then shoved itself apart page by page as images landed,
which reads as the reader scrolling on its own. It also made the list briefly
unscrollable, which is exactly the test the fix above uses for "at the end". A
`heightIn(min = 240.dp)` on strip pages fixes the drift *and* keeps the
end-of-chapter test honest; without it the first fix would mark a chapter read
the instant it opened.

Carry forward: **a position derived from "first visible" is not a position, it
is a lower bound.** Anywhere a counter has to reach the end of a list — progress,
read state, "did they finish it" — the first visible item cannot express it, and
the honest test is whether there is anything left to scroll to.

### A vendored API does not drift gently — it holds, then everything moves at once

`:source-api` is a vendored copy of Tachiyomi 0.15's API, and for many releases
that was free. On 2026-07-31 **one extension update** ended it, and the bill
arrived in a single evening: five interceptor assertions the app didn't satisfy,
two OkHttp classes it didn't ship, Kotlin 2.0.20 → 2.2.21, compileSdk 34 → 36, a
packaging exclude, six releases and four red CI runs. Nothing had warned that
any of it was pending, because nothing exercises a vendored API's *absence*
until an extension asks for it.

Three things follow, and they generalise past this incident.

**Read the dex before writing code.** An extension APK's `classes.dex` lists
every class it references. Unzip it and grep it — `SESSION_HANDOFF_0.83.md` §5
has the commands — and the entire gap between what an extension wants and what
the app ships appears in one pass. Discovering it one crash at a time is what
turned one release into six.

**An inference from evidence is not an observation.** `SMangaUpdate` was missing
from the vendored API and referenced by the extension, so it was named as the
cause with confidence. It was wrong; the actual stack trace said
`CompressionInterceptor`. Both facts were true and only one was the bug. This is
the same lesson as the covers fix in §0's 0.70 entry, arrived at from a
different direction — and on Android the trace is one tap away, under **View
summary** in the "keeps stopping" dialog.

**Close the boundary rather than enumerating the exits.** See the 0.73–0.78
entry in §0: two releases widened catch sites and still missed a path, and one
conversion at `TachiyomiSourceAdapter` made the missed path irrelevant. When
foreign code is involved, the set of places a failure can surface is unbounded
and the set of places it can enter is one.

### A repository can change shape underneath you, and yours will not say so

The Extensions tab went from 1368 entries to two: "Outdated App" and "Update to
Mihon 0.20.1+". Nothing in this app had changed.

Keiyoushi replaced `.../repo/index.min.json` — the flat array every Tachiyomi
fork reads — with a two-entry stub carrying exactly those names, and moved the
real list to `.../repo/index.json` in a **different shape**:

| | flat (`index.min.json`, now a stub) | nested (`index.json`) |
|---|---|---|
| root | array | object with repo metadata |
| entries | top level | `extensionList.extensions[]` |
| package | `pkg` | `packageName` |
| version | `version` | `versionName` |
| apk | `apk`, relative to `apk/` | `resources.apkUrl`, absolute |
| language | `lang` on the entry | `sources[].language` |
| nsfw | `nsfw: 0/1` | `contentWarning`: SAFE / MIXED / NSFW |

Most of the catalogue is still `extensionLib` 1.4 (1202 of 1368), so the APKs
themselves are the same ones this app was already loading — the format changed,
the compatibility didn't.

`fetchAvailable` now reads both. **The URL still has to be edited by hand**, and
that is the part worth remembering: a parser that understands the new shape
still gets two entries from a URL that only serves two.

What made this cost a session rather than a minute:

- **The failure was well-formed.** Two valid entries, correctly parsed,
  correctly rendered, with a plausible "2 of 2" count. Had the stub been
  malformed, `JSONArray()` would have thrown and the empty screen would at least
  have pointed somewhere. A remote change that keeps parsing is strictly harder
  to see than one that breaks it.
- **The names were the message and nobody was listening.** "Outdated App" and
  "Update to Mihon 0.20.1+" are the upstream telling the user precisely what
  happened. They read as ordinary extension names inside a list of extension
  names.
- **`catch { e.printStackTrace() }` in `fetchAvailable` swallows a whole repo.**
  It didn't fire here, but a repo that 404s or changes to a third shape fails
  silently and identically. If this screen is touched again, surface per-repo
  failures.

### A filter nobody could have used, fixed twice before being deleted

The per-source browse screen carried a row of category chips: All, then one per
user category. Reported as "entries in the Default category don't show". Fixed
to §4's union rule, which was a real inconsistency — `LibraryTab` had used the
union since the import and this screen never had. The report came back: *every*
chip was empty, not just Default.

The chips filtered `shown`, and `shown` is the page already loaded. On an
extension source that is Popular's first ~20 titles. So a chip asked "which of
these twenty catalogue entries are in this category of your library", which is
almost always none — and the screen disabled "Load more" while a chip was
active, so it couldn't even reach further. It made sense only for the local
folder source, where the listing *is* effectively the library, and that is
probably what it was built against; §0's "category chips froze the app" is that
same row over a listing large enough to matter.

Deleted in 0.56 at the user's call: Popular, Latest and Filter are the listing
controls, and filtering a library belongs to the library.

Two things worth keeping:

- **Confirming the reported symptom is not the same as understanding it.** The
  Default report was accurate, the rule it named was real, and fixing it was
  correct — and it left the feature exactly as useless as before, because the
  reason it was empty had nothing to do with Default. The tell was available
  and not asked for: does *any* chip work? One question, and it would have
  reframed the whole thing before a line was written.
- **A filter over a paged listing needs its scope stated.** Filtering what has
  been fetched, while paging is off, is a different feature from filtering what
  exists — and it fails silently, looking like a data problem rather than a
  design one. If a control can't reach past the current page, either it searches
  or it shouldn't be there.

### A keyed lazy list re-anchors, and the symptom blames the sort

"Random sort doesn't work, it just scrolls down, and the refresh button does
nothing." Two complaints, one cause, and neither of them is the sort — which had
been reordering the list correctly the whole time.

`LibraryGrid` keys its items by series id. When the data reorders under a keyed
lazy list, Compose looks up the key that was at the top and scrolls to wherever
that item now is. After a shuffle that's the middle of the library, so the grid
lurched downwards and then showed the *same series* at the top — which reads
exactly like "it scrolled and didn't sort". Reshuffling made it worse: the
anchored item stayed pinned at the top of the viewport, so a genuinely different
order looked like a button that did nothing.

The fix is to decide that a reorder invalidates the scroll position and say so:
`ScrollMemory.sync(ordering)` drops stored positions when the ordering changes,
and the state object is rebuilt with it, so the grid starts at the top. Keys are
still right — they're what makes an item animate to its new place instead of
being torn down — the position just isn't meaningful across them.

Generalise it: **item keys make position sticky, and stickiness across a reorder
is a bug that presents as the reorder not happening.** Any list where both the
order and the contents can change under a stable key needs an explicit answer to
"is the old position still meaningful", and the honest answer is usually no.

The shuffle function was weak too and that was found by looking, not from the
report: `hashCode() xor seed` is a bit-flip, and ids from one source differ in
their last few characters, so their hashes arrive clustered and xor can't break
a cluster apart. It's now an avalanche mix (`LibraryPrefs.shuffleKey`). Worth
separating from the real bug — fixing only this would have changed nothing the
user could see.

### Hoisting one value out of a doomed branch doesn't hoist its neighbours

`activeCategory` was moved into `YomuApp` in 0.52 because the routing chain
destroys `LibraryTab` the moment a series opens. Correct, documented, and
verified — and the search box and both grids' scroll positions were left sitting
in the same doomed composable for three more releases, failing the same way for
the same reason. They were reported as three separate bugs.

`rememberSaveable` is not the escape hatch it looks like here. It survives a
configuration change and process death; it does **not** survive leaving the
composition, which is what an `if / else if` routing chain does to a branch. The
same wrinkle is recorded against `SettingsScreen`'s `openSection` in the routing
note above, and against the browse screen's category chip in §0 — three
instances of one bug, found three times.

**A fourth instance turned up in 0.65**, on a different screen: `SeriesScreen`'s
`LazyColumn` had no hoisted state, so opening a chapter destroyed the chapter
list's scroll position and backing out landed at the top of a list the user may
have scrolled a long way down. Reported, again, as its own separate bug. Note
that the enumeration advice below was followed for `LibraryTab` and stopped
there — the rule was applied to the branch that had been caught rather than to
the pattern, and the reader's branch destroys *every* screen beneath it.

It also needed its own `ScrollMemory` rather than sharing the library's: the
class keeps one signature for everything it holds, so a library re-sort would
have cleared the chapter position and opening a different series would have
cleared the library's. Its signature is the series id, which is exactly what
makes returning from a chapter restore and opening a different series start at
the top.

So: **when a routing branch is found to be destructive to one piece of state,
enumerate that branch's state — and then enumerate every other branch it can
cover.** Anything a user can see and change, and would expect to find on the way
back, belongs outside it. The cost of checking is one read of the composable;
the cost of not is a bug report per value, spread over however many releases it
takes someone to notice.

### `remember(key)` is not a cache

`remember` holds a value only while its composable is composed, and a
`LazyColumn` **disposes rows the moment they scroll off screen**. So a
`remember(id) { expensiveThing() }` in a list row re-runs continuously while
scrolling — the exact opposite of what the code reads like it does.

Two of these were live at once. `Downloads.isComplete` did several filesystem
stats per chapter row, on external storage where every stat crosses the FUSE
layer. `SourceIcon` called `getApplicationIcon` per source row — a PackageManager
IPC plus opening another APK's resources — across a list of 1368 extensions.

If a value is expensive and doesn't depend on composition, it belongs in a
process-level cache with an explicit invalidation path, not in `remember`.

### Guessing the hot path finds *a* real cost, not the biggest one

The Downloads tab was slow. The recovery scan above was found, explained,
fixed — and the tab was still slow, because `Downloads.sizeOf` walks every page
file in every chapter folder and the screen calls it once per downloaded
chapter. Both were real. Only one dominated.

Two rounds of "found it, fixed it, still slow" in one evening. The lesson is not
to look harder; it is that a plausible culprit found by reading is a hypothesis,
and the cheap way to rank several is to look at *all* the per-item work in a
screen before fixing any of it.

### Check how a field is consumed before declaring a signature against it

`Library.healCover(seriesId, cover: String)` failed CI because `Series.cover` is
`Any?` — a URL `String` from extensions, a `java.io.File` from local folders.
The signature was written from what the *call site* looked like it passed.

With no compiler in the loop, one `grep` for how a field is read elsewhere costs
seconds and a wrong guess costs a full CI round trip. Narrow inside the function
instead: `(cover as? String) ?: (cover as? File)?.absolutePath`.

### An anchor-based edit can land inside a declaration's annotations

Inserting a block "just before `fun SourceIcon`" put it **between `@Composable`
and the function**. The annotation then applied to the inserted `private object`,
and the compiler reported six errors at the *call sites* inside `SourceIcon`
("`@Composable` invocations can only happen from the context of a `@Composable`
function") and one at the object. Nothing pointed at the insertion.

The brace-balance checker passes this happily — braces are fine. Insert above the
KDoc, not above the declaration, and a cheap scan for "annotation line whose next
line isn't a declaration" catches it before CI does. That scan found nothing else
across eight edited files, so it is worth keeping as a pre-push check.

### Offline-first means reading the cache first, not falling back to it

`openFromDownloads` was documented as offline-first and genuinely worked in
airplane mode — because the network call *failed fast* and it fell through to the
cache. Online, the same code awaited two round trips before drawing anything, on
the screen whose whole purpose is instant local access.

A fallback path and a fast path are not the same thing. If the cache is good
enough to render, render from it immediately and let the network refresh land
when it lands.

### The manhwatoon 400s: five attempts, and the fix was deleting one of them

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

`recycleConnections()` in `TachiyomiSourceAdapter` — `connectionPool.evictAll()`
before every retry and every `CONNECTION_RECYCLE_BATCHES` batches, so no
connection carries more than about 8 requests — took it from 18% to 3-8%.
Still not zero. Four partial fixes, each one tightening the same constraint.

**The fifth attempt went the other way and worked.** Removing
`.protocols(listOf(Protocol.HTTP_1_1))` — the second attempt's own fix, still
sitting in `NetworkHelper` — gives whole chapters with zero failed pages. HTTP/2
multiplexing was never the cause; per-connection request limits were, and
`recycleConnections()` is what addresses those. Forcing 1.1 correlated with an
improvement because it also changed how many requests shared a connection, which
is the variable that mattered. It was a proxy for the real fix and was mistaken
for it, and then defended for three more rounds.

Worse, it wasn't free. See the next section.

Lessons, in order of how expensive they were:

- **A partial improvement is evidence about the variable, not a step toward the
  fix.** 33% → 18% identified requests-per-connection. Treating it as progress
  meant three more rounds of tuning the wrong knob.
- **A mitigation that never fully worked is a suspect, not an asset.** Nobody
  reconsidered forcing HTTP/1.1 for four attempts because it had \u201chelped\u201d.
- **The evidence was one unread response body the whole time.** §6's probe now
  exists; use it first, not fifth.

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

The manhwatoon 400s went the same way and took four extra attempts to admit it;
`NetworkProbe.kt` (§6) exists so the next one doesn't. **When a slow
build/install loop meets an ambiguous symptom, spend the cycle on making the
symptom specific, not on a candidate fix.**

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
- **`ScrollMemory` (0.55) is a plain `remember`,** deliberately, and so is
  everything it holds. It solves leaving the composition, which is the common
  case and happens dozens of times a session; it does not solve recreation, and
  pretending otherwise would mean a `Saver` for something that is already lost
  along with the source, the handle and the loaded pages. It is one more object
  for §7's holder to absorb, not an exception to it.

### A client that lies about what it is, part two: the protocol layer

Forcing `Protocol.HTTP_1_1` globally, for manhwatoon's sake, broke a completely
different source and nobody connected the two for weeks.

`allporncomic.com` answered `200 OK` to one phone and `403` with
`cf-mitigated: challenge` to another, on identical code. The visible WebView
loaded the site fine on both, so no challenge was ever presented and no
`cf_clearance` was ever issued \u2014 there was **nothing for the user to solve**,
and the \u201cOpen in WebView\u201d button could not have worked no matter how it was
wired.

What Cloudflare was reacting to: the client announces itself as
`Chrome/150 \u2026 Android` \u2014 deliberately, via `ClearanceUserAgents`, so that
clearance earned in a WebView is honoured \u2014 and then negotiates HTTP/1.1, which
real Chrome never does. A claimed identity contradicting observed behaviour is
exactly what bot detection is for.

**This is the same bug as the User-Agent one two sections down, one layer
lower.** That one made the WebView claim a desktop UA inside an Android WebView;
this one made an Android Chrome speak a protocol Android Chrome doesn't speak.
Both were introduced as fixes for something else. Both produced symptoms that
looked like a different subsystem failing.

Two things worth carrying:

- **When you make a client claim to be a browser, everything else about the
  connection has to keep that promise** \u2014 protocol, header order, TLS. Changing
  any of them somewhere else in the codebase silently breaks the claim.
- **The two phones were the experiment.** Identical builds, one working, one
  not, differing only in that the failing one is a tablet whose WebView UA omits
  the `Mobile` token \u2014 so it claimed *desktop* Chrome over HTTP/1.1, a sharper
  contradiction. Without a second device this would still be open.

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

**Browse → \u22ee → Connection probe** (`NetworkProbe.kt`) fires one request at the
source's `baseUrl` through `HttpSource.client` — the extension's own client,
headers, cookie jar and Cloudflare interceptor — and reports the status,
negotiated protocol, the User-Agent that actually left the phone (read off
`response.request`, after interceptors rewrite it), `cf-mitigated`, `cf-ray`,
`Server`, `Set-Cookie` *names*, and the block page's text with markup stripped.
`cf_clearance` is checked before and after.

This is the single most useful thing in this section. `cf-mitigated: challenge`
means a block is solvable; its absence on a 403 means it isn't. It settled the
HTTP/1.1 bug in §5 in two runs, after an evening of guessing, and it is the tool
§5 keeps saying to reach for: **make the symptom specific before trying fixes.**

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

1. **The library is one JSON string, and so is the index now.** `Library.list()`
   is cached, but every write rewrites all 3567 entries; `Categories.setCategoriesFor`
   does the same to the assignment map, and `SeriesIndex.record` does it to the
   counts. Bulk helpers exist (`mergeAll`, `removeAll`) and should be used for
   anything touching many series at once. If a *single* add or remove ever feels
   slow, the answer is a different storage shape — probably per-series keys or
   SQLite — not another cache.

   **The library refresh ran straight into this, and `recordAll` is the answer
   that shipped for it** (0.67). `SeriesIndex.record` is safe at one series open
   per user action because it refuses a write that changes nothing; a sweep over
   3571 series hits it thousands of times with something to say each time, which
   is quadratic. `SeriesIndex.recordAll` takes a whole batch and writes once, in
   the same shape and for the same reason as `Library.mergeAll`, and
   `LibraryRefreshService` flushes into it every 100 series — about 36 writes
   instead of 3571. **Anything new that touches many series at once uses
   `recordAll`, `mergeAll` or `removeAll`, never the single-item call in a loop.**

   One caveat that is still open: `recordAll` is `all()` → merge → `save()` with
   no lock, and the sweep calls it from three coroutines. See §0's 0.68 section,
   bug 1.

2. **Extension icons are cached for the process lifetime** and never
   invalidated. A newly installed extension is fine (new package name, fresh
   lookup); an extension that *changes its icon* in an update will show the old
   one until the app restarts. Cheap to fix if it ever matters — drop the entry
   on the install broadcast.

3. **Covers in the library go stale — fixed and verified in 0.90.**
   `CoverRepair.kt` and the repair step in `LibraryRefresh` are the answer this
   entry asked for, built as described below except for the cost: the sweep does
   *not* hold a fetched `SManga`, so a cover is a second request and is spent
   only on candidates. **233 covers repaired across 510 series in one partial
   sweep, a 46% yield.** `SESSION_HANDOFF_0.106.md` §3, which also records what
   that number does *not* count. The rest of this item is the original analysis,
   kept because it is still the clearest statement of why the bug existed.

   **Covers in the library go stale and nothing repairs them in bulk.**
   `healCover` fires on the first open of a series and **only when the stored
   cover is blank or loopback** — never when it is present and wrong. That
   narrowness is now two board items: SpyFakku entries stay grey until each is
   opened individually (the import blanked their loopback covers, so the heal is
   working, one series at a time across 3571 entries), and other entries render
   an HTTP 404 because the source moved the file and nothing ever re-asks.
   The series screen looks correct throughout, because it draws the freshly
   fetched `thumbnail_url` rather than the stored string.

   **The library refresh sweep is where this belongs.** It already visits every
   series and already holds the fetched `SManga`, so a corrected cover is nearly
   free alongside the counts. Write it through a bulk path — `Library` is one
   JSON string and a per-series `healCover` over a sweep is quadratic, the same
   trap `recordAll` was built to avoid for `SeriesIndex` (item 1 above). Once
   that exists, "a re-import is needed to fix covers" stops being true.
   Analysis in `SESSION_HANDOFF_0.87.md` §6; unverified, read off the code.

4. **The Downloads recovery scan is gated but not free.** It still runs once per
   process when legacy flat-layout folders exist, because their names are hashes
   that can't be mapped back to chapter ids. If that ever hurts, the answer is to
   record what the scan found rather than to scan faster.

5. **Downloader gaps.** The foreground service, queue, retry and Downloads tab
   are done (§4) but only lightly exercised — see §0. What's left versus Mihon: no reordering in the queue (strictly
   FIFO), no per-series grouping in the queue screen, and no auto-download of
   new chapters. Retry/backoff settings are global, not per-source — see the
   note on manhwatoon in §5 for why that might eventually need to change.
   Android 14 also caps `dataSync` foreground services at ~6 hours a day, which a
   queue left paused indefinitely would burn through; pausing releases the wake
   lock but not the service.

   **That budget is shared, as of 0.67.** `LibraryRefreshService` is a second
   `dataSync` foreground service, and a full sweep is **~1h25m — measured**:
   3114 series in 73 minutes on the night of 2026-07-29/30, or **42.7/min** over
   3571 entries, cross-checked against a full run of 3378 series in 79 minutes.
   So the original "an hour or two" estimate in this document was right, and two
   full sweeps in one night came to under three hours — comfortably inside the
   ~6-hour cap. The hazard is real but not one or two sweeps deep: it is a queue
   left paused indefinitely, or a sweep put on a repeating schedule. If it ever is
   scheduled rather than pressed, it should become `WorkManager` work instead of a
   service; a user-initiated sweep is defensible as a foreground service, a
   recurring one is not.

   *A previous revision of this entry claimed ~3h10m and "over half the daily
   budget". That was wrong — it came from computing a rate against an assumed
   timestamp for a progress reading that didn't carry one. Don't derive rates from
   readings whose time you inferred.*

   **The sweep is latency-bound, not spacing-bound**, and this part survives the
   correction. 42.7/min across `SOURCE_CONCURRENCY = 3` is roughly 4 seconds per
   series within a source, against a `REQUEST_SPACING_MS` of 250ms — the pacing
   constant is not what sets the pace, the network is. If this ever needs to be
   faster, `SOURCE_CONCURRENCY` is the lever and `REQUEST_SPACING_MS` is not.
   Weigh either against the manhwatoon lesson in §5 first.

   **A related thing found while measuring:** `NetworkHelper` gives the shared
   client a 5 MiB disk cache and `Requests.kt` defaults every `GET` to
   `maxAge(10, MINUTES)`. So two sweeps started inside ten minutes of each other
   can serve chapter lists from cache rather than re-checking the source — which is
   not what a *refresh* means. In practice a sweep takes far longer than ten
   minutes and 5 MiB won't hold 3571 chapter lists, so most of a second sweep
   misses anyway. Worth knowing before anyone adds a "refresh this series now"
   button, which would sit squarely inside that window.
6. **Cloudflare costs 30 seconds before the error appears.** A source behind an
   interactive challenge burns `CloudflareInterceptor`'s full timeout on the
   headless attempt that cannot succeed, and only then shows the error carrying
   the "Open in WebView" action. Shortening the timeout once a host is known to
   need a human — or skipping the headless attempt for hosts with a
   `ClearanceUserAgents` entry that still 403 — would make that instant.
7. **Coil still sends no `Referer`.** Images now go through the extension's
   client, so they carry cookies and the UA, but a source whose CDN checks
   `Referer` will still 403 its covers while its pages load fine — `fetchPage`
   goes through `HttpSource.getImage`, which applies the source's headers.
   Nothing has hit this yet. If it does, the fix is a Coil `Fetcher` or an
   interceptor on a derived image client, not a change to the shared one.
8. **Reader features that need the page pipeline.** Crop borders, split wide
   pages, rotate wide pages to fit, and tap-zone layouts are all absent by
   decision (§4) — the first three need to inspect and cut the bitmap and the
   last needs a gesture model this screen doesn't have. Pinch-zoom is **done**
   for paged modes (0.62); long strip is the remaining gap and needs the
   viewport zoomed rather than an item, which is a piece of work rather than a
   flag. **This is now a wanted feature rather than a declined one** — SY has it,
   by scaling the whole `WebtoonRecyclerView`; the Compose equivalent is
   `graphicsLayer` + `transformable` on the `LazyColumn`, which has to coexist
   with the tap detector and the transition rows. `SESSION_HANDOFF_0.106.md` §10.
9. **Covers are not cached for offline.** A library entry still shows a grey box
   in airplane mode. Coil's disk cache is on by default and may already cover
   most of this now that images share the client; it hasn't been checked.
10. **Global search paging and persistence.** Pinned-only fan-out is done
   (`0023c81`). Each row still shows page 1 only, and results are lost on restart.
11. **Sort/filter for search.** `getFilterList()` is available on every
   `CatalogueSource` and unused — `searchSeries` passes an empty `FilterList()`.
12. **`OBSOLETE` badge.** Mihon marks installed extensions absent from the index.
   The list is built from the index only, so those packages aren't visible at
   all. Update detection (`e1913c2`) already does the version half.
13. **Per-source settings only reach `ConfigurableSource` basics.** Toggles,
   lists, multi-select and text are rendered; other `Preference` subclasses are
   skipped rather than shown as dead rows.
14. **One extension is lib 1.6** (`AHottie`, v1.6.4) — inside the accepted range
   but built against the newer API; may fail at runtime.
11. ~~`HttpException.kt` isn't in the vendored network package.~~ **Wrong — it is**,
    at the bottom of `network/OkHttpExtensions.kt`:
    `class HttpException(val code: Int) : IllegalStateException("HTTP error $code")`.
    `awaitSuccess()` throws it on any non-2xx, which is what makes the download
    failure messages in the queue screen useful. Nothing to add here.
16. **`YomuApp` is ~800 lines, and none of its state survives recreation.** Screens are split out, but all state and every
    handler still lives in one composable, and the routing chain plus
    `SeriesOrigin` encode real navigation rules in `if / else if`. Hoisting into a
    state holder, or adopting a nav library, is the next structural step — and
    unlike the file split it is *not* mechanical. The manifest's `configChanges`
    (§5) covers rotation, but low memory and "don't keep activities" still drop
    the user back at the Library mid-chapter.
17. **No Feed / Migrate tabs.** Mihon has four sub-tabs under Browse; this has two.
18. **Storage location doesn't move the reading cache or app data.** Chapter
    lists (`filesDir/chapterlists`), the download queue and the path index stay
    in internal storage by design — they're app data, not user data — but it
    does mean "everything Yomu wrote" isn't quite one folder.
19. **The Cloudflare path has no `WebViewInterceptor`.** When a site refuses
    OkHttp but serves the WebView, and no challenge is presented, there is
    nothing to solve \u2014 the only real answer is routing that request through the
    WebView. §4 records the original as deliberately deleted for pulling in
    QuickJS and moko-resources; a minimal rewrite, the way `CloudflareInterceptor`
    was rewritten, is the structural fix. Not needed yet: removing forced
    HTTP/1.1 (§5) resolved the case that raised it.
20. **Automatic backups are silent when no folder is set.** They land inside app
    storage, where a file manager can't reach them: fine as a safety net,
    useless for moving to another phone. The settings note says so; nothing
    warns more loudly.
21. **Reorganise can't place a chapter whose series was never in the library.**
    `ChapterCache` only holds a list fetched while online, so there's nothing to
    match the hash against. Those stay in the flat layout and keep working.
22. **Icon debt from `material-icons-core`.** Three places now use an
    approximate glyph because the core set is ~40 icons: a filled/dimmed `Star`
    for pinning (no `PushPin`), `KeyboardArrowDown` for download, and `Menu` for
    source visibility. Adding `material-icons-extended` fixes all three at once —
    it's a chunky artifact, so decide it deliberately rather than working around
    it a fourth time.

---

## 8. Commit history

**Deleted.** This was a hand-maintained list of commit subjects, last extended
somewhere around 0.120 and describing a fraction of the 189 releases since. `git
log --oneline` is the same thing, always current, and cannot fall behind.

A copy of a fact the tool already owns is the shape §5 keeps recording: the
"known-good" figure nobody re-measured, the item count restated beside the list
that owns it, the stated cost that rots faster than a stated mechanism.

---

## 9. Folded from deleted session files

`SESSION_HANDOFF_0.122.md` and `SESSION_HANDOFF_0.134.md` were deleted on
2026-08-02. Everything below is what would otherwise have been lost — the rest
of both files is either resolved, superseded by `SESSION_HANDOFF_0.143.md`, or
carried in more detail on a Trello card. Git history has the originals.

### From 0.122 — a green CI run is not evidence for a build-config change

0.121 built clean, published a 9.59 MB APK, and crashed in `Application.onCreate`
on every launch. **No build step reaches `onCreate`.** Anything touching R8,
manifest merging, or initialisation order must be **launched on a device before
it is believed**, and that check has to be explicit — the usual signal here
(green, artifact published, size moved) was all present and all meaningless.

### From 0.122 — the flag was on the wrong build type for its entire life

`isMinifyEnabled` sat inside `getByName("release")`. CI runs `assembleDebug` on
every push and publishes `app-debug.apk`; the release APK is behind a manual
`workflow_dispatch` input. **So the APK on the phone has always been the debug
one**, and every discussion of "turning minification on" before 0.121 was
describing a change to a build nobody installs. Generalises: before tuning a
build flag, confirm which build type the artifact you actually install comes
from.

### From 0.122 — R8 writes the rule it wants

A build that dies at `minifyDebugWithR8` on a missing class writes the exact
`-dontwarn` it needs to `app/build/outputs/mapping/debug/missing_rules.txt`.
Take it from there rather than guessing at the package. CI uploads that
directory as **`mapping-debug`** with `if: always()`, which is the point — the
run that most needs it is a failed one.

### From 0.134 — `SourceNsfw`, and the shape it generalises

`SourceNsfw.kt` answers "is this source 18+" for a screen that cannot afford to
classload extensions. `LibraryEntry` stores `seriesId`, `sourceId`, `title`,
`cover`, `addedAt` and nothing about content, so a saved series can only be
classified by its source — and asking a source means `listAllSources()`, which
classloads extension APKs and cannot happen in composition.

**The solved shape: a record written at the point the information already
exists.** It learns from `listAllSources` and from the repo index, exactly where
`SourceNames` does. Anything else a screen needs to know about a *source*
without classloading gets answered this way.

- **A map, not a set of the flagged ones.** A set collapses "known safe" into
  "never seen", and this store has the usual three states. Only a map can record
  a source that *stops* being flagged. Third appearance of the `SeriesIndex`
  three-state trap.
- **Unknown reads as "the condition doesn't hold"** — Include hides it, Exclude
  keeps it. A source nothing has classified must not make a saved series vanish
  from a library someone is looking at.
- **It classifies a source, not a series.** A mixed-content source marks
  everything saved from it. Series-level needs a field `LibraryEntry` has
  nowhere to put.
- **Only touched switches are recorded** in the by-hand classifier. An untouched
  one must not store "not 18+", which is a claim nobody made.

### From 0.134 — `tachi:6901` is E-Hentai, and one id is unidentifiable

Two library sources were neither installed nor in any index. `tachi:6901` is
**E-Hentai**: TachiyomiSY's `source-api/.../exh/source/SourceIds.kt` declares
`LEWD_SOURCE_SERIES = 6900` and `EH_SOURCE_ID = LEWD_SOURCE_SERIES + 1`. A fork
built-in, which is why no index has it.

The other, ending `831257`, is **unidentified and probably unrecoverable**: not
an SY built-in, not in the 1367-extension Keiyoushi index, and not derivable
from the id — Tachiyomi computes a source id as MD5 of `name/lang/versionId`
(verified: that formula reproduces 995 of 1043 live ids), and a 7,200-candidate
sweep found no match. **The only place it survives is the user's own backup,
field 101.** `TachiyomiImport` records names from that field now, but that code
landed in 0.94–0.96 and this library was imported at `931cf5f`, long before, so
it never ran. A re-import would name it and would also remove non-favourite
entries, so it is not free. Offered and declined.

### From 0.134 — check the file's own import list, not the tree

0.131 went red on `clipToBounds` (it is in `androidx.compose.ui.draw`, not
`androidx.compose.foundation`) and on `transformable`'s `canPan` overload
needing `ExperimentalFoundationApi` in the opt-in. **Both were one grep away** —
`SourceBrowseScreens.kt` imports `ExperimentalFoundationApi` on line 14. That was
the fifth CI failure in this project from an identifier assumed rather than
checked, and it happened in the same session that avoided the same trap three
times by checking.

The import split as it stands: `SourceBrowseScreens.kt`, `ReaderScreen.kt` and
`MoreScreens.kt` carry the wildcard block; `SettingsScreens.kt` and
`DownloadQueueScreen.kt` use curated lists and are where this keeps happening.

### From 0.134 — confirm what deletes, not what cancels

"Every delete button should request confirmation" was applied more narrowly than
it reads: **confirm what deletes files or wipes a list, not what cancels an
action.** History's per-entry Remove and its Clear all ask; the download queue's
Remove and Cancel all do not, because those undo something you can re-add in a
tap and a dialog there is noise.

Found while applying it, and not on any card: **History's Clear all had no guard
at all** and wiped the whole history on one tap — the most destructive control
on the screen needed the fewest taps. It surfaced from grepping every delete
affordance in the app before starting, which is worth doing again if the rule is
revisited.
