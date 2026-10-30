# Notes — bug: a fast run of Down presses on the Photos grid freezes the app until Android kills it (ANR)

Issue: #27 — bug: a fast run of Down presses on the Photos grid freezes the app until Android kills it (ANR) (https://github.com/YasarMahomedAbbas/immich-tv/issues/27)
Branch: `fix/27-pace-grid-key-presses`

## Planning

- Approach: on the shared photo grid, consume an Up or Down press that comes under 100 ms after the last accepted move or before a frame has run, so presses can't queue focus searches on the main thread.
- Decided (the pipeline, on the user's behalf; flags: ux, scope, open-risk): how to stop the freeze. Took option 1, pace Up and Down moves. A burst no longer travels its full count, and a hold has a capped speed. Not taken: option 2, upgrade Compose (the cause isn't proven fixed upstream, and it touches every screen); option 3, instant scrolling (changes the feel and doesn't help when no frame runs between keys).
- Decided (the pipeline, on the user's behalf; flags: ux, scope, open-risk): the ticket's held Down on the real remote, which no pipeline can press. Took option 1, a paced adb stand-in plus the burst as this pipeline's check, with the real-remote hold listed for the user as a hand check, and the ticket can merge on that. The stated criterion isn't literally met when it ships. Not taken: option 2, park until the user has tried the remote (blocks on the user); option 3, keep the ticket open until the user confirms (leaves an open issue after release and blocks tickets that need it).
- Decided (the pipeline, flag if wrong): Up and Down only; the rail, Albums and search grids are untouched; refused presses are consumed; the 100 ms is a constant tuned on the TV.
- Tests: the cause can't be reproduced off the device, because the project has no Robolectric or Compose UI tests. A JVM test covers the pacing logic and fails without it; the TV check is the proof.
- Speed note from the user for 2026-10-30: unit tests for changed classes only, the full CI command once in `raise-pr`, the TV check limited to this ticket with the lock held 10 minutes at most and released before any fix.
- Open risks: the cause is a hypothesis from Compose's main-branch source and two stack samples, not proven; pacing should bound it anyway. A quick double tap under 100 ms loses its second press. The TV may be unavailable: the window ends about 14:25 CET on 2026-10-30 and the lock is shared; if skipped, the fix ships unproven on the device. Release builds may behave better than the debug build the ANR was seen on; not measured.

## Implementation

- Shipped: `MovePace`, the plain pacing logic (`ui/photos/MovePace.kt:15`); the grid consumes an Up or Down press it refuses, and counts a frame after each accepted one (`ui/photos/PhotoGrid.kt:129`). Left, Right, OK and key up pass through untouched.
- Tests: `MovePaceTest` 6 passed / 0 failed; with `accept` always true, 4 of the 6 fail. The grid wiring has no unit test (no Compose UI tests in the project); the TV check covers it. The full CI command wasn't run here, per the speed note: it runs once, in `raise-pr`.
- Outstanding: none in the checklist's tasks. The on-TV checks in the test plan are for self-verification.
- Contradicted the plan: none. One test added beyond the plan: an accepted move needs a new frame before the next.

## Review

- Fixed: none
- Raised: none
- API: none touched; the branch adds and changes no Immich call.
- Layers: clean

## Doc drift

- No drift found.
- Checked: README's feature list and architecture block (it names layers, not files, so `MovePace` changes nothing), CLAUDE.md's and README's commands against `ci.yml` and `build.sh` (CLAUDE.md leaves out CI's two Kover report tasks, which only produce reports), `docs/design/README.md`'s `PhotoGrid` rows.
- Skipped: the pacing of Up and Down on `PhotoGrid`: `PhotoGrid`'s KDoc says it and why, and `MovePaceTest` holds the logic, so anyone changing the grid sees it.
- Skipped: a general rule to pace every grid: the cause isn't proven, and the Albums and search grids are out of this ticket's scope.
- Skipped: a README line on the capped speed in the grid: it's tuning, not a feature someone uses or configures.
- Stale, fixed separately: none

## Self verification

Result: partial

- CI build: not run here, per the user's speed note for 2026-10-30; the full command runs once, in `raise-pr`. `assembleDebug` built the APK in Docker. `MovePaceTest` results are under Implementation.
- Server: not applicable; the branch adds and changes no Immich call.
- TV: installed the debug build on the Chromecast and drove the Photos grid and one album's photos, the lock held about 8 minutes. One process throughout (same pid), and the `reason=6` count in `dumpsys activity exit-info` stayed at 2 from before the install to after the last check; the newest ANR on record is 13:05, before this install. No `immichtv`, `AndroidRuntime` or `ANR in` lines in logcat errors.
- Verified: 33 Downs as one `input keyevent` command, twice: the app stays open, focus visible on a tile; the first run went from the top of March 2025 into December 2024.
- Verified: 150 Ups as one command from December 2024: the grid is back at its top and focus is in the top bar. 120 Ups mid-grid and 33 Downs at the grid's last row: no stall.
- Verified: single presses at about 1.5 a second: three presses from mid-grid moved focus row by row; the shots after the first and third show each press landed.
- Verified: straight after a burst, Left reached the month rail (from the first column), OK opened the focused photo and Back returned to the grid.
- Verified: 33 Downs twice on an album's photos: focus reached the last row, Back returned to the album screen. That album has only 14 items, so it reached the end within the burst.
- Failed: none
- Not self-verifiable: the held Down on the real remote: hold Down on the Photos grid for about 10 seconds and check the app stays open. Planned as a hand check.
- Not self-verifiable: the paced stand-in at the planned 20 to 30 keys a second. No sender was confirmed at that rate: `monkey --port` with an acknowledgement per press gave about 4.5 a second, and sending 25 a second without waiting got 286 of 500 key events acknowledged in about 15 seconds, about 10 presses a second. Three `input keyevent` senders in parallel, 300 Downs, took at most about 18 seconds, so at least about 16 a second. All of these stayed clear of an ANR, but they are slower than the plan's rate and the 100 ms gap wasn't tuned because nothing called for it.
- Not self-verifiable: a burst into a month still loading. The first burst after a fresh install crossed into months not yet shown, but no loading state was seen on screen, so this edge isn't proven.
- Not measured: the same bursts on the build without the fix, this session; the ANR before the fix rests on the ticket's reports and the exit history. Release build: only the debug build was driven.
