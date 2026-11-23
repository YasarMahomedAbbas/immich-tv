# Checklist — feat: play a video when it's opened

Issue: #40 — feat: play a video when it's opened (https://github.com/YasarMahomedAbbas/immich-tv/issues/40)
Branch: `feat/40-play-opened-videos`
Problem: opening a video full-screen shows a still with a play badge. Videos only play in the slideshow.

## Done when

- Opening a video full-screen plays it, from every place that opens the viewer: Photos, the album and memory grids, and Home's Recently added row.
- Back returns to where the viewer was opened from, as it does today.
- The video plays with sound. OK pauses and resumes. Left and right move to the neighbour and stop the video. At the end the last frame stays and OK replays it.
- The play badge shows at bottom-left while the video loads, is paused or has ended, and is hidden while it plays.

## References

- Immich API: no call added or changed. The player is `AppContainer.videoPlayer` → `immichVideoPlayer` → `GET /api/assets/{id}/video/playback` with `x-api-key`, the same path from v1.133 to v3.3.5 (verified in #6 and #26).
- #6 (slideshow `VideoLayer`, the pattern mirrored here), #26 (library show videos).
- Manual: `docs/design/README.md`, the Photo row (to update) and the Slideshow row's video rules.

## Context

`PhotoViewerContent` (`ui/photos/PhotoViewerScreen.kt`) draws a still plus `VideoBadge` for a video. Only `SlideshowScreen.VideoLayer` owns a Media3 `Player`, created through a `newPlayer: (Context) -> Player` lambda that `MainActivity` gives as `container::videoPlayer`. The viewer gets the same lambda, so `ui/` still never names `data/`. The fitted `TextureView` picture moves out of the slideshow into a shared `ui/components/VideoPicture.kt`, and both screens use it.

The viewer's playback is a small pure state, `ViewerVideo` in `ui/photos/ViewerVideo.kt`, that can be unit-tested. Player callbacks and keys map onto it, and the screen renders from it.

Decisions the pipeline took on the user's behalf (flag: ux):
- The video plays with sound.
- OK pauses and resumes. Left and right move on and stop the video. At the end the last frame stays and OK replays it.
- The existing badge shows while the video loads, is paused or has ended.

Decided in planning:
- The video starts on opening, with the still under it until the first frame.
- A failure keeps the still and shows "Couldn't play this video." centred.
- The screen stays on while a video plays.
- Leaving the app pauses the video.
- One player per viewer: it is created on the first video, reused as Left and Right pass through videos, and released when the video leaves the screen and on Back.
- No seeking.

The viewer is opened by an OK press on a tile. That press's repeats and its KeyUp reach the viewer, so OK acts only on a KeyDown with `repeatCount == 0`.

## Test plan

- Unit: `ViewerVideoTest` covers:
  - opening starts in Loading
  - first frame → Playing
  - OK → Paused, and OK again → Playing
  - ended → Ended, and OK → replay (`run` goes up, back to Loading)
  - buffering mid-play is still Playing for the badge, and Loading before the first frame
  - an error → Failed, and OK does nothing
  - a report for another video changes nothing
  - the badge shows for Loading, Paused and Ended, and not for Playing or Failed
  - the screen stays on only while Playing
- Robolectric: `PhotoViewerVideoTest`, with a counting `newPlayer` built on a Media3 `ExoPlayer` (the viewer's state is driven through the `ViewerVideo` callbacks, not real decoding):
  - a video composes one player and a photo composes none
  - Left from a video to a photo releases the player, and video to video keeps the same one
  - an OK KeyDown with `repeatCount > 0`, or a KeyUp, doesn't toggle
  - Back is left to the activity
  - If Robolectric can't build an `ExoPlayer`, use a minimal `Player` fake through `androidx.media3.common.SimpleBasePlayer` (in `media3-common`, already a transitive dependency) and record that in NOTES.
- MockWebServer: none. The stream call is unchanged and `ImmichVideoTest` already covers it.
- Real server: read-only ranged `GET /api/assets/{id}/video/playback` for one timeline video with the header (206) and without it (401), and confirm the path in the running version's spec.
- On the TV: the user has it until about 14:50 UTC on 2026-11-23; after that, skip and record it. Take the adb lock (`mkdir /tmp/immich-tv-adb.lock`) before installing or driving, and `rmdir` it after, on failure too.
  - Photos tab: open a video. It plays with sound. OK pauses (badge shows) and resumes. Let it end: the last frame stays with the badge, and OK replays it. Right moves to the next item and the sound stops. Back returns to the video's tile.
  - An album's grid: the same open and Back, back to the tile.
  - Home's Recently added row: open a video if one is there. Back returns to Home.
  - A memory grid: open a video if one has any (memories may have none). Otherwise record it.
  - Pressing Home on the remote pauses the video.
  - `dumpsys meminfo` stays steady over several videos.

---

## Checkpoint 1 — The viewer's playback state · commit: `feat(photos): model a viewed video's playback`

Goal: a pure, tested state for one viewed video, with no player yet.

### [x] 1.1 — `ViewerVideo`

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/photos/ViewerVideo.kt`

- `internal enum class VideoPhase { Loading, Playing, Paused, Ended, Failed }`
- `internal data class ViewerVideo(val id: PhotoId, val phase: VideoPhase = VideoPhase.Loading, val run: Int = 0, val firstFrame: Boolean = false)`
- Each event is a pure function returning a new `ViewerVideo`. A report for another id (`from: PhotoId`) returns `this`.
  - `firstFrame(from)` → `firstFrame = true`, and Loading → Playing.
  - `playing(from)` → Playing if `firstFrame`.
  - `buffering(from)` → Loading only if `!firstFrame`.
  - `ended(from)` → Ended.
  - `failed(from)` → Failed.
  - `toggle()` changes the phase:
    - Playing → Paused
    - Paused → Playing
    - Ended → Loading with `run + 1` and `firstFrame` kept (the last frame stays until the replay's first frame)
    - Loading → Paused
    - Failed → unchanged
- Read-only properties:
  - `val playWhenReady get() = phase == Loading || phase == Playing`
  - `val showBadge get() = phase in setOf(Loading, Paused, Ended)`
  - `val keepScreenOn get() = phase == Playing`

### [x] 1.2 — `ViewerVideoTest`

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/photos/ViewerVideoTest.kt`. Plain JUnit, one test per item in the test plan's Unit line.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*ViewerVideoTest'`

---

## Checkpoint 2 — Share the video picture · commit: `refactor(slideshow): move the fitted video picture into components`

Goal: the slideshow uses a shared `VideoPicture`, with no change in behaviour.

### [x] 2.1 — `VideoPicture`

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/components/VideoPicture.kt`

`@Composable internal fun VideoPicture(player: Player, size: VideoSize?, visible: Boolean, modifier: Modifier = Modifier)`: the `Box` + `AndroidView(TextureView)` from `SlideshowScreen.VideoLayer`, moved as it is. It is non-focusable, `isOpaque = false`, `alpha = if (visible) 1f else 0f`, `player.setVideoTextureView` in the factory, and keeps the `aspectRatio` fit (height first for portrait).

### [x] 2.2 — Slideshow uses it

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/slideshow/SlideshowScreen.kt`. `VideoLayer`'s picture becomes `VideoPicture(player, size, visible = firstFrame)`. Nothing else changes.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*Slideshow*' assembleDebug`

---

## Checkpoint 3 — Play the video in the viewer · commit: `feat(photos): play a video when it's opened full-screen`

Goal: every viewer entry point plays a video, with the decided keys, sound, badge and failure behaviour.

### [ ] 3.1 — Wire the player in

- `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/photos/PhotoViewerScreen.kt`: `PhotoViewerScreen(viewModel, newPlayer: (Context) -> Player, onBack)`.
- `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`: the three `PhotoViewerScreen` calls (`AlbumPhotoViewer`, `MemoryPhotoViewer`, `PhotoViewer`) pass `newPlayer = container::videoPlayer`.

### [ ] 3.2 — `ViewerVideoLayer`

File: `PhotoViewerScreen.kt`. Composed only when `photo.isVideo && !LocalInspectionMode.current`, keyed so it leaves when a photo is shown. It mirrors `SlideshowScreen.VideoLayer`:
- It `remember`s one player from `newPlayer`, and a `DisposableEffect(player)` adds the listener, then removes it and `release()`s on dispose.
  - The listener maps `onRenderedFirstFrame` → `firstFrame`, `onIsPlayingChanged(true)` → `playing`, `STATE_BUFFERING` → `buffering`, `STATE_ENDED` → `ended` and `onPlayerError` → `failed`, each with the id from `currentMediaItem?.mediaId`.
- It sets `setAudioAttributes(USAGE_MEDIA / AUDIO_CONTENT_TYPE_MOVIE, handleAudioFocus = true)` once, with audio left enabled.
- `LaunchedEffect(player, video.id, video.run)` calls `setMediaItem(MediaItem.Builder().setMediaId(Video(photo.id).photo.value).build())`, `seekTo(0)` when `run > 0`, and `prepare()`.
- `playWhenReady = video.playWhenReady && lifecycle at least STARTED`.
- `VideoPicture(player, size, visible = video.firstFrame)`.
- The state is `var video by remember(photo.id) { mutableStateOf(ViewerVideo(photo.id)) }`.

The player outlives a move from one video to the next: the `remember` sits above the `photo.id` key, so it is released only when the viewed item stops being a video or the viewer leaves. Moving to a photo disposes the layer, which stops the sound.

### [ ] 3.3 — Keys, badge, failure, screen on

File: `PhotoViewerScreen.kt`.
- In `onPreviewKeyEvent`, `Key.DirectionCenter` and `Key.Enter` on a video toggle the state, only on `KeyDown` with `nativeKeyEvent.repeatCount == 0`. Left and Right are unchanged. On a photo, OK is not consumed.
- The `ViewerVideo` state is held in `PhotoViewerContent` (`remember(photo.id)`) and passed down to `ViewerVideoLayer` with its event callbacks, so the badge and the failure line render from it. `VideoBadge` shows when `photo.isVideo && video.showBadge`. It stays at the same place and margins.
- On `Failed`, show `stringResource(R.string.video_failed)` centred in the `photo_failed` style.
- `KeepScreenOn(video.keepScreenOn)`: move the slideshow's `KeepScreenOn` to `ui/components/KeepScreenOn.kt` as `internal` and use it from both screens.
- `app/src/main/res/values/strings.xml`: `<string name="video_failed">Couldn\'t play this video.</string>`.

### [ ] 3.4 — `PhotoViewerVideoTest`

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/photos/PhotoViewerVideoTest.kt`. Robolectric, set up like `PhotoGridFocusTest` (sdk 34, the television qualifiers, NATIVE graphics), with the Robolectric cases from the test plan. To reach the content without a ViewModel, make `PhotoViewerContent` `internal`. Its signature becomes `PhotoViewerContent(photo: Photo, newPlayer: (Context) -> Player, onMove: (Int) -> Unit)`.

### [ ] 3.5 — Manual

`docs/design/README.md`, the Photo row: "a video plays fitted on `bg`, with sound, over its still until the first frame. OK pauses and resumes, and at the end the last frame stays and OK replays it. Left and right move on and stop it. The play badge shows bottom-left at the slideshow's margins while it loads, is paused or has ended. A video that can't play stays a still with 'Couldn't play this video.'"

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*ViewerVideoTest' --tests '*PhotoViewerVideoTest' --tests '*PhotoGridFocusTest' --tests '*ArchitectureTest'`
- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`
- Seeing it work on the TV is self-verification's job (see the test plan's window and lock).

---

## Out of scope

- Seeking, a progress bar or a time readout in the viewer.
- A sound or mute toggle, and any use of the slideshow's Videos setting.
- Adding videos to memories (Home drops them today), or changing which items any grid shows.
- Live photos' motion part, transcoding or quality choice, and caching or buffering ahead.
- Changes to the slideshow's behaviour (checkpoint 2 is a move only).
