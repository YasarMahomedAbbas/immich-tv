# Notes — feat: Continue, random start and a Shuffle button on album slideshows

Issue: #43 — feat: Continue, random start and a Shuffle button on album slideshows (https://github.com/YasarMahomedAbbas/immich-tv/issues/43)
Branch: `feat/43-continue-random-start-shuffle`

## Planning

- Approach: `LoadSlideshow` takes a `ShowStart` (Beginning, Continue, RandomSlide, Shuffled) and returns the playlist plus the shuffle seed. Every slide an album-screen show lands on is saved as the album's `ResumePoint` (photo, index, seed) behind a domain store kept in `data/settings`. The album screen offers Continue and Start over when there is a spot, and compact Random start and Shuffle buttons on the settings heading line. No Immich call changes.
- Decided (pipeline, on the user's behalf; flag: ux): Q1-1, Random start and Shuffle are compact secondary buttons, right-aligned on the "Slideshow settings" heading line. The extra height is paid for by trimming that line's top padding. Rejected: a row of compact buttons under the main row (Q1-2: needs about 63dp, so the hint line would go and the mosaic would shrink); two icon-only circles at the end of the main row (Q1-3: they don't fit in 760dp and rely on icons alone).
- Decided (pipeline, on the user's behalf; flag: ux): Q2-1, Continue resumes the show that was left. A shuffled one (by Order or by the button) resumes its same seeded sequence; an ordered one plays in the album's current Order. Rejected: strictly the current Order, with the seed used only while the Order is Shuffle (Q2-2).
- Decided (pipeline, on the user's behalf; flag: ux): Q3-1, only shows started from the album screen's buttons record a spot. Home's hero "Play slideshow" doesn't, as Home offers no Continue. Rejected: any album show from its start records (Q3-2).
- Decided (pipeline, on the user's behalf; flag: ux applies to the set), the "decided myself" list:
  - The spot is saved on every slide change and at the start, which covers Back, the activity going and a process kill.
  - It is kept per album under its own key (`resume.<id>`) beside the slideshow settings, behind a domain interface: photo id, index, and the seed when shuffled.
  - A seeded shuffle shuffles the whole album before the Videos filter, so changing Videos keeps the sequence.
  - A photo still in the album but skipped resumes at the next kept one, or at the start.
  - A photo gone from the album resumes at its old index, clamped, or at the start.
  - Random start is a seeded `nextInt` over the kept slides.
  - With a spot, Continue (the primary slot) gets first focus, and coming Back lands on it.
  - The album screen re-reads the spot whenever it comes back into view.
- Decided while writing the checklist (not asked):
  - The primary button slot keeps the focus requester and switches label, so a spot read after the first frame doesn't move focus.
  - The save runs `NonCancellable`, so clearing the show store on Back doesn't cut it short.
  - Settings heading top padding goes from 28dp to 8dp to pay for the compact buttons' height; the hint line stays.
- Open risks:
  - Continue + Start over + Browse photos come to about 746 of 760dp in the info column, so very tight. Self-verification checks it on the TV; if it doesn't fit, that's a re-plan, not a guess.
  - The info column's vertical fit with the heading row is estimated, not measured. Check on the TV that the strip and its focus outline stay on screen.
- Plan approved: by the pipeline on the user's behalf, including the choices above; the user has not reviewed it.
