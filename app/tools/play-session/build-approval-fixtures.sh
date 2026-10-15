#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# Emulator fixtures only. The temporary signer has no authority outside these test APKs.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
bash "$here/build-fixture.sh"
out=${PAPERCLIP_RUN_SCRATCH_DIR:?run scratch required}/play-fixture
sdk=${ANDROID_HOME:-$HOME/Library/Android/sdk}
if [[ ! -e "$out/other-signer.jks" ]]; then
  "${JAVA_HOME:?JDK required}/bin/keytool" -genkeypair -keystore "$out/other-signer.jks" \
    -storepass testonly -keypass testonly -alias fixture -dname 'CN=Emulator Fixture' \
    -keyalg RSA -keysize 2048 -validity 2 >/dev/null 2>&1
fi
"$sdk/build-tools/36.0.0/apksigner" sign --out "$out/play-other-signer.apk" \
  --ks "$out/other-signer.jks" --ks-pass pass:testonly --ks-key-alias fixture "$out/aligned.apk"
