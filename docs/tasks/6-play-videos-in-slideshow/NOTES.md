# Notes — feat: play videos in the slideshow

Issue: #6 — feat: play videos in the slideshow (https://github.com/YasarMahomedAbbas/immich-tv/issues/6)
Branch: `feat/6-play-videos-in-slideshow`

## Planning

- Approach: the Videos setting filters at load; the screen hands one Media3 player a domain `Video`, and `data/` turns it into the playback stream with the key header on the one HTTP client. The video's preview crossfades in as a photo does, then the video replaces it at its first frame; the video's end moves the show on.
- Decided (pipeline, on the user's behalf; flag: api): Q1.1, the screen hands the player a domain "video of this item" value and the server layer's media source resolves the address and key header, as images work. The screen uses Media3 types, which the layer rules allow. Rejected: a domain player interface hiding Media3 (Q1.2), which leaks a surface or wraps a lot for no second implementation.
- Decided (pipeline, on the user's behalf; flag: ux): Q2.1, the preview crossfades in as a photo does, then the video starts and replaces it at its first frame. Rejected: cutting directly to the video (Q2.2), which breaks the manual's crossfade and shows a dark screen while buffering.
- Decided (pipeline, on the user's behalf; flag: ux, open-risk): Q3.1, only failures to reach the server count towards five in a row; a video this TV can't decode is skipped without counting. Rejected: every failure counts (Q3.2), which ends the show with a wrong message; none count (Q3.3), which tries a run of videos one by one with the server down.
- Decided (pipeline, on the user's behalf; flag: ux), the "also decided" list:
  - Skip leaves videos out at load; the other two modes keep them in the chosen order. Muted disables the audio track; with sound takes audio focus.
  - One player, for the current video only, released on a photo. Next-slide readiness means the preview is ready; nothing is buffered ahead.
  - A video's end moves the show on, with no time cap; the time-per-photo setting doesn't apply. A sole video loops. Pause freezes it, left/right skip, leaving the app pauses it.
  - 30 s with no frame moving (no first frame, or stuck buffering) counts as a failure to reach the server and skips it. A failed video leaves the show until restart; a failed preview also skips it.
  - No zoom or drift on video. Fitted, never cropped; portrait on the same blurred, darkened backdrop. Clock, date and place, controls and position bar unchanged.
- Decided while writing the checklist (not asked; flag if you disagree):
  - A video's preview being ready doesn't reset the failure count: a cached preview would otherwise hide a dead server. Its first frame does.
  - If undecodable videos are all that was left, the show is `Empty` rather than `Failed(Unreachable)`, which would be the wrong message Q3.1 avoids.
  - `TextureView`, not `SurfaceView`: it is transparent until its first frame, so the preview below shows through with no z-order handling. The cost is checked on the TV.
  - Media3 1.5.1: the newer lines aren't needed, and it builds on the same okhttp 4.12.0 the app uses.
- API: `GET /api/assets/{id}/video/playback` is in the v1.133 and v3.3.5 specs at the same path, permission `asset.view` on v3.3.5 (already a documented key permission). On the real v3.3.5 server: `x-api-key` with a range gets 206 and `video/mp4`, no key gets 401.
- Open risks:
  - open-risk: some originals may not decode on the Chromecast. The server has no transcodes for the 25 longest of its 263 videos (mp4 or QuickTime originals, longest about 2 minutes). They are skipped; only the TV check can measure how many.
  - The on-TV check is only allowed until about 12:20 CET on 2026-10-30, under the adb lock in the issue's comment; after that it is skipped and recorded as skipped.

## Implementation

- Shipped: the Videos setting decides at load (`domain/slideshow/Slideshow.kt:24`); the playback stream with the key as a header (`data/immich/ImmichVideo.kt:15`) and a player that resolves a domain `Video` on the loader thread (`data/immich/ImmichVideoPlayer.kt:30`, `AppContainer.kt:51`); the show's video rules in virtual time (`ui/slideshow/SlideshowViewModel.kt:155`, `:166`, `:180`, `:307`); the video on screen (`ui/slideshow/SlideshowScreen.kt:370`); the manual's Slideshow row and `README.md`.
- Tests: 289 passed / 0 failed / 0 skipped; `koverVerifyDebug`, `lintDebug` and `assembleDebug` pass.
- Outstanding: nothing on the checklist is unticked, but no video has played on a device yet: playback, sound, the first-frame hand-over and the real-server checks are self-verification's (see the test plan's window and lock). The Compose layer has no unit tests.
- Contradicted the plan:
  - 4.2 asked for `VideoLayer` to be composed only after the fade and also to reuse its player between videos, which can't both hold: each video-to-video move would release it. `VideoLayer` now stays composed for any current video and takes `showing`; it streams only while showing and otherwise stops, so the old video's sound ends as the next fades in.
  - 4.2 relied on the `TextureView` being transparent until its first frame and on composing it once the size is known. A reused view keeps the last video's last frame, and `onVideoSizeChanged` isn't sure to fire again for a same-size video, so the view is always composed and its alpha stays 0 until `onRenderedFirstFrame` for this video.
  - 4.2's `onFailed: (serverFailure)` became `(PhotoId, serverFailure)`, and the other reports take a `PhotoId` too: each names the video the player has, so a late one is ignored as the ViewModel's rule intends.
  - 2.3: `"*.VideoSourceFactory*"` joined the Kover excludes, since the factory is its own class.
