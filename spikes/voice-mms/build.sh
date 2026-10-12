#!/usr/bin/env bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# THROWAWAY (PLA-458): builds probe.apk and seeder.apk with javac/d8/aapt2, no Gradle.
set -euo pipefail
cd "$(dirname "$0")"
sdk=${ANDROID_HOME:?Set ANDROID_HOME}
bt="$sdk/build-tools/35.0.0"
jar="$sdk/platforms/android-35/android.jar"
ks=${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}
out=build
rm -rf "$out"; mkdir -p "$out"
for app in probe seeder; do
  w="$out/$app"; mkdir -p "$w/classes" "$w/dex" "$w/res"
  javac -nowarn -source 8 -target 8 -cp "$jar" -d "$w/classes" common/*.java $app/*.java 2>&1 | grep -v 'warning: \[options\]' || true
  (cd "$w/classes" && jar cf ../classes.jar .)
  "$bt/d8" --lib "$jar" --min-api 30 --output "$w/dex" "$w/classes.jar"
  resargs=()
  if [ -d "$app/res" ]; then "$bt/aapt2" compile --dir "$app/res" -o "$w/res.zip"; resargs=("$w/res.zip"); fi
  "$bt/aapt2" link --manifest "$app/AndroidManifest.xml" -I "$jar" -o "$w/unsigned.apk" ${resargs[@]+"${resargs[@]}"}
  (cd "$w/dex" && zip -q -j ../unsigned.apk classes.dex)
  "$bt/zipalign" -f 4 "$w/unsigned.apk" "$w/aligned.apk"
  "$bt/apksigner" sign --ks "$ks" --ks-pass pass:android --out "$out/$app.apk" "$w/aligned.apk"
done
ls -l "$out"/*.apk
