# Notes — feat: connect to the Immich server

Issue: #3 — feat: connect to the Immich server (https://github.com/YasarMahomedAbbas/immich-tv/issues/3)
Branch: `feat/3-connect-to-server`

## Planning

- Approach: `ConnectToServer` (domain) checks the input, then `ImmichServerConnector` (data) pings `/api/server/ping` and reads `/api/users/me` with `x-api-key`. The session is stored with the key AES-GCM-encrypted by an AndroidKeyStore key. `AppViewModel` picks Setup or Home, and Home has a Settings gear back to Setup.
- Decided (pipeline, on the user's behalf; flag: security): Q1.1, the key is encrypted with an AES key in the Android Keystore and kept in private SharedPreferences, with no new dependency. Rejected: plain SharedPreferences, and the deprecated security-crypto library.
- Decided (pipeline, on the user's behalf; flag: ux): Q2.1, Settings is a gear button at the right end of the top menu, beside the clock. Until the top menu exists, the Home placeholder shows it as a secondary button in that slot. Recorded in the design manual.
- Decided (pipeline, on the user's behalf; flag: api): Q3.1, Immich v1.118 and newer only (the `/api/server/*` paths). Older servers get the "not an Immich server (v1.118 or newer)" message.
- Decided (pipeline, on the user's behalf): the address is accepted with or without a scheme (default `http://`), with a trailing `/` trimmed in domain and a trailing `/api` stripped in data.
- Decided (pipeline, on the user's behalf): navigation is hand-rolled (an `AppScreen` state in `AppViewModel` plus BackHandler), with no navigation-compose dependency.
- Decided (pipeline, on the user's behalf): a stored session opens straight to Home, with no check at launch.
- Decided (pipeline, on the user's behalf): the key field is masked, with a Show/Hide toggle.
- Decided (pipeline, on the user's behalf): no HTTP logging interceptor at all, and `ApiKey.toString` is redacted.
- Decided (pipeline, on the user's behalf): 10s connect and read timeouts, so an unreachable server fails quickly.
- Decided (pipeline, on the user's behalf): new dependencies are OkHttp, kotlinx.serialization (plugin + json), coroutines and lifecycle-viewmodel-compose. All are in the catalog already except the serialization plugin.
- Decided: a 403 from `/users/me` is its own message (the key lacks `user.read`), and a 5xx counts as unreachable. A key that can't be decrypted at load sends the app back to Setup.
- Decided: `KeystoreCipher` and `SharedPreferencesStorage` need a device, so they are kept tiny and excluded from Kover; the logic behind them is tested with fakes.
- Open risks:
  - No `verify.env` on this machine, so the plan follows the v3.3.5 spec (the latest release) rather than the live server's version, and the real-server and TV checks will be skipped unless it appears.
  - tv-material has no text field, so the on-screen keyboard behaviour of a `BasicTextField` on the Chromecast is unproven until the TV check.

## Implementation

- Shipped:
  - Domain: `ServerAddress.parse` (`domain/server/Server.kt:14`, `@ConsistentCopyVisibility` so `copy()` can't bypass it), the redacted `ApiKey` (`Server.kt:32`), `ConnectResult`, `ServerConnector`, `SessionStore`, and the `ConnectToServer`/`GetSession` use cases (`domain/server/ConnectToServer.kt`).
  - Data: `apiUrl` strips a trailing `/api` (`data/immich/ImmichHttp.kt:27`); the client has 10s timeouts and no logging interceptor. `ImmichServerConnector` pings without the key (`data/immich/ImmichServerConnector.kt:32`), then reads `/users/me` with `x-api-key` (`:44`). `KeystoreCipher` (AES-256/GCM in the AndroidKeyStore, `data/settings/KeystoreCipher.kt:17`) and `StoredSessionRepository`, where a key that won't decrypt counts as no session (`data/settings/StoredSessionRepository.kt:16`).
  - Wiring: `AppContainer` (`AppContainer.kt:16`), built in `ImmichTvApp.onCreate`.
  - UI: `AppViewModel` routes Loading/Setup/Home, and Back from Setup opened in Settings goes Home (`ui/AppViewModel.kt:46`). `SetupViewModel` ignores Connect while connecting (`ui/setup/SetupViewModel.kt:48`). `SetupScreen` has previews for every state. `HomeScreen` gets the Settings gear top-right with the initial focus (`ui/home/HomeScreen.kt:30`). `MainActivity` finishes when `back()` returns false (`MainActivity.kt:30`).
  - Components: `PrimaryButton`/`SecondaryButton` (`ui/components/Buttons.kt`), `HouseTextField` (OK opens the IME) and the shared `Modifier.focusOutline` (`ui/components/HouseTextField.kt:49`, `:98`), and the `ic_settings` gear.
  - Manual: a Setup row, the Settings gear slot, and the text field recipe in `docs/design/README.md`.
- Tests: 67 passed / 0 failed (full CI command: `testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`, BUILD SUCCESSFUL). Line coverage is 88.9%. Lint has no errors; its warnings are Gradle/AGP version nags, a long vector path in `ic_settings`, and pre-existing manifest notes.
- Outstanding: none in the checklist. The real-server and on-TV checks in the test plan belong to self-verification. Still unproven until then: whether D-pad focus alone pops the IME on a `BasicTextField` on the Chromecast, and whether a disabled Connect keeps focus while connecting.
- Contradicted the plan:
  - `@Preview`/`Devices.TV_1080p` need `compose-ui-tooling-preview`, which wasn't in the dependency list. Added it from the BOM, with `ui-tooling` as `debugImplementation`.
  - A `surface` text field on a `surface` panel would be invisible, so `HouseTextField` is `raised`. Task 3.4 and the manual were updated to match.
  - The house focus outline for non-button items became a shared `Modifier.focusOutline` in `HouseTextField.kt`.

## Review

- Fixed: `x-api-key` followed a redirect to another server. OkHttp follows redirects and, across hosts, strips only `Authorization`, so an address that redirects `/api/users/me` elsewhere would have handed that host the key. A network interceptor in `immichHttpClient()` (`data/immich/ImmichHttp.kt`) now drops the key from any hop off the original host/port. It keeps the key on a same-host http → https upgrade, but drops it on a downgrade. Tests: a redirect to a second MockWebServer arrives without the key (it failed before the fix), and a same-server redirect keeps it.
- Raised: none
- API: the server runs v3.3.5 (`/api/server/version`); checked against that tag's spec. `GET /api/server/ping`: no security, `ServerPingResponse` with `res` required. Matches. `GET /api/users/me`: `api_key` scheme is the `x-api-key` header, permission `user.read`, `UserAdminResponseDto` with `name` required. Matches; the v3.3.5 test body has every required field.
- Layers: clean

## Doc drift

- Applied: README.md:Install — the app needs Immich v1.118+ (cites the v1.118.0 release note: `/api/server-info/*` → `/api/server/*`) and a key with at least `user.read` (v3.3.5 spec, checked in review). Rung 3: a user making a scoped key or running an old server would otherwise hit the error first.
- Applied: CLAUDE.md:Immich API, Auth — every request, image loading included, goes through `immichHttpClient()`, whose interceptor drops `x-api-key` on a redirect off the server's host. Rung 2: the image-loading ticket could plausibly build its own client (e.g. for the image loader) and leak the key.
- Skipped: Keystore-encrypted session storage — README's "stored settings" still holds; the code and `StoredSessionRepository` say the rest.
- Skipped: hand-rolled navigation (`AppScreen` in `AppViewModel`) — one `rg` finds it, and the choice is recorded in Planning.
- Skipped: Kover exclusions for `KeystoreCipher`/`SharedPreferencesStorage` — commented in `app/build.gradle.kts`.
- Skipped: Setup row, Settings gear slot and text field recipe — already added to `docs/design/README.md` by implement-plan; checked against `SetupScreen`, `HomeScreen` and `HouseTextField`.
- Checked, no change: README architecture block, "plain http:// allowed" (`usesCleartextTraffic` in the manifest), "HTTP client tested against MockWebServer", the build commands.
- Stale, fixed separately: none
