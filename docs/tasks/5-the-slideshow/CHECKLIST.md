# Checklist — feat: the slideshow

Issue: #5 — feat: the slideshow (https://github.com/YasarMahomedAbbas/immich-tv/issues/5)
Branch: `feat/5-the-slideshow`
Problem: Start slideshow on the album screen does nothing, so the thing the app exists for, watching an album as a calm slideshow, can't be done.

## Done when

- Starting from the Album screen plays the album's photos full-screen, honouring the album's settings (time per photo, oldest first / newest first / shuffle, show date & place).
- Photos crossfade (about 1.5 s), and each one zooms and pans slowly while it's on screen.
- Portrait photos sit centred on a blurred, darkened copy of themselves; landscape photos fill the screen.
- The next photo is loaded before it's needed, so a transition never waits; at most a few bitmaps are held at once.
- Date and place (from the photo's metadata) bottom-left when enabled; a thin progress bar on top.
- Remote: left/right go back/forward, OK or play/pause pauses (the controls show and fade after a few seconds), Back returns to the album.
- The screen doesn't sleep while it plays.
- Videos are skipped in this ticket.

## References

- Immich API: `GET /api/timeline/buckets` and `GET /api/timeline/bucket` with `albumId`, `order=asc` (and `timeBucket`), permission `asset.read`, already used by `ImmichAlbumRepository`. New columns read from `TimeBucketAssetResponseDto`, all parallel arrays:
  - `ratio` (width/height, already corrected for rotation on 3.3.5), `city`, `country` (nullable items): v1.133 onward.
  - Date, v1.133–v1.134: `localDateTime`. v1.135 onward: `fileCreatedAt` (UTC, without zone in v2.0 and v3.3.5) plus `localOffsetHours` (may be fractional). Checked in the specs at v1.133.0, v1.134.0, v1.135.0–v1.144.0 (sampled), v2.0.0 and v3.3.5.
  - Live server is v3.3.5. `2025-08-23T13:06:48.312` + `2` = the asset's `localDateTime` 15:06:48.
- Image: `GET /api/assets/{id}/thumbnail?size=preview` through the existing `Preview` and `PreviewInterceptor`.
- Design: canvas https://claude.ai/artifact/8Tq2kuJDNjk3SFiALwbfZH, board "3 · Slideshow"; `docs/design/README.md` § Screens.
- Follow-ups: the video ticket uses the Videos setting and the `isVideo` kept on a `Slide`.

## Context

The album screen holds the loaded `Album` and `SlideshowSettings`; Start slideshow is a no-op. Mirrors: `ImmichAlbumRepository.contents` (bounded parallel bucket reads) and its MockWebServer test; `AppScreen.AlbumPhotos` and its `back()` for navigation; `PhotoGridViewModel` and `AlbumViewModel` for ViewModel style; `viewModel(key = identity)` from Setup for a fresh ViewModel per start. Decisions (taken by the pipeline on the user's behalf, see NOTES): the show loops in the same order; the whole album is read once at the start, oldest first, and ordered on the device; date, place and shape come from the bucket columns with no per-photo call. `AppScreen.Slideshow` carries the settings from the album screen rather than re-reading them, so a change just made can't lose a race with its save. Activity-scoped ViewModels outlive the screen, so the timer is a suspend function the screen runs while started: it stops on Back and when the app is left. "Ready" is reported by the screen from Coil, keeping the ViewModel free of Android and testable with virtual time.

## Test plan

- Unit: `LoadSlideshow` (drops videos, oldest/newest/shuffle with a seeded `Random`, failures pass through); `SlideshowViewModel` with virtual time (loading → playing/empty/failed, retry; advances after `secondsPerPhoto`; waits while the next isn't ready; wraps both ways; skip restarts the time; pause holds the time and resumes the remainder; skip while paused stays paused; a failed photo is skipped; 5 in a row fails; controls show on a key and hide after 4 s, also while paused; `next` and `prev` slides for preloading; no timer after `play()` is cancelled); `AppViewModel` (open slideshow, Back → album with Start focused); `dateAndPlace` formatting (both, date only, place only with city or country alone, neither, locale); `slideMotion` per index; `actionFor(key)`.
- MockWebServer: `slides` per version shape: v3 (`fileCreatedAt` + `localOffsetHours`, including fractional and day-crossing), v1.133 (`localDateTime`), a `fileCreatedAt` ending in `Z`, unusable date → null date, missing `ratio` → not portrait, null `city`/`country`, requests carry `order=asc`, the header and the sent bucket, oldest month first, one failed month fails it, 401/403 mapped, empty album.
- Real server: `/timeline/buckets` and `/timeline/bucket` with `order=asc` for each album on v3.3.5: every column array as long as `id`, dates equal `localDateTime` on a sample of assets, portrait flag matches `width`/`height`. Place: 0 of 2,388 items had `city` or `country` at planning; re-count, and record that place was only seen in tests if still zero.
- On the TV (shared with the household; installs interrupt whoever is watching, so follow self-verification's rules): Album → Start slideshow plays; oldest, newest and shuffle orders differ; 5 s setting timed; crossfade and motion across screenshots; a portrait photo on its backdrop; a landscape one fills; Date & place off hides the panel; left/right skip, wrapping at both ends; OK pauses and resumes; play/pause key via `adb shell input keyevent 85`; controls fade; progress bar moves; an album's videos never appear; Back → album with Start slideshow focused; `dumpsys power` / `SurfaceFlinger` show screen-on held while playing; memory with `dumpsys meminfo` steady over 50 photos in the largest album; no dropped-frame storm (`dumpsys gfxinfo`) during crossfade over a portrait.

---

## Checkpoint 1 — Slides from the server · commit: `feat(slideshow): read an album's slides from the server`

Goal: the domain can ask for an album's photos as slides, ordered per the settings, on both date shapes.

### [x] 1.1 — Domain

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/slideshow/Slideshow.kt` (new)

- `data class Slide(val photo: Photo, val portrait: Boolean, val takenOn: LocalDate?, val place: Place?)`
- `data class Place(val city: String?, val country: String?)`: never both null.
- `class LoadSlideshow(private val repository: AlbumRepository, private val random: Random = Random.Default)`, `suspend operator fun invoke(album: Album, order: SlideshowOrder): ServerResult<List<Slide>>`: drops `photo.isVideo`, whatever the Videos setting; `OldestFirst` as given, `NewestFirst` reversed, `Shuffle` `shuffled(random)`.

File: `…/domain/albums/AlbumRepository.kt`: add `/** Every item in [album], oldest first. */ suspend fun slides(album: Album): ServerResult<List<Slide>>`.

File: `test/…/domain/albums/Fakes.kt`: `FakeAlbumRepository` gains `var slides: ServerResult<List<Slide>> = ServerResult.Ok(emptyList())`.

Test: `test/…/domain/slideshow/LoadSlideshowTest.kt`.

### [x] 1.2 — Immich implementation

File: `…/data/immich/ImmichAlbumRepository.kt`

- `buckets` takes `order: String = album.order`; a new `columns(session, album, timeBucket, order)` is what `bucket` now maps. `slides` reads them with `"asc"`, using the same `Semaphore(MAX_IN_FLIGHT)` shape as `contents`; a failed month fails it.
- `BucketAssetsDto` gains, all defaulting to empty so an absent column reads as unknown, and with nullable items because the album screen reads this DTO too and a null must not fail it: `ratio: List<Double?>`, `city: List<String?>`, `country: List<String?>`, `fileCreatedAt: List<String?>`, `localOffsetHours: List<Double?>`, `localDateTime: List<String?>`. `fun toSlides(): List<Slide>` uses `getOrNull` per index.
- `portrait` = ratio known and `< 1`.
- `takenOn`, private top-level with a comment on the versions: `localDateTime` if present (`take(10)` parsed), else `fileCreatedAt` (a trailing `Z` removed, `LocalDateTime.parse`) plus `localOffsetHours` converted to seconds, `.toLocalDate()`. Unparseable → null for that item, never failing the show.
- `place`: blank strings count as null; both null → null.
- Update the class comment.

File: `…/AppContainer.kt`: `val loadSlideshow = LoadSlideshow(albums)`.

Test: extend `test/…/data/immich/ImmichAlbumRepositoryTest.kt` as in the test plan. Its `bucket(...)` helper stays; add one that writes the new columns.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*LoadSlideshow*' --tests '*ImmichAlbumRepository*' --tests '*ArchitectureTest*'`
- A slide list on both date shapes, one date per item, no `Immich` name in `domain/`.

---

## Checkpoint 2 — The player · commit: `feat(slideshow): the slideshow's timing and remote actions`

Goal: everything the slideshow does over time, tested with virtual time and no screen.

### [x] 2.1 — ViewModel

File: `…/ui/slideshow/SlideshowViewModel.kt` (new)

- `sealed interface SlideshowLoad { Loading; Empty; Failed(problem: Problem); Playing }`
- `data class SlideshowState(val load: SlideshowLoad = Loading, val slides: List<Slide> = emptyList(), val index: Int = 0, val paused: Boolean = false, val controlsVisible: Boolean = false)` with `current`, `next`, `previous` (each wrapping; null when no slides) and `position` (index + 1).
- `class SlideshowViewModel(album: Album, private val settings: SlideshowSettings, private val loadSlideshow: LoadSlideshow) : ViewModel()`: loads in `init`. Constants in its companion: `CONTROLS_MS = 4_000L`, `MAX_FAILURES = 5`.
- `suspend fun play()`: the timer, a loop run by the screen and cancelled with it. Counts elapsed time in 100 ms `delay` ticks, not while paused or not `Playing`, nor until the current photo's image is ready (else a slow one would be skipped unseen); at `secondsPerPhoto` it advances if `next` is ready, otherwise keeps checking.
- `fun skipForward()`, `skipBack()`: move at once, wrap, zero the elapsed time, show controls.
- `fun togglePause()`, `fun showControls()`: restart a `viewModelScope` hide job of `CONTROLS_MS`.
- `fun imageReady(photo: PhotoId)`: remembered for the next-ready check, only the latest few kept; zeroes the failure count.
- `fun imageFailed(photo: PhotoId)`: removes that slide from the list (keeping `index` on the same slide); no slides left or `MAX_FAILURES` in a row → `Failed(Problem.Unreachable)`.
- `fun retry()`: reloads; ignored while `Loading`.
- Factory `factory(container, album, settings)`.

### [x] 2.2 — Pure helpers

File: `…/ui/slideshow/SlideshowViewModel.kt`:

- `internal fun dateAndPlace(slide: Slide, locale: Locale = Locale.getDefault()): Pair<String?, String?>`: date as `d MMMM yyyy`, place as "City, Country" or whichever exists.
- `internal data class SlideMotion(val endScale: Float, val endX: Float, val endY: Float)`, `internal fun slideMotion(index: Int)`: scale 1 → 1.08, drift 1% of the screen, one of four diagonals by `index % 4`.
- `internal enum class SlideshowAction { Back, Forward, TogglePause, ShowControls }`, `internal fun actionFor(key: Key): SlideshowAction?`: left/right; `DirectionCenter`, `Enter`, `NumPadEnter`, `MediaPlayPause`, `MediaPlay`, `MediaPause` toggle; up/down show controls.

Test: `test/…/ui/slideshow/SlideshowViewModelTest.kt` as in the test plan, `SlideshowHelpersTest.kt`. Check `lintDebug` and Kover still pass the ViewModel file's new helpers.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*Slideshow*'`

---

## Checkpoint 3 — The screen · commit: `feat(slideshow): play an album full-screen`

Goal: Start slideshow plays the album on the TV, per the board.

Shape: the manual's Slideshow screen. Apply `design-recipe`.

### [x] 3.1 — Navigation

File: `…/ui/AppViewModel.kt`: `AppScreen.Slideshow(val album: DomainAlbum, val settings: SlideshowSettings)`, `fun openSlideshow(album, settings)`, `back()` → `AppScreen.Album(current.album.id)`. Test in `AppViewModelTest`.

File: `…/ui/album/AlbumScreen.kt`: `onStartSlideshow: (Album, SlideshowSettings) -> Unit` from `LoadedAlbum` with its page's album and the current settings. Update the KDoc ("it plays in #5").

File: `…/MainActivity.kt`: the new branch. ViewModel key `"slideshow-${System.identityHashCode(current)}"` (with `connection`), so each start begins at the first photo.

### [x] 3.2 — The screen (written and built; how it looks and behaves is for the TV, below)

File: `…/ui/slideshow/SlideshowScreen.kt` (new), in a full-screen `Box` on the theme's `bg`, as rule 2 keeps colours to tokens (no safe area). `SlideshowContent(state, …)` is stateless for previews.

- **Timer**: `DisposableEffect` lifecycle observer in `SlideshowScreen` runs `viewModel.play()` from ON_START to ON_STOP, with no new dependency.
- **Keys**: the root `Box` is `focusable`, takes focus on entry, `onPreviewKeyEvent` on `KeyDown` calls `actionFor`. Back stays with the activity's `BackHandler`.
- **Awake**: `LocalView.current.keepScreenOn = !paused` in an effect, cleared on dispose. Only while `Playing`.
- **Preload**: `LaunchedEffect(state.next?.photo?.id)` runs `context.imageLoader.execute(ImageRequest.Builder(context).data(Preview(id)).size(1920, 1080).scale(Scale.FILL).precision(Precision.INEXACT).crossfade(false).build())`, then `imageReady` or `imageFailed`. Likewise for `current` after a skip and for `previous`.
- **Layers**: at most two composed, the outgoing and the incoming. The incoming starts its 1500 ms alpha fade once its image is available; until then the outgoing stays. Requests as above, so at most previous, current and next are held by the slideshow; no strong reference outside the composition. Painted with a `rememberAsyncImagePainter`/`AsyncImage`, no Coil crossfade.
- **Landscape**: `ContentScale.Crop`, full screen. **Portrait**: a backdrop of the same image, `ContentScale.Crop`, inset -40dp, `Modifier.blur(30.dp)` (a no-op below Android 12), under `bg` at 42%; the photo `ContentScale.Fit` centred with the motion.
- **Motion**: an `Animatable` per slide from `slideMotion(index)`, in `graphicsLayer`, linear over `secondsPerPhoto + 1.5 s`; paused → `stop()`, resumed → the remainder. It must not jump if the slide stays on because the next isn't ready: it just stays at its end.
- **Progress**: 5dp track full width at the top, `fg` at 18%; fill `fg`, width `position / count`, animated 600 ms. Not amber (rule 3).
- **Clock**: `Clock` in `TopMenu.kt` is `internal` with a `ClockStyle.OverPhoto` variant; in a new `OverPhotoPanel` (`ui/components/`, style `Pill`; date and place use `Info`, controls `Controls`) on a `scrim` pill 96dp from the right, 54dp from the top, 22sp 700.
- **Date and place**: when `settings.showDateAndPlace` and something to show: bottom-left 96/54dp, `scrim` at 55%, 18dp corners, date 30sp 800, place 22sp 500 `fg` with a new pin icon `ic_place.xml`.
- **Controls**: `AnimatedVisibility` fade, bottom-centre 54dp, `scrim` at 80%, fully round: "‹ Album" label, previous, play/pause (76dp `fg` circle, 4dp amber outline 4dp off, always), next, "37 / 214". Indicators: nothing in it takes focus. New icons `ic_previous.xml`, `ic_next.xml`, `ic_pause.xml`.
- **States**: `Loading`: black with a `SkeletonBlock` in the date panel's place, Back works. `Empty`: `EmptyState("No photos to play", "This album only has videos, or nothing yet.", "Back to album", onBack)`. `Failed`: `FailedState`, Try again. Strings in `res/values/strings.xml`.
- `@Preview`s: landscape playing, portrait, paused with controls, no place, loading, empty, failed.
- [x] Seen working on the TV, per the test plan: for self-verification. Unit tests and the build can't see it.

### [x] 3.3 — The manual

File: `docs/design/README.md`: extend the Slideshow row (progress bar off-white and what it measures, clock, indicators only); remove the open decision on the controls fading: they fade after 4 s.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`
- The manual's rules, one by one; previews render.

---

## Out of scope

- Playing videos, and acting on the Videos setting (its own ticket).
- The screensaver.
- Slideshows from anywhere but the Album screen (Search's Play as slideshow, Home's hero).
- Reshuffling on each pass, remembering the position, a slideshow-wide settings screen.
- Reading place from anywhere but the bucket columns (no state/region, no per-photo call).
