# Checklist — feat: Continue, random start and a Shuffle button on album slideshows

Issue: #43 — feat: Continue, random start and a Shuffle button on album slideshows (https://github.com/YasarMahomedAbbas/immich-tv/issues/43)
Branch: `feat/43-continue-random-start-shuffle`
Problem: an album's slideshow always starts from the same photo, and shuffling it means changing the saved Order in the settings row.

## Done when

- **Continue**
  - Leaving an album's slideshow (Back, or the activity going) remembers the slide on screen for that album. It is stored like the album's slideshow settings: a domain interface, with the storing in `data/`.
  - When the album has a remembered spot, the main button is **Continue** and gets the first focus. Beside it, **Start slideshow** is relabelled **Start over** and plays from the beginning; that show's spot replaces the remembered one. With no remembered spot, the screen looks as it does today.
  - Continue resumes in the album's current Order at the remembered photo. A show that was shuffled resumes the *same* shuffled sequence: the seed is kept with the spot.
  - A remembered photo that is no longer in the album, or is skipped because Videos is now Skip, resumes at the next one that is, or at the start if there is none. Never an error.
  - Starting a show from a grid tile, the Photos tab, memories, search, the screensaver or Home's hero doesn't touch the album's remembered spot.
- **Random start**: plays from a random slide of the album, in its Order, wrapping round to the start at the end as the show does today.
- **Shuffle**: plays the album shuffled for this show only, without changing the Order setting. Its spot is remembered for Continue like any other show.
- The layout follows `design-recipe` and `docs/design/`: Random start and Shuffle are compact secondary buttons on the settings heading line (decision Q1-1).
- Tests: the start index for random start (seeded `Random`), Continue's resume in each Order including the same shuffle, the missing-photo fallback, and the stored spot round trip.

## References

- Immich API: no new or changed call. The ticket reuses `AlbumRepository.slides(album)` (`GET /api/albums/{id}` plus the timeline reads) exactly as it stands. Nothing in `data/immich` changes.
- Design manual: `docs/design/README.md`, Album row, Buttons (primary, secondary, compact), rules 4, 7, 9. No canvas: this ticket isn't labelled `design`. Build the screen with `design-recipe`.
- Prior work: #4 (album screen and settings storage), #5 (the slideshow), #23 (`playlistFrom`, the fallback for a skipped item).

## Context

The album screen (`AlbumScreen`/`AlbumViewModel`) starts `AppScreen.Slideshow(album, settings, cameFrom)`. `SlideshowViewModel.factory` builds it from `LoadSlideshow(album, order, videos)`, which today shuffles unseeded and always starts at 0. Per-album settings live in `StoredSlideshowSettings`, behind `SlideshowSettingsStore`. A new `ResumePointStore` mirrors that pair and uses the same `slideshow` SharedPreferences.

`LoadSlideshow` gains a `ShowStart` (Beginning, Continue, RandomSlide, Shuffled) and returns an `AlbumShow`: the playlist plus the shuffle seed, if any. A shuffled show shuffles the *whole* album with `Random(seed)` *before* the Videos filter, so changing Videos keeps the sequence. `SlideshowViewModel` reports each slide it lands on, and the album show saves it as the spot. Saving on every slide (and at the start) covers Back, the activity going and a process kill alike.

Only shows started from the album screen's buttons record a spot. A Home hero show (`CameFrom.HomeHero`) doesn't (Q3-1). Continue resumes the show that was left (Q2-1): a spot with a seed resumes that seeded shuffle whatever the Order is now. A spot without one plays in the current Order, which is a fresh shuffle if the Order is now Shuffle.

On the album screen, the first button slot stays the one `PrimaryButton` with the start focus requester. Its label and action switch between Start slideshow and Continue, and Start over is inserted after it. If the spot arrives after the first frame, focus is still on that slot. All decisions are in NOTES.

## Test plan

- Unit:
  - `StoredResumePoints`: round trip with and without a seed, missing, unreadable, a photo id containing `|`.
  - `LoadSlideshow`:
    - Beginning in each Order.
    - RandomSlide: start = a seeded `Random`'s `nextInt(kept.size)`, in each Order; an empty album starts at 0.
    - Shuffled: shuffled with Order OldestFirst, and the seed returned.
    - Continue: in OldestFirst and NewestFirst at the photo. A seeded spot gives the same sequence as the show that saved it, even with Order now OldestFirst, and with Videos changed from muted to Skip. A seedless spot with Order Shuffle gives a fresh seed, starting at the photo.
    - Fallbacks: a photo now skipped as a video resumes at the next kept slide; a skipped photo with nothing kept after it starts at 0; a photo gone from the album uses its stored index, clamped, or 0 past the end; no spot starts at 0.
    - A failure passes through.
  - `AlbumShow.resumePointAt`.
  - `SlideshowViewModel`: `onSlide` on Playing, on each skip and timed move, and when the current slide is dropped; not on Failed or Empty.
  - The album show factory's remembering, through `AppViewModel`: Back from a `Slideshow` with any `ShowStart` still goes to the album.
  - `AlbumViewModel`: `canContinue` from the store at init and after `refresh()`, and false when the store has none.
  - `AlbumLayoutTest` still passes.
- MockWebServer: none new: no call changes.
- Real server: read-only `GET /api/server/version`, `GET /api/albums` and `GET /api/albums/{id}` for an album with a video. This confirms that the slides `LoadSlideshow` orders come back as they did (no shape change is expected).
- On the TV:
  - Open an album: no Continue at first (or after clearing the spot), and the screen as today.
  - Start slideshow, skip three, Back. Continue is focused, Start over is beside it, and Continue resumes on that photo.
  - Shuffle, skip, Back, Continue: the same shuffled sequence (note the photos either side). The Order setting still reads its old value.
  - Random start lands somewhere other than the first photo (try twice).
  - Start over begins at the first photo.
  - A hold-OK grid show and a Home hero play don't change the spot.
  - D-pad Up and Down through the main row, Random start, Shuffle and the settings. Every focus outline is visible and the strip stays on screen.
  - Check the main row with Continue, Start over and Browse photos fits on one line in the 760dp column: the open risk.

---

## Checkpoint 1 — The remembered spot · commit: `feat(slideshow): remember where an album's slideshow stopped`

Goal: an album's spot can be stored and read back, tested, with no Android.

### [ ] 1.1 — The domain

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/slideshow/ResumePoint.kt` (new)

- `enum class ShowStart { Beginning, Continue, RandomSlide, Shuffled }`, a KDoc per value.
- `data class ResumePoint(val photo: PhotoId, val index: Int, val shuffleSeed: Long?)`:
  - `index` is its position in the show that saved it, the fallback when the photo is gone;
  - `shuffleSeed` is non-null when that show was shuffled.
- `interface ResumePointStore { suspend fun load(album: AlbumId): ResumePoint?; suspend fun save(album: AlbumId, point: ResumePoint) }`
- `class GetResumePoint(private val store: ResumePointStore)` and `class SaveResumePoint(private val store: ResumePointStore)`, mirroring `GetSlideshowSettings` and `SaveSlideshowSettings`.

Fake: add `FakeResumePointStore` (a map, recording saves) to `app/src/test/java/.../domain/slideshow/Fakes.kt`.

### [ ] 1.2 — The stored spot

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/data/settings/StoredResumePoints.kt` (new), mirroring `StoredSlideshowSettings`.

- `internal class StoredResumePoints(private val storage: StringStorage) : ResumePointStore`.
- Key `resume.<albumId>`. Value `index|seed|photoId`, with the seed empty when null, split with `limit = 3` so a `|` in the id survives.
- Missing or unreadable (index not a non-negative int, seed neither empty nor a long, empty id) is `null`. IO goes on `Dispatchers.IO`.

Test: `app/src/test/java/.../data/settings/StoredResumePointsTest.kt`, with `MemoryStorage`.

### [ ] 1.3 — Wiring

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/AppContainer.kt`
`private val resumePoints = StoredResumePoints(slideshowStorage)`; `val getResumePoint = GetResumePoint(resumePoints)`; `val saveResumePoint = SaveResumePoint(resumePoints)`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*StoredResumePointsTest' --tests '*ArchitectureTest'`

---

## Checkpoint 2 — How an album show begins · commit: `feat(slideshow): continue, random start and one-off shuffle for album shows`

Goal: `LoadSlideshow` builds the show for each `ShowStart`, tested with seeded `Random`s.

### [ ] 2.1 — `AlbumShow` and the new `LoadSlideshow`

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/slideshow/Slideshow.kt`

- `data class AlbumShow(val playlist: Playlist, val shuffleSeed: Long?)` with `fun resumePointAt(index: Int): ResumePoint?`. It returns the point for `playlist.slides[index]` with `shuffleSeed`, or null when the index is out of range.
- `class LoadSlideshow(private val repository: AlbumRepository, private val resumePoints: ResumePointStore, private val random: Random = Random.Default)`.
  - `suspend operator fun invoke(album: Album, order: SlideshowOrder, videos: VideoMode, start: ShowStart = ShowStart.Beginning): ServerResult<AlbumShow>`.
  - For `Continue`, the spot is `resumePoints.load(album.id)` (read before the server call).
  - Seed:
    - `Continue` with a seeded spot: that spot's seed.
    - Otherwise, when `start == Shuffled` or `order == Shuffle`: `random.nextLong()`.
    - Else null.
  - `ordered` (all items, videos included):
    - with a seed: `all.shuffled(Random(seed))`;
    - otherwise, by order: OldestFirst is `all`, NewestFirst is `all.reversed()`.
  - `kept`: `ordered.withoutVideos()` when `videos == Skip`, else `ordered`.
  - Start index:
    - Beginning and Shuffled: 0.
    - RandomSlide: `if (kept.isEmpty()) 0 else random.nextInt(kept.size)`.
    - Continue with no spot: 0.
    - Continue with the spot's photo in `ordered`: `playlistFrom(ordered, kept, photo).start`.
    - Continue with the photo gone: `spot.index` if it is in `kept.indices`, else 0.
- Keep `playlistFrom` as is.

### [ ] 2.2 — Callers

- `AppContainer`: `LoadSlideshow(albums, resumePoints)`.
- `SlideshowViewModel.factory(container, album, settings)`: for now, call with `ShowStart.Beginning` and map to `it.playlist`, so it compiles. Checkpoint 3 replaces this.

Test: `app/src/test/java/.../domain/slideshow/LoadSlideshowTest.kt`. Update the existing tests to read `.value.playlist.slides`, then add the cases in the test plan's `LoadSlideshow` items. The same-shuffle test saves `resumePointAt(i)` from a Shuffled show into the fake store, then loads `Continue` with Order OldestFirst and a fresh `Random`. It asserts the same slide list and `start == i`, and that the result's `shuffleSeed` equals the first show's.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*LoadSlideshowTest'`

---

## Checkpoint 3 — The show reports where it is · commit: `feat(slideshow): save the album's spot as its slideshow plays`

Goal: an album-screen show saves the slide on screen, other shows don't, and the start mode reaches the show.

### [ ] 3.1 — `SlideshowViewModel.onSlide`

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/slideshow/SlideshowViewModel.kt`

- Add the constructor parameter `private val onSlide: suspend (index: Int, slide: Slide) -> Unit = { _, _ -> }`. It goes between `settings` and `loadPlaylist`, so existing trailing-lambda calls still compile.
- In `init`, collect `state` filtered to `load == Playing` and mapped to `index to current`, with `distinctUntilChanged()` on the photo id plus the index. For each, call `onSlide`, wrapped as `withContext(NonCancellable)`, so a save under way finishes when the show store is cleared.
- Tests in `SlideshowViewModelTest`: the cases in the test plan's `SlideshowViewModel` item.

### [ ] 3.2 — The album show remembers

- `ui/slideshow/SlideshowViewModel.kt`: `factory(container: AppContainer, album: Album, settings: SlideshowSettings, start: ShowStart, remember: Boolean)`.
  - The loader calls `container.loadSlideshow(album, settings.order, settings.videos, start)`, keeps the `AlbumShow` in a local `var`, and returns `it.playlist`.
  - When `remember`, `onSlide` saves `ResumePoint(slide.photo.id, index, show.shuffleSeed)` through `container.saveResumePoint(album.id, …)`.
- `ui/AppViewModel.kt`: `AppScreen.Slideshow` gains `val start: ShowStart = ShowStart.Beginning`. `openSlideshow(album, settings, cameFrom = AlbumsTab, start: ShowStart = ShowStart.Beginning)`. `back()` is unchanged.
- `MainActivity.kt`: the `AppScreen.Slideshow` branch passes `current.start` and `remember = current.cameFrom != CameFrom.HomeHero` (Q3-1). The Home hero's `onPlayAlbum` stays on `Beginning`.
- Tests in `AppViewModelTest`: `openSlideshow` with `ShowStart.Shuffled` carries it in the screen, and Back still goes to the album.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*SlideshowViewModelTest' --tests '*AppViewModelTest'`

---

## Checkpoint 4 — The album screen's buttons · commit: `feat(albums): Continue, Start over, Random start and Shuffle on the album screen`

Goal: the four ways to start are on the album screen, reachable with the D-pad, and the manual describes them.

### [ ] 4.1 — `AlbumViewModel`

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumViewModel.kt`

- `AlbumState` gains `val canContinue: Boolean = false`.
- The constructor gains `getResumePoint: GetResumePoint`, and the factory passes `container.getResumePoint`.
- `init` reads the settings and the spot in one launch.
- `refresh()` also launches a re-read of the spot, before its early returns, so returning from a show updates Continue even while the album load is busy.
- Tests in `AlbumViewModelTest`: the cases in the test plan's `AlbumViewModel` item.

### [ ] 4.2 — `AlbumScreen`

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumScreen.kt`. Use `design-recipe`.

- `onStartSlideshow: (Album, SlideshowSettings, ShowStart) -> Unit` through `AlbumScreen`, `AlbumContent` and `LoadedAlbum`. `MainActivity` calls `app.openSlideshow(album, settings, current.cameFrom, start)`.
- Main row:
  - The `PrimaryButton` (play icon, `startFocus`) is Continue when `canContinue` (`ShowStart.Continue`), else Start slideshow (`Beginning`).
  - When `canContinue`, a `SecondaryButton` Start over (`Beginning`) follows it.
  - Then Browse photos, as now.
  - Focus enters on the primary slot, as now.
- The settings heading becomes a `Row` (full width, centred vertically): the heading text with `weight(1f)`, then `SecondaryButton(compact = true)` Random start (`RandomSlide`) and Shuffle (`Shuffled`), 14dp apart. The row's top padding drops from 28dp to 8dp so the column's height stays about the same; the focus outline needs 8dp, which the column's 16dp spacing gives.
- Strings in `app/src/main/res/values/strings.xml`: `album_continue` "Continue", `album_start_over` "Start over", `album_random_start` "Random start", `album_shuffle` "Shuffle".
- The skeleton is unchanged: it already stands for two main buttons.
- Previews: add a "can continue" preview.
- `AlbumLayoutTest` stays green.

### [ ] 4.3 — The manual

File: `docs/design/README.md`, Album row: "Start slideshow (Continue and Start over when the album has a remembered spot, Continue focused), Browse photos, slideshow settings with Random start and Shuffle as compact buttons on their heading line, in two rows of two".

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`
- The album previews render (lint/compile). The real fit is checked on the TV in self-verification.

---

## Out of scope

- Continue, Random start or Shuffle anywhere but the album screen (Home hero, grids, Photos, memories, search, screensaver).
- Any change to the Order setting's choices, or to a show started from a tile.
- Showing on the album screen which photo the spot is, or clearing a spot by hand.
- Syncing spots across devices or with Immich.
