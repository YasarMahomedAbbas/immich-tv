# Notes — feat: play videos in slideshows from the Photos tab

Issue: #26 — feat: play videos in slideshows from the Photos tab (https://github.com/YasarMahomedAbbas/immich-tv/issues/26)
Branch: `feat/26-videos-in-photos-slideshow`

## Planning

- Approach: `LoadLibrarySlideshow` stops filtering videos and starts at the held item; the repository, ViewModel, screen and player already handle them.
- Decided (pipeline, on the user's behalf; flag: ux): the library show's videos are muted, from `TimelineSettings`' default `VideoMode.PlayMuted`, the album default. A library show has no settings to change it. No step-3 question: nothing else had more than one reasonable design.
- Decided (pipeline, on the user's behalf; flag: scope): memories and search results stay photos only, and the viewer keeps a video as a still; the ticket names only the Photos tab's show.
- Brief approved (pipeline, on the user's behalf; flags: ux, scope): option 1 of the plan brief: videos muted in the library show; memory and search shows stay photos only. Rejected: sound on (option 2); widening to memory and search shows (option 3).
- Decided: an item no longer on the timeline starts at the newest, as today. Only videos now plays them rather than showing Empty.
- API: no call added or changed. On the live v3.3.5 server: 44 videos in 555 timeline items; a timeline video's playback with the key and a range gives 206 `video/mp4`. `isImage` and `video/playback` are in the v1.133.0 and v3.3.5 specs.
- Speed note on the issue (2026-10-30): run only the changed classes' unit tests while implementing and fixing; the full CI command once, in `raise-pr`; the on-TV check limited to this ticket, under the lock for 10 minutes at most, released before fixing.
- Open risks: a video the Chromecast can't decode is skipped (#6); only the TV check shows how many of these 44 are affected.

## Implementation

- Shipped: `LoadLibrarySlideshow` keeps videos in place and starts at the held item, photo or video (`domain/slideshow/Slideshow.kt:47-51`); `TimelineSettings`' KDoc says videos are muted (`ui/slideshow/SlideshowViewModel.kt:284`); the manual's Slideshow row and the README's Slideshow and Photos lines say the same.
- Tests: `LoadLibrarySlideshowTest` 7 passed / 0 failed; `SlideshowViewModelTest` 54 passed / 0 failed. Only these two classes ran. The full CI command did not run here: the speed note on the issue moves it to `raise-pr`.
- Outstanding: none in the checklist. The on-TV and real-server checks in the test plan belong to self-verification.
- Contradicted the plan: task 1.4 said the README needed no change. Its Slideshow line said the library show leaves videos out and its Photos line said hold OK "on a photo"; both updated, and the task text with them.

## Review

- Fixed: none
- Raised: none
- API: no call added or changed. A library show now reaches `GET /api/timeline/bucket` columns `id` and `isImage` and `GET /api/assets/{id}/video/playback` from the timeline — both match the v3.3.5 spec (live server version); the key stays in the header, through the existing player.
- Layers: clean

## Doc drift

- No drift found. The branch's implementation commit already corrected `README.md` (Slideshow, Photos) and `docs/design/README.md` (Slideshow row); checked against the code: the library show keeps videos in place and starts at the held item (`LoadLibrarySlideshow`), muted by the `VideoMode.PlayMuted` default in `TimelineSettings`, hold OK has no photo-only guard (`PhotosScreen`), memory and search shows stay photos only (`LoadHome`, `SearchScreen`). `CLAUDE.md` makes no claim the branch touches, and no doc names `LoadLibrarySlideshow` or `TimelineSettings`.
- Skipped: a library show's videos being muted because of the settings default: the KDoc on `TimelineSettings` says it and the manual states the behaviour, so a future session wouldn't get it wrong.
- Stale, fixed separately: none

## Self verification

Result: partial

- CI build: not run here (speed note on the issue: the full command runs once, in `raise-pr`). `assembleDebug` built the APK installed below. Unit tests from implementation: `LoadLibrarySlideshowTest` 7 passed, `SlideshowViewModelTest` 54 passed. No code changed in this stage.
- Server: Immich v3.3.5. No call added or changed.
- TV: debug build installed on the Chromecast, TV lock held 12:58-13:06 UTC. Driven: the Photos tab's show from a video tile and from a photo tile, pause, previous/next, Back, and a pass over every video.
- Verified, server: with the app's query (`visibility=timeline`, `withStacked=true`, `order=desc`), all 10 months have every `timeline/bucket` column as long as `id`, 555 items, 44 videos. `id`, `isImage` and `duration` are arrays in the v3.3.5 spec, and `GET /api/assets/{id}/video/playback` and `GET /api/timeline/bucket` are there. Playback for a timeline video: 206 `video/mp4` with `x-api-key` and a range, 401 without the key.
- Verified, hold OK on a video: the show opened on the held video (the January tile at position 26), played it to its end after about 34 s and moved on to the next video, whose controls read 27 / 555. The progress bar sat at 26/555 of the width.
- Verified, hold OK on a photo: the show opened on that photo (25 / 555), then reached the video after it in its place and went on to 27.
- Verified, controls: pause held on a video and across a move to a photo, with no advance over 11 s; resume played on; left and right crossed videos and photos with the right position; Back from a video returned to the Photos grid with the amber outline on the starting tile, also after moving far from it (position 421).
- Verified, sound: by code only. `PlayMuted` disables the audio track (`SlideshowScreen`), and `TimelineSettings` keeps that default (unit test). Nobody listened.
- Decode count, from a short check: 0 of 44 videos failed. All 44 are H.264 by their file headers (43 `video/mp4` Baseline, 1 `video/quicktime` High 4.0, at most 1920x1080). Each was shown for about 2 s: the total stayed 555 (a failed video is dropped from the show, which lowers it), the position landed exactly on 421, and logcat had no player, codec or app errors. A failure later than 2 s into a video, or on a very slow load, would not show here, and frames were not checked video by video.
- Failed: none
- Not self-verifiable: a video that won't decode being skipped on the TV: none of the server's videos failed, so this path ran only in unit tests. Sound: listen to a Photos-tab show and check it is silent.
