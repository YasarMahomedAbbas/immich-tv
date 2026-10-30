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
