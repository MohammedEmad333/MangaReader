# Manga chapter updates — implementation roadmap

Existing functionality (do not duplicate):
- `LibraryRefreshService` fetches chapter lists for all series or selected sources.
- `RefreshCursor` resumes interrupted full-library refreshes.
- `LibraryRefreshPrefs` supports minimum age, skip-completed and per-series exclusions.
- `LibraryRefreshSummaryStore` persists refresh outcomes and per-source failures.
- `LibraryRefreshForeground` displays an ongoing foreground notification.

## PR 1 — Refresh visibility (this PR)
Display the date and time of the last completed refresh attempt in Settings using the user's locale. Do not present it as a successful refresh: a run may have failures or may have been stopped. Add JVM tests. No new network requests.

## PR 2 — Detect newly available chapters
Compare pre-refresh and post-refresh chapter identities, not simply total counts or index timestamps. Persist a bounded, deduplicated event summary by source and series. Do not mark chapters as read and do not treat a first-time index as a release alert. Add tests for new/renamed/reordered/removed chapters and repeated sweeps.

## PR 3 — Updates screen
Show updates grouped by series and date, source and failure status, with direct navigation to chapters. Add an empty state and clear/read controls. Make all list state stable.

## PR 4 — Notifications
Add a separate opt-in notification channel for new chapters. Respect Android 13+ notification permission, collapse duplicates, and avoid alerts on initial indexing and manual history imports. Foreground progress notifications remain independent.

## PR 5 — Background scheduling
Use WorkManager unique periodic work with persisted user preferences and network/battery constraints. Avoid launching foreground services from disallowed Android background contexts; design short bounded work or allowed long-running worker appropriately. Backoff and per-source rate limits are required. Manual refresh continues to work offline/without notification permission.

## Verification
Each PR runs JVM tests, passes existing CI, and requires review of migration/backward compatibility before merge. Since current PR workflow skips APK compilation, verify a main-branch Android build after merge and consider a future dedicated PR compilation job with a repository-supported signing-free setup.
