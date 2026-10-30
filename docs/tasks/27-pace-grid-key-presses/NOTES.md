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
