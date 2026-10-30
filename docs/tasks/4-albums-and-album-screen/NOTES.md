# Notes — feat: albums and the album screen

Issue: #4 — feat: albums and the album screen (https://github.com/YasarMahomedAbbas/immich-tv/issues/4)
Branch: `feat/4-albums-and-album-screen`

## Planning

- Approach: domain `Album`/`AlbumContents`/`SlideshowSettings` with `AlbumRepository` and `SlideshowSettingsStore`. `ImmichAlbumRepository` reads `GET /albums`, `/albums/{id}`, `/users/me` and the album timeline (`/timeline/buckets` + `/timeline/bucket` with `albumId`). A Coil 3 interceptor in data/ turns a domain `Preview` into the preview-thumbnail URL with `x-api-key` on `immichHttpClient()`. A top menu with tabs, the Albums grid and the Album screen are driven by ViewModels on hand-rolled navigation.
- Decided (pipeline, on the user's behalf; flag: api): Q1.1, the app's floor rises from v1.118 to v1.133. Connect reads `/server/version` and refuses older servers with a message naming the version. The album timeline has one shape from v1.133 to v3.3.5. Rejected: supporting the pre-1.133 timeline shape too (Q1.2), and a degraded album screen on old servers (Q1.3).
- Decided (pipeline, on the user's behalf; flag: api): Q2.1, images load with Coil 3 (coil-compose + coil-network-okhttp 3.0.4) on the shared `immichHttpClient()`. A data/ interceptor maps a domain `Preview` to `/assets/{id}/thumbnail?size=preview` plus the key header, with cache keys that hold no key. Rejected: hand-rolled loading (Q2.2).
- Decided (pipeline, on the user's behalf; flag: ux): Q3.1, slideshow settings are kept per album on the device, with defaults of 8 s, oldest first, play muted, and date & place on. Rejected: one set for the whole device (Q3.2).
- Decided (pipeline, on the user's behalf; flag: api): filter on the device from one `GET /albums`. The owner is `ownerId` (v1/v2) or else `albumUsers[0].user.id` (v3 dropped `ownerId`), compared with `/users/me` `id`, which is cached per session.
- Decided (pipeline, on the user's behalf; flag: api): photo/video counts come from the timeline's `isImage` column across all buckets (at most 4 requests in flight). `/search/statistics` needs asset.statistics, which the configured key lacks (403, checked live), and `/search/metadata` `total` is only the page count.
- Decided (pipeline, on the user's behalf; flag: ux): "Recently updated" sorts by the later of `updatedAt` and `lastModifiedAssetTimestamp`. Ties sort by name.
- Decided (pipeline, on the user's behalf; flag: ux): the strip is the first 30 items in the album's own order (`order` asc/desc). The mosaic is the cover plus the next two items.
- Decided (pipeline, on the user's behalf; flag: ux): navigation stays hand-rolled (`AppScreen.Main(tab)` / `Album(id)`). Back goes Album → Albums → Home → exit. The Settings gear moves into the top menu beside the clock.
- Decided (pipeline, on the user's behalf; flag: ux): Home, Photos and Search tabs are placeholders; Home is #9.
- Decided (pipeline, on the user's behalf; flag: api): a 401 is KeyRejected and a 403 is KeyLacksPermission, each with its own message. A 5xx, a network failure or a bad body counts as Unreachable.
- Decided: the session is cached in memory in `StoredSessionRepository`, because every image request asks for it and Keystore decryption per image would be slow.
- Decided: the album card says "N photos" from `assetCount` (videos included), as on the canvas. The logo glyph is `fg`, not the canvas's amber, because of rule 3.
- Open risks:
  - An album spanning many months costs one request per month to count photos and videos. This is fine for typical albums, but slow for one spanning years. If it bites, load the counts lazily.
  - The configured key lacks asset.statistics. If it also lacks album.read, asset.read or asset.view, the screens show the permission message. Self-verification will tell.
  - Coil 3.0.4 with Compose BOM 2024.12.01 and Kotlin 2.1.0 is expected to resolve; checkpoint 4's `assembleDebug` proves it.
- Decided (user, on the issue): option 1 for Browse photos. It opens a 6-column album photo grid with a heading per month, loaded month by month from the album timeline, with skeleton, empty and failed states; focus on the first tile, OK a no-op until #7, Back to the album with Browse photos focused; built so #7 can reuse it. Added as checkpoint 8. Rejected: a no-op with a follow-up ticket, and hiding the button.
- Approved (user, on the issue): the plan, with that revision. The other pipeline decisions (v1.133 floor, Coil 3, per-album slideshow settings, filtering and sorting on the device, counts from the timeline) stand. The TV is now connected (`TV_ADB` set), so the on-TV checks run.

## Implementation

- Shipped (paths under `app/src/main/java/io/github/yasarmahomedabbas/immichtv/`):
  - The v1.133 floor (`data/immich/ImmichServerConnector.kt:100`) and its Setup message; the session cached in memory.
  - Albums from Immich, both owner shapes (`data/immich/ImmichAlbumRepository.kt:40`), and the domain `Timeline` (`domain/photos/Timeline.kt:15`).
  - Slideshow settings per album (`data/settings/StoredSlideshowSettings.kt:15`).
  - Preview thumbnails through Coil with the key (`data/immich/PreviewInterceptor.kt:25`).
  - The top menu and tabs (`ui/components/TopMenu.kt:52`, `ui/main/MainScreen.kt:30`, `ui/AppViewModel.kt:75`).
  - The Albums tab (`ui/albums/AlbumsScreen.kt:61`), the album screen (`ui/album/AlbumScreen.kt:75`) and the album photo grid (`ui/album/AlbumPhotosScreen.kt:40`, `ui/photos/PhotoGrid.kt:60`).
  - 1dp = 1/1920 of the screen's width, keeping the system font scale (`ui/theme/Theme.kt:42`, applied at `:48`), with rule 10 in `docs/design/README.md`. This also fixes Setup from #3.
- Tests: 163 passed / 0 failed, 0 skipped; koverVerifyDebug, lintDebug and assembleDebug pass. `CanvasDensityTest` fails 3 of its 5 tests when `canvasDensity` returns the system density. No test covers `ImmichTvTheme` applying it (no Robolectric or instrumented tests here); that is for the TV.
- Outstanding: no unticked tasks. Not yet seen on the TV: the new screens and Setup at the new density, for self-verification. tv-material parts with built-in dp sizes now scale too and may look small next to the canvas's values. The keyed ViewModels for each opened album and album grid stay in the activity's store for the session.
- Contradicted the plan: the TV's screen is 960×540 dp (1920×1080 px at density 2.0), but the manual, the checklist and the code read canvas px as dp, so everything drew at twice its size. Decided (user, on the issue): option 1, one app-wide change in the theme, added as checkpoint 9. Rejected: the same fix as its own ticket, and halving every value and rewriting the manual and canvas in 960×540 dp.

## Review

- Fixed:
  - Album dates read a month early west of UTC: Immich sends the local day as midnight UTC (spec: "UTC representation of (local)"; all 30 live values are midnight), and the summary converted it to the device's zone, so 1 August showed as July in New York. `Album.startDate`/`endDate` are now `LocalDate`s, read in `data/`; the v3.3.5 fixture now sends midnight as the real server does.
  - After connecting to another server or key in Settings, the Albums tab, album screens and grids still showed what they had loaded from the old one, because their ViewModels live as long as the activity. They are now keyed by `AppViewModel.connection`, which goes up on each connect.
  - After a filter or sort change the grid stayed scrolled to wherever its first album landed, hiding the albums above it. A new list now starts from the top (`AlbumGrid`). No test: this is Compose scroll behaviour and the project has no Compose UI tests, so self-verification must check it on the TV.
- Raised: https://github.com/YasarMahomedAbbas/immich-tv/issues/18 (albums and album screens never refresh once loaded)
- For self-verification on the TV (not provable from the code):
  - The album screen adds up to about 1095 of 1080 canvas px, so the strip's tiles and their focus outline may be cut at the bottom.
  - D-pad Down from the last row of a month, when the next month is still non-focusable skeleton tiles: it must move on to the next month, at worst after a second press.
- API: `GET /server/version`, `/users/me`, `/albums`, `/albums/{id}`, `/timeline/buckets`, `/timeline/bucket` and `/assets/{id}/thumbnail?size=preview` match the v3.3.5 spec (the version the server runs) for method, path, parameters, required response fields and the permissions named. Live `/albums` and `/timeline/buckets` responses match the fixtures' shape. `/albums/{id}` and `/timeline/bucket` were checked against the spec only; the live check of them is self-verification's.
- Layers: clean, with one note left as the plan approved it: `strings.xml` names Immich's permission names (`album.read`, `asset.read`, `asset.view`) and the v1.133 floor in user-facing messages, so another photo server would need those strings changed too.

## Doc drift

- Applied: README.md:Install — floor v1.118 → v1.133 (cites the v1.133.0 release note) and key permissions `user.read`, `album.read`, `asset.read`, `asset.view` (checked against the v3.3.5 spec); rung 3.
- Applied: README.md:features — Albums line now says what the screen does and that Start slideshow waits for #5; rung 3.
- Applied: README.md:Architecture — a ViewModel holding server data is keyed by `AppViewModel.connection`; no test enforces it and the next screens would miss it; rung 3.
- Applied: CLAUDE.md:Immich API — "support both" goes back only to `MINIMUM`, the floor; rung 2.
- Applied: docs/design/README.md:Screens — Browse photos on Album, and the new Album photos row; rung 4.
- Skipped: Coil and `Preview` as the way to load images — `PhotoTile` and `PreviewInterceptor` show it, and the layer rule already forbids building URLs.
- Skipped: hand-rolled navigation, the in-memory session, the memory of the bucket string, the album dates and `ownerId` shapes — each is commented at the code and tested or found with one `rg`.
- Skipped: counts from the timeline rather than `/search/statistics` — NOTES and the repository's code cover it; only matters when changing that code.
- Skipped: CLAUDE.md trim — no line is newly enforced by the code.
- Checked, still true: CI command, `build.sh` lines, coverage claim (80%, Compose screens excluded), `immichHttpClient()` for image loading, preview-size images, design rule 10 and the manual's sizes for the new components.
- Stale, fixed separately: README.md:features — listed the slideshow, Photos and Search as if built; now marked planned with #5, #7, #8.

## Self verification

Result: partial

- CI build: 168 tests passed, 0 failed, coverage 87.5% (floor 80%), lint clean, APK built.
- Server: Immich v3.3.5 (read-only calls; paths and parameters checked against that tag's spec).
- TV: debug build installed on the Chromecast, connected to the real server; every path on the checklist's On the TV line driven, between 10:32 and 10:49 CET, inside the window the user gave.
- Verified:
  - `GET /server/version`, `/users/me`, `/albums` (15 albums) and `/thumbnail?size=preview` (200, image/jpeg) as the code builds them. `/albums/{id}` and `/timeline/buckets` + `/timeline/bucket` live for a 9-item album and a 699-item, 30-month one: each bucket's `id` and `isImage` have the bucket's count, totals match `assetCount`. The 699-item album's 564 + 135 split showed on the TV as "564 photos · 135 videos". No fixture changed.
  - Density: Setup, Albums and Album render at the canvas's sizes; Setup's Connect is no longer squashed.
  - Albums: 5-column grid with covers, filters (10 shared with me of 15), all three sorts in the right order. After each filter or sort change the grid's first row sat at the top. Only a little scroll was possible with 15 albums, and the D-pad can reach the toolbar only through the first row, so this is weak proof of the scroll-to-top fix.
  - Album: Start slideshow focused on open, the four settings cycle and survive leaving the album, a reinstall and a restart; strip tiles and their outline are fully on screen and the strip scrolls.
  - Album photos: month headings, focus on the first tile, Down across months into unloaded ones, 31 fast presses with no ANR (frames skip on this debug build), focus kept as photos arrive. Back → album with Browse photos focused → Albums with the same card focused → Home → exit. Gear → Setup, Back → Home.
  - Reinstalled after each fix and re-driven.
- Failed, then fixed here:
  - Down through the album photo grid froze the app until an ANR killed it. Tiles of an unloaded month are now focusable with stable keys.
  - The fourth setting was crushed to a sliver and pushed the strip off screen; the canvas itself overflows (962 of 760 wide, 1088 of 1080 tall). Now two rows of two, mosaic rows 284 instead of 300. The canvas still shows the old layout.
  - tv-material's default button padding made buttons 45 px tall against the canvas's 75. Buttons now set the canvas's sizes, with a compact variant for Sort and Show.
- Failed, still open: none.
- Not self-verifiable:
  - Setup: with the keyboard closed, Down sent over adb did not leave the Server address field, with either input source. The keyboard's Next does move on. Check Down with the real remote; this screen's fields come from #3.
  - The albums and album empty and failed states, and a month that fails to load: not driven, the server has none and nothing here may change it. If a focused tile's month fails, its tile goes away and focus has to recover; check that by hand.
- Small things seen, left: Up from the first album card lands on My albums, not All albums. The strip's and grid's first tiles sit 8dp right of their headings, the focus room.
- Incident: a Down that didn't move focus (above) sent the API key into Setup's visible address field, so it showed in clear on the TV for about 40 seconds and in one screenshot read during this run. The app was force-stopped and those screenshots deleted. The key stays valid, so rotating it is the user's call. The TV keeps a saved session with this key, as a normal Connect does.
