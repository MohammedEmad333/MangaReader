# Contributing

## Before changing code

1. Keep behavior changes focused and small.
2. Check whether the code participates in extension compatibility, R8 rules, storage migration, or persisted data.
3. Add or update tests for pure logic when practical.
4. Do not commit signing material, local properties, IDE state, or build output.

## Validation

At minimum, run:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

For changes affecting source extensions or shrinking, also verify on a device:

- app startup
- extension loading
- source browse/search
- series details
- reader
- downloads
- library
- at least one source using compressed network responses

## Dependency changes

Do not casually upgrade Kotlin, OkHttp, kotlinx.serialization, or related source-api dependencies independently. They form a compatibility set with external extension APKs.

Shared versions belong in `gradle/libs.versions.toml` where possible.

## Pull requests

Keep refactors separate from feature work when practical. A behavior-preserving cleanup is easier to review and safer to revert when it does not also change product behavior.
