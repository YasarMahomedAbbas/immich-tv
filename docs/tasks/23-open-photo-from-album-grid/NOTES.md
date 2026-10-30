# Notes — feat: open a photo from an album's grid

Issue: #23 — feat: open a photo from an album's grid (https://github.com/YasarMahomedAbbas/immich-tv/issues/23)
Branch: `feat/23-open-photo-from-album-grid`

## Planning

- Approach: the Photos tab's viewer, hold OK and key hint come to the album and memory grids through the existing `Timeline` ViewModel. A new use case gives a show from a photo in the grid's order. No new server calls.
- Decided (pipeline, on the user's behalf; flag: ux, scope): Q1 option 1, a show from an album tile uses the album's saved time, videos and date-and-place, and always the grid's order from the held photo; the saved Order is ignored. Holding on a video plays it, unless the album skips videos, then the next photo. Rejected: fixed defaults as on the Photos tab (option 2); all saved settings including Order (option 3).
- Decided (pipeline, on the user's behalf; flag: ux, scope): Q2 option 1, the memory grid gets it too: OK opens, left/right stay in the memory, Back to the tile, hold OK plays the memory from that photo with its fixed defaults. Rejected: album grids only, with a new ticket (option 2).
- Decided (pipeline, flagged to the user's stand-in; flag: ux), the "decided, flag if you disagree" list:
  - Viewer: the same full-screen look as the Photos tab, a video a still with the badge, no wrap at the ends.
  - Back: from the viewer or the show, to the tile last viewed or the one the show started on. Back from the grid as today, including albums opened from Home.
  - Browse photos entered afresh starts at the top, not the old tile, though the grid's state lasts the session.
  - An album-grid show says "‹ Album" even for an album opened from Home's hero, as Back goes to the grid. A memory-grid show says "‹ Memory" (added in planning, on the same reasoning: "‹ Home" would be wrong).
  - The grid gets the Photos tab's OK / hold OK hint; the show wraps at its ends like other album shows.
  - The manual's rows are updated and the open decision removed.
- Open risks:
  - open-risk: focus restoring to a far tile on return, with Back's own focus request just before it, is only provable on the TV. Compose focus code has no automated tests.
  - open-risk: the memory grid can't be driven on the TV, as the server has no memories; tests only.
  - open-risk: the TV check depends on the window in the issue's comments (until 15:30 CET on 2026-10-30) and the shared lock; outside it, it is skipped and recorded.

## Implementation

- Shipped: `LoadSlideshowFromPhoto` and the shared `playlistFrom` (`domain/slideshow/Slideshow.kt:59`, `:71`), `Memory.playlistFrom` (`domain/home/Memory.kt`); the four new screens, `returning` and the settings load that yields to Back (`ui/AppViewModel.kt:59`, `:149`); OK, hold OK, the key hint and the tile restore on the grid (`ui/album/AlbumPhotosScreen.kt:44`); the screens and shared grid keys in the activity (`MainActivity.kt:143`, `:225`); the manual's rows, and the open decision removed.
- Tests: checkpoint runs only, per the user's speed note. Checkpoint 1: `LoadSlideshowFromPhotoTest` (11), `MemoryTest` (5), `LoadLibrarySlideshowTest` (6, unchanged), build successful. Checkpoints 2 and 3: `AppViewModelTest` 29 passed / 0 failed, `PhotoGridViewModelTest` 35 passed / 0 failed. The full CI command was not run here: it runs once, in `raise-pr`.
- Outstanding: the on-TV check of 3.1 (focus lands on the restored tile, not Back) stays unticked for self-verification.
- Contradicted the plan: checkpoint 2 can't compile without the activity's new branches, as the `when` over `AppScreen` is exhaustive. The helpers and those branches went in with checkpoint 2; checkpoint 3 wires the grid's callbacks. No behaviour change from the plan.

## Review

- Fixed: none
- Raised: none
- API: no call touched; `data/` is unchanged. The show from a tile reuses `slides()`, the grid its `timeline/buckets` and `timeline/bucket`, checked against v3.3.5 at planning (the running version, read again here).
- Layers: clean
