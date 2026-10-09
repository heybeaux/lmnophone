#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-24). Read-only state + versions for the Q2 unattended run.
# Usage: bash tools/q2-state.sh   (prints to stdout; no changes to the phone)
set -uo pipefail
SERIAL="${SERIAL:-SPARE_DEVICE_SERIAL}"
case "$SERIAL" in emulator-*) echo "refusing: $SERIAL is not the Motorola" >&2; exit 2;; esac
a() { adb -s "$SERIAL" "$@"; }
PKGS="${PKGS:-com.google.android.apps.messaging com.google.android.dialer com.google.android.gm com.google.android.deskclock com.google.android.apps.photos com.google.android.calculator com.google.android.gms com.android.vending}"
echo "host_utc=$(date -u +%FT%TZ) device_time=$(a shell date +%FT%T%z)"
bash "$(dirname "$0")/pp.sh" cmd status
a shell dumpsys activity activities | grep -m1 -E 'mLockTaskModeState' | sed 's/^ *//'
bash "$(dirname "$0")/pp.sh" top
echo "boot_count=$(a shell settings get global boot_count) stay_on=$(a shell settings get global stay_on_while_plugged_in) screen_off_timeout=$(a shell settings get system screen_off_timeout)"
echo "uptime: $(a shell uptime)"
a shell dumpsys power | grep -m2 -E 'mWakefulness=|mHoldingDisplaySuspendBlocker' | sed 's/^ *//'
a shell dumpsys battery | grep -E 'AC powered|USB powered|level|status:' | sed 's/^ *//' | tr '\n' ' '; echo
a shell dumpsys wifi | grep -m1 'Wi-Fi is'
a shell dumpsys connectivity | grep -m1 -E 'Active default network' 
echo "--- versions (pkg versionName versionCode lastUpdateTime updated-system-app?)"
for p in $PKGS; do
  d=$(a shell dumpsys package "$p")
  vn=$(echo "$d" | grep -m1 'versionName=' | sed 's/.*versionName=//')
  vc=$(echo "$d" | grep -m1 'versionCode=' | sed 's/.*versionCode=\([0-9]*\).*/\1/')
  lu=$(echo "$d" | grep -m1 'lastUpdateTime=' | sed 's/.*lastUpdateTime=//')
  up=$(echo "$d" | grep -c 'UPDATED_SYSTEM_APP')
  echo "$p | $vn | $vc | $lu | updated=$up"
done
