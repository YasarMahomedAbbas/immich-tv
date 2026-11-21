# Checklist — feat: Down from an album's photo row opens all its photos, Up comes back

Issue: #46 — feat: Down from an album's photo row opens all its photos, Up comes back (https://github.com/YasarMahomedAbbas/immich-tv/issues/46)
Branch: `feat/46-down-into-album-photos`
Problem: On an album, Down from the "In this album" row does nothing, so the remote can't carry on into the album's photos. In Browse photos, getting back up means stopping on the "‹ <album>" link first.

## Done when

- Down from any tile in "In this album" opens Browse photos for the album, the same as OK on Browse photos, with focus on the first photo (top-left).
- Up from the grid's top row (the first month's first row, or its Try again when that month failed) does what Back does. The back link stays on screen and still works when clicked, but the D-pad no longer reaches it from the grid. Up from the top row of a later month still moves to the month above.
- The Up rule holds on every grid this screen draws. An album grid entered from the album screen goes back to the album. An album grid from Home's hero and a memory grid go back to Home, as their Back does.
- Back from the grid goes where it goes today.
- On the album screen, coming back by Up or by Back lands on the row's tile you left, scrolled into view, if you came down from the row. If you came in through Browse photos, it lands on Browse photos (as today). The tile-you-left rule still holds after opening a photo or a show from the grid and coming back from it.
- Unchanged: Browse photos entered afresh starts at the top; Back from a photo or a show opened from the grid returns to its tile; Left/Right along the row; Up from the row to the settings; the Photos tab's grid (Up from its top row still goes to the rail or menu).
- `docs/design/README.md` (Album and Album photos rows) says how Down and Up move between the two screens.
- Tests: Down from the row opens the grid on its first photo. Up from the top row and Back each return to the row's tile when entered from the row, and to Browse photos when entered from the button. Up from a later month's top row stays in the grid.

## References

- Immich API: none. This is navigation in `ui/` only, with no server, `domain/` or `data/` change.
- Design canvas (the user's choices): https://claude.ai/artifact/CSKSDoaKzYo15uZF2mwbTT — Up from the top row; back to the tile you left; Down lands on the first photo.
- #39 (OK on a row tile opens the photo) is separate. If both land, OK opens the photo and Down opens the grid.
- Prior work: `docs/tasks/23-open-photo-from-album-grid/` (`returning` through the viewer and the show), `docs/tasks/7-browse-photos-by-month/`.

## Context

`AppViewModel` carries every screen as an `AppScreen` value. `Album.focusBrowse` already sends focus back to Browse photos, and `AlbumPhotos.returning` sends the grid back to its tile after a photo or a show. This ticket adds a third such fact: `fromTile: PhotoId?` on `AlbumPhotos`, `AlbumPhotoViewer` and `AlbumPhotoSlideshow` (non-null means "entered by Down from this row tile"). It is carried through the round trip, and `back()` turns it into `Album.focusTile`. Up from the top is wired as the screen's `onBack`, so it always goes exactly where Back goes; no second routing table. `PhotoGrid` (shared with the Photos tab) gets an optional `onUpFromTop` that only `AlbumPhotosScreen` passes. The grid already knows the focused tile (`lastFocused`); it will also know whether that tile is in the first section's first `COLUMNS` tiles, or is the first month's Try again. The album strip gets a Down handler on its tiles, and a `focusTile` that scrolls the `LazyRow` to the tile and focuses it. If the tile is gone, focus falls back to Start slideshow, as `Strip`'s `onFocusLost` already does. Decided (the pipeline, on the user's behalf; see NOTES): the Up rule covers every grid `AlbumPhotosScreen` draws (album from the album screen, album from Home's hero, memory). Decided as well: the back link keeps holding focus in the loading state and can still be reached from the empty and failed states' buttons; only the loaded grid's Up is taken over.

## Test plan

- Unit: `AppViewModelTest`: `openAlbumPhotos(album, cameFrom, fromTile)` keeps `fromTile`, which survives the viewer and the show and their Back (with `returning = true`). Back from a grid with `fromTile` gives `Album(id, focusTile = fromTile, cameFrom)`, and without it gives `focusBrowse = true` as today. HomeHero and memory Back are unchanged.
- Compose (Robolectric, real keys, modelled on `PhotoGridFocusTest`):
  - `AlbumFocusTest` (new): Down from a row tile calls `onDownFromRow` with that tile, while Left/Right along the row and Up to the settings don't. `focusTile` focuses that tile (one far along, so it has to be scrolled to). A `focusTile` no longer in the row falls back to Start slideshow. `focusBrowse` still focuses Browse photos.
  - `AlbumPhotosFocusTest` (new): on arrival the first tile has focus. Up from each tile of the first row calls `onBack` once, and Up from a lower row of the first month doesn't. With two loaded months, Up from the second month's first row lands on the first month and doesn't call `onBack`. Up from a failed first month's Try again calls `onBack`.
  - `PhotoGridFocusTest`: one more case: on the Photos tab, Up from the first row still leaves the grid for the menu (no `onUpFromTop` given).
- MockWebServer: none (no new call).
- Real server: none (no new call). The TV check below runs against the real album data.
- On the TV: open an album. Down from the third row tile opens Browse photos on the first photo. Up from the top row returns to the album on that third tile, scrolled into view. Down again, then Back, returns to the same tile. Browse photos by OK, then Up from the top row, returns to Browse photos. Down, Right, OK opens a photo; Back returns to its tile and Up returns to the album's tile. Down twice in the grid, then Up twice, stays in the grid until the top row. On Home's hero, View photos, then Up from the top row, goes to Home.

---

## Checkpoint 1 — Carry "came down from a tile" through the screens · commit: `feat(albums): remember the row tile Browse photos was entered from`

Goal: the app's navigation knows which row tile a grid was entered from, keeps it through a photo or a show from the grid, and sends Back to that tile. It is unit-tested, with no UI change yet.

### [x] 1.1 — The screen values

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/AppViewModel.kt`
- `AppScreen.Album`: add `val focusTile: PhotoId? = null` after `focusBrowse`. KDoc: "[focusTile]: coming back from its photos entered by Down from that row tile, so focus goes to it."
- `AppScreen.AlbumPhotos`: add `val fromTile: PhotoId? = null` last. KDoc: "[fromTile]: entered by Down from that tile of the album's row; Back (and Up from the top) returns there instead of to Browse photos."
- `AppScreen.AlbumPhotoViewer` and `AppScreen.AlbumPhotoSlideshow`: add `val fromTile: PhotoId? = null` last, carried for the way back.

### [x] 1.2 — Opening and Back

Same file.
- `openAlbumPhotos(album: DomainAlbum, cameFrom: CameFrom = CameFrom.AlbumsTab, fromTile: PhotoId? = null)` → `AppScreen.AlbumPhotos(album, cameFrom, fromTile = fromTile)`.
- `openAlbumPhotoViewer(album, cameFrom, fromTile: PhotoId? = null)` and `openAlbumPhotoSlideshow(album, from, cameFrom, fromTile: PhotoId? = null)` pass it into their screens.
- `back()`:
  - `AlbumPhotos` (not HomeHero) → `AppScreen.Album(id, focusBrowse = current.fromTile == null, focusTile = current.fromTile, cameFrom = current.cameFrom)`.
  - `AlbumPhotoViewer` / `AlbumPhotoSlideshow` → `AlbumPhotos(album, cameFrom, returning = true, fromTile = current.fromTile)`.

### [x] 1.3 — Wire the carry in the activity

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`
In the `AppScreen.AlbumPhotos` branch, pass `current.fromTile` to `openAlbumPhotoViewer` and `openAlbumPhotoSlideshow`. (The album screen's Down is wired in checkpoint 2.)

### [x] 1.4 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/AppViewModelTest.kt`
- `down from a row tile opens the album's photos, and back returns to that tile`: `openAlbum`, then `openAlbumPhotos(summer, fromTile = PhotoId("p3"))`. Back gives `Album(summer.id, focusBrowse = false, focusTile = PhotoId("p3"))`.
- `a photo or a show from a grid entered from the row still goes back to the row tile, for album-screen origins`: for `AlbumsTab` and `HomeRow`, viewer → back → `AlbumPhotos(..., returning = true, fromTile = p3)` → back → `Album(..., focusTile = p3, cameFrom = origin)`. The same through `openAlbumPhotoSlideshow` (with `advanceUntilIdle()`).
- The existing `browse photos opens ... back returns to the album on browse photos` stays green unchanged (proves `fromTile = null` keeps `focusBrowse = true`).

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AppViewModelTest*'`

---

## Checkpoint 2 — Down from the row, and back onto its tile · commit: `feat(albums): Down from the photo row opens Browse photos`

Goal: on the album screen, Down from a row tile opens Browse photos entered from that tile, and the album screen focuses `focusTile` (scrolled into view) when it comes back.

### [x] 2.1 — The album screen's parameters

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumScreen.kt`
- `AlbumScreen(..., onDownFromRow: (Album, PhotoId) -> Unit, focusTile: PhotoId? = null, ...)`; thread both through `AlbumContent` and `LoadedAlbum`.
- Make `AlbumContent` `internal` (for the focus test); update the preview call.
- KDoc: "Down from a tile of the row calls [onDownFromRow]; [focusTile] puts focus on that tile instead, scrolled into view, for coming back from the grid entered there (Start slideshow if it's gone)."

### [x] 2.2 — Focus on arrival

Same file, `LoadedAlbum`'s `LaunchedEffect(Unit)`: when `focusTile != null`, ask `Strip` to focus it. Hoist the strip's `LazyListState` (`rememberLazyListState()`) and a `FocusRequester` for the tile whose id is `focusTile`. After `withFrameNanos {}`, if the index of `focusTile` in `contents.firstItems` is found: `listState.scrollToItem(index)`, another `withFrameNanos {}`, then `runCatching { tileFocus.requestFocus() }`. If it isn't found, or the request fails, use `startFocus`. Otherwise keep today's `focusBrowse`/`startFocus` choice.

### [x] 2.3 — Down on a row tile

Same file, `Strip`: add `onDown: (PhotoId) -> Unit`, `listState: LazyListState`, `focusTile: PhotoId?`, `tileFocus: FocusRequester`. On each tile's modifier, `onPreviewKeyEvent { it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown && run { onDown(photo.id); true } }`. Attach `Modifier.focusRequester(tileFocus)` to the tile whose id is `focusTile`. Update `Strip`'s KDoc.

### [x] 2.4 — Wire it

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`
In the `AppScreen.Album` branch: `onDownFromRow = { album, tile -> app.openAlbumPhotos(album, current.cameFrom, fromTile = tile) }` and `focusTile = current.focusTile`.

### [x] 2.5 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumFocusTest.kt` (new; Robolectric setup and `settle()`/`press()` as in `PhotoGridFocusTest`). Render `AlbumContent` with a loaded page of 30 items.
- `down from the buttons reaches the row, and down from a row tile opens the grid from that tile`: from Start slideshow, Down until a row tile is focused (no call yet), then Down: `onDownFromRow` was called once with that tile.
- `down from the third tile opens the grid from it`: entered on `p0`, Right ×2 and Down. `onDownFromRow` was called once with `p2`.
- `left, right and up on the row don't open the grid`: entered on `p1`, Right, Left (back on `p1`), Up. `onDownFromRow` is never called, and the last Up lands on a setting.
- `coming back to a tile far along the row focuses it`: `focusTile = p20`. The tile tagged `p20` is focused (give tiles a `testTag` of the photo id if none exists).
- `coming back to a tile no longer in the row lands on start slideshow`: `focusTile = gone`. Start slideshow is focused.
- `coming back from browse photos still lands on browse photos`: `focusBrowse = true`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AlbumFocusTest*' --tests '*AppViewModelTest*'`

---

## Checkpoint 3 — Up from the grid's top row goes back · commit: `feat(photos): Up from the top of an album's photos goes back`

Goal: on every grid `AlbumPhotosScreen` draws, Up from the first month's first row (or its Try again) calls `onBack`. The back link stays clickable, and the Photos tab's grid is unchanged.

### [ ] 3.1 — The grid knows its top row

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/photos/PhotoGrid.kt`
- Add parameter `onUpFromTop: (() -> Unit)? = null`. KDoc: "Up from the first month's first row (or its Try again) calls [onUpFromTop] instead of leaving the grid, when given."
- Track `var onTopRow by remember { mutableStateOf(false) }`. `monthSection` gets `isFirst` already: tiles set `onTopRow = isFirst && index < COLUMNS` when they gain focus (via an extra `onTopRowFocused: (Boolean) -> Unit`), and the Try again button sets it to `isFirst`.
- In the grid's `onPreviewKeyEvent`, before pacing: if `onUpFromTop != null && event.type == KeyDown && event.key == Key.DirectionUp && onTopRow`, call it and return `true`.
- Clear `onTopRow` when the grid loses focus (`onFocusChanged { if (!it.hasFocus) onTopRow = false }` on the grid).

### [ ] 3.2 — The screen passes its Back

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumPhotosScreen.kt`
- Pass `onUpFromTop = onBack` to `PhotoGrid`. Make `AlbumPhotosContent` `internal` for the test.
- Update the screen's KDoc: "Up from the grid's top row does what Back does; the back link stays clickable but the D-pad doesn't reach it from the grid."

### [ ] 3.3 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumPhotosFocusTest.kt` (new; harness as in `PhotoGridFocusTest`, rendering `AlbumPhotosContent` with a counting `onBack`).
- `down from the row lands on the first photo`: with the first month loaded, the first tile is focused on arrival.
- `up from any tile of the top row goes back`: Right ×k then Up, for k in 0..5. `onBack` was called once each time.
- `up from the second row of the first month stays in the grid`: 14 photos, Down, Up. `onBack` is not called and the first tile is focused.
- `up from a later month's top row moves to the month above`: two loaded months (14 + 8); Down ×3 to reach the second month's first row, then Up. `onBack` is not called and focus is on the first month.
- `up from a failed first month's try again goes back`.

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/photos/PhotoGridFocusTest.kt`
- `on the photos tab, up from the first row still goes to the menu`: with the first month loaded, Up moves focus to the Photos pill (or the rail, whichever is above today). Assert it is not on a tile.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AlbumPhotosFocusTest*' --tests '*PhotoGridFocusTest*'`
- Then the full CI command: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`

---

## Checkpoint 4 — The manual · commit: `docs(design): Down and Up between an album and its photos`

Goal: the design manual describes the new moves.

### [ ] 4.1 — Album and Album photos rows

File: `docs/design/README.md`
- Album row: append "Down from a tile of the strip opens Album photos on its first photo; coming back by Up or Back lands on that tile, scrolled into view (on Browse photos when it was opened by Browse photos)."
- Album photos row: append "Up from the first month's top row does what Back does; the back link stays clickable but the D-pad doesn't reach it from the grid. The same holds for an album's grid from Home's hero and a memory's grid (both back to Home)."
- Back link component line: no change (it still "does what Back does").

### Verify

- Read the two rows back against the Done when list.

---

## Out of scope

- OK on a row tile opening the photo (#39).
- The Photos tab's grid, whose Up from the top row still reaches the rail and menu.
- Reaching the back link from the empty and failed states' buttons, which is unchanged.
- Any server, `domain/` or `data/` change.
- Holding Down to repeat into the grid beyond the first press (MovePace already paces the grid).
