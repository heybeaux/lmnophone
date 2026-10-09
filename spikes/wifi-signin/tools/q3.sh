#!/bin/bash
# THROWAWAY SPIKE (PLA-18). Q3 launch path. Run under bash.
cd "$(dirname "$0")/.."; source tools/pp.sh "${1:?serial}" || exit 1
tapnotif() { A shell cmd statusbar expand-notifications; sleep 1.5; echo "shade: $(texts 400)"; tapp "Sign in to" || tapp "Sign in"; sleep 3; echo "top after tap: $(toppkg)"; A shell cmd statusbar collapse; }
case "$2" in
baseline)
  echo "## a) no session, sign-in app hidden: tap the system notification"; cmd signin-state; tapnotif; cmd signin-state ;;
session)
  echo "## b) session started (unhidden + lock-task allowlist): tap the system notification"; cmd signin-start 300; tapnotif; cmd signin-state
  A shell dumpsys activity activities | grep -E "mLockTaskModeState|topResumedActivity|\* Task\{" | head -6 ;;
direct)
  echo "## c) Device Owner opens it itself"; for m in reflect intent; do echo "-- $m: $(cmd signin-open $m)"; sleep 3; echo "top: $(toppkg)"; A shell input keyevent KEYCODE_HOME; sleep 1; done
  echo "-- recheck: $(cmd signin-open recheck)"; sleep 4; A shell dumpsys notification --noredact | grep -E "android.title=String \(Sign in" | head -2 ;;
esac
