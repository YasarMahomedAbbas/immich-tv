# Checklist — bug: finished slideshows stay in memory until the activity goes

Issue: #22 — bug: finished slideshows stay in memory until the activity goes (https://github.com/YasarMahomedAbbas/immich-tv/issues/22)
Branch: `fix/22-release-finished-slideshows`
Problem: every slideshow we start and leave keeps its whole slide list in memory until the app's activity goes, so each round on a TV that runs for days holds on to more.

## Done when

- Leaving an album, search results or library show releases its ViewModel and slide list, however the screen is left.
- A new show always starts fresh, at its first photo (the library show at the photo it was started from).
- A running show still survives the activity being recreated, carrying on where it was.
- Back from each show lands where it does today, with focus as it is today.
- A test fails without the fix.

## References

- Immich API: no call or shape changes, so nothing to check against the spec.
- Raised by the self-review of #7 (`docs/tasks/7-browse-photos-by-month/NOTES.md`).
- `docs/design/README.md`: no visual changes.

## Context

`MainActivity` gets each show's `SlideshowViewModel` from the activity's store under a per-start key (`System.identityHashCode(current)`), and nothing clears that store while the activity lives. `AppViewModel` already outlives activity recreation and is the only thing that moves between screens, so it will own a `ViewModelStore` for the current show and clear it on every move between screens and in its own `onCleared`. The per-start key goes: an emptied store already guarantees a fresh show. Decisions: the store lives in `AppViewModel` rather than the screen (Q1.1), because a composition-scoped store would also clear on recreation and could only be tested with new Compose test dependencies; only the shows change (Q2.1), because they are the only retained ViewModels that hold big lists and grow per round. See NOTES. The analogue for the tests is `AppViewModelTest`.

## Test plan

Per the user's speed note for 2026-10-30: run only `AppViewModelTest` while implementing and fixing. The full CI command runs once locally, in `raise-pr`; self-verification doesn't run it first or re-run it after a small fix. GitHub CI must still be green before merging.

- Unit (`AppViewModelTest`): leaving each of the three show types by Back clears its ViewModel; leaving by a non-Back move clears it; a second show gets a new instance; asking again during the same show (as a recreated activity does) gets the same instance; clearing `AppViewModel` itself clears the show. The first and second fail without the fix.
- MockWebServer: none; no call changes.
- Real server: none; no call changes. Record as not applicable.
- On the TV (only what this ticket changed; hold the lock 10 minutes at most, and release it before fixing anything):
  - An album show, Back, and the same show again: starts at the first photo, Back lands on the album screen with its usual focus.
  - A library show from a Photos tile, Back, again: starts at that photo, Back lands on the Photos tab.
  - A search results show, Back: lands on the results with focus on them.
  - Recreate the activity mid-show (`adb shell cmd uimode night yes`, then restore with `night no`): the show carries on and isn't back at the first photo.
  - `dumpsys meminfo` Java heap before and after several rounds: indicative only, as the library is 555 items. The proof of release is the unit test.

---

## Checkpoint 1 — Release a show when it ends · commit: `fix(slideshow): release a slideshow's memory when it ends`

Goal: a show's ViewModel lives in a store that is emptied on leaving it, and the three shows use it.

### [x] 1.1 — The show store and its tests

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/AppViewModel.kt`

- Add `private val showStore = ViewModelStore()` and a public `val shows: ViewModelStoreOwner = object : ViewModelStoreOwner { override val viewModelStore get() = showStore }`, with a KDoc: what lives here goes when the screen changes, and survives the activity being recreated.
- Add `private fun moveTo(next: AppScreen) { showStore.clear(); _screen.value = next }`. Every `_screen.value = ...` goes through it, in `selectTab`, the `open*` functions (`openSettings` inside its coroutine), `connected` and `back`. Only the first assignment in `init` stays direct, since no show can exist yet.
- `override fun onCleared() { showStore.clear() }`.
- Adjust the `connection` KDoc: shows are the exception to "kept for the whole activity", and they don't need its key.

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/AppViewModelTest.kt`

Add a private `class ShowProbe : ViewModel()` whose `onCleared` sets a `cleared` flag, obtained with `ViewModelProvider(vm.shows, viewModelFactory { initializer { ShowProbe() } })[ShowProbe::class.java]`. Tests:

- `leaving a show by Back clears it`, for `openSlideshow`, `openResultsSlideshow` and `openTimelineSlideshow`.
- `leaving a show another way clears it`: `selectTab` while a show is open.
- `the next show gets a new ViewModel`: Back, open again, not the same instance.
- `a show asked again while it plays keeps its ViewModel`: same instance, not cleared, standing in for a recreated activity.
- `clearing the app clears the show`: get `AppViewModel` from a `ViewModelProvider` on its own `ViewModelStore`, and clear that store.

### [x] 1.2 — The three shows use it

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/MainActivity.kt`

In the `AppScreen.Slideshow`, `AppScreen.ResultsSlideshow` and `AppScreen.TimelineSlideshow` branches, replace `viewModel(key = "...identityHashCode...", factory = ...)` with `viewModel(viewModelStoreOwner = app.shows, factory = ...)`, keeping each factory. Comment once why: it is emptied on leaving, so a show starts fresh and its slides don't stay. Leave the `setup-` key and every other key alone.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*.ui.AppViewModelTest'`
- All the new tests pass, and `rg identityHashCode` under `app/src/main` finds only the Setup key.

---

## Out of scope

- The per-album keys (`album-…`, `album-photos-…`): capped at one per album per connection, so they don't grow per round.
- Setup's per-open key: a few strings per Settings visit.
- ViewModels kept from an earlier `connection`.
- Any change to what a show does or how it looks.
