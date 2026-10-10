#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-5). A5: installed / enabled / hidden / launcher entry for each package given.
# Usage: tools/a5-inventory.sh pkg...   (prints TSV)
set -uo pipefail
: "${SERIAL:?Set SERIAL to the explicitly approved test device}"
case "$SERIAL" in emulator-*) echo "refusing: physical-device helper requires an approved physical device" >&2; exit 2;; esac
export SERIAL
a() { adb -s "$SERIAL" "$@"; }
disabled=$(a shell pm list packages -d | tr -d '\r')
visible=$(a shell pm list packages | tr -d '\r')
all=$(a shell pm list packages -u | tr -d '\r')
printf 'package\tinstalled\tvisible\tenabled\tlauncher_activity\n'
for p in "$@"; do
  if ! grep -qx "package:$p" <<<"$all"; then printf '%s\tno\t-\t-\t-\n' "$p"; continue; fi
  vis=$(grep -qx "package:$p" <<<"$visible" && echo yes || echo no)
  en=$(grep -qx "package:$p" <<<"$disabled" && echo disabled || echo enabled)
  la=$(a shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$p" 2>/dev/null | tr -d '\r' | grep '/' | tail -1)
  printf '%s\tyes\t%s\t%s\t%s\n' "$p" "$vis" "$en" "${la:-none}"
done
