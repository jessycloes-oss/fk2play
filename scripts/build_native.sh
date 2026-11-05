#!/usr/bin/env bash
# Compile libfk2video.so (wrapper JNI) avec ndk-build.
# Usage : NDK=/chemin/android-ndk-r17c ./scripts/build_native.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${NDK:?Definissez NDK=/chemin/vers/android-ndk-r17c}"
[ -f "$ROOT/ffmpeg-android/armeabi-v7a/lib/libavcodec.a" ] || "$ROOT/scripts/build_ffmpeg.sh"
"$NDK/ndk-build" -C "$ROOT/app/src/main" NDK_PROJECT_PATH="$ROOT/app/src/main" \
  APP_BUILD_SCRIPT="$ROOT/app/src/main/jni/Android.mk" \
  NDK_APPLICATION_MK="$ROOT/app/src/main/jni/Application.mk" -j"$(nproc)"
ls -la "$ROOT/app/src/main/libs/armeabi-v7a/"
