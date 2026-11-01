# Yomu Reliability and Reader 2.0 Roadmap

Status: in progress (partial reliability hardening). Baseline: main, November 2026.

## Delivered incremental fixes
- PR #457: chapter update navigation refuses blank or duplicate chapter IDs (merged to main).
- Reliability batch: exact source/series matching also refuses blank or duplicate library identities; failed download retry ignores blank IDs and avoids duplicate queue entries; failed-entry dismissal reports actual changes. Includes regression tests; do not mark complete until CI passes and the PR merges.

## Still outstanding
- Full page-loading error-state and bounded-backoff work; process-death download and reading-progress instrumentation; extension and device compatibility checks.
- Run on-device smoke tests for chapter switching, downloads interrupted by process death, and reading progress restoration before claiming reliability complete.

## Phase 1 — Reliability (P0)
1. Reader identity: ensure every page request and navigation result is scoped to immutable series/source/chapter IDs; cancel stale loads on navigation. Add tests for rapid chapter switching and source changes.
2. Page pipeline: explicit loading/loaded/retryable-error/permanent-error states, bounded retries with exponential backoff, and cancellation. Never show a blank page indefinitely.
3. Downloads: atomic index writes, restart reconciliation, retry failed entries, validate file integrity before marking completed, and guard against concurrent duplicate jobs.
4. Progress: persist chapter/page position idempotently and test restoration after process death.

Acceptance: no cross-series navigation in regression tests; failed pages show actionable retry; interrupted downloads reconcile; restored position is deterministic.

## Phase 2 — Reader Experience (P1)
- Per-series reading mode, direction, fit, and prefetch settings with global defaults.
- Bounded memory-aware prefetch, cancellation on chapter change, and offline-first page lookup.
- Accessible controls and screen-reader announcements.
- Test portrait/landscape, RTL/LTR, offline mode, large chapters, and low-memory devices.

## Phase 3 — Library and Downloads (P1)
- Queue controls, storage usage, integrity inspection, and selective cleanup.
- Deduplication using stable source/series identifiers; migration preview and rollback.
- Evaluate Room for structured indexes, with reversible migration and backup compatibility.

## Phase 4 — Search and Sources (P1)
- Stable result keys, cancellation of outdated searches, deduplication, source health diagnostics, and migration fallbacks.
- Maintain ABI/API compatibility for external Tachiyomi/Mihon/Aniyomi extensions; validate on real devices.

## Phase 5 — Anime (P2)
- Stream fallback, subtitle/audio selection persistence, playback recovery, watch progress, and PiP regression checks.

## Phase 6 — Build and Release (ongoing)
- Reconcile README Gradle requirement with CI's pinned 8.14.5; consider committing a verified Gradle Wrapper.
- Keep AVIF Coder 3.x isolated until its failing compatibility/build issue is resolved.
- Add instrumentation smoke coverage for extensions, storage, reader navigation, and Media3 playback.
- Verify exact PR head SHA checks before merging; squash merge and delete branches after merge.

## Delivery policy
Each implementation phase should use a focused PR with tests, migration notes where applicable, and device verification. Do not merge failing checks or claim a feature complete from documentation alone.
