#!/bin/bash
# THROWAWAY SPIKE (PLA-23, V5). Exits from the notification shade while locked: Quick Settings/gear, Manage, keyboard notification.
cd "$(dirname "$0")/.."; source tools/pp.sh "$1" || exit 1; source tools/detect.sh; D=evidence/pla23/v345-$2
rtap() { local id="$1" how="$2" node="$3" res; ui | grep -qE "(text|content-desc)=\"[^\"]*$node" || { printf '%s\tTEST_FAILED\tNO NODE %s\n' "$id" "$node"; return; }
  A logcat -b all -c; $how "$node" >/dev/null; sleep 3
  res=$(A logcat -b all -d | grep -oE "wm_set_resumed_activity: \[[0-9]+,[^,]+" | sed -E 's/.*,//' | sort -u | tr '\n' ' ')
  printf '%s\tresumed=%s\tstatus=%s\n' "$id" "$res" "$(pol | grep -oE 'lockTaskModeState=[0-9]')"; }
echo "# $(date -u +%FT%TZ) V5 shade, locked. policy: $(pol)"
A shell input keyevent KEYCODE_HOME; sleep 1; A shell cmd statusbar expand-settings; sleep 2.5; shot $D/v5-locked-shade-expand-settings.png
echo "expand-settings under lock task: Quick Settings tiles / settings gear nodes: $(ui | grep -ciE '(content-desc|text)="[^"]*(settings|internet|bluetooth)')"
rtap v5-shade-manage tapt "Manage"; shot $D/v5-shade-manage.png; echo "landed texts: $(texts 200)"
A shell input keyevent KEYCODE_BACK; A shell input keyevent KEYCODE_HOME; sleep 1; A shell cmd statusbar expand-notifications; sleep 2
rtap v5-shade-keyboard-notif tapp "qwerty2 configured"; shot $D/v5-shade-keyboard-notif.png; echo "landed texts: $(texts 200)"
A shell input keyevent KEYCODE_BACK; A shell input keyevent KEYCODE_HOME; sleep 1
echo "# end policy: $(pol)"
