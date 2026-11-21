# Notes — feat: Down from an album's photo row opens all its photos, Up comes back

Issue: #46 — feat: Down from an album's photo row opens all its photos, Up comes back (https://github.com/YasarMahomedAbbas/immich-tv/issues/46)
Branch: `feat/46-down-into-album-photos`

## Planning

- Approach: carry "entered by Down from row tile X" as `fromTile` through `AppScreen.AlbumPhotos`, the viewer and the show, so Back lands on that tile (`Album.focusTile`). Up from the grid's top row is wired to the screen's `onBack`, through a new optional `PhotoGrid.onUpFromTop`. UI only, with no API change.
- Decided (the pipeline, on the user's behalf; flags: ux, scope): option 1 of 3. Every grid `AlbumPhotosScreen` draws follows the Up-from-top rule, going where its Back goes: an album grid from the album screen goes to the album, an album grid from Home's hero and a memory grid go to Home. The alternatives were album grids only (option 2), or only album grids entered from the album screen (option 3).
- Decided: the entry mode is carried in `AppScreen` and survives a photo or a show opened from the grid. Entering through Browse photos still returns to Browse photos.
- Decided: `PhotoGrid` gets an optional "Up from the top" action that only this screen passes, so the Photos tab is unchanged. A failed first month's Try again counts as the top row.
- Decided: if the row tile is gone after a refresh, focus falls back to Start slideshow, as the row already does when it loses its tile. The back link keeps holding focus while loading and can still be reached from the empty and failed states.
- Decided: the user's canvas choices, recorded in the ticket, are Up from the top row, back to the tile you left, and Down lands on the first photo.
- Open risks: the album strip's `LazyRow` must scroll to a far tile before it can take focus. Checkpoint 2 tests this with a tile at index 20.
