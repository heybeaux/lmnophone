#!/bin/bash
# THROWAWAY SPIKE (PLA-11). Q4 web containment on one emulator. Usage: tools/q4.sh <serial> <label>
cd "$(dirname "$0")/.."
source tools/pp.sh "$1" || exit 1
L="$2"; O="evidence/q4/$L.txt"; : > "$O"
log() { echo "$*" | tee -a "$O"; }
run() { log "\$ $*"; "$@" 2>&1 | tee -a "$O"; }
log "# Q4 on $(A shell getprop ro.build.fingerprint) $(date -u +%FT%TZ)"
for f in applinks browser emergency urlbar httponly; do run A install -t -r apps/fx-$f/build/outputs/apk/debug/fx-$f-debug.apk; done
log "## browser role before classifying"; run A shell cmd role get-role-holders android.app.role.BROWSER
log "## negative control: classifier without the identity checks (Plainphone first, never-hide first)"; run cmd classify-noidentity
log "## classifier dry run"; run cmd classify-dry
log "## floor (system-only) and negative control floor-naive"; run cmd floor; run cmd floor-naive
log "## resolve before hiding"
for s in "data=https://example.com/" "data=http://example.com/" "data=https://bank.plainphone-fx.example/x" "data=ppfx://open" "action=com.android.phone.EmergencyDialer.DIAL"; do run cmd resolve "$s"; done
run cmd customtabs
log "## classifier apply (hides general browsers and named packages)"; run cmd classify
for p in com.android.chrome com.plainphone.fx.browser com.google.android.googlequicksearchbox com.plainphone.fx.httponly com.plainphone.fx.urlbar com.plainphone.fx.applinks com.plainphone.spike.emu; do run cmd is-hidden $p; done
log "## browser role after hiding"; run A shell cmd role get-role-holders android.app.role.BROWSER
log "## resolve after hiding"
for s in "data=https://example.com/" "data=http://example.com/" "data=https://bank.plainphone-fx.example/x" "data=ppfx://open"; do run cmd resolve "$s"; done
run cmd customtabs
log "## app links verification state"; run A shell pm get-app-links com.plainphone.fx.applinks
log "## browser role: in-app request while locked (RoleManager.createRequestRoleIntent)"
A logcat -c; cmd browser-role-request >/dev/null; sleep 3
run top; shot "evidence/q4/$L-role-request.png"
A logcat -d | grep -E "PPEMU|BlockedApp|RequestRole|role" | grep -v "cmd \[" | tail -8 | tee -a "$O"
log "## texts on screen"; texts 400 | tee -a "$O"; echo | tee -a "$O"
A shell input keyevent KEYCODE_BACK; sleep 1; A shell input keyevent KEYCODE_HOME; sleep 1
run A shell cmd role get-role-holders android.app.role.BROWSER
log "## link matrix (see evidence/q1/matrix-$L-q4-links.tsv)"
