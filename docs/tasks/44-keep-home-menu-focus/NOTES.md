# Notes — bug: Home arriving pulls focus off the top menu

Issue: #44 — bug: Home arriving pulls focus off the top menu (https://github.com/YasarMahomedAbbas/immich-tv/issues/44)
Branch: `fix/44-keep-home-menu-focus`

## Planning

- Approach: hand `MainScreen`'s `mayTakeFocus` to the Home tab and ask it after the first frame before `HomeLoaded` takes focus, with a real-key focus test modelled on `AlbumGridFocusTest`.
- Decided: no Immich API call changes, so the plan needs no spec check.
- Decided while planning, flag if you disagree: Home always asks, coming Back included. It cannot tell a cold first load from a restore (`HomeState.focused` defaults to Play), and on coming Back MainScreen is composed fresh with the pill focused, so the answer is yes. The alternative, a nullable remembered target to exempt restores as `AlbumGrid` does, changes the ViewModel for no visible gain.
- Decided while planning, flag if you disagree: the `lost` refocus effect also fires on the page's first composition when the remembered target isn't on the page (any page without a hero on a cold start), taking focus unasked. It is kept off the first composition so the first arrival asks too; the issue named this path as separate, but without it Done-when fails for a page without a hero. Later refreshes keep the unasked refocus.
- Decided: test cases are no key pressed, Right during the skeleton, Right then Left back to the pill, a page without a hero (Right and no key), Try again, entering a loaded tab, switching to Home from Albums, and a remembered photo.
- Decided (the pipeline, on the user's behalf; flags: scope, ux, open-risk): plan brief approved with option 1 of 3, the plan as written, including the lost-item guard on first arrival and Home always asking. The alternatives were dropping the guard (closer to the issue's wording, but a cold start with no hero would still take focus unasked) and exempting restores via a nullable remembered target (a ViewModel change for no visible gain).
- Open risks: (1) The issue was found by reading the code and not reproduced on the TV; Home's first load may be too short to land a Right on the real device, so the unit test is the proof either way. (2) The Chromecast refused adb during #33's verification; the on-TV check may be skipped.

## Implementation

- Shipped: `MainScreen` hands `mayTakeFocus` to the Home slot too (`ui/main/MainScreen.kt:43`, `:71`; `MainActivity.kt:114`). `HomeLoaded` asks it one frame after the page is laid out (`ui/home/HomeScreen.kt:194`) and keeps the `lost` refocus off the first arrival with an `arrived` flag (`:190`, `:200`). `HomeContent` and `HomeActions` are `internal` for the test (`:99`, `:143`). Manual's Top menu line and README's testing line name Home.
- Tests: new `HomeFocusTest`, 9 cases. Checkpoint set (`HomeFocusTest`, `AlbumGridFocusTest`, `PhotoGridFocusTest`, `HomeViewModelTest`): 39 passed / 0 failed. Full CI command: 533 passed / 0 failed, `koverVerifyDebug`, `lintDebug`, `assembleDebug` green.
- Proved red: with the `mayTakeFirstFocus()` check removed, "right during the skeleton, albums keeps focus" and "right during the skeleton of a page without a hero, albums keeps focus" fail (2 of 9). With only the `!arrived` guard removed, the no-hero Right case fails (1 of 9). Both put back.
- Outstanding: none in the checklist. The on-TV drive in the test plan is self-verification's.
- Contradicted the plan: the checklist's test pages all had an empty albums row, but the `lost` path begins with `albumRow.scrollToItem(0)` (`ui/home/HomeScreen.kt:201`), which never returns when no albums row is laid out (its LazyListState is attached to nothing), so on such a page that path never reaches `requestFocus` and removing the `!arrived` guard turned nothing red. Resolved in the test: the no-hero page has one album, the pill is told from the row's "Albums" title by `hasText(...) and hasClickAction()`, and that page's no-key case expects the album card. Checklist 1.3 and the test plan were edited to match.
- Found, out of scope: as a consequence, the unasked refocus after a later refresh never happens on a page with no albums row (it hangs on the scroll). Pre-existing; this ticket leaves the later-refresh path alone. Worth its own ticket if it matters.
