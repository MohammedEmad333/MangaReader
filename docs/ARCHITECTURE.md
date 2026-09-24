# Architecture

## Modules

### app

The Android application. It owns navigation, Compose UI, manga reader state, anime playback state, library state, downloads, backups, storage selection, extension discovery, source browsing, and background services.

### source-api

Compatibility surface used by Tachiyomi/Mihon-style manga extension APKs and Aniyomi anime extension APKs. Treat this module as an ABI/API compatibility boundary: dependency and shrinker changes here can break extensions even when the app itself compiles.

## Main functional areas

- **Library** — saved series, categories, counts, filtering, refresh, and migration.
- **Sources** — extension discovery/loading, source preferences, browse/search/filter flows, and network diagnostics.
- **Reader** — manga page loading, zoom/swipe behavior, resume position, read state, and reader preferences.
- **Anime playback** — Aniyomi episode/video resolution, stream selection, Media3 playback, subtitles, playback progress/completion, watch history, audio focus, retry handling, and picture-in-picture.
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
6. Manga and anime extensions share the host process but have distinct source APIs and media semantics; code that classifies extension/local sources must handle both `tachi:` and `aniyomi:` ids.

For these reasons, dependency upgrades and R8 changes must be validated on a real device with representative extensions rather than accepted solely because compilation succeeds.

## Current structure and maintenance direction

The 2026 refactor removed the former large shared/activity containers and split orchestration into focused routes, controllers, overlays, reader components, and feature-specific UI files.

Current maintenance priorities:

- Keep `MainActivity.kt` and route hosts thin; feature behavior belongs in dedicated controllers/routes.
- Keep reader window effects, controls, page components, and overlays separate rather than rebuilding a monolithic reader file.
- Keep download persistence/index/storage responsibilities separated; avoid moving them back into one service or UI file.
- `Changelog.kt` is the query API only; release-note data lives in `ReleaseNotesData.kt`.
- Files around roughly 300–400 lines are not automatically refactor targets. Split them only when they contain separable responsibilities or become hard to test/review.
- Historical implementation notes and superseded designs live under `docs/archive/` and should not be treated as current architecture documentation.

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
- Aniyomi episode/video resolution and hoster fallbacks
- Media3 playback, subtitles, resume/completion persistence, audio focus, and picture-in-picture

## CI

Pull requests and pushes to `main` run JVM tests and build the debug APK. Only pushes to `main` publish the rolling `latest` prerelease.
