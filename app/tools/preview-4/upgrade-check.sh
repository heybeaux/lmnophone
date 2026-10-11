#!/usr/bin/env bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
#
# preview-4 in-place upgrade check (PLA-306). Installs over the current build with `install -r`
# (never uninstalls), reads back version, Device Owner and lock task, restarts once and checks the
# full re-apply reads back before Home is drawn, then More > Emergency and Phone with 9-1-1 typed.
# No call key is sent and Call is never tapped. Package names in re-apply reasons are redacted.
#
# Usage: upgrade-check.sh <adb-serial> <plainphone-debug.apk> <out-dir> [label]
# SKIP_INSTALL=1 reads back and restarts without installing (after an earlier install run).
set -uo pipefail  # no -e: a flaky UI dump must not end the check early
S=${1:?serial}; APK=${2:?apk}; OUT=${3:?out dir}; LABEL=${4:-$S}
# This preparation tool is emulator-only; never address a physical device.
case "$S" in
  emulator-5590|emulator-5604|emulator-5742) echo "Reserved emulator" >&2; exit 2 ;;
  emulator-[0-9][0-9][0-9][0-9]) ;;
  *) echo "An owned emulator serial is required" >&2; exit 2 ;;
esac
. "$(dirname "$0")/../preview-2/ui.sh"
mkdir -p "$OUT"
redact() { sed -E 's/reason=package:([A-Z_]+):[^ ]+/reason=package:\1:<pkg>/'; }
texts() { dump | grep -o 'text="[^"]*"' | grep -v 'text=""' | tr '\n' ' '; echo; }
ver() { sh_ dumpsys package org.plainphone | grep -m2 -E 'versionCode|versionName' | sed 's/^ *//' | tr '\n' ' '; echo; }
wait_boot() { for _ in $(seq 1 120); do [[ $(sh_ getprop sys.boot_completed) == 1 ]] && return; sleep 2; done; echo "no boot" >&2; exit 1; }
# tap_match <python regex>: taps the first node whose text, content-desc or resource-id matches.
tap_match() {
  local b
  b=$(dump | python3 -c '
import sys,re,xml.etree.ElementTree as ET
want=re.compile(sys.argv[1])
for n in ET.fromstring(sys.stdin.read()).iter("node"):
    # Never a call control: skip anything whose id, label or text names a call or "dial".
    if re.search(r"call|^dial$|^Call$", " ".join(n.get(k) or "" for k in ("text","content-desc","resource-id")), re.I):
        continue
    if any(want.search(n.get(k) or "") for k in ("text","content-desc","resource-id")):
        x1,y1,x2,y2=map(int,re.findall(r"\d+",n.get("bounds"))); print((x1+x2)//2,(y1+y2)//2); break
' "$1")
  [[ -n "$b" ]] || { echo "tap_match: '$1' not on screen" >&2; return 1; }
  sh_ input tap $b
}
# Opens the dial pad if it is closed, then taps 9 1 1 by key id. The Call button is never tapped.
digits() {
  dump | grep -q 'dialer:id/nine' || { tap_match 'id/dialpad_fab$|^[Kk]ey ?pad$|^[Dd]ial ?pad$'; sleep 2; }
  for _ in $(seq 1 12); do [[ "$(dialtext)" =~ [0-9] ]] || break; tap_match 'id/deleteButton$'; done
  tap_match 'dialer:id/nine$'; tap_match 'dialer:id/one$'; tap_match 'dialer:id/one$'; sleep 1
}
dialtext() { dump | grep -o 'text="[^"]*"[^>]*dialer:id/digits' | cut -d'"' -f2; }
more_center() { dump | python3 -c '
import sys,re,xml.etree.ElementTree as ET
r=ET.fromstring(sys.stdin.read()); W=None
for n in r.iter("node"):
    b=list(map(int,re.findall(r"\d+",n.get("bounds")))); W=max(W or 0,b[2])
for n in r.iter("node"):
    if n.get("text")=="More":
        b=list(map(int,re.findall(r"\d+",n.get("bounds")))); c=(b[0]+b[2])/2
        print(f"More text bounds={b} centre_x={c:.0f} screen_width={W} offset_px={c-W/2:+.0f}"); break
'; }

{
echo "# preview-4 upgrade check on $LABEL, $(date -u +%FT%TZ). No call key, no Call tap."
echo "## Before"
echo "version: $(ver)"; sh_ dpm list-owners | tail -1; locktask
echo "home shows: $(texts)"
shot "$OUT/0-before-home.png"

if [[ "${SKIP_INSTALL:-}" != 1 ]]; then
echo "## Install in place (install -r, no uninstall)"
echo "apk sha256: $(shasum -a 256 "$APK" | cut -d' ' -f1)"
echo "install started: $(date -u +%FT%TZ)"
a install -r -t "$APK"
echo "install finished: $(date -u +%FT%TZ)"
sleep 4
fi
echo "## Read back after install"
echo "version: $(ver)"; sh_ dumpsys package org.plainphone | grep -m1 -o 'TEST_ONLY'
sh_ dpm list-owners | tail -1; locktask; top
sh_ am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep 3
echo "home shows: $(texts)"
more_center
shot "$OUT/1-after-install-home.png"

echo "## One restart"
a reboot; sleep 5; a wait-for-device; wait_boot
echo "boot completed: $(date -u +%FT%TZ)"
sh_ input keyevent KEYCODE_WAKEUP; sleep 1; sh_ wm dismiss-keyguard; sleep 1; sh_ input swipe 540 1800 540 600 200 || true
for _ in $(seq 1 60); do sh_ logcat -d -s PlainphoneBoot | grep -q 'home shown' && break; sleep 1; done
sleep 3
sh_ logcat -d -s PlainphoneBoot | grep -E 'reapply|home shown' | redact | cut -c1-260 | head -30
log=$(sh_ logcat -d -s PlainphoneBoot)
full=$(grep -m1 -E 'reapply .*locked=false ready=true applied=\[[^]]*HOME' <<<"$log" | cut -c1-18 || true)
home=$(grep -m1 'home shown' <<<"$log" | cut -c1-18 || true)
echo "first full read-back: ${full:-none}; first home shown: ${home:-none}"
[[ -n "$full" && -n "$home" && ! "$home" < "$full" ]] && echo "ORDER OK: Home drawn after the full read-back" || echo "ORDER NOT SHOWN"
echo "version: $(ver)"; sh_ dpm list-owners | tail -1; locktask; top
echo "home shows: $(texts)"
more_center
shot "$OUT/2-after-restart-home.png"

echo "## Check 1a: Home > More > Emergency"
tap_text More; sleep 2
echo "more shows: $(texts)"; top
shot "$OUT/3-more.png"
tap_match '^Emergency 9-1-1' && echo "tapped: Emergency 9-1-1"; sleep 3
top; locktask; echo "dial pad: $(dialtext)"; echo "calls: $(calls)"
shot "$OUT/4-more-emergency-911.png"
sh_ input keyevent KEYCODE_HOME; sleep 2; top

echo "## Check 1b: Home > Phone, open the dial pad, tap 9 1 1"
tap_text Phone; sleep 3; top; digits
locktask; echo "dial pad: $(dialtext)"; echo "calls: $(calls)"
shot "$OUT/5-phone-911.png"
sh_ input keyevent KEYCODE_HOME; sleep 2; top; locktask
echo "## End $(date -u +%FT%TZ)"
} 2>&1 | tee "$OUT/${LOG:-upgrade-check.txt}"
