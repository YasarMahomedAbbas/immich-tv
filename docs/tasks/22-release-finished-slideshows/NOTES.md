# Notes — bug: finished slideshows stay in memory until the activity goes

Issue: #22 — bug: finished slideshows stay in memory until the activity goes (https://github.com/YasarMahomedAbbas/immich-tv/issues/22)
Branch: `fix/22-release-finished-slideshows`

## Planning

- Approach: `AppViewModel` owns a `ViewModelStore` for the current show and empties it on every move between screens and in its own `onCleared`. The album, search results and library shows take their ViewModel from it, and the per-start key goes.
- Decided (pipeline, on the user's behalf; flags: scope, open-risk): Q1.1, the store is owned by the app-level ViewModel, so a show survives activity recreation as it does today and a plain unit test proves the release. This differs from the ticket's suggested fix. Rejected: Q1.2, a store made by the screen and cleared when it leaves composition, which also clears on recreation unless special-cased and needs Robolectric and Compose test dependencies, or a helper-only test that would pass if the screen stopped using it.
- Decided (pipeline, on the user's behalf; flags: scope, open-risk): Q2.1, all three kinds of show only: they are the only retained ViewModels that hold big lists and grow with every round. Rejected: Q2.2, shows plus Setup's per-open key (a few strings per Settings visit, outside the ticket's title); Q2.3, shows plus the per-album keys (capped at one per album per connection, and they need a nested scope that risks focus on return).
- Decided (pipeline, flagged to the user's stand-in; flag if you disagree):
  - The store is cleared on every screen change, not just on Back.
  - The per-start key goes, as an emptied store already guarantees a fresh show.
  - No Immich call changes, and no visuals change.
- Open risks (flag: open-risk): with today's 555-item library, memory growth is small on the TV, so `dumpsys meminfo` is only indicative. The proof of release rests on the unit test. The smaller retained ViewModels in Out of scope stay.

## Implementation

- Shipped: `AppViewModel` owns the show store (`ui/AppViewModel.kt:72`), empties it on every move between screens (`moveTo`, `:148`) and when it is cleared itself (`:144`). The album, search results and library shows take their ViewModel from it (`MainActivity.kt:113`, `:122`, `:132`); the per-start key is gone, and `identityHashCode` remains only in Setup's key.
- Tests: `AppViewModelTest` 20 passed / 0 failed, 5 of them new. With the clear taken out of `moveTo`, `leaving a show by Back clears it`, `leaving a show another way clears it` and `the next show gets a new ViewModel` fail. Full CI command not run here, per the user's speed note for 2026-10-30: it runs once in `raise-pr`.
- Outstanding: the on-TV checks in the test plan, for self-verification.
- Contradicted the plan: none.

## Review

- Fixed: none
- Raised: none
- API: no endpoint touched
- Layers: clean

## Doc drift

- Applied: `README.md`:Architecture — the `connection` key claim no longer held for slideshows, so a paragraph names them as the exception and says where their ViewModels live (rung 3). A new kind of show would otherwise be given the key instead of `AppViewModel.shows`.
- Skipped: `CLAUDE.md` — the store is about one area, not every ticket; the README and `AppViewModel`'s KDoc carry it.
- Skipped: `docs/design/` — no visuals changed. No command, permission, navigation, named file or Immich call changed either.
- Stale, fixed separately: none. On `origin/main` slideshows already didn't call `RefreshOnStart`, so that claim was loose before this branch; the same new sentence covers it, so it has no commit of its own.

## Self verification

Result: passed

- CI build: full command not run here, per the user's speed note for 2026-10-30: it runs once in `raise-pr`. `assembleDebug` built the APK in Docker. Unit tests not re-run; the last run is in Implementation (`AppViewModelTest` 20 passed).
- Server: not applicable, the diff touches no `data/` call and no Immich call changed.
- TV: this branch's debug build installed on the Chromecast. Driven with the remote's keys: album show, library show and search results show, each with Back and a restart, and each with the activity recreated mid-show.
- Verified:
  - `MainActivity` uses `app.shows` (the behaviour no unit test covers): each show starts fresh and survives recreation, as below.
  - Album show: starts at the album's first photo and advances. Back lands on the album screen with focus on Start slideshow. Started again after it had advanced, it is back at the first photo.
  - Library show: hold OK on a tile in the Photos grid starts at that tile's photo. Back lands on the Photos tab with focus on the same tile. Started again, it is at that photo again.
  - Search results show ("mountains", 100 matches): Back lands on the results with focus on Play as slideshow. Started again, it is at the first photo.
  - Recreation mid-show, all three shows, by flipping night mode: six `finishDrawing of relaunch` lines in logcat, same process throughout, so the activity really was recreated. Album: the same later photo after the relaunch, not the first. Library and results: carried on from where they were, moving on to later photos. Back after recreating still lands as above (library and results driven; album after its second relaunch).
  - No error lines for the app or `AndroidRuntime` in logcat over the whole run; Activities: 1 at the end.
- Failed: none
- Not self-verifiable:
  - Memory release on the device: `dumpsys meminfo` is indicative only and shows no release by itself (Java heap 10.8 MB at launch, 15.4 MB after all the rounds, with albums, the Photos grid and search results also loaded). The proof of release is the unit test, as planned.
  - Leaving a show other than by Back: only Back leaves a show on the TV, so this rests on the unit test.
- Plan correction: this TV's night mode was already `yes`, so the test plan's `night yes` recreates nothing. The check flipped to `no` and back to `yes`, leaving it as found.

## Raise PR

- Merged `origin/main` after verification. It brought #9's home screen, including a fourth kind of show, the memory slideshow, still on the per-start key. It now takes its ViewModel from `app.shows` as well, and `leaving a show by Back clears it` covers it. Every other `open*` from main goes through `moveTo`.
- CI build: the full command passed on the merged tree: 449 tests, 0 failed; `koverVerifyDebug` passed at its 80% bound; lint 0 errors, 30 warnings; `assembleDebug` passed.
- Not re-verified on the TV: the merged tree, including the memory show. The on-TV checks in Self verification ran before the merge. The memory show's release rests on the unit test, and that it uses `app.shows` was checked by reading `MainActivity`.
