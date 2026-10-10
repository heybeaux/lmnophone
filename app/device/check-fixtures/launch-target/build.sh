#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
#
# PLA-219: builds launch-target.apk into OUT (default ./out) with a throwaway key made there, and prints
# its signer digest (pass it as -e expect). Keys never leave OUT; do not commit them.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="${1:-$here/out}"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
bt="$sdk/build-tools/${BUILD_TOOLS:-36.0.0}"
jar="$sdk/platforms/android-${PLATFORM:-35}/android.jar"
mkdir -p "$out"
cd "$out"
[ -f key.jks ] || keytool -genkeypair -keystore key.jks -storepass checkpass -keypass checkpass \
  -alias k -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Plainphone launch check key" >/dev/null 2>&1
"$bt/aapt2" link -o unsigned.apk -I "$jar" --manifest "$here/AndroidManifest.xml" --version-code 1 --version-name 1
"$bt/zipalign" -f -p 4 unsigned.apk aligned.apk
"$bt/apksigner" sign --out launch-target.apk --ks key.jks --ks-pass pass:checkpass --ks-key-alias k aligned.apk
"$bt/apksigner" verify --print-certs launch-target.apk | sed -n 's/.*SHA-256 digest: //p' | head -1
