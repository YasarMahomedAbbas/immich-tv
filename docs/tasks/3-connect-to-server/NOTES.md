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
