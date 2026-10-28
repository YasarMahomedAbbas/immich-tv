---
name: self-review
description: >-
  Use when a branch's implementation is finished and needs its pre-PR review — "review the branch", "self-review", or the pipeline's third stage. A deliberately narrow review of the diff against origin/main: correctness, checklist conformance, the layer rules, Immich API conformance (reference and the server version's spec), the TV rules, security of the API key, and test honesty. Not a style or naming pass. Fixes what it finds, records the result in NOTES.
---

# Self Review

`start-ticket` → `implement-plan` → **`self-review`** → `doc-drift` → `self-verification` →
`raise-pr` → `merge-release`

Design and structure were settled at planning. This review hunts for what a checklist can't
catch: defects that exist only once real code exists.

## What it looks for

1. **Correctness**: logic errors, inverted conditions, unhandled empty, loading or error
   states, swallowed failures, coroutine scope and cancellation mistakes, state that survives
   a rotation wrongly. Each finding needs concrete inputs that produce the wrong outcome.
2. **Checklist conformance**: every "Done when" item and every ticked task is in the diff;
   nothing is in the diff that Out of scope excluded.
3. **Layers** (`CLAUDE.md` § Layers): no `ui/` or `domain/` import of `data/`; no Android,
   OkHttp or JSON in `domain/`; no Immich field name, path, version or enum string
   outside `data/`; domain models named in the app's words. `ArchitectureTest` catches
   imports; you catch the names and shapes it can't.
4. **Immich API conformance**, for every call the diff touches: method, path, query
   parameters, request body and response fields against the reference
   (https://api.immich.app) and the spec for the server's version (`$IMMICH_URL/api/server/version`,
   then `open-api/immich-openapi-specs.json` at that tag; `~/.config/immich-tv/verify.env`).
   Every version the code claims to read has a test. MockWebServer responses match what the
   real server sends, not a convenient fiction.
5. **TV and memory**: every new focusable item is reachable with the D-pad and shows focus;
   Back never traps; images are preview-size, decoded at screen size, and a long slideshow or
   grid doesn't hold every bitmap it has shown.
6. **Security**: the API key is never logged, put in a URL or stored outside the settings
   store; image requests carry it as a header; nothing exported in the manifest that
   shouldn't be.
7. **Test honesty**: tests assert the behaviour the test plan named, not a mocked-out subject
   or an always-true condition. A bug fix has a test that fails without it.

**Not flagged**: naming, formatting, "could be simpler", alternative designs, anything lint
enforces.

## Steps

1. ```bash
   git fetch -q origin main
   git diff origin/main...HEAD --stat
   git diff origin/main...HEAD
   ```
   Read `CHECKLIST.md` and NOTES `## Implementation` in `docs/tasks/<n>-<slug>/`.
2. Work through the seven categories, reading surrounding code where a hunk depends on it.
3. Try to refute each candidate: re-read the path, look for an upstream guard, run the test.
   Keep only what survives, with `file:line`, the failure scenario and the fix.
4. Fix every surviving finding that is in this branch's scope, re-run the affected tests and
   the full CI command (`./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug
   lintDebug assembleDebug`), and commit via `commit-code` (`fix(<scope>): address
   self-review findings`). A finding outside the ticket's scope becomes a new issue
   (`gh issue create`, label `bug` or `enhancement`), not a fix here.
5. Append to NOTES:

   ```markdown
   ## Review

   - Fixed: <findings fixed on this branch, or none>
   - Raised: <issue links for out-of-scope findings, or none>
   - API: <each endpoint touched — matches docs and schema / the difference>
   - Layers: clean | <what was moved>
   ```

   An empty review of a well-planned branch is a good result. Don't pad it.

## Guardrails

- Every finding has a concrete failure scenario.
- Never restructure at review time; a real structural problem is a `scope-stop`.
- The result, clean included, is recorded in NOTES.
