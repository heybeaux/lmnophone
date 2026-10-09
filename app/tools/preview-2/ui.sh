#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
# Small adb UI helpers for the preview-2 checks. Source under bash: S=<serial>; . ui.sh
a() { adb -s "$S" "$@"; }
sh_() { a shell "$@" | tr -d '\r'; }
dump() { a shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; a shell cat /sdcard/ui.xml; }
# tap_text "<exact text or content-desc>": taps the centre of the first matching node.
tap_text() {
  local b
  b=$(dump | python3 -c '
import sys,re,xml.etree.ElementTree as ET
want=sys.argv[1]; root=ET.fromstring(sys.stdin.read())
for n in root.iter("node"):
    if n.get("text")==want or n.get("content-desc")==want:
        x1,y1,x2,y2=map(int,re.findall(r"\d+",n.get("bounds"))); print((x1+x2)//2,(y1+y2)//2); break
' "$1")
  [[ -n "$b" ]] || { echo "tap_text: '$1' not on screen" >&2; return 1; }
  sh_ input tap $b
}
shot() { a exec-out screencap -p > "$1"; }
top() { sh_ dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | sed 's/^ *//'; }
locktask() { sh_ dumpsys activity activities | grep -m1 -o 'mLockTaskModeState=[A-Z_]*'; }
calls() { sh_ dumpsys telecom | grep -m1 -A2 'mCalls' | tr '\n' ' '; }
