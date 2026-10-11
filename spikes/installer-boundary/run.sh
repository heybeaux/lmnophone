#!/bin/bash
# THROWAWAY SPIKE (PLA-86). Usage: ./run.sh <emulator-serial>. Shell-uid sessions use the same
# PackageInstallerSession checks as an app's Session.commit(); app-only APIs are not covered here.
cd "$(dirname "$0")"; A="adb -s ${1:?serial}"; P=org.plainphone.spike.upd
size() { stat -f %z "out/$1.apk"; }
create() { $A shell pm install-create -S "$(size $1)" ${2:+--pkg $2} | sed -n 's/.*\[\([0-9]*\)\].*/\1/p'; }
push() { $A shell pm install-write -S "$(size $2)" "$1" base.apk - < "out/$2.apk"; }
installed() { $A shell dumpsys package $P | grep -m1 versionCode | tr -s ' '; }
step() { echo; echo "== $*"; }
echo "device: $($A shell getprop ro.build.version.release) API $($A shell getprop ro.build.version.sdk) $($A shell getprop ro.product.model)"
$A shell pm uninstall $P >/dev/null 2>&1; $A shell pm uninstall org.plainphone.spike.other >/dev/null 2>&1
step "E0 baseline: install v2 (keyA)"; $A install -r out/v2.apk; installed

step "E1 a written session outlives the process that created it"
S1=$(create v3 $P); push $S1 v3 >/dev/null; echo "session $S1 written by a pm process that has now exited"
$A shell dumpsys package installer | grep -m1 -o "sessionId=$S1[^ ]*\|$S1[^ ]* " ; echo "(listed above if present)"
echo "later, unrelated process commits it:"; $A shell pm install-commit $S1; installed

step "E2 write after commit"; push $S1 v3 2>&1 | grep -A1 -m1 -i "exception\|success" | tail -1
step "E3 second commit of the same session"; $A shell pm install-commit $S1 2>&1 | grep -A1 -m1 -i "exception\|failure\|success" | tail -1

step "E4 appPackageName binding: --pkg $P with another package's APK"
S4=$(create otherpkg $P); push $S4 otherpkg >/dev/null; $A shell pm install-commit $S4 2>&1 | tail -1
$A shell pm list packages org.plainphone.spike.other

step "E5 downgrade: v2 over installed v3"; S5=$(create v2 $P); push $S5 v2 >/dev/null; $A shell pm install-commit $S5 2>&1 | tail -1; installed
step "E6 same version: v3 over installed v3"; S6=$(create v3 $P); push $S6 v3 >/dev/null; $A shell pm install-commit $S6 2>&1 | tail -1; installed
step "E7 other signer: v3 keyB over installed v3 keyA"; S7=$(create v3-otherkey $P); push $S7 v3-otherkey >/dev/null; $A shell pm install-commit $S7 2>&1 | tail -1

step "E8 bytes replaced in an open session before commit (write v2, then v3 under the same name)"
$A shell pm uninstall $P >/dev/null; $A install out/v1.apk >/dev/null; installed
S8=$(create v3 $P); push $S8 v2 >/dev/null; push $S8 v3 >/dev/null; $A shell pm install-commit $S8 2>&1 | tail -1; installed

step "E9 commit after abandon"; S9=$(create v2 $P); push $S9 v2 >/dev/null; $A shell pm install-abandon $S9; $A shell pm install-commit $S9 2>&1 | grep -A1 -m1 -i "exception\|failure\|success" | tail -1

step "cleanup"; $A shell pm uninstall $P; $A shell pm uninstall org.plainphone.spike.other 2>&1 | tail -1
