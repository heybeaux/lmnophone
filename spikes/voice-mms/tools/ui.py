#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# THROWAWAY (PLA-458): uiautomator helper. ui.py <serial> dump | tap <text-or-desc substring>
import re, subprocess, sys, xml.etree.ElementTree as ET

serial, cmd = sys.argv[1], sys.argv[2]
if not re.fullmatch(r"emulator-59[0-9]{2}", serial):
    sys.exit("Use only the private emulator-59xx assigned to this spike")
if cmd not in ("dump", "tap"):
    sys.exit("Use dump or tap")
adb = ["adb", "-s", serial]
avd_name = subprocess.run(adb + ["shell", "getprop", "ro.boot.qemu.avd_name"],
                          capture_output=True, check=True, text=True).stdout.strip()
if avd_name != "pla458-a35":
    sys.exit("Use only the private pla458-a35 AVD assigned to this spike")
subprocess.run(adb + ["shell", "uiautomator", "dump", "/sdcard/u.xml"], capture_output=True, check=True)
root = ET.fromstring(subprocess.run(adb + ["exec-out", "cat", "/sdcard/u.xml"], capture_output=True, check=True).stdout)
nodes = list(root.iter("node"))
if cmd == "dump":
    for n in nodes:
        t, d = n.get("text"), n.get("content-desc")
        if t or d:
            print("%s | text=%r desc=%r rid=%s pkg=%s %s" % (n.get("class"), t, d, n.get("resource-id"), n.get("package"), n.get("bounds")))
elif cmd == "tap":
    want = sys.argv[3]
    for n in nodes:
        if want in (n.get("text") or "") or want in (n.get("content-desc") or "") or want == n.get("resource-id"):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            subprocess.run(adb + ["shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
            print("tapped", want, n.get("bounds"))
            break
    else:
        print("not found", want)
        sys.exit(1)
