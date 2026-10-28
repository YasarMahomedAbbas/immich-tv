# Immich TV

An Android TV app for a Chromecast with Google TV that shows your photos from a self-hosted [Immich](https://immich.app/) server.

- **Albums**: pick one and start a slideshow.
- **Slideshow**: crossfades with a slow zoom, portrait photos on a blurred copy of themselves, the date and place in a corner, and the remote's left/right/OK to skip and pause.
- **Photos**: the whole timeline by month; start a slideshow from any photo.
- **Search**: Immich's smart search ("dog on the beach"), by voice from the remote.

The app talks only to your own server. It's built for a server reached over the home network or a VPN such as Tailscale, so plain `http://` addresses are allowed.

## Install

1. On the TV, install [Obtainium](https://github.com/ImranR98/Obtainium) and add this repository's URL. It installs the latest release and keeps it updated.
2. Or from a computer on the same network, with developer options and network debugging on: download `immich-tv-<version>.apk` from the [latest release](../../releases/latest) and run `adb connect <tv-ip>:5555 && adb install immich-tv-<version>.apk`.
3. Open Immich TV and enter the server address and an API key (Immich web → Account Settings → API Keys).

## Architecture

Three layers, each depending only on the one below it:

```
ui/       Compose for TV screens + ViewModels (state as StateFlow)
  ↓
domain/   Models, repository interfaces, use cases. Plain Kotlin, no Android.
  ↑
data/     The Immich REST client and stored settings
```

`data` implements the interfaces `domain` defines, so the use cases never see HTTP or Android. `AppContainer` wires the layers together by hand, without a DI framework.

Any change to an Immich call follows the API of the server's version (see `CLAUDE.md`).

## Development

There's no toolchain to install: the JDK, Android SDK and Gradle live in a Docker image (`docker/Dockerfile`). Only `adb` is needed on the host, to reach the TV.

```sh
./build.sh                                     # debug APK in app/build/outputs/apk/debug/
./build.sh ./gradlew testDebugUnitTest         # unit tests
./build.sh ./gradlew koverHtmlReportDebug      # coverage report in app/build/reports/kover/
TV_ADB=<tv-ip>:5555 ./build.sh install         # build, install on the TV and open it
```

To reach the Chromecast over the network: Settings → System → About → click **Android TV OS build** seven times, then Settings → System → Developer options → turn on **USB debugging**. The first `adb connect` shows a prompt on the TV; allow it and tick "Always allow".

Tests cover the domain and data layers and the ViewModels, with the HTTP client tested against MockWebServer. CI fails when line coverage drops below 80%. Compose screens need a device and aren't counted.

## CI/CD

- **CI** (`.github/workflows/ci.yml`) runs on every push and PR: unit tests, the coverage check, lint and a debug build. The job summary shows the coverage numbers.
- **Release** (`.github/workflows/release.yml`) runs when a `vX.Y.Z` tag is pushed. It tests, builds a signed APK and publishes it as a GitHub Release. The version code is derived from the tag (`v1.2.3` → 10203).

  ```sh
  git tag v0.2.0 && git push origin v0.2.0
  ```

- Signing uses the `RELEASE_KEYSTORE_B64` and `RELEASE_KEYSTORE_PASSWORD` repository secrets. Android only installs an update when it's signed with the same key, so **keep a backup of the keystore**.
- Dependabot opens weekly PRs for Gradle dependencies and monthly ones for Actions.
