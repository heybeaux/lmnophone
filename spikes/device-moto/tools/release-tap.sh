#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-5). Taps the in-app RELEASE button twice within its 3 s confirm window.
# A uiautomator dump between taps takes >3 s, so find the button once and tap the same point twice.
set -euo pipefail
: "${SERIAL:?Set SERIAL to the explicitly approved test device}"
case "$SERIAL" in emulator-*) echo "refusing: physical-device helper requires an approved physical device" >&2; exit 2;; esac
export SERIAL
a() { adb -s "$SERIAL" "$@"; }
# The first tap swaps the status text for a short "Tap RELEASE again" line, which can move the button.
# So: tap once to arm, let the layout settle (and the 3 s window lapse), find the button again, then
# double-tap at the settled position (re-arming keeps the same short text, so it no longer moves).
find_btn() {
  a shell uiautomator dump /sdcard/pp-ui.xml >/dev/null
  a shell cat /sdcard/pp-ui.xml | tr '>' '\n' | grep -E 'text="RELEASE' | head -1 \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' || true
}
center() { set -- $1; echo "$(( ($1+$3)/2 )) $(( ($2+$4)/2 ))"; }
b=$(find_btn); [ -z "$b" ] && { echo "RELEASE button not on screen"; exit 1; }
read -r x y <<<"$(center "$b")"; a shell input tap "$x" "$y"; sleep 3.5
b=$(find_btn); [ -z "$b" ] && { echo "RELEASE button gone after arming tap"; exit 1; }
read -r x y <<<"$(center "$b")"
a shell input tap "$x" "$y"; sleep 0.5; a shell input tap "$x" "$y"
echo "tapped RELEASE twice at $x,$y (after settling)"
