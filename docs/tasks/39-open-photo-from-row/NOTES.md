# Notes — feat: open a photo from the album screen's preview row

Issue: #39 — feat: open a photo from the album screen's preview row (https://github.com/YasarMahomedAbbas/immich-tv/issues/39)
Branch: `feat/39-open-photo-from-row`

## Planning

- Approach: OK on a row tile opens a new `AppScreen.AlbumRowViewer`. That screen sets the photo as the one being viewed on the album's existing grid ViewModel, the way Home's Recently added does, and shows `PhotoViewerScreen`, so left/right move through the whole album in its order. Back returns to the album screen with the tile to focus. The change is UI and navigation only, with no API change.
- Decided (the pipeline, on the user's behalf; flag: ux): option 1 of 3. Back lands on the tile of the photo on screen when that photo is in the row (scrolled into view), otherwise on the tile that was opened. This matches the design manual's Photo rule ("Back returns to the last viewed tile of its grid"). The alternatives were always the opened tile (option 2) or the row's last tile when past the row (option 3).
- Decided: a new `AppScreen` variant, not a flag on `AlbumPhotoViewer`, so the grid viewer's Back path stays as it is. The viewer's branch has its own `BackHandler`, because only the grid ViewModel knows the photo on screen. `AppScreen.Album` gains a `fallbackTile` beside `focusTile`.
- Decided: a video tile opens as a still with the play badge, as it does from the grid. Hold-OK to start a slideshow from the row is out of scope. Browse photos and Down into the grid still start at the top.
- Open risks: a viewer opened before the album grid's months have loaded drops the first left/right press, as Home's Recently added viewer does. The grid ViewModel's `open` is called from `remember` during composition, so that `viewing` is set before the viewer's first frame.
