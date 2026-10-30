# Notes — feat: browse photos by month

Issue: #7 — feat: browse photos by month (https://github.com/YasarMahomedAbbas/immich-tv/issues/7)
Branch: `feat/7-browse-photos-by-month`

## Planning

- Approach: the library is a second `Timeline` behind a new `LibraryRepository`, shown by the existing grid and its ViewModel beside a new month rail. A full-screen viewer moves through the same ViewModel's months, and the slideshow gains a starting point.
- Decided (pipeline, on the user's behalf; flag: scope, api): Q1.1, the user's own timeline as Immich shows it: `visibility=timeline`, stacks collapsed, no partners. **For the PR body: the Photos tab shows far fewer items than Albums, 555 here against 2,388 in albums (1,370 in the 5 owned albums)**, because photos in albums shared by others, and any not on the timeline, stay out. Rejected: stacks expanded, 574 items (Q1.2); adding partners, which the current key can't check without `partner.read` (Q1.3).
- Decided (pipeline, on the user's behalf; flag: ux): Q2.1, Back from the full-screen photo focuses the tile of the photo last viewed, scrolled there. Rejected: the tile originally opened (Q2.2).
- Decided (pipeline, on the user's behalf; flag: ux): Q3.1, the whole photo fitted on the page ground, no overlays, a play badge on a video. No canvas board exists for it, so the manual gains the shape. Rejected: the slideshow's look with backdrop and date and place (Q3.2).
- Decided (pipeline, on the user's behalf; flag: ux, scope): Q4.1, videos stay in the left/right sequence as a still with the badge, with no playback. #6 covers the slideshow only, so playing them here needs its own ticket. Rejected: skipping videos (Q4.2); playing them, which pulls the player forward from #6 (Q4.3).
- Decided (pipeline, on the user's behalf; flag: api, open-risk): Q5.1, a show reads every month once at the start and holds a light list, as album shows do. 10 requests here; a very large library waits before its first photo. Rejected: month by month while playing (Q5.2), which needs the show reworked and makes the progress bar approximate.
- Decided (pipeline, on the user's behalf; flag: ux, scope): Q6.1, fixed defaults: 8 s, date and place on, newest to oldest from the chosen photo, wrapping to the newest after the oldest. They can't be changed. Rejected: stored library-wide settings with a way to edit them (Q6.2).
- Decided (pipeline, on the user's behalf; flag: scope): Q7.1, Photos tab only. **Needs a follow-up ticket for OK on the album grid**, which still does nothing. Rejected: including the album grid now (Q7.2).
- Decided (pipeline, flagged to the user's stand-in; flag: ux), the "also decided" list:
  - Rail: OK on a month jumps and moves focus to its first tile; moving along the rail alone doesn't scroll the grid; year labels aren't focusable; left from the grid's first column enters the rail.
  - Viewer: no wrap at the timeline's ends.
  - Slideshow: hold OK starts it, in the grid only, with the canvas's hint pill and no separate button; holding on a video starts at the next photo; Back returns to the tile it started on; videos stay out until #6.
  - Structure: the Immich call plumbing is shared between albums and the library; the grid position is held in the ViewModel because the screen is torn down when the viewer opens; the screen refreshes quietly on entry, as the others do.
- Open risks:
  - open-risk: focusing a tile far off-screen after a jump in a lazy grid has been fragile before, and is only provable on the TV. Compose focus code has no automated tests.
  - open-risk: the TV check depends on the window in the issue's comment (until about 12:20 CET on 2026-10-30) and on the shared lock; outside the window it is skipped and recorded.
  - open-risk: the wait before a show's first photo on a very large library can't be measured here, as this library has 10 months.

## Implementation

- Shipped: the library from Immich (`ImmichLibraryRepository.kt`, with the shared `ImmichApi.kt` and `ImmichTimelineDtos.kt`); the Photos tab, rail and grid in step (`PhotosScreen.kt`, `MonthRail.kt`, `PhotoGrid.kt`, `PhotoGridViewModel.kt` `gridIndexOfMonth`); the full-screen photo (`PhotoViewerScreen.kt`, `PhotoGridViewModel.kt` `viewerMove`); the slideshow from a photo (`Slideshow.kt` `LoadLibrarySlideshow`, `SlideshowViewModel.kt` `fromTimeline`, hold OK in `PhotoTile.kt`, `KeyHint.kt`); the manual (`docs/design/README.md`).
- Tests: 298 passed / 0 failed, with `koverVerifyDebug`, `lintDebug` (0 errors, 23 warnings) and `assembleDebug` green.
- Outstanding: nothing unticked. Nothing here has run on the TV or against the real server: focus entering the rail on the selected month, focusing a far tile after a jump and on return, and hold OK are only provable on the TV (self-verification). The follow-up ticket for OK on the album grid (Q7.1) is still to be raised.
- Contradicted the plan: `inline` in an internal class needed `internal` members, not `@PublishedApi` (it refused `decode` reading `immichJson`); checklist corrected. A library show would have said "‹ Album" and "Back to album", so `SlideshowScreen` gained `source: SlideshowSource`, with three strings; checklist corrected. `refresh()` rebuilt the state from scratch, which would have lost the focused month and the viewed photo, so it now copies. `KeyHint`'s 32dp bottom margin was not in the checklist.

## Review

- Fixed: a press waiting for a month survived leaving the viewer: at a month's last photo, right then Back before the month arrived moved `viewing`, so the grid's place changed behind the remote (`PhotoGridViewModel.viewerClosed`, tested). The first month arriving pulled focus back to the first tile when the remote had already moved onto a tile of it (`PhotoGrid.kt`, the first-focus guard; Compose focus code has no automated tests, so this one has none and goes to the TV check). The same arrival can still pull focus from the rail if Left is pressed within that first load: rare, left as is.
- Raised: https://github.com/YasarMahomedAbbas/immich-tv/issues/22 (finished slideshows stay in memory); https://github.com/YasarMahomedAbbas/immich-tv/issues/23 (OK on an album's grid, Q7.1).
- API: `GET /api/timeline/buckets` and `GET /api/timeline/bucket` with `visibility=timeline`, `withStacked=true`, `order=desc`: match docs and schema in the specs at v1.133.0 and v3.3.5, and the live v3.3.5 server (every column as long as `id`; the extra `stack` column is ignored). `timeBucket` sent back as received. Header on every call, tested.
- Layers: clean
