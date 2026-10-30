# Checklist — feat: the home screen

Issue: #9 — feat: the home screen (https://github.com/YasarMahomedAbbas/immich-tv/issues/9)
Branch: `feat/9-the-home-screen`
Problem: the Home tab is a placeholder, so the app opens on nothing worth looking at.

## Done when

- A memories hero ("One year ago today", from Immich's memories) with **Play slideshow** and **View photos**; when there's no memory today, the most recently updated album instead.
- An Albums row (recently updated first) with **See all** to the Albums tab.
- A Recently added row.
- The app opens on Home with focus on the hero's Play slideshow.
- Skeleton and failed states.

## References

- Immich API, checked against the specs and server source of v3.3.5 (the server) and v1.133.0 (`MINIMUM`):
  - `GET /api/memories`, query `for` and `type=on_this_day`. `for` must be date-only (`YYYY-MM-DD`): v3.3.5 answers 400 to a full timestamp (confirmed live); v1.133's spec says date-time, but its DTO parses to a `Date`, so a date-only value should pass. Not run against a real v1.133.
  - `MemoryResponseDto`: `memoryAt`, `type`, `assets` are required in both. `data` is `OnThisDayDto` in v1.133 and `MemoryDataDto` in v3; the app reads neither. The `birthday` type exists on newer servers only and is filtered out by `type`.
  - `AssetResponseDto`: `id`, `type`, `visibility`, `localDateTime` required in both. `width`/`height` are absent in v1.133 and nullable in v3.3.5. `exifInfo` optional. v3.3.5 filters memory assets to the timeline server-side; v1.133 doesn't, so the client filters on `visibility`.
  - v3.3.5 requires `memory.read` for this call; a key without it gets 403.
  - `GET /api/albums` and `/api/timeline/buckets`, `/api/timeline/bucket`: already used, unchanged.
  - Immich can't sort by upload date: `SearchOrderField` (v3.2+) has no `createdAt`, and older versions order by `fileCreatedAt` only.
- Design: canvas https://claude.ai/artifact/8Tq2kuJDNjk3SFiALwbfZH, board "1 · Home"; `docs/design/README.md`, screen "Home".

## Context

`MainScreen` shows `PlaceholderTab` for Home and hands each tab a composable slot; `AlbumsScreen`/`AlbumsViewModel` is the analogue for load, refresh, retry and focus restored after coming Back. `ImmichLibraryRepository` and its MockWebServer test are the analogue for the new calls. Grids reuse `AlbumPhotosScreen` and `PhotoGridViewModel` through the `Timeline` interface. Decisions are in `NOTES.md`: recent means newest taken; the most recent year's memory only; View photos opens a grid of that memory; a refused memories call falls back to the album.

Consequences to keep to: a hero memory holds photos only, so the count, the show and the grid agree. Canvas values that break the manual follow the manual: no amber label, main button sizes from `PrimaryButton`.

## Test plan

- Unit: `LoadHome` (memory beats album; newest year wins; memory with only videos skipped; memories failing for any reason falls back; albums or recent failing fails the page; no album and no memory gives no hero; albums order and limit; years ago count). `HomeViewModel` (loading, loaded, failed, retry, refresh keeping the screen on failure, focus remembered). `AppViewModel` Back from every path opened on Home. `memoryTimeline`.
- MockWebServer: memories for the v1.133 and v3.3.5 asset shapes; query is date-only with `type=on_this_day` and the key header; 401, 403, 500 and bad JSON; non-timeline assets dropped; unreadable date. `recent` across one bucket, several buckets, no buckets, a failed bucket.
- Real server: read-only `GET /api/memories?for=<today>&type=on_this_day` (expect 200 and, today, an empty list), plus the recent-photos calls.
- On the TV: launch lands on Play slideshow on the album fallback; D-pad to View photos, the Albums row, See all, Recently added; every Back returns to Home with focus restored; failed state with the network off if practical. The memory hero can't be driven: the server has no memories, so it is proven by tests only and is recorded as such. Hold the adb lock per the issue's note.

---

## Checkpoint 1 — Memories and recent photos from the server · commit: `feat(api): read today's memories and recent photos`

Goal: the domain can ask for today's memories and the newest photos, on every supported server version.

### [x] 1.1 — Domain: memories

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/home/Memory.kt`
`data class Memory(val takenOn: LocalDate, val slides: List<Slide>)`, with `val photos: List<Photo>` from the slides.
`interface MemoryRepository { suspend fun memoriesOn(day: LocalDate): ServerResult<List<Memory>> }`, documented as "On this day" memories for that day, photos and videos, any order.
`fun Memory.timeline(): Timeline`: one `TimelineMonth(YearMonth.from(takenOn), slides.size)`, its `photos(month)` the slides' photos, empty for another month.

### [x] 1.2 — Domain: recent photos

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/photos/Library.kt`
Add to `LibraryRepository`: `suspend fun recent(limit: Int): ServerResult<List<Photo>>`, "the newest [limit] items, by when they were taken". Update `FakeLibraryRepository` with a settable `recent`.

### [x] 1.3 — Data: memories

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/data/immich/ImmichMemoryRepository.kt`
`internal class ImmichMemoryRepository(client: OkHttpClient, sessions: SessionStore) : MemoryRepository`, using `ImmichApi`. `GET memories` with `for=<day>` (`LocalDate.toString()`) and `type=on_this_day`. Private DTOs: `MemoryDto(memoryAt: String, assets: List<MemoryAssetDto>)`, `MemoryAssetDto(id, type, visibility: String? = null, width: Int? = null, height: Int? = null, localDateTime: String? = null, exifInfo: ExifDto? = null)`, `ExifDto(city: String? = null, country: String? = null)`. `takenOn` is `memoryAt`'s UTC calendar day, read as `calendarDay` in `ImmichAlbumRepository` does (make that `internal`, or share it). An asset whose `visibility` is present and not `timeline`, or whose `type` is neither `IMAGE` nor `VIDEO`, is dropped. `portrait` is `height > width` when both are known, else false. `takenOn` of a slide is `localDateTime`'s first 10 characters, null if unreadable. Share `takenOn` and `place` from `ImmichTimelineDtos.kt`, and `mapAll` from `ImmichAlbumRepository.kt`, by making them `internal`. An unreadable `memoryAt` fails as `Problem.Unreachable`.

### [x] 1.4 — Data: recent photos

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/data/immich/ImmichLibraryRepository.kt`
`recent(limit)`: `timeline/buckets` with `QUERY`, then `timeline/bucket` one month at a time from the newest, stopping once `limit` items are held, so it costs as few calls as the library allows. Any failure returns that failure.

### [x] 1.5 — Wiring and tests

Files: `AppContainer.kt` (a private `ImmichMemoryRepository`), `README.md` (add `memory.read` to the key's permissions, saying that without it Home shows an album instead of a memory).
Tests: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/data/immich/ImmichMemoryRepositoryTest.kt` (mirror `ImmichLibraryRepositoryTest`), additions to `ImmichLibraryRepositoryTest`, `app/src/test/java/io/github/yasarmahomedabbas/immichtv/domain/home/MemoryTest.kt`. See the test plan.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*ImmichMemoryRepositoryTest' --tests '*ImmichLibraryRepositoryTest' --tests '*MemoryTest' --tests '*ArchitectureTest'`
- A date-only `for` is asserted on the recorded request; both asset shapes decode.

---

## Checkpoint 2 — What Home shows · commit: `feat(home): load the hero and rows for the home screen`

Goal: a tested use case and ViewModel hold everything the Home screen renders.

### [x] 2.1 — Use case

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/home/LoadHome.kt`
```
sealed interface HomeHero {
    data class FromMemory(val memory: Memory, val yearsAgo: Int) : HomeHero
    data class FromAlbum(val album: Album) : HomeHero
}
data class HomePage(val hero: HomeHero?, val albums: List<Album>, val recent: List<Photo>)
class LoadHome(albums: AlbumRepository, memories: MemoryRepository, library: LibraryRepository, today: () -> LocalDate = LocalDate::now)
```
`invoke()`: reads all three concurrently. Albums or recent failing returns that failure. A failed memories call counts as no memory, whatever the problem. Each memory keeps its non-video slides; those left empty are dropped; the one with the latest `takenOn` is the hero, with `yearsAgo = today().year - takenOn.year`. Otherwise the hero is the first album by `filterAndSort(All, RecentlyUpdated)` with `itemCount > 0`, or null. `albums` is that sort, first `ALBUM_ROW = 10`. `recent` is `library.recent(RECENT_ROW = 20)`. Constants in the companion.

### [x] 2.2 — ViewModel

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/home/HomeViewModel.kt`, mirroring `AlbumsViewModel`.
`sealed interface HomeLoad { Loading; Loaded(page: HomePage); Failed(problem) }`. `sealed interface HomeFocus { Play; ViewPhotos; SeeAll; AlbumCard(id: AlbumId); Recent(id: PhotoId) }`. `data class HomeState(load, focused: HomeFocus = HomeFocus.Play)`. `onFocused`, `retry`, `refresh` (no skeleton; failure leaves the screen). `suspend fun heroAlbumSettings(album: Album): SlideshowSettings` through `GetSlideshowSettings`. Factory from `AppContainer`, which gains `val loadHome`. `HomePage.focusTarget(focused)` says where focus goes: `focused` if it is still on the page, else Play, else the first album or recent item, else null.

### [x] 2.3 — Tests

Files: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/domain/home/LoadHomeTest.kt`, `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/home/HomeViewModelTest.kt`, with a `FakeMemoryRepository` in `domain/home/Fakes.kt`. See the test plan.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*LoadHomeTest' --tests '*HomeViewModelTest'`

---

## Checkpoint 3 — The Home screen · commit: `feat(home): show the memory hero and rows on the home tab`

Goal: the Home tab renders the hero, both rows and every state, with the app's own components. Run the `design-recipe` skill first.

### [x] 3.1 — Screen

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/home/HomeScreen.kt`
`HomeScreen(viewModel, onPlayMemory: (Memory) -> Unit, onMemoryPhotos: (Memory, String) -> Unit, onPlayAlbum: (Album, SlideshowSettings) -> Unit, onAlbumPhotos: (Album) -> Unit, onOpenAlbum: (AlbumId) -> Unit, onOpenRecent: (Photo) -> Unit, onSeeAll: () -> Unit)`. A stateless content composable below it for previews. `RefreshOnStart(viewModel::refresh)`.
A vertically scrolling column, since the canvas's three blocks overrun 1080: focus moving down brings a row into view, moving up returns to the hero.
- Hero, 380dp tall, 22dp corners (a new `TileStyle.Hero`), full width: a preview-size `PhotoTile` (`memory.photos.first()` or `album.cover`), with `OverPhotoPanel` bottom-left at 40dp and 620dp wide. A new `OverPhotoStyle.Hero`: 78% scrim, 18dp corners. Label 18sp 700 `mfg` caps ("Memories" or "Recently updated"), title 46sp 800 ("One year ago today" through a plural `R.plurals.home_years_ago`, or the album name), a line 22sp `mfg`: the localized long date and "23 photos" (`R.plurals.photo_count`), or the album's count. Then `PrimaryButton` Play slideshow with its icon, `SecondaryButton` View photos.
- Albums row: title 28sp 700, `SecondaryButton(compact = true)` See all, then a `LazyRow` of `AlbumCard` 320dp wide, 32dp gaps.
- Recently added: title, `LazyRow` of 200dp focusable `PhotoTile`s, 18dp gaps, with the video badge.
- No hero and nothing in either row: `EmptyState` with the Albums empty wording's pattern, "Nothing here yet" / "Add photos in Immich and they show up here." / Refresh. A row with nothing in it is left out.
- Skeleton mirroring the layout with `SkeletonBlock`. `FailedState` with Try again.
- Focus: once loaded, `HomeState.focused` is requested, Play first; if it has gone, Play. With no hero, the first focusable item. During loading focus stays on the menu pill, as `MainScreen` sets it. Report changes through `onFocused`.
- `@Preview`s for memory, album fallback, skeleton, failed, empty. Strings in `strings.xml`.

### [x] 3.2 — Slot

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/main/MainScreen.kt`
Add a `home: @Composable () -> Unit` slot; `Tab.Home -> home()`; fix the previews. `PlaceholderTab` stays for Search.

### [x] 3.3 — Manual

File: `docs/design/README.md`: the Home row in § Screens gains the hero's values and the 10 and 20 item limits; `OverPhotoPanel` gains `Hero`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest lintDebug assembleDebug`
- Previews compile; each manual rule checked as `design-recipe` step 4 asks.

---

## Checkpoint 4 — Getting around from Home · commit: `feat(home): open slideshows and photos from home and come back`

Goal: every action on Home works, and Back from each returns to Home with focus where it was.

### [x] 4.1 — Navigation

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/AppViewModel.kt`
`enum class CameFrom { AlbumsTab, HomeRow, HomeHero }`. `Album`, `AlbumPhotos` and `Slideshow` gain `cameFrom: CameFrom = AlbumsTab`. New screens: `MemorySlideshow(memory: Memory)`, `MemoryPhotos(memory: Memory, title: String)`. `PhotoViewer` becomes `data class PhotoViewer(val fromHome: Boolean = false)`.
Back: `Album` goes to `Main(Home)` when `HomeRow`, else `Main(Albums)`. `AlbumPhotos` and `Slideshow` go to `Main(Home)` when `HomeHero`, else to `Album(id, cameFrom = cameFrom, ...)` as today. `MemorySlideshow`, `MemoryPhotos` and `PhotoViewer(fromHome = true)` go to `Main(Home)`. `open*` functions take the origin, defaulting as today.

### [x] 4.2 — Slideshow source

Files: `SlideshowScreen.kt` (`SlideshowSource.Home`, "‹ Home", with its own empty-state strings), `SlideshowViewModel.kt` (`fromMemory(memory)`: `TimelineSettings`, `Playlist(memory.slides)`, in the server's order).

### [x] 4.3 — Activity

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`
Pass `home` to `MainScreen`, with `viewModel(key = "home-$connection", factory = HomeViewModel.factory(container))`. Hero memory: `MemorySlideshow`; `MemoryPhotos` through `AlbumPhotosScreen(title, PhotoGridViewModel.factory(memory.timeline()))`. Hero album: `Slideshow` and `AlbumPhotos` with `HomeHero`, its show saying "‹ Home" since that is where Back goes. Row: `Album` with `HomeRow`, its onward screens carrying it. Recent tile: on the `photos-$connection` ViewModel call `open(photo)`, then show `PhotoViewer(fromHome = true)`. See all: `selectTab(Tab.Albums)`.

### [ ] 4.4 — Opening focus and Back onto Home

File: `ui/main/MainScreen.kt` if needed (it wasn't: Home waits a frame before its request, as the Albums grid does): Home's focus request must win over `menuFocus`, as the Albums grid's does. Coming Back recreates the screen, and `HomeState.focused` restores focus. Left unticked: only the TV can show it, so it is for self-verification.

### [x] 4.5 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/AppViewModelTest.kt`: Back from each path above, and the Albums-tab paths unchanged.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`

---

## Out of scope

- Birthday memories, and more than one memory at once (no switcher, no merged years).
- Marking a memory as seen, or any write to the server.
- Ordering Recently added by upload date.
- OK on a tile in the memory's or an album's grid (#23).
- Videos in a memory: the hero's show, count and grid are photos only.
- Search, settings and slideshow changes beyond the Home source.
