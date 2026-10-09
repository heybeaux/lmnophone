#!/bin/bash
# THROWAWAY SPIKE (PLA-23, V2). Blocked-launch / link matrix with the result detector, protected and control runs.
# Usage: tools/matrix2.sh <serial> <label> <cases-file> <on|control>
#  on      = final policy (lock task on, BLOCK, hidden browsers/Google app/CaptivePortalLogin)
#  control = protection off: lock task stopped via the adb-only hook and the hidden packages unhidden, to show each
#            target is reachable. The script restores the final policy afterwards.
cd "$(dirname "$0")/.."
source tools/pp.sh "$1" || exit 1
source tools/detect.sh
LABEL="$2"; CASES="$3"; MODE="$4"
HIDDEN="com.android.chrome com.plainphone.fx.browser com.google.android.googlequicksearchbox"
OUT="evidence/pla23/matrix-$LABEL-$MODE.tsv"
want=1
if [ "$MODE" = control ]; then cmd lt-stop >/dev/null; sleep 2; for p in $HIDDEN; do cmd unhide $p >/dev/null; done; want=0; fi
{ echo "# $(date -u +%FT%TZ) mode=$MODE policy: $(pol)"; printf 'id\tmode\toutcome\tdetail\tresumed\tlaunch\tspec\n'; } > "$OUT"
grep -v '^#' "$CASES" | grep -v '^$' | while IFS='|' read -r id spec; do
  for m in ${MODES:-same newtask chooser}; do
    extra=""; [ $m = newtask ] && extra=" newtask=1"; [ $m = chooser ] && extra=" chooser=1"
    r=$(run_case "$spec$extra" $want)
    printf '%s\t%s\t%s\t%s\n' "$id" "$m" "$r" "$spec" | tee -a "$OUT" | cut -f1-4
  done
done
if [ "$MODE" = control ]; then for p in $HIDDEN; do cmd hide $p >/dev/null; done; A shell input keyevent KEYCODE_HOME; sleep 1; cmd lt-start >/dev/null; sleep 15; fi
echo "# end policy: $(pol)" >> "$OUT"
