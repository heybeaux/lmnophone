#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# PLA-494 private emulator helpers (owned read-only AVD only; never the spare). PINs come from
# the environment (synthetic, emulator only) and are never printed.
: "${S:?owned emulator serial}"; [[ $S == emulator-* ]] || { echo "refusing non-emulator target"; exit 2; }
a(){ adb -s "$S" "$@"; }
dump(){ a shell "uiautomator dump /data/local/tmp/u.xml >/dev/null 2>&1; cat /data/local/tmp/u.xml; rm -f /data/local/tmp/u.xml"; }
texts(){ dump | python3 -I -c "import sys,re; print('|'.join(re.findall(r' text=\"([^\"]+)\"', sys.stdin.read())))" | sed 's/•*//g'; }
pos(){ dump | python3 -I -c "
import sys,re
k=sys.argv[1]; nodes=re.findall(r'<node [^>]*>',sys.stdin.read())
if k.startswith('#'):
    nodes=[n for n in nodes if 'EditText' in n]; i=int(k[1:]); nodes=nodes[i:i+1]
else:
    nodes=[n for n in nodes if re.search(r' text=\"([^\"]*)\"',n).group(1)==k]
for n in nodes[:1]:
    b=list(map(int,re.findall(r'\d+',re.search(r'bounds=\"([^\"]*)\"',n).group(1)))); print((b[0]+b[2])//2,(b[1]+b[3])//2)" "$1"; }
tap(){ local p; p=$(pos "$1"); [ -n "$p" ] || { echo "FAIL: '$1' not found"; return 1; }; a shell input tap $p; sleep 1.5; }
type_in(){ tap "#$1" && a shell input text "$2" && sleep 0.5; }
home(){ a shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; sleep 2; }
controls(){ home; tap More; tap "For Sam and Jo"; sleep 1; }
enter(){ type_in 0 "$1"; tap Continue; sleep 2; }
state(){ echo "version=$(a shell dumpsys package org.plainphone | grep -m1 -o 'versionCode=[0-9]*') $(a shell dumpsys activity activities | grep -m1 -o 'mLockTaskModeState=[A-Z]*') DO=$(a shell dpm list-owners | grep -c DeviceOwner) calls=$(a shell dumpsys telecom | grep -c 'Call id=')"; }
# Scroll down until the text shows, then tap it.
stap(){ local i; for i in 1 2 3 4 5 6; do [ -n "$(pos "$1")" ] && { tap "$1"; return; }; a shell input swipe 540 1700 540 700 300; sleep 1; done; echo "FAIL: '$1' not found after scrolling"; return 1; }
# System Back as a person presses it: the first press only hides an open keyboard.
back(){ a shell dumpsys input_method | grep -q 'mInputShown=true' && { a shell input keyevent BACK; sleep 1; }; a shell input keyevent BACK; sleep 1.5; }
