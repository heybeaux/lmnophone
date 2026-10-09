#!/bin/bash
# THROWAWAY SPIKE (PLA-23, V1). UI route walker. Source after tools/pp.sh <serial> and tools/detect.sh.
# walk <id> <home-button-text> <pkg> <step>... ; last step is the measured tap.
#   step forms: t:<exact text>  d:<exact content-desc>  p:<partial text/desc>  o:<exact text, optional (skip if absent)>
#               k:<KEYCODE>  l:<long-press exact text/desc>  s:<sec sleep>
# Every tap asserts the node exists in a fresh uiautomator dump; missing -> TEST_FAILED "NO NODE <x>".
# Appends a row to $WALK_TSV: id route outcome landed texts nested evidence
EV=${EV:-evidence/pla23/v1}
WALK_TSV=${WALK_TSV:-$EV/walks.tsv}
ALLOWED="com.android.camera2 com.google.android.dialer com.plainphone.spike.emu com.android.server.telecom com.google.android.apps.safetyhub com.google.android.apps.messaging com.android.phone"
LINKRE='Learn more|Help|Privacy|Terms|http|Settings|account|Sign in'
# on this fresh a36 image /sdcard dumps do not persist; dump to /data/local/tmp instead (overrides pp.sh ui)
ui() { A shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1; A shell cat /data/local/tmp/ui.xml; }

_node() { # $1 kind $2 value -> prints bounds numbers of first matching node from fresh dump
  local x; x=$(ui | tr '>' '\n')
  case "$1" in
    t|o) echo "$x" | grep -m1 "text=\"$2\"" ;;
    d) echo "$x" | grep -m1 "content-desc=\"$2\"" ;;
    p) echo "$x" | grep -m1 -E "(content-desc|text)=\"[^\"]*$2" ;;
    l|x) echo "$x" | grep -m1 -E "(content-desc|text)=\"$2\"" ;;
  esac | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | head -4 | tr '\n' ' '
}
_step() { # returns 1 + prints NO NODE on failure
  local k="${1%%:*}" v="${1#*:}" b
  case "$k" in
    k) A shell input keyevent "$v"; sleep 1; return 0 ;;
    s) sleep "$v"; return 0 ;;
  esac
  b=$(_node "$k" "$v")
  if [ -z "$b" ]; then [ "$k" = o ] && return 0; echo "NO NODE $v"; return 1; fi
  set -- $b
  if [ "$k" = l ]; then A shell input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 1200
  else A shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); fi
  sleep "${STEPWAIT:-1.8}"
}
_reset() { # $1 home button text, $2 pkg
  A shell am force-stop "$2"; A shell input keyevent KEYCODE_HOME; sleep 1.2
  _step "t:$1" || return 1
  sleep 1
}
toptop() { A shell dumpsys activity activities | grep -m1 topResumedActivity | sed -E 's/.* u0 ([^ ]+) t[0-9]+.*/\1/'; }
nodetexts() { ui | grep -o -E '(content-desc|text)="[^"]+"' | sed -E 's/^(content-desc|text)="//; s/"$//'; }

# walk1: one walk, echoes "OUTCOME<TAB>landed<TAB>texts" and saves evidence
walk1() {
  local id="$1" home="$2" pkg="$3"; shift 3
  local n=$# i=0 s err pre_top pre_txt post_top post_txt res log out detail p0 p1
  p0=$(pol)
  if ! err=$(_reset "$home" "$pkg"); then printf 'TEST_FAILED\t%s\t\n' "$err"; return; fi
  for s in "$@"; do
    i=$((i+1))
    if [ $i -eq $n ]; then
      pre_top=$(toptop); pre_txt=$(nodetexts | sort | tr '\n' '|')
      # assert last node before clearing log
      if [ "${s%%:*}" != k ] && [ -z "$(_node "${s%%:*}" "${s#*:}")" ]; then printf 'TEST_FAILED\tNO NODE %s\t%s\n' "${s#*:}" "$pre_top"; return; fi
      A logcat -b all -c
    fi
    if ! err=$(_step "$s"); then printf 'TEST_FAILED\t%s\t%s\n' "$err" "$(toptop)"; return; fi
  done
  sleep 1.5
  log=$(A logcat -b all -d)
  res=$(echo "$log" | grep -oE "wm_set_resumed_activity: \[[0-9]+,[^,]+" | sed -E 's/.*,//' | sort -u | tr '\n' ' ')
  post_top=$(toptop); post_txt=$(nodetexts | sort | tr '\n' '|')
  shot "$EV/$id.png"
  nodetexts > "$EV/$id.txt"; { echo "resumed: $res"; echo "top: $post_top"; } >> "$EV/$id.txt"
  p1=$(pol)
  local allp=$( (echo "$res" | tr ' ' '\n'; echo "$post_top") | grep -v '^$' | sed 's#/.*##' | sort -u)
  local outside=$(for q in $allp; do case " $ALLOWED android " in *" $q "*) ;; *) echo -n "$q ";; esac; done)
  if [ "$p0" != "$p1" ]; then out=TEST_FAILED; detail="policy changed"
  elif echo "$res $post_top" | grep -q BlockedAppActivity; then out=POLICY_DENIED; detail="BlockedAppActivity"
  elif echo "$res $post_top" | grep -q "com.plainphone.spike.emu/.LinkActivity"; then out=LINK_CAUGHT; detail=LinkActivity
  elif [ "$pre_top" = "$post_top" ] && [ "$pre_txt" = "$post_txt" ]; then out=TEST_FAILED; detail="NO CHANGE"
  elif [ -n "$outside" ]; then out=TARGET_OPENED; detail="ESCAPE outside allowlist: $outside"
  else out=TARGET_OPENED; detail="in allowed pkg(s): $(echo $allp)"; fi
  printf '%s\t%s | res=%s top=%s\t%s\n' "$out" "$detail" "$res" "$post_top" "$(echo "$post_txt" | cut -c1-300)"
}

# walk: walk1 + follow up to 2 link candidates on the landing screen; append to TSV
walk() {
  local id="$1" home="$2" pkg="$3"; shift 3
  local r out landed txt nested="" c j=0 rr
  r=$(walk1 "$id" "$home" "$pkg" "$@")
  out=$(echo "$r" | cut -f1); landed=$(echo "$r" | cut -f2); txt=$(echo "$r" | cut -f3)
  if [ "$out" = TARGET_OPENED ] && [ -z "$NOLINKS" ]; then
    while IFS= read -r c; do
      [ -z "$c" ] && continue; j=$((j+1)); [ $j -gt 2 ] && break
      rr=$(walk1 "$id.L$j" "$home" "$pkg" "$@" "x:$c")
      nested="$nested [L$j '$c': $(echo "$rr" | cut -f1,2 | tr '\t' ' ')]"
    done < <(echo "$txt" | tr '|' '\n' | grep -E "$LINKRE" | grep -v -x -E 'Settings|Navigate up|More options' | head -2)
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$id" "$home > $*" "$out" "$landed" "$(echo "$txt" | cut -c1-200)" "$nested" "$EV/$id.png" | tee -a "$WALK_TSV"
}
