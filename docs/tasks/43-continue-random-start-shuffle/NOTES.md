# Notes — feat: Continue, random start and a Shuffle button on album slideshows

Issue: #43 — feat: Continue, random start and a Shuffle button on album slideshows (https://github.com/YasarMahomedAbbas/immich-tv/issues/43)
Branch: `feat/43-continue-random-start-shuffle`

## Planning

- Approach: `LoadSlideshow` takes a `ShowStart` (Beginning, Continue, RandomSlide, Shuffled) and returns the playlist plus the shuffle seed. Every slide an album-screen show lands on is saved as the album's `ResumePoint` (photo, index, seed) behind a domain store kept in `data/settings`. The album screen offers Continue and Start over when there is a spot, and compact Random start and Shuffle buttons on the settings heading line. No Immich call changes.
- Decided (pipeline, on the user's behalf; flag: ux): Q1-1, Random start and Shuffle are compact secondary buttons, right-aligned on the "Slideshow settings" heading line. The extra height is paid for by trimming that line's top padding. Rejected: a row of compact buttons under the main row (Q1-2: needs about 63dp, so the hint line would go and the mosaic would shrink); two icon-only circles at the end of the main row (Q1-3: they don't fit in 760dp and rely on icons alone).
- Decided (pipeline, on the user's behalf; flag: ux): Q2-1, Continue resumes the show that was left. A shuffled one (by Order or by the button) resumes its same seeded sequence; an ordered one plays in the album's current Order. Rejected: strictly the current Order, with the seed used only while the Order is Shuffle (Q2-2).
- Decided (pipeline, on the user's behalf; flag: ux): Q3-1, only shows started from the album screen's buttons record a spot. Home's hero "Play slideshow" doesn't, as Home offers no Continue. Rejected: any album show from its start records (Q3-2).
- Decided (pipeline, on the user's behalf; flag: ux applies to the set), the "decided myself" list:
  - The spot is saved on every slide change and at the start, which covers Back, the activity going and a process kill.
  - It is kept per album under its own key (`resume.<id>`) beside the slideshow settings, behind a domain interface: photo id, index, and the seed when shuffled.
  - A seeded shuffle shuffles the whole album before the Videos filter, so changing Videos keeps the sequence.
  - A photo still in the album but skipped resumes at the next kept one, or at the start.
  - A photo gone from the album resumes at its old index, clamped, or at the start.
  - Random start is a seeded `nextInt` over the kept slides.
  - With a spot, Continue (the primary slot) gets first focus, and coming Back lands on it.
  - The album screen re-reads the spot whenever it comes back into view.
- Decided while writing the checklist (not asked):
  - The primary button slot keeps the focus requester and switches label, so a spot read after the first frame doesn't move focus.
  - The save runs `NonCancellable`, so clearing the show store on Back doesn't cut it short.
  - Settings heading top padding goes from 28dp to 8dp to pay for the compact buttons' height; the hint line stays.
- Open risks:
  - Continue + Start over + Browse photos come to about 746 of 760dp in the info column, so very tight. Self-verification checks it on the TV; if it doesn't fit, that's a re-plan, not a guess.
  - The info column's vertical fit with the heading row is estimated, not measured. Check on the TV that the strip and its focus outline stay on screen.
- Plan approved: by the pipeline on the user's behalf, including the choices above; the user has not reviewed it.

## Implementation

- Shipped:
  - The remembered spot: `ShowStart`, `ResumePoint`, `ResumePointStore` and its use cases (`domain/slideshow/ResumePoint.kt`), stored as `resume.<id>` = `index|seed|photoId` by `StoredResumePoints` (`data/settings/StoredResumePoints.kt:15`), wired in `AppContainer`.
  - `AlbumShow` with `resumePointAt` (`domain/slideshow/Slideshow.kt:35`) and `LoadSlideshow` taking a `ShowStart` (`Slideshow.kt:50`): a seeded shuffle over the whole album before the Videos filter, Continue via `playlistFrom`, the gone-photo fallback to the stored index or 0.
  - `SlideshowViewModel` reports every slide a playing show lands on through `onSlide`, NonCancellable (`ui/slideshow/SlideshowViewModel.kt:99`); the album factory saves it when `remember` (`SlideshowViewModel.kt:282`), and `MainActivity.kt:184` passes `remember = false` for a Home hero show. `AppScreen.Slideshow` carries `start` (`ui/AppViewModel.kt:83`).
  - `AlbumState.canContinue`, read at init and on every `refresh()` before its early returns (`ui/album/AlbumViewModel.kt:59`, `:86`).
  - Album screen: the primary slot switches between Continue and Start slideshow, Start over follows it when there is a spot, Random start and Shuffle are compact buttons on the settings heading row with its top padding cut from 28dp to 8dp (`ui/album/AlbumScreen.kt:231`, `:263`); a "can continue" preview; four strings; the manual's Album row updated.
- Tests: full CI command green: 549 passed / 0 failed / 0 skipped; koverVerifyDebug, lintDebug and assembleDebug passed. New or changed: `StoredResumePointsTest` (6), `LoadSlideshowTest` (26), `SlideshowViewModelTest` (+5 onSlide), `AppViewModelTest` (+1), `AlbumViewModelTest` (+4).
- Outstanding: none of the checklist's tasks. Still for self-verification on the TV: the test plan's device steps, including whether Continue + Start over + Browse photos fit one line in the 760dp column, and whether the heading row keeps the strip on screen. The factory's save path is covered only through its parts (it needs an `AppContainer`): `onSlide` is unit-tested, `remember` is a one-line flag in `MainActivity`.
- Contradicted the plan: none.

## Review

- Fixed: none. Checked and kept as built: the album factory sets the show's seed before the first Playing state, so the first save carries it. The NonCancellable save finishes after the show store is cleared. Only the hero passes `CameFrom.HomeHero`, so every album-screen show remembers. The spot is re-read through `RefreshOnStart` before `refresh()`'s early returns. The primary slot keeps `startFocus` whichever label it shows. A dropped (failed) slide shifts the saved index, but the index is only the fallback for a photo gone from the album, so the photo id still decides.
- Raised: none
- API: no Immich call touched (`AlbumRepository.slides` reused unchanged; nothing in `data/immich` changed)
- Layers: clean (`ShowStart`, `ResumePoint`, `ResumePointStore` in domain words; the `index|seed|photoId` encoding stays in `data/settings`)

## Doc drift

- Applied: README.md:Albums feature bullet — the album screen's start choices (beginning, random photo, one-off shuffle, Continue from the remembered spot on this device); rung 3, a user-facing feature the feature list would otherwise omit.
- Applied (during implementation): docs/design/README.md:Album row — Continue/Start over and the compact Random start/Shuffle buttons; checked against `AlbumScreen.kt`, still accurate.
- Skipped: the `resume.<id>` key and `index|seed|photoId` encoding — documented in `StoredResumePoints`' KDoc, stays in `data/`, one `rg` finds it.
- Skipped: Home hero shows don't remember a spot — the comment at the `remember =` line in `MainActivity` says so; the design Album row already names Continue as album-screen only.
- Skipped: the NonCancellable save outliving the show store's clearing — the code and `SlideshowViewModelTest` carry it; README's `AppViewModel.shows` paragraph ("each show loads anew") is still true.
- Skipped: README architecture block ("data/ … stored settings") and CLAUDE.md — still accurate; no new layer, command, permission or Immich call.
- Stale, fixed separately: none

## Self verification

Result: partial

- Decision (user, 2026-11-23, issue comment): skip the TV checks and ship with verification marked partial; the outstanding on-TV checks below go in the PR as hand-check items.
- CI build: full CI command in Docker green: 549 tests passed / 0 failed / 0 skipped, line coverage 89.6% (bound 80%), lint clean, debug APK built.
- Server: Immich 3.3.6
- TV: not run (2026-11-21 re-run in the TV window the user granted on the issue: the debug APK built, but `adb connect` to the Chromecast was refused for 5 minutes (20 tries, 15 s apart). The TV was on the network and answering on its cast ports 8008/8009/8443, but nothing listened on adb's 5555: network debugging is off on the TV and can't be turned on from here. Earlier window: the TV was shared and the pipeline's 2026-10-30 window had ended.) Code unchanged since the CI run above, so CI was not re-run.
- Verified:
  - No Immich call changed (`data/immich` untouched; the only `data/` change is `StoredResumePoints`, local storage, covered by `StoredResumePointsTest`).
  - Read-only against the real server, built as the code builds them: `GET /api/server/version`; `GET /api/albums` (15 albums, the fields `AlbumDto` reads present); `GET /api/albums/{id}` on a 1962-item album with videos (order `desc`); `GET /api/timeline/buckets?albumId=…&order=asc` (19 months, counts sum to 1962) and `GET /api/timeline/bucket?albumId=…&timeBucket=…&order=asc` for every month: `id` and `isImage` columns match each month's count, 1511 images and 451 videos. The slides `LoadSlideshow` orders, shuffles and resumes over come back in the shape `BucketAssetsDto` reads, so no shape change.
- Failed: none
- Not self-verifiable (check on the TV by hand, after turning on Developer options → Network debugging so adb can reach it):
  - Open an album with no spot: no Continue, the screen as before.
  - Start slideshow, skip three, Back: Continue focused (amber outline), Start over beside it; Continue resumes on that photo.
  - Continue + Start over + Browse photos fit one line in the 760dp info column (estimated about 746dp, the plan's open risk); if not, that is a re-plan.
  - The settings heading row with Random start and Shuffle keeps the strip and its focus outline on screen (vertical fit estimated, not measured).
  - Shuffle, skip, Back, Continue: the same shuffled sequence; the Order setting keeps its old value.
  - Random start lands away from the first photo (twice); Start over begins at the first photo.
  - A hold-OK grid show and a Home hero play leave the spot unchanged; no crash in logcat.
