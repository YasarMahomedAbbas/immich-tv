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
- Decided (pipeline, while writing the checklist; approved by the pipeline on the user's behalf with the plan brief, option 1; flag: ux, scope):
  - OK on a Recently added tile opens the full-screen photo viewer, with Back to Home. The ticket and canvas don't say.
  - A memory's grid and count are photos only as well, so they agree with its show.
  - The fallback album is the most recently updated one holding at least one item.
  - The rows hold 10 albums and 20 photos, and the screen scrolls, because the canvas's blocks overrun 1080.
- Open risks:
  - The real server returns no memories at all, for today or any other date, so the memory hero is proven by tests only. On the TV, only the album fallback can be checked.
  - The date-only `for` on v1.133 rests on its spec and source; it hasn't been run against a real v1.133 server.

## Implementation

- Shipped: memories behind `MemoryRepository` (`data/immich/ImmichMemoryRepository.kt:22`) and `LibraryRepository.recent` (`data/immich/ImmichLibraryRepository.kt:54`); `LoadHome` (`domain/home/LoadHome.kt:36`); `HomeViewModel` and the pure `focusTarget` (`ui/home/HomeViewModel.kt:43`); `HomeScreen` (`ui/home/HomeScreen.kt:107`), with new `OverPhotoStyle.Hero` and `TileStyle.Hero`; navigation that knows where a screen came from (`ui/AppViewModel.kt:133`), wired in `MainActivity.kt:69`; `SlideshowViewModel.fromMemory` (`ui/slideshow/SlideshowViewModel.kt:264`). README adds `memory.read`; the manual has Home's values.
- Tests: 382 passed / 0 failed / 0 skipped; `testDebugUnitTest koverVerifyDebug lintDebug assembleDebug` green in Docker.
- Outstanding: 4.4 is unticked: Home's focus beating the menu's and focus restored after Back can only be seen on the TV, as can the page's scrolling, the hero panel's fit (620dp holding both buttons, 28dp padding chosen here) and all previews, which were compiled but not looked at. All for self-verification.
- Contradicted the plan: nothing in the code disproved it. Small additions, written into the checklist: `TileStyle.Hero` for the 22dp corners; a hero album's show says "‹ Home", since that is where its Back goes; `SlideshowSource.Home` has real empty-state words, because a hero album's show can be empty with Skip videos; MainScreen needed no change. Until checkpoint 4 the activity kept the placeholder in the new slot.
- Side effects to review: a recent tile opens through the Photos tab's ViewModel, so that ViewModel now loads at launch (the months and first month, one more bucket call or two) and the Photos grid later returns to the tile last viewed. `BringIntoViewRequester` needs an `@OptIn(ExperimentalFoundationApi::class)` in this Compose version; it's an opt-in, not a lint suppression.

## Review

- Fixed: (1) `ui/photos/PhotoGridViewModel.kt:77`: a Recently added tile from a month the grid hadn't loaded (any month after the newest, common as the row spans months) opened in the viewer, and Left and Right did nothing. The viewer now loads months until it finds the photo. If no month holds it, a press reads every month once and stays put. `MainActivity` also refreshes the grid on opening a tile, since nothing else does while Home is up and the tile may be newer than its months. Tests added; both fail without the fix. (2) `ui/home/HomeScreen.kt`, `Hero`: a refresh swapping memory and album (midnight, or a memories call failing) rebuilt the hero's buttons, and focus vanished from the button the remote was on with nothing requesting it again. It is now one `HeroBlock` call. No test: the project has no Compose UI test harness, and the TV can't produce it because the server has no memories, so this rests on reading the code.
- Raised: none
- API: `GET /api/memories` (`for`, `type`, response fields): matches the v3.3.5 and v1.133.0 specs; 200 live on v3.3.5 with `[]`. v1.133 declares `for` as date-time, and its repository compares it to `showAt`/`hideAt`; date-only is still not run against a real v1.133. `GET /api/timeline/buckets`, `/api/timeline/bucket`: parameters match the v3.3.5 spec, calls unchanged.
- Layers: clean

## Doc drift

- Applied: README.md:features: added Home, which the list left out, and that its newest photos go by date taken (rung 3).
- Applied: README.md:Architecture: a quiet refresh "reads one cheap summary" is false for Home, whose `refresh()` runs all of `LoadHome`; stated the exception (rung 3).
- Applied: docs/design/README.md:Photo: Back also goes to Home when opened from Recently added, per `AppViewModel.back` (rung 4).
- Checked, no change: the manual's Home values against `HomeScreen`/`LoadHome`, `OverPhotoStyle.Hero`, `TileStyle.Hero`, "‹ Home"; `memory.read` is in the v3.3.5 spec; no workflow or `build.sh` change.
- Skipped: `CameFrom` Back routing: `AppViewModelTest` covers it and the enum's comment says it.
- Skipped: Home's ViewModel sharing the Photos grid's, so it loads at launch: a comment in `MainActivity` says why.
- Skipped: the memory grid reusing the album photos screen: the manual's Album photos row stays true, and `MainActivity` shows it.
- Skipped: CLAUDE.md: nothing cross-cutting is new; its one-line app summary isn't false without Home.
- Not a doc matter, for self-verification: the album screen's back link always says "‹ Albums" (`album_back`), though from Home's row Back goes to Home. The manual's "It does what Back does" holds; the label misleads.
- Stale, fixed separately: none

## Self verification

Result: partial

- CI build: the full command ran once, before the fixes below: 386 tests passed, line coverage 87.3%, lint clean, APK built. After the See all fix only `HomeViewModelTest` (16) and `AlbumBackLabelTest` (2) ran, green, and the debug APK built. The full command is left to `raise-pr` because of the user's speed note on the issue.
- Server: Immich 3.3.5. Spec for that tag read; `GET /api/memories`, `/api/timeline/buckets`, `/api/timeline/bucket` made as the code builds them.
- TV: installed on the Chromecast, then driven with the D-pad; reinstalled after the fix and the changed paths driven again. No crash in logcat. The TV was left on its launcher.
- Verified:
  - `GET /api/memories?for=<today>&type=on_this_day`: 200 and `[]`. `GET /api/timeline/buckets` (10 months) and `/api/timeline/bucket`: 200, and the columns the code reads are there.
  - Skeleton while loading, with focus on the Home pill, then focus moves to the hero's Play slideshow (4.4).
  - Both buttons fit the 620dp panel, with the outline clear of its edge.
  - Page scrolling: moving down brings a block in whole, moving up returns to the hero; rows scroll with focus.
  - View photos opens the album's grid; Back returns to Home on View photos.
  - Album card opens the album, its link says "‹ Home"; Back returns to Home on that card.
  - Play slideshow advances, says "‹ Home", 2 / 233 after a skip; Back returns to Play.
  - Recently added tile opens the viewer, Right moves on; Back returns to Home on the tile opened.
  - See all goes to the Albums tab; Back returns to Home on See all.
- Fixed here (both are this ticket's, each with a test that fails without it):
  - The album's back link always said "‹ Albums", though from Home's row Back goes to Home. It now names where Back goes (`albumBackLabel`, `album_back_home`); the manual says so. An album's own Back is unchanged.
  - See all could only be reached through the Settings gear: Up from any card went to the hero, as See all sits at the far right, and Right from the last card left the row for the gear. Now Up from a card goes to See all, Up from See all goes to View photos, and Right stops at a row's last item (`upFrom`, `endsRow`). Getting from the row to the hero now takes two presses. Seen on the TV from the first and the last card. `focusProperties` itself has no unit test: the project has no Compose UI harness, so the wiring rests on the TV run.
- Failed: none
- Not self-verifiable:
  - The memory hero: the real server has no memories, so it, its title, its show and its grid are proven by tests only, not on the TV. Check by hand on a day with a memory.
  - The failed state: not driven, since taking the shared TV off the network wasn't practical. Tests only, and the screen itself was never seen. Check with the server stopped.
  - The empty state and the no-hero page: the real library can't produce them.
  - A real v1.133 server: date-only `for` still rests on its spec and source.

