#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-24). Q2 window setup on the spare test device: roll back preinstalled Google apps so Play has
# real updates waiting, then (MODE=test) apply the intended policy, or (MODE=compare) leave the phone unrestricted.
set -uo pipefail
: "${SERIAL:?Set SERIAL to the explicitly approved test device}"
case "$SERIAL" in emulator-*) echo "refusing: physical-device helper requires an approved physical device" >&2; exit 2;; esac
export SERIAL
a() { adb -s "$SERIAL" "$@"; }; pp() { bash "$(dirname "$0")/pp.sh" "$@"; }
MODE="${MODE:?MODE=test or MODE=compare}"
ROLLBACK="com.google.android.apps.messaging com.google.android.gm com.google.android.deskclock com.google.android.apps.photos com.google.android.calculator"
APPROVED="com.google.android.dialer,com.google.android.apps.messaging,com.google.android.gm,com.android.phone,com.google.android.apps.safetyhub,org.telegram.messenger,com.Slack,com.spotify.music,com.microsoft.office.outlook"
echo "# q2-setup MODE=$MODE $(date -u +%FT%TZ)"
for p in $ROLLBACK; do echo "uninstall-system-updates $p -> $(a shell pm uninstall-system-updates $p 2>&1)"; done
if [ "$MODE" = test ]; then
  pp cmd "lt-packages $APPROVED"
  pp cmd "lt-features SYSTEM_INFO|NOTIFICATIONS|HOME|GLOBAL_ACTIONS|BLOCK"
  pp cmd home-set
  pp cmd lt-start; sleep 3
else
  pp cmd lt-stop; sleep 2; pp cmd home-clear
  pp cmd "lt-packages "
  a shell input keyevent 3; sleep 2
fi
a shell settings put global stay_on_while_plugged_in 0
echo "stay_on=$(a shell settings get global stay_on_while_plugged_in)"
