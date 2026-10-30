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
- Decided while writing the checklist (not asked; approved by the pipeline on the user's behalf with the plan): the slideshow's progress bar stays on the screensaver, as the same slideshow; the dark ground alone shows while loading, with no skeleton on an idle TV; Kover's `*Screen*` exclusion is narrowed, because it would otherwise hide every `*Screensaver*` class from the coverage gate.
- Plan approved: by the pipeline on the user's behalf, including the three choices above; the user has not reviewed it.
- Open risks:
  - open-risk: whether this Google TV build accepts a third-party component through `screensaver_components` and starts it on idle is only known on the TV; the ticket states it, nothing here has tried it.
  - open-risk: Compose in a `DreamService` with hand-made owners isn't done elsewhere in the app; checked on the TV.
  - A large library is read in full at each start before the cap applies, so a big library starts slower and briefly holds the whole list.
  - The TV window closes at 14:25 CET on 2026-10-30; after it the on-TV check is skipped and recorded so.

## Implementation

- Shipped: the source and its loading (`domain/slideshow/Screensaver.kt:48`, `data/settings/StoredScreensaverSettings.kt:13`); the dream (`ui/screensaver/ScreensaverService.kt:26`, `ui/screensaver/ScreensaverScreen.kt:52`) reusing the slideshow's `Playing` and `RunWhileStarted`, now `internal`; the clock and the minute's retry (`ui/screensaver/ScreensaverLogic.kt`, `retryWhileStopped` in `ui/slideshow/SlideshowViewModel.kt`); Settings and the picker (`ui/AppViewModel.kt:107`, `ui/settings/SettingsViewModel.kt:67`, `ui/settings/SettingsScreen.kt:36`, `ui/settings/ScreensaverPickerScreen.kt:56`, `ui/components/PickerRow.kt:24`); Kover's `*Screen*` narrowed (`app/build.gradle.kts`); the README's Screensaver section and the manual's rows.
- Tests: 99 passed / 0 failed, running only the changed classes (`AppViewModelTest` 17, `SettingsViewModelTest` 9, `LoadScreensaverTest` 10, `StoredScreensaverSettingsTest` 5, `ArchitectureTest` 1, `ScreensaverLogicTest` 5, `SlideshowViewModelTest` 52). `assembleDebug` passed and the merged manifest lists the service.
- Not run here: the full CI command (`koverVerifyDebug`, `lintDebug`, the whole unit suite). Today's speed note from the issue moves it to `raise-pr`, once. So the narrowed Kover patterns have not yet met the 80% gate, and lint hasn't seen the new screens.
- Outstanding: none unticked. Seeing it dream, the D-pad on the new screens and the clock on the TV are self-verification's.
- Contradicted the plan: nothing in the code. Filled in or adjusted while building:
  - `"*ScreensaverScreen*"` wasn't needed in Kover: `"*ScreenKt*"` already covers it, so only `"*.ScreensaverService*"` is named. The checklist says so.
  - The plan named three values for the Screensaver setting; a fourth, "An album", shows while the albums are loading or failed, because calling the album gone would be untrue before the list is known.
  - Reading the albums again on entering the picker keeps a list already showing, also if that read fails, so focus isn't lost and no skeleton flashes. The skeleton and failed card are for when no list has loaded.

## Review

- Fixed: picker lost visible focus after Try again. With the albums failed and the random row selected (or a stored album that's gone), pressing Try again removed the focused button, and the entry-focus effect didn't run again because `selectedRow` stayed 0, so no row showed the amber outline until a key was pressed. `SettingsState.enterRow` (`ui/settings/SettingsViewModel.kt`) now changes from null to the row, and the same change stops the entry focus taking over from Try again when the card first appears. Test: `SettingsViewModelTest`.
- Raised: none
- API: no call added or changed. `GET /albums`, `/albums/{id}`, `/timeline/buckets`, `/timeline/bucket` read against the v3.3.5 spec (the server's version): the query parameters the repositories send are all there; matches docs and schema.
- Layers: clean
- Tests: `SettingsViewModelTest` only, per today's speed note; the full CI command runs in `raise-pr`.

## Doc drift

- Applied: README.md:Architecture — the `RefreshOnStart` rule now names the branch's exceptions: the screensaver, and Settings and its picker, which don't call it (rung 3).
- Applied: README.md:features — "one `adb` command" became "turned on over `adb`, once": the section shows three commands (rung 3).
- Checked, true: the README's Screensaver section (component name, no debug `applicationId` suffix, 500 cap, 8 s, photos only, 60 s retry, any key wakes), the connection key on `SettingsViewModel`, the API key permissions (no new call), the CI line, and the manual's Settings, picker, Screensaver and `PickerRow` entries.
- Skipped: Compose owners in a `DreamService`, the narrowed Kover patterns, `ScreensaverShow`'s fixed values: each is stated where the code is, by a comment or KDoc. `CLAUDE.md`'s one-line description of the app lacks the screensaver: it isn't a list a session relies on, and every session would pay for the line. Screensaver setting as a TV rule exception (any key wakes rather than Back): in the manual, where screen work looks.
- Stale, fixed separately: README.md:Architecture — doc said every screen showing server data calls `RefreshOnStart`, code shows the slideshow never has (a4bbd1f).
- Raised, not changed (code, not docs): `SettingsViewModel` loads once per connection and only the picker reloads, so the Settings screen can show an old album name or "Album no longer there" until the picker is opened, and a failed first read stays "An album" until then. `RefreshOnStart(viewModel::load)` would meet the rule, but `load()` isn't a quiet refresh after a failure (it shows the skeleton). The README records the behaviour as it is.

## Self verification

Result: passed

- CI build: not run here, per today's speed note: the full command runs once, in `raise-pr`. `assembleDebug` passed, which built the APK that was tested. Kover and lint still haven't seen the branch.
- Server: Immich v3.3.5
- TV: installed on the Chromecast (Android 14). Drove the idle start, the library show, Settings, the picker, the album show, waking with a key, the clock fallback and memory, in three lock holds of under 10 minutes each, inside the 15:30 CET window.
- Verified:
  - `GET /albums` 200 (15 albums, `id` and `albumName` present); `GET /albums/{id}` 200; `GET /timeline/buckets` and `/timeline/bucket` 200, with the exact query the code builds, for the library and for an album. Every parameter is in the v3.3.5 spec. A missing album answers 400, and the app treats it as a failure.
  - Library size: 10 buckets, 555 assets, 511 of them photos, so the 500 cap applies. The cap itself rests on `LoadScreensaverTest`: the screen shows no count.
  - `screensaver_components` accepts our service, and the system starts it **on idle**: `dumpsys dreams` named `ScreensaverService`, `mWakefulness=Dreaming`. This closes both open risks from Planning (a third-party dream on this build, Compose with hand-made owners).
  - Library show: photos advance, with the date, clock and progress bar in place. Dates from different years appeared.
  - Settings: Back label correct, focus enters on Server, the amber outline is visible, Right reaches Screensaver. OK on Server opens setup and Back returns.
  - Picker: focus enters on the selected row, "Selected" shows at the right, Down moves the outline, nothing is cut off beyond the scrolling list. OK saves `album:<id>` and returns to Settings with focus on Screensaver, which then names the album.
  - Album show: every photo seen carries the album's date (22 July 2024).
  - Wake: a key ends the dream and returns to the Settings screen it left, without acting on the focused button.
  - Fallback, with a missing album id stored through `run-as`: the plain clock on the dark ground, moved between 14:13 (lower right) and 14:14 (upper middle). Settings shows "Album no longer there".
  - Memory over about 50 photos (7 minutes at 8 s each): total PSS flat at 159, 165, 157, 157 MB, with no process death. No fatal errors in logcat.
- Failed: none
- Not self-verifiable: the clock's way back to the photos when the server returns, and a truly unreachable server, weren't driven: the server is the household's and stays up. `retryWhileStopped` covers the return in `SlideshowViewModelTest`, and a server failure reaches the same clock as the missing album.

Notes for whoever tests this next:

- Neither start in the plan works here: this build has no `Somnambulator`, and `cmd dreams` needs root. The check used the real idle path instead, shortening `system screen_off_timeout` from 600000 to 20000 for the test. That goes beyond the setting the plan named; it was restored with the others.
- `com.android.tv.settings/.device.display.daydream.DaydreamVoiceAction` doesn't start a dream here; it brings the Settings task to the front.
- `UndimDetectorWakeLock` (system) holds the screen on for 10 minutes when a key arrives just after a dim, so the TV won't idle. A `SLEEP` then `WAKEUP` clears it.
- Pipelines sharing the TV overwrite each other's debug build: a build from another branch was on the TV at the second hold, and the system logged our dream as "does not exist" and fell back to Google's Backdrop. Reinstall at the start of each hold.

Restored after each hold: `screensaver_components` to `com.google.android.apps.tv.dreamx/.service.Backdrop`, `screen_off_timeout` to 600000, the stored `screensaver.source` removed, the TV on the launcher. Screenshots stayed in the scratchpad.
