# Notes — feat: albums and the album screen

Issue: #4 — feat: albums and the album screen (https://github.com/YasarMahomedAbbas/immich-tv/issues/4)
Branch: `feat/4-albums-and-album-screen`

## Planning

- Approach: domain `Album`/`AlbumContents`/`SlideshowSettings` with `AlbumRepository` and `SlideshowSettingsStore`. `ImmichAlbumRepository` reads `GET /albums`, `/albums/{id}`, `/users/me` and the album timeline (`/timeline/buckets` + `/timeline/bucket` with `albumId`). A Coil 3 interceptor in data/ turns a domain `Preview` into the preview-thumbnail URL with `x-api-key` on `immichHttpClient()`. A top menu with tabs, the Albums grid and the Album screen are driven by ViewModels on hand-rolled navigation.
- Decided (pipeline, on the user's behalf; flag: api): Q1.1, the app's floor rises from v1.118 to v1.133. Connect reads `/server/version` and refuses older servers with a message naming the version. The album timeline has one shape from v1.133 to v3.3.5. Rejected: supporting the pre-1.133 timeline shape too (Q1.2), and a degraded album screen on old servers (Q1.3).
- Decided (pipeline, on the user's behalf; flag: api): Q2.1, images load with Coil 3 (coil-compose + coil-network-okhttp 3.0.4) on the shared `immichHttpClient()`. A data/ interceptor maps a domain `Preview` to `/assets/{id}/thumbnail?size=preview` plus the key header, with cache keys that hold no key. Rejected: hand-rolled loading (Q2.2).
- Decided (pipeline, on the user's behalf; flag: ux): Q3.1, slideshow settings are kept per album on the device, with defaults of 8 s, oldest first, play muted, and date & place on. Rejected: one set for the whole device (Q3.2).
- Decided (pipeline, on the user's behalf; flag: scope): Q4.1, Browse photos is a no-op placeholder, and a follow-up ticket for an album photo grid is raised. Rejected: building the grid here (Q4.2).
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
  - `TV_ADB` is empty in `verify.env`, so the on-TV checks will likely be skipped.
