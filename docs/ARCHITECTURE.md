# Architecture

## Modules

### app

The Android application. It owns navigation, Compose UI, reader state, library state, downloads, backups, storage selection, extension discovery, source browsing, and background services.

### source-api

Compatibility surface used by Tachiyomi/Mihon-style extension APKs. Treat this module as an ABI/API compatibility boundary: dependency and shrinker changes here can break extensions even when the app itself compiles.

## Main functional areas

- **Library** — saved series, categories, counts, filtering, refresh, and migration.
- **Sources** — extension discovery/loading, source preferences, browse/search/filter flows, and network diagnostics.
- **Reader** — page loading, zoom/swipe behavior, resume position, read state, and reader preferences.
- **Downloads** — queueing, foreground service execution, readable storage paths, local covers, and reconciliation.
- **Persistence** — SharedPreferences-backed stores, JSON indexes, filesystem caches, and backup/restore.
- **Background work** — foreground services for long-running network work and WorkManager for scheduled backups.

## Compatibility constraints

The project intentionally carries several non-obvious compatibility requirements:

1. External source APKs call classes from the vendored source API by name.
2. R8 cannot see code inside external APKs, so apparently-unused APIs may still be runtime requirements.
3. Native libraries can reference Java/Kotlin classes through JNI, which also defeats ordinary shrinker reachability analysis.
4. Kotlin and kotlinx.serialization metadata versions must remain compatible with the extensions being loaded.
5. OkHttp versions must support APIs expected by current extensions.

For these reasons, dependency upgrades and R8 changes must be validated on a real device with representative extensions rather than accepted solely because compilation succeeds.

## Refactoring direction

Several files are intentionally scheduled for incremental decomposition rather than one large rewrite:

- `MainActivity.kt` — separate navigation/state orchestration from feature actions.
- `SourceBrowseScreens.kt` — split browse, search, filters, and series details.
- `SettingsScreens.kt` — split settings sections into feature-level files.
- `ReaderScreen.kt` — isolate reader controls/state transformations from rendering.
- `LibraryScreens.kt` — isolate selection, filtering, and grid/list presentation.
- `WhatsNew.kt` — move historical changelog data away from UI logic.

Refactors should remain behavior-preserving and land behind passing tests/builds.

## Testing strategy

Prefer JVM tests for deterministic pure logic and data transformations. Instrumented/device validation remains important for:

- extension class loading
- package discovery/install/uninstall flows
- foreground services
- storage permissions and SAF behavior
- WebView/Cloudflare flows
- R8/shrinker compatibility
- native compression paths

## CI

Pull requests and pushes to `main` run JVM tests and build the debug APK. Only pushes to `main` publish the rolling `latest` prerelease.
