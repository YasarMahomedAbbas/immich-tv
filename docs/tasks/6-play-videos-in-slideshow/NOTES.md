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

## Review

- Fixed: `SlideshowVideoPreview` threw `No player in a preview` as soon as it was rendered. `Stage` composes `VideoLayer` for any current video (the 4.2 change), so it called the preview's throwing `newPlayer`, and the comment saying no layer would ask had gone stale. `Stage` now composes no video layer under `LocalInspectionMode`. Tooling only, never on a device; the Compose layer has no test harness, so this fix has no test.
- Raised: none
- API: `GET /api/assets/{id}/video/playback` (`playAssetVideo`): matches the v3.3.5 spec read from the running server's version: path, GET, optional `key` and `slug` never sent, `application/octet-stream`, `asset.view`; the key goes as the `x-api-key` header.
- Layers: clean

## Doc drift

- Applied: `CLAUDE.md`, Immich API, Auth: the key rule named only API calls and images; it now names video requests too, which `immichVideoPlayer` sends through `immichHttpClient()` (rung 2, a cross-cutting security rule).
- Skipped: `README.md` features and `docs/design/README.md` Slideshow row: already updated on this branch and checked against the code (1.5 s fade, fitted video and preview, no zoom on video, backdrop for portrait, the three Videos modes).
- Skipped: `CLAUDE.md` preview-size rule: it is about images and still holds; a video's preview uses it and the playback stream isn't an image.
- Skipped: `README.md` architecture block: "the Immich REST client" already covers the player, as it does the image loader, at that altitude; `AppContainer` remains the only place naming `data/`.
- Skipped: Media3 types in `ui/`: `ui/` still imports no `data/` and builds no URL (a `MediaItem` carries only the id); `ArchitectureTest` enforces the import rule and the planning note records the choice.
- Skipped: one player and nothing buffered ahead, the 30 s stall, and which failures count: the code's comments and `SlideshowViewModelTest` carry them.
- Skipped: Kover excludes for the player: commented in `app/build.gradle.kts`; the README's 80% and CI command still match the build and `ci.yml`.
- Stale, fixed separately: none

## Self verification

Result: partial

- CI build: 289 tests passed, 0 failed, 0 skipped; line coverage 89.5% (745 of 832, minimum 80%); `koverVerifyDebug` and `lintDebug` pass; APK built.
- Server: Immich v3.3.5, read from the running server.
- TV: installed on the Chromecast under the adb lock, inside the owner's window (11:59 to about 12:15 CET); driven in Deane Park (103 photos, 10 mp4 videos) and Deutschland Trip - 02 (14 videos of 32, the first ones QuickTime), by screenshots and one screen recording.
- Verified, API: `GET /api/assets/{id}/video/playback` (`playAssetVideo`) in the v3.3.5 spec: GET, `id` in the path, optional `key` and `slug` never sent, `application/octet-stream`, `asset.view`. With `x-api-key` and `Range: bytes=0-1023`: 206, `video/mp4`, 1024 bytes for all 10 of Deane Park's videos and 2 QuickTime originals. Without the key: 401 for all 12.
- Verified, playing: muted, 2 videos in a row in the recording played to their end and the show moved on, each near its own length (10.9 s and 7.2 s), not the 8 s per photo; with sound, 3 in a row ran from the first video to the end of the third in about 30 s. The picture moves in the recording, so the frames are the video and not its preview. Portrait videos sit fitted on the blurred backdrop, landscape fills the screen, with no zoom. Photo to video and video to video crossfade with no black or flash at the recording's 2 frames a second, so a shorter flash isn't ruled out. In Deutschland Trip - 02 the show went from 11 to 16 of 32 in 60 s with `c2.amlogic.avc.decoder` in the log and no playback error. The album's videos I listed are QuickTime originals, but the screenshots don't show which slide was which, so QuickTime playing is inferred from that, not seen item by item.
- Verified, sound: with sound, `dumpsys audio` shows the app's `AudioTrack` as `state:started`, `USAGE_MEDIA`, and the app on top of the focus stack; nobody listened. Muted, on a video: no started player. Home with sound: no started player.
- Verified, Skip: the count drops to 103; position 7 is the photo that was at 10, so all three early videos are gone.
- Verified, remote: on a video, OK pauses (the same frame at two screenshots 4 s and 5 s apart) and resumes from there; a video reached while paused holds. Left goes back to the video and right to the next slide; the count stays right. Back returns to the album with Start slideshow focused.
- Verified, Home: leaving and coming back within about 2 s, same process, the show goes on where it was. After about 6 s away the low-memory killer ended the backgrounded app (`lowmemorykiller`, 12:09:12) and it reopened on the Home tab; no crash, and it is Android's call for a stopped app on 2 GB.
- Verified, device: no playback errors, `FATAL` or `AndroidRuntime` in logcat. Total PSS over about 3 minutes of videos: 177 MB at the start, 263 MB after 60 s, then 224, 198 and 84 MB, so no growth. Settings put back: Deane Park on Play muted; the TV left on the launcher, with this debug build installed.
- Open, frame cost: `dumpsys gfxinfo` over 60 s of mostly portrait videos: 2,115 frames, 52.15% janky, p50 23 ms, p99 77 ms. Over Deane Park with a screen recording running: 22.95% janky, p50 21 ms, p99 93 ms. The slideshow's photos measured 0.69% to 2.77% in #5. The planning note's `TextureView` cost is real: the frames are late against 60 Hz. No stutter stood out in the recording, but at 2 frames a second it couldn't show one, so whether it can be seen is for a person to judge.
- Failed: none
- Not self-verifiable: a video failing on the TV, by hand: the forced failure wasn't run. All 138 videos the key's search returned are H.264, so none is known to fail to decode, and cutting the network mid-video would also cut adb. Skipping on failure, the 30 s stall and the failure count rest on `SlideshowViewModelTest` alone; `VideoLayer` reporting a player error has never run. To check: stop the server mid-video and see the show move on. Also by ear: that sound is heard with sound, and silence when muted.
