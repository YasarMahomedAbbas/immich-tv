# Notes — bug: the first month arriving pulls focus off the top menu on the Photos tab

Issue: #28 — bug: the first month arriving pulls focus off the top menu on the Photos tab (https://github.com/YasarMahomedAbbas/immich-tv/issues/28)
Branch: `fix/28-keep-menu-focus-on-arrival`

## Planning

- Approach: `MainScreen` tracks whether the selected pill holds focus and hands the grid a question; the first arrival moves focus to the first tile only if the pill still holds it.
- Decided (pipeline, on the user's behalf; flag: scope): option 3 of the test question. Add Robolectric and the Compose UI test library as test-only dependencies and write a key-driven test through the real menu and grid, falling back to a plain decision function with a unit test if a key-driven focus move can't be made to work within the first checkpoint, recording the fallback here. Rejected: Robolectric with no fallback (option 1), and the plain function alone (option 2).
- Decided: the grid's check defaults to "may take focus", so the album's photo screen is unchanged.
- Decided: restoring a tile on return from a full-screen photo skips the check.
- Decided: moving away and back onto the pill before the month arrives still hands focus to the first tile, because focus is again where the app put it.
- Speed note from the user for today's run (2026-10-30, issue comment): only the changed classes' tests while implementing; the full CI command once locally, in `raise-pr`; the on-TV check limited to this ticket's paths, with the TV lock held 10 minutes at most and released before any fix. A failing-without-the-fix test, the on-TV check and green CI all still stand.
- TV note (issue comment): pipelines may use the TV until about 14:25 CET on 2026-10-30, then skip and record it as skipped. One pipeline at a time, through the `mkdir /tmp/immich-tv-adb.lock` lock.
- Open risks: the pill's focus answer could lag the grid's check when entering the tab with the month already loaded, which would leave the first tile unfocused with no key pressed; a test case and a TV path cover it. The load window on the TV is short, so the menu and rail cases may be hard to catch by hand. Robolectric may not run focus search or TV Material as the device does, hence the fallback. The Albums grid may have the same pattern: out of scope, unconfirmed.
