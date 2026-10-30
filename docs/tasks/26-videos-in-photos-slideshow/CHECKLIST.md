# Checklist — feat: play videos in slideshows from the Photos tab

Issue: #26 — feat: play videos in slideshows from the Photos tab (https://github.com/YasarMahomedAbbas/immich-tv/issues/26)
Branch: `feat/26-videos-in-photos-slideshow`
Problem: A slideshow started from the Photos tab leaves videos out and, held on a video, begins at the next photo, while an album's show plays them.

## Done when

- A slideshow started from a tile in the Photos tab plays videos in their place in the timeline, the same way an album's slideshow does.
- Holding OK on a video starts the slideshow at that video, not at the next photo.
- The progress display stays correct with videos counted.

## References

- Immich API: no call added or changed. `GET /api/timeline/bucket` already gives `isImage` (in the specs at v1.133.0 and v3.3.5), and `ImmichLibraryRepository.slides()` already returns videos in place (`ImmichLibraryRepositoryTest`). `GET /api/assets/{id}/video/playback` is in both specs and is what #6's player calls. Live server v3.3.5: 44 videos among 555 timeline items; a timeline video's playback with `x-api-key` and a range gave 206 `video/mp4`.
- #6 (the show's video rules), #7 (the library show), `docs/tasks/7-browse-photos-by-month/NOTES.md`, whose raise-pr entry left this open.
- Manual: `docs/design/README.md` § Screens, Slideshow row.

## Context

`LoadLibrarySlideshow` is the only thing keeping videos out: the repository returns them, `SlideshowViewModel` and `SlideshowScreen` play any `Photo` with `isVideo`, and `MainActivity` already hands the library show `TimelineSettings` and the video player. So the change is in the use case, its tests and the manual. The progress bar is `position / slides.size`, so it counts videos once they are in the list. Decisions, the pipeline's on the user's behalf: the show is muted, because `TimelineSettings` keeps the default `VideoMode.PlayMuted`, the album default, and a library show has no settings to change it; memories and search results stay photos only (the ticket names the Photos tab only); the full-screen viewer keeps a video as a still.

## Test plan

- Unit: `LoadLibrarySlideshow`: plays every item in timeline order including videos; starts at a chosen photo; starts at a chosen video; a video at the very end starts there; an item no longer there starts at 0; only videos plays them; an empty library gives an empty list; failures pass through. `SlideshowViewModel`: a playlist starting on a video shows that video, with `position` and `slides.size` counting videos; when a video before the current slide is dropped, `position` and `slides.size` both fall by one; `TimelineSettings` is muted. Run only these classes (speed note on the issue).
- MockWebServer: none new: `ImmichLibraryRepositoryTest` already has a month with a video among the slides.
- Real server: read-only on v3.3.5: every `timeline/bucket` column read is as long as `id` for the months holding videos; `video/playback` for a timeline video gives 206 with the key and a range, 401 without the key.
- On the TV (holding `/tmp/immich-tv-adb.lock`, 10 minutes at most, released before fixing anything; inside the window on the issue's comments, otherwise skipped and recorded): this ticket's paths only. Hold OK on a video tile: the show opens on that video, plays it muted and moves on at its end. Hold OK on the photo before a video: the show reaches the video in its place. Progress bar sensible on both. Left/right across a video; pause on a video; Back from a video returns to the starting tile. Edge: a video that won't decode is skipped and the show goes on.
- The full CI command runs once, in `raise-pr` (speed note on the issue). Self-verification doesn't run it first or re-run it after a small fix.

---

## Checkpoint 1 — Videos in the library show · commit: `feat(slideshow): play videos in a show from the Photos tab`

Goal: a library show holds every timeline item in place and starts at the held one, photo or video.

### [ ] 1.1 — The use case keeps videos

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/slideshow/Slideshow.kt`

`LoadLibrarySlideshow.invoke(from: PhotoId): ServerResult<Playlist>` keeps its signature and becomes `repository.slides().map { all -> Playlist(all, start = all.indexOfFirst { it.photo.id == from }.coerceAtLeast(0)) }`. KDoc: what a library show plays, newest to oldest and videos in place, beginning at the chosen item; an item no longer there begins at the newest. Reword `withoutVideos`'s KDoc only if it still names the library (it doesn't today).

### [ ] 1.2 — Its tests

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/domain/slideshow/LoadLibrarySlideshowTest.kt`

Replace the photos-only cases, on the existing `a, b, v1, c, v2` fake: `it plays everything in timeline order, videos in their place, starting at the chosen photo` (all five ids, start 1); `from a video it starts at that video` (`v1` → 2); `from a video at the very end it starts there` (`v2` → 4); `only videos plays them` (`Playlist([v1], 0)`, replacing "only videos gives nothing to play"). Keep `a photo no longer there starts at the first` and `a failure passes through`. Add `an empty library has nothing to play` (`Playlist(emptyList(), 0)`).

### [ ] 1.3 — The show from a video, and its progress

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/slideshow/SlideshowViewModelTest.kt`

Beside `a playlist with a start plays from there, and moves on and back from there`, with `Playlist(listOf(slide("a"), video("v"), slide("b")), start = 1)`: `a playlist starting on a video shows it, and counts videos in its position` (`shown == "v"`, `position == 2`, `slides.size == 3`). Then `dropping a video before the current slide keeps the position true`: from `[video("v"), slide("a"), slide("b")]` with `start = 2`, `videoFailed` can't name a non-current video, so use `imageFailed(photo("v"))`, its preview failing: `shown == "b"`, `position == 2`, `slides.size == 2`. Add `a library show plays its videos muted`: `assertEquals(VideoMode.PlayMuted, TimelineSettings.videos)`. Production code in `SlideshowViewModel.kt` changes only in `TimelineSettings`' KDoc, which gains "videos muted".

### [ ] 1.4 — The manual and the README

File: `docs/design/README.md`, Slideshow row: replace "with fixed defaults and photos only" by "with fixed defaults, its videos muted and in their place; held on a video it starts at that video". Memory and search wording stays. `README.md`'s Photos line needs no change: re-read it to confirm.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*LoadLibrarySlideshowTest' --tests '*SlideshowViewModelTest'`
- The old photos-only assertions are gone and the new ones pass; the rest of `SlideshowViewModelTest` is untouched and green.

---

## Out of scope

- Videos in memory and search-result shows: both stay photos only.
- Playing a video in the full-screen photo viewer: it stays a still with the badge.
- Settings for the library show, sound included.
- Any change to the show's video rules from #6, or any Immich call.
