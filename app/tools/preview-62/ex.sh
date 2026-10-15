#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# PLA-603 keyboard and text-size exercise steps (from PLA-592) on top of tb.sh (sole spare driver). Keyboard focus
# (TAB) drives TalkBack on the spare; the PIN comes from the secrets file, never printed.
tab(){ local i; for i in $(seq 1 "${1:-1}"); do a shell input keyevent TAB; sleep "${2:-2.8}"; done; }
tbon(){ a shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService; a shell settings put secure accessibility_enabled 1; tbup; sleep 3; }
tboff(){ a shell settings delete secure enabled_accessibility_services; a shell settings put secure accessibility_enabled 0; sleep 2; a shell dumpsys accessibility | grep -o 'Bound services:{[^}]*}'; }
pin(){ tr -d '[:space:]' < "$HOME/.plainphone/secrets/spare-test-pin"; }
