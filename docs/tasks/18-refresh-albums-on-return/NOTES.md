# Notes — fix: albums and album screens never refresh once loaded

Issue: #18 — fix: albums and album screens never refresh once loaded (https://github.com/YasarMahomedAbbas/immich-tv/issues/18)
Branch: `fix/18-refresh-albums-on-return`

## Planning

- Approach: each screen refreshes silently on entry and on returning to the app, through a quiet path that never shows the skeleton. It reads one cheap summary and fetches more only if that changed; a failure leaves the screen alone.
- Decided (pipeline, on the user's behalf; flag: ux): Q1.1, refresh silently on returning to the app and on entering a screen, with no button. Rejected: only on returning to the app (Q1.2), a Refresh action on each screen (Q1.3).
- Decided (pipeline, on the user's behalf; flag: ux): Q2.1, a failed background refresh keeps what is loaded, with no message, and tries again next time. A deleted album or revoked key isn't announced until something fails to open. Rejected: switching to the failed screen with Try again (Q2.2).
- Decided (pipeline, on the user's behalf; flag: api): Q3.1, re-read one summary and fetch more only if it changed: the album's own record for the album screen, the month list for the grid (only months whose count changed). A same-count swap with no asset edit could stay stale. Rejected: re-reading everything each time (Q3.2), about 30 requests per entry for the 699-item album. Checked in the v3.3.5 server source: the album's count and last-modified time are a live query over its non-trashed assets, and adding assets bumps its updated time.
- Decided (pipeline, flagged to the user's stand-in; flag if you disagree):
  - A refresh never passes through the skeleton, and identical data causes no redraw, so focus and scroll stay put. Filter and sort are kept.
  - When data did change, the focused item keeps focus and the grid scrolls to keep it visible (a newly updated album jumps to the top in the default sort).
  - If the focused item is gone, focus moves to the first item. No item ever sits unfocusable.
  - A failed screen retries silently on return.
  - Empty and failed states keep their existing buttons. Refresh runs alongside them.
- Open risks (flag: open-risk): self-verification only makes read-only calls, so on the TV it can prove "nothing changed keeps focus". Changed-data behaviour rests on unit tests, as the project has no Compose UI tests, unless someone edits an album during that run.

## Implementation

- Shipped: `RefreshOnStart` and `lostFocus` (`ui/components/RefreshOnStart.kt:13`); a quiet `refresh()` on `AlbumsViewModel` (`ui/albums/AlbumsViewModel.kt:73`), `AlbumViewModel` (`ui/album/AlbumViewModel.kt:73`) and `PhotoGridViewModel` (`ui/photos/PhotoGridViewModel.kt:94`), with `mergeMonths` (`ui/photos/PhotoGridViewModel.kt:50`); `LoadAlbum.ifChanged` (`domain/albums/AlbumRepository.kt:38`); focus and scroll handling in `AlbumsScreen`, `AlbumScreen` and `PhotoGrid`; the album's order in the photo grid's ViewModel key (`MainActivity.kt`). `lifecycle-runtime-compose` added.
- Tests: full CI command green (testDebugUnitTest, koverVerifyDebug, lintDebug, assembleDebug): 206 passed / 0 failed, 0 skipped.
- Outstanding: none unticked. The Compose focus and scroll code (the 1.3, 2.3 and 3.2 screen changes) compiles and passes lint but has no automated test, as the project has no Compose UI tests; the TV checks in the test plan are for self-verification.
- Contradicted the plan: nothing in the code contradicted it. Three refinements, with the checklist text updated: `AlbumGrid`'s effect is keyed on the order as well as the list, or a filter leaving the same list would be remembered late and the next refresh would jump to the top; it waits a frame before reading `layoutInfo`, which otherwise describes the old list; `reread` is a suspend function run one after another inside `refreshJob`, so a refresh counts as running until they finish.

## Review

- Fixed: a loaded month whose re-read failed kept the new count, so the next refresh saw no change and never re-read it: the heading said 3, the grid showed 2, until the app was killed. The month now goes back to its old count on failure, as the screen was, and the next refresh tries again (`ui/photos/PhotoGridViewModel.kt`; the test that had asserted the mismatch now asserts this and fails without the fix).
- Raised: none
- API: no call changed. `GET /albums`, `GET /albums/{id}`, `GET /timeline/buckets`, `GET /timeline/bucket` match the v3.3.5 spec, the version the server runs (`AlbumDto`'s required fields are required there; `lastModifiedAssetTimestamp`, `startDate`, `endDate`, `order` optional; bucket `count` and `timeBucket` required).
- Layers: clean
- Not automated: the Compose focus and scroll code was read, not run, here; the TV checks are self-verification's.

## Doc drift

- Applied: `README.md:Architecture`: a screen showing server data calls `RefreshOnStart` with a quiet `refresh()`, and what quiet means (rung 3: nothing enforces it, and a new screen such as Photos or Search that loads once would bring this bug back).
- Skipped: the album's order in the photo grid's ViewModel key: the comment at that line in `MainActivity` says why.
- Skipped: `LoadAlbum.ifChanged`, `mergeMonths`, `lostFocus`, the focus handling: found by reading the screens, and covered by the README rule where it matters.
- Skipped: `lifecycle-runtime-compose`: the version catalog says it.
- Skipped: `docs/design/README.md`: no claim changed. The screens' shapes and the Refresh and Try again buttons stay as written; refresh adds no visible state.
- Checked, still true: README's `AppViewModel.connection` key rule (every server-data key in `MainActivity` still takes it), its layer block, `CLAUDE.md`'s layer rules (`domain/` addition is plain Kotlin) and its CI command (the branch changed neither workflows nor `build.sh`).
- Stale, fixed separately: none

## Self verification

Result: partial

- CI build: 206 tests passed, 0 failed, 0 skipped; line coverage 87.7% (minimum 80); lint with no errors (22 warnings, mostly dependency versions; not compared with main); APK built.
- Server: Immich v3.3.5. The branch changes no `data/` call; the test plan's read-only reads ran.
- TV: debug build installed on the Chromecast, inside the window the user gave, holding the shared TV lock. Driven with the D-pad: Albums, the album screen and its photo grid, each with Home and return.
- Verified:
  - `GET /albums` (15 albums), `GET /albums/{id}` (the 699-item album) and `GET /timeline/buckets` (30 months, counts sum to 699), each read twice: identical answers, so an unchanged album skips the re-read and a real refresh is one call. Every field `Album` reads was present.
  - Albums, card focused on the third row with the grid scrolled: after Home and return, the screenshot is pixel-identical (same card, same scroll, no skeleton).
  - Album screen, third visible strip tile focused with the strip scrolled: pixel-identical once the launcher's crossfade ended; the first frame back already showed the content and the focus, no skeleton.
  - Photo grid, focus on the eleventh row, past the 29 items of the first month: pixel-identical after the same crossfade.
  - Back from Browse to the album keeps Browse photos focused. The strip is back at its start by then; only focus was asked for.
  - The refresh does fire: on the album screen, the app's traffic rose by about 1.3 KB received and 0.9 KB sent on return, and by nothing while on the launcher, so one small call and no images reloaded.
  - No crash or error from the app in logcat.
  - A launcher-style intent on top of the `am start -n` that `build.sh install` uses stacked a second activity, which opened on Home; a harness effect of mixing intents, not of this branch. Back removed it and the return checks then used the same intent, with the same activity record throughout.
- Failed: none
- Not self-verifiable: changed data, as this stage may not edit the server and nobody edited an album during the run; it rests on the unit tests. By hand: open an album, press Home, add a photo to it in Immich, return: the counts and strip update and Browse shows the photo; a new album shows in the Albums tab; delete the focused album or photo: focus moves to the first item.
