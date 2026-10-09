#!/bin/bash
# THROWAWAY SPIKE (PLA-22). Runs the trial matrix on emulator-5620. Appends rows to evidence/trials.md.
cd "$(dirname "$0")/.."; source tools/bl.sh emulator-5620
CP=com.google.android.captiveportallogin
run() { # dir pkg delay kind label
  local pre=visible; [ "$1" = unhide ] && pre=hidden; [ "$1" = unsuspend ] && pre=suspended
  reset $2 $pre
  trial "$@" | tee -a evidence/trials.md
}
while read -r dir pkg d kind label; do
  [ -z "$dir" ] && continue
  case $pkg in CP) pkg=$CP;; esac
  run $dir $pkg $d $kind $label
done < "${1:-/dev/stdin}"
A emu avd snapshot load do-clean2 >/dev/null
echo MATRIX DONE
