#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# PLA-603 keyboard and text-size exercise steps (from PLA-592) on top of tb.sh (sole spare driver). Keyboard focus
# (TAB) drives TalkBack on the spare; the PIN comes from the secrets file, never printed.
tab(){ local i; for i in $(seq 1 "${1:-1}"); do a shell input keyevent TAB; sleep "${2:-2.8}"; done; }
tbon(){ a shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService; a shell settings put secure accessibility_enabled 1; tbup; sleep 3; }
tboff(){ a shell settings delete secure enabled_accessibility_services; a shell settings put secure accessibility_enabled 0; sleep 2; a shell dumpsys accessibility | grep -o 'Bound services:{[^}]*}'; }
pin(){ tr -d '[:space:]' < "$HOME/.plainphone/secrets/spare-test-pin"; }
# Text and description of the focused node and its descendants (keyboard focus readback).
focus(){ dump | python3 -I -c "
import sys,xml.etree.ElementTree as E
r=E.fromstring(sys.stdin.read())
for n in r.iter('node'):
    if n.get('focused')=='true':
        t=[m.get('text') or m.get('content-desc') for m in n.iter('node') if (m.get('text') or m.get('content-desc'))]
        print('FOCUS:', ' / '.join(t)[:120] or '(no text)', n.get('class').split('.')[-1], n.get('bounds')); break
else: print('FOCUS: none')" | sed 's/•*//g'; }
key(){ a shell input keyevent "$1"; sleep "${2:-1.2}"; }
# Scale pass: scroll to the end, then report the lowest clickable node and the lowest text against the nav bar top (y 2280).
bottom(){ local i; for i in 1 2 3 4; do a shell input swipe 540 1800 540 600 250; sleep 0.6; done; sleep 0.8; report; }
report(){ dump | python3 -I -c "
import sys,re,xml.etree.ElementTree as E
ns=list(E.fromstring(sys.stdin.read()).iter('node'))
def b(n): return list(map(int,re.findall(r'\d+',n.get('bounds'))))
cl=[n for n in ns if n.get('clickable')=='true' and b(n)[3]>b(n)[1]]
tx=[n for n in ns if (n.get('text') or n.get('content-desc')) and b(n)[3]>b(n)[1]]
L=max(cl,key=lambda n:b(n)[3]) if cl else None
T=max(tx,key=lambda n:b(n)[3])
lt=lambda n:(n.get('text') or n.get('content-desc') or ''.join(m.get('text') or '' for m in n.iter('node')))[:40]
print('  last-action=%r bottom=%s  last-text=%r bottom=%s  clears-nav=%s narrow=%s'%(lt(L) if L is not None else None, b(L)[3] if L is not None else None, lt(T), b(T)[3], (b(L)[3] if L is not None else 0)<=2280 and b(T)[3]<=2280, [lt(n) for n in tx if b(n)[0]<0 or b(n)[2]>1080]))"; }
shot(){ a exec-out screencap -p > "$OUT/$1.png"; }
scale_pass(){ local f=$1; a shell settings put system font_scale "$f"; sleep 3; echo "== font $(a shell settings get system font_scale)"
  home; shot "home-$f"; echo " Home"; bottom_noscroll
  tap More; shot "more-$f-top"; echo " More"; bottom; shot "more-$f-end"
  home; tap More; tap "Phone settings"; shot "phone-settings-$f-top"; echo " Phone settings"; bottom; shot "phone-settings-$f-end"
  home; tap More; stap "About this phone"; sleep 1; shot "about-$f-top"; echo " About"; bottom; shot "about-$f-end"
  home; tap More; stap "PIN settings"; sleep 1; texts | grep -q "Enter your PIN" && { type_in 0 "$(pin)" && key ENTER 3; }; echo " PIN settings (secure, no shot): $(texts | cut -c1-30)"; bottom
  stap "Apps on this phone"; sleep 1; echo " Apps on this phone (secure)"; bottom; home; }
bottom_noscroll(){ report; }
