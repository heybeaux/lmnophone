# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
"""tools/emergency-gate.sh against a mocked adb (PLA-245; extends the PLA-244 review fixture).

No emulator or phone is contacted: `adb` and `sleep` on PATH are mocks, and every adb call is logged.
All Android results here are synthetic inputs that test the script's orchestration and safety checks.
"""
import json
import os
import signal
import stat
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCRIPT = os.path.join(ROOT, "tools/emergency-gate.sh")
BUILDS = ["debug", "ctlNoPhone", "ctlNoDialerLockTask", "ctlNoAlert", "ctlNoAlertLockTask"]

MOCK_ADB = r'''#!/usr/bin/env python3
import json, os, sys, time
d = os.environ["MOCK_DIR"]
with open(os.path.join(d, "adb.log"), "a") as f:
    f.write(" ".join(sys.argv[1:]) + "\n")
path = os.path.join(d, "state.json")
s = json.load(open(path)) if os.path.exists(path) else {"build": None, "owner": False, "pin": False, "numbers": [], "call": 0}
env = os.environ.get
CONTROLS = {"debug": "none", "ctlNoPhone": "no-phone", "ctlNoDialerLockTask": "no-dialer-lock-task",
            "ctlNoAlert": "no-alert", "ctlNoAlertLockTask": "no-alert-lock-task"}
FAULTS = {"no-phone": {"home-phone", "app-home-phone"}, "no-dialer-lock-task": {"home-phone", "app-home-phone"},
          "no-alert": {"alert-display-launch"}, "no-alert-lock-task": {"alert-display-ready", "alert-display-launch"}}
NOT_COVERED = ["sos", "setup", "pin-lockout", "supervised-session", "updater", "turn-off"]

def save():
    json.dump(s, open(path, "w"))

def verdict(check):
    control = CONTROLS.get(s["build"] or "", "none")
    if env("MOCK_VERDICT"):
        return env("MOCK_VERDICT")
    if check in FAULTS.get(control, ()):
        return "FAIL"
    return "PASS"

def gate(check, args):
    if check == env("MOCK_SLOW_CHECK"):
        open(os.path.join(d, "slow.marker"), "w").close()
        time.sleep(3)
    control = CONTROLS.get(s["build"] or "", "none")
    if check == "capabilities":
        print(f"GATE capabilities PASS control={control} notBuilt=more-emergency ready=true")
    elif check == "not-covered":
        for n in NOT_COVERED:
            print(f"GATE {n} NOT_COVERED mock")
    elif check == "more-emergency":
        print("GATE more-emergency NOT_BUILT declared")
    elif check == "alert":
        for c in ("alert-display-ready", "alert-display-launch"):
            print("GATE", c, verdict(c), "mock")
        print("GATE alert-shown INCONCLUSIVE mock unsupported")
    elif check == "telecom-emergency-call":
        if env("MOCK_STUCK_CALL"):
            s["call"] = 2
        if env("MOCK_CALL_RETURNS") and not s.get("returned"):
            s["returns_in"] = 2  # reads idle twice, then the call is back (API 36, PLA-245)
        print("GATE telecom-emergency-call", verdict(check), "mock afterHangUp=mCallState=0")
    elif check == "hangup":
        if not env("MOCK_STUCK_CALL"):
            s["call"] = 0
        print("GATE hangup", "PASS" if s["call"] == 0 else "FAIL", f"mCallState={s['call']}")
    else:
        print("GATE", check, verdict(check), "mock")

a = sys.argv[3:]  # skip -s SERIAL
if a[:1] == ["install"]:
    apk = a[-1]
    if env("MOCK_FAIL_INSTALL_CTL") and "plainphone-ctl" in apk:
        sys.exit(1)
    name = os.path.basename(apk)
    if name.startswith("plainphone-"):
        s["build"] = name[len("plainphone-"):-len(".apk")]
elif a[:1] == ["logcat"]:
    if "-d" in a and s["build"] and s["owner"]:
        print(f"reapply control={CONTROLS[s['build']]} locked=false ready=true")
elif a[:1] == ["shell"]:
    c = a[1:]
    if len(c) == 1:
        c = c[0].split(" ")
    j = " ".join(c)
    if c[:1] == ["getprop"]:
        print({"ro.build.type": "userdebug", "ro.kernel.qemu": env("MOCK_QEMU", "1"), "ro.boot.qemu": env("MOCK_QEMU", "1"),
               "ro.build.version.sdk": "36", "ro.build.fingerprint": "mock-emulator", "sys.boot_completed": "1"}.get(c[1], ""))
    elif c[:2] == ["locksettings", "get-disabled"]:
        print("false" if s["pin"] else "true")
    elif c[:2] == ["locksettings", "set-pin"]:
        s["pin"] = True
    elif c[:2] == ["locksettings", "clear"]:
        s["pin"] = False
    elif c[:2] == ["dpm", "list-owners"]:
        print("Device owner (User 0): org.plainphone/.PlainphoneAdminReceiver" if s["owner"] else "no owners")
    elif c[:2] == ["dpm", "set-device-owner"]:
        s["owner"] = True
        print("Success: Device owner set")
    elif c[:2] == ["dpm", "remove-active-admin"]:
        s["owner"] = False
    elif c[:2] == ["dumpsys", "telephony.registry"]:
        if s.get("returns_in"):
            s["returns_in"] -= 1
            if s["returns_in"] == 0:
                s["call"], s["returned"] = 2, True
        print(f"    mCallState={s['call']}")
    elif c[:2] == ["dumpsys", "window"]:
        locked = s["pin"] and bool(env("MOCK_UNLOCK_FAIL"))
        print(f"isKeyguardShowing={'true' if locked else 'false'}")
        print("  mCurrentFocus=Window{1 u0 NotificationShade}")
    elif c[:2] == ["dumpsys", "activity"]:
        print("mLockTaskModeState=LOCKED")
    elif c[:2] == ["dumpsys", "user"]:
        print("    Effective restrictions:\n      no_factory_reset\n      no_safe_boot\n    UserProperties:")
    elif "emergency-number-test-mode" in c:
        if "-a" in c and not env("MOCK_REG_FAIL"):
            s["numbers"].append(c[-1])
        elif "-c" in c:
            s["numbers"] = []
        elif "-p" in c:
            print("[" + ", ".join(["112", "911"] + s["numbers"]) + "]")
    elif c[:1] == ["pidof"]:
        print("4242")
    elif c[:2] == ["am", "instrument"]:
        gate(c[c.index("check") + 1], c)
save()
'''


def _read(path):
    with open(path) as f:
        return f.read()


class HostGateTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        bin_dir = os.path.join(self.dir, "bin")
        self.apks = os.path.join(self.dir, "apks")
        os.makedirs(bin_dir)
        os.makedirs(self.apks)
        for name, body in (("adb", MOCK_ADB), ("sleep", "#!/bin/sh\nexit 0\n")):
            p = os.path.join(bin_dir, name)
            with open(p, "w") as f:
                f.write(body)
            os.chmod(p, os.stat(p).st_mode | stat.S_IEXEC)
        for b in BUILDS + ["release-unsigned"]:
            with open(os.path.join(self.apks, f"plainphone-{b}.apk"), "w") as f:
                f.write(b)
        with open(os.path.join(self.apks, "emergencygate-debug.apk"), "w") as f:
            f.write("gate")
        self.env = dict(os.environ, PATH=bin_dir + os.pathsep + os.environ["PATH"], MOCK_DIR=self.dir)
        self.env.pop("GATE_NUMBER", None)

    def args(self, *extra):
        return ["bash", SCRIPT, "-s", "emulator-5888", "--no-build", "--apk-dir", self.apks,
                "--out", os.path.join(self.dir, "out"), *extra]

    def run_gate(self, *extra, **env):
        p = subprocess.run(self.args(*extra), env=dict(self.env, **env), capture_output=True, text=True, timeout=120)
        return p.returncode, p.stdout + p.stderr

    def adb_log(self):
        path = os.path.join(self.dir, "adb.log")
        return _read(path) if os.path.exists(path) else ""

    def state(self):
        return json.loads(_read(os.path.join(self.dir, "state.json")))

    def assert_dials_only_7357(self, log):
        for line in log.splitlines():
            if "-e number" in line:
                self.assertIn("-e number 7357 ", line + " ")
            if "emergency-number-test-mode -a" in line:
                self.assertTrue(line.endswith("-a 7357"), line)
        self.assertNotIn("KEYCODE_POWER", log)
        self.assertNotIn("check sos", log)

    # ---- finding 1: the number fence and its set-up ----

    def test_forbidden_number_is_refused_before_any_adb_call(self):
        rc, out = self.run_gate(GATE_NUMBER="999")
        self.assertEqual(2, rc, out)
        self.assertEqual("", self.adb_log())

    def test_failed_registration_never_dials(self):
        rc, out = self.run_gate(MOCK_REG_FAIL="1")
        self.assertEqual(1, rc, out)
        self.assertNotIn("check telecom-emergency-call", self.adb_log())
        self.assertIn("GATE telecom-emergency-call ERROR not dialed", out)
        self.assertIn("RESULT NOT QUALIFIED", out)

    def test_not_an_emulator_is_refused(self):
        rc, out = self.run_gate(MOCK_QEMU="0")
        self.assertEqual(2, rc, out)
        self.assertNotIn("instrument", self.adb_log())

    def test_physical_serial_is_refused(self):
        p = subprocess.run(["bash", SCRIPT, "-s", "ZY22KW6P9J", "--no-build"], env=self.env, capture_output=True, text=True)
        self.assertEqual(2, p.returncode)
        self.assertEqual("", self.adb_log())

    # ---- a full mocked qualification run ----

    def test_full_run_qualifies_except_alert_shown_and_cleans_up(self):
        rc, out = self.run_gate()
        self.assertEqual(5, rc, out)
        self.assertIn("RESULT QUALIFIED EXCEPT alert-shown INCONCLUSIVE", out)
        log = self.adb_log()
        self.assert_dials_only_7357(log)
        self.assertIn("check not-covered", log)
        self.assertIn("check cannot-start", log)
        self.assertIn("org.plainphone.test.HOLD --ez hold true", log)
        st = self.state()
        self.assertEqual((0, [], False, False), (st["call"], st["numbers"], st["pin"], st["owner"]))
        debug = _read(os.path.join(self.dir, "out", "debug.txt"))
        self.assertIn("# debug qualification run: debug", debug)
        self.assertRegex(debug, r"apk_sha256=[0-9a-f]{64}")
        self.assertIn("# restrictions=no_factory_reset,no_safe_boot", debug)
        self.assertIn("# after telecom-emergency-call: mCallState=0", debug)

    # ---- finding 3: set-up failures and partial runs never qualify ----

    def test_review_repro_control_install_failure_is_not_success(self):
        rc, out = self.run_gate(MOCK_FAIL_INSTALL_CTL="1")
        self.assertEqual(1, rc, out)
        self.assertIn("GATE provision ERROR install failed for ctlNoPhone", out)
        self.assertIn("RESULT NOT QUALIFIED", out)

    def test_missing_control_apk_is_not_success(self):
        os.remove(os.path.join(self.apks, "plainphone-ctlNoAlert.apk"))
        rc, out = self.run_gate()
        self.assertEqual(1, rc, out)
        self.assertIn("GATE provision ERROR missing APK", out)

    def test_review_repro_partial_builds_are_diagnostic(self):
        rc, out = self.run_gate("--builds", "ctlNoPhone")
        self.assertEqual(3, rc, out)
        self.assertIn("RESULT DIAGNOSTIC", out)

    def test_quick_is_diagnostic(self):
        rc, out = self.run_gate("--quick")
        self.assertEqual(3, rc, out)

    # ---- finding 4: the required schema ----

    def test_review_repro_all_inconclusive_does_not_qualify(self):
        rc, out = self.run_gate(MOCK_VERDICT="INCONCLUSIVE")
        self.assertEqual(1, rc, out)

    def test_review_repro_all_not_built_does_not_qualify(self):
        rc, out = self.run_gate(MOCK_VERDICT="NOT_BUILT")
        self.assertEqual(1, rc, out)

    # ---- finding 6: calls and cleanup ----

    def test_call_that_cannot_be_ended_stops_the_whole_matrix(self):
        rc, out = self.run_gate(MOCK_STUCK_CALL="1")
        self.assertEqual(4, rc, out)
        log = self.adb_log()
        self.assertNotIn("plainphone-ctlNoPhone.apk", log)
        st = self.state()
        self.assertEqual(([], False), (st["numbers"], st["owner"]))

    def test_call_that_comes_back_after_reading_idle_is_ended(self):
        rc, out = self.run_gate(MOCK_CALL_RETURNS="1")
        self.assertEqual(5, rc, out)
        self.assertIn("check hangup", self.adb_log())
        self.assertNotIn("mCallState=2", _read(os.path.join(self.dir, "out", "debug.txt")))
        self.assertEqual(0, self.state()["call"])

    def test_failed_unlock_stops_the_run_and_clears_the_pin(self):
        rc, out = self.run_gate(MOCK_UNLOCK_FAIL="1")
        self.assertEqual(4, rc, out)
        self.assertIn("could not unlock with the PIN", _read(os.path.join(self.dir, "out", "debug.txt")))
        self.assertIn("locksettings clear --old", self.adb_log())
        self.assertFalse(self.state()["owner"])

    def test_term_mid_run_cleans_up(self):
        p = subprocess.Popen(self.args(), env=dict(self.env, MOCK_SLOW_CHECK="lock-screen"),
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        marker = os.path.join(self.dir, "slow.marker")
        for _ in range(600):
            if os.path.exists(marker):
                break
            time.sleep(0.1)
        self.assertTrue(os.path.exists(marker), "never reached lock-screen")
        p.send_signal(signal.SIGTERM)
        out, _ = p.communicate(timeout=60)
        self.assertEqual(143, p.returncode, out)
        st = self.state()
        self.assertEqual((0, [], False, False), (st["call"], st["numbers"], st["pin"], st["owner"]))
        self.assertNotIn("plainphone-ctlNoPhone.apk", self.adb_log())


if __name__ == "__main__":
    unittest.main()
