# Checklist — feat: play videos in the slideshow

Issue: #6 — feat: play videos in the slideshow (https://github.com/YasarMahomedAbbas/immich-tv/issues/6)
Branch: `feat/6-play-videos-in-slideshow`
Problem: the album's Videos setting offers "Play muted", "Play with sound" and "Skip", but the slideshow leaves every video out whatever it says.

## Done when

- With play muted or with sound, a video in the slideshow plays from start to end (Media3 / ExoPlayer, the Immich playback stream with the API key header), then the slideshow moves on.
- Skip leaves videos out.
- Play/pause and left/right work on a video the same way as on a photo.
- A video that fails to play is skipped, not a stuck screen.

## References

- Immich API: `GET /api/assets/{id}/video/playback` (https://api.immich.app, operation `playAssetVideo`). Same path in the v1.133.0 and v3.3.5 specs; response `application/octet-stream`; permission `asset.view` on v3.3.5. Its optional `key` and `slug` query parameters are never used: the key goes in the `x-api-key` header. On the real v3.3.5 server, a range request gets 206 with `video/mp4`, and no key gets 401. `/thumbnail?size=preview` gives the video's preview as for a photo.
- Issue comment: the TV window (until about 12:20 CET on 2026-10-30) and the adb lock.
- Manual: `docs/design/README.md`, Slideshow row and rules 2, 3 and 5.

## Context

`Slide.photo.isVideo` already comes from the timeline's `isImage` column and `LoadSlideshow` drops those items; `VideoMode` is stored per album. The analogue is images: a screen hands Coil a domain `Preview` and `PreviewInterceptor` turns it into a URL and key header, so here a screen hands the player a domain `Video` and `ImmichVideoPlayer` does the same (Q1.1). The video's preview crossfades through the existing `SlideLayer`, then the video starts over it (Q2.1). `SlideshowViewModel` owns timing with no Android in it, so the end, stall and failure rules are tested in virtual time. Only failures to reach the server count towards `MAX_FAILURES` (Q3.1). Every decision is in `NOTES.md`.

## Test plan

- Unit: `LoadSlideshow` keeps videos for muted and with sound, in each order, and drops them for Skip. `SlideshowViewModel` in virtual time: a video ignores the time per photo; its end moves on, waiting if the next isn't ready; a sole video replays (`run` goes up); pause holds the stall clock; 30 s without playing fails it, also mid-video; playing restarts the clock; skips reset it; late reports about another slide change nothing; an undecodable failure doesn't count and a server failure does; a video's preview doesn't reset the count and its first frame does; undecodable last item gives `Empty`; five server failures give `Failed`. `isServerFailure` by error code.
- MockWebServer: `videoStream`'s request through `immichHttpClient()`: path, `x-api-key` header, no key in the URL, a `Range` request answered 206 reads through, and a redirect to another host loses the key. The response doesn't differ by version.
- Real server: read-only ranged `GET /api/assets/{id}/video/playback` for one mp4 and one QuickTime video with the header (206), and without it (401); confirm the path against the running version's spec.
- On the TV (until about 12:20 CET on 2026-10-30, otherwise skip and record it; `mkdir /tmp/immich-tv-adb.lock` before the first adb command that installs, opens or drives the app, wait and retry if it fails, `rmdir` it when done, on failure too): Deane Park (103 photos, 10 videos). Muted: a video plays to its end with no sound, then moves on. With sound: audio is heard or `dumpsys audio` shows the app playing. Skip: no video appears and the count drops. Pause, resume, left and right on a video. Preview crossfades, then the video takes over without a flash. A portrait video on its backdrop. Back returns to the album with Start focused. Home pauses it. A forced failure (a video the TV can't play, or the server unreachable mid-video) skips. `dumpsys meminfo` steady and `dumpsys gfxinfo` reasonable over several videos. Record how many videos failed to decode.

---

## Checkpoint 1 — The setting decides at load · commit: `feat(slideshow): let the Videos setting keep videos in the show`

Goal: `LoadSlideshow` obeys the setting and the Media3 dependency builds. Until checkpoint 4, a kept video shows as a still preview on the photo timer.

### [x] 1.1 — Filter by mode

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/slideshow/Slideshow.kt`
`suspend operator fun invoke(album: Album, order: SlideshowOrder, videos: VideoMode): ServerResult<List<Slide>>`. Drop `photo.isVideo` only when `videos == VideoMode.Skip`; remove the old comment. Update the call in `SlideshowViewModel.load()` to pass `settings.videos`, and `LoadSlideshowTest`, adding the keep and drop cases per mode.

### [x] 1.2 — Dependencies

Files: `gradle/libs.versions.toml`, `app/build.gradle.kts`
`media3 = "1.5.1"`; `media3-exoplayer` and `media3-datasource-okhttp` (group `androidx.media3`), both `implementation`. No `media3-ui`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*LoadSlideshowTest' --tests '*SlideshowViewModelTest'`
- `./build.sh ./gradlew --console=plain assembleDebug` resolves Media3.

---

## Checkpoint 2 — The stream behind the door · commit: `feat(api): open a video's playback stream with the key in a header`

Goal: `data/` can turn a domain `Video` into an authenticated stream; nothing plays yet.

### [x] 2.1 — The domain value

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/albums/Albums.kt`
Next to `Preview`: `/** A video to play: what a screen hands the player. */ data class Video(val photo: PhotoId)`.

### [x] 2.2 — The stream

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/data/immich/ImmichVideo.kt` (mirror `ImmichImages.kt`)
`internal data class VideoStream(val url: String, val headers: Map<String, String>)` with a `toString` that omits the headers, so the key can't reach a log. `internal fun videoStream(session: Session, video: Video): VideoStream`: `apiUrl(session.server, "assets")` + path segments id, `video`, `playback`; headers `API_KEY_HEADER` to the key.

Test: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/data/immich/ImmichVideoTest.kt`, with MockWebServer and `immichHttpClient()`, per the test plan.

### [x] 2.3 — The player

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/data/immich/ImmichVideoPlayer.kt` (mirror `PreviewInterceptor.kt`)
`internal fun immichVideoPlayer(context: Context, client: OkHttpClient, sessions: SessionStore): ExoPlayer`. Build `ExoPlayer.Builder(context).setMediaSourceFactory(...)`. The factory wraps `ProgressiveMediaSource.Factory(ResolvingDataSource.Factory(OkHttpDataSource.Factory(client), resolver))`: `createMediaSource(item)` sets the item's URI to `video:<mediaId>`, so no address or key is in a `MediaItem`. The resolver, on the loader thread, does `runBlocking { sessions.load() }` (`IOException` if signed out), calls `videoStream`, and returns `dataSpec.withUri(...).withAdditionalHeaders(...)`. The client is the one `immichHttpClient()`, so a redirect off the server loses the key.

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/AppContainer.kt`
`fun videoPlayer(context: Context): ExoPlayer = immichVideoPlayer(context, http, sessions)`, next to `imageLoader`.

File: `app/build.gradle.kts`
Add `"*.ImmichVideoPlayer*"` and `"*.VideoSourceFactory*"` (its private media source factory) to the Kover excludes, with a comment as for `PreviewInterceptor`: it needs an Android context, and the address and header are pure and tested in `ImmichVideo`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*ImmichVideoTest' --tests '*ArchitectureTest'`
- The key appears only as a request header in the test's recorded request.

---

## Checkpoint 3 — The show's rules for a video · commit: `feat(slideshow): move on when a video ends and skip one that fails`

Goal: the ViewModel plays by the video's reports instead of the timer, tested in virtual time; the screen doesn't send them yet.

### [ ] 3.1 — State

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/slideshow/SlideshowViewModel.kt`
`SlideshowState` gains `val run: Int = 0`: goes up on every `moveBy`, so a sole video replays on its own move. Private fields: `videoEnded: Boolean`, `videoMoving: Boolean`, `videoWaitMs: Long`, all reset in `moveBy`, in `load` and when the current slide is dropped. Companion: `const val VIDEO_STALL_MS = 30_000L`.

### [ ] 3.2 — Reports from the player

Same file. Each ignores a `photo` that isn't the current slide's:
- `fun videoPlaying(photo: PhotoId)`: `videoMoving = true`, `videoWaitMs = 0`, `failuresInARow = 0`.
- `fun videoWaiting(photo: PhotoId)`: `videoMoving = false`.
- `fun videoEnded(photo: PhotoId)`: `videoEnded = true`, `videoMoving = false`.
- `fun videoFailed(photo: PhotoId, serverFailure: Boolean)`.

`imageFailed` and `videoFailed` share `private fun drop(photo: PhotoId, counts: Boolean)`, holding `imageFailed`'s present body. When `counts` is false, `failuresInARow` doesn't change, and if no slides are left `load` becomes `SlideshowLoad.Empty`. `imageFailed` passes `counts = true`.

`imageReady` resets `failuresInARow` only when the slide isn't a video: look it up in `slides`, and treat an id no longer in the show as not a video.

### [ ] 3.3 — The clock

Same file. In `play()`, after the pause check, when `state.current?.photo?.isVideo == true` run a video tick and `continue`:
- not in `ready`: nothing (the preview isn't up, so the video hasn't started).
- `videoEnded`: `moveBy(1)` once `state.next?.photo?.id in ready`.
- `!videoMoving`: `videoWaitMs += TICK_MS`; at `VIDEO_STALL_MS`, `videoFailed(id, serverFailure = true)`.

### [ ] 3.4 — The error split

Same file, next to `actionFor`: `internal fun isServerFailure(errorCode: Int): Boolean = errorCode in 2000..2999`, Media3's `ERROR_CODE_IO_*` range, with a comment naming it. Tested in `SlideshowHelpersTest`.

Tests: `SlideshowViewModelTest`, per the test plan; a `video(id)` helper next to `slide(id)`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*SlideshowViewModelTest' --tests '*SlideshowHelpersTest'`

---

## Checkpoint 4 — The video on screen · commit: `feat(slideshow): play videos full screen in the slideshow`

Goal: every "Done when" line holds on the device. Use `design-recipe` for this checkpoint.

### [ ] 4.1 — The still under it

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/slideshow/SlideshowScreen.kt`
In `SlideLayer`, for `slide.photo.isVideo`: no motion (the progress effect doesn't run) and `ContentScale.Fit` for a landscape video too. The portrait backdrop stays as it is.

### [ ] 4.2 — The player

Same file. `Stage` composes `VideoLayer` above the current layer only when `current.photo.isVideo` and `layers.shown?.photo?.id == current.photo.id`, so the fade has finished and a held right key starts no loads.

`VideoLayer(slide, run, paused, newPlayer: (Context) -> Player, sound: Boolean, onPlaying, onWaiting, onEnded, onFailed: (serverFailure: Boolean) -> Unit)`:
- `remember` one player from `newPlayer`, `DisposableEffect` to `release()`. As `VideoLayer` leaves with the slide, a photo holds no player. Keep the player in a `key`-free slot above the per-video effects, so moving between videos reuses it.
- `LaunchedEffect(slide.photo.id, run)`: `setMediaItem(MediaItem.Builder().setMediaId(Video(slide.photo.id).photo.value).build())`, `prepare()`.
- Muted: `trackSelectionParameters` with `C.TRACK_TYPE_AUDIO` disabled. With sound: `setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), /* handleAudioFocus = */ true)`.
- `playWhenReady = !paused && lifecycle at least STARTED` (`currentStateAsState()`), so leaving the app pauses it.
- `Player.Listener`: `onIsPlayingChanged(true)` → `onPlaying`; `onPlaybackStateChanged`: `STATE_BUFFERING` → `onWaiting`, `STATE_ENDED` → `onEnded`; `onPlayerError` → `onFailed(isServerFailure(error.errorCode))`.
- The picture: `AndroidView` of a non-focusable `TextureView` with `isOpaque = false` and `player.setVideoTextureView`, centred, sized by `Modifier.aspectRatio` from `onVideoSizeChanged` (height-first for portrait), not composed until the size is known. It stays transparent until its first frame, so the preview shows through.

### [ ] 4.3 — Wiring

Files: `SlideshowScreen.kt`, `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`
`SlideshowScreen(viewModel, settings, newPlayer: (Context) -> Player, onBack)`; `MainActivity` passes `container::videoPlayer`. `SlideshowContent` and `Playing` pass it on with the four reports; `sound = settings.videos == VideoMode.PlayWithSound`. Previews pass a lambda that is never called, and one gains a video slide.

### [ ] 4.4 — Docs

Files: `docs/design/README.md`, `README.md`
The manual's Slideshow row: a video's preview crossfades in, then the video plays fitted and without motion until its end. `README.md`: the line saying videos stay out until #6 now says what the setting does.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`
- Seen working on the TV is self-verification's (see the test plan's window and lock).

---

## Out of scope

- Live photos' motion part: an item the server calls an image stays a photo.
- Buffering a video ahead, or caching streams on disk.
- A progress bar through the video, or seeking inside it: left/right skip, as on a photo.
- Video outside the slideshow (opening one from a grid or the timeline).
- Asking the server to transcode, or choosing a quality.
- Retrying a failed video within a show.
