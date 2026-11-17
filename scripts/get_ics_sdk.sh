#!/usr/bin/env bash
# Prepare les en-tetes et bibliotheques Android 4.0.4 (AOSP) necessaires au decodeur materiel.
# - en-tetes : miroir GitHub d'AOSP, tag android-4.0.4_r2.1 (licence Apache 2.0)
# - bibliotheques : image systeme de l'emulateur android-15 (uniquement pour l'edition de liens ;
#   a l'execution, ce sont celles du Honda Connect qui sont utilisees). Rien n'est publie dans le depot.
# Usage : ANDROID_SDK=~/android-sdk ./scripts/get_ics_sdk.sh [dossier]   (defaut : ./ics-sdk)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/ics-sdk}"
: "${ANDROID_SDK:?Definissez ANDROID_SDK}"
TAG=android-4.0.4_r2.1
mkdir -p "$OUT/include" "$OUT/lib" "$OUT/src"
fetch() { # repo dossier destination
  local repo=$1 dir=$2 dst=$3
  [ -d "$OUT/src/$repo" ] || git clone -q --depth 1 --filter=blob:none --sparse --branch $TAG \
      "https://github.com/aosp-mirror/platform_$repo" "$OUT/src/$repo"
  git -C "$OUT/src/$repo" sparse-checkout set "$dir"
  rm -rf "$OUT/include/$dst"; cp -r "$OUT/src/$repo/$dir" "$OUT/include/$dst"
}
fetch frameworks_base include frameworks_base
fetch system_core include system_core
fetch hardware_libhardware include libhardware
# Corrections pour compiler en C++11 avec le NDK (identiques a celles d'OpenDroidAuto) :
# char16_t / char32_t sont des types natifs ; KeyedVector a besoin de this-> (recherche en deux phases).
sed -i 's/^typedef uint32_t char32_t;/typedef uint32_t gchar32_t;/; s/^typedef uint16_t char16_t;/\/\/ char16_t : type natif en C++11/' \
    "$OUT/include/frameworks_base/utils/Unicode.h"
sed -i 's/    ssize_t i = indexOfKey(key);/    ssize_t i = this->indexOfKey(key);/' "$OUT/include/frameworks_base/utils/KeyedVector.h"
IMG="$ANDROID_SDK/system-images/android-15/default/armeabi-v7a/system.img"
[ -f "$IMG" ] || yes | "$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_SDK" \
    "system-images;android-15;default;armeabi-v7a" >/dev/null
for l in libstagefright libutils libcutils libbinder; do
  debugfs -R "dump /lib/$l.so $OUT/lib/$l.so" "$IMG" 2>/dev/null
done
ls -la "$OUT/lib"
