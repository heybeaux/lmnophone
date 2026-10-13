#!/bin/bash
# Copied from the build 42 helper (PLA-505); serial from the environment.
# Prints TTS (uid 10316, speech) player starts from the PlayerBase event log.
adb -s "$S" shell dumpsys audio | tr -d '\r' | python3 -I -c '
import sys,re
L=sys.stdin.read().split("Events log: playback activity as reported through PlayerBase",1)[1].split("\n\n",1)[0].splitlines()
tts=set()
for l in L:
    m=re.search(r"new player piid:(\d+) uid/pid:10316/",l)
    if m and "CONTENT_TYPE_SPEECH" in l and "USAGE_MEDIA" in l: tts.add(m[1])
for l in L:
    m=re.search(r"player piid:(\d+) event:started",l)
    if m and m[1] in tts: print(l)
'
