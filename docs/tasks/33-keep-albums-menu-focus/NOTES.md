# Notes — bug: the albums arriving pull focus off the top menu on the Albums tab

Issue: #33 — bug: the albums arriving pull focus off the top menu on the Albums tab (https://github.com/YasarMahomedAbbas/immich-tv/issues/33)
Branch: `fix/33-keep-albums-menu-focus`

## Planning

- Approach: hand `MainScreen`'s `mayTakeFocus` to the Albums tab and ask it before the first card takes focus, with a real-key focus test modelled on `PhotoGridFocusTest`.
- Decided (the pipeline, on the user's behalf; flags: scope, open-risk): option 1 of 3, hold until #28 was merged, then cut from fresh `origin/main` (90be591) and reuse its focus question and test harness. The alternatives were rebuilding them here, which duplicates and conflicts, and stacking on #28's branch, which needs rework after its squash merge. #28 merged as PR #37 during the hold. The issue was not edited.
- Decided (stood after checking against the merged code): no Immich API call changes, so the plan needs no spec check.
- Decided: only first focus asks. Coming Back to a remembered album restores without asking, as `PhotoGrid` does for a tile.
- Decided: Try again and Refresh still hand focus to the card on arrival, because the rule covers "nothing holds focus".
- Decided: the refresh branch that refocuses when the focused album disappears is out of scope.
- Decided: test cases are no key pressed, Right during the skeleton, Try again and arriving on an already-loaded tab, plus Left during the skeleton, a tab switch, a remembered album and a chip, added while writing the checklist.
- Decided while writing the checklist, flag if you disagree: a chip that brings the grid back with no remembered album in the list keeps focus instead of losing it to the first card. This is new behaviour, and it follows from the rule.
- Decided (the pipeline, on the user's behalf; flags: ux, scope, open-risk): plan brief approved with option 1 of 2, the plan as written, including a chip keeping focus. The alternative was keeping today's move to the first card for that chip, which adds a condition to the rule for a rare path.
- Decided (the pipeline's revision): the docs checkpoint also confirms `MainScreen`'s KDoc, reworded in 1.1, no longer names only the Photos tab.
- Speed note: the user's note for today's run is in the checklist's Context.
- Open risks: (1) The TV window ends about 15:30 CET on 2026-10-30; the on-TV check is skipped and recorded as skipped if it is reached later. (2) The Home screen takes focus on arrival the same way, unasked. It is out of scope here, and no ticket for it was checked for. (3) The albums' first load is short on the real server, so the on-TV Right during the skeleton may be hard to land; the unit test is the proof either way.

## Red proof (checkpoint 1)

- With `mayTakeFirstFocus()` replaced by `true` in `AlbumGrid`'s first-focus effect: `AlbumGridFocusTest` 8 run, 3 failed (Right during the skeleton, Left during the skeleton, the chip case). Check put back: 8/8 green, `PhotoGridFocusTest` 6/6 green.
- The chip case's first route was wrong: Down from the pill skips the toolbar and lands on the empty state's Show all albums; Up from there reaches Shared with me. The test now asserts that route, and the checklist says so.

## Implementation

- Shipped: `MainScreen` hands `mayTakeFocus` to the Albums tab as well as Photos (`ui/main/MainScreen.kt:52`, `:72`), `MainActivity` passes it to `AlbumsScreen` (`MainActivity.kt:122`), and `AlbumGrid` asks it after its first frame unless restoring a remembered album (`ui/albums/AlbumsScreen.kt:152-156`); `AlbumsContent` is now `internal` for the test. New `AlbumGridFocusTest` (8 cases). Manual Top menu line and README testing line name the Albums grid too.
- Tests: `AlbumGridFocusTest` 8 passed / 0 failed, `PhotoGridFocusTest` 6 passed / 0 failed. Without the check, 3 of 8 fail (see Red proof).
- Full CI command: not run in this stage, per the user's speed note (it runs once locally in `raise-pr`).
- Outstanding: none.
- Contradicted the plan: the chip case's route. Down from the pill lands on the empty state's Show all albums, not a chip; the test goes Up to Shared with me from there, and task 1.3 now says so.

## Review

- Fixed: none. Checked: the first-focus check reads `pillHoldsFocus`/`screenHoldsFocus` live after the frame; the remembered-album path skips the question only when that album is in the list (Back from an album, a tab switch back); Try again and Refresh land with nothing focused, so the answer is yes; the refresh refocus branch is untouched. Every Done-when item and ticked task is in the diff, nothing out of scope is. `AlbumGridFocusTest` 8/8 and `PhotoGridFocusTest` 6/6 green on re-run.
- Raised: https://github.com/YasarMahomedAbbas/immich-tv/issues/44 (Home takes focus on arrival without asking, the same bug on the Home tab; out of scope here).
- API: no endpoint touched.
- Layers: clean (`ui/` only; no `data/` import, no Immich names).
