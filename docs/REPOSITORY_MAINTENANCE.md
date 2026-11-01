# Repository maintenance and branch lifecycle

This document describes the current repository workflow and the November 2026 branch cleanup. It is operational guidance, not a substitute for verifying live GitHub settings.

## Branch and pull-request policy

- `main` is the integration branch. Create short-lived topic branches from current `main`.
- Separate behavior changes, dependency upgrades and maintenance whenever practical.
- Open a pull request describing the change, compatibility risks and validation.
- Run the **Build APK** workflow for the exact PR head commit. Merge only if the relevant required checks have completed successfully. A successful workflow on an older SHA does not validate the current PR head.
- Prefer squash merging for focused changes. Confirm that the repository's **Automatically delete head branches** setting is enabled; this is a GitHub repository setting, not a guarantee enforced by this document.
- Keep failed dependency-update PRs closed until the compatibility problem has been fixed and verified. Do not bypass failing checks.

## Local verification

Requirements and build commands are maintained in [the repository README](../README.md).

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

For changes affecting external extensions, reader persistence, downloads or filesystem access, also run relevant manual Android-device checks.

## Historical branches and recovery

In the November 2026 cleanup, old working branches were removed after a Git-tag archival procedure was run. The branch list was independently checked afterwards: only `main` remained, with no open PRs. Archived tags were not individually audited by the repository review; before restoring historical code, inspect the tag and its contents.

The AVIF Coder 3.0.0 dependency PR **#403** was closed without merging after an unsuccessful Build APK check. Keep its breaking API change separate from unrelated work. Version **2.2.1** was previously built and merged. Investigate decoder API and extension compatibility before attempting another 3.x upgrade.

To inspect archive tags from a Git checkout:

```bash
git fetch --tags origin
git tag -l 'archive/2026-cleanup/*'
git show --stat '<archive-tag>'
```

A tag preserves a historical commit; it does **not** mean that the branch's work was incorporated into `main`. When restoring useful changes, create a new branch from current `main`, cherry-pick or port selectively, add tests, and open a new PR.

## Preventing branch accumulation

Review open PRs and remote branches periodically. Before deleting a stale unmerged branch, inspect its diff against `main`, ensure any unique work is recoverable, and confirm it is not used by an open PR. Protected/default branches must never be deleted. Avoid force pushes during cleanup.

## Recent change references

- **#454:** confirmed clearing failed-download entries before executing the clear action; merged after Build APK #1411 succeeded.
- **#403:** AVIF Coder 3.0.0 update; closed without merge because Build APK #1410 failed.

Always consult live GitHub PR and workflow state for the latest status.
