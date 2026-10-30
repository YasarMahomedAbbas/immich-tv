# Notes — feat: the slideshow

Issue: #5 — feat: the slideshow (https://github.com/YasarMahomedAbbas/immich-tv/issues/5)
Branch: `feat/5-the-slideshow`

## Planning

- Approach: `AlbumRepository.slides` reads the album's months once from the timeline columns, `LoadSlideshow` drops videos and orders them, `SlideshowViewModel` owns timing and remote actions, and `SlideshowScreen` draws the crossfade, motion and overlays with Coil preloading the next preview.
- Decided (pipeline, on the user's behalf; flag: ux): Q1.1, after the last photo the show loops to the first in the same order. Rejected: return to the album (Q1.2), reshuffle each pass (Q1.3).
- Decided (pipeline, on the user's behalf; flag: ux, api): Q2.1, read every month once at the start and hold a light list. Rejected: month by month while playing (Q2.2), which breaks shuffle and the progress bar.
- Decided (pipeline, on the user's behalf; flag: api, open-risk): Q3.1, date, place and shape come from the month listing's columns. Rejected: fetching each photo's details (Q3.2), which adds state/region at one more call per photo. Dates need both shapes: `localDateTime` to v1.134, `fileCreatedAt` + `localOffsetHours` from v1.135.
- Decided (pipeline, on the user's behalf; flag: ux), the "also decided" list:
  - Controls show on any key and fade after 4 s, also while paused. They are indicators without focus: left/right always skip, OK always pauses.
  - Pausing freezes the timer and the zoom; skipping while paused stays paused.
  - The screen stays awake only while playing, so a long pause may let the TV's screensaver start.
  - The Videos setting is ignored until the video ticket, so "Play muted" has no effect yet.
  - Portrait backdrop is the same bitmap blurred and darkened 42%; below Android 12 only darkened.
  - The progress bar shows position in the album, off-white instead of the canvas's amber (rule 3).
  - The canvas's clock stays, though the issue doesn't list it.
  - A photo that won't load is skipped; 5 failures in a row shows the failed state. If the next isn't ready the current stays. At most 3 bitmaps: previous, current, next.
  - An album with no photos shows an empty state with a way back.
- Open risks:
  - open-risk: place can't be seen working on the real server. None of its 2,388 album items has a city or country, so place is proven only in tests; a photo without one shows the date alone.
  - Blur on the Chromecast during a crossfade may cost frames; checked on the TV.
  - `PreviewInterceptor` sets one memory-cache key per photo whatever the size, so a small grid bitmap under that key must not be accepted for the full screen; checked on the TV for sharpness.

## Implementation

- Shipped: `slides` and the date, place and shape columns (`data/immich/ImmichAlbumRepository.kt:105`, `:310`, `:337`); `LoadSlideshow` (`domain/slideshow/Slideshow.kt`); the timer and remote actions (`ui/slideshow/SlideshowViewModel.kt:76`, `:119`); the screen (`ui/slideshow/SlideshowScreen.kt:114`, preload `:249`, crossfade `:277`); `AppScreen.Slideshow`; `OverPhotoPanel` and `ClockStyle` in `ui/components/`; the manual's Slideshow row.
- Tests: 215 passed / 0 failed / 0 skipped; `koverVerifyDebug`, `lintDebug` and `assembleDebug` pass.
- Outstanding: the "Seen working on the TV" line under 3.2 is unticked: nothing about how the screen looks or behaves has been seen, and `@Preview`s were compiled, not rendered. Real-server checks are also still to do. Both are self-verification's.
- Contradicted the plan: none disproved it, but these differ from its text and the checklist now says so: the new column items are nullable, because the album screen decodes the same DTO; a photo's time waits for its own image, not only the next's; the screen's ground and the backdrop's darkening use `bg`, not pure black, per rule 2; a new `OverPhotoPanel` component holds the panels.
- For review: each start keeps its `SlideshowViewModel`, and so its light slide list, in the activity's store until the app closes, the same pattern as Setup's. A skip during a fade shows the fading photo at once rather than finishing its fade.
