# Offline recovery: ambiguous source and chapter identities

A readable downloaded chapter may be represented by its stored relative path,
a source-folder map, a source-qualified series-folder map, and the chapter
directory's `.chapterid` marker.

After a corrupted index or an imported/moved directory, one folder can have
multiple recorded owners. The old recovery path selected the first owner from
the index; the directory scan selected the last encountered path for a duplicate
chapter marker. Both are unsafe because they can attribute offline content to
an unrelated source or manga series.

The recovery procedure now refuses ambiguous owner matches and collects
chapter markers before writing the resulting index. Markers with conflicting
paths are excluded rather than picking a winner based on filesystem traversal
order. Rebuilding the index also persists removals of stale ambiguous mappings.

This does **not** delete chapter files. When the data is ambiguous, the app may
not list an offline chapter until its owner is re-established through a valid
download index or a fresh download from the source.

JVM tests cover duplicate source folders, same-named folders in distinct
sources, conflicting chapter markers, and malformed identities. Real-device
checks remain needed for external storage and index restoration.
