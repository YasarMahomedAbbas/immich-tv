# Checklist — feat: browse photos by month

Issue: #7 — feat: browse photos by month (https://github.com/YasarMahomedAbbas/immich-tv/issues/7)
Branch: `feat/7-browse-photos-by-month`
Problem: The Photos tab is a placeholder, so the library can only be seen an album at a time, and a slideshow can't start from a chosen photo.

## Done when

- A month rail on the left (grouped by year) and a 6-column grid of photos per month, newest first, loaded month by month as you scroll.
- Moving down through the grid moves the rail's selection; choosing a month on the rail jumps there.
- OK opens a photo full-screen; left/right move through the timeline from there; Back returns to the grid where you were.
- Holding OK starts the slideshow from that photo onward.
- Skeleton, empty and failed states.

## References

- Immich API, all permission `asset.read`, parameters present from v1.133.0 (checked in the specs at v1.133.0, v2.0.0, v3.3.5); the live server is v3.3.5:
  - `GET /api/timeline/buckets?visibility=timeline&withStacked=true&order=desc`: `[{timeBucket, count}]`. On v3.3.5 `count` already counts a stack as one (live: 144 for 2024-12, 161 without `withStacked`; 555 and 574 in all, 10 months). `timeBucket` is `YYYY-MM-DD` on v3, a full timestamp on older servers: send back what was received, as `ImmichAlbumRepository` does.
  - `GET /api/timeline/bucket?timeBucket=…&visibility=timeline&withStacked=true&order=desc`: the columnar `TimeBucketAssetResponseDto`. Length of `id` equals the bucket's `count` on the months sampled live. Date columns differ by version (`localDateTime` to v1.134, `fileCreatedAt` + `localOffsetHours` from v1.135), already handled by `takenOn`.
  - No `albumId` and no `withPartners`: the current key lacks `partner.read`, and partners were left out (Q1.1).
- Image: `GET /api/assets/{id}/thumbnail?size=preview` through `Preview` and `PreviewInterceptor`, unchanged.
- Design: canvas https://claude.ai/artifact/8Tq2kuJDNjk3SFiALwbfZH, board "4 · Browse photos" (200dp rail, 48dp gap, section heading, hint pill). `docs/design/README.md` § Screens, Photos row. No board for the full-screen photo: this ticket adds the shape to the manual.
- Follow-ups: #6 (videos in the slideshow); a new ticket for OK on an album's grid (Q7.1).

## Context

`Timeline` (`months()`, `photos(month)`) and `PhotoGridViewModel`/`PhotoGrid` were built in #4 for this ticket, so the library is a second `Timeline`. `ImmichAlbumRepository` keeps its HTTP helpers and DTOs private; they move out so a second repository can use them. The screens mirror `AlbumPhotosScreen` (states), `AlbumsScreen` (focus moved from the menu into content), `SlideshowScreen` (remote and full-screen request). Decisions are in NOTES: the user's own timeline with stacks collapsed; the last viewed tile on Back; the whole photo fitted on the page ground with no overlays; videos as stills in the sequence; the show reads every month once and starts at the chosen photo; fixed default settings, newest to oldest, wrapping to the newest. Screens are torn down on navigation (`when (screen)` in `MainActivity`) while ViewModels live as long as the activity, so where you were in the grid is held in `PhotoGridViewModel`, not in a remembered grid state.

Shape (`design-recipe`): Photos is the manual's Photos row. Parts: `TopMenu`, a new `MonthRail`, `PhotoGrid`, `PhotoTile`, `EmptyState`, `FailedState`, `SkeletonBlock`, a new `KeyHint` pill. The full-screen photo is a new shape, named in the manual.

## Test plan

- Unit: `LoadLibrarySlideshow` (starts at the photo; at the next photo from a video; a video at the very end wraps to the first photo; a photo no longer there starts at 0; only videos gives an empty list; failures pass through). `SlideshowViewModel` starting at an index, and the existing tests through the new constructor. `railEntries` (years grouped, newest first, one year, year boundary). `gridIndexOfMonth`/`gridIndexOfPhoto` (loaded, not loaded, failed month as one row, a count that differs from the loaded list). `PhotoGridViewModel`: `open`, `viewerMove` within a month, across a month boundary into a loaded month, into a not-loaded month (stays until it arrives, then moves), over an empty month, at both ends (stays put), into a failed month (stays, tries again on the next press), grid position after moving, focused month tracking. `AppViewModel`: open viewer and timeline slideshow; Back from each returns to `Main(Tab.Photos)`.
- MockWebServer: the library timeline and slides: requests carry `visibility=timeline`, `withStacked=true`, `order=desc`, the header and the bucket string as sent (`YYYY-MM-DD` and a full timestamp); no `albumId`; months parsed; bad date → `Unreachable`; 401/403 mapped; one failed month fails the slides; empty library; the album calls unchanged after the extraction (existing tests stay green).
- Real server: read-only on v3.3.5: the two calls with these parameters; every column array as long as `id`; each month's `count` equals its `id` length across all months; newest first within and across months; no album id needed; `visibility` values all `timeline`.
- On the TV (only inside the window on the issue's comment, holding `/tmp/immich-tv-adb.lock`; otherwise skipped and recorded): Photos tab opens on the rail and grid with a focused tile; the rail shows years; moving down crosses months and moves the rail's selection; left enters the rail; OK on a far month jumps with its first tile focused; up reaches the top menu; OK opens a photo, whole and sharp, portrait and landscape; left/right cross a month boundary and stop at both ends; a video shows a still with a badge; Back focuses the last viewed tile, scrolled there; hold OK starts the show at that photo and a held key doesn't pause it; the show continues to older photos; Back returns to the starting tile; a video tile starts at the next photo; memory with `dumpsys meminfo` steady through the whole grid.

---

## Checkpoint 1 — The library from the server · commit: `feat(photos): read the whole library's timeline from the server`

Goal: the domain can ask for the library as a `Timeline` and as slides, against both bucket shapes. Album behaviour is unchanged.

### [x] 1.1 — Extract the shared Immich plumbing

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/data/immich/ImmichApi.kt` (new)

- `internal class ImmichApi(client: OkHttpClient, sessions: SessionStore)` with `suspend fun <T> signedIn(block: suspend (Session) -> ServerResult<T>): ServerResult<T>` and `suspend inline fun <reified T> get(session, path, query)`: moved as they are from `ImmichAlbumRepository`, with `decode`. `inline` in an internal class needs the members it reads to be `internal`, not `private`.
- `internal fun List<BucketDto>.toMonths(remember: (YearMonth, String) -> Unit): ServerResult<List<TimelineMonth>>`: the parsing and `DateTimeParseException` handling from `AlbumTimeline.months`.

File: `…/data/immich/ImmichTimelineDtos.kt` (new): `BucketDto`, `BucketAssetsDto`, `takenOn`, `place` moved from `ImmichAlbumRepository.kt`, made `internal`.

File: `…/data/immich/ImmichAlbumRepository.kt`: uses both; the constructor keeps `(client, sessions)` so its tests don't change.

### [x] 1.2 — Domain

File: `…/domain/photos/Library.kt` (new)

```kotlin
/** Everything on the user's own timeline, newest first. */
interface LibraryRepository {
    fun timeline(): Timeline
    /** Every item, newest first. */
    suspend fun slides(): ServerResult<List<Slide>>
}

class LibraryTimeline(private val repository: LibraryRepository) {
    operator fun invoke(): Timeline = repository.timeline()
}
```

File: `test/…/domain/photos/FakeLibrary.kt` (new): `FakeLibraryRepository(timeline: Timeline = FakeTimeline(), var slides: ServerResult<List<Slide>> = ServerResult.Ok(emptyList()))`.

Correct the `Timeline` KDoc: the library now gives one.

### [x] 1.3 — Immich implementation

File: `…/data/immich/ImmichLibraryRepository.kt` (new): `internal class ImmichLibraryRepository(client, sessions) : LibraryRepository`.

- Query for every call: `visibility=timeline`, `withStacked=true`, `order=desc`.
- `timeline()`: a `Timeline` remembering the bucket string per month, as `AlbumTimeline` does; `photos` maps `toPhotos()`.
- `slides()`: buckets, then every bucket's columns with `MAX_IN_FLIGHT = 4`, in order, `toSlides()`; one failed month fails it, as the album's does.

File: `…/AppContainer.kt`: `private val library = ImmichLibraryRepository(http, sessions)`, `val libraryTimeline = LibraryTimeline(library)`.

Test: `test/…/data/immich/ImmichLibraryRepositoryTest.kt`, mirroring `ImmichAlbumRepositoryTest`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*ImmichLibraryRepositoryTest' --tests '*ImmichAlbumRepositoryTest' --tests '*ArchitectureTest'`
- Album tests pass unchanged; the requests hold the three parameters and no `albumId`.

---

## Checkpoint 2 — The Photos tab · commit: `feat(photos): the Photos tab with a month rail and grid`

Goal: the tab shows the rail and the grid, in all states. Rail and grid are not linked yet.

### [ ] 2.1 — Rail entries

File: `…/ui/photos/MonthRail.kt` (new)

- `internal sealed interface RailEntry { data class Year(val year: Int); data class Month(val month: YearMonth) }`
- `internal fun railEntries(sections: List<MonthSection>): List<RailEntry>`: a `Year` before each run of months in that year, keeping the sections' order.
- `@Composable fun MonthRail(entries, selected: YearMonth?, onChoose: (YearMonth) -> Unit, modifier)`: a `LazyColumn`, 200dp wide. Year: 18sp 700 `mfg`, not focusable. Month: 22sp, 12dp corners, 18×12dp padding, month name in the device's locale; selected `fg` with `on-fg` 800, others `mfg`-toned 600 with no fill, house focus outline. 18sp+ throughout; no colours passed in.

Test: `test/…/ui/photos/MonthRailTest.kt`.

### [ ] 2.2 — The screen

File: `…/ui/photos/PhotosScreen.kt` (new)

- `@Composable fun PhotosScreen(viewModel: PhotoGridViewModel)`: `RefreshOnStart(viewModel::refresh)`; private `PhotosContent(state, …)`, a `Row` with 48dp gap: `MonthRail` then `PhotoGrid`.
- Loading: a rail skeleton (`SkeletonBlock`s, 200dp wide) and `PhotoGridSkeleton`; focus stays on the menu pill, which `MainScreen` gave it.
- Empty: `EmptyState` with new strings `photos_empty_title` "No photos yet", `photos_empty_hint` "Add some in Immich and they show up here.", action Refresh (`viewModel::refresh`).
- Failed: `FailedState(problem, viewModel::retry)`.
- `@Preview`s for loaded, month failed, skeleton, empty, failed.

File: `…/ui/main/MainScreen.kt`: add a `photos: @Composable () -> Unit` slot; `Tab.Photos -> photos()`. Update its previews.

File: `…/MainActivity.kt`: `photos = { PhotosScreen(viewModel(key = "photos-$connection", factory = PhotoGridViewModel.factory(container.libraryTimeline()))) }`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*MonthRailTest' --tests '*PhotoGridViewModelTest' --tests '*ArchitectureTest'`
- `./build.sh ./gradlew --console=plain assembleDebug`

---

## Checkpoint 3 — Rail and grid in step · commit: `feat(photos): keep the month rail and the grid in step`

Goal: moving through the grid moves the rail; choosing a month jumps there.

### [ ] 3.1 — Grid positions and the selected month

File: `…/ui/photos/PhotoGridViewModel.kt`

- `internal fun gridIndexOfMonth(sections, month): Int?`: the index of the month's heading in the lazy grid. Each section is one heading plus `tiles().size` items, or one row when `Failed` (the same structure as `monthSection`).
- `internal fun gridIndexOfPhoto(sections, id: PhotoId): Int?`.
- `PhotoGridState` gains `focusedMonth: YearMonth?`; `fun monthFocused(month: YearMonth)` sets it. Rail selection is `focusedMonth ?: sections.firstOrNull()?.month`. The focused month is used rather than the first visible item, which lags behind a focus moving down the screen.

### [ ] 3.2 — Link them

File: `…/ui/photos/PhotoGrid.kt`: new parameters, default no-ops so the album screen is unchanged: `onMonthFocused: (YearMonth) -> Unit = {}`, `jumpTo: YearMonth? = null`, `onJumped: () -> Unit = {}`. Each month's first tile gets a `FocusRequester` from a `remember`ed map. On `jumpTo`: `scrollToItem(gridIndexOfMonth)`, wait a frame, `requestFocus` its first tile, `onJumped()`. A failed month has no tile: focus its Try again, through the same map. Replace the "nothing yet" KDoc on `onOpen`.

File: `…/ui/photos/MonthRail.kt`: OK calls `onChoose`. Moving along the rail changes nothing else. The selected month scrolls into view when it changes. `focusProperties { enter = … }` sends focus entering the rail to the selected month, with its `FocusRequester`; a selected month not laid out falls back to default entry after scrolling it into view.

File: `…/ui/photos/PhotosScreen.kt`: holds `jumpTo` and passes it along. Left from the grid's first column reaches the rail by focus search; up from the first row reaches the top menu.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PhotoGridViewModelTest' --tests '*MonthTilesTest'`
- The tiles of a month still loading stay focusable, so a jump to one can land (see `monthSection`'s comment).

---

## Checkpoint 4 — The full-screen photo · commit: `feat(photos): open a photo full-screen and move through the timeline`

Goal: OK opens a photo, left/right move through the timeline, Back returns to the last viewed tile.

### [ ] 4.1 — Moving through the timeline

File: `…/ui/photos/PhotoGridViewModel.kt`

- `PhotoGridState` gains `viewing: Photo? = null`.
- `fun open(photo: Photo)`: sets `viewing`.
- `fun viewerMove(by: Int)` (±1): the neighbour within the month; past its end, the first photo of the next non-empty month in the direction moved (last photo going back). A `NotLoaded` month is fetched and the move completes when it arrives; while waiting, `viewing` stays. Only the latest press is pending, and a second press while waiting replaces it. A `Failed` month is tried again and `viewing` stays. At either end of the timeline, nothing happens: no wrap.
- `viewing` is also where the grid goes back to.
- `refresh()` keeps `viewing`.

### [ ] 4.2 — The viewer

File: `…/ui/components/FullScreenImage.kt` (new): `fun fullScreenRequest(context: Context, id: PhotoId): ImageRequest`, moved from `SlideshowScreen.kt`, which now imports it: the same request shares the bitmap.

File: `…/ui/photos/PhotoViewerScreen.kt` (new): `@Composable fun PhotoViewerScreen(viewModel: PhotoGridViewModel, onBack: () -> Unit)`

- Full screen with no safe area, `bg` ground, `AsyncImage(fullScreenRequest(…), contentScale = ContentScale.Fit)`. No overlays; a video gets the play badge, bottom-left at the slideshow's overlay margins, shared with `PhotoTile` as `VideoBadge` in `PhotoTile.kt`.
- A focused root with `onPreviewKeyEvent`: `DirectionLeft`/`DirectionRight` on `KeyDown` call `viewerMove`, repeats included. OK does nothing. Back isn't handled here, so it stays with `MainActivity`'s handler.
- Preloads the two loaded neighbours with `imageLoader.execute`, at most three bitmaps asked for.
- An image that fails shows `surface`-toned hint text, new string `photo_failed` "Couldn't load this photo."; left/right still move.
- `@Preview`.

File: `…/ui/AppViewModel.kt`: `data object PhotoViewer : AppScreen`; `fun openPhotoViewer()`; in `back()`, `PhotoViewer -> Main(Tab.Photos)`.

File: `…/MainActivity.kt`: `is AppScreen.PhotoViewer`, with the same `viewModel` key as the tab (`"photos-$connection"`), so it is the same instance. `AlbumPhotosScreen` stays without `onOpen`.

### [ ] 4.3 — Back to where you were

File: `…/ui/photos/PhotoGrid.kt`: new parameter `restoreTo: PhotoId? = null`. `rememberLazyGridState(initialFirstVisibleItemIndex = restoreTo?.let { gridIndexOfPhoto(sections, it) } ?: 0)`. That tile gets a `FocusRequester` and the focus after a frame, in place of the first tile; if it's gone, the first tile as today (`lostFocus`).

File: `…/ui/photos/PhotosScreen.kt`: passes `state.viewing?.id`; `onOpen` calls `viewModel.open` then the screen's `onOpenPhoto`. `focusedMonth` updates through `onMonthFocused` when the tile takes focus.

Rest of the screen: `MainScreen` asks for the pill's focus on entry and the grid takes it after a frame, the same order `AlbumsScreen` uses.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PhotoGridViewModelTest' --tests '*AppViewModelTest' --tests '*SlideshowViewModelTest' --tests '*SlideshowHelpersTest'`
- `./build.sh ./gradlew --console=plain assembleDebug`

---

## Checkpoint 5 — Slideshow from here · commit: `feat(photos): start a slideshow from any photo in the timeline`

Goal: holding OK on a tile plays the library from that photo onward.

### [ ] 5.1 — Domain

File: `…/domain/slideshow/Slideshow.kt`

- `data class Playlist(val slides: List<Slide>, val start: Int = 0)`.
- `class LoadLibrarySlideshow(private val repository: LibraryRepository)`, `suspend operator fun invoke(from: PhotoId): ServerResult<Playlist>`: drops videos, and `start` is the first photo at or after `from` in the full list. A video with no photo after it wraps to 0; a photo no longer there gives 0.

File: `…/AppContainer.kt`: `val loadLibrarySlideshow = LoadLibrarySlideshow(library)`.

Test: `test/…/domain/slideshow/LoadLibrarySlideshowTest.kt`.

### [ ] 5.2 — The show

File: `…/ui/slideshow/SlideshowViewModel.kt`: constructor becomes `(settings: SlideshowSettings, loadPlaylist: suspend () -> ServerResult<Playlist>)`; `load()` sets `index = playlist.start`. Factories: `factory(container, album, settings)` wraps `loadSlideshow(album, settings.order)` in a `Playlist`; new `fromTimeline(container, from: PhotoId)`. `val TimelineSettings = SlideshowSettings(order = SlideshowOrder.NewestFirst)`: the defaults, 8 s with date and place on. Update `SlideshowViewModelTest`'s constructions through a helper; add a start index test. Progress shows the position in the library, so a show from the middle starts mid-bar.

File: `…/ui/AppViewModel.kt`: `data class TimelineSlideshow(val from: PhotoId) : AppScreen`; `fun openTimelineSlideshow(from: PhotoId)`; `back()` → `Main(Tab.Photos)`. Coming back, `viewing` wasn't moved by the show, so focus returns to the tile it started on; set it on hold with `open`.

File: `…/MainActivity.kt`: `is AppScreen.TimelineSlideshow -> SlideshowScreen(viewModel(key = "slideshow-$connection-${System.identityHashCode(current)}", factory = SlideshowViewModel.fromTimeline(container, current.from)), settings = TimelineSettings, onBack = { app.back() })`.

### [ ] 5.3 — Hold OK and its hint

File: `…/ui/components/PhotoTile.kt`: `onLongClick: (() -> Unit)? = null`, passed to tv `Surface`.

File: `…/ui/photos/PhotoGrid.kt`: `onHold: ((Photo) -> Unit)? = null`; a tile whose photo hasn't arrived ignores it. The album grid passes nothing.

The key repeats of the hold reach the slideshow, which ignores repeats of OK (`SlideshowAction.TogglePause(whenHeld = false)`), so the show doesn't pause itself. Say so in a comment there.

File: `…/ui/components/KeyHint.kt` (new): `@Composable fun KeyHint(hints: List<Pair<String, String>>, modifier)`: the canvas's pill: `surface`, fully round, 20sp 600 text, each key a small `fg` chip with `on-fg` 800 text. Shown bottom-right on Photos only while the grid is loaded, new strings `hint_ok` "OK", `hint_open` "open", `hint_hold_ok` "hold OK", `hint_slideshow_from_here` "slideshow from here". Not focusable.

### [ ] 5.4 — The manual

File: `docs/design/README.md`: Screens: the Photos row (rail 200dp, years not focusable, OK jumps) and a new row "Photo": full screen with no safe area, the whole photo fitted on `bg`, no overlays, left/right move, a video is a still with the badge, stops at the ends. Components: `MonthRail`, `KeyHint`. Remove "built for Photos to reuse in #7" from the Album photos row if no longer apt; the album grid's OK waiting on its ticket goes under Open decisions.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`
- All green, with `ArchitectureTest` among them.

---

## Out of scope

- OK on an album's Browse photos grid: stays as it is, with a follow-up ticket (Q7.1).
- Playing videos anywhere (#6); in the viewer a video is a still.
- Partners' photos and photos only in shared albums (Q1.1).
- Settings for a timeline slideshow, shuffle or oldest first from the timeline (Q6.1).
- Date, place or any overlay on the full-screen photo (Q3.1).
- Expanding a stack, favourites, archive, or a "Slideshow from here" button (hold OK is the action).
- Hold OK inside the full-screen view.
- The Home tab and Search.
