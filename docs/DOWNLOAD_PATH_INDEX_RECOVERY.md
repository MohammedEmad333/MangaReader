# Download path index resilience

The readable download tree (`Source/Series/Chapter`) is backed by
`download_paths.json`. The index now uses Android `AtomicFile` for crash-safe
writes and backup-aware reads, including legacy JSON without format changes.

When a previously saved index cannot be parsed, the app attempts to
reconstruct chapter paths from the existing per-chapter `.chapterid` markers.
Malformed paths, traversal segments, absolute paths, and entries with invalid
folder names are ignored rather than used for filesystem access.

This does not delete downloaded pages or modify the configured storage root.
Older downloads with intact index data retain the same relative paths.

## Verification

Unit tests cover valid Unicode paths, directory traversal, absolute paths,
invalid segments, and malformed folder names. Device verification is still
required for interrupted writes, SAF/external storage, and metadata recovery
for old folders whose marker files are missing.
