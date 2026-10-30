# Checklist — fix: albums and album screens never refresh once loaded

Issue: #18 — fix: albums and album screens never refresh once loaded (https://github.com/YasarMahomedAbbas/immich-tv/issues/18)
Branch: `fix/18-refresh-albums-on-return`
Problem: the Albums tab, the album screen and the album photo grid load once and keep that for as long as the activity lives, so new albums and photos don't show until the app is killed or reconnected.

## Done when

- Open an album, press Home, add a photo to that album in Immich, return and reopen it: the counts, strip and grid show the new photo, and a new album shows in the Albums tab, without killing the app or reconnecting.
- Returning to the app, or entering one of these screens, refreshes it without a skeleton.
- When nothing changed, focus and scroll stay exactly where they were.
- A refresh that fails leaves the screen as it was.
- Each new behaviour has a test that fails without it.

## References

- Immich API (no new call; all checked against v3.3.5, the version the server runs): `GET /albums`, `GET /albums/{id}` (`AlbumResponseDto`: `assetCount`, `updatedAt` required; `lastModifiedAssetTimestamp` optional), `GET /timeline/buckets` (`count`, `timeBucket` required), `GET /timeline/bucket`.
- v3.3.5 server source: `getMetadataForIds` computes `assetCount` and `lastModifiedAssetTimestamp` live over non-trashed assets; `addAssets` sets the album's `updatedAt`. So `Album` equality notices adds, removes and trashing. Its comment says `lastModifiedAssetTimestamp` may be removed; the DTO already treats it as optional, and `assetCount` and `updatedAt` remain.
- #4's self-review raised this ticket; `docs/tasks/4-albums-and-album-screen/NOTES.md`.
- `docs/design/README.md`: no new visuals, the empty and failed states are unchanged, rule 7 (focus always visible).

## Context

ViewModels live in the activity's store, keyed by `AppViewModel.connection`, and load only in `init`. Their `load()` goes through `Loading`, which swaps content for a skeleton and drops focus, so a refresh gets its own quiet path and `retry()` stays as it is. A new `RefreshOnStart` composable, placed on each screen, fires on entering it and on returning to the app (a lifecycle observer catches up to the current state when added). The first firing lands during the `init` load and is ignored. `MutableStateFlow` drops equal values and all the state is data classes, so an unchanged answer redraws nothing. Decisions: refresh on return and on entry, keep what's shown on failure, read one cheap summary and fetch more only if it changed (see NOTES). The analogues are the existing ViewModels and their tests.

## Test plan

- Unit: `LoadAlbum.ifChanged`; `AlbumsViewModel`, `AlbumViewModel` and `PhotoGridViewModel` `refresh()`: no `Loading` state, equal data leaves the same state, changed data replaces, failure keeps, failed screen recovers, ignored during a load, one at a time, filter/sort/focus/settings kept, which calls are made; the `lostFocus` helper.
- MockWebServer: none; no call or shape changes.
- Real server: self-verification reads `GET /albums`, `/albums/{id}` and `/timeline/buckets` twice on v3.3.5 and confirms `Album` fields are stable between reads, so an unchanged album really does skip the re-read.
- On the TV: Albums with a card focused down the grid, Home, return: same card, same scroll, no skeleton. The same on the album screen (a strip tile focused) and the photo grid (a tile in a later month). Back from Browse to the album keeps Browse focused. Changed data needs an edit on the server, which self-verification may not make, so it rests on the unit tests unless someone edits an album during that run; record as such.

---

## Checkpoint 1 — Refresh the Albums tab · commit: `fix(albums): refresh the Albums tab on entry and on return`

Goal: the Albums tab picks up new, changed and removed albums silently, keeping focus.

### [x] 1.1 — The trigger and the focus helper

File: `gradle/libs.versions.toml`, `app/build.gradle.kts`
Add `lifecycle-runtime-compose` (`androidx.lifecycle`, version.ref `lifecycle`) and `implementation(libs.lifecycle.runtime.compose)`.

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/components/RefreshOnStart.kt` (new)
`@Composable fun RefreshOnStart(onRefresh: () -> Unit)`: `LifecycleStartEffect(Unit) { onRefresh(); onStopOrDispose {} }`, using `rememberUpdatedState` for `onRefresh`. KDoc says it fires on entry too.
`internal fun <T> lostFocus(lastFocused: T?, present: Collection<T>): Boolean` = `lastFocused != null && lastFocused !in present`.

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/components/RefreshOnStartTest.kt` (new): `lostFocus` with null, present and gone.

### [x] 1.2 — `AlbumsViewModel.refresh()`

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/albums/AlbumsViewModel.kt`
`fun refresh()`: returns if `load == Loading` or `refreshJob?.isActive == true`. Launches into `private var refreshJob: Job?`: `loadAlbums()`; `Ok` sets `load = Loaded(value)`; `Failed` changes nothing, on a `Failed` screen too. Never sets `Loading`. `retry()` cancels `refreshJob` first, so an older answer can't overwrite its result.

### [x] 1.3 — The screen

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/albums/AlbumsScreen.kt`
`AlbumsScreen`: `RefreshOnStart(viewModel::refresh)`. Pass `state.filter to state.sort` to `AlbumGrid`. Replace the `shown` effect so it holds the last `albums` and `filter to sort`, on `LaunchedEffect(albums, order)` (keyed on the order too: a filter that leaves the same list must still be remembered, or the next refresh would read as a filter change and jump to the top):
- filter or sort changed: `scrollToItem(0)`, as now.
- otherwise, `lostFocus(focused, albums.map { it.id })` and `focused` was in the last list: `scrollToItem(0)`, a frame, `targetFocus.requestFocus()` in `runCatching`.
- otherwise, after a frame (so the layout read is of the new list), the focused album isn't in `gridState.layoutInfo.visibleItemsInfo`: `scrollToItem(index - index % COLUMNS)`, so focus stays visible when "Recently updated" moves it.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AlbumsViewModelTest' --tests '*RefreshOnStartTest'`
- Tests in `AlbumsViewModelTest`: a refresh never passes through `Loading`; a new album shows; identical data leaves the same state object; failure keeps `Loaded`; `Failed` recovers to `Loaded` without `Loading`; ignored during the first load (`albumsCalls` 1); a second refresh while one runs is ignored; filter, sort and `focused` kept; a retry's result isn't overwritten by an older refresh.

---

## Checkpoint 2 — Refresh the album screen · commit: `fix(albums): refresh the album screen when the album changed`

Goal: the album's counts, mosaic and strip update when the album changed, for one call when it hasn't.

### [x] 2.1 — `LoadAlbum.ifChanged`

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/albums/AlbumRepository.kt`
`suspend fun ifChanged(known: AlbumPage): ServerResult<AlbumPage>`: `repository.album(known.album.id)`; `Failed` is returned; if equal to `known.album`, `Ok(known)` with no contents call; otherwise `repository.contents(album, STRIP_SIZE).map { AlbumPage(album, it) }`.

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/domain/albums/AlbumsTest.kt`: unchanged skips contents (`contentsLimits` empty); changed `itemCount` or `updatedAt` reads them; failure of either call is `Failed`.

### [x] 2.2 — `AlbumViewModel.refresh()`

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumViewModel.kt`
`fun refresh()` with the same guards and `refreshJob` as 1.2: from `Loaded(page)` calls `loadAlbum.ifChanged(page)`; from `Failed` calls `loadAlbum(id)`. `Ok` sets `Loaded`; `Failed` changes nothing. `settings` untouched. `retry()` cancels `refreshJob`.

### [x] 2.3 — The screen

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumScreen.kt`
`AlbumScreen`: `RefreshOnStart(viewModel::refresh)`. `Strip` takes `onFocusLost: () -> Unit`; each tile's modifier adds `.onFocusChanged { if (it.isFocused) lastFocused = photo.id }`, with `lastFocused` remembered in `Strip`. `LaunchedEffect(items)`: if `lostFocus(lastFocused, items.map { it.id })`, clear it and call `onFocusLost`. `LoadedAlbum` passes `{ runCatching { startFocus.requestFocus() } }`. `lastFocused` isn't cleared on moving to a button, so a tile vanishing then moves focus from that button to Start slideshow: rare and harmless, and it keeps this simple.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AlbumViewModelTest' --tests '*AlbumsTest'`
- Tests in `AlbumViewModelTest`: no `Loading`; unchanged keeps the state and doesn't call contents; changed album updates the page; failure keeps; `Failed` recovers; ignored during the first load; settings changed before a refresh survive it.

---

## Checkpoint 3 — Refresh the album photo grid · commit: `fix(albums): refresh the album photo grid month by month`

Goal: the grid updates only the months whose count changed, in place.

### [ ] 3.1 — `PhotoGridViewModel.refresh()`

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/photos/PhotoGridViewModel.kt`
`fun refresh()`: returns if `load == Loading` or `refreshJob` is active. `timeline.months()`; `Failed` changes nothing. On `Ok`, build the sections in the new order through `internal fun mergeMonths(old: List<MonthSection>, new: List<TimelineMonth>): List<MonthSection>`:
- same month and count: the old section as it is.
- same month, new count: `copy(count = new)`, photos unchanged for now.
- a month not seen: `NotLoaded`.
- a month gone: dropped.

Set `load` to `Empty` or `Loaded`. Then quietly re-read each month that was `Loaded` and whose count changed: a new `private fun reread(month)` that doesn't set `MonthPhotos.Loading` and, on failure, keeps the old photos. If the first month is `NotLoaded` (the grid was empty or failed), `fetch` it. `retry()` cancels `refreshJob`.

### [ ] 3.2 — The screens

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/album/AlbumPhotosScreen.kt`
`AlbumPhotosScreen`: `RefreshOnStart(viewModel::refresh)`.

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/photos/PhotoGrid.kt`
Each tile's modifier adds `.onFocusChanged { if (it.isFocused) lastFocused = "t-$key-$index" }`; pass a setter into `monthSection`. `LaunchedEffect(sections)`: compute the present tile keys from `section.tiles()`; if `lostFocus(lastFocused, keys)`, clear it, `gridState.scrollToItem(0)`, a frame, `firstTile.requestFocus()` in `runCatching`.

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`
Add `${current.album.newestFirst}` to the `album-photos-` key: the timeline keeps the order it was created with, so an album whose order changed would otherwise keep its old grid.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PhotoGridViewModelTest'`
- Tests: no `Loading` and same state when nothing changed (`monthsCalls` 2, `asked` unchanged); a changed loaded month is re-read and the others aren't; a changed unloaded month only changes its count; a new month is `NotLoaded` in the right place; a gone month drops; a failed re-read keeps the old photos; months failing keeps everything; failed and empty recover and fetch the first month; ignored during the first load.

---

## Out of scope

- A Refresh button, timed polling, or refreshing while the app sits open on a screen.
- Home, Photos and Search tabs (#9, #7, #8).
- The album name on the photo grid's back link, which is read when Browse photos is pressed.
- Clearing kept ViewModels of albums no longer open.
- Telling the user an album was deleted or a key revoked during a background refresh.
