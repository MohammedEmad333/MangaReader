# MangaReader / Yomu

A native Android manga reader built with Kotlin and Jetpack Compose.

Yomu supports local reading, downloadable chapters, library management, source extensions, global search, source migration, backups, reading history, configurable reader behavior, and background download/library-refresh jobs.

## Project structure

- `app/` — Android application and Compose UI
- `source-api/` — compatibility layer used by supported Tachiyomi/Mihon-style source extensions
- `.github/workflows/build.yml` — CI build, unit tests, APK artifacts, and the rolling `latest` prerelease
- `docs/ARCHITECTURE.md` — module boundaries, compatibility constraints, and refactoring direction
- `docs/RELEASE_SIGNING.md` — secret-backed release signing migration
- `CONTRIBUTING.md` — validation rules and contribution guidance
- `DESIGN_SERIES_SCREEN.md` — series-screen design notes
- `PROJECT_HANDOFF.md` and `SESSION_HANDOFF_*.md` — historical implementation/debugging notes

## Requirements

- JDK 17
- Android SDK with API 36 available
- Gradle 8.9 (CI currently installs this version)
- Android 7.0 / API 24 or newer

## Build

Until the Gradle Wrapper is committed, use a local Gradle 8.9 installation:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Continuous integration

Every push to `main` runs the JVM unit tests and builds a debug APK. Successful main builds update the rolling GitHub prerelease tagged `latest`.

The release APK is optional and is built only from a manual workflow dispatch.

## Signing

Signing files are intentionally ignored by Git. Do not add `*.keystore`, `*.jks`, `*.p12`, or `keystore.properties` to the repository.

The normal Android debug signing configuration is used for debug builds. Release signing currently expects a local `release.keystore` plus the `RELEASE_KEYSTORE_PASSWORD` environment variable; CI migration to a secret-provisioned keystore is tracked separately so the existing manual release path is not broken.

## Tests

The project currently has JVM coverage for chapter-number recognition. New pure logic should be covered under `app/src/test`, especially download reconciliation, backup/restore, migration, indexing, and storage-path behavior.

Run tests with:

```bash
gradle testDebugUnitTest
```

## Architecture notes

The app is currently feature-oriented but still contains several large Compose/activity files. Refactoring should preserve behavior while gradually extracting navigation, state, actions, and screen-specific UI into smaller units.

The `source-api` module intentionally carries compatibility dependencies required by external source APKs. Dependency upgrades—especially Kotlin, OkHttp, serialization, and R8 rules—should be tested against installed extensions rather than treated as ordinary library bumps.

## Security and permissions

Yomu installs and discovers external source packages, downloads content, and supports user-selected storage locations. This requires permissions that are broader than a typical reader app. Any future Play Store distribution should re-evaluate package visibility, storage, APK install/delete, and cleartext-network requirements against current Play policies.

## Release hygiene

Before publishing a release:

1. Run unit tests.
2. Build and install the debug APK.
3. Verify extension loading, browse/search, series details, reader, downloads, and library refresh.
4. Verify backup/restore and downloaded-content behavior.
5. Build the signed release only after the debug build has passed the same compatibility checks.
