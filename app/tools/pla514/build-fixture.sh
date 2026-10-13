#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
out=${PAPERCLIP_RUN_SCRATCH_DIR:?run scratch required}/calculator-fixture
sdk=${ANDROID_HOME:-$HOME/Library/Android/sdk}
bt=$sdk/build-tools/36.0.0
mkdir -p "$out"
"$bt/aapt2" link -o "$out/unsigned.apk" -I "$sdk/platforms/android-36/android.jar" --manifest "$here/CalculatorManifest.xml" --version-code 1 --version-name 1.0
"$bt/zipalign" -f -p 4 "$out/unsigned.apk" "$out/aligned.apk"
"$bt/apksigner" sign --out "$out/calculator.apk" --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --ks-key-alias androiddebugkey "$out/aligned.apk"
