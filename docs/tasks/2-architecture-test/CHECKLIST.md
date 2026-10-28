# Checklist — chore: enforce the layers with an ArchitectureTest

Issue: #2 — chore: enforce the layers with an ArchitectureTest (https://github.com/YasarMahomedAbbas/immich-tv/issues/2)
Branch: `chore/2-architecture-test`
Problem: the layer rule (`ui/` → `domain/` ← `data/`, only `AppContainer` names a `data/` class) is only written down, so nothing stops a later ticket breaking it.

## Done when

- A unit test fails the build when a file in `ui/` or `domain/` imports from `data/`, when `domain/` imports Android, OkHttp or kotlinx.serialization, or when anything other than `AppContainer` names a `data/` class.
- It runs in `testDebugUnitTest`, so CI enforces it.
- The empty layer packages exist (`domain/`, `data/`), and `CLAUDE.md` § Layers mentions the test.

## References

- Immich API: none (no server calls).
- `CLAUDE.md` § Layers; `.claude/skills/implement-plan` and `self-review` already name `ArchitectureTest`.

## Context

The app is a skeleton: `ImmichTvApp`, `MainActivity`, `ui/home/HomeScreen.kt`, `ui/theme/Theme.kt`. There are no `domain/` or `data/` packages, no `AppContainer` and no unit tests, so this test sets the pattern for the test source set (`app/src/test/java/io/github/yasarmahomedabbas/immichtv/`, JUnit 4, already a `testImplementation`). Decided: a hand-rolled source scan with no new dependency (option 1, chosen by the pipeline). The rule logic is a pure function over `(path, text)` pairs, tested on synthetic sources so the test provably fails on a violation, plus one test that runs it over the real main source set. "Android" means `android.` and `androidx.`. Comments and string literals are stripped before matching, and both `import` lines and inline fully-qualified names count. `AppContainer` is matched by file name `AppContainer.kt` in the root package (`io/github/yasarmahomedabbas/immichtv/AppContainer.kt`); files inside `data/` may name each other. The scan fails if it finds no `.kt` files, so a wrong path cannot pass silently. The empty packages are Kotlin files with only a KDoc and a `package` line: they compile to nothing, so Kover sees no new classes.

## Test plan

- Unit: `LayerRulesTest` on synthetic sources: a clean `ui/`, `domain/` and `data/` set passes; `ui/` importing `...immichtv.data.X` fails; `domain/` importing `...data.X` fails; `domain/` importing `android.os.Bundle`, `androidx.annotation.Keep`, `okhttp3.OkHttpClient`, `kotlinx.serialization.Serializable` fails (one case each); an inline fully-qualified `io.github.yasarmahomedabbas.immichtv.data.X()` in `ui/` fails; root `MainActivity.kt` naming `data` fails; root `AppContainer.kt` naming `data` passes; `data/` files naming each other pass; `data.` inside a comment or a string literal in `domain/` passes; `domain/` importing `kotlinx.coroutines.flow.Flow` passes; each violation's message names the file, line and rule. `ArchitectureTest`: the real `src/main/java` tree has more than zero files and no violations.
- MockWebServer: none.
- Real server: none.
- On the TV: none (no runtime change).

---

## Checkpoint 1 — layer packages and the ArchitectureTest · commit: `test(build): enforce the layer rules with an ArchitectureTest`

Goal: `domain/` and `data/` exist and `testDebugUnitTest` fails on any layer violation in the main source set.

### [ ] 1.1 — Empty layer packages

Files: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/Domain.kt`, `app/src/main/java/io/github/yasarmahomedabbas/immichtv/data/Data.kt`
Each file is a KDoc saying what the layer holds (take it from `CLAUDE.md` § Layers, in one or two sentences, and point to `ArchitectureTest`), then the `package` line and nothing else. Keep words like `android.`/`okhttp3.` out of the KDoc anyway, even though comments are stripped.

### [ ] 1.2 — The rule checker

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/LayerRules.kt`
```kotlin
data class SourceFile(val path: String, val text: String)   // path relative to src/main/java, '/' separators
data class Violation(val path: String, val line: Int, val rule: String, val reference: String)
object LayerRules {
    const val ROOT = "io/github/yasarmahomedabbas/immichtv"
    fun check(files: List<SourceFile>): List<Violation>
}
```
- Layer of a file: the first path segment after `ROOT/` (`ui`, `domain`, `data`), else `root`.
- Strip `//` and `/* */` comments and string literals (`"..."`, `"""..."""`) before matching, keeping line numbers (replace with spaces / keep newlines).
- Match qualified names with `\b(io\.github\.yasarmahomedabbas\.immichtv\.data|androidx?|okhttp3|kotlinx\.serialization)\.` on import lines and in the body alike.
- Rules: (1) outside `data/`, only `ROOT/AppContainer.kt` may reference `...immichtv.data.` — rule text `"only AppContainer names a data/ class"`; for `ui/` and `domain/` files use `"ui/ and domain/ never import data/"`. (2) in `domain/`, no `android.`, `androidx.`, `okhttp3.`, `kotlinx.serialization.` — rule `"domain/ is plain Kotlin: no Android, OkHttp or kotlinx.serialization"`.
- `Violation.toString()` gives `path:line: rule (reference)`.

### [ ] 1.3 — Tests for the checker

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/LayerRulesTest.kt`
JUnit 4, one `@Test` per case listed under Test plan → Unit (synthetic `SourceFile`s), asserting the exact violation list (empty, or path + line + rule).

### [ ] 1.4 — The ArchitectureTest over the real sources

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ArchitectureTest.kt`
Read every `.kt` under `File("src/main/java")` (Gradle runs unit tests with the module dir as working dir); `assertTrue` the list is non-empty with a message naming the path; `assertEquals(emptyList<Violation>(), LayerRules.check(files))` with the violations joined one per line in the message.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*LayerRulesTest' --tests '*ArchitectureTest'`
- Temporarily add `import okhttp3.OkHttpClient` to `domain/Domain.kt`: `ArchitectureTest` fails naming that file and line; remove it again (not committed).
- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug` passes.

---

## Checkpoint 2 — document the test · commit: `docs: name the ArchitectureTest in the layer rules`

Goal: `CLAUDE.md` § Layers says the rule is enforced and by what.

### [ ] 2.1 — CLAUDE.md § Layers

File: `CLAUDE.md`
After the first paragraph of § Layers, one sentence: `ArchitectureTest` (in `testDebugUnitTest`, so CI) fails on a `ui/`/`domain/` import of `data/`, on Android, OkHttp or kotlinx.serialization in `domain/`, and on any `data/` reference outside `data/` and `AppContainer`; it checks names, not shapes, so Immich field names are still for review.

### Verify

- `rg -n ArchitectureTest CLAUDE.md` shows the line.

---

## Out of scope

- `AppContainer` itself, or any domain or data code beyond the empty packages.
- Rules beyond the ticket's (Immich field names, `org.json`, `java.net`, ui → Android restrictions).
- A new dependency (Konsist, ArchUnit) or a Gradle/CI change: the test runs in the existing `testDebugUnitTest`.
- README changes (doc-drift decides).
