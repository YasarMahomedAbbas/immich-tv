# Checklist — bug: the albums arriving pull focus off the top menu on the Albums tab

Issue: #33 — bug: the albums arriving pull focus off the top menu on the Albums tab (https://github.com/YasarMahomedAbbas/immich-tv/issues/33)
Branch: `fix/33-keep-albums-menu-focus`
Problem: on the Albums tab, a remote that has moved along the top menu while the albums load has focus pulled to the first card when they arrive, so a Right and OK meant for the menu can open an album.

## Done when

- Focus moved off the Albums pill during the first load stays where it is when the cards arrive.
- With no key pressed, the target card still takes focus on arrival.
- A test covers this and fails without the fix.

## References

- Immich API: none; no call is added or changed.
- #28 (PR #37): `mayTakeFocus` in `MainScreen`, `mayTakeFirstFocus` in `PhotoGrid`, and `PhotoGridFocusTest`, the harness to mirror.
- Manual: `docs/design/README.md`, the Top menu line.

## Context

`MainScreen` already answers `mayTakeFocus` (yes while the selected pill holds focus or nothing on the screen does) but hands it only to the Photos tab. `AlbumGrid`'s `LaunchedEffect(Unit)` requests focus on its target card after one frame without asking, and it is composed fresh when the skeleton gives way to the cards. The fix hands the same question to the Albums tab and asks it after that frame, so the answer is current. Coming back to a remembered album doesn't ask, as `PhotoGrid` does for a tile being restored: restoring means `focused` is in the list. Try again and Refresh need nothing extra: their button has gone, so nothing holds focus and the answer is yes. One small change follows: a chip that brings the grid back when no remembered album is in the list keeps its focus, because it now counts as a remote gone elsewhere. The refresh branch that refocuses when the focused album disappears is unchanged. Decisions are in `NOTES.md`.

Speed note from the user for this run (issue comment, 2026-10-30): while implementing and fixing, run only the unit tests for the classes changed, not the full command at each checkpoint. The full CI command runs once locally, in `raise-pr`; self-verification doesn't need to run it first and shouldn't re-run it after a small fix. GitHub CI still has to be green before merging. Keep the on-TV check to what this ticket changed: its main path and its edge cases, not unrelated screens. These stay the same: a bug fix comes with a test that fails without it, the on-TV check still runs, API calls are still checked against the real server, and CI must pass.

## Test plan

- Unit: `AlbumGridFocusTest` (Robolectric, real keys through `MainScreen` and `AlbumsContent`), mirroring `PhotoGridFocusTest`: no key pressed, the first card takes focus on arrival; Right during the skeleton, Photos keeps focus; Left during the skeleton, Home keeps focus; Try again on a failed load, the first card takes focus on arrival; entering the tab with albums loaded, the first card takes focus; switching to the tab from another with albums loaded, the first card takes focus; a remembered album takes focus without asking; a chip that brings the grid back with no remembered album keeps focus. `PhotoGridFocusTest` is re-run because `MainScreen` changes.
- MockWebServer: none; no call changes.
- Real server: none; no call changes, so there is nothing to check.
- On the TV: only what this ticket changed, holding the lock (`mkdir /tmp/immich-tv-adb.lock`, a successful mkdir only; `rmdir` when done, on failure too) for 10 minutes at most, and releasing it before fixing anything. Allowed until about 15:30 CET on 2026-10-30; after that, skip it and record it as skipped. Drive: cold open of the Albums tab with no key pressed (first card takes focus); cold open with Right during the skeleton (focus stays on the menu when the cards arrive; send keys straight after opening, as the load is short); Back from an album (focus returns to that album); leaving for Photos and selecting Albums again (a card takes focus).

---

## Checkpoint 1 — Ask before the first card takes focus · commit: `fix(albums): keep focus on the menu when the albums arrive`

Goal: a remote moved during the first load stays where it is, with the test that proves it.

### [ ] 1.1 — Hand the question to the Albums tab

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/main/MainScreen.kt`
Change the slot to `albums: @Composable (mayTakeFocus: () -> Boolean) -> Unit`. Hold the answer once, `val mayTakeFocus = { pillHoldsFocus || !screenHoldsFocus }`, and pass it in both branches: `Tab.Albums -> albums(mayTakeFocus)`, `Tab.Photos -> photos(mayTakeFocus)`. Reword the KDoc so it names both the Albums and Photos tabs as asking first; Home still moves focus without asking. The existing `albums = {}` callers compile unchanged.

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`
`albums = { mayTakeFocus -> AlbumsScreen(..., mayTakeFirstFocus = mayTakeFocus) }`.

### [ ] 1.2 — Gate first focus in the grid

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/albums/AlbumsScreen.kt`
- `AlbumsScreen` gains `mayTakeFirstFocus: () -> Boolean` and passes it on.
- `AlbumsContent` becomes `internal` and gains `mayTakeFirstFocus: () -> Boolean`, passed to `AlbumGrid`. The preview passes `{ true }` and its `albums` slot takes the new parameter.
- `AlbumGrid` gains the same parameter. Its first `LaunchedEffect(Unit)` becomes: `withFrameNanos { }`, then `if (albums.any { it.id == focused } || mayTakeFirstFocus()) runCatching { targetFocus.requestFocus() }`, with a one-line comment that it asks after the frame so the answer is current, and that coming back to an album doesn't ask.
- Add to the KDoc that a remote already gone elsewhere stays there. Leave the refresh effect below as it is.

### [ ] 1.3 — The test

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/albums/AlbumGridFocusTest.kt`
Mirror `PhotoGridFocusTest`: the same runner, `@GraphicsMode`, `@Config`, hand-stepped clock and `settle()`/`press()` helpers (the skeleton's pulse never lets the clock go idle). `open(initial)` puts `AlbumsContent` in `MainScreen(Tab.Albums, ...)`, passing `mayTakeFocus`, with `onFocused` recording into a list and `onRetry` setting `AlbumsLoad.Loading`. The cases are those in the Test plan. Use the pill and chip labels as they are in `strings.xml`; assert focus with `assertIsFocused()` on the label for menu items and chips, and with the recorded `onFocused` list for cards. For the tab-switch case, hold `tab` in test state. For the chip case, route from the pill into the toolbar with keys and assert each stop rather than assume it, with `onFilter` updating the state.

Prove it red: with the `mayTakeFirstFocus()` check taken out, the Right and Left cases must fail. Record that in NOTES, then put the check back.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AlbumGridFocusTest' --tests '*PhotoGridFocusTest'`
- All cases are green with the fix, and the Right and Left cases fail without it.

---

## Checkpoint 2 — The manual says so · commit: `docs(albums): widen the first-focus rule to the Albums grid`

Goal: the docs the ticket names match the code.

### [ ] 2.1 — Manual and README

File: `docs/design/README.md`
In the Top menu line, "The Photos grid takes focus from there on arrival only while..." becomes "The Albums and Photos grids take focus from there on arrival only while...".

File: `README.md`
The testing line that names `PhotoGridFocusTest` also names `AlbumGridFocusTest`, for the Albums grid.

### Verify

- `rg -n 'Albums and Photos grids' docs/design/README.md` finds the line, and `rg -n 'AlbumGridFocusTest' README.md` finds the other.

---

## Out of scope

- The Home screen, which also takes focus on arrival without asking. It is not this ticket; self-review or doc-drift may note it as a follow-up.
- The refresh branch in `AlbumGrid` that refocuses when the focused album disappears.
- The Photos tab's behaviour.
- Any change to `AlbumsViewModel`.
