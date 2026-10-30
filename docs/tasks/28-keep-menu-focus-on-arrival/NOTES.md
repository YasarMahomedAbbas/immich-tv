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
- Decided (the user, on the issue, 2026-10-30, answering the park): option 1, one wider rule. The first tile takes first focus if the pill holds focus or nothing in the screen does. Add a Try again test, keep the rail case as a guard entered through the grid, and edit the checklist's rule, test list and Verify to match.
- TV window (the user, on the issue): until 15:30 CET on 2026-10-30, replacing 14:25.
- Open risks: the pill's focus answer could lag the grid's check when entering the tab with the month already loaded, which would leave the first tile unfocused with no key pressed; a test case and a TV path cover it. The load window on the TV is short, so the menu and rail cases may be hard to catch by hand. Robolectric may not run focus search or TV Material as the device does, hence the fallback. The Albums grid may have the same pattern: out of scope, unconfirmed.

## Implementation

- Shipped: the question is `pillHoldsFocus || !screenHoldsFocus` (`ui/main/MainScreen.kt:59`), from the pill's report (`ui/components/TopMenu.kt:77`) and the root's `hasFocus` (`ui/main/MainScreen.kt:50`); the grid asks it after a frame (`ui/photos/PhotoGrid.kt:98`). Manual line in `docs/design/README.md`. New `PhotoGridFocusTest`, with Robolectric and the Compose UI test library as test-only dependencies.
- Proved: "nothing holds focus" is reported in time. In a probed run of the Try again test the root reported `hasFocus=false` as the button went, and the grid's check at arrival read pill false, screen false; the first tile then took focus.
- Tests: `PhotoGridFocusTest` 6 passed / 0 failed, `ArchitectureTest` 1 passed / 0 failed. Only these classes ran, per the user's speed note; the full CI command runs once in `raise-pr`.
- Without the fix (grid's check removed): 5 passed / 1 failed. The Search case fails, which is the bug. The rail guard passes without the fix, as expected, so it is not proof of the bug: the rail is reached only through the grid, and a tile having had focus already blocks the first-focus move. This run replaces the earlier inference. Try again also passes here, since it only needs the widened rule rather than the check.
- With a pill-only rule: 5 passed / 1 failed, Try again, so that test holds the widening in place.
- Outstanding: none in the checklist. The on-TV paths are self-verification's, and the Try again path (g) may not be catchable there.
- Contradicted the plan: (A) a pill-only rule left no element focused after Try again; (B) the planned rail route (Left to another pill, then Down) can't reach the rail. Both resolved by the user's answer above. Also: a hand-stepped clock didn't pick up the test's own state writes in the Try again route, so `settle()` now calls `Snapshot.sendApplyNotifications()`. Not explained from the earlier run: going straight from Failed to Loaded with focus on the rail; that route isn't tested.
- Build note: Robolectric fetches its Android jars from Maven Central at test time (`android-all-instrumented` 14 and 15, though the test asks for SDK 34) into the container's home, which `build.sh` doesn't keep, so every local run downloads them again. Central answered 429 to some of today's requests from this machine, failing every case in `PhotoGridFocusTest` with "Failed to fetch maven artifact". For this stage the checksum-verified jars were mounted into the container by hand; no repo file changed for it.
- Seen, out of scope: the Albums grid's own first focus was not looked at.

## Review

- Fixed: (1) Robolectric fetched its two Android jars from Maven Central during the tests, outside any cache (`~/.m2` isn't kept by `build.sh`, nor cached by `setup-gradle` in CI or Release), so one 429 failed every `PhotoGridFocusTest` case, including on the Release workflow after a tag. Gradle now resolves them (`app/build.gradle.kts`, `syncRobolectricJars`) into its own cache and Robolectric runs with `robolectric.offline=true`. Each jar has its own configuration, since two versions of one module in one configuration resolve to the newer alone. (2) The manual's Top menu line and `MainScreen`'s KDoc said the rule held for any tab's content; only Photos asks. Both now say so.
- Raised: https://github.com/YasarMahomedAbbas/immich-tv/issues/33 (the Albums grid takes focus on arrival with no check; read in the code, not reproduced on the TV).
- API: no endpoint touched.
- Layers: clean.
- Tests: `PhotoGridFocusTest` 6 passed, `ArchitectureTest` 1 passed, per the user's speed note. Offline mode was proved by the failures without each jar ("Path is not a file", no download attempted).
- Not proved: Gradle's own download of the SDK 34 jar. Central kept answering 429 for it from this machine (all 11 attempts through `build.sh`). The SDK 35 jar did resolve without the seed, but Gradle may have reused the seeded copy by checksum, so that isn't proof of a download either. The passing run took that jar from the checksum-verified local copy, through an init script outside the repo. Until Gradle has fetched it once, `./build.sh` needs Central to answer. A 429 on the first fetch still fails a run, now in `syncRobolectricJars` with the URL named; a cached run makes no such request, and `setup-gradle` only writes its cache from the default branch, so PR runs before the merge fetch both jars.
- Not run: the full CI command, so `koverVerifyDebug` and the whole suite alongside Robolectric are unproved until `raise-pr`.
