#!/usr/bin/env bash
# Run a command in the build container, as the host user so output files stay ours.
#   ./build.sh                      -> ./gradlew assembleDebug
#   ./build.sh ./gradlew test       -> any other command
#   ./build.sh install              -> build, then adb install and open it on the TV
# The TV's adb address comes from TV_ADB, else from ~/.config/immich-tv/verify.env.
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=immich-tv-build
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/immich-tv"
mkdir -p "$CACHE/gradle" "$CACHE/android"

docker build -q -t "$IMAGE" docker > /dev/null

install=false
if [[ "${1:-}" == install ]]; then install=true; shift; fi
[[ $# -eq 0 ]] && set -- ./gradlew assembleDebug

docker run --rm -t \
  --user "$(id -u):$(id -g)" \
  -e HOME=/tmp \
  -e GRADLE_USER_HOME=/cache/gradle \
  -e ANDROID_USER_HOME=/cache/android \
  -v "$PWD:/src" \
  -v "$CACHE:/cache" \
  "$IMAGE" "$@"

if $install; then
  env_file="$HOME/.config/immich-tv/verify.env"
  if [[ -z "${TV_ADB:-}" && -f "$env_file" ]]; then
    TV_ADB=$(sed -n 's/^TV_ADB=//p' "$env_file")
  fi
  : "${TV_ADB:?set TV_ADB=<tv-ip>:5555 or add it to $env_file}"
  adb connect "$TV_ADB" > /dev/null
  adb -s "$TV_ADB" install -r app/build/outputs/apk/debug/app-debug.apk
  adb -s "$TV_ADB" shell am start -n io.github.yasarmahomedabbas.immichtv/.MainActivity > /dev/null
fi
