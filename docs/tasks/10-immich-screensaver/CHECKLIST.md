# Checklist — feat: Immich photos as the TV's screensaver

Issue: #10 — feat: Immich photos as the TV's screensaver (https://github.com/YasarMahomedAbbas/immich-tv/issues/10)
Branch: `feat/10-immich-screensaver`
Problem: when the TV goes idle it shows Google's ambient mode, not our own photos.

## Done when

- A `DreamService` plays the slideshow of an album chosen in the app's settings, or a random selection of the library.
- The README explains how to turn it on: `adb shell settings put secure screensaver_components io.github.yasarmahomedabbas.immichtv/.ui.screensaver.ScreensaverService`, once, because Google TV hides third-party screensavers.
- When the server is unreachable it shows a plain clock on the dark ground rather than a broken screen.

## References

- Immich API: no new or changed call. It reuses `GET /api/albums/{id}`, `GET /api/timeline/buckets` and `GET /api/timeline/bucket` through `AlbumRepository` and `LibraryRepository`, as they stand (v1.133 to v3.3.5 handled; checked in #5 and #7). `GET /api/albums` lists the albums for the picker. `/timeline/*` and `/albums` were re-read in the v3.3.5 spec for this plan.
- Rejected: `POST /search/random` (see NOTES).
- Android: `android.service.dreams.DreamService`, `BIND_DREAM_SERVICE`.
- Design manual: `docs/design/README.md`, Slideshow row, rules 1, 2, 3, 7, 8, 9. No canvas: this ticket isn't labelled `design`. Build the screens with `design-recipe`.

## Context

`SlideshowViewModel` knows nothing of Android and takes any `suspend () -> ServerResult<Playlist>`, so the screensaver supplies a new playlist and reuses the timer, the failure counting and `Playing` from `SlideshowScreen`. A `DreamService` is not an activity, so the service owns its lifecycle, saved-state and ViewModel store and sets them on the decor view for Compose. Decisions are in NOTES: a Settings screen behind the gear, a shuffled capped read of the existing timeline, fixed defaults, photos only, a clock with a 60 s retry. Nothing here touches `data/immich`. Note Kover's `*Screen*` exclusion also matches every `*Screensaver*` class by accident: their tests are still required, and they don't count towards the gate. Checkpoint 2 narrows the exclusion.

## Test plan

- Unit: `LoadScreensaver` (library: photos only, shuffled by a seeded `Random`, cap at 500; album: photos only, shuffled; failure passes through for library, a missing album and a failing album; no session); `StoredScreensaverSettings` (round trip, missing, unreadable, an album id with `:` in it); `showsClock`, `retryWhileStopped` and `clockSpot`; `SettingsViewModel` (load, failure and retry, choose); `AppViewModel` (gear, Server, picker, Back from each).
- MockWebServer: none new: no call changes.
- Real server: read-only `GET /api/albums`, `/api/albums/{id}`, `/api/timeline/buckets`, `/api/timeline/bucket` on v3.3.5, confirming the library's photo count against the screensaver's (capped at 500).
- On the TV (under the rules in the issue's comments: the adb lock, and skip and record once the latest window has passed; the latest comment says 14:25 CET on 2026-10-30): read and record `settings get secure screensaver_components`, set ours, start the dream (`am start -n com.android.systemui/.Somnambulator`, or `cmd dreams` if that's what this Android has), see the random library show, then choose an album in Settings and see it play. A D-pad key wakes the TV back to where it was. Force the fallback on the debug build by storing a missing album id through `run-as`: the clock shows and moves. Put the stored choice back and **restore the recorded setting, on failure too**. Memory over about 50 photos (`dumpsys meminfo`).

---

## Checkpoint 1 — What the screensaver plays · commit: `feat(slideshow): choose and load what the screensaver plays`

Goal: the source can be stored and turned into a playlist, tested, with no Android.

### [x] 1.1 — The domain

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/slideshow/Screensaver.kt`

- `sealed interface ScreensaverSource { data object Library; data class Album(val id: AlbumId) }`
- `interface ScreensaverSettingsStore { suspend fun load(): ScreensaverSource; suspend fun save(source: ScreensaverSource) }`
- `class GetScreensaverSource(store)` and `class SaveScreensaverSource(store)`, mirroring `GetSlideshowSettings` and `SaveSlideshowSettings`.
- `val ScreensaverShow = SlideshowSettings(secondsPerPhoto = 8, order = SlideshowOrder.Shuffle, videos = VideoMode.Skip, showDateAndPlace = true)`.
- `class LoadScreensaver(store, albums: AlbumRepository, library: LibraryRepository, random: Random = Random.Default)`, `suspend operator fun invoke(): ServerResult<Playlist>`:
  - `Library`: `library.slides()`, drop videos, `shuffled(random)`, `take(MAX_LIBRARY_PHOTOS)` (`const val MAX_LIBRARY_PHOTOS = 500`).
  - `Album(id)`: `albums.album(id)`, then `albums.slides(album)`, drop videos, `shuffled(random)`. A failure from either passes through, and the stored choice is left alone.

Test: `app/src/test/java/.../domain/slideshow/LoadScreensaverTest.kt`, with `FakeLibraryRepository` and the fake in `domain/albums/Fakes.kt`.

### [x] 1.2 — The stored setting

File: `app/src/main/java/.../data/settings/StoredScreensaverSettings.kt`, mirroring `StoredSlideshowSettings`.

`internal class StoredScreensaverSettings(storage: StringStorage) : ScreensaverSettingsStore`. Key `screensaver.source`, value `library` or `album:<id>`, split on the first `:` only. Missing or unreadable is `Library`.

Test: `app/src/test/java/.../data/settings/StoredScreensaverSettingsTest.kt`, with `MemoryStorage`.

### [x] 1.3 — Wiring

File: `app/src/main/java/.../AppContainer.kt`

`private val screensaverSettings = StoredScreensaverSettings(<the "slideshow" SharedPreferencesStorage>)`, kept in one shared `val` with `slideshowSettings`; expose `getScreensaverSource`, `saveScreensaverSource`, `loadScreensaver`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*LoadScreensaverTest' --tests '*StoredScreensaverSettingsTest' --tests '*ArchitectureTest'`
- `ArchitectureTest` stays green: no `data/` import outside `data/` and `AppContainer`.

---

## Checkpoint 2 — The screensaver plays · commit: `feat(slideshow): play the slideshow as the TV's screensaver`

Goal: with the system setting on, the TV's idle screen is our slideshow, and any key wakes it.

### [x] 2.1 — The show, reused

Files: `app/src/main/java/.../ui/slideshow/SlideshowScreen.kt`, `.../ui/slideshow/SlideshowViewModel.kt`

- Make `Playing` and `RunWhileStarted` `internal`. No behaviour change. The controls never show because nothing calls `showControls`; the progress bar stays, as the same slideshow.
- Add `SlideshowViewModel.forScreensaver(container: AppContainer)` to the companion: `SlideshowViewModel(ScreensaverShow) { container.loadScreensaver() }`.

### [x] 2.2 — The screen

File: `app/src/main/java/.../ui/screensaver/ScreensaverScreen.kt`

`@Composable fun ScreensaverScreen(viewModel: SlideshowViewModel)`: `bg` ground, `RunWhileStarted(viewModel)`, and `Playing(state, ScreensaverShow, SlideshowSource.Library, …)` once playing. Its `newPlayer` is `{ error("A screensaver plays no videos") }`: `VideoMode.Skip` means nothing asks. Nothing focusable. The ground alone while loading: no skeleton on an idle TV. Failed and Empty are empty ground until checkpoint 3.

### [x] 2.3 — The service

File: `app/src/main/java/.../ui/screensaver/ScreensaverService.kt`

`class ScreensaverService : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner`.

- `onCreate`: `savedStateController.performRestore(null)`, lifecycle to `CREATED`.
- `onAttachedToWindow`: `isInteractive = false`, `isFullscreen = true`; `setViewTreeLifecycleOwner`, `setViewTreeSavedStateRegistryOwner`, `setViewTreeViewModelStoreOwner` on `window.decorView`; `setContentView(ComposeView(this).apply { setContent { ImmichTvTheme { ScreensaverScreen(vm) } } })`, with `vm` from `ViewModelProvider(this, SlideshowViewModel.forScreensaver(container))`.
- `onDreamingStarted`: lifecycle to `RESUMED`. `onDreamingStopped`: lifecycle to `CREATED`, which stops the timer.
- `onDetachedFromWindow`: lifecycle to `DESTROYED`, `viewModelStore.clear()`.

`container` is `(application as ImmichTvApp).container`, so Coil's one loader and the key header come along. Add `androidx.savedstate` to the version catalogue only if the transitive one doesn't resolve.

### [x] 2.4 — The manifest

File: `app/src/main/AndroidManifest.xml`

```xml
<service android:name=".ui.screensaver.ScreensaverService"
    android:exported="true"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_DREAM_SERVICE">
    <intent-filter>
        <action android:name="android.service.dreams.DreamService" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</service>
```

### [x] 2.5 — Coverage

File: `app/build.gradle.kts`

Kover's `"*Screen*"` swallows every `*Screensaver*` class. Exclude `"*.ScreensaverService*"` by name (`"*ScreenKt*"` already covers `ScreensaverScreenKt`), and keep the domain and data classes counted (change `"*Screen*"` to patterns that don't match `Screensaver`, such as `"*Screen"`, `"*Screen$*"`, `"*ScreenKt*"`).

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`
- The manifest in the built APK lists the service. Seeing it dream is self-verification's.

---

## Checkpoint 3 — The clock when there are no photos · commit: `feat(slideshow): fall back to a clock when the screensaver can't play`

Goal: no photos for any reason means a calm clock, and the show comes back by itself.

### [x] 3.1 — The logic

Files: `app/src/main/java/.../ui/slideshow/SlideshowViewModel.kt`, `.../ui/screensaver/ScreensaverLogic.kt`

- `suspend fun SlideshowViewModel.retryWhileStopped(everyMs: Long = SCREENSAVER_RETRY_MS)`: forever, `delay(everyMs)`, then `retry()` if the load is `Failed` or `Empty`. `SCREENSAVER_RETRY_MS = 60_000L`.
- `internal fun showsClock(load: SlideshowLoad, wasClock: Boolean): Boolean`: `Failed`, `Empty` → true; `Playing` → false; `Loading` → `wasClock`, so a retry doesn't blink the clock.
- `internal fun clockSpot(minute: Long): Pair<Float, Float>`: one of a fixed set of at least 8 spots as fractions of the safe area, a different one each minute.

Tests: `app/src/test/java/.../ui/slideshow/SlideshowViewModelTest.kt` (with virtual time: retries a failed load, retries an empty one, leaves a playing show alone, resumes when the server answers), `.../ui/screensaver/ScreensaverLogicTest.kt`.

### [x] 3.2 — The clock

Files: `.../ui/screensaver/ScreensaverScreen.kt`, `.../ui/components/TopMenu.kt`

- Add `ClockStyle.Screensaver`: 96sp `mfg`, weight 700. No panel: it is on the plain ground, and amber isn't used.
- The screen runs `retryWhileStopped` in a `LaunchedEffect(viewModel)` while started, and shows the clock when `showsClock`, placed by `clockSpot`, inside the 48dp and 96dp safe area, with the minute from the system clock.
- Add `@Preview`s for the clock and the playing show.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*SlideshowViewModelTest' --tests '*ScreensaverLogicTest'`
- Failed covers unreachable, refused key, no session and a deleted album; Empty covers an album with no photos. All reach the same clock.

---

## Checkpoint 4 — Settings and the picker · commit: `feat(ui): a Settings screen with the screensaver picker`

Goal: the gear opens Settings, and the user can choose what the screensaver plays with the D-pad.

### [x] 4.1 — Navigation

Files: `app/src/main/java/.../ui/AppViewModel.kt`, `app/src/test/java/.../ui/AppViewModelTest.kt`

- `AppScreen.Settings(val from: AppScreen, val focusScreensaver: Boolean = false)` and `AppScreen.ScreensaverPicker(val settings: Settings)`.
- `openSettings()` goes to `Settings(from = current)`, or back to the same place if already there. `openServerSetup()` is what `openSettings` does today, with `from = current Settings`. `openScreensaverPicker()`.
- `back()`: `Settings` → `from`; `ScreensaverPicker` → its `settings` with `focusScreensaver = true`. Setup's Back goes to its `from`, unchanged. `connected()` stays Home.
- Update the tests at the existing `openSettings` cases and add one per move.

### [x] 4.2 — The ViewModel

File: `app/src/main/java/.../ui/settings/SettingsViewModel.kt`, mirroring `AlbumsViewModel`.

`SettingsState(source: ScreensaverSource, address: String?, albums: AlbumsLoad)`; reuses `AlbumsLoad`. `load()`, `retry()`, `choose(source)` (saves, updates state). The value shown for a source: `Random from my library`, the album's name, or `Album no longer there` when the stored album isn't in a loaded list. `factory(container)`.

Test: `app/src/test/java/.../ui/settings/SettingsViewModelTest.kt`.

### [x] 4.3 — The screens

Files: `.../ui/settings/SettingsScreen.kt`, `.../ui/settings/ScreensaverPickerScreen.kt`, `.../ui/components/PickerRow.kt`, `app/src/main/res/values/strings.xml`, `.../MainActivity.kt`

- **Settings**: `BackLink` (which says where Back goes), title, two `SettingButton`s: Server (the address) and Screensaver (the current source). Focus enters on Server, or on Screensaver when `focusScreensaver`. Rule 4 allows one primary button, but none here, since nothing is the one action.
- **Screensaver picker**: `BackLink`, title, a column that scrolls with focus: `Random from my library`, then each album. `PickerRow`: `surface`, 16dp corners, the name 23sp 700 and `Selected` 18sp `mfg` at the right, house focus. OK saves and returns to Settings. Focus enters on the selected row, or the first if it's gone.
- **States (rule 8)**: skeleton rows while loading; the Random row stays usable throughout, with `FailedState` and Try again under it on failure, and a hint that there are no albums when empty.
- `MainActivity`: wire both, with one `SettingsViewModel` under key `settings-$connection`, reloaded on entering the picker.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AppViewModelTest' --tests '*SettingsViewModelTest'`
- `@Preview`s for Settings and each picker state compile.

---

## Checkpoint 5 — The docs · commit: `docs(slideshow): how to turn on the screensaver`

Goal: a reader can turn it on and off, and the manual knows the new screens.

### [x] 5.1 — README

File: `README.md`

A feature line, plus a "Screensaver" section: the `adb shell settings put secure screensaver_components io.github.yasarmahomedabbas.immichtv/.ui.screensaver.ScreensaverService` command, run once, why it needs adb, how to go back (the previous value from `settings get`), and where to choose the album. Correct the service name if checkpoint 2 moved it.

### [x] 5.2 — The manual

File: `docs/design/README.md`

Rows for Settings, Screensaver picker and Screensaver; `PickerRow` under Components; Settings is no longer "the gear opens Setup".

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`

---

## Out of scope

- Videos in the screensaver, and per-source timing or order settings.
- A setting for date and place on the screensaver.
- `POST /search/random`.
- Turning the screensaver on from inside the app, or a way that doesn't need adb.
- Any change to `data/immich`, the album screen's settings, or the regular slideshow's behaviour.
- A design canvas.
