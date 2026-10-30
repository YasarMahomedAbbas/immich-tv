# CLAUDE.md

Immich TV: an Android TV app (Kotlin, Compose for TV) for a Chromecast with Google TV that shows photos from a self-hosted Immich server: albums, a slideshow, the timeline and search. See `README.md` for the layout, build and release.

## Working on the app
- **Build and test only in Docker:** `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug` is what CI runs. Nothing is installed on the desktop apart from `adb`.
- **On the TV:** `./build.sh install` builds, installs over adb and opens the app. The TV's address is `TV_ADB` in `~/.config/immich-tv/verify.env`.
- **Release:** commit to `main`, push, then `git tag vX.Y.Z && git push origin vX.Y.Z`. The Release workflow builds the signed APK and publishes it; wait for the run with `gh run watch` and check the APK is on the release. The TV updates from GitHub Releases through Obtainium.
- **Signing key:** kept outside the repo in `~/.config/immich-tv/` and given to the Release workflow as the `RELEASE_KEYSTORE_B64` and `RELEASE_KEYSTORE_PASSWORD` secrets. Never print, move or regenerate it without asking: a new key forces the installed copy to be uninstalled before it can update.
- A bug fix comes with a test that fails without it.

## How tickets get built
Tickets are GitHub issues; a `Needs: #a, #b` line names what must be closed first. The skills in `.claude/skills/` carry them from issue to release with nobody watching:

- **`pipeline #<n>`** runs one ticket in its own worktree (`../worktrees/immich-tv/issue-<n>`) and tmux session (`immich-<n>`): `start-ticket` → `implement-plan` → `self-review` → `doc-drift` → `self-verification` → `raise-pr` → `merge-release`. The ticket folder `docs/tasks/<n>-<slug>/` (`CHECKLIST.md`, `NOTES.md`) is its stage log.
- **`autopilot`** starts a pipeline for every open ticket whose needs are closed (three at a time) and restarts ones a reboot stopped. A finished pipeline wakes it.
- **The user has authorised merging and releasing without them.** `merge-release` squash-merges once CI is green and verification passed, then tags the next version when app code changed. Reviews happen inside the pipeline; nothing waits for a person.
- **At a judgment call the pipeline parks** instead of guessing: a research note on the issue, the `parked` label, a push notification. Removing the label hands it back.
- **`design` tickets** end with a design canvas the user reviews (`design-canvas`); every screen is built with `design-recipe` and the manual in `docs/design/`.
- Real checks read `~/.config/immich-tv/verify.env`: `IMMICH_URL` and `IMMICH_API_KEY` for the server, `TV_ADB` (`<ip>:5555`) for the Chromecast. Without them, that check is skipped and recorded as such.

## Layers: the server is behind one door
`ui/` → `domain/` ← `data/`, and `AppContainer` is the only place that names a `data/` class. The goal: swapping Immich for another photo server means rewriting `data/` and nothing else.

`ArchitectureTest` (in `testDebugUnitTest`, so CI) fails on a `ui/` or `domain/` import of `data/`, on Android, OkHttp or kotlinx.serialization in `domain/`, and on any `data/` reference outside `data/` and `AppContainer`. It checks names, not shapes, so Immich field names in `domain/` are still for review.

- **`domain/`** is plain Kotlin in the app's own words: albums, photos, videos, people, a slideshow, a server that can refuse. No Android, no OkHttp, no JSON, and nothing Immich-shaped: no endpoint paths, asset ids treated as URLs, field names like `exifInfo`, or Immich enum strings. It defines the repository interfaces `data/` implements.
- **`data/`** is the only layer that talks to anything outside the process: HTTP, image loading headers, stored settings. Every Immich call, every response shape and every version difference stays here and is turned into domain models before it leaves.
- **`ui/`** sees only `domain/`: ViewModels call use cases, screens render state. A screen never imports `data/`, builds a URL or reads an Immich field.
- New server work starts by asking: what does the app need, in domain terms? That's the interface. How Immich gives it is the implementation.

## Immich API: always check the version the server runs
Every call to Immich (`data/`) must follow the API of the Immich version the server actually runs. Immich changes its API between releases, sometimes breaking it. Don't write or change a call from memory or from older examples.

- **API reference:** https://api.immich.app
- **Release notes** (breaking API changes): https://github.com/immich-app/immich/releases
- **The spec for the running version:** read the version from `<server>/api/server/version`, then use `open-api/immich-openapi-specs.json` at that tag in the Immich repo.
- **Auth:** the `x-api-key` header, on API calls and on every image and video request. Never in a URL. Every request, image and video loading included, goes through `immichHttpClient()`: OkHttp would carry a custom header along a redirect to another host, and its interceptor drops the key there.

When a response shape changes between versions, support both (the server may lag behind or run ahead, down to the oldest version the app accepts: `MINIMUM` in `ImmichServerConnector`) and add a test for each. MockWebServer accepts things a real Immich refuses; check API changes against the real server too.

## TV rules
- Everything is reachable with the D-pad: every screen has a visible focus (the amber outline), Back always goes back, and nothing needs a touchscreen or a pointer.
- Load preview-size images (`/thumbnail?size=preview`), never originals, for grids and the slideshow: the Chromecast has about 2 GB of RAM.
