#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# PLA-582/PLA-592 TalkBack exercise helpers (sole spare driver). Records the spare's audio
# output with scrcpy and transcribes it locally with Whisper, so the evidence is what
# TalkBack actually spoke. Usage: bash tb.sh '<commands>'
: "${S:?spare serial from the environment}"; export S PLAINPHONE_SPARE_DRIVER=1
source "$(dirname "$0")/spare_lib.sh"
AUD=${AUD:?audio output dir}; OUT="$(dirname "$0")/evidence/spare/talkback"
rec(){ REC=$1; scrcpy -s "$S" --no-video --no-window --no-control --audio-source=output --audio-codec=raw --record="$AUD/$1.wav" >"$AUD/$1.log" 2>&1 & RP=$!; sleep 4; }
stop(){ sleep 3; kill -TERM $RP; wait $RP 2>/dev/null
  ffmpeg -hide_banner -loglevel error -y -i "$AUD/$REC.wav" -ac 1 -ar 16000 -af "silenceremove=start_periods=1:start_threshold=-50dB:stop_periods=-1:stop_duration=0.6:stop_threshold=-50dB" "$AUD/$REC.t.wav"; mv "$AUD/$REC.t.wav" "$AUD/$REC.trim.wav"
  whisper "$AUD/$REC.trim.wav" --model base --language en --condition_on_previous_text False --output_format srt --output_dir "$AUD" --fp16 False >/dev/null 2>&1
  cp "$AUD/$REC.trim.srt" "$OUT/$REC.srt"; echo "== $REC"; grep -v -E '^[0-9]+$|^$' "$AUD/$REC.trim.srt" | paste - - | sed 's/,[0-9]* --> [0-9:]*,[0-9]*//'; }
nx(){ local i; for i in $(seq 1 "${1:-1}"); do a shell input swipe 200 1300 900 1300 120; sleep "${2:-2.5}"; done; }   # TalkBack: next item
pv(){ a shell input swipe 900 1300 200 1300 120; sleep 2.5; }   # previous item
# One dump per screen, before recording: a uiautomator dump restarts TalkBack.
tbup(){ local i; for i in $(seq 1 20); do a shell dumpsys accessibility | grep -q 'Bound services:{Service\[label=TalkBack' && return; sleep 1; done; echo "FAIL: TalkBack not bound"; exit 3; }
grab(){ dump > "$AUD/cur.xml"; sleep 4; tbup; sleep 3; }
xy(){ python3 -I -c "
import sys,re,xml.etree.ElementTree as E
for n in E.parse(sys.argv[2]).iter('node'):
    if n.get('text')==sys.argv[1] or n.get('content-desc')==sys.argv[1]:
        b=list(map(int,re.findall(r'\\d+',n.get('bounds')))); print((b[0]+b[2])//2,(b[1]+b[3])//2); break" "$1" "$AUD/cur.xml"; }
dt(){ local p; p=$(xy "$1"); [ -n "$p" ] || { echo "FAIL: '$1' not found"; return 1; }; tbup; a shell "input tap $p; input tap $p"; sleep 2.5; }  # activate by text
ft(){ local p; p=$(xy "$1"); [ -n "$p" ] || { echo "FAIL: '$1' not found"; return 1; }; tbup; a shell input tap $p; sleep 2.5; }  # focus by touch
shot(){ a shell screencap -p > "$OUT/$1.png"; }
eval "$1"
