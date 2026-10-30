# Notes — feat: the home screen

Issue: #9 — feat: the home screen (https://github.com/YasarMahomedAbbas/immich-tv/issues/9)
Branch: `feat/9-the-home-screen`

## Planning

- Approach: a `LoadHome` use case gathers today's memory (new `MemoryRepository`), the albums and the newest photos (`LibraryRepository.recent`) into a page; a Home screen in the `AlbumsScreen` pattern renders it; navigation learns where a screen was opened from, so Back returns to Home.
- Decided (pipeline, on the user's behalf; flag: ux, api, open-risk): Q1.1, "Recently added" shows the newest photos from the timeline. Immich can't sort by upload date in any supported version, so an old photo uploaded today won't appear. **For the PR body: the label is slightly generous.** Rejected: a widening created-after search sorted locally (Q1.2), renaming the row "Recent photos" (Q1.3).
- Decided (pipeline, on the user's behalf; flag: ux, api, open-risk): Q2.1, several memories today show the most recent year only, with no switcher. Rejected: merged years titled "On this day" (Q2.2), left/right between years (Q2.3).
- Decided (pipeline, on the user's behalf; flag: ux, api, open-risk): Q3.1, View photos on a memory opens a grid of that memory through the album photos screen. OK on a tile does nothing until #23, as on album grids. Rejected: the Photos tab at that month (Q3.2).
- Decided (pipeline, on the user's behalf; flag: ux, api, open-risk): Q4.1, a key without `memory.read` counts as no memory today and shows the album; the README adds the permission. Such a user isn't told why memories are missing. Rejected: the failed state with the permission message (Q4.2).
- Decided (pipeline, flagged to the user's stand-in), the "also decided" list:
  - Birthday memories are ignored.
  - A memory's slideshow uses the library show's fixed defaults, photos only. The album fallback uses the saved slideshow settings.
  - The hero's small label uses the secondary text colour, not the canvas's amber, because the manual keeps amber for focus.
  - "Today" is the device's local date.
  - Memory load failures of any kind fall back to the album; album or recent-photo failures show the failed state.
  - Back from anything opened on Home returns to Home with focus restored, which needs a small navigation change.
- Decided (pipeline, while writing the checklist; not yet seen by the stand-in, raised in the brief; flag: ux, scope):
  - OK on a Recently added tile opens the full-screen photo viewer, with Back to Home. The ticket and canvas don't say.
  - A memory's grid and count are photos only as well, so they agree with its show.
  - The fallback album is the most recently updated one holding at least one item.
  - The rows hold 10 albums and 20 photos, and the screen scrolls, because the canvas's blocks overrun 1080.
- Open risks:
  - The real server returns no memories at all, for today or any other date, so the memory hero is proven by tests only. On the TV, only the album fallback can be checked.
  - The date-only `for` on v1.133 rests on its spec and source; it hasn't been run against a real v1.133 server.
