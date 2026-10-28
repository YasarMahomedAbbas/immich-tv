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
