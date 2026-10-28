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

- Shipped: `LayerRules.check` in `app/src/test/java/io/github/yasarmahomedabbas/immichtv/LayerRules.kt:16` (rules by layer at `:31`, comment/string blanking at `:58`); `ArchitectureTest` over `src/main/java` in `ArchitectureTest.kt:10`; 15 synthetic cases in `LayerRulesTest.kt`; empty `domain/Domain.kt` and `data/Data.kt` packages; one paragraph in `CLAUDE.md` § Layers (`CLAUDE.md:25`).
- Tests: 16 passed / 0 failed (`LayerRulesTest` 15, `ArchitectureTest` 1). Full CI command (`testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`) BUILD SUCCESSFUL; the 19 lint warnings are all pre-existing (manifest, banner, version catalog). Negative check: adding `import android.os.Bundle` to `domain/Domain.kt` made `ArchitectureTest` fail with `.../domain/Domain.kt:8: domain/ is plain Kotlin: ... (android.os.Bundle)`; reverted.
- Outstanding: none.
- Contradicted the plan: the checkpoint 1 negative check named `import okhttp3.OkHttpClient`, but OkHttp is not a dependency yet so the main source set would not compile; used `android.os.Bundle` instead and edited the Verify line to match. Also added a line-number test after a multi-line block comment (beyond the listed cases).
