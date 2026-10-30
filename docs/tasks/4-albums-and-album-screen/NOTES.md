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
