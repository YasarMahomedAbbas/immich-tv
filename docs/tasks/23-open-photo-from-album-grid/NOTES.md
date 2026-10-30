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

## Doc drift

- Applied: README.md:features, Albums line — a photo opens full screen and hold OK starts a show, in an album's grid and a memory's (rung 3, using the app).
- Applied: docs/design/README.md:Photo row — left/right also move through a memory, which the branch's edit had left out (rung 4, the area's manual). The branch's other manual rows were checked against the code and hold: the hint, the shows' labels and settings, memory shows being photos only (`LoadHome` filters them), and #23's open decision removed.
- Skipped: a grid and its viewer sharing one ViewModel key — `albumGrid` and `memoryGrid` in `MainActivity` say so, and they are the only way to build those grids.
- Skipped: `returning` and the show's use of the grid's order — the KDoc on `AppScreen` and `LoadSlideshowFromPhoto` says it, the tests enforce it, and the manual has the behaviour.
- Skipped: README's architecture and server sections, and `CLAUDE.md` — still true: no new layer, permission, server call or command; new ViewModel keys take `connection` as README requires.
- Stale, fixed separately: none

## Self verification

Result: partial

- CI build: not run here, per the user's speed note; the full command runs once in `raise-pr`. `assembleDebug` built in Docker. No tests were run in this stage and no code changed.
- Server: Immich 3.3.5. `data/` is unchanged, so the spec was not re-read here (checked at planning); the calls the show from a tile relies on were repeated live.
- TV: installed the debug build on the Chromecast. Driven from Home's row (Cats, 699 items, oldest first) and the Albums tab (Bamberg, 9 photos, newest first). Lock held 12:49:44 to 12:57:59 UTC.
- Verified:
  - Server: `GET /api/albums`, then `timeline/buckets` and `timeline/bucket` with `albumId` and both `order` values for all 15 albums. Every album's item count matched, and `desc` was the exact reverse of `asc`.
  - OK opens the photo full-screen; a video is a still with its badge.
  - Left stops at the album's first photo and right at its last (two presses past each end: Cats' start, Bamberg's end).
  - Right crossed two month boundaries, and Back focused the viewed tile in December, 50 photos in, with the grid scrolled to it.
  - Hold OK starts the show at the held photo with its date shown; the held key didn't pause it, as it advanced on its own. Back returned to the held tile, not the one the show had reached.
  - Hold on a video with Play muted: the video plays. With Skip videos: the show starts at the next photo and skips the following video, on the album's saved 12 seconds. That run had Shuffle saved and still followed the grid.
  - An oldest-first album's show runs in grid order (Cats, by date). A newest-first album's last tile opens as 9 / 9 (Bamberg), and its show says "‹ Album".
  - Back from the grid reaches the album on Browse photos, then Browse photos again starts at the top on the first tile. From the Albums tab, Back goes on to that tab on the album's tile.
  - The key hint shows on the grid. No error lines from the app or `AndroidRuntime` in logcat.
- Failed: none
- Not self-verifiable:
  - The memory grid: this server has no memories, so it is proven by tests only. To check by hand: Home's View photos on a memory, then OK, left/right, Back, hold OK, and "‹ Memory".
  - Back while the album's settings are loading, and an album grid opened from Home's hero: tests only.
- Observed, not a bug in this branch's scope: 60 fast scripted right presses in the viewer moved about 50 photos, so some were dropped around month loads. The viewer and the grid still agreed on the photo. A person pressing at a normal pace wasn't tested for this.
- Left as found: Cats' saved settings were changed for the checks and put back (8 seconds, Oldest first, Play muted, date and place on); the TV was returned to its launcher.
