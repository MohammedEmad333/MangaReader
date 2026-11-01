# Yomu — downloaded-series source identity

Prior Downloads grouping used `seriesId` alone. Separate manga sources can
reuse that identifier, so their chapters could appear together in Downloads.

The current download-index view groups by (`sourceId`, `seriesId`), retaining
the chapter order, sizes and per-series cover. New cached offline covers also
use source-qualified names so they cannot overwrite one another.

The persisted download-index JSON now uses Android `AtomicFile` to retain a
previous complete snapshot if the app stops during an update.

### Compatibility

Existing records and their stored `cover` paths remain readable. Old
series-only cover files are deliberately **not deleted** when a source-qualified
series is removed: a different existing record may still reference one.
Clearing downloads/storage using existing settings remains available.

This change does not yet change the underlying page-folder key, which remains
chapter-ID based for backwards compatibility. Distinct sources sharing the
same *chapter ID* require separate storage migration planning and on-device
testing. Likewise, source-qualified IDs across library badges/categories are
a separate compatibility project.

### Tests needed on device

- Download similarly identified series from two different extensions.
- Restart Yomu during download-index writes; verify intact records.
- Remove one source's downloaded series and check the other still opens.
- Restore older downloads and ensure the recorded cover paths remain usable.

JVM tests cover grouping and cover-filename identity, not device behavior.
