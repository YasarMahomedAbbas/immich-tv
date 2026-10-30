# Notes — feat: search, by voice

Issue: #8 — feat: search, by voice (https://github.com/YasarMahomedAbbas/immich-tv/issues/8)
Branch: `feat/8-search-by-voice`

## Planning

- Approach: a `SearchRepository` in `domain/search/` (search, people, a person's photos) that returns `Slide`s, implemented by `ImmichSearchRepository` in `data/`. `SearchViewModel` owns the query, the results and the People row; the Search tab is one 7-column scrolling grid. Voice is the system speech prompt, and the slideshow learns to play any list of photos.
- Decided (pipeline, on the user's behalf; flag: ux, open-risk): Q1.1, voice uses the system speech prompt, opened by the on-screen mic and by a Search key from the remote. No microphone permission. The canvas's inline "Listening…" is replaced by the system overlay. Rejected: an in-app recogniser (Q1.2), which needs the record-audio permission, a first-use dialog and more states.
- Decided (pipeline, on the user's behalf; flag: ux): Q2.1, suggestion chips are a fixed list of everyday subjects, as on the canvas: dog on the beach, sunset, snow, birthday cake, mountains. Rejected: adding recent searches stored on the device (Q2.2), places from the server (Q2.3).
- Decided (pipeline, on the user's behalf; flag: ux, api): Q3.1, no paging: the 100 best matches for a search, up to 1000 newest first for a person; the slideshow plays what is shown. The heading says "100 best matches" at the cap, because Immich gives no real total. Rejected: loading more pages on scroll (Q3.2).
- Decided (pipeline, on the user's behalf; flag: ux): Q4.1, a person's photos show in the Search tab's results area with their name, count, grid and Play as slideshow. Back goes up in the tab. Rejected: a separate person screen (Q4.2).
- Decided (pipeline, on the user's behalf; flag: ux, api), the "decided, flag if you disagree" list:
  - Search runs on submit or chip, not per keystroke.
  - The People row shows named, unhidden people only. It is left out if the key lacks person.read, while search still works.
  - A server with smart search turned off gets its own failed message.
  - Videos show in the grid with the badge but are left out of the slideshow, as in albums.
  - A search slideshow plays in the order shown at default timing with no settings; Back returns to the results.
  - The slideshow gets a source other than an album, so its screen no longer depends on one.
- Decided while planning (no question raised):
  - Any failure loading people leaves the row out; it is read again when the screen returns.
  - Search calls use a longer read timeout (60 s) from a client derived from `immichHttpClient()`: the first query after the server's model has unloaded can take well over the app's 10 s.
  - A tile does nothing on OK until the photo viewer (#7), as in `PhotoGrid`.
- Open risks:
  - open-risk: **the physical mic button.** The app cannot be proven to receive it unattended. The TV's speech service resolves and a key layout maps the button's scan code to Search (which apps receive), but that layout was not confirmed to be the remote's, and a simulated key press proves only the app's handling. If the system keeps the button for the Assistant, that "Done when" item and the canvas's hint fail. Self-verification must record the outcome here: what was tested, what was not, and whether the hint stayed. If the button can't be shown to work, the hint is dropped and the gap is stated in the PR. The button stays a "Done when" item, so its handling is built either way (checkpoint 3.2); unless proven on the TV it is recorded as unproven, never as passed.
  - api: the top-level `personIds` on `/search/metadata` is deprecated since v3.2.0. It works on 3.3.5 and exists in 1.133, so one shape covers the range; a future major may remove it. The nested `filter.personIds` is not an array, and 3.3.5 refuses one.
  - ux: smart search returned results even for nonsense text, so "no results" will be rare and weak matches will appear as results.
  - The real server has 2 named people out of 12, so the row is short there, and the search results checked have no city or country, so place is proven only in tests.

## Implementation

- Shipped: search, people and a person's photos in domain terms (`domain/search/Search.kt:17`), from Immich in both API shapes (`data/immich/ImmichSearchRepository.kt:32`), faces through the image loader (`data/immich/PreviewInterceptor.kt`); a slideshow of any list (`ui/slideshow/SlideshowViewModel.kt`, `AppScreen.ResultsSlideshow` in `ui/AppViewModel.kt`); the Search tab (`ui/search/SearchScreen.kt`, `ui/search/SearchViewModel.kt`), voice (`ui/search/VoiceInput.kt`, the key in `ui/main/MainScreen.kt`), `VoiceButton` (`ui/components/Buttons.kt`).
- Tests: 304 passed / 0 failed; full CI command (testDebugUnitTest koverVerifyDebug lintDebug assembleDebug) green.
- Outstanding: nothing unticked in the checklist. The test plan's real-server and on-the-TV checks are self-verification's, including the physical mic button (still unproven) and whether moving focus to the field opens the keyboard (on Back from results, and when a load takes away what the remote was on).
- Contradicted the plan: none. Added beyond its text, each edited into the checklist: `decode()` made internal alongside `place()`; a `backLabel` on `SlideshowScreen` ("‹ Search"), as the controls' "‹ Album" is wrong for results; `resultCount` returns a `ResultCount` so the wording is tested without resources; focus moves to the field when a load removes what the remote was on; `VoiceButton` is a shared component.

## Review

- Fixed: archived and hidden assets could appear in search and person results on v3 (`data/immich/ImmichSearchRepository.kt`). With no `visibility`, v3.3.5 searches everything but locked (`search.service.ts`: `'not-locked'`), while v1.133 defaulted to the timeline, so an archived photo would have shown and played on a current server. Both bodies now send `"visibility":"timeline"`; the request tests fail without it.
- Raised: none
- API: `POST /api/search/smart` and `POST /api/search/metadata`: match the v3.3.5 and v1.133.0 specs; the top-level `visibility`, like `personIds` and `order`, is deprecated since v3.2 but accepted, with the same enum in both, and 3.3.5 answered 200 with only timeline assets. The server has no archived photos, so the exclusion itself is proven from its source and the request tests, not by a real archived photo. `GET /api/people?size=1000` and `GET /api/people/{id}/thumbnail`: match both specs.
- Layers: clean

## Doc drift

- Applied: README.md:Features — Search no longer "planned, #8"; says what it does, its caps and that it needs smart search on (rung 3). The remote's mic button is deliberately not claimed: it is still unproven, for self-verification.
- Applied: README.md:Features — the slideshow plays search results too, not only an album (rung 3).
- Applied: README.md:Install — `person.read` is optional and what its absence does (rung 3). `GET /people` and `/people/{id}/thumbnail` need it in the v3.3.5 spec and in v1.133.0's `person.controller.ts`.
- Applied: docs/design/README.md:Components — Failed lists the smart-search-off reason; Screens — the progress bar is the position in the show, not the album (rung 4).
- Skipped: `problem_key_lacks_permission` and the manual's matching sentence not widened to person.read: it is shown only for a failure that reaches the screen, and a people failure never does (`SearchViewModel.refreshPeople`). Search and a person's photos need asset.read (v3.3.5 spec), which it names. Naming person.read would send people to change a key for an error it can't fix.
- Skipped: the system speech prompt / no microphone permission: the manual already says it, the manifest shows it.
- Skipped: the 60 s search timeout and the `visibility` field: code comments at the point of use say why.
- Skipped: CLAUDE.md: no new cross-cutting rule. The face thumbnail isn't a preview-size exception worth a line: it is its own small endpoint, in `data/`, and goes through the same client. The architecture block, RefreshOnStart and the `connection` key rule still hold (`SearchScreen`, `MainActivity`).
- Stale, fixed separately: none
