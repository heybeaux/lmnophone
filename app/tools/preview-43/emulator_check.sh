#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# PLA-488 private emulator check: in-place 42 -> 43, Forget question (Keep, Back, Forget),
# leave during the check, Pair page pause/resume. Owned read-only AVD only; never the spare.
# Usage: S=emulator-NNNN APK42=... APK43=... bash emulator_check.sh
set -u
: "${S:?owned emulator serial}" "${APK42:?}" "${APK43:?}"
[[ $S == emulator-* ]] || { echo "refusing non-emulator target"; exit 2; }
a(){ adb -s "$S" "$@"; }
dump(){ a shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1; a shell cat /data/local/tmp/ui.xml; a shell rm -f /data/local/tmp/ui.xml; }
texts(){ dump | python3 -I -c "import sys,re; print('|'.join(re.findall(r' text=\"([^\"]+)\"', sys.stdin.read())))"; }
tap(){ local xy; xy=$(dump | python3 -I -c "
import sys,re
for n in re.findall(r'<node [^>]*>',sys.stdin.read()):
    if re.search(r' text=\"([^\"]*)\"',n).group(1)==sys.argv[1]:
        b=list(map(int,re.findall(r'\d+',re.search(r'bounds=\"([^\"]*)\"',n).group(1)))); print((b[0]+b[2])//2,(b[1]+b[3])//2); break" "$1")
  [ -n "$xy" ] || { echo "FAIL: '$1' not found"; exit 1; }; a shell input tap $xy; }
state(){ echo "saved=$(a shell cmd wifi list-networks | shasum -a 256 | cut -c1-16) ids=$(a shell cmd wifi list-networks | awk 'NR>1{print $1}' | sort -u | tr '\n' ',') location=$(a shell settings get secure location_mode) connected=$(a shell cmd wifi status | grep -c 'is connected to')"; }
connect(){ a shell cmd wifi connect-network AndroidWifi open >/dev/null; sleep 10; }
calls(){ a shell dumpsys telecom | grep -c 'Call id=' ; }
echo "AVD $(a emu avd name | head -1 | tr -d '\r') API $(a shell getprop ro.build.version.sdk)"
echo "baseline: $(state) bluetooth_on=$(a shell settings get global bluetooth_on) calls=$(calls)"
a install -r -t "$APK42" | tail -1; a shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep 10
echo "on 42: $(a shell dumpsys package org.plainphone | grep -m1 -o 'versionCode=[0-9]*') $(a logcat -d | grep 'PlainphoneBoot: reapply' | tail -1 | grep -o 'ready=[a-z]*')"
echo "install 43 start $(date -u +%FT%TZ)"; a install -r -t "$APK43" | tail -1; echo "install 43 end $(date -u +%FT%TZ)"
a shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep 10
echo "on 43: $(a shell dumpsys package org.plainphone | grep -E 'versionCode|versionName|flags=\[' | head -3 | tr -s ' ' | tr '\n' ' ')"
echo "DO: $(a shell dpm list-owners | tail -1 | tr -s ' ')"; echo "$(a shell dumpsys activity activities | grep -m1 mLockTaskModeState | tr -s ' ') $(a logcat -d | grep 'PlainphoneBoot: reapply' | tail -1 | grep -o 'ready=[a-z]*')"
connect
tap More; sleep 1; tap "Phone settings"; sleep 1; tap "Wi-Fi"; sleep 2
echo "== Wi-Fi page: $(texts)"; echo "   $(state)"
tap "Forget this network"; sleep 1; echo "== question: $(texts)"; echo "   $(state)"
tap Keep; sleep 2; echo "== after Keep: $(texts)"; echo "   $(state)"
tap "Forget this network"; sleep 1; a shell input keyevent BACK; sleep 2; echo "== question then Back: $(texts)"; echo "   $(state)"
tap "Forget this network"; sleep 1; tap Forget; for i in 2 4 6 8 10 12; do sleep 2; echo "   Forget t+${i}s $(state)"; done
echo "== page after Forget: $(texts)"
for d in 0 1.5; do
  connect; texts >/dev/null; echo "== leave run (screen off ${d}s after Forget); before: $(state)"
  tap "Forget this network"; sleep 1
  xy=$(dump | python3 -I -c "
import sys,re
for n in re.findall(r'<node [^>]*>',sys.stdin.read()):
    if ' text=\"Forget\"' in n:
        b=list(map(int,re.findall(r'\d+',re.search(r'bounds=\"([^\"]*)\"',n).group(1)))); print((b[0]+b[2])//2,(b[1]+b[3])//2)")
  a shell "input tap $xy; sleep $d; input keyevent SLEEP"
  echo "   screen off: $(state)"; sleep 12; echo "   t+13s screen off: $(state)"
  a shell input keyevent WAKEUP; sleep 1; a shell wm dismiss-keyguard; sleep 3
  echo "   after return: $(state)"; echo "   page: $(texts)"
done
a shell input keyevent BACK; sleep 1; a logcat -c
tap Bluetooth; sleep 1; echo "== Bluetooth page: $(texts)"
tap "Pair a new device"; sleep 2; echo "== Pair page open: $(texts)"; sleep 18; echo "== after 20s: $(texts)"
a shell input keyevent SLEEP; sleep 3; a shell input keyevent WAKEUP; sleep 1; a shell wm dismiss-keyguard; sleep 2
echo "== after screen off/on: $(texts)"; sleep 18; echo "== 20s later: $(texts)"
a shell input keyevent HOME; sleep 2
echo "== discovery sequence (this app's uid only):"
uid=$(a shell dumpsys package org.plainphone | grep -m1 -o 'userId=[0-9]*' | cut -d= -f2)
a logcat -d | grep -E "(startDiscovery|cancelDiscovery): from uid/pid=$uid/|discoveryStateChangeCallback|PowerManagerService: (Going to sleep|Waking up)" | awk '{print $2, $5, $6, $7, $8, $9}'
echo "final: $(state) bluetooth_on=$(a shell settings get global bluetooth_on) calls=$(calls)"
