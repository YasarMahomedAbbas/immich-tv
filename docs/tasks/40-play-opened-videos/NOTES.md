# Notes — feat: play a video when it's opened

Issue: #40 — feat: play a video when it's opened (https://github.com/YasarMahomedAbbas/immich-tv/issues/40)
Branch: `feat/40-play-opened-videos`

## Planning

- Approach: the viewer gets the slideshow's `newPlayer` lambda and its own video layer, which mirrors `SlideshowScreen.VideoLayer`. The layer runs from a pure, tested `ViewerVideo` state (loading, playing, paused, ended, failed). The fitted video picture becomes a shared component.
- Decided (pipeline, on the user's behalf; flag: ux): Q1-1, a video opened in the viewer plays with sound.
- Decided (pipeline, on the user's behalf; flag: ux): Q2-1. OK pauses and resumes. Left and Right move on and stop the video. At the end the last frame stays and OK replays it.
- Decided (pipeline, on the user's behalf; flag: ux): Q3-1. The existing play badge shows bottom-left while the video loads, is paused or has ended, and is hidden while it plays.
- Decided (planning, flag if you disagree):
  - The video starts on opening, over its still until the first frame.
  - A failure keeps the still and shows "Couldn't play this video."
  - The screen stays on while a video plays, and leaving the app pauses it.
  - One player per viewer, reused between videos and released when a photo or Back takes over.
  - OK acts only on a fresh KeyDown, because the tile's OK press carries into the viewer.
  - No seeking.
  - The manual's Photo row is updated.
- Brief approved (pipeline, on the user's behalf; flag: ux): option 1, the plan as written. Rejected: dropping the shared-picture refactor (option 2).
- API: no call added or changed. The stream is `GET /api/assets/{id}/video/playback` with the key header, unchanged since #6.
- Open risks:
  - Memory grids may have no videos, because Home drops them from memories. That part of "Done when" may only be checkable as "no video to open".
  - Robolectric may not build an `ExoPlayer`. If so, the fallback is a `SimpleBasePlayer` fake.
  - The TV window is about an hour from 13:52 UTC on 2026-11-23.

## Implementation

- Shipped:
  - `ViewerVideo` (`ui/photos/ViewerVideo.kt:14`): the viewed video's pure playback state (loading, playing, paused, ended, failed), its OK toggle and the badge, screen-on and play-when-ready rules.
  - `VideoPicture` (`ui/components/VideoPicture.kt:20`): the slideshow's fitted `TextureView` picture, moved as it is; the slideshow uses it (`ui/slideshow/SlideshowScreen.kt:439`). `KeepScreenOn` moved to `ui/components/KeepScreenOn.kt:9` and is shared.
  - The viewer plays videos: `PhotoViewerScreen` takes `newPlayer` (`ui/photos/PhotoViewerScreen.kt:69`), given `container::videoPlayer` by the three viewer routes (`MainActivity.kt:170,175,180`). `ViewerVideoLayer` (`PhotoViewerScreen.kt:159`) holds one player across video-to-video moves, releases it when a photo or Back takes over, plays with movie audio attributes and audio focus, and pauses when the app stops. OK on a video toggles only on a fresh KeyDown (`PhotoViewerScreen.kt:112`); OK on a photo isn't consumed. The badge shows while loading, paused or ended; a failure shows "Couldn't play this video." (`strings.xml:71`); the screen stays on while playing (`PhotoViewerScreen.kt:98`).
  - Manual: the Photo row in `docs/design/README.md:51`.
- Tests: full CI command green, 564 passed / 0 failed / 0 skipped (`ViewerVideoTest` 10, `PhotoViewerVideoTest` 6). Removing the `repeatCount == 0` guard fails `only a fresh OK press toggles`. Lint: no errors; one new `SwitchIntDef` warning on the listener's `when (playbackState)`, the same one the slideshow's identical listener already carries.
- Outstanding: none in the checklist. On-TV checks (sound, pause, replay, Back to the tile, Home pauses, memory) are self-verification's.
- Contradicted the plan: `PhotoViewerVideoTest` uses a `SimpleBasePlayer` fake instead of a plain `ExoPlayer`. Robolectric isn't the blocker: a plain `ExoPlayer` can't take the viewer's mediaId-only item, which only the data layer's source factory resolves. The plan names this fallback, and the checklist's 3.4 now says so.

## Review

- Fixed: a player report delivered in the same frame as an OK pause undid it. Example: OK pressed just as playback starts, then the player's `isPlaying(true)` (sent before it heard of the pause) arrives. `ViewerVideo.playing` moved Paused back to Playing, and because `playWhenReady` went true→false→true within that frame, the effect never re-ran, so the video kept playing with the user's pause lost. `playing` and `buffering` now act only while the video is meant to run (`ViewerVideo.kt`). The new test `a late playing or buffering report doesn't undo a pause` fails without the fix. Full CI green, 565 passed / 0 failed.
- Raised: none
- API: no call added or changed. The viewer uses the existing `container::videoPlayer` → `GET /api/assets/{id}/video/playback` with the `x-api-key` header through `immichHttpClient()`, the same as the slideshow.
- Layers: clean. `ui/` names only the domain `Photo`/`PhotoId`/`Video` and Media3; the player comes in as a lambda from `MainActivity`.

## Doc drift

- Applied: README.md:Features (Albums, Photos) — opening a video full screen now plays it with sound and OK pauses it; the lines only said "open one full screen" (rung 3, using the app).
- Skipped: docs/design/README.md Photo row — already updated on this branch during implementation and matches the code (sound, OK pause/replay, badge states, "Couldn't play this video.").
- Skipped: `VideoPicture` and `KeepScreenOn` in `ui/components/` — non-visual helpers, like `FullScreenImage` and `RefreshOnStart`, which the manual's Components list also leaves out; one `rg` finds them.
- Skipped: README Development's test list — it names the focus tests; `PhotoViewerVideoTest` is found by `rg` and no session would act wrongly without it.
- Skipped: CLAUDE.md — no new cross-cutting rule; the viewer's player comes through `container::videoPlayer` and `immichHttpClient()` as CLAUDE.md already says, and `ArchitectureTest` enforces the layers.
- Stale, fixed separately: none

## Self verification

Result: partial

- CI build: the full CI command in Docker is green. 565 tests passed, 0 failed, 0 skipped. Application line coverage is 92.0% (the bound is 80). Lint has 0 errors and 31 warnings. The debug APK built.
- Server: Immich 3.3.6. Checked against the v3.3.6 spec only, because the verification key can't fetch media.
- TV: not run. The Chromecast answered ping, but adb on `TV_ADB` (port 5555) refused the connection at every try from 14:05 to 14:16 UTC on 2026-11-23, inside the user's window, with the adb lock held while connecting. Network debugging looks switched off on the TV.
- Verified:
  - In the v3.3.6 spec, `GET /assets/{id}/video/playback` exists with an `id` path parameter and accepts the `api_key` header scheme. It needs the `asset.view` permission. The branch adds or changes no call.
  - Without the key, a ranged GET of a real timeline video returns 401.
  - With the verification key it returns 403 "Missing required permission: asset.view", and `/thumbnail?size=preview` returns the same. The verify key has only read scopes (no `asset.view`), so the stream's 206 couldn't be shown here. The app's own key on the TV already plays slideshow videos through this same path (#6).
- Failed: none
- Not self-verifiable (check on the TV by hand):
  - Photos tab: open a video. It plays with sound. OK pauses it (the badge shows) and resumes it. Let it end: the last frame stays with the badge, and OK replays it. Right moves on and the sound stops. Back returns to the video's tile.
  - An album grid: open a video, then Back returns to its tile.
  - Home's Recently added row: open a video if one is there. Back returns to Home.
  - A memory grid: open a video if a memory has one (memories may have none).
  - The remote's Home button pauses the video.
  - `dumpsys meminfo` stays steady over several videos.
