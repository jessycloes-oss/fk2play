#!/usr/bin/env bash
# Compile libfk2omx.so (decodeur video materiel) avec ndk-build.
# Usage : NDK=/chemin/android-ndk-r17c ICS_SDK=./ics-sdk ./scripts/build_omx.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${NDK:?Definissez NDK=/chemin/vers/android-ndk-r17c}"
ICS_SDK="$(cd "${ICS_SDK:-$ROOT/ics-sdk}" && pwd)"
"$NDK/ndk-build" -C "$ROOT/app/src/main" NDK_PROJECT_PATH="$ROOT/app/src/main" \
  APP_BUILD_SCRIPT="$ROOT/app/src/main/jni-omx/Android.mk" \
  NDK_APPLICATION_MK="$ROOT/app/src/main/jni-omx/Application.mk" \
  NDK_OUT="$ROOT/app/src/main/obj-omx" ICS_SDK="$ICS_SDK" -j"$(nproc)"
ls -la "$ROOT/app/src/main/libs/armeabi-v7a/"
