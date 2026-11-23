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
