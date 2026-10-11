#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
#
# PLA-219: builds uid-a.apk and uid-b.apk into OUT (default ./out), both signed with one throwaway key
# made there and both declaring sharedUserId org.plainphone.uidshared. Keys never leave OUT; do not commit.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="${1:-$here/out}"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
bt="$sdk/build-tools/${BUILD_TOOLS:-36.0.0}"
jar="$sdk/platforms/android-${PLATFORM:-35}/android.jar"
mkdir -p "$out"
cd "$out"
[ -f key.jks ] || keytool -genkeypair -keystore key.jks -storepass checkpass -keypass checkpass \
  -alias k -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Plainphone uid check key" >/dev/null 2>&1
for x in a b; do
  "$bt/aapt2" link -o "unsigned-$x.apk" -I "$jar" --manifest "$here/AndroidManifest-$x.xml" --version-code 1 --version-name 1
  "$bt/zipalign" -f -p 4 "unsigned-$x.apk" "aligned-$x.apk"
  "$bt/apksigner" sign --out "uid-$x.apk" --ks key.jks --ks-pass pass:checkpass --ks-key-alias k "aligned-$x.apk"
done
