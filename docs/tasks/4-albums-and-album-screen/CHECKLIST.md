# Checklist — feat: albums and the album screen

Issue: #4 — feat: albums and the album screen (https://github.com/YasarMahomedAbbas/immich-tv/issues/4)
Branch: `feat/4-albums-and-album-screen`
Problem: after connecting, the app shows only a placeholder: there is no way to see your albums, open one or set up how its slideshow plays.

## Done when

- The top menu (Home · Albums · Photos · Search, the clock on the right, the Settings gear beside it) works with the D-pad; Home, Photos and Search are placeholders until their tickets.
- **Albums**: every album the key can see, as a 5-column grid of cover cards with name, photo count and "Shared"; filters All albums / My albums / Shared with me; sort by recently updated, A–Z, most photos.
- **Album**: cover mosaic, title, photo/video counts and date range, **Start slideshow** (focused when the screen opens; a placeholder until #5), **Browse photos** (opens the album photo grid), and the slideshow settings (time per photo, order, videos, show date & place) remembered on the device for each album.
- A strip of the album's photos at the bottom.
- **Album photos** (behind Browse photos): a 6-column grid of the album's preview tiles with a heading per month, loaded month by month from the album timeline, with skeleton, empty and failed states; focus starts on the first tile, OK does nothing until #7, Back returns to the album with Browse photos focused. Built on a domain `Timeline` so #7 can reuse it.
- Covers and photos load as preview thumbnails with the `x-api-key` header, through `immichHttpClient()`.
- Skeleton, empty ("No albums yet") and failed states on both screens.
- Connecting to an Immich server older than v1.133 is refused with a message that names the version.

## References

- Immich API (checked against the v3.3.5 spec, the version the server runs, and v1.118.0 / v1.133.0 / v2.0.0 / v3.0.0 / v3.2.0 for differences):
  - `GET /api/server/version` → `ServerVersionResponseDto {major, minor, patch}`, no auth. Used to refuse servers < v1.133.
  - `GET /api/albums` (album.read) → `AlbumResponseDto[]`. No query params: all albums the user owns plus those shared with them, in every version. Fields read: `id`, `albumName`, `albumThumbnailAssetId` (nullable), `assetCount`, `shared`, `updatedAt`, `lastModifiedAssetTimestamp?`, `startDate?`, `endDate?`, `order?` (`asc`|`desc`), plus the owner. **Shape difference:** up to v2.x the owner is `ownerId` (and `owner`, `assets` are present); from v3.0.0 `ownerId`/`owner`/`assets` are gone and `albumUsers[0].user.id` is the owner ("First entry is always the album owner").
  - `GET /api/albums/{id}` (album.read) → `AlbumResponseDto`. v3 has no `withoutAssets`; v1/v2 do and embed `assets`. Call it with no query params; `assets` is ignored if present.
  - `GET /api/users/me` (user.read) → `id` (required in every version): who "me" is, for My albums / Shared with me.
  - `GET /api/timeline/buckets?albumId=<id>&order=<asc|desc>` (asset.read) → `[{timeBucket, count}]`. Same shape v1.133 → v3.3.5 (before v1.133 it needed `size=MONTH`; those servers are refused).
  - `GET /api/timeline/bucket?albumId=<id>&timeBucket=<YYYY-MM-DD>&order=<asc|desc>` (asset.read) → columnar `TimeBucketAssetResponseDto`; read only `id: string[]` and `isImage: boolean[]` (both required v1.133 → v3.3.5).
  - `GET /api/assets/{id}/thumbnail?size=preview` (asset.view) → image/jpeg. Header `x-api-key`.
  - Not used, and why: `/search/statistics` needs `asset.statistics`, which the configured key lacks (403, checked live); `/search/metadata`'s `albumIds`/`type`/`page` are deprecated in v3.2.0 and its `total` is only the page count.
- Design: canvas https://claude.ai/artifact/8Tq2kuJDNjk3SFiALwbfZH, boards "6 · Albums" (`Albums.dc.html`), "2 · Album" (`Album.dc.html`) and the top menu on "1 · Home" (`Main.dc.html`); the manual `docs/design/README.md`.
- Follow-ups: #5 wires Start slideshow and reads the settings; #6 uses the Videos setting; #7 the Photos tab (reuses the photo grid and `Timeline`); #9 the Home tab.
- The album photo grid has no design board; it follows the manual's grid and the strip's tile (12dp corners, amber focus), per the user's answer on the issue.

## Context

Paths below are under `app/src/main/java/io/github/yasarmahomedabbas/immichtv/` (main, written `…/`) and `app/src/test/java/io/github/yasarmahomedabbas/immichtv/` (test, written `test/…/`).

#3 built the one Immich client (`data/immich/ImmichHttp.kt`: `immichHttpClient()`, `apiUrl`, `immichJson`, `API_KEY_HEADER`), the connector and its MockWebServer test (`ImmichServerConnectorTest`, the analogue for every new call), the stored session (`StoredSessionRepository`), the use-case style (`ConnectToServer`), the ViewModel style (`SetupViewModel` + factory from `AppContainer`), hand-rolled navigation (`AppScreen` in `AppViewModel`), and the house buttons and `Modifier.focusOutline`. This ticket mirrors all of those. Decisions (taken by the pipeline on the user's behalf, see NOTES): the app's floor rises to Immich v1.133 so the album timeline has one shape; images load with Coil 3, whose interceptor in `data/` turns a domain `Preview` into the thumbnail URL plus header, so `ui/` never sees a URL; slideshow settings are kept per album on the device, defaults 8 s / oldest first / play muted / date & place on; Browse photos opens an album photo grid (the user chose option 1), built on a domain `Timeline` of months so #7 can reuse grid and ViewModel for the whole library. Filtering and sorting run on the device from one `GET /albums`; photo/video counts and the strip come from the album's timeline buckets (the `isImage` column), the only source the key can read.

## Test plan

- Unit: `ServerVersion` comparison; `filterAndSort` for each filter × sort (ties broken by name); `LoadAlbums`/`LoadAlbum` pass failures through; `SlideshowSettings.next*` cycling; `StoredSlideshowSettings` round-trip, defaults for an unknown album, a corrupt value falls back to defaults; `AlbumsViewModel` (loading → loaded/empty/failed, filter and sort changes, retry); `AlbumViewModel` (loading → loaded/failed, settings cycle and save, retry); `AppViewModel` (tabs, open album, open album photos, Back from album photos → Album, Back from album → Albums, Back from a tab → Home, Back on Home exits); `PhotoGridViewModel` (months load → first month's photos; next month loads on demand, once; a failed month and retry; empty timeline; failed months list); the `ImmichImages` interceptor maps a `Preview` to the thumbnail URL with the header and leaves other data alone.
- MockWebServer: `/server/version` 3.3.5 and 1.133.0 connect, 1.132.0 and 1.118.0 → `ServerTooOld`, a 404 there → `NotPhotoServer`; `/albums` with a v3.3.5 body (owner in `albumUsers[0]`) and a v2.0.0 body (`ownerId`, embedded `assets`), each mapped to the same `Album`s incl. `ownedByMe`; nullable cover, missing dates; 401 → `KeyRejected`, 403 → `KeyLacksPermission`, 500 and a dead socket → `Unreachable`, garbage JSON → `Unreachable`; the album contents across two buckets (counts summed, strip capped at 30, `order` passed through), an empty album (`[]` buckets); the album `Timeline`: months from `/timeline/buckets` mapped to `YearMonth` with counts, one month's photos from `/timeline/bucket` with the exact `timeBucket`; every request carries `x-api-key` and exact paths/queries; a `Preview` thumbnail request carries the key and drops it on a cross-host redirect (the existing interceptor).
- Real server (read-only, by self-verification): `GET /server/version`, `/users/me`, `/albums`, `/albums/{id}`, `/timeline/buckets` and `/timeline/bucket` with `albumId`, and `/assets/{id}/thumbnail?size=preview` for an album cover, each parsed by the app's DTOs.
- On the TV (if `TV_ADB` is set): open the app → Home tab with the menu; D-pad to Albums; the grid loads covers; change filter and sort; open an album; Start slideshow has focus; cycle each setting, leave and reopen the album → settings kept; the strip loads; Browse photos → the grid with month headings, focus on the first tile, scrolling down loads later months, Back → the album with Browse photos focused; Back → Albums with the same card focused → Back → Home → Back exits; Settings gear opens Setup.

---

## Checkpoint 1 — Immich v1.133 floor and a cached session · commit: `feat(server): require Immich v1.133 or newer`

Goal: Connect refuses servers older than v1.133 with its own message, and the session is read from memory after the first load (every image request will ask for it).

### [x] 1.1 — The domain result

File: `…/domain/server/Server.kt`
Add `data class ServerTooOld(val version: String) : ConnectResult` (KDoc: the server is Immich, but older than the oldest version the app speaks). The floor itself lives in `data/`.

### [x] 1.2 — The version check

File: `…/data/immich/ImmichServerConnector.kt`
After `ping` and before `me`, `GET apiUrl(server, "server/version")` with no key → `@Serializable private data class VersionDto(val major: Int, val minor: Int, val patch: Int)`. 200 and parsed: if `(major, minor, patch) < (1, 133, 0)` → `ConnectResult.ServerTooOld("$major.$minor.$patch")`; ≥ → continue to `me`. 5xx → `Unreachable`; any other code or unparseable → `NotPhotoServer`. Put the floor in a `private val MINIMUM = VersionDto(1, 133, 0)` constant with a comment citing the timeline shape change at v1.133. Update the class KDoc (v1.133+).

### [x] 1.3 — Setup shows it

Files: `…/ui/setup/SetupViewModel.kt`, `…/ui/setup/SetupScreen.kt`, `app/src/main/res/values/strings.xml`
Add `SetupProblem.ServerTooOld` to the enum and `val serverVersion: String? = null` to the state, set with it. String `setup_problem_server_too_old`: "This Immich server runs v%1$s. Immich TV needs v1.133 or newer: update the server." Change `setup_problem_not_photo_server` to "(v1.133 or newer)". Add a preview.

### [x] 1.4 — Session cache

File: `…/data/settings/StoredSessionRepository.kt`
Keep the loaded `Session?` in a `@Volatile private var cached` (plus a `loaded` flag); `load()` returns it after the first read; `save()` updates it. A failed decrypt is cached as null too.

### [x] 1.5 — Tests

Files: `test/…/data/immich/ImmichServerConnectorTest.kt`, `test/…/data/settings/StoredSessionRepositoryTest.kt`, `test/…/ui/setup/SetupViewModelTest.kt`
Dispatcher gains `/api/server/version` (default `{"major":3,"minor":3,"patch":5}`). Tests: 1.133.0 connects; 1.132.9 → `ServerTooOld("1.132.9")` and `/users/me` is never called; 404 → `NotPhotoServer`; 503 → `Unreachable`; the version request carries no key. The existing v1.118 test becomes "a v1.118 server is too old". Repository: two `load()`s decrypt once; `save` then `load` returns the saved session without decrypting. ViewModel maps `ServerTooOld`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*ImmichServerConnectorTest' --tests '*StoredSessionRepositoryTest' --tests '*SetupViewModelTest'`
- A v1.132 fixture is refused with the version in the result.

---

## Checkpoint 2 — Albums in domain and from Immich · commit: `feat(albums): read albums and their contents from Immich`

Goal: `LoadAlbums` and `LoadAlbum` return domain albums and an album's counts and first items from a real Immich shape, tested against v3.3.5 and v2.0.0 bodies.

### [x] 2.1 — Domain models

File: `…/domain/albums/Albums.kt`
- `@JvmInline value class AlbumId(val value: String)`, `@JvmInline value class PhotoId(val value: String)`.
- `data class Photo(val id: PhotoId, val isVideo: Boolean)`; `data class Preview(val photo: PhotoId)` (what the screen hands the image loader; KDoc: a preview-size image of that photo).
- `data class Album(val id: AlbumId, val name: String, val itemCount: Int, val cover: PhotoId?, val shared: Boolean, val ownedByMe: Boolean, val updatedAt: Instant, val startDate: Instant?, val endDate: Instant?, val newestFirst: Boolean)` (`java.time.Instant`; `updatedAt` is already the later of updated/last-modified-asset).
- `data class AlbumContents(val photoCount: Int, val videoCount: Int, val firstItems: List<Photo>)`.
- `enum class AlbumFilter { All, Mine, SharedWithMe }`, `enum class AlbumSort { RecentlyUpdated, Name, MostItems }`.
- `fun List<Album>.filterAndSort(filter: AlbumFilter, sort: AlbumSort): List<Album>`: Mine = `ownedByMe`; SharedWithMe = `!ownedByMe`; RecentlyUpdated = `updatedAt` desc; Name = case-insensitive (`String.CASE_INSENSITIVE_ORDER`); MostItems = `itemCount` desc; ties by name.

### [x] 2.2 — Results and the repository

File: `…/domain/ServerResult.kt` (shared by later tickets)
`sealed interface ServerResult<out T> { data class Ok<T>(val value: T); data class Failed(val problem: Problem) : ServerResult<Nothing> }`, `enum class Problem { Unreachable, KeyRejected, KeyLacksPermission, NotSignedIn }`.
File: `…/domain/albums/AlbumRepository.kt`
`interface AlbumRepository { suspend fun albums(): ServerResult<List<Album>>; suspend fun album(id: AlbumId): ServerResult<Album>; suspend fun contents(album: Album, limit: Int): ServerResult<AlbumContents> }`.
Use cases in the same file: `class LoadAlbums(repo)` (`invoke(): ServerResult<List<Album>>`), `class LoadAlbum(repo)` (`invoke(id): ServerResult<AlbumPage>`, `data class AlbumPage(val album: Album, val contents: AlbumContents)`, calling `album(id)` then `contents(album, STRIP_SIZE)` with `const val STRIP_SIZE = 30`).

### [x] 2.3 — The Immich implementation

File: `…/data/immich/ImmichAlbumRepository.kt` — `internal class ImmichAlbumRepository(client: OkHttpClient, sessions: SessionStore) : AlbumRepository`
- A private `get(session, path, query)` helper on `Dispatchers.IO`: builds `apiUrl(...)` + query params, sets `API_KEY_HEADER`, maps 401 → `KeyRejected`, 403 → `KeyLacksPermission`, 5xx / `IOException` / `IllegalArgumentException` / unparseable body → `Unreachable`; no session → `NotSignedIn`. Mirror `ImmichServerConnector.parse`.
- `myId`: `GET users/me` → `MeDto(id)`, cached per `Session` (a `@Volatile` pair).
- `albums()`: `GET albums` → `List<AlbumDto>`. `AlbumDto(id, albumName, albumThumbnailAssetId: String? = null, assetCount: Int, shared: Boolean, updatedAt: String, lastModifiedAssetTimestamp: String? = null, startDate: String? = null, endDate: String? = null, order: String? = null, ownerId: String? = null, albumUsers: List<AlbumUserDto> = emptyList())`, `AlbumUserDto(user: UserDto(id))`. Owner = `ownerId ?: albumUsers.firstOrNull()?.user?.id` (comment: v1/v2 send `ownerId`; v3 dropped it and puts the owner first in `albumUsers`). `newestFirst = order != "asc"`. Dates via `Instant.parse`.
- `album(id)`: `GET albums/{id}` → same mapping.
- `contents(album, limit)`: `GET timeline/buckets?albumId=&order=` (`asc`/`desc` from `newestFirst`) → `[BucketDto(timeBucket, count)]`; then for each bucket in order `GET timeline/bucket?albumId=&timeBucket=&order=` → `BucketAssetsDto(id: List<String>, isImage: List<Boolean>)`, with at most 4 requests in flight (`Semaphore(4)` + `async`), results kept in bucket order. Counts = sum of `isImage` true / false; `firstItems` = the first `limit` pairs. Any failed bucket fails the whole call.

### [x] 2.4 — Wiring

File: `…/AppContainer.kt`
`private val albums = ImmichAlbumRepository(http, sessions)`; `val loadAlbums = LoadAlbums(albums)`, `val loadAlbum = LoadAlbum(albums)`.

### [x] 2.5 — Tests

Files: `test/…/domain/albums/AlbumsTest.kt` (filterAndSort, every filter × sort and the tie rule; `LoadAlbum` with a fake repo passes failures through and asks for 30), `test/…/domain/albums/Fakes.kt` (`FakeAlbumRepository`), `test/…/data/immich/ImmichAlbumRepositoryTest.kt` (MockWebServer, fixtures as `const val`s taken from the specs: `ALBUMS_V3_3_5` with `albumUsers` and no `ownerId`, `ALBUMS_V2_0_0` with `ownerId`, `owner`, `assets`; both map to equal `Album` lists; a null `albumThumbnailAssetId` and an empty album without `startDate`/`endDate`; `/users/me` is asked once for two `albums()` calls; contents over two buckets, counts and the 30-item cap, `order=asc` when the album says so, `[]` → zeros; 401/403/500/garbage/socket close; exact paths and queries; every request has `x-api-key`; no session → `NotSignedIn`).

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AlbumsTest' --tests '*ImmichAlbumRepositoryTest' --tests '*ArchitectureTest'`
- Both version fixtures produce the same domain albums.

---

## Checkpoint 3 — Slideshow settings per album · commit: `feat(slideshow): remember slideshow settings per album`

Goal: each album has its own slideshow settings, kept on the device, with the canvas's defaults.

### [x] 3.1 — Domain

File: `…/domain/slideshow/SlideshowSettings.kt`
`enum class SlideshowOrder { OldestFirst, Shuffle, NewestFirst }`, `enum class VideoMode { PlayMuted, PlayWithSound, Skip }`, `data class SlideshowSettings(val secondsPerPhoto: Int = 8, val order: SlideshowOrder = OldestFirst, val videos: VideoMode = PlayMuted, val showDateAndPlace: Boolean = true)` with `companion val SECONDS = listOf(8, 12, 20, 5)` (the canvas's cycle order) and `fun nextSeconds()`, `nextOrder()`, `nextVideos()`, `toggleDateAndPlace()` returning a copy (cycling in the canvas order; an unknown seconds value goes to 8). `interface SlideshowSettingsStore { suspend fun load(album: AlbumId): SlideshowSettings; suspend fun save(album: AlbumId, settings: SlideshowSettings) }`. Use cases `GetSlideshowSettings(store)`, `SaveSlideshowSettings(store)`.

### [x] 3.2 — Data

File: `…/data/settings/StoredSlideshowSettings.kt` — `internal class StoredSlideshowSettings(storage: StringStorage) : SlideshowSettingsStore`
One key per album, `slideshow.<albumId>`, value `seconds|ORDER|VIDEOS|true` (enum names). Missing or unparseable → `SlideshowSettings()`. On `Dispatchers.IO`.
File: `…/AppContainer.kt`: a second prefs file `slideshow` via `SharedPreferencesStorage`; expose `getSlideshowSettings`, `saveSlideshowSettings`.

### [x] 3.3 — Tests

Files: `test/…/domain/slideshow/SlideshowSettingsTest.kt` (each `next*` cycles in order and wraps; defaults), `test/…/data/settings/StoredSlideshowSettingsTest.kt` (round-trip; two albums kept apart; unknown album → defaults; corrupt value → defaults) with an in-memory `StringStorage` (reuse the one in `StoredSessionRepositoryTest` by moving it to `test/…/data/settings/MemoryStorage.kt`).

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*SlideshowSettings*'`

---

## Checkpoint 4 — Preview thumbnails with the key · commit: `feat(photos): load preview thumbnails with the API key`

Goal: a composable can show any `Preview`, fetched as `/assets/{id}/thumbnail?size=preview` with `x-api-key`, through `immichHttpClient()`, cached in memory and on disk.

### [ ] 4.1 — Dependencies

Files: `gradle/libs.versions.toml`, `app/build.gradle.kts`
`coil = "3.0.4"`; `coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }`, `coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }`; both `implementation`.

### [ ] 4.2 — The interceptor

Files: `…/data/immich/ImmichImages.kt` (the pure `previewUrl`, `previewCacheKey`), `…/data/immich/PreviewInterceptor.kt`
`internal class PreviewInterceptor(sessions: SessionStore) : coil3.intercept.Interceptor`: if `chain.request.data` is a `Preview`, load the session (none → `ErrorResult`), rewrite the request: `data(previewUrl(server, id))` (= `apiUrl(server, "assets/<id>/thumbnail")` + `size=preview`), `httpHeaders(NetworkHeaders.Builder().set(API_KEY_HEADER, key).build())`, `memoryCacheKey`/`diskCacheKey` = `previewCacheKey(id)` (`"preview:<id>"`) (so the key never ends up in a cache key), then `chain.withRequest(...).proceed()`. Other data passes through unchanged.
In `PreviewInterceptor.kt`, `internal fun immichImageLoader(context: PlatformContext, client: OkHttpClient, sessions: SessionStore): ImageLoader` = `ImageLoader.Builder(context).components { add(PreviewInterceptor(sessions)); add(OkHttpNetworkFetcherFactory(callFactory = { client })) }.crossfade(true).build()` (memory cache default 25 %, disk cache `cacheDir/previews` 256 MB).
KDoc: why an interceptor (the UI never builds a URL), and that the shared client's redirect interceptor still strips the key.

### [ ] 4.3 — Wiring

Files: `…/AppContainer.kt` (`fun imageLoader(context: Context): ImageLoader = immichImageLoader(...)`), `…/ImmichTvApp.kt` (implements `coil3.SingletonImageLoader.Factory`, `newImageLoader(context) = container.imageLoader(context)`).

### [ ] 4.4 — The tile

File: `…/ui/components/PhotoTile.kt`
`@Composable fun PhotoTile(photo: PhotoId?, modifier: Modifier, shape: Shape = RoundedCornerShape(12.dp), focusable: Boolean = false, onClick: () -> Unit = {})`: `AsyncImage(model = photo?.let(::Preview), contentScale = Crop)` on a `surface` placeholder; a video gets a small play badge (param `isVideo: Boolean = false`); focusable tiles use the house focus outline (`Modifier.focusOutline`). Variants for the 12dp tile and the 14dp album card corner, per the manual.

### [ ] 4.5 — Tests

File: `test/…/data/immich/PreviewInterceptorTest.kt`
Coil's `ImageRequest` needs an Android context, so the logic sits in pure functions in `ImmichImages.kt` that the interceptor calls: `internal fun previewUrl(server: ServerAddress, photo: PhotoId): String` and `internal fun previewCacheKey(photo: PhotoId): String`. Test them (exact path and `size=preview`; a trailing `/api` address gives the same URL; the cache key holds no key). A MockWebServer test fetches `previewUrl` with `immichHttpClient()` and the header and checks the key arrives, and is dropped on a redirect to a second server. `PreviewInterceptor.kt` (the interceptor and `immichImageLoader`) needs Android, stays thin and is excluded from Kover with a comment, like `KeystoreCipher`: add `"*.PreviewInterceptor*"` to `classes(...)` in `app/build.gradle.kts`. `ImmichImages.kt` is not excluded: it holds the tested functions.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PreviewInterceptorTest' --tests '*ArchitectureTest'`
- `./build.sh ./gradlew --console=plain assembleDebug` (Coil resolves and compiles).

---

## Checkpoint 5 — The frame: top menu, tabs and navigation · commit: `feat(ui): the top menu and its tabs`

Goal: after Setup the app shows the top menu with four tabs (Albums wired in the next checkpoint, others placeholders), the clock and the Settings gear; Back behaves.

### [ ] 5.1 — Navigation state

File: `…/ui/AppViewModel.kt`
`enum class Tab { Home, Albums, Photos, Search }`. `AppScreen.Home` becomes `data class Main(val tab: Tab)`; add `data class Album(val id: AlbumId)`. `selectTab(tab)`, `openAlbum(id)`. `back()`: `Album` → `Main(Albums)`; `Main(tab != Home)` → `Main(Home)`; `Main(Home)` → false; Setup as today (non-first-run → `Main(Home)`). `connected()` → `Main(Home)`.

### [ ] 5.2 — Top menu

File: `…/ui/components/TopMenu.kt`
`@Composable fun TopMenu(selected: Tab, onSelect: (Tab) -> Unit, onOpenSettings: () -> Unit, modifier)`: the logo (the image glyph tinted `fg`, "Immich TV" 26sp 800 — the canvas's amber logo stroke would break rule 3, so the logo is `fg`), pills Home/Albums/Photos/Search (22sp; selected = `fg` fill + `on-fg` text, others `mfg`-ish text on no fill; focus = amber outline; OK selects), Search with its icon, a spacer, the clock (24sp `mfg`, `HH:mm`, ticks each minute via `LaunchedEffect`, 24-hour per the device's `DateFormat.is24HourFormat`), then the Settings gear (`SecondaryButton` icon). 56dp tall. Add `ic_search` vector.

### [ ] 5.3 — Main screen

Files: `…/ui/main/MainScreen.kt`, `…/ui/main/PlaceholderTab.kt`
`MainScreen(tab, onSelectTab, onOpenSettings, onOpenAlbum, albums: @Composable () -> Unit)` : column with 48dp top / 96dp side padding, `TopMenu`, then the tab's content; Home/Photos/Search show `PlaceholderTab(title)` (the tab name, "Coming soon" in `mfg`). Focus enters on the selected menu pill. The old `ui/home/HomeScreen.kt` is removed (its gear moves to the menu).
File: `…/MainActivity.kt`: route `Main` and `Album`.

### [ ] 5.4 — Tests

File: `test/…/ui/AppViewModelTest.kt`: launch with a session → `Main(Home)`; `selectTab`; `openAlbum`; each Back rule above; `connected()` → `Main(Home)`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AppViewModelTest'`
- Previews of the top menu with each tab selected.

---

## Checkpoint 6 — The Albums tab · commit: `feat(albums): the Albums tab`

Goal: the Albums tab lists albums as the canvas's 5-column grid with filters, sort and the three extra states.

### [ ] 6.1 — ViewModel

File: `…/ui/albums/AlbumsViewModel.kt`
`data class AlbumsState(val load: AlbumsLoad, val filter: AlbumFilter = All, val sort: AlbumSort = RecentlyUpdated)`, `sealed interface AlbumsLoad { Loading; data class Loaded(val all: List<Album>); data class Failed(val problem: Problem) }`, derived `visible: List<Album>` via `filterAndSort`. `setFilter`, `cycleSort` (RecentlyUpdated → Name → MostItems → …), `retry()`. Factory from `AppContainer.loadAlbums`. Mirror `SetupViewModel`.

### [ ] 6.2 — Components

Files: `…/ui/components/Chip.kt` (filter chip: `raised` at rest, `fg` + `on-fg` 800 when selected, full round, 20sp, amber focus), `…/ui/components/AlbumCard.kt` (16:10 cover via `PhotoTile` with 14dp corners and the focus outline on the cover, name 22sp 700, meta 18sp `mfg`: "1,032 photos · Shared"; `onClick`), `…/ui/components/StateCards.kt` (`EmptyState(title, hint, actionLabel, onAction)` and `FailedState(problem, onRetry)`: one secondary/primary action, the failed text says to check the home network or VPN; `KeyRejected`/`KeyLacksPermission` say so and name album.read / asset.read / asset.view), `…/ui/components/Skeleton.kt` (`raised` blocks shaped like the loaded layout, a slow alpha pulse).

### [ ] 6.3 — Screen

File: `…/ui/albums/AlbumsScreen.kt`
Filter chips (All albums / My albums / Shared with me) left, the sort button (`SecondaryButton` with `ic_sort`: "Sort: Recently updated | A–Z | Most photos") right; `LazyVerticalGrid(GridCells.Fixed(5))` with 32dp gaps; the first card takes focus when loaded; the focused card stays in view; remembers the focused album id across opening an album and coming Back. Empty (no albums at all): "No albums yet", hint "Make one in Immich and it shows up here.", action Refresh. A filter with nothing in it: "Nothing here" with action "Show all albums". Failed: `FailedState` with Try again. Skeleton: 10 card skeletons. Previews for loaded, each empty, failed and skeleton.

### [ ] 6.4 — Tests

File: `test/…/ui/albums/AlbumsViewModelTest.kt` (fake repo from checkpoint 2): Loading → Loaded; empty list; Failed(Unreachable) then `retry()` → Loaded; filter and sort change `visible` without a new load (the fake counts calls).

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AlbumsViewModelTest'`

---

## Checkpoint 7 — The album screen · commit: `feat(albums): the album screen`

Goal: opening an album shows the canvas's Album board with real data and working, remembered settings.

### [ ] 7.1 — ViewModel

File: `…/ui/album/AlbumViewModel.kt`
`AlbumViewModel(id, loadAlbum, getSettings, saveSettings)`; state `AlbumLoad { Loading; Loaded(page: AlbumPage); Failed(problem) }` plus `settings: SlideshowSettings` (loaded at once, independent of the server). `cycleSeconds()`, `cycleOrder()`, `cycleVideos()`, `toggleDateAndPlace()` each update and save. `retry()`. Factory keyed by album id.

### [ ] 7.2 — Screen

File: `…/ui/album/AlbumScreen.kt`
Per the "2 · Album" board: a back link "‹ Albums" (secondary text button; OK = Back); the mosaic (560×616 cover spanning two rows + two 320×300 tiles, 18dp corners, 16dp gaps: the cover, then `firstItems` excluding the cover; missing tiles stay `surface`); title 64sp 800; "214 photos · 12 videos · June – August 2025" (zero parts dropped; one month → "June 2025"; same year → "June – August 2025"; across years → "December 2024 – January 2025"; formatted from `startDate`/`endDate` in the device zone, a pure `internal fun albumSummary(...)` in the ViewModel file so it is unit-tested); **Start slideshow** `PrimaryButton` with the play icon (requests focus on open; no-op until #5) and **Browse photos** `SecondaryButton` (no-op); "SLIDESHOW SETTINGS" 20sp `mfg` caps; four `SettingButton`s (new in `…/ui/components/SettingButton.kt`: `surface`, 16dp corners, 17sp `mfg` label over 23sp value, amber focus; OK cycles); the hint "Press OK on a setting to change it. It is remembered for this album." 18sp `mfg`; "In this album" 28sp and a `LazyRow` of 232dp `PhotoTile`s (12dp corners, focusable, OK does nothing yet). Empty album: the strip area says "This album is empty". Failed: `FailedState`. Skeleton mirrors the layout. Previews for each state.

### [ ] 7.3 — Tests

File: `test/…/ui/album/AlbumViewModelTest.kt`: Loading → Loaded; Failed → retry; settings load from the fake store and each cycle saves for this album only; `albumSummary` for zero videos, zero photos, one month, same year, across years, missing dates.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AlbumViewModelTest'`
- The full CI command: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`.

### [ ] 7.4 — The manual

File: `docs/design/README.md`
Record what this ticket settled: the top menu recipe (logo in `fg`, selected pill, clock, gear), the chip, the album card meta, the setting button, the empty/failed card texts. Remove "until the top menu exists, Home shows it top-right".

---

## Checkpoint 8 — The album photo grid · commit: `feat(albums): browse an album's photos`

Goal: Browse photos opens a 6-column grid of the album's previews with a heading per month, loaded month by month, built on a domain `Timeline` so #7 can show the whole library with the same grid.

### [ ] 8.1 — Domain timeline

File: `…/domain/photos/Timeline.kt`
`data class TimelineMonth(val month: java.time.YearMonth, val count: Int)`; `interface Timeline { suspend fun months(): ServerResult<List<TimelineMonth>>; suspend fun photos(month: YearMonth): ServerResult<List<Photo>> }` (KDoc: a set of photos grouped by month, newest or oldest first as the source orders them; #7 gives the whole library one). `AlbumRepository` gains `fun timeline(album: Album): Timeline`.

### [ ] 8.2 — The Immich implementation

File: `…/data/immich/ImmichAlbumRepository.kt`
`timeline(album)` returns a `Timeline` whose `months()` is `GET timeline/buckets?albumId=&order=` mapped to `TimelineMonth(YearMonth.from(LocalDate.parse(timeBucket.take(10))), count)` (the bucket is `YYYY-MM-DD…`, the first of the month), and `photos(month)` is `GET timeline/bucket?albumId=&timeBucket=<the bucket string for that month>&order=` → `Photo`s from `id`/`isImage`. The bucket strings are remembered from `months()` so the exact value Immich sent is passed back; a month not seen yet falls back to `<yyyy-MM>-01T00:00:00.000Z`. `contents()` reuses the same bucket calls.

### [ ] 8.3 — ViewModel

File: `…/ui/photos/PhotoGridViewModel.kt` (in `ui/photos/`, not `ui/album/`, so #7 reuses it)
`PhotoGridViewModel(timeline: Timeline)`; state `PhotoGridState(val load: GridLoad, val sections: List<MonthSection>)`, `GridLoad { Loading; Loaded; Empty; Failed(problem) }`, `MonthSection(month: YearMonth, count: Int, photos: MonthPhotos)` with `MonthPhotos { NotLoaded; Loading; Loaded(list); Failed(problem) }`. On start: `months()` then the first month's photos. `loadMonth(month)` loads a month once (no-op if loading or loaded); `retry()` reloads months, or retries a failed month. Factory taking a `Timeline`.

### [ ] 8.4 — Screen

Files: `…/ui/photos/PhotoGrid.kt` (the reusable grid), `…/ui/album/AlbumPhotosScreen.kt`
`PhotoGrid(state, onNeedMonth, onRetry, modifier)`: a `LazyVerticalGrid(GridCells.Fixed(6))`, 16dp gaps; per month a full-span heading ("June 2025" 28sp 700, count in `mfg`) then that month's `PhotoTile`s (square, 12dp corners, focusable, video badge, OK does nothing); a month not loaded yet shows `count` skeleton tiles and asks for its photos when it comes into view; a failed month shows a full-span "Couldn't load this month" with Try again. First tile takes focus once loaded. `AlbumPhotosScreen(albumName, state, onBack, …)`: back link "‹ <album name>", then the grid; whole-screen skeleton, empty ("This album is empty") and failed (`FailedState`) states. Previews for each state.

### [ ] 8.5 — Navigation

Files: `…/ui/AppViewModel.kt`, `…/MainActivity.kt`, `…/ui/album/AlbumScreen.kt`, `…/AppContainer.kt`
`AppScreen.AlbumPhotos(val album: Album)` (the album screen already holds it: its name for the back link, its id and order for the timeline); `openAlbumPhotos(album)`; `back()` from `AlbumPhotos` → `Album(album.id, focusBrowse = true)`. `AppScreen.Album` gains `focusBrowse: Boolean = false`: coming Back, the album screen focuses Browse photos instead of Start slideshow. Browse photos calls `openAlbumPhotos` (only once the album has loaded). Use case `AlbumTimeline(repo)` in `domain/albums/AlbumRepository.kt` (`invoke(album): Timeline`); `AppContainer` exposes `albumTimeline` so `ui/` never names `data/`.

### [ ] 8.6 — Tests

Files: `test/…/ui/photos/PhotoGridViewModelTest.kt` (a `FakeTimeline`: months then first month; `loadMonth` once only; a failed month then retry; empty months → `Empty`; months failing → `Failed` then retry → `Loaded`), `test/…/data/immich/ImmichAlbumRepositoryTest.kt` (timeline months → `YearMonth`s and counts; `photos(month)` sends the exact bucket string and `order`; failures mapped), `test/…/ui/AppViewModelTest.kt` (open album photos; Back → `Album(id, focusBrowse = true)`; Back from that album → Albums).

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*PhotoGridViewModelTest' --tests '*ImmichAlbumRepositoryTest' --tests '*AppViewModelTest' --tests '*ArchitectureTest'`
- The full CI command: `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`.

---

## Out of scope

- Playing the slideshow (#5) and videos (#6): Start slideshow is a focused no-op; the Videos setting is stored only.
- Opening a photo from the album photo grid (#7 adds the viewer).
- Opening a photo from the strip.
- The Home tab's content (#9), the Photos tab (#7), Search (#8).
- Servers older than v1.133: refused at Connect, not supported.
- Editing albums or anything that writes to the server.
