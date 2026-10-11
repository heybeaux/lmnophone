#!/usr/bin/env python3
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
"""Judges one build's GATE lines for tools/emergency-gate.sh (PLA-245).

The same rules as GateSchema.kt; both run app/emergencygate/src/test/resources/judge-cases.txt.

    emergency_gate_judge.py <control> <result-file>   -> prints "<OUTCOME>" then one reason per line
"""
import re
import sys

NOT_COVERED = ["sos", "setup", "pin-lockout", "supervised-session", "updater", "turn-off"]
OPTIONAL = {"more-emergency"}
RUN = ["home-phone", "app-home-phone", "more-emergency", "alert-display-ready", "alert-display-launch",
       "alert-shown", "telecom-emergency-call", "lock-screen", "locked-boot", "boot-checking"]
REAL = ["capabilities"] + RUN + ["cannot-start"] + NOT_COVERED
CONTROL = ["capabilities"] + RUN + NOT_COVERED
# The alert launch FAILs the gate classifies as the build's behaviour (GateJudge.alertLaunched, PLA-296 R4).
ALERT_LAUNCH_CAUSES = {"alert-display-launch": ("unavailable:", "blocked by lock task:")}
# control: (checks that must FAIL, checks that may also fail because of the fault,
#           causes a must-FAIL check's detail must start with for its FAIL to count as detection)
CONTROLS = {
    "no-phone": ({"home-phone", "app-home-phone"}, {"telecom-emergency-call", "boot-checking"}, {}),
    "no-dialer-lock-task": ({"home-phone", "app-home-phone"}, {"telecom-emergency-call", "boot-checking"}, {}),
    "no-alert": ({"alert-display-launch"}, {"alert-display-ready"}, ALERT_LAUNCH_CAUSES),
    "no-alert-lock-task": ({"alert-display-ready", "alert-display-launch"}, set(), ALERT_LAUNCH_CAUSES),
}
LINE = re.compile(r"^GATE (\S+) (PASS|FAIL|ERROR|INCONCLUSIVE|NOT_BUILT|NOT_COVERED) ?(.*)$")


def parse(lines):
    out = []
    for line in lines:
        m = LINE.match(line.strip())
        if m:
            out.append((m.group(1), m.group(2), m.group(3)))
    return out


def field(detail, key):
    for word in detail.split(" "):
        if word.startswith(key + "="):
            return word[len(key) + 1:]
    return None


def allowed(check, verdict, not_built):
    if check in NOT_COVERED:
        return None if verdict == "NOT_COVERED" else f"{verdict} where only NOT_COVERED is allowed"
    if verdict == "PASS":
        return None
    if verdict == "INCONCLUSIVE" and check == "alert-shown":
        return None
    if verdict == "NOT_BUILT" and check in OPTIONAL and check in not_built:
        return None
    if verdict == "NOT_BUILT":
        return "NOT_BUILT but the build does not declare it optional"
    return f"{verdict} is not allowed"


def judge(control, results):
    caps = [r for r in results if r[0] == "capabilities"]
    one = caps[0] if len(caps) == 1 else None
    declared = field(one[2], "control") if one and one[1] == "PASS" else None
    not_built = {x for x in (field(one[2], "notBuilt") or "").split(",") if x and x != "-"} if one else set()
    real = control == "none"
    spec = CONTROLS.get(control)
    errors = [f"{c} ERROR {d}".strip() for c, v, d in results if v == "ERROR"]
    if not real and spec is None:
        return "ERROR", [f"unknown control '{control}'"]
    if declared is None:
        return "ERROR", ["no passing capabilities line: build not identified"] + [f"{c} ERROR {d}" for c, v, d in results if v == "ERROR"]
    if declared != control:
        return "ERROR", [f"build reports control={declared}, expected {control}"]

    required = REAL if real else CONTROL
    counts = {}
    for c, _, _ in results:
        counts[c] = counts.get(c, 0) + 1
    incomplete = []
    missing = [c for c in required if c not in counts]
    unknown = [c for c in counts if c not in required]
    twice = [c for c, n in counts.items() if n > 1]
    if missing:
        incomplete.append("missing: " + ", ".join(missing))
    if unknown:
        incomplete.append("unknown: " + ", ".join(unknown))
    if twice:
        incomplete.append("reported twice: " + ", ".join(twice))

    by_check = {}
    for r in results:
        by_check[r[0]] = r  # last wins, like associateBy
    failed, inconclusive = [], []
    for check, (_, verdict, detail) in by_check.items():
        if check not in required or check == "capabilities" or verdict == "ERROR":
            continue
        if spec and check in spec[0]:
            causes = spec[2].get(check)
            if verdict != "FAIL":
                failed.append(f"{check} {verdict}: the control's fault was not detected")
            elif causes is not None and not detail.startswith(causes):
                errors.append(f"{check} FAIL without a recognised cause: {detail}".strip())
            continue
        if spec and check in spec[1] and verdict == "FAIL":
            continue
        problem = allowed(check, verdict, not_built)
        if problem is None:
            if check == "alert-shown" and verdict == "INCONCLUSIVE":
                inconclusive.append("alert-shown INCONCLUSIVE")
        elif verdict == "FAIL":
            failed.append(f"{check} FAIL {detail}".strip())
        else:
            incomplete.append(f"{check}: {problem}")

    if real:
        reasons = failed + errors + incomplete + inconclusive
        if failed:
            return "FAIL", reasons
        if errors:
            return "ERROR", reasons
        if incomplete:
            return "INCOMPLETE", reasons
        if inconclusive:
            return "INCONCLUSIVE", reasons
        return "PASS", reasons
    not_detected = [f for f in failed if f.endswith("not detected")]
    prerequisite = [f for f in failed if f not in not_detected]
    reasons = errors + ["prerequisite " + p for p in prerequisite] + incomplete + not_detected
    if errors or prerequisite:
        return "ERROR", reasons
    if incomplete:
        return "INCOMPLETE", reasons
    if not_detected:
        return "NOT_DETECTED", reasons
    return "DETECTED", reasons


def main(argv):
    if len(argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    try:
        with open(argv[2]) as f:
            lines = f.read().splitlines()
    except OSError as e:
        print("ERROR")
        print(f"result file unreadable: {e}")
        return 0
    outcome, reasons = judge(argv[1], parse(lines))
    print(outcome)
    for r in reasons:
        print(r)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
