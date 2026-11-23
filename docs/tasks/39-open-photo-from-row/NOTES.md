# Notes — feat: open a photo from the album screen's preview row

Issue: #39 — feat: open a photo from the album screen's preview row (https://github.com/YasarMahomedAbbas/immich-tv/issues/39)
Branch: `feat/39-open-photo-from-row`

## Planning

- Approach: OK on a row tile opens a new `AppScreen.AlbumRowViewer`. That screen sets the photo as the one being viewed on the album's existing grid ViewModel, the way Home's Recently added does, and shows `PhotoViewerScreen`, so left/right move through the whole album in its order. Back returns to the album screen with the tile to focus. The change is UI and navigation only, with no API change.
- Decided (the pipeline, on the user's behalf; flag: ux): option 1 of 3. Back lands on the tile of the photo on screen when that photo is in the row (scrolled into view), otherwise on the tile that was opened. This matches the design manual's Photo rule ("Back returns to the last viewed tile of its grid"). The alternatives were always the opened tile (option 2) or the row's last tile when past the row (option 3).
- Decided: a new `AppScreen` variant, not a flag on `AlbumPhotoViewer`, so the grid viewer's Back path stays as it is. The viewer's branch has its own `BackHandler`, because only the grid ViewModel knows the photo on screen. `AppScreen.Album` gains a `fallbackTile` beside `focusTile`.
- Decided: a video tile opens as a still with the play badge, as it does from the grid. Hold-OK to start a slideshow from the row is out of scope. Browse photos and Down into the grid still start at the top.
- Open risks: a viewer opened before the album grid's months have loaded drops the first left/right press, as Home's Recently added viewer does. The grid ViewModel's `open` is called from `remember` during composition, so that `viewing` is set before the viewer's first frame.

## Implementation

- Shipped: `AppScreen.AlbumRowViewer`, `openAlbumRowViewer` and `backFromRowViewer`, plus `Album.fallbackTile` (`ui/AppViewModel.kt:57-87`, `:191-210`, and the `back()` branch). The album screen focuses the first of `focusTile`/`fallbackTile` that is in the row (`ui/album/AlbumScreen.kt:238`), and OK on a strip tile calls `onOpenFromRow` (`AlbumScreen.kt:395`). The `AlbumRowViewer` branch in `MainActivity.kt:178` opens the photo on the album's grid ViewModel, refreshes it, and has its own `BackHandler`. The design manual's Album and Photo rows are updated.
- Tests: 558 passed / 0 failed (full CI command: testDebugUnitTest koverVerifyDebug lintDebug assembleDebug, BUILD SUCCESSFUL). New: 4 in `AppViewModelTest`, 6 in `AlbumFocusTest`.
- Outstanding: none in the checklist. The TV walk-through in the test plan is left to self-verification.
- Contradicted the plan: the plan set the photo on the grid with `remember(current) { grid.open(...); grid.refresh() }` during composition. Lint's RememberReturnType rejects a `remember` that returns Unit, so the branch now does this in a `LaunchedEffect(current)` and shows `PhotoViewerScreen` only once the photo is set, with a `bg` box for that first frame. Back during that frame ignores the grid's stale `viewing` and lands on the opened tile. Checklist 1.3 has been updated to match.

## Review

- Fixed: a photo opened from the row on an album whose grid hadn't been opened yet this session bounced straight back to the album screen. The `AlbumRowViewer` branch creates the album grid's ViewModel, `open()` sets `viewing`, and when the months arrive `loadMonths()` replaced the whole state, `viewing` with it; `PhotoViewerScreen` then saw no photo and called `onBack`. `loadMonths()` now keeps `viewing` (`ui/photos/PhotoGridViewModel.kt`), with a test that fails without it (`PhotoGridViewModelTest`: a photo opened before the months arrive stays open). Full CI: 559 passed / 0 failed.
- Raised: none
- API: none touched (the existing album timeline calls only).
- Layers: clean

## Doc drift

- Applied: none on this stage. `docs/design/README.md` (Album and Photo rows) was already updated in implementation to say OK on a strip tile opens it full-screen and where Back lands; checked against `AlbumScreen.kt` and `AppViewModel.backFromRowViewer`, it holds.
- Skipped: README's Albums feature line doesn't name opening a photo from the album screen's strip. It stays true (it lists ways in, not all of them) and no session would do the wrong thing without it.
- Skipped: `PhotoGridViewModel.loadMonths()` now keeps `viewing`. The code and `PhotoGridViewModelTest` say it; not a cross-cutting rule.
- Skipped: README architecture block. The branch adds no source files (only modifies existing ones), so the tree it lists is unchanged; no commands, server reads, permissions or release steps changed.
- Stale, fixed separately: none
