# Yomu — source-qualified chapter-cache migration

## Why

Earlier chapter lists were persisted under a hash of `seriesId` alone.
Two manga sources can return the same series ID; the old cache could then
show unrelated chapters or trigger erroneous update/download recovery.

## New behavior

- Chapter-list snapshots are addressed by a length-prefixed pair of
  `sourceId` and `seriesId` before hashing. This is an on-disk cache
  change, not an extension API change.
- Every existing chapter-cache call site now passes the source identity.
- Download alias reconciliation additionally requires a matching source.
- The new snapshots use Android `AtomicFile` for crash-safe updates.
- A legacy `seriesId`-only file is read only when exactly one matching
  entry exists in the local library and it belongs to the requested source.
  Ambiguous legacy files are deliberately not used.

## Upgrade considerations

No downloaded chapter files or reading history are deleted by this change.
An ambiguous legacy chapter list may not be available offline until the
extension successfully refreshes that series. On successful refresh, the
source-qualified snapshot is written automatically.

The old legacy cache is not automatically erased; clearing the app's chapter
cache remains available through existing settings.

## Verification

Pure JVM tests cover source-identity key separation, delimiter safety, and
conservative legacy ownership. Android integration tests should still cover:
1. Reopen a saved series without network access after upgrade.
2. Two extensions containing the same `seriesId` but different chapters.
3. Refresh/update checks and offline download-index repair for those series.
4. Process termination during chapter-list writes and subsequent recovery.

This document is not evidence that device-level verification has completed.
