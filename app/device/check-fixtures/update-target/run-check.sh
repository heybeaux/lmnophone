#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
#
# Runs UpdateInstallerCheck (PLA-167) on an emulator you own, with no Device Owner and no accounts.
# Build first: ./build.sh and (cd ../../.. && ./gradlew :device:assembleDebugAndroidTest).
# Usage: ./run-check.sh <emulator-serial> [port]   (port and port+1 on the host's loopback)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$here/out"
A="adb -s ${1:?serial}"
port="${2:-8765}"; redirect=$((port + 1))
T=org.plainphone.device.test
apk="$here/../../build/outputs/apk/androidTest/debug/device-debug-androidTest.apk"
bt="${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools/${BUILD_TOOLS:-36.0.0}"
digest() { "$bt/apksigner" verify --print-certs "$1" | sed -n 's/.*SHA-256 digest: //p' | head -1; }
key=$(digest "$out/v1.apk"); keyb=$(digest "$out/v6-keyb.apk"); sha2=$(shasum -a 256 "$out/v2.apk" | cut -d' ' -f1)
inst() { $A shell am instrument -w -e phase "$1" -e key "$key" -e keyb "$keyb" -e sha2 "$sha2" -e port "$port" \
  -e redirect "$redirect" $T/org.plainphone.device.storage.DirectBootJournalCheck; }

echo "# PLA-167 Update installer check, $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "# AVD $($A emu avd name | head -1 | tr -d '\r'), API $($A shell getprop ro.build.version.sdk), $($A shell getprop ro.build.fingerprint)"
echo "# key A $key; key B $keyb; v2 sha256 $sha2"
python3 -I "$here/serve.py" "$out" "$port" "$redirect" > "$out/serve.log" 2>&1 &
server=$!
trap 'kill $server 2>/dev/null; $A shell dpm remove-active-admin $T/org.plainphone.device.apply.CheckAdmin >/dev/null 2>&1 || true' EXIT
sleep 1
$A install -r -t "$apk" >/dev/null
$A uninstall org.plainphone.updatetarget >/dev/null 2>&1 || true
$A install "$out/v1.apk" >/dev/null
$A shell dpm set-device-owner $T/org.plainphone.device.apply.CheckAdmin
echo; echo "== phase update"; inst update
echo; echo "== phase pending1 (the process kills itself right after commit)"; $A logcat -c; inst pending1 || true
sleep 0.2; $A logcat -d -s PLA167:I | grep PLA167 || true
echo; echo "== phase pending2 (new process)"; inst pending2
echo; echo "== host requests"; cat "$out/serve.log"
echo; echo "== cleanup"
$A shell dpm remove-active-admin $T/org.plainphone.device.apply.CheckAdmin
$A uninstall org.plainphone.updatetarget
$A shell dpm list-owners
