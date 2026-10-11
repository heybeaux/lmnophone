#!/usr/bin/env bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
sdk=${ANDROID_HOME:?Set ANDROID_HOME}
mkdir -p classes dex
javac -source 8 -target 8 -cp "$sdk/platforms/android-36/android.jar" -d classes SuspendSpike.java
jar cf classes.jar -C classes .
"$sdk/build-tools/36.0.0/d8" --lib "$sdk/platforms/android-36/android.jar" --min-api 30 --output dex classes.jar
"$sdk/build-tools/36.0.0/aapt2" link --manifest AndroidManifest.xml -I "$sdk/platforms/android-36/android.jar" -o probe.apk
zip -j probe.apk dex/classes.dex
"$sdk/build-tools/36.0.0/apksigner" sign --ks "${REVIEW_DEBUG_KEYSTORE:?Use the target debug signing key}" --ks-pass pass:android probe.apk
