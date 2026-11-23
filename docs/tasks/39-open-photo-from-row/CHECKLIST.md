# Checklist — feat: open a photo from the album screen's preview row

Issue: #39 — feat: open a photo from the album screen's preview row (https://github.com/YasarMahomedAbbas/immich-tv/issues/39)
Branch: `feat/39-open-photo-from-row`
Problem: on an album's screen the "In this album" row's tiles take focus but OK on one does nothing, so a photo seen there can't be opened.

## Done when

- OK on a tile in the row opens that photo full-screen.
- Left/right move through the album (the whole album, in its own order, stopping at both ends).
- Back returns to the row: to the tile of the photo on screen when that photo is in the row (scrolled into view), otherwise to the tile that was opened.

## References

- Immich API: none new. The viewer moves through the album with the album grid's existing `Timeline` (`container.albumTimeline(album)`) and loads `fullScreenRequest` previews as the grid's viewer does.
- #23 (open a photo from the album grid), #46 (Down from the row into the grid; `fromTile` / `focusTile`), design manual rows Album and Photo in `docs/design/README.md`.

## Context

The row is `Strip` in `ui/album/AlbumScreen.kt`; its `PhotoTile`s are `focusable = true` with no `onClick`. The grid's viewer (`PhotoViewerScreen`) shows `PhotoGridViewModel.state.viewing` and moves with `viewerMove`; Home's Recently added already opens a photo from outside its grid by calling `open(photo)` and `refresh()` on the grid's ViewModel, then showing the viewer (`step()` fetches months until it finds the photo). This mirrors that with the album's grid ViewModel (`albumGrid(container, connection, album)` in `MainActivity.kt`). The row is the album's first items in the album's order, the same order as the album grid's timeline, so left/right continue naturally. A new `AppScreen.AlbumRowViewer` keeps the grid viewer's Back path (to the grid) untouched. Back has to know the photo on screen, which only the grid ViewModel knows, so the viewer's branch installs its own `BackHandler` that hands it to `AppViewModel.backFromRowViewer`; the plain `back()` (only reached if that handler is absent) falls back to the opened tile. The album screen gets a `fallbackTile` beside `focusTile`: the first of the two that is in the row takes focus, else Start slideshow as today. A video tile opens as a still with the play badge, as from the grid.

## Test plan

- Unit (`AppViewModelTest`): `openAlbumRowViewer` sets `AlbumRowViewer`; `backFromRowViewer(viewed)` goes to `Album(focusTile = viewed, fallbackTile = opened, cameFrom)` for `AlbumsTab` and `HomeRow`; `backFromRowViewer(null)` and plain `back()` go to `Album(focusTile = opened, cameFrom)`; `backFromRowViewer` off that screen does nothing.
- Robolectric focus (`AlbumFocusTest`): OK on a row tile calls `onOpenFromRow` with that tile's `Photo` (fails today: no `onClick`), including a video tile and a far tile (index 20); arriving with a `focusTile` not in the row and a `fallbackTile` in it focuses the fallback; arriving with neither in the row lands on Start slideshow; `focusTile` in the row wins over `fallbackTile`.
- MockWebServer: none (no new call).
- Real server: none new; self-verification's TV run exercises the existing album timeline calls.
- On the TV: open an album, Down to the row, Right to the third tile, OK: that photo full-screen. Right twice and Left once: moves through the album. Back: focus on the tile of the photo shown (the fourth), scrolled into view. Then OK on the row's last tile, Right past the row's end, Back: focus on the last tile (the opened one). OK on a video tile: a still with the play badge. Back from the album screen still goes to Albums (or Home when opened from Home's row).

---

## Checkpoint 1 — The row viewer's navigation · commit: `feat(albums): a viewer screen for photos opened from the album row`

Goal: the app can show an album's photo opened from its row, and Back from it returns to the album screen with the viewed and opened tiles; the album screen focuses the first of them that is in its row.

### [x] 1.1 — `AppScreen.AlbumRowViewer` and its navigation

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/AppViewModel.kt`
- Add `val fallbackTile: PhotoId? = null` to `AppScreen.Album` (after `focusTile`); KDoc: focus goes to [focusTile] if it is in the row, else to [fallbackTile] if it is.
- Add `data class AlbumRowViewer(val album: DomainAlbum, val opened: Photo, val cameFrom: CameFrom) : AppScreen`, KDoc: one photo of [album] full-screen, opened from the album screen's row on [opened]; left/right move through the album with its grid; Back comes back to the row.
- `fun openAlbumRowViewer(album: DomainAlbum, opened: Photo, cameFrom: CameFrom = CameFrom.AlbumsTab)` → `_screen.value = AppScreen.AlbumRowViewer(album, opened, cameFrom)` (no `moveTo`, as `openAlbumPhotoViewer`).
- `fun backFromRowViewer(viewing: PhotoId?)`: only when the current screen is `AlbumRowViewer`; `moveTo(AppScreen.Album(album.id, focusTile = viewing ?: opened.id, fallbackTile = opened.id.takeIf { viewing != null && it != viewing }, cameFrom = cameFrom))`.
- In `back()`: `is AppScreen.AlbumRowViewer -> AppScreen.Album(current.album.id, focusTile = current.opened.id, cameFrom = current.cameFrom)`.
- Import `domain.albums.Photo`.

### [x] 1.2 — `fallbackTile` on the album screen

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumScreen.kt`
- `AlbumScreen`, `AlbumContent`, `LoadedAlbum` take `fallbackTile: PhotoId? = null` (`AlbumContent` after `focusTile`, defaulted so the previews and tests keep compiling).
- In `LoadedAlbum`'s `LaunchedEffect`: `val target = listOfNotNull(focusTile, fallbackTile).firstOrNull { t -> contents.firstItems.any { it.id == t } }`; scroll to and focus `target` as `focusTile` is today. Pass `target` (not `focusTile`) to `Strip` as the tile carrying `tileFocus`.
- Update the KDoc of `AlbumScreen`.

### [x] 1.3 — Wire the screen in `MainActivity`

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`
- `is AppScreen.Album` passes `fallbackTile = current.fallbackTile`.
- New branch `is AppScreen.AlbumRowViewer ->`: `val grid = albumGrid(container, connection, current.album)`; `LaunchedEffect(current) { grid.open(current.opened); grid.refresh(); opened = true }` and `PhotoViewerScreen` only once `opened` (a `bg` box before), so the viewer never sees no photo and goes straight back; refresh as Home's Recently added does, the grid may be stale. (Planned as `remember { open; refresh }` during composition; lint's RememberReturnType rejects a `remember` returning Unit.) `BackHandler { app.backFromRowViewer(grid.state.value.viewing?.id.takeIf { opened }) }`; `PhotoViewerScreen(viewModel = grid, onBack = { app.backFromRowViewer(grid.state.value.viewing?.id) })`. Comment why the branch has its own `BackHandler` (the activity's one can't see the photo on screen).

### [x] 1.4 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/AppViewModelTest.kt` (mirror the `fromTile` tests at ~line 130)
- `opening a photo from the album row and coming back lands on the photo shown, falling back to the opened one` for both `CameFrom.AlbumsTab` and `CameFrom.HomeRow`.
- `back from the row viewer without the photo shown lands on the opened tile` (`backFromRowViewer(null)` and plain `back()`).
- `back from the row viewer on the opened photo has no fallback` (`fallbackTile == null`).
- `backFromRowViewer elsewhere does nothing`.

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumFocusTest.kt`
- `open(...)` takes `fallbackTile: PhotoId? = null`.
- `coming back with a tile not in the row falls back to the opened tile`, `the shown tile wins over the fallback`, `with neither in the row it lands on start slideshow`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AppViewModelTest' --tests '*AlbumFocusTest'`
- New tests pass; existing `fromTile` / `focusTile` tests unchanged and green.

---

## Checkpoint 2 — OK on a row tile opens it · commit: `feat(albums): OK on a tile of the album row opens that photo`

Goal: OK on a tile of "In this album" opens the photo full-screen, through the album's grid.

### [x] 2.1 — `onOpenFromRow` on the album screen

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumScreen.kt`
- `AlbumScreen` and `AlbumContent` take `onOpenFromRow: (Album, Photo) -> Unit` (after `onDownFromRow`); `LoadedAlbum` takes `onOpenFromRow: (Photo) -> Unit`, passed as `{ onOpenFromRow(load.page.album, it) }`.
- `Strip` takes `onOpen: (Photo) -> Unit` and passes `onClick = { onOpen(photo) }` to each `PhotoTile`. Update the Strip KDoc (drop "opening one comes with the photo viewer").
- Update the preview's `AlbumContent` call.

### [x] 2.2 — Wire it

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`
- `is AppScreen.Album` passes `onOpenFromRow = { album, photo -> app.openAlbumRowViewer(album, photo, current.cameFrom) }`.

### [x] 2.3 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumFocusTest.kt`
- Record `opens` from `onOpenFromRow`.
- `OK on a row tile opens that photo`: from Start slideshow, Down to the row, Right as needed, `Key.DirectionCenter`; assert the recorded `Photo` is the focused tile's. Fails without 2.1.
- `OK on a far tile opens it` (`focusTile = p20`, OK → `p20`), and `OK on a video tile opens it` with a video in `items` (assert `isVideo` carried).
- `left, right and up on the row don't open the grid` also asserts nothing was opened.

File: `docs/design/README.md`
- Album row: "OK on a tile of the strip opens it full-screen; Back comes to the tile of the photo shown if it is in the strip, else to the tile opened". Photo row: add "or to the album screen's strip when opened from it".

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AlbumFocusTest' --tests '*AppViewModelTest'`
- Full CI: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`

---

## Out of scope

- Hold OK on a row tile to start a slideshow from it (the grid's long-press); the row only opens.
- A `KeyHint` on the album screen.
- Prefetching the album grid's months when the album screen opens; a first left/right pressed before the months arrive is dropped, as on Home's Recently added viewer.
- Any change to Browse photos or Down into the grid: they still start at the top.
- Any Immich API change.
