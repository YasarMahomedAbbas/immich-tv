# Checklist — feat: search, by voice

Issue: #8 — feat: search, by voice (https://github.com/YasarMahomedAbbas/immich-tv/issues/8)
Branch: `feat/8-search-by-voice`
Problem: the Search tab is a placeholder, so there is no way to find a photo by describing it or by who is in it.

## Done when

- A search field that takes the on-screen keyboard and voice input (the remote's mic button and a mic button on screen).
- Results from Immich's smart search in a 7-column grid, with **Play as slideshow**.
- Suggested searches as chips under the field.
- A People row (named people from Immich); choosing a person shows their photos.
- No results and failed states.

## References

- Immich API, spec read at v3.3.5 (the server) and v1.133.0 (`MINIMUM`), and calls tried on the server:
  - `POST /api/search/smart` (asset.read). Body `query` (required; empty is a 400), `size` (1–1000), `withExif`. Response `assets.items`; `total` is only the page's count and `nextPage` never ends, so neither is read. A 400 also means "Smart search is not enabled" (`search.service.ts` at both tags).
  - `POST /api/search/metadata` (asset.read). Body `personIds`, `size`, `withExif`, `order`. Top-level `personIds` and `order` are deprecated since v3.2.0 but accepted; the v3.2 `filter.personIds` is not an array and doesn't exist before v3.2, so it isn't used.
  - `AssetResponseDto`: `id`, `type` (`IMAGE`/`VIDEO`), `localDateTime` in every version; `exifInfo.city`, `.country`, `.exifImageWidth`, `.exifImageHeight`, `.orientation` (a string) with `withExif`. **Differs:** v3 has top-level `width` and `height`, already rotated (3024×4032 where the exif says 4032×3024, orientation 6); v1.133 has neither, so shape comes from the exif sizes, swapped for orientation 5–8.
  - `GET /api/people?size=1000` (person.read). `people[].id`, `.name`, `.isHidden`; hidden people are left out by default. `hasNextPage` is not read: named people sort first.
  - `GET /api/people/{id}/thumbnail` (person.read), image.
- Design canvas, board "5 · Search": https://claude.ai/artifact/8Tq2kuJDNjk3SFiALwbfZH. `docs/design/README.md` § Search and rule 3 (the voice button is the one amber exception).

## Context

The Search tab is `PlaceholderTab`. `ImmichAlbumRepository` and its MockWebServer test are the analogue for `data/`, though it only has `GET`; `AlbumViewModel` for the ViewModel; `AlbumPhotosScreen` for the skeleton, empty and failed states. Results are `Slide`s, because they carry what the slideshow needs in one response. The slideshow is album-bound today, so its ViewModel takes a loader instead. Voice is the system prompt (`RecognizerIntent`), which needs no permission. The tab is one `LazyVerticalGrid` with full-span items above the tiles, because field, chips, People and results don't fit one screen without scrolling. Decisions and who took them are in `NOTES.md`.

## Test plan

- Unit: `SearchPhotos` (trims, blank never reaches the server), `LoadPeople` (named only), `playable()`; `SearchViewModel` (submit, a newer search replacing an older one, retry, clear, a person, people failing leaves the row out and search working, count label inputs); `SlideshowViewModel` from a list; `AppViewModel` (results slideshow, Back to Search with `focusResults`); `isVoiceKey`, `firstHeard`.
- MockWebServer: smart search on the v3.3.5 and v1.133 shapes (portrait from `width`/`height`, and from exif sizes with orientation 6 and without); request body, `x-api-key`, no key in the URL; empty results; a video; missing exif; 400 → `SearchUnavailable`, 401, 403, 500, unreadable body; metadata search by person; people (hidden and unnamed filtered, 403); `PersonFace` URL, header and cache key.
- Real server: `POST /api/search/smart`, `POST /api/search/metadata` with `personIds`, `GET /api/people`, `GET /api/people/{id}/thumbnail` on v3.3.5: every field read is present, and archived or trashed photos don't show.
- On the TV (within the window and lock in the issue's comment, or skipped and recorded): D-pad from the pill to field, mic, chips, People, Play and tiles, all with visible focus; OK on the field opens the keyboard and submitting searches; the on-screen mic opens the speech prompt; a chip searches; a person shows their photos; Play as slideshow, then Back returns with focus on Play; Back from results clears them, Back again goes Home. Send `input keyevent KEYCODE_SEARCH` and record the physical mic button's outcome in NOTES.

---

## Checkpoint 1 — Search and people from the server · commit: `feat(search): search photos and read people from the server`

Goal: the app can ask for photos by description or person and for named people, in domain terms, against both API shapes.

### [ ] 1.1 — The domain

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/search/Search.kt` (new)

```kotlin
@JvmInline value class PersonId(val value: String)
data class Person(val id: PersonId, val name: String)
/** A person's face image: what a screen hands the image loader. */
data class PersonFace(val person: PersonId)

interface SearchRepository {
    /** The closest matches to [description], best first, at most [limit]. */
    suspend fun search(description: String, limit: Int): ServerResult<List<Slide>>
    suspend fun people(): ServerResult<List<Person>>
    /** Photos of [person], newest first, at most [limit]. */
    suspend fun photosOf(person: PersonId, limit: Int): ServerResult<List<Slide>>
}

class SearchPhotos(repository)   // trims; blank is Ok(emptyList()) with no call; RESULT_LIMIT = 100
class LoadPeople(repository)     // keeps those with a non-blank name
class LoadPhotosOf(repository)   // PERSON_LIMIT = 1000
```

File: `domain/ServerResult.kt`: add `Problem.SearchUnavailable` ("this server has smart search turned off").
File: `ui/components/StateCards.kt` and `res/values/strings.xml`: its message, `problem_search_unavailable`: "Smart search is turned off on this server. Turn on machine learning in Immich, then try again."
Test: `domain/search/SearchTest.kt`, with `domain/search/Fakes.kt` (`FakeSearchRepository`).

### [ ] 1.2 — The Immich implementation

File: `data/immich/ImmichSearchRepository.kt` (new), `internal class ImmichSearchRepository(client: OkHttpClient, sessions: SessionStore) : SearchRepository`.

- `search`: `POST search/smart`, body `{"query":…,"size":limit,"withExif":true}`. `photosOf`: `POST search/metadata`, body `{"personIds":[…],"size":limit,"withExif":true,"order":"desc"}`. `people`: `GET people?size=1000`, dropping `isHidden`.
- Status mapping as in `ImmichAlbumRepository.get`, plus 400 on smart search → `SearchUnavailable`.
- Both search calls use `client.newBuilder().readTimeout(60, SECONDS).build()`, one instance: it keeps `KeepKeyOnServer`. The first query after the model unloads is slow.
- `@Serializable` DTOs read `assets.items`: `id`, `type`, `width?`, `height?`, `localDateTime?`, `exifInfo?` (`city?`, `country?`, `exifImageWidth?`, `exifImageHeight?`, `orientation?`). Portrait: `width/height` when both are there, else exif sizes swapped for orientation 5–8, else false. `takenOn` is `localDateTime.take(10)`, unknown if it can't be read. `isVideo` is `type == "VIDEO"`.
- `place()` is private in `ImmichAlbumRepository.kt`: make it `internal` and reuse it.

File: `data/immich/PreviewInterceptor.kt`: also handle `PersonFace`: `people/<id>/thumbnail`, the header, cache key `face:<id>`. File: `data/immich/ImmichImages.kt`: add `personFaceUrl`, `personFaceCacheKey`.
File: `AppContainer.kt`: `private val search = ImmichSearchRepository(http, sessions)`; `val searchPhotos`, `loadPeople`, `loadPhotosOf`.
Test: `data/immich/ImmichSearchRepositoryTest.kt` (mirror `ImmichAlbumRepositoryTest`), and extend `PreviewInterceptorTest`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*SearchTest' --tests '*ImmichSearchRepositoryTest' --tests '*PreviewInterceptorTest' --tests '*ArchitectureTest'`
- A v1.133 and a v3.3.5 response give the same `Slide`s.

---

## Checkpoint 2 — A slideshow of any photos · commit: `refactor(slideshow): play any list of photos, not only an album`

Goal: results can be played; albums play exactly as before.

### [ ] 2.1 — The ViewModel and the route

File: `domain/slideshow/Slideshow.kt`: `fun List<Slide>.playable(): List<Slide> = filterNot { it.photo.isVideo }`, used by `LoadSlideshow`.
File: `ui/slideshow/SlideshowViewModel.kt`: the constructor becomes `(settings: SlideshowSettings, loadSlides: suspend () -> ServerResult<List<Slide>>)`. `factory(container, album, settings)` stays, passing `{ container.loadSlideshow(album, settings.order) }`; add `factory(slides: List<Slide>)` with default settings.
File: `ui/AppViewModel.kt`: `AppScreen.ResultsSlideshow(val slides: List<Slide>)`, `openResultsSlideshow(slides)`, and `Main(val tab: Tab, val focusResults: Boolean = false)`. Back from `ResultsSlideshow` goes to `Main(Tab.Search, focusResults = true)`.
File: `MainActivity.kt`: route `ResultsSlideshow` to `SlideshowScreen`, keyed like the album route.
Test: `SlideshowViewModelTest` (constructor, a list plays), `AppViewModelTest`, `LoadSlideshowTest`.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*SlideshowViewModelTest' --tests '*AppViewModelTest' --tests '*LoadSlideshowTest'`
- Existing album slideshow tests pass unchanged in what they assert.

---

## Checkpoint 3 — The Search tab · commit: `feat(search): the Search tab with results, chips and voice`

Goal: searching by keyboard, chip or voice works on screen, with every state. Follow `design-recipe`.

### [ ] 3.1 — The ViewModel

File: `ui/search/SearchViewModel.kt` (new; mirror `AlbumViewModel`).

```kotlin
sealed interface SearchSubject {
    data class Description(val text: String) : SearchSubject
    data class OfPerson(val person: Person) : SearchSubject
}
sealed interface SearchLoad {
    data object Idle : SearchLoad           // nothing asked yet
    data object Loading : SearchLoad
    data object Empty : SearchLoad
    data class Loaded(val slides: List<Slide>) : SearchLoad
    data class Failed(val problem: Problem) : SearchLoad
}
data class SearchState(val text: String = "", val subject: SearchSubject? = null, val load: SearchLoad = SearchLoad.Idle, val people: List<Person> = emptyList())
```

Functions: `edit(text)`, `submit()`, `submit(text)` (chip, voice), `choose(person)`, `retry()`, `clear()`, `refreshPeople()`. A new request cancels the one in flight. Any people failure leaves the row empty. The factory takes `searchPhotos`, `loadPeople`, `loadPhotosOf`.
`internal val SUGGESTIONS` in order: "dog on the beach", "sunset", "snow", "birthday cake", "mountains". Strings, not resources, as these are queries to the server.
Test: `ui/search/SearchViewModelTest.kt`.

### [ ] 3.2 — Voice

File: `ui/search/VoiceInput.kt` (new): `@Composable fun rememberVoiceInput(onHeard: (String) -> Unit): () -> Unit`, launching `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (`LANGUAGE_MODEL_FREE_FORM`) with `rememberLauncherForActivityResult`, catching `ActivityNotFoundException` so the mic does nothing rather than crash. `internal fun firstHeard(results: List<String>?): String?`, `internal fun isVoiceKey(key: Key): Boolean` (`Key.Search`, `Key.VoiceAssist`, `Key.Assist`).
File: `ui/main/MainScreen.kt`: add a `search: @Composable () -> Unit` slot and `onVoiceKey: () -> Unit`; the root Column's `onPreviewKeyEvent` calls it on key up for a voice key while `tab == Tab.Search`, and consumes the down event.
File: `MainActivity.kt`: create the `SearchViewModel` (key `search-$connection`) and voice input in the Main branch, and wire both. Update the previews.
Test: `ui/search/VoiceInputTest.kt`.

### [ ] 3.3 — The screen

File: `ui/search/SearchScreen.kt` (new): `SearchScreen(viewModel, onVoice, onPlay: (List<Slide>) -> Unit, focusResults: Boolean)` with `RefreshOnStart(viewModel::refreshPeople)`; content in one `LazyVerticalGrid(GridCells.Fixed(7))`, full-span items above the tiles, gaps 16dp, room for the focus outline as in `PhotoGrid`:

- Field row: `SearchField` (file-private: `surface`, fully round, 88dp tall, 1100dp wide, search icon, 32sp 700, placeholder "Search your photos", OK opens the keyboard as `HouseTextField` does, `ImeAction.Search` calls `submit`), the 88dp amber voice button (rule 3's exception; focus outline `fg`, as on the canvas), and the hint "Press the mic on your remote and say it" 22sp `mfg`.
- "Try" 20sp `mfg`, then the `Chip`s; selected when it equals the current description.
- People: "People" 26sp 700, a `LazyRow` of `PersonCircle` (120dp circle of `AsyncImage(PersonFace(id))`, amber focus, name 18sp 600 one line, ellipsised). Left out when there are no people.
- Results when a subject is set: heading `“text”` or the person's name 30sp 800, count 22sp `mfg`, and `PrimaryButton` with the play icon "Play as slideshow", shown only when `slides.playable()` isn't empty, calling `onPlay(slides.playable())`. Tiles: `PhotoTile` at 4:3, focusable, badge on videos.
- Count: `internal fun resultCount(subject, count, limit)`: "62 photos" (plurals), "100 best matches" at a search's cap, "1,000 newest photos" at a person's cap. Test it.
- Loading: 14 `SkeletonBlock` tiles at 4:3 under the heading, and the field keeps focus.
- Empty: `EmptyState("Nothing found", "Try describing it another way.", "Clear search", clear)`, Failed: `FailedState(problem, retry)`, each in a full-span item with `Modifier.height(480.dp)`: `StateCard` fills its height, which a grid item doesn't bound.
- `BackHandler(enabled = state.subject != null)`: `clear()`, then focus on the field. Results take focus on their first tile when they arrive; `focusResults` puts it on Play.

File: `res/values/strings.xml`: every label above; plurals reuse `photo_count`. File: `res/drawable/ic_mic.xml`, `ic_search.xml` if missing.
File: `ui/main/PlaceholderTab` stays for Home and Photos. File: `docs/design/README.md`: extend the Search row with the field's measures, the person circle and the system speech prompt.
Previews: idle, loaded, skeleton, empty, failed.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*SearchViewModelTest' --tests '*VoiceInputTest'`
- `./build.sh ./gradlew --console=plain testDebugUnitTest lintDebug assembleDebug`

---

## Checkpoint 4 — A person's photos and playing results · commit: `feat(search): show a person's photos and play results as a slideshow`

Goal: choosing a person fills the results area, and Play as slideshow goes there and back.

### [ ] 4.1 — Wire it up

File: `ui/search/SearchScreen.kt`: OK on a `PersonCircle` calls `choose(person)`.
File: `MainActivity.kt`: `onPlay = app::openResultsSlideshow`, `focusResults = current.focusResults`.
Test: `SearchViewModelTest` (person's result heading and count, person then text replaces, retry repeats the person), `AppViewModelTest` if the wiring adds a path.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`
- The state keeps results across a slideshow: the ViewModel is keyed to the activity, as Albums is.

---

## Out of scope

- Paging results; recent searches; places as suggestions; an in-app recogniser and the record-audio permission.
- Opening a photo from a tile (the viewer is #7).
- Global Google TV search or Assistant integration into the app.
- Slideshow settings for results.
- Filters beyond description and one person; several people at once.
- Moving to `filter.personIds` (v3.2+) while the top-level field works.
