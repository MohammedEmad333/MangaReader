# Release signing

The release workflow supports reconstructing `release.keystore` from the GitHub Actions secret `RELEASE_KEYSTORE_BASE64`.

## 1. Back up the current release key

Keep the existing `release.keystore` in at least one secure offline location before removing it from Git. Android app updates must be signed with the same release key as previous releases.

Do not regenerate the release key for an existing published signing identity.

## 2. Encode the keystore

On Linux:

```bash
base64 -w 0 release.keystore > release.keystore.base64
```

On macOS:

```bash
base64 < release.keystore | tr -d '\n' > release.keystore.base64
```

Copy the resulting single-line value. Do not commit the encoded file.

## 3. Add GitHub Actions secrets

Repository → Settings → Secrets and variables → Actions → New repository secret.

Create:

- `RELEASE_KEYSTORE_BASE64` — the complete base64-encoded keystore.
- `RELEASE_KEYSTORE_PASSWORD` — the existing release keystore/key password expected by the Gradle signing configuration.

The workflow decodes `RELEASE_KEYSTORE_BASE64` into `release.keystore` only for a manually requested release build.

## 4. Verify before removing the tracked key

Run the **Build APK** workflow manually with the release option enabled.

Confirm that:

1. unit tests pass;
2. the debug APK builds;
3. the release APK builds;
4. the release APK is signed successfully;
5. the release APK can update an installation signed with the previous release artifact.

Only after that verification should the tracked `release.keystore` be removed from the current repository tree.

## 5. Remove the tracked key

Once the secret-backed build is verified:

```bash
git rm release.keystore
git commit -m "Remove tracked release keystore"
```

The repository already ignores `*.keystore`, so the file will not be accidentally re-added.

## History cleanup

Removing the file from the current branch does **not** remove it from Git history.

A full history purge requires rewriting repository history (for example with `git filter-repo`) and force-pushing rewritten refs. That operation invalidates existing clones and should be scheduled separately after collaborators are informed.

For a private repository, first moving active CI signing to secrets and deleting the key from the live tree is the safer incremental step.
