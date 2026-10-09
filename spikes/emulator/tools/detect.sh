#!/bin/bash
# THROWAWAY SPIKE (PLA-23, V2). Result detector. Source after tools/pp.sh.
# run_case <spec> <expected lockTaskModeState 1|0>  -> prints "OUTCOME<TAB>detail<TAB>resumed<TAB>launch"
# OUTCOME is one of POLICY_DENIED, LINK_CAUGHT, NO_HANDLER, TARGET_OPENED, TEST_FAILED.
# - policy state (status line) is read before and after; any change, or a lock-task state other than expected -> TEST_FAILED
# - activity events (wm_set_resumed_activity, START) are captured across the whole case, so a target that opens
#   briefly and returns still counts as TARGET_OPENED
# - no launch log line (stimulus never ran) or an unexpected exception -> TEST_FAILED
DPC=com.plainphone.spike.emu
pol() { cmd status | sed -E 's/ locked=(true|false)//'; }
run_case() {
  local spec="$1" want="${2:-1}" pre post log launch res out detail
  A shell input keyevent KEYCODE_HOME; sleep 0.7
  pre=$(pol)
  A logcat -b all -c
  cmd launch "$spec" >/dev/null
  sleep "${WAIT:-2.2}"
  log=$(A logcat -b all -d)
  post=$(pol)
  launch=$(echo "$log" | grep -m1 -oE "PPEMU *: launch \[.*\] (returned normally|threw [A-Za-z]+)" | grep -oE "(returned normally|threw [A-Za-z]+)$")
  res=$(echo "$log" | grep -oE "wm_set_resumed_activity: \[[0-9]+,[^,]+" | sed -E 's/.*,//' | grep -v "^$DPC/.MainActivity$" | sort -u | tr '\n' ' ')
  if [ "$pre" != "$post" ]; then out=TEST_FAILED; detail="policy changed during case"
  elif ! echo "$pre" | grep -q "lockTaskModeState=$want "; then out=TEST_FAILED; detail="lock-task state not $want before case"
  elif [ -z "$launch" ]; then out=TEST_FAILED; detail="no launch log (stimulus not delivered)"
  elif [ "$launch" = "threw ActivityNotFoundException" ]; then out=NO_HANDLER; detail="ActivityNotFoundException"
  elif [[ "$launch" == threw* ]]; then out=TEST_FAILED; detail="unexpected $launch"
  elif echo "$res" | grep -q "$DPC/.LinkActivity"; then out=LINK_CAUGHT; detail="LinkActivity"
  elif [ -n "$(echo "$res" | tr ' ' '\n' | grep -v -e '^$' -e BlockedAppActivity)" ]; then out=TARGET_OPENED; detail=$(echo "$res" | tr ' ' '\n' | grep -v -e '^$' -e BlockedAppActivity | head -3 | tr '\n' ' ')
  elif echo "$res" | grep -q BlockedAppActivity; then out=POLICY_DENIED; detail=$(echo "$log" | grep -m1 -oE "BlockedAppActivity.{0,0}" ; echo "$log" | grep -m1 -oE "is not available right now" )
  else out=TEST_FAILED; detail="launched but no activity resumed"; fi
  printf '%s\t%s\t%s\t%s\n' "$out" "$(echo $detail)" "$res" "$launch"
}
