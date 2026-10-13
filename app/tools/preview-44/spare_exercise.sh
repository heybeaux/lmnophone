#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# PLA-494 spare Change PIN exercise: original -> temporary -> original, exactly one wrong try.
# Usage: S=<spare> PLAINPHONE_SPARE_DRIVER=1 bash spare_exercise.sh. PINs are read from the secrets
# directory and never printed; the temporary PIN file is deleted at the end (also on failure).
set -u
cd "$(dirname "$0")"; source ./spare_lib.sh
SEC=$HOME/.plainphone/secrets; ORIG=$(cat "$SEC/spare-test-pin"); TMPF="$SEC/spare-temp-pin-pla494"
( umask 077; python3 -I -c 'import secrets; print("".join(secrets.choice("0123456789") for _ in range(8)))' > "$TMPF" ); TEMP=$(cat "$TMPF")
[ "$TEMP" != "$ORIG" ] || { echo "FAIL: temporary equals original"; rm -f "$TMPF"; exit 1; }
expect(){ local t; t=$(texts); if [[ $t == *"$1"* ]]; then echo "OK: $2"; else echo "STOP: expected '$1' ($2); screen: ${t:0:160}"; exit 1; fi; }
change(){ stap "Change PIN" && expect "Your PIN" "Change PIN asks for the current PIN"
  enter "$1"; expect "Choose a new PIN" "current PIN accepted, new-PIN page"
  type_in 0 "$2"; type_in 1 "$2"; tap Continue; sleep 3; expect "PIN changed." "PIN changed shown"
  tap OK; expect "You hold the PIN" "back on the menu"; back; }
trap 'rm -f "$TMPF"; echo "temporary PIN file deleted"' EXIT
a shell input keyevent WAKEUP; a shell wm dismiss-keyguard; sleep 1
echo "start $(date -u +%FT%TZ) $(state)"
controls; enter "$ORIG"; expect "You hold the PIN" "original PIN opens the menu"
change "$ORIG" "$TEMP"; echo "changed to temporary at $(date -u +%FT%TZ)"
controls; enter "$ORIG"; expect "That PIN isn't right" "original PIN refused after the change (the one wrong try)"
back
controls; enter "$TEMP"; expect "You hold the PIN" "temporary PIN opens the menu"
change "$TEMP" "$ORIG"; echo "changed back at $(date -u +%FT%TZ)"
controls; enter "$ORIG"; expect "You hold the PIN" "original PIN opens again"
back; home
echo "end $(date -u +%FT%TZ) $(state)"
