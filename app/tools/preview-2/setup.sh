#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
#
# preview-2 setup (PLA-144): installs the testOnly APK, makes Plainphone Device Owner, sets the
# fixed starter list (Phone, Messages, Camera, Maps; the emergency floor is added by the app),
# re-applies it through the boot runtime, and prints what reads back. The phone must have no accounts for set-device-owner.
#
# Usage: setup.sh <adb-serial> <plainphone-debug.apk>
# Way out: adb -s <serial> shell dpm remove-active-admin org.plainphone/.PlainphoneAdminReceiver
set -euo pipefail
S=${1:?serial}; APK=${2:?apk}
a() { adb -s "$S" "$@"; }
sh_() { a shell "$@" | tr -d '\r'; }
ADMIN=org.plainphone/.PlainphoneAdminReceiver

a install -r -t "$APK"
if ! sh_ dpm list-owners | grep -q org.plainphone; then
  sh_ dpm set-device-owner "$ADMIN"
fi

dialer=$(sh_ cmd role get-role-holders android.app.role.DIALER)
sms=$(sh_ cmd role get-role-holders android.app.role.SMS)
camera=$(sh_ cmd package resolve-activity --brief -a android.media.action.STILL_IMAGE_CAMERA | tail -1 | cut -d/ -f1)
if [[ -z "$camera" || "$camera" == android || "$camera" == *"No activity"* ]]; then
  camera=${PREVIEW2_CAMERA:?camera did not resolve to one app; set PREVIEW2_CAMERA}
fi
maps=com.google.android.apps.maps
starter="$dialer,$sms,$camera,$maps"
echo "starter list: $starter"

a logcat -c
sh_ am broadcast -a org.plainphone.preview.SET_STARTER -n org.plainphone/.preview.SetupCommandReceiver \
  --esa packages "$starter"
for _ in $(seq 1 30); do
  line=$(sh_ logcat -d -s PlainphoneBoot | grep 'reapply reason=setup' | tail -1 || true)
  [[ -n "$line" ]] && break
  sleep 1
done
echo "apply: ${line:-<no re-apply line after 30 s>}"
sh_ logcat -d -s Plainphone | grep 'PREVIEW2 PLAN' | tail -1 || true

sh_ am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
sleep 3
echo "--- read back"
sh_ dpm list-owners
echo "home: $(sh_ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
echo "lock task state: $(sh_ dumpsys activity activities | grep -m1 -o 'mLockTaskModeState=[A-Z_]*')"
sh_ dumpsys device_policy | grep -A3 -i 'lockTaskPackages\|mLockTaskPackages' | head -8 || true
comm -23 <(sh_ pm list packages -u | sort) <(sh_ pm list packages | sort) | sed 's/^package:/hidden or uninstalled: /'
echo "testOnly: $(sh_ dumpsys package org.plainphone | grep -m1 -o 'TEST_ONLY' || echo missing)"
