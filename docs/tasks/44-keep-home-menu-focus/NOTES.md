# Notes — bug: Home arriving pulls focus off the top menu

Issue: #44 — bug: Home arriving pulls focus off the top menu (https://github.com/YasarMahomedAbbas/immich-tv/issues/44)
Branch: `fix/44-keep-home-menu-focus`

## Planning

- Approach: hand `MainScreen`'s `mayTakeFocus` to the Home tab and ask it after the first frame before `HomeLoaded` takes focus, with a real-key focus test modelled on `AlbumGridFocusTest`.
- Decided: no Immich API call changes, so the plan needs no spec check.
- Decided while planning, flag if you disagree: Home always asks, coming Back included. It cannot tell a cold first load from a restore (`HomeState.focused` defaults to Play), and on coming Back MainScreen is composed fresh with the pill focused, so the answer is yes. The alternative, a nullable remembered target to exempt restores as `AlbumGrid` does, changes the ViewModel for no visible gain.
- Decided while planning, flag if you disagree: the `lost` refocus effect also fires on the page's first composition when the remembered target isn't on the page (any page without a hero on a cold start), taking focus unasked. It is kept off the first composition so the first arrival asks too; the issue named this path as separate, but without it Done-when fails for a page without a hero. Later refreshes keep the unasked refocus.
- Decided: test cases are no key pressed, Right during the skeleton, Right then Left back to the pill, a page without a hero (Right and no key), Try again, entering a loaded tab, switching to Home from Albums, and a remembered photo.
- Open risks: (1) The issue was found by reading the code and not reproduced on the TV; Home's first load may be too short to land a Right on the real device, so the unit test is the proof either way. (2) The Chromecast refused adb during #33's verification; the on-TV check may be skipped.
