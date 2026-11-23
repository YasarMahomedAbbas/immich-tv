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
- API: no call added or changed. The stream is `GET /api/assets/{id}/video/playback` with the key header, unchanged since #6.
- Open risks:
  - Memory grids may have no videos, because Home drops them from memories. That part of "Done when" may only be checkable as "no video to open".
  - Robolectric may not build an `ExoPlayer`. If so, the fallback is a `SimpleBasePlayer` fake.
  - The TV window is about an hour from 13:52 UTC on 2026-11-23.
