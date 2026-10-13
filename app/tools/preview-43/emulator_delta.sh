#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# PLA-491 delta on the owned read-only AVD: a connection switch ends the Forget question (P1);
# a result settled while away (Home, Phone settings) is shown on reopen, then cleared (P2). Never the spare.
# Usage: S=emulator-NNNN APK43=... bash emulator_delta.sh
set -u
: "${S:?owned emulator serial}" "${APK43:?}"
[[ $S == emulator-* ]] || { echo "refusing non-emulator target"; exit 2; }
a(){ adb -s "$S" "$@"; }
dump(){ a shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1; a shell cat /data/local/tmp/ui.xml; a shell rm -f /data/local/tmp/ui.xml; }
texts(){ dump | python3 -I -c "import sys,re; print('|'.join(re.findall(r' text=\"([^\"]+)\"', sys.stdin.read())))"; }
xy(){ dump | python3 -I -c "
import sys,re
for n in re.findall(r'<node [^>]*>',sys.stdin.read()):
    if re.search(r' text=\"([^\"]*)\"',n).group(1)==sys.argv[1]:
        b=list(map(int,re.findall(r'\d+',re.search(r'bounds=\"([^\"]*)\"',n).group(1)))); print((b[0]+b[2])//2,(b[1]+b[3])//2); break" "$1"; }
tap(){ local p; p=$(xy "$1"); [ -n "$p" ] || { echo "FAIL: '$1' not found"; exit 1; }; a shell input tap $p; }
state(){ echo "saved=$(a shell cmd wifi list-networks | shasum -a 256 | cut -c1-16) ids=$(a shell cmd wifi list-networks | awk 'NR>1{print $1}' | sort -u | tr '\n' ',') location=$(a shell settings get secure location_mode) connected=$(a shell cmd wifi status | grep -c 'is connected to')"; }
connect(){ a shell cmd wifi connect-network AndroidWifi open >/dev/null; sleep 10; }
calls(){ a shell dumpsys telecom | grep -c 'Call id=' ; }
open_wifi(){ a shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep 2; tap More; sleep 1; tap "Phone settings"; sleep 1; tap "Wi-Fi"; sleep 2; }
# Forget, then leave at once with the given key (no sleep between, in one shell call).
forget_and(){ tap "Forget this network"; sleep 1; local p; p=$(xy Forget); a shell "input tap $p; input keyevent $1"; }
echo "AVD $(a emu avd name | head -1 | tr -d '\r') API $(a shell getprop ro.build.version.sdk)"
echo "baseline: $(state) calls=$(calls) installed $(a shell dumpsys package org.plainphone | grep -m1 -o 'versionCode=[0-9]*')"
echo "install 43 (PLA-491 fix) start $(date -u +%FT%TZ)"; a install -r -t "$APK43" | tail -1; echo "install end $(date -u +%FT%TZ)"
a shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep 10
echo "on 43: $(a shell dumpsys package org.plainphone | grep -E 'versionCode|versionName|flags=\[' | head -3 | tr -s ' ' | tr '\n' ' ')"
echo "DO: $(a shell dpm list-owners | tail -1 | tr -s ' ')"; echo "$(a shell dumpsys activity activities | grep -m1 mLockTaskModeState | tr -s ' ')"
echo "== P1: the question ends when the connection is replaced (same network, new connection, name hidden with Location off)"
connect; open_wifi; echo "   before: $(state)"
tap "Forget this network"; sleep 1; echo "   question: $(texts)"
a shell cmd wifi set-wifi-enabled disabled; sleep 3; a shell cmd wifi set-wifi-enabled enabled; sleep 12
echo "   after reconnect: $(state)"; echo "   page: $(texts)"
echo "== P2a: Forget, then Home at once; reopen after the readback ends"
open_wifi; echo "   before: $(state)"; forget_and HOME; sleep 13; echo "   t+13s at Home: $(state)"
open_wifi; echo "   reopened: $(texts)"
a shell input keyevent BACK; sleep 1; tap "Wi-Fi"; sleep 2; echo "   opened again after it was shown: $(texts)"
echo "== P2b: Forget, then Back to Phone settings at once; reopen Wi-Fi after the readback ends"
connect; open_wifi; echo "   before: $(state)"; forget_and BACK; sleep 1; echo "   left to: $(texts)"; sleep 12
echo "   t+13s: $(state)"; tap "Wi-Fi"; sleep 2; echo "   reopened: $(texts)"
a shell input keyevent BACK; sleep 1; tap "Wi-Fi"; sleep 2; echo "   opened again after it was shown: $(texts)"
connect; a shell input keyevent HOME; sleep 1
echo "final: $(state) bluetooth_on=$(a shell settings get global bluetooth_on) calls=$(calls)"
