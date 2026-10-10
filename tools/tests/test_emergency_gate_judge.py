# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
"""The host judge runs the same verdict table as GateSchemaTest (judge-cases.txt); they must agree."""
import os
import re
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import emergency_gate_judge as judge  # noqa: E402

CASES = os.path.join(ROOT, "app/emergencygate/src/test/resources/judge-cases.txt")


def cases():
    with open(CASES) as f:
        text = f.read()
    for block in re.split(r"(?m)^# case ", text)[1:]:
        rows = block.splitlines()

        def field(key):
            return next(r for r in rows if r.startswith(f"# {key} "))[len(key) + 3:].strip()

        yield rows[0].strip(), field("control"), field("expect"), [r for r in rows if r.startswith("GATE ")]


class SharedTableTest(unittest.TestCase):
    def test_every_shared_case(self):
        all_cases = list(cases())
        self.assertGreaterEqual(len(all_cases), 30)
        wrong = []
        for name, control, expect, lines in all_cases:
            outcome, reasons = judge.judge(control, judge.parse(lines))
            if outcome != expect:
                wrong.append(f"{name}: expected {expect}, got {outcome} {reasons}")
        self.assertEqual([], wrong, "\n".join(wrong))

    def test_schema_matches_kotlin(self):
        with open(os.path.join(ROOT, "app/emergencygate/src/main/kotlin/org/plainphone/emergencygate/GateSchema.kt")) as f:
            kt = f.read()
        run = re.search(r"val RUN = listOf\(([^)]*)\)", kt, re.S).group(1)
        self.assertEqual(judge.RUN, re.findall(r'"([^"]+)"', run))
        self.assertEqual(judge.NOT_COVERED, re.findall(r'^\s+"([a-z-]+)" to "', kt.split("val NOT_COVERED")[1].split(")\n")[0], re.M))


if __name__ == "__main__":
    unittest.main()
