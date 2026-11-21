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

## Implementation

- Shipped:
  - `fromTile` on `AppScreen.AlbumPhotos`, `AlbumPhotoViewer` and `AlbumPhotoSlideshow`, `focusTile` on `AppScreen.Album`; `back()` turns `fromTile` into `focusTile` (`ui/AppViewModel.kt:60`, `:74`, `:78`, `:89`, `:254`), carried through the grid's photo and show (`MainActivity.kt:153`).
  - Album screen: Down on a row tile calls `onDownFromRow` (`ui/album/AlbumScreen.kt:375`, wired at `MainActivity.kt:145`); on arrival with `focusTile` the row scrolls to it, then focuses it, falling back to Start slideshow (`ui/album/AlbumScreen.kt:228`). Row tiles carry the photo id as a test tag.
  - `PhotoGrid.onUpFromTop` (`ui/photos/PhotoGrid.kt:92`, `:141`): tracks whether focus is on the first month's first row or its Try again (`:209`, `:237`), cleared when the grid loses focus. Only `AlbumPhotosScreen` passes it, as its `onBack` (`ui/album/AlbumPhotosScreen.kt:124`). Grid tiles carry `tileKey` as a test tag.
  - `docs/design/README.md` Album and Album photos rows describe Down and Up.
- Tests: full CI command green (`testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`): 547 passed / 0 failed / 0 skipped. New: 2 in `AppViewModelTest`, 6 in `AlbumFocusTest`, 5 in `AlbumPhotosFocusTest`, 1 in `PhotoGridFocusTest`. Checked the tests bite: without the row scroll, the far-tile test fails; without `onUpFromTop = onBack`, the top-row and Try again tests fail.
- Outstanding: the TV check in the test plan is for self-verification.
- Contradicted the plan: none in the code. Test details adjusted in the checklist: Down from Start slideshow reaches some row tile whose position depends on layout, so the "p2" case enters on `p0` via `focusTile`; Up from the Photos tab's first tile lands on the Albums pill (straight above), so that test asserts "a menu pill, not a tile"; `AlbumPhotosFocusTest` advances Robolectric's system clock because `MovePace` reads `SystemClock`, otherwise a second Up/Down is dropped.

## Review

- Fixed: `PhotoGrid` kept "on the top row" as a flag set when a tile gained focus, so a refresh that put a newer month above (Browse photos refreshes whenever the app comes back to it) left the flag true on the old first month: Up there went Back instead of moving into the new month. It now keeps the focused month and tile and checks them against the current sections on each Up (`ui/photos/PhotoGrid.kt`). New test `after a refresh puts a newer month on top, up from the old first row moves to it` in `AlbumPhotosFocusTest` fails without the fix. Full CI command green.
- Raised: none
- API: none touched (navigation in `ui/` only)
- Layers: clean

## Doc drift

- Applied: README.md:Development — the list of Robolectric focus tests named three classes; the branch added `AlbumFocusTest` and `AlbumPhotosFocusTest`, now listed with what they drive (rung 3).
- Skipped: `docs/design/README.md` Album and Album photos rows — already updated by the branch; checked against `AlbumScreen.kt` (Down from a row tile, return to `focusTile` or Start slideshow fallback) and `PhotoGrid.onUpFromTop` / `AlbumPhotosScreen` (Up from the first month's top row does Back), accurate.
- Skipped: `MovePace` reads `SystemClock`, so a Robolectric focus test pressing Up/Down twice must advance `ShadowSystemClock` — the dropped press fails the test visibly and `AlbumPhotosFocusTest` shows the pattern; one `rg MovePace` finds it.
- Skipped: `fromTile` / `focusTile` on `AppScreen` — navigation state the code and `AppViewModelTest` describe; no cross-cutting rule for CLAUDE.md.
- Stale, fixed separately: none

## Self verification

Result: partial

- CI build: `testDebugUnitTest koverVerifyDebug lintDebug assembleDebug` green: 548 tests passed / 0 failed / 0 skipped, line coverage 91.4% (koverVerify passed), lint clean, debug APK built.
- Server: not applicable (no `data/` change, no Immich call touched).
- TV: not run (the Chromecast is a shared household TV; the user's standing rule is not to install or launch on it without their say-so that it is free, and today's permission covered #43 and #33 only).
- Verified: the navigation by the unit and Robolectric focus tests (Down from a row tile opens the grid from that tile; Up from the first month's top row and Back return to that tile, or to Browse photos when entered by its button; Up from a later month's top row and after a refresh stays in the grid; the Photos tab's Up still reaches the menu).
- Failed: none
- Not self-verifiable: on the TV, open an album; Down from the third row tile opens Browse photos on the first photo; Up from the top row returns to that tile, scrolled into view; Down then Back does the same; Browse photos by OK then Up returns to Browse photos; Down, Right, OK opens a photo, Back returns to its tile, Up returns to the album's tile; Down twice then Up twice stays in the grid until the top row; Home's hero, View photos, Up from the top row goes to Home. Check the amber focus outline at each step and that nothing crashes.
