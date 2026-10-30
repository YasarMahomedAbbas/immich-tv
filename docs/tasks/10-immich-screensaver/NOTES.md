# Notes — feat: Immich photos as the TV's screensaver

Issue: #10 — feat: Immich photos as the TV's screensaver (https://github.com/YasarMahomedAbbas/immich-tv/issues/10)
Branch: `feat/10-immich-screensaver`

## Planning

- Approach: a `DreamService` with its own lifecycle reuses `SlideshowViewModel` and the slideshow's `Playing`, fed by a new `LoadScreensaver` from a stored source; no photos means a moving clock and a retry each minute. The gear gains a Settings screen with a picker for the source.
- Decided (pipeline, on the user's behalf; flag: ux, scope): Q1.1, the gear opens a new small Settings screen with Server and Screensaver rows, and the latter opens a list ("Random from my library", then the albums). Rejected: a setting on the setup panel (Q1.2), a "Use as screensaver" button on the album screen (Q1.3).
- Decided (pipeline, on the user's behalf; flag: api): Q2.1, "random" is the existing whole-timeline read, photos only, shuffled, at most 500 held; it is also the default. Rejected: `POST /search/random` (Q2.2), a new call whose `type` filter is deprecated since v3.2.
- Decided (pipeline, on the user's behalf; flag: ux): Q3.1, fixed defaults for every source: photos only, shuffled, 8 s, date, place and clock showing; an album's saved settings don't apply. Rejected: the album's settings with videos muted (Q3.2).
- Decided (pipeline, on the user's behalf; flag: ux): Q4.1, the clock retries every 60 s and returns to the photos when the server answers. Rejected: staying on the clock until the next start (Q4.2).
- Decided (pipeline, on the user's behalf; flag: ux, scope, api apply to the set), the "also decided" list:
  - Any key wakes the TV; no controls on screen.
  - The clock fallback covers unreachable server, refused key, no session, a deleted album and an album with no photos.
  - A deleted album's choice is kept, so it plays again if the album returns.
  - The clock moves about once a minute to avoid burn-in.
  - Self-verification changes the TV's screensaver setting to test this: it records the old value and restores it, under the adb lock and the latest TV window on the issue (14:25 CET on 2026-10-30).
- Decided while writing the checklist (not asked, for review): the slideshow's progress bar stays on the screensaver, as the same slideshow; the ground alone shows while loading, with no skeleton on an idle TV; Kover's `*Screen*` exclusion is narrowed, because it would otherwise hide every `*Screensaver*` class from the coverage gate.
- Open risks:
  - open-risk: whether this Google TV build accepts a third-party component through `screensaver_components` and starts it on idle is only known on the TV; the ticket states it, nothing here has tried it.
  - open-risk: Compose in a `DreamService` with hand-made owners isn't done elsewhere in the app; checked on the TV.
  - A large library is read in full at each start before the cap applies, so a big library starts slower and briefly holds the whole list.
  - The TV window closes at 14:25 CET on 2026-10-30; after it the on-TV check is skipped and recorded so.
