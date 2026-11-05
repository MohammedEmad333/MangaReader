# Yomu — library source-collision safeguards

The library, categories, bulk selection, and series-count index originally
assumed that a `seriesId` uniquely identifies a series across all extensions.
That is not guaranteed when sources are installed independently.

## This stage

- A normal add cannot silently overwrite an existing saved entry from a
  **different source** with the same `seriesId`. The dialog shows an error.
- Import batches preserve the existing source owner of a conflicting ID
  rather than replacing it. Conflicting new IDs with multiple source owners
  are skipped until the rest of the index model is upgraded.
- Exact-source checks are used for library membership, removal, and cover
  repair in the series screen and source-detail refresh.
- Reader UI state, Compose row keys, and last-read sort keys include both
  source and series identity.
- The JSON layout of existing library entries is unchanged.

## Still required for full multi-source support

This is deliberately a **collision guard, not full migration**. Library
selection, category assignments, SeriesIndex counts, backups, and bulk actions
still use plain `seriesId`. Fully accepting duplicate IDs would allow bulk
operations to unintentionally affect more than one source. The app therefore
prevents adding such collisions until those dependent stores and screen actions
can migrate together with a documented rollback path.

## Validation

JVM tests cover same-source updates, cross-source conflicts, ambiguous import
batches, stable identity keys, and independent last-read ordering. Real-device
tests must cover legacy backups, source switching, and user-facing collision
errors before declaring library identity migration complete.

## Source-safe Tachiyomi backup import

A backup's "not a favourite" entries now remove only the matching
(`sourceId`, `seriesId`) library entry rather than deleting every series
that shares the series ID. Before mutation the importer computes which
favourite entries can be accepted without replacing another source.

Only accepted favourites receive category assignments and reading-history
updates. The import summary reports how many favourite entries were accepted
and how many were skipped because their source/series identities conflict.
Chapter read flags and saved pages remain available independently of library
favourite status, as before.

The full source-qualified category/index migration is still outstanding;
existing accepted data is intentionally preserved instead of pretending
that ambiguous imported series have been merged correctly.
