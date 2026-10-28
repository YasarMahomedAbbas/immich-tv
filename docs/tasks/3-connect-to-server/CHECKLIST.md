# Checklist — feat: connect to the Immich server

Issue: #3 — feat: connect to the Immich server (https://github.com/YasarMahomedAbbas/immich-tv/issues/3)
Branch: `feat/3-connect-to-server`
Problem: the app has no way to know which server to show photos from, so nothing else can load.

## Done when

- A setup screen with two fields (server address, API key) that work with the TV's on-screen keyboard and the D-pad, and a **Connect** button.
- Connect checks that the server is Immich and that the key works (the current user), and shows a clear message for: can't reach the server, not an Immich server, the key is wrong (plus: the key lacks permission, the address is not valid, the key is empty).
- The address and key are stored on the device (the key encrypted with an Android Keystore key, never in logs or a URL), and the app opens straight past setup next time.
- A way back to setup later (to change the server or key) from a Settings entry: a gear button, shown on the Home placeholder until the top menu exists.
- Domain: a `ServerAddress`/`ApiKey`/`Session` in the app's words. `data/`: the Immich client with the `x-api-key` header, tested with MockWebServer.

## References

- Immich API, checked against the v3.3.5 spec (the latest release; no server configured, so no live version), cross-checked with v1.118.0 and v2.0.0:
  - `GET /api/server/ping`: no auth, `200 {"res":"pong"}` (`ServerPingResponse`). Before v1.118 the path was `/api/server-info/ping`, which is out of scope (Q3.1).
  - `GET /api/users/me`: auth via the `x-api-key` header (security scheme `api_key`), permission `user.read`, `200 UserAdminResponseDto` (only `name` is read; `name` is required in every version checked). A bad key gets 401, and a key without `user.read` gets 403.
  - `GET /api/server/version`: not called here. Its `prerelease` field exists only from v3.0.0, so any later reader takes only `major`/`minor`/`patch`.
- Reference: https://api.immich.app (Server → Ping, Users → Get current user).
- Design: https://claude.ai/artifact/8Tq2kuJDNjk3SFiALwbfZH has no setup row. Follow `docs/design/README.md`; this ticket adds the Setup shape and the settings gear slot to the manual.

## Context

The app is a skeleton: `MainActivity` shows a placeholder `ui/home/HomeScreen.kt`, `domain/` and `data/` are empty packages, there is no `AppContainer`, and `ArchitectureTest` enforces the layers. So this ticket sets the first pattern for each layer.

- `domain/server/` holds the models, the `ServerConnector` and `SessionStore` interfaces, and the `ConnectToServer` use case.
- `data/` holds `ImmichServerConnector` (OkHttp + kotlinx.serialization, tested with MockWebServer) and `StoredSessionRepository`, which encrypts the key through a `SecretCipher` (`KeystoreCipher`: AES-256/GCM in the AndroidKeyStore) into a `StringStorage` (`SharedPreferencesStorage`).
- `ui/` holds an `AppViewModel` choosing between Setup and Home, a `SetupViewModel`, the `SetupScreen` and the first shared components.
- The root `AppContainer` wires them.

Decisions: Q1.1 Keystore-encrypted key (security), Q2.1 a Settings gear at the right of the top menu, shown as a secondary button on the Home placeholder until that menu exists (ux), Q3.1 Immich v1.118+ only (api). The remaining decisions are in NOTES § Planning. The device-only classes (`KeystoreCipher`, `SharedPreferencesStorage`) are kept tiny and excluded from Kover; everything with logic behind them is tested on the JVM with fakes.

## Test plan

- Unit:
  - `ServerAddressTest`: scheme added, trailing slashes trimmed, whitespace trimmed, `https` kept, blank / bad scheme / no host → null.
  - `ApiKeyTest`: `toString` never contains the key.
  - `ConnectToServerTest`: invalid address and blank key short-circuit without a call; Connected saves the session; each failure saves nothing.
  - `StoredSessionRepositoryTest`: round trip; storage never holds the plaintext key; empty → null; a cipher failure → null.
  - `SetupViewModelTest`: field edits; Connect → connecting → connected event; each result → its message; prefill from a stored session; a second Connect while connecting is ignored.
  - `AppViewModelTest`: no session → Setup(first run); session → Home; open settings → Setup(change); connected → Home; Back from change → Home.
- MockWebServer (`ImmichServerConnectorTest`):
  - happy path with a v3.3.5-shaped and a v1.118-shaped `/users/me` body;
  - ping 404, ping 200 HTML, ping 200 `{"res":"nope"}` → NotImmich;
  - me 401 → KeyRejected; me 403 → KeyLacksPermission; ping or me 500 → Unreachable; server shut down → Unreachable;
  - the request paths are exactly `/api/server/ping` and `/api/users/me`; `x-api-key` is on `/users/me`, not on the ping, and the key is in no request URL;
  - an address entered with a trailing `/api` hits the same paths.
- Real server: if `IMMICH_URL`/`IMMICH_API_KEY` are set, `GET /api/server/version`, `GET /api/server/ping` and `GET /api/users/me` with the key, plus `/api/users/me` with a wrong key (expect 401). Otherwise skipped and recorded.
- On the TV: on a fresh install the app opens on Setup. Check that:
  - D-pad moves address → key → Show → Connect; each field opens the on-screen keyboard; a wrong key shows its message; correct values reach Home;
  - after a relaunch the app goes straight to Home; Settings → Setup is prefilled, and Back returns Home; on first-run Setup, Back exits.

  This needs `TV_ADB`; otherwise it is skipped and recorded.

---

## Checkpoint 1 — the connection check · commit: `feat(server): check an Immich server and its API key`

Goal: `ConnectToServer` validates the input and reports exactly one outcome for any server, tested with fakes and with MockWebServer.

### [ ] 1.1 — Dependencies

Files: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`

- In the catalog, add the plugin `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`.
- In the root build, add it with `apply false`; in the app build, apply it.
- `implementation`: `okhttp`, `serialization-json`, `coroutines-android`, `lifecycle-viewmodel-compose`.
- `testImplementation`: `okhttp-mockwebserver`, `coroutines-test`.

All of these except the plugin are already in the catalog.

### [ ] 1.2 — Domain models and interfaces

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/server/Server.kt`
```kotlin
/** Where the photo server lives: an http(s) URL with no trailing slash. */
data class ServerAddress private constructor(val url: String) {
    companion object {
        /** Trims, adds `http://` when no scheme is given, drops trailing `/`; null if blank, not http(s), or no host. */
        fun parse(input: String): ServerAddress?
    }
}
/** The secret that opens the server. [toString] is redacted so it never reaches a log. */
class ApiKey(val value: String) { override fun toString() = "ApiKey(****)"; equals/hashCode on value }
data class Session(val server: ServerAddress, val apiKey: ApiKey)

sealed interface ConnectResult {
    data class Connected(val session: Session, val userName: String) : ConnectResult
    data object InvalidAddress : ConnectResult
    data object MissingKey : ConnectResult
    data object Unreachable : ConnectResult      // network failure, timeout, 5xx
    data object NotPhotoServer : ConnectResult   // answered, but not as the server we speak to
    data object KeyRejected : ConnectResult
    data object KeyLacksPermission : ConnectResult
}

/** Asks a server whether it is one we can talk to and whether the key opens it. */
interface ServerConnector { suspend fun check(session: Session): ConnectResult }

/** The one remembered session. */
interface SessionStore { suspend fun load(): Session?; suspend fun save(session: Session) }
```
Parse the host with `java.net.URI` (plain Kotlin/JDK, allowed in domain).

### [ ] 1.3 — The use case

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/domain/server/ConnectToServer.kt`
`class ConnectToServer(private val connector: ServerConnector, private val store: SessionStore) { suspend operator fun invoke(address: String, apiKey: String): ConnectResult }`.

1. Parse the address; if that fails, return `InvalidAddress`.
2. Trim the key; if it's blank, return `MissingKey`.
3. Call `connector.check`, and on `Connected` call `store.save(result.session)`.

Also `class GetSession(private val store: SessionStore) { suspend operator fun invoke(): Session? }` in the same file.

### [ ] 1.4 — The Immich connector

Files: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/data/immich/ImmichHttp.kt`, `.../data/immich/ImmichServerConnector.kt`

- `ImmichHttp.kt`: `internal fun immichHttpClient(): OkHttpClient` with connect/read timeouts of 10s and **no logging interceptor**. Also `internal val immichJson = Json { ignoreUnknownKeys = true }` and `internal fun apiUrl(server: ServerAddress, path: String): HttpUrl`, which removes a trailing `/api` from the address and appends `/api/<path>`.
- `ImmichServerConnector(private val client: OkHttpClient) : ServerConnector`, running on `Dispatchers.IO`:
  1. `GET api/server/ping` without the key. An `IOException` → `Unreachable`; 5xx → `Unreachable`; any other non-200, a body that isn't JSON, or `res != "pong"` → `NotPhotoServer`.
  2. `GET api/users/me` with the header `x-api-key: <key>`. 200 → parse `@Serializable private data class MeDto(val name: String)` → `Connected(session, name)`; 401 → `KeyRejected`; 403 → `KeyLacksPermission`; 5xx or an `IOException` → `Unreachable`; anything else (including a body that won't parse) → `NotPhotoServer`.
- A URL that fails to build → `Unreachable`. Close every response.
- The DTOs (`PingDto(val res: String)`, `MeDto`) are private to this file.

### [ ] 1.5 — Tests

Files: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/domain/server/ServerAddressTest.kt`, `ApiKeyTest.kt`, `ConnectToServerTest.kt` (hand-written fakes, `runTest`), `app/src/test/java/io/github/yasarmahomedabbas/immichtv/data/immich/ImmichServerConnectorTest.kt`.

Cover the cases in Test plan → Unit and MockWebServer. For the v3.3.5 body, take the full `UserAdminResponseDto` example (with `clusterGroupId`, `license`, etc.); the v1.118 body omits the v3-only fields.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*.domain.server.*' --tests '*.data.immich.*' --tests '*ArchitectureTest'`
- Every case green; `ArchitectureTest` still passes (no OkHttp or serialization in `domain/`).

---

## Checkpoint 2 — remembering the session · commit: `feat(server): remember the session with a Keystore-encrypted key`

Goal: a saved session survives a restart, with the key stored only encrypted.

### [ ] 2.1 — Storage and cipher seams

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/data/settings/Storage.kt`
```kotlin
internal interface StringStorage { fun get(key: String): String?; fun put(values: Map<String, String?>) }
internal interface SecretCipher { fun encrypt(plain: String): String; fun decrypt(sealed: String): String }
internal class SharedPreferencesStorage(private val prefs: SharedPreferences) : StringStorage  // put uses one edit().apply(); null removes
```

### [ ] 2.2 — Keystore cipher

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/data/settings/KeystoreCipher.kt`
`internal class KeystoreCipher(private val alias: String = "immich_tv_api_key") : SecretCipher`:
- AES-256/GCM/NoPadding, with the key generated once in `AndroidKeyStore` (`KeyGenParameterSpec`, purposes ENCRYPT|DECRYPT, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`).
- The output is `java.util.Base64` of `iv (12 bytes) + ciphertext`.
- It never logs anything.

### [ ] 2.3 — The session repository

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/data/settings/StoredSessionRepository.kt`
`internal class StoredSessionRepository(private val storage: StringStorage, private val cipher: SecretCipher) : SessionStore`, on `Dispatchers.IO`.
- Keys: `server_url`, `api_key_sealed`.
- `save` writes the URL and `cipher.encrypt(key)`.
- `load` returns null if either key is missing. If `decrypt` throws (for example the Keystore key was lost), it returns null, so the app goes back to Setup. Otherwise it returns `Session(ServerAddress.parse(url) ?: return null, ApiKey(plain))`.

### [ ] 2.4 — Coverage excludes

File: `app/build.gradle.kts`: add `"*.KeystoreCipher*"` and `"*.SharedPreferencesStorage*"` to the Kover `classes(...)` excludes, with a comment saying they need a device.

### [ ] 2.5 — Tests

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/data/settings/StoredSessionRepositoryTest.kt`. Use an in-memory `StringStorage`, plus a fake cipher that reverses and prefixes the string and can be switched to throw. Cover the cases in Test plan → Unit.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*.data.settings.*'`
- The stored value for `api_key_sealed` is not the plaintext key (asserted).

---

## Checkpoint 3 — the setup screen and the way back · commit: `feat(ui): set up the server on first launch and from settings`

Goal: on the TV, the app opens on Setup the first time, on Home afterwards, and Settings leads back to Setup.

### [ ] 3.1 — AppContainer

Files: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/AppContainer.kt`, `ImmichTvApp.kt`
`class AppContainer(context: Context)` builds:
- `immichHttpClient()`;
- `ImmichServerConnector`;
- `StoredSessionRepository(SharedPreferencesStorage(context.getSharedPreferences("session", MODE_PRIVATE)), KeystoreCipher())`.

It exposes only domain types: `val connectToServer: ConnectToServer`, `val getSession: GetSession`. `ImmichTvApp` holds `lateinit var container: AppContainer`, set in `onCreate`.

The `data/` classes are `internal`, so `AppContainer` (same module) can name them.

### [ ] 3.2 — App navigation state

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/AppViewModel.kt`
```kotlin
sealed interface AppScreen { data object Loading; data class Setup(val firstRun: Boolean, val current: Session?); data object Home }
class AppViewModel(getSession: GetSession) : ViewModel() {
    val screen: StateFlow<AppScreen>   // Loading, then Setup(firstRun = true, null) or Home
    fun openSettings()                 // Home → Setup(firstRun = false, the stored session)
    fun connected()                    // → Home
    fun back(): Boolean                // Setup(firstRun = false) → Home, true; otherwise false (let the system finish)
}
```
`openSettings` reloads the session through `GetSession`. The ViewModel has a `companion` `factory(container)` built with `viewModelFactory { initializer { } }`.

### [ ] 3.3 — Setup ViewModel

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/setup/SetupViewModel.kt`
```kotlin
data class SetupState(val address: String = "", val apiKey: String = "", val keyVisible: Boolean = false,
                      val connecting: Boolean = false, val problem: SetupProblem? = null)
enum class SetupProblem { InvalidAddress, MissingKey, Unreachable, NotPhotoServer, KeyRejected, KeyLacksPermission }
class SetupViewModel(private val connect: ConnectToServer, current: Session?) : ViewModel() {
    val state: StateFlow<SetupState>   // prefilled from current (address url, key value)
    val connected: Flow<Unit>          // one-shot (Channel), fired on Connected
    fun onAddressChange(v: String); fun onApiKeyChange(v: String); fun toggleKeyVisible(); fun connect()
}
```
- Editing a field clears `problem`.
- `connect()` is ignored while `connecting` is true. It maps each failed `ConnectResult` to its `SetupProblem`.

### [ ] 3.4 — Shared components

Files:
- `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/components/Buttons.kt`: `PrimaryButton(text, onClick, modifier, enabled)` and `SecondaryButton(text, onClick, modifier, icon: Painter? = null)` built on tv-material `Button`.
  - Shapes are fully round. Primary uses the `fg` fill with `on-fg` text; Secondary uses the `raised` fill with `fg` text.
  - The focus border is 4dp amber, 4dp off the edge (`ButtonDefaults.border(focusedBorder = Border(BorderStroke(4.dp, Focus), inset = (-4).dp, shape))`).
- `.../ui/components/HouseTextField.kt`: `HouseTextField(value, onValueChange, label, modifier, keyboardOptions, keyboardActions, visualTransformation)`.
  - Built on `BasicTextField`, with a `surface` fill, 18dp corners, a 22sp `fg` value and an 18sp `mfg` label.
  - It shows the amber 4dp outline when focused (track with `onFocusChanged`).
  - Centre (OK) on a focused field opens the IME (`LocalSoftwareKeyboardController`).
- `app/src/main/res/drawable/ic_settings.xml`: a gear vector, 24dp, tinted at the call site with the theme colour.

Follow `design-recipe`.

### [ ] 3.5 — The setup screen

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/setup/SetupScreen.kt`
- Shape: a centred `surface` panel (18dp corners, max width 960dp) inside the safe area (48dp top, 96dp sides).
- Contents, top to bottom:
  - a title, "Connect to Immich" (32sp);
  - a 22sp `mfg` hint: "Server address and an API key from Immich → Account Settings → API Keys";
  - the address field (`KeyboardType.Uri`, `ImeAction.Next`);
  - the key field (`KeyboardType.Password`, `ImeAction.Done` → connect, masked unless `keyVisible`), with a "Show"/"Hide" secondary button beside it;
  - the Connect primary button (its text is "Connecting…" while connecting, and it is disabled then);
  - the problem message under it, in `fg` (22sp).
- Initial focus goes to the address field when it is empty, otherwise to Connect.
- `BackHandler(enabled = !firstRun)` calls `onBack`.
- The messages live in `res/values/strings.xml`:
  - Unreachable: "Can't reach the server. Check the address, and that the TV is on your home network or VPN."
  - NotPhotoServer: "That address answered, but not as an Immich server (v1.118 or newer)."
  - KeyRejected: "The server refused this API key. Check it in Immich → Account Settings → API Keys."
  - KeyLacksPermission: "This API key can't read your account. Give it the user.read permission, or all permissions."
  - InvalidAddress: "Enter the server address, like http://192.168.1.10:2283."
  - MissingKey: "Enter an API key."
- `@Preview(device = Devices.TV_1080p)` for empty, filled, connecting and each problem.

### [ ] 3.6 — Home gets the Settings entry; MainActivity routes

Files: `ui/home/HomeScreen.kt`, `MainActivity.kt`
- `HomeScreen(onOpenSettings: () -> Unit)`: keep the title. Add a `SecondaryButton("Settings", icon = gear)` at the top-right, in the top menu's slot (48dp top, 96dp right), and give it the initial focus.
- `MainActivity` gets `AppViewModel` (from its factory) and renders by `AppScreen`:
  - Loading → an empty `bg` box;
  - Setup → `SetupScreen` with a `SetupViewModel` keyed on the screen instance;
  - Home → `HomeScreen`.
- `BackHandler` calls `appViewModel.back()`, and the activity finishes when it returns false.

### [ ] 3.7 — The manual

File: `docs/design/README.md`:
- § Screens: add a row, `Setup | a centred panel: title, hint, server address and API key fields (Show beside the key), Connect, the problem under it`.
- After the top-menu line, add: "Settings is a gear button at the right end, beside the clock; until the top menu exists, Home shows it top-right."
- § Open decisions: keep the QR line (still open).

### [ ] 3.8 — Tests

Files: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/AppViewModelTest.kt`, `.../ui/setup/SetupViewModelTest.kt`.

Use `Dispatchers.setMain(StandardTestDispatcher())` in `@Before`/`@After`, with fake `ServerConnector`/`SessionStore` behind the real use cases. Cover the cases in Test plan → Unit.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerifyDebug lintDebug assembleDebug`
- Everything green, coverage ≥ 80%, `ArchitectureTest` passes with `AppContainer` naming `data/`.

---

## Out of scope

- Setting it up from a phone with a QR code (an open decision in the design manual).
- Immich servers older than v1.118 (the `/api/server-info/*` paths).
- The real top menu, the clock and anything on Home beyond the Settings button.
- An auth interceptor for later calls and image loading headers (the first ticket that loads albums or images adds it, reading the `SessionStore`).
- Checking the session again at launch, signing out, or more than one server.
- Calling `/api/server/version` or gating features by version.
