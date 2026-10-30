# Checklist — bug: the first month arriving pulls focus off the top menu on the Photos tab

Issue: #28 — bug: the first month arriving pulls focus off the top menu on the Photos tab (https://github.com/YasarMahomedAbbas/immich-tv/issues/28)
Branch: `fix/28-keep-menu-focus-on-arrival`
Problem: someone moving along the top menu (or onto the month rail) while Photos loads for the first time is moved onto the first tile when the first month arrives, without pressing anything.

## Done when

- Focus moved anywhere off the Photos pill during the first load (the menu or the rail) stays where it is when the first month arrives.
- With no key pressed, the first tile still takes focus on arrival.
- A test covers this and fails without the fix.

## References

- Immich API: none. This ticket adds and changes no call.
- `docs/design/README.md`: Top menu ("focus enters a main screen on the selected pill"), Photos, rule 7. No visual change.
- #7 (`docs/tasks/7-browse-photos-by-month/NOTES.md`): the first-focus guard this extends, and the rail case left as is.

## Context

`MainScreen` puts focus on the selected pill. `PhotoGrid`'s first-arrival effect then calls `focusFirst()` unless the grid has already had focus (`focusedOnce`, `lastFocused`), so a remote elsewhere outside the grid counts as "hasn't moved". The fix: `MainScreen` knows whether the selected pill holds focus and passes that down as a question the grid asks at arrival. The grid takes first focus only if the answer is yes. One rule covers the menu, the gear and the rail. The issue's cause is confirmed in the code. Decisions are in NOTES. Compose focus code has had no automated test until now; this ticket adds Robolectric and the Compose UI test library so the test presses real keys, with a bounded fallback (1.3). The analogue for the fallback is `lostFocus` and `RefreshOnStartTest`.

## Test plan

Speed note from the user for this run (issue comment, 2026-10-30): while implementing and fixing, run only the unit tests for the classes changed, not the full command at each checkpoint. The full CI command runs once locally, in `raise-pr`; self-verification doesn't need to run it first and shouldn't re-run it after a small fix. GitHub CI must still be green before merging.

- Unit: `PhotoGridFocusTest` (Robolectric, real keys through `MainScreen` and `PhotosContent`): no key pressed, the first tile takes focus on arrival; Right during the skeleton, Search keeps focus; on the rail during the first month's load, the rail keeps focus; on a tile of the loading month, the tile keeps its place; entering the tab with the first month already loaded, the first tile takes focus (guards against the pill's answer arriving late). Fallback only if 1.3 triggers: the decision function, in the same cases.
- MockWebServer: none, no API change.
- Real server: none, no API change.
- On the TV: only what this ticket changed, holding the lock (`mkdir /tmp/immich-tv-adb.lock`, a successful mkdir only; `rmdir` when done, on failure too) for 10 minutes at most, and releasing it before fixing anything. Allowed until about 14:25 CET on 2026-10-30; after that, skip and record as skipped. Cold start to Home, Right twice and OK on Photos: (a) press nothing, the first tile takes focus; (b) Right during the skeleton, focus stays on Search; (c) onto the rail during the first load, focus stays on the rail; (d) Down onto a tile of the loading month, the tile keeps its place; (e) open a photo and Back, the tile last viewed is focused; (f) another tab and back to Photos, the first tile takes focus as it did before. Cold starts make the load window short, so (b) and (c) may need several tries; record what was actually caught.

---

## Checkpoint 1 — Compose focus tests that press real keys · commit: `test(photos): reproduce the first month taking focus off the menu`

Goal: the bug is reproduced by a test that presses keys. The test is red for the bug's cases and the commit is held until checkpoint 2 makes it green (see Verify).

### [ ] 1.1 — Test dependencies

File: `gradle/libs.versions.toml`
Add `robolectric = "4.14.1"` and libraries `robolectric`, `compose-ui-test-junit4` (`androidx.compose.ui:ui-test-junit4`, from the BOM), `compose-ui-test-manifest` (`androidx.compose.ui:ui-test-manifest`, from the BOM).

File: `app/build.gradle.kts`
`testImplementation(platform(libs.compose.bom))`, `testImplementation(libs.robolectric)`, `testImplementation(libs.compose.ui.test.junit4)`, `debugImplementation(libs.compose.ui.test.manifest)`. Under `android`, `testOptions { unitTests { isIncludeAndroidResources = true } }`. Nothing reaches the release APK.

### [ ] 1.2 — The test

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/photos/PhotoGridFocusTest.kt` (new)
`@RunWith(RobolectricTestRunner::class)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")` (SDK 35 under Robolectric wants a newer JDK than the build may have). Make `PhotosContent` in `PhotosScreen.kt` `internal` so the test can drive it from a `MutableState<PhotoGridState>`, inside `ImmichTvTheme { MainScreen(Tab.Photos, {}, {}, albums = {}, photos = { PhotosContent(...) }) }`. Assert focus by `onNodeWithText(...).assertIsFocused()` for pills and rail months, and by what `onMonthFocused` received for tiles. The five cases are in the test plan. Reach the rail by keys (Left to another pill, then Down), and assert it got there before the month arrives.

Set `mainClock.autoAdvance = false` and step with `advanceTimeBy`: the skeleton's infinite pulse never lets `waitForIdle` finish otherwise.

### [ ] 1.3 — The time limit and the fallback

If a key-driven focus move can't be made to work here within this checkpoint, stop trying. Take the Robolectric dependencies and the test out and use the fallback: in `PhotoGrid.kt`, `internal fun takesFirstFocus(firstReady: Boolean, alreadyFocused: Boolean, pillHoldsFocus: Boolean): Boolean`, unit-tested in `MonthTilesTest.kt`, with the wiring proved only on the TV. Record the fallback and what failed in NOTES, and fix this checklist. It isn't a question for anyone: the pipeline already chose it.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PhotoGridFocusTest'`
- The two "stays where it is" cases fail with focus on the first tile; the other three pass. Knowingly red work isn't committed, so note the failures in NOTES and commit this checkpoint together with checkpoint 2, using checkpoint 2's message.

---

## Checkpoint 2 — The grid leaves focus where the remote put it · commit: `fix(photos): keep focus on the menu when the first month arrives`

Goal: the test is green, and the Photos tab behaves as the ticket's "Done when" says.

### [ ] 2.1 — The menu reports the pill

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/components/TopMenu.kt`
New parameter `onSelectedFocusChanged: (Boolean) -> Unit = {}`. The selected pill's modifier becomes `Modifier.focusRequester(selectedFocus).onFocusChanged { onSelectedFocusChanged(it.isFocused) }`.

### [ ] 2.2 — `MainScreen` passes the question down

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/main/MainScreen.kt`
`var pillHoldsFocus by remember { mutableStateOf(false) }`, set from `onSelectedFocusChanged`. `photos: @Composable () -> Unit` becomes `photos: @Composable (mayTakeFocus: () -> Boolean) -> Unit`, called as `photos { pillHoldsFocus }`. Update the KDoc: a tab's content may move focus only while the pill still holds it. The previews' `photos = {}` still compile.

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`
`photos = { mayTakeFocus -> PhotosScreen(..., mayTakeFirstFocus = mayTakeFocus) }`.

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/photos/PhotosScreen.kt`
`mayTakeFirstFocus: () -> Boolean` on `PhotosScreen` and `PhotosContent`, passed to `PhotoGrid`. Fix the comment on `GridLoad.Loading` if it no longer holds. The preview's call passes `{ true }`.

### [ ] 2.3 — The grid asks

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/photos/PhotoGrid.kt`
New parameter `mayTakeFirstFocus: () -> Boolean = { true }`, so `AlbumPhotosScreen` is unchanged. In the first-arrival `LaunchedEffect(firstReady)`, after `withFrameNanos { }` (so the pill's answer is current): restoring requests `restoreFocus` as today, without asking; otherwise `focusedOnce = mayTakeFirstFocus() && focusFirst()`. A refusal leaves `focusedOnce` false, so nothing else changes. Update the KDoc's "Focus goes to the first tile once it is there". The `lostFocus` and `jumpTo` effects are untouched: both follow from something the remote or a refresh did to the grid itself.

### [ ] 2.4 — The manual

File: `docs/design/README.md`
Top menu entry, after "focus enters a main screen on the selected pill": a tab's content may take focus from there on arrival, but only while the pill still holds it.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PhotoGridFocusTest'` (and `--tests '*MonthTilesTest'` if the fallback was taken)
- All five cases pass.

---

## Out of scope

- The Albums grid's own first focus, and any other screen's focus on arrival, even if the same pattern shows there. Record it in NOTES if seen; it gets its own ticket.
- Changing how the album's photo screen holds focus while loading.
- Converting other screens to Robolectric tests, or counting Compose code in coverage.
- Any visual change.
