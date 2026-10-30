# Checklist — feat: open a photo from an album's grid

Issue: #23 — feat: open a photo from an album's grid (https://github.com/YasarMahomedAbbas/immich-tv/issues/23)
Branch: `feat/23-open-photo-from-album-grid`
Problem: OK on a tile of an album's Browse photos grid does nothing, so a photo in an album can't be seen full-screen or started a slideshow from, as it can on the Photos tab.

## Done when

- OK on an album grid's tile opens the photo full-screen.
- Left/right move through that album.
- Back returns to the tile last viewed.
- Holding OK starts a slideshow from that photo, as on the Photos tab.
- The same on the memory grid opened from Home's View photos (decision Q2, from #9's deferral).

## References

- Immich API: no new or changed call. `GET /api/timeline/buckets` and `GET /api/timeline/bucket` with `albumId` and `order`, already used by `ImmichAlbumRepository`: both exist with those parameters and `asset.read` in the v3.3.5 spec; the #7 notes cover v1.133.0. On the live v3.3.5 server, 4 albums (up to 699 items, both `order` values) gave `desc` as the exact reverse of `asc`, so `slides()` (oldest first) reversed matches a newest-first grid.
- #7 (the viewer, hold OK; decision Q7.1), #9 (memory grid; its Q3.1), `docs/design/README.md` rows Album photos, Photo, Slideshow and its Open decisions.
- Follow-ups already raised, not this ticket: #26 (videos in Photos tab shows), #22 (finished shows stay in memory).

## Context

`PhotoGrid`, `PhotoViewerScreen`, `PhotoGridViewModel` and `KeyHint` already do everything for the Photos tab, and take any `Timeline`; `AlbumPhotosScreen` simply passes no `onOpen` or `onHold`. The viewer shares the grid's ViewModel by key, so it must use the same key the grid does. `PhotosScreen` is the analogue for the wiring, `LoadLibrarySlideshow` for a show from a photo. Decisions: an album show from a tile uses the album's saved time, videos and date-and-place, in the grid's order, ignoring the saved Order; the memory grid behaves the same with the fixed defaults its show already has. Grid state lives for the session, so a `returning` flag on the grid's screen decides whether to restore the tile: Browse photos entered afresh starts at the top.

## Test plan

- Unit: `LoadSlideshowFromPhoto` (newest-first and oldest-first follow the album's order; Skip drops videos; held video starts itself when videos play, at the next photo when skipped; a skipped video with nothing after it, or a photo gone, starts at the first; failure passes through; empty). `Memory.playlistFrom`. `LoadLibrarySlideshow`'s existing tests stay green over the shared helper. `AppViewModel`: every open and Back path below, including `cameFrom` kept through them for all three origins, a fresh Browse photos not `returning`, settings loaded for the show, and Back during the settings load not being overridden. `SlideshowViewModel` factories are thin, covered by the existing tests of its playlist behaviour.
- MockWebServer: none, no call changes.
- Real server: read-only `GET /api/albums`, then buckets and bucket for an album with `order` both ways: done at planning, repeated by self-verification against the album it drives on the TV.
- On the TV: Albums, an album, Browse photos. OK opens a photo whole; left/right cross a month boundary and stop at both ends; a video is a still with its badge; Back focuses the last viewed tile, including a far one; hold OK starts at that photo, with the album's saved time and date-and-place, held key not pausing, and Back returns to that tile; hold on a video with videos playing and with Skip; Back from the grid reaches the album on Browse photos, then Browse photos again starts at the top; the key hint shows. An oldest-first album's show runs in grid order. A Shuffle album's show still follows the grid. The memory grid can't be driven: this server has no memories, so it is proven by tests only and recorded so. Lock: at most 10 minutes, released before any fix.
- Per the user's speed note: each checkpoint runs only its changed classes' tests; the full CI command runs once, in `raise-pr`.

---

## Checkpoint 1 — A show from a chosen photo · commit: `feat(slideshow): start an album or memory show from a photo`

Goal: the domain can say what a show from a tile plays, in the grid's order, and the show can run it.

### [x] 1.1 — Shared start logic

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/slideshow/Slideshow.kt`
Add `internal fun playlistFrom(all: List<Slide>, kept: List<Slide>, from: PhotoId): Playlist`: `kept` is `all` less what the show leaves out, both in play order. Starts at the first kept slide at or after `from` in `all`; `from` not in `all`, or no kept slide after it, starts at 0. Move `LoadLibrarySlideshow`'s body onto it, with no change in behaviour.

### [x] 1.2 — The album show from a photo

Same file: `class LoadSlideshowFromPhoto(private val repository: AlbumRepository)` with `suspend operator fun invoke(album: Album, from: PhotoId, videos: VideoMode): ServerResult<Playlist>`: `repository.slides(album)`, `.reversed()` when `album.newestFirst`, kept is `withoutVideos()` when `videos == VideoMode.Skip`, else all; then `playlistFrom`. Doc comment says the grid's order is why the saved Order isn't taken.
Test: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/domain/slideshow/LoadSlideshowFromPhotoTest.kt`, mirroring `LoadLibrarySlideshowTest`, with `FakeAlbumRepository`.

### [x] 1.3 — The memory show from a photo

File: `…/domain/home/Memory.kt`: `fun Memory.playlistFrom(from: PhotoId): Playlist` calling the helper with `slides` as both lists. Test in `…/domain/home/MemoryTest.kt`.

### [x] 1.4 — Wiring and words

- `…/AppContainer.kt`: `val loadSlideshowFromPhoto = LoadSlideshowFromPhoto(albums)`.
- `…/ui/slideshow/SlideshowViewModel.kt` companion: `fun fromAlbumPhoto(container: AppContainer, album: Album, from: PhotoId, settings: SlideshowSettings)`, `fun fromMemoryPhoto(memory: Memory, from: PhotoId)` with `TimelineSettings`.
- `…/ui/slideshow/SlideshowScreen.kt`: `SlideshowSource.Memory(R.string.slideshow_back_memory, R.string.slideshow_empty_hint_home, R.string.slideshow_empty_action_memory)`.
- `app/src/main/res/values/strings.xml`: `slideshow_back_memory` "‹ Memory", `slideshow_empty_action_memory` "Back to memory".

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*LoadSlideshowFromPhotoTest' --tests '*LoadLibrarySlideshowTest' --tests '*MemoryTest'`
- The library show's tests pass unchanged.

---

## Checkpoint 2 — Where each screen goes · commit: `feat(albums): navigate from a grid's photo and back to its tile`

Goal: the new screens exist in `AppViewModel`, and every Back path is tested.

### [ ] 2.1 — The screens

File: `…/ui/AppViewModel.kt`, in `AppScreen`:
- `AlbumPhotos` gains `val returning: Boolean = false`; `MemoryPhotos` the same. `returning`: coming Back from a photo or a show, so the grid goes to that tile; otherwise it starts at the top.
- New: `AlbumPhotoViewer(album: DomainAlbum, cameFrom: CameFrom)`, `MemoryPhotoViewer(memory: Memory, title: String)`, `AlbumPhotoSlideshow(album: DomainAlbum, from: PhotoId, settings: SlideshowSettings, cameFrom: CameFrom)`, `MemoryPhotoSlideshow(memory: Memory, title: String, from: PhotoId)`.

### [ ] 2.2 — Moving between them

Same file. `AppViewModel` gains a constructor parameter `getSlideshowSettings: GetSlideshowSettings`, given in `factory`. Add `openAlbumPhotoViewer(album, cameFrom)`, `openMemoryPhotoViewer(memory, title)`, `openMemoryPhotoSlideshow(memory, title, from)` and `openAlbumPhotoSlideshow(album, from, cameFrom)`. The last loads the album's settings in `viewModelScope`, then changes screen only if the current screen is still that `AlbumPhotos`, so Back during the load isn't overridden.
`back()`: both album new screens go to `AlbumPhotos(album, cameFrom, returning = true)`; both memory new screens to `MemoryPhotos(memory, title, returning = true)`. `AlbumPhotos` and `MemoryPhotos` go where they do today.
Test: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/AppViewModelTest.kt`, its `viewModel()` helper passing `GetSlideshowSettings(FakeSlideshowSettingsStore())`. Cover: viewer and show Back to a `returning` grid for `AlbumsTab`, `HomeRow`, `HomeHero`; Back from that grid as today for each; `openAlbumPhotos` not `returning`; the show carrying the saved settings; Back during the load stays on the album screen; both memory paths.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AppViewModelTest'`

---

## Checkpoint 3 — The grid and the viewer on screen · commit: `feat(albums): open a photo from an album's or memory's grid`

Goal: the remote does what the ticket says, and the manual says so.

### [ ] 3.1 — The grid screen

File: `…/ui/album/AlbumPhotosScreen.kt`: `AlbumPhotosScreen(albumName, viewModel, onBack, returning: Boolean, onOpenPhoto: () -> Unit, onStartSlideshow: (PhotoId) -> Unit)`. Mirror `PhotosScreen`: `onOpen` calls `viewModel.open(photo)` then `onOpenPhoto()`; `onHold` calls `viewModel.open(photo)` then `onStartSlideshow(photo.id)`. Pass to `PhotoGrid` `onOpen`, `onHold` and `restoreTo = if (returning) state.viewing?.id else null`. Under the `Loaded` grid, `KeyHint` with the same two hints as `PhotosContent`, bottom-right, in a `Box` wrapping the `PhotoGrid`. Leave the focus on Back while loading as it is; the `LaunchedEffect` giving Back focus on `Loaded` runs before the grid's restore, which waits a frame: confirm on the TV. Update the previews.

### [ ] 3.2 — The activity

File: `…/MainActivity.kt`: private helper composables `albumGrid(album)` and `memoryGrid(memory)` returning the `PhotoGridViewModel` with today's keys, so grid and viewer can't drift apart. Wire `AlbumPhotos` and `MemoryPhotos` to the new callbacks and `returning`. Add `AlbumPhotoViewer` and `MemoryPhotoViewer` (`PhotoViewerScreen` with that grid's ViewModel), `AlbumPhotoSlideshow` (`SlideshowViewModel.fromAlbumPhoto`, `settings = current.settings`, `source = SlideshowSource.Album`, fresh key as the others), `MemoryPhotoSlideshow` (`fromMemoryPhoto`, `TimelineSettings`, `SlideshowSource.Memory`).

### [ ] 3.3 — The manual

File: `docs/design/README.md`: Album photos row: OK opens, hold OK starts a show from there, the `KeyHint`, Browse photos entered afresh starts at the top, and the memory grid is the same. Photo row: moves through "the timeline or album", Back to its grid's tile. Slideshow row: a show from an album grid's tile uses the album's time, videos and date-and-place in the grid's order, says "‹ Album" and goes back to the tile; a memory grid's says "‹ Memory". Remove the album grid's line from Open decisions.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AppViewModelTest' --tests '*PhotoGridViewModelTest'`
- Compiles with the previews; the rest is the on-TV check in self-verification (Compose focus code has no automated tests).

---

## Out of scope

- Playing a video in the viewer: it stays a still with the badge, as on the Photos tab.
- Videos in Photos tab shows (#26); finished shows staying in memory (#22).
- Photos from the album screen's strip, or search results' tiles.
- The month rail on album grids.
- Changing what the album screen's Start slideshow plays.
- Changing the Photos tab's or Home's Recently added behaviour.
