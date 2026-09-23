# MangaReader / Yomu

A native Android manga reader built with Kotlin and Jetpack Compose.

Yomu supports local reading, downloadable chapters, library management, source extensions, global search, source migration, backups, reading history, configurable reader behavior, and background download/library-refresh jobs.

## Project structure

- `app/` — Android application and Compose UI
- `source-api/` — compatibility layer used by supported Tachiyomi/Mihon-style source extensions
- `.github/workflows/build.yml` — CI build, unit tests, APK artifacts, and the rolling `latest` prerelease
- `docs/ARCHITECTURE.md` — current module boundaries, compatibility constraints, and maintenance guidance
- `docs/RELEASE_SIGNING.md` — secret-backed release signing migration
- `CONTRIBUTING.md` — validation rules and contribution guidance
- `docs/archive/designs/` — historical design notes
- `docs/archive/handoffs/` — historical implementation/debugging handoffs

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

The normal Android debug signing configuration is used for debug builds. Manual release builds support reconstructing `release.keystore` from the `RELEASE_KEYSTORE_BASE64` GitHub Actions secret and use `RELEASE_KEYSTORE_PASSWORD` for the key password. A legacy tracked `release.keystore` still exists and should be removed only after the secret-backed release path is verified.

## Tests

The project has JVM coverage for several deterministic logic paths, including chapter recognition, download/storage behavior, and series counts. New pure logic should continue to be covered under `app/src/test`, especially reconciliation, backup/restore, migration, indexing, and storage-path behavior.

Run tests with:

```bash
gradle testDebugUnitTest
```

## Architecture notes

The large activity/shared-UI refactor has been completed: navigation, state, actions, overlays, reader components, and feature UI now live in focused files. Future refactors should target only files that remain genuinely cohesive-but-large, rather than recreating broad shared containers.

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
