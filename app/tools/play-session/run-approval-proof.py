#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Run one private emulator proof. Build/install the app and instrumentation APK first.
Usage: run-approval-proof.py emulator-59xx approve|approval-expiry|approval-signer
Reset the synthetic starter list with setup.sh between cases. Never use on a phone.
"""
import os
from pathlib import Path
import re
import subprocess
import sys
import time

target, phase = sys.argv[1:]
assert re.fullmatch(r"emulator-59[0-9][02468]", target), "private emulator port required"
assert phase in {"approve", "approval-expiry", "approval-signer"}
root = Path(os.environ["PAPERCLIP_RUN_SCRATCH_DIR"])
sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk")))
adb = [str(sdk / "platform-tools/adb"), "-s", target]
avd = subprocess.check_output(adb + ["emu", "avd", "name"], text=True).splitlines()[0]
assert avd in {"pla104-a36", "pla113-a35", "pla186-a35"}, "unexpected AVD"
log = root / ("proof-" + phase + ".log")
subprocess.run(adb + ["uninstall", "org.plainphone.playcandidate"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
with log.open("w") as output:
    process = subprocess.Popen(adb + ["shell", "am", "instrument", "-w", "-e", "phase", phase,
        "org.plainphone.test/org.plainphone.play.PlaySessionProofInstrumentation"], stdout=output, stderr=subprocess.STDOUT)
    installed = resigned = False
    deadline = time.monotonic() + 150
    while process.poll() is None and time.monotonic() < deadline:
        text = log.read_text()
        if "readyForInstall=true" in text and not installed:
            subprocess.run(adb + ["install", "-r", "-t", str(root / "play-fixture/play-candidate.apk")], check=True)
            installed = True
        if "readyForResign=true" in text and not resigned:
            subprocess.run(adb + ["uninstall", "org.plainphone.playcandidate"], check=True)
            subprocess.run(adb + ["install", "-r", "-t", str(root / "play-fixture/play-other-signer.apk")], check=True)
            resigned = True
        time.sleep(.2)
    if process.poll() is None:
        process.terminate()
        process.wait(timeout=10)
text = log.read_text()
print(text)
if "result=PASS:" + phase not in text:
    raise SystemExit("Proof failed; inspect the scratch log")
