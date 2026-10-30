# Notes — feat: play videos in slideshows from the Photos tab

Issue: #26 — feat: play videos in slideshows from the Photos tab (https://github.com/YasarMahomedAbbas/immich-tv/issues/26)
Branch: `feat/26-videos-in-photos-slideshow`

## Planning

- Approach: `LoadLibrarySlideshow` stops filtering videos and starts at the held item; the repository, ViewModel, screen and player already handle them.
- Decided (pipeline, on the user's behalf; flag: ux): the library show's videos are muted, from `TimelineSettings`' default `VideoMode.PlayMuted`, the album default. A library show has no settings to change it. No step-3 question: nothing else had more than one reasonable design.
- Decided (pipeline, on the user's behalf; flag: scope): memories and search results stay photos only, and the viewer keeps a video as a still; the ticket names only the Photos tab's show.
- Decided: an item no longer on the timeline starts at the newest, as today. Only videos now plays them rather than showing Empty.
- API: no call added or changed. On the live v3.3.5 server: 44 videos in 555 timeline items; a timeline video's playback with the key and a range gives 206 `video/mp4`. `isImage` and `video/playback` are in the v1.133.0 and v3.3.5 specs.
- Speed note on the issue (2026-10-30): run only the changed classes' unit tests while implementing and fixing; the full CI command once, in `raise-pr`; the on-TV check limited to this ticket, under the lock for 10 minutes at most, released before fixing.
- Open risks: a video the Chromecast can't decode is skipped (#6); only the TV check shows how many of these 44 are affected.
