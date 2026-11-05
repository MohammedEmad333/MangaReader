# Source-scoped download queue controls

The series screen previously determined whether the Stop button should appear,
and which queued jobs it should remove, by `seriesId` alone. Different
extensions can use identical series IDs, so stopping downloads from one screen
could unexpectedly stop jobs belonging to another source.

The Stop action and its displayed status now use both `sourceId` and `seriesId`.
The source-qualified identity is forwarded end-to-end from the series route
through the action controller to the persisted download queue.

The existing `ACTION_SKIP` service behavior is retained: the active transfer
is interrupted only if its chapter ID was among the removed jobs. Other queued
jobs remain in their previous order.

The persisted download format does not change. Note that the underlying queue
and download-page directories still use globally keyed chapter IDs; a migration
to fully source-qualified chapter storage needs a dedicated compatibility plan.

## Checks

JVM unit tests cover overlapping series IDs, blank identities, and duplicate
chapter IDs. Device regression tests should verify stopping an active transfer,
stopping queued jobs from a non-active source, and process restart during Stop.
