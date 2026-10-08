#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-5). Print the state of a Settings row: its title, summary, enabled flag, and any switch's checked flag.
# Usage: tools/pref-state.sh "<title text>" [max scrolls]
# Scrolls down until the title is on screen. Read-only: never taps the row.
set -uo pipefail
SERIAL="${SERIAL:-SPARE_DEVICE_SERIAL}"
case "$SERIAL" in emulator-5590|emulator-5604) echo "refusing: $SERIAL belongs to another team" >&2; exit 2;; esac
a() { adb -s "$SERIAL" "$@"; }
title="$1"; max="${2:-12}"
for _ in $(seq 0 "$max"); do
  a shell uiautomator dump /sdcard/pp-ui.xml >/dev/null 2>&1
  xml=$(a shell cat /sdcard/pp-ui.xml)
  if echo "$xml" | grep -qF "text=\"$title\""; then
    echo "$xml" | python3 -c '
import sys, xml.etree.ElementTree as ET
title = sys.argv[1]
root = ET.fromstring(sys.stdin.read())
parent = {c: p for p in root.iter() for c in p}
for n in root.iter("node"):
    if n.get("text") == title:
        row = n
        # climb to the clickable row that holds title + summary + widget
        while row in parent and row.get("clickable") != "true":
            row = parent[row]
        texts = [x.get("text") for x in row.iter("node") if x.get("text")]
        sw = [x for x in row.iter("node") if x.get("checkable") == "true"]
        print("row=%r enabled=%s title_enabled=%s texts=%r switch_checked=%s switch_enabled=%s" % (
            title, row.get("enabled"), n.get("enabled"), texts,
            sw[0].get("checked") if sw else None, sw[0].get("enabled") if sw else None))
        break
' "$title"
    exit 0
  fi
  a shell input swipe 540 1900 540 900 300; sleep 0.8
done
echo "row=$title NOT FOUND"
exit 1
