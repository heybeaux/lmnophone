#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# PLA-524 spare helpers (copied from preview-47) (sole driver only). PINs come from
# the environment (synthetic, emulator only) and are never printed.
: "${S:?spare serial from the environment}"; [[ ${PLAINPHONE_SPARE_DRIVER:-} == 1 ]] || { echo "spare requires sole-driver opt-in"; exit 2; }
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
snapshot(){ local p; p=$(a shell dumpsys package org.plainphone)
  echo "taken=$(date -u +%FT%TZ) ($1)"
  echo "version=$(echo "$p" | grep -m1 -o 'versionName=[^ ]*')"; echo "versionCode=$(echo "$p" | grep -m1 -o 'versionCode=[0-9]*')"
  echo "lastUpdate=$(echo "$p" | grep -m1 -o 'lastUpdateTime=.*' | tr -d ' ')"
  echo "saved_networks_sha=$(a shell cmd wifi list-networks | shasum -a 256 | cut -c1-16)"
  echo "bonded=$(a shell dumpsys bluetooth_manager 2>/dev/null | sed -n '/Bonded devices:/,/^$/p' | grep -cE '([0-9A-F]{2}:){5}')"
  echo "location=$(a shell settings get secure location_mode)"; echo "font=$(a shell settings get system font_scale)"; echo "ring=$(a shell settings get system volume_ring)"
  echo "mic=$(echo "$p" | grep -m1 'android.permission.RECORD_AUDIO: granted')"
  echo "owner=$(a shell dumpsys device_policy | grep -m1 'admin=ComponentInfo')"
  echo "locktask=$(a shell dumpsys activity activities | grep -m1 -o 'mLockTaskModeState=[A-Z]*')"
  echo "sup=$(a shell dumpsys device_policy | grep -m1 'System Update Policy')"
  echo "talkback=$(a shell settings get secure enabled_accessibility_services) / $(a shell settings get secure accessibility_enabled)"
  echo "calls_dumpsys=$(a shell dumpsys telecom | grep -c 'Call id=')"
  echo "wifi_location_marker=$(a shell run-as org.plainphone cat /data/user_de/0/org.plainphone/shared_prefs/wifi_location.xml 2>/dev/null | grep -o 'name="owned" value="[a-z]*"')"; }
