#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# PLA-488 spare exercise. Forget is NEVER confirmed: open the question, read it, tap Keep.
# Pair page: search, Home, return; counts only (no device names or addresses are printed).
# Usage: PLAINPHONE_SPARE_DRIVER=1 S=<serial> bash spare_exercise.sh
set -u
: "${S:?serial}"; [ "${PLAINPHONE_SPARE_DRIVER:-}" = 1 ] || { echo "sole-driver opt-in required"; exit 2; }
a(){ adb -s "$S" "$@"; }
dump(){ a shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1; a shell cat /data/local/tmp/ui.xml; a shell rm -f /data/local/tmp/ui.xml; }
# Only known app strings are printed; anything else is counted.
KNOWN='^(‹ .*|More|Phone settings|Wi-Fi|Bluetooth|Connected to Wi-Fi|Not connected|Forget this network|Nearby networks|Show nearby networks|To see nearby networks.*|Forget this network\?|The Wi-Fi this phone is connected to|You will need its password to join it again\.|Keep|Forget|The network was forgotten\.|Paired with:|No paired devices\.|Pair a new device|Looking for nearby devices…|No devices found\..*|Look again|Emergency|›)$'
texts(){ dump | python3 -I -c "
import sys,re
t=re.findall(r' text=\"([^\"]+)\"', sys.stdin.read()); k=[x for x in t if re.match(sys.argv[1],x)]
print('|'.join(k)+' [other_text_nodes=%d]'%(len(t)-len(k)))" "$KNOWN"; }
tap(){ local xy; xy=$(dump | python3 -I -c "
import sys,re
for n in re.findall(r'<node [^>]*>',sys.stdin.read()):
    m=re.search(r' text=\"([^\"]*)\"',n)
    if m and m.group(1)==sys.argv[1]:
        b=list(map(int,re.findall(r'\d+',re.search(r'bounds=\"([^\"]*)\"',n).group(1)))); print((b[0]+b[2])//2,(b[1]+b[3])//2); break" "$1")
  [ -n "$xy" ] || { echo "FAIL: '$1' not found"; exit 1; }; a shell input tap $xy; }
state(){ echo "saved=$(a shell cmd wifi list-networks | shasum -a 256 | cut -c1-16) location=$(a shell settings get secure location_mode) bonded=$(a shell dumpsys bluetooth_manager | sed -n '/Bonded devices:/,/^$/p' | grep -cE '([0-9A-F]{2}:){5}') calls=$(a shell dumpsys telecom | grep -c 'Call id=')"; }
wake(){ a shell input keyevent WAKEUP; sleep 1; a shell wm dismiss-keyguard; sleep 2; }
home(){ a shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; sleep 2; }
echo "exercise start $(date -u +%FT%TZ)"; wake; home
echo "before: $(state)"
tap More; sleep 1; tap "Phone settings"; sleep 1; tap "Wi-Fi"; sleep 3
echo "== Wi-Fi page: $(texts)"
tap "Forget this network"; sleep 1; echo "== question: $(texts)"; echo "   $(state)"
tap Keep; sleep 2; echo "== after Keep: $(texts)"; echo "   $(state)"
a shell input keyevent BACK; sleep 1; a logcat -c
uid=$(a shell dumpsys package org.plainphone | grep -m1 -o 'userId=[0-9]*' | cut -d= -f2)
tap Bluetooth; sleep 1; echo "== Bluetooth page: $(texts)"
tap "Pair a new device"; sleep 2; echo "== Pair page open: $(texts)"; sleep 18; echo "== after 20s: $(texts)"
a shell input keyevent HOME; sleep 4; echo "   at Home: $(texts)"
tap More; sleep 1; tap "Phone settings"; sleep 1; tap Bluetooth; sleep 1; tap "Pair a new device"; sleep 2
echo "== Pair page reopened after Home: $(texts)"
a shell input keyevent SLEEP; sleep 4; wake; echo "== after screen off/on: $(texts)"; sleep 18; echo "== 20s later: $(texts)"
home
echo "== discovery calls from this app (uid $uid) and state changes, times only:"
a logcat -d | grep -E "(startDiscovery|cancelDiscovery): from uid/pid=$uid/|discoveryStateChangeCallback|PowerManagerService: (Going to sleep|Waking up)" | awk '{print $2, $5, $6, $7, $8}' | sed -E 's/([0-9A-F]{2}:){5}[0-9A-F]{2}/<mac>/g'
echo "final: $(state) locktask=$(a shell dumpsys activity activities | grep -m1 -o 'mLockTaskModeState=[A-Z]*')"
echo "exercise end $(date -u +%FT%TZ)"
