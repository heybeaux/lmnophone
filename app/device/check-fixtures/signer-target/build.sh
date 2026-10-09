#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
#
# Builds the PackageFactsCheck target twice into OUT (default ./out), with throwaway keys made there:
#   target-v1.apk  versionCode 1, signed with key A
#   target-v2.apk  versionCode 2, signed with key B, rotated from A (apksigner --lineage)
# then prints both with `apksigner verify --print-certs`. Keys never leave OUT; do not commit them.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="${1:-$here/out}"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
bt="$sdk/build-tools/${BUILD_TOOLS:-36.0.0}"
jar="$sdk/platforms/android-${PLATFORM:-35}/android.jar"
mkdir -p "$out"
cd "$out"

for k in a b; do
  [ -f "key-$k.jks" ] || keytool -genkeypair -keystore "key-$k.jks" -storepass checkpass -keypass checkpass \
    -alias "$k" -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Plainphone check key $k" >/dev/null 2>&1
done
sign_a=(--ks key-a.jks --ks-pass pass:checkpass --ks-key-alias a)
sign_b=(--ks key-b.jks --ks-pass pass:checkpass --ks-key-alias b)
[ -f lineage ] || "$bt/apksigner" rotate --out lineage --old-signer "${sign_a[@]}" --new-signer "${sign_b[@]}"

for v in 1 2; do
  "$bt/aapt2" link -o "unsigned-v$v.apk" -I "$jar" --manifest "$here/AndroidManifest.xml" \
    --version-code "$v" --version-name "$v"
  "$bt/zipalign" -f -p 4 "unsigned-v$v.apk" "aligned-v$v.apk"
done
"$bt/apksigner" sign --out target-v1.apk "${sign_a[@]}" aligned-v1.apk
"$bt/apksigner" sign --out target-v2.apk "${sign_a[@]}" --next-signer "${sign_b[@]}" --lineage lineage aligned-v2.apk

for v in 1 2; do
  echo "== target-v$v.apk"
  "$bt/apksigner" verify --print-certs "target-v$v.apk"
done
echo "== lineage"
"$bt/apksigner" lineage --in lineage --print-certs
