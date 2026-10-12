#!/usr/bin/env bash
# Usage: SPARE_SERIAL=... sample.sh <out.txt>. Reboots the spare and merges repeated log reads (main log rotates fast).
set -u
D=${SPARE_SERIAL:?set SPARE_SERIAL}; OUT=$1; T=$(mktemp -d)
echo "# adb reboot $(date -u +%FT%TZ). Epoch seconds, device clock." > "$OUT"
adb -s $D reboot; sleep 5; adb -s $D wait-for-device
end=$((SECONDS+110))
while [ $SECONDS -lt $end ]; do
  adb -s $D logcat -d -v epoch -b events 2>/dev/null | grep -E "boot_progress|uc_|ssm_user|am_user_state" >> $T/ev
  adb -s $D logcat -d -v epoch -b main,system 2>/dev/null | grep -E "PlainphoneBoot|SystemServerTiming: broadcastIntent" >> $T/main
  sleep 1.5
done
{ echo "## events"; sort -u $T/ev | sort -n -k1,1; echo "## PlainphoneBoot and broadcast timing (merged)"; sort -u $T/main | sort -n -k1,1; } >> "$OUT"
rm -rf $T
