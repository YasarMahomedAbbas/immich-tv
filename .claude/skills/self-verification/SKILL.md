---
name: self-verification
description: >-
  Use when a branch is built and reviewed and needs proof it works beyond the unit tests — "verify this", "self-verify", or the pipeline's fifth stage. Fully autonomous: runs the full CI command in Docker, checks every Immich call the branch touches against the real server (the spec for its version, read-only calls), installs the debug build on the Chromecast over adb and drives the changed screens with the D-pad, reading screenshots, fixes in-scope bugs and re-runs, and records a Result line in NOTES. Never asks.
---

# Self Verification

`start-ticket` → `implement-plan` → `self-review` → `doc-drift` → **`self-verification`** →
`raise-pr` → `merge-release`

Turns "the unit tests are green" into "it builds as CI builds it, the server answers the way
the code expects, and it works on the TV". MockWebServer accepts things a real Immich refuses,
and a Compose screen that passes every test can still trap the D-pad, so the server and the
TV are the checks that matter.

## Principles

- **Never ask, never wait.** A question becomes a recorded finding.
- **Never claim a step you didn't run.** "Not self-verifiable" is a legitimate outcome.
- **Fix what's in scope, record what isn't.**
- **Real photos are the household's.** The app only reads, and so does this stage: no call
  that creates, changes or deletes anything on the server, ever.

```bash
set -a; . ~/.config/immich-tv/verify.env; set +a   # IMMICH_URL, IMMICH_API_KEY, TV_ADB
```

No file → record the server and TV steps as `not run (no verify.env)`; the Result is at best
`partial`.

## 1. Scope

```bash
git fetch -q origin main
git diff --stat origin/main...HEAD
```

Read `CHECKLIST.md` (the "Done when" items and the test plan's *Real server* and *On the TV*
lines) and NOTES `## Implementation`.

## 2. The CI build

```bash
./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug
```

Record the real test counts and coverage. Red → fix (in scope) and re-run.

## 3. The real server

Only when the diff touches `data/` calls to Immich. Unreachable → `Server: not run
(<why>)`, and go on.

1. **Version and spec**: `curl -s -H "x-api-key: $IMMICH_API_KEY" "$IMMICH_URL/api/server/version"`,
   then fetch `open-api/immich-openapi-specs.json` at that tag from the Immich repo. Check
   each path, method, parameter and field the code uses exists with the type the code
   expects.
2. **Read calls**: make each GET the branch added, with `curl`, exactly as the code builds it
   (same path, query, headers), and check the response parses into what the code reads. Save
   a trimmed response as a MockWebServer fixture if the tests used an invented one; strip
   anything personal (names, places, file names) from a fixture before committing it.
3. **Never** a POST, PUT, PATCH or DELETE that changes server state. A search is a POST that
   only reads (`/api/search/smart`, `/api/search/metadata`): those are allowed. Anything else
   is `checked against the spec only`.

A mismatch is a bug in this branch: fix it in `data/`, add the test that would have caught
it, re-run step 2.

## 4. On the TV

Only when the diff changes something the user sees or the app's behaviour on the device.

```bash
adb connect "$TV_ADB"            # "connected" or "already connected"; anything else → not run
./build.sh install               # builds, installs, opens the app
```

Unreachable or unauthorised → `TV: not run (<why>)`, the steps go to "Not self-verifiable",
and go on. The TV may be asleep: `adb -s "$TV_ADB" shell input keyevent WAKEUP` first.

Drive each path the checklist's *On the TV* line names, with the remote's keys:

```bash
adb -s "$TV_ADB" shell input keyevent DPAD_RIGHT     # DPAD_UP/DOWN/LEFT/RIGHT, DPAD_CENTER, BACK, MEDIA_PLAY_PAUSE
adb -s "$TV_ADB" exec-out screencap -p > <scratchpad>/tv-<step>.png
```

Read each screenshot and check, against `docs/design/README.md` and the canvas: the screen
shows what the ticket asked, the focus outline is visible and where the key press should have
put it, nothing is cut off at the edges, text is readable. For the slideshow, take two
screenshots a few seconds apart to see it advance. Also check the app hasn't crashed:

```bash
adb -s "$TV_ADB" logcat -d -t 300 '*:E' | rg -i 'immichtv|AndroidRuntime'
```

Screenshots stay in the scratchpad; they show the household's photos and are never committed
or attached to the PR. Describe what they showed instead.

Leave the TV as you found it: `adb -s "$TV_ADB" shell input keyevent HOME`.

## 5. Record

Append to NOTES:

```markdown
## Self verification

Result: passed | partial | failed | not applicable

- CI build: <N tests passed, coverage X%, lint clean, APK built>
- Server: <Immich version> | not run (<why>)
- TV: <installed on the Chromecast, the paths driven> | not run (<why>)
- Verified: <each call or step, and what proved it>
- Failed: <step — what happened> | none
- Not self-verifiable: <step — what to check on the TV by hand> | none
```

- `passed`: everything in scope ran and passed.
- `partial`: everything that ran passed; some steps couldn't run (server or TV unreachable).
- `failed`: something ran and failed and couldn't be fixed in scope. `raise-pr` opens a
  draft and `merge-release` won't merge it.
- `not applicable`: docs or tooling only.

Commit via `commit-code`: `docs(tasks): record #<n> verification`.
