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
- Speed note: the user's note for today's run is in the checklist's Context.
- Open risks: (1) The TV window ends about 15:30 CET on 2026-10-30; the on-TV check is skipped and recorded as skipped if it is reached later. (2) The Home screen takes focus on arrival the same way, unasked. It is out of scope here, and no ticket for it was checked for. (3) The albums' first load is short on the real server, so the on-TV Right during the skeleton may be hard to land; the unit test is the proof either way.
