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

## Doc drift

- Applied: `ui/home/HomeScreen.kt` `HomeLoaded` KDoc — said Home's first focus beats the top menu "as the Albums grid's does"; the Albums grid now asks `mayTakeFocus` first, so it now says Home takes focus unasked, unlike the Albums and Photos grids (rung 1: a session reading it would wrongly assume Home and Albums behave alike; Home's fix is #44).
- Skipped: `MainScreen` KDoc, the manual's Top menu line (`docs/design/README.md`) and the README testing line — already updated on this branch in implementation and checked true against the code (`MainScreen.kt:52`, `:72`; `AlbumGridFocusTest` exists).
- Skipped: a CLAUDE.md rule for "a tab's first focus asks `mayTakeFocus`" — the manual's Top menu line states it where a screen builder reads, and the focus tests enforce it for both grids.
- Skipped: README architecture block and use-case table — no file added or removed under `app/src/main` (only `AlbumGridFocusTest` under `app/src/test`); no commands, server support or release steps touched.
- Stale, fixed separately: none.

## Self verification

Result: partial

- CI build: full command not run here, per the user's speed note (raise-pr runs it once). Ran the unit tests for the changed classes instead: `AlbumGridFocusTest` 8 passed / 0 failed, `PhotoGridFocusTest` 6 passed / 0 failed. `assembleDebug` built the debug APK.
- Server: not run (UI-only ticket: no Immich call is added or changed, so there is nothing to check against the spec or the server).
- TV: not run (the Chromecast answered ping on 2026-11-21 but refused adb on port 5555, `Connection refused`, on every try from 10:29 to 10:40 CET; network debugging looks switched off or adbd is not listening). The lock was never taken because nothing was installed.
- Verified: first focus asks `mayTakeFocus` (Right and Left during the skeleton keep focus on the menu; no key pressed hands the first card focus; Try again, entering and switching to a loaded tab hand the card focus; a remembered album takes focus without asking; a chip with no remembered album keeps focus), all through real keys in `AlbumGridFocusTest`.
- Failed: none.
- Not self-verifiable: on the TV, by hand: (1) open the Albums tab cold with no key pressed, and the first card takes focus when the albums arrive; (2) open it cold and press Right straight after OK on the Albums pill, and focus stays on Photos when the cards arrive; (3) open an album and press Back, and focus returns to that album; (4) go to Photos and select Albums again, and a card takes focus.
