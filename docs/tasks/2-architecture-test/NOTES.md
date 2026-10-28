# Notes — chore: enforce the layers with an ArchitectureTest

Issue: #2 — chore: enforce the layers with an ArchitectureTest (https://github.com/YasarMahomedAbbas/immich-tv/issues/2)
Branch: `chore/2-architecture-test`

## Planning

- Approach: a hand-rolled JUnit source scan (no new dependency): a pure `LayerRules.check` tested on synthetic sources, run by `ArchitectureTest` over `src/main/java`; empty `domain/` and `data/` packages; one sentence in `CLAUDE.md` § Layers.
- Decided (pipeline, on the user's behalf): option 1, hand-rolled source scan over Konsist or ArchUnit; flags none.
- Decided: "Android" is `android.` and `androidx.`; comments and string literals are stripped; imports and inline fully-qualified names both count.
- Decided: the empty packages are KDoc + `package` files that compile to nothing, so coverage is unchanged.
- Decided: `AppContainer` is matched as `AppContainer.kt` in the root package; `data/` files may name each other.
- Decided: the scan fails if it finds no source files.
- Open risks: none.

## Implementation

- Shipped: `LayerRules.check` in `app/src/test/java/io/github/yasarmahomedabbas/immichtv/LayerRules.kt:29` (rules by layer at `:31`, comment/string blanking at `:57`); `ArchitectureTest` over `src/main/java` in `ArchitectureTest.kt:11`; 15 synthetic cases in `LayerRulesTest.kt`; empty `domain/Domain.kt` and `data/Data.kt` packages; one paragraph in `CLAUDE.md` § Layers (`CLAUDE.md:25`).
- Tests: 16 passed / 0 failed (`LayerRulesTest` 15, `ArchitectureTest` 1). Full CI command (`testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`) BUILD SUCCESSFUL; the 19 lint warnings are all pre-existing (manifest, banner, version catalog). Negative check: adding `import android.os.Bundle` to `domain/Domain.kt` made `ArchitectureTest` fail with `.../domain/Domain.kt:8: domain/ is plain Kotlin: ... (android.os.Bundle)`; reverted.
- Outstanding: none.
- Contradicted the plan: the checkpoint 1 negative check named `import okhttp3.OkHttpClient`, but OkHttp is not a dependency yet so the main source set would not compile; used `android.os.Bundle` instead and edited the Verify line to match. Also added a line-number test after a multi-line block comment (beyond the listed cases).

## Review

- Fixed: `LayerRules.blankCommentsAndStrings` blanked the code inside `${...}` string templates, so `"${io.github.yasarmahomedabbas.immichtv.data.X.NAME}"` in `ui/` or `"${android.os.Build.VERSION}"` in `domain/` passed; template code is now scanned. Nested block comments (`/* a /* b */ okhttp3.Call */`) ended at the first `*/` and failed on comment text; they now nest. Three `LayerRulesTest` cases, each failing before the fix (18 checker tests + `ArchitectureTest`, full CI command green).
- Raised: none
- API: none (no server calls)
- Layers: clean

## Doc drift

- Applied: none in this stage. The one new fact (the `ArchitectureTest` exists, runs in CI, and checks names not shapes) is already in `CLAUDE.md` § Layers from implementation (rung 2: every session touching a layer needs to know which rules CI enforces and which are still for review); checked against `LayerRules.kt`: `ui/`/`domain/` → `data/`, Android/OkHttp/kotlinx.serialization in `domain/`, `data/` names outside `data/` and `AppContainer.kt`, imports and inline qualified names alike. Accurate.
- Skipped: README § Development / testing: no mention of the `ArchitectureTest`. It runs inside `testDebugUnitTest`, which the README already lists, and a failure names the broken rule; `CLAUDE.md` covers it.
- Skipped: the KDoc in the new `domain/Domain.kt` and `data/Data.kt` restates `CLAUDE.md` § Layers; consistent with it, nothing to change.
- Stale, fixed separately: none. Noted, not fixed: README § Architecture and § Development describe pieces not yet in the code (`AppContainer`, the Immich REST client, MockWebServer tests). This is the planned architecture of a freshly scaffolded app that #3 builds next, not drift; rewriting it as "not yet" would be reverted by #3.

## Self verification

Result: passed

- CI build: full CI command BUILD SUCCESSFUL; 19 tests passed, 0 failed, 0 skipped (`LayerRulesTest` 18, `ArchitectureTest` 1); `koverVerifyDebug` passed with an empty error report (the branch adds no main-source classes: `Domain.kt` and `Data.kt` are a `package` line only); lint 19 warnings, 0 errors, all pre-existing; `app-debug.apk` built.
- Server: not in scope (the branch makes no Immich calls; `verify.env` also absent on this machine).
- TV: not in scope (no runtime change: the new main-source files compile to nothing, the rest is test code and docs).
- Verified: the layer rules are enforced in `testDebugUnitTest` (`ArchitectureTest` passes over the real `src/main/java`; implementation's negative check showed it fails on `android.os.Bundle` in `domain/`).
- Failed: none
- Not self-verifiable: none
