# Checklist — bug: Home arriving pulls focus off the top menu

Issue: #44 — bug: Home arriving pulls focus off the top menu (https://github.com/YasarMahomedAbbas/immich-tv/issues/44)
Branch: `fix/44-keep-home-menu-focus`
Problem: on the Home tab, a remote that has moved along the top menu while Home loads has focus pulled to the hero (or the remembered item) when the page arrives, so an OK meant for the menu acts on Home.

## Done when

- Focus moved off the Home pill during the first load stays where it is when the page arrives.
- With no key pressed, the target still takes focus on arrival.
- A test covers this and fails without the fix.

## References

- Immich API: none; no call is added or changed.
- #33 (PR #45) and #28 (PR #37): `mayTakeFocus` in `MainScreen`, `mayTakeFirstFocus` in `AlbumGrid`/`PhotoGrid`, and `AlbumGridFocusTest`, the harness to mirror.
- Manual: `docs/design/README.md`, the Top menu line.

## Context

`MainScreen` answers `mayTakeFocus` (yes while the selected pill holds focus or nothing on the screen does) and hands it to the Albums and Photos tabs, not Home. `HomeLoaded` takes focus twice without asking: its `LaunchedEffect(Unit)` requests `targetFocus` one frame after the page is first composed, and its `LaunchedEffect(lost)` (the "refresh takes away what the remote was on" path) also fires on that first composition whenever the remembered target isn't on the page, which on a cold start is any page without a hero (the default remembered target is Play). The fix hands Home the question, asks it after the first frame, and keeps the `lost` path off the first composition so the first arrival is decided in one place. Unlike `AlbumGrid`, Home always asks: it cannot tell a cold first load from coming Back, because `HomeState.focused` defaults to Play, and coming Back needs no exemption anyway (MainScreen is composed fresh and its pill takes focus, so the answer is yes). The `lost` refocus on later refreshes is unchanged. Decisions are in `NOTES.md`.

## Test plan

- Unit: `HomeFocusTest` (Robolectric, real keys through `MainScreen` and `HomeContent`), mirroring `AlbumGridFocusTest`: no key pressed, Play takes focus on arrival; Right during the skeleton, the Albums pill keeps focus; Right then Left back to the Home pill during the skeleton, Play takes focus (the pill holds focus again, so the answer is yes); a page without a hero (the `lost` path on first arrival), Right during the skeleton, the Albums pill keeps focus, and with no key pressed the first recent photo takes focus; Try again on a failed load, Play takes focus on arrival; entering the tab with the page loaded, Play takes focus; switching to Home from the Albums tab with the page loaded, Play takes focus; a remembered recent photo on a loaded page (as on coming Back), that photo takes focus. `AlbumGridFocusTest` and `PhotoGridFocusTest` are re-run because `MainScreen` changes.
- MockWebServer: none; no call changes.
- Real server: none; no call changes, so there is nothing to check.
- On the TV: only what this ticket changed, holding the lock (`mkdir /tmp/immich-tv-adb.lock`, a successful mkdir only; `rmdir` when done, on failure too) for 10 minutes at most, and releasing it before fixing anything. Drive: cold open on Home with no key pressed (Play takes focus); cold open with Right straight after launch, during the skeleton (focus stays on the Albums pill when the page arrives); open an album from the Home row and press Back (focus returns to that card); go to Albums and select Home again (the remembered item takes focus).

---

## Checkpoint 1 — Ask before Home takes focus · commit: `fix(home): keep focus on the menu when Home arrives`

Goal: a remote moved during Home's first load stays where it is, with the test that proves it.

### [ ] 1.1 — Hand the question to the Home tab

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/main/MainScreen.kt`
Change the slot to `home: @Composable (mayTakeFocus: () -> Boolean) -> Unit` and call `Tab.Home -> home(mayTakeFocus)`. Reword the KDoc: focus enters on the selected pill, and every tab whose content takes focus on arrival (Home, Albums, Photos) is handed `mayTakeFocus` to ask first; drop "(Home does, once it is laid out)". The existing `home = {}` callers (previews, `AlbumGridFocusTest`, `PhotoGridFocusTest`) compile unchanged.

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`
`home = { mayTakeFocus -> ... HomeScreen(..., mayTakeFirstFocus = mayTakeFocus) }`.

### [ ] 1.2 — Gate first focus on the page

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/home/HomeScreen.kt`
- `HomeScreen` gains `mayTakeFirstFocus: () -> Boolean` and passes it to `HomeContent`.
- `HomeContent` becomes `internal` and gains `mayTakeFirstFocus: () -> Boolean`, passed to `HomeLoaded`. `HomeActions` becomes `internal` so the test can build one. `HomePreview` passes `{ true }`.
- `HomeLoaded` gains `mayTakeFirstFocus: () -> Boolean`. Add `var arrived by remember { mutableStateOf(false) }` (imports `mutableStateOf`, `setValue`). The first `LaunchedEffect(Unit)` becomes: `withFrameNanos { }`, then `if (mayTakeFirstFocus()) runCatching { targetFocus.requestFocus() }`, then `arrived = true`, with a one-line comment: asked after the frame so the answer is current; coming Back asks too, and the pill holds focus then.
- The `LaunchedEffect(lost)` returns early also when `!arrived`: `if (!lost || !arrived) return@LaunchedEffect`, with a one-line comment that the first arrival is the effect above's to decide. Nothing else in it changes.
- Rewrite the KDoc: focus goes to [target] once the page is laid out, as the Albums and Photos grids' does, only while the top menu's pill still holds focus or nothing does; again, without asking, if a later refresh takes away what the remote was on. Keep the sentence on blocks being brought into view.

### [ ] 1.3 — The test

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/home/HomeFocusTest.kt`
Mirror `AlbumGridFocusTest`: the same runner, `@GraphicsMode`, `@Config`, hand-stepped clock and `settle()`/`press()` helpers (the skeleton's pulse never lets the clock go idle). `open(initial: HomeState, startTab: Tab = Tab.Home)` puts `HomeContent` in `MainScreen(tab, ...)`, passing `mayTakeFocus`, with `onFocused` recording each `HomeFocus` into a list, `onRetry` setting `HomeLoad.Loading`, and `HomeActions` of no-ops; the `albums` slot is `{}` and the tab is held in test state. Pages: `heroPage = HomePage(HomeHero.FromMemory(memory, yearsAgo = 1), albums = emptyList(), recent = listOf(r0, r1))` and `noHeroPage = HomePage(null, emptyList(), listOf(r0, r1))`. Keep the albums row empty in every page: its "Albums" title would collide with the Albums pill's label. Assert menu focus with `assertIsFocused()` on the pill label (`Home`, `Albums`) and Home focus with the recorded list (`HomeFocus.Play`, `HomeFocus.Recent(r0.id)`). The cases are those in the Test plan; for the remembered photo, `open(HomeState(Loaded(heroPage), focused = HomeFocus.Recent(r1.id)))` and expect `[Recent(r1.id)]`; for the switch, start on `Tab.Albums`, Left to Home, OK.

Prove it red: with the `mayTakeFirstFocus()` check taken out (and the `!arrived` guard taken out, separately), the Right cases must fail: the hero page's for the first, the no-hero page's for the second. Record both in NOTES, then put them back.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*HomeFocusTest' --tests '*AlbumGridFocusTest' --tests '*PhotoGridFocusTest' --tests '*HomeViewModelTest'`
- All cases are green with the fix, and the Right cases fail without each guard.

---

## Checkpoint 2 — The manual says so · commit: `docs(home): widen the first-focus rule to Home`

Goal: the docs the ticket names match the code.

### [ ] 2.1 — Manual and README

File: `docs/design/README.md`
In the Top menu line, "The Albums and Photos grids take focus from there on arrival only while..." becomes "Home and the Albums and Photos grids take focus from there on arrival only while...".

File: `README.md`
The testing line becomes: "`HomeFocusTest`, `PhotoGridFocusTest` and `AlbumGridFocusTest` press real keys through the top menu and the Home page and the Photos and Albums grids under Robolectric."

Confirm the KDocs reworded in 1.1 and 1.2 (`MainScreen`, `HomeLoaded`) no longer say Home takes focus unasked; widen them in this commit if they still do.

### Verify

- `rg -n 'Home and the Albums and Photos grids' docs/design/README.md` and `rg -n 'HomeFocusTest' README.md` each find the line.
- `rg -n 'unasked|without asking whether' app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui` finds nothing about Home's first focus.

---

## Out of scope

- The `lost` refocus after a later refresh (it still moves focus without asking).
- Any change to `HomeViewModel`, `HomeState` or `focusTarget`, including making the remembered target nullable.
- The Albums and Photos tabs' behaviour.
