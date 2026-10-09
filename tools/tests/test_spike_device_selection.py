"""Device-selection regression checks; adb is always replaced with a recording stub."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PHYSICAL = {
    "a3-matrix.sh": ["out"],
    "a3-probe.sh": ["out", "probe", "action=fixture"],
    "a4-crash.sh": ["out", "crash"],
    "a5-inventory.sh": ["org.example.fixture"],
    "bq4-probe.sh": ["fixture", "probe", "--no-text", "--no-shot"],
    "gm-nav.sh": [],
    "pp.sh": ["cmd", "status"],
    "pref-state.sh": ["fixture", "0"],
    "q2-setup.sh": [],
    "q2-state.sh": [],
    "release-tap.sh": [],
}
EMULATOR = [
    "spikes/bootloop-repro/tools/bl.sh",
    "spikes/emulator/tools/pp.sh",
    "spikes/grayscale/tools/pp.sh",
    "spikes/wifi-signin/tools/pp.sh",
    "spikes/wifi-signin/tools/fixture.sh",
]


class DeviceSelectionTest(unittest.TestCase):
    def setUp(self):
        # Paperclip owns scratch during agent runs; ordinary local runs use system temp.
        self.temp = tempfile.TemporaryDirectory(dir=os.environ.get("PAPERCLIP_SCRATCH_DIR"))
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        shutil.copytree(ROOT / "spikes", self.root / "spikes",
                        ignore=shutil.ignore_patterns("evidence", "apps", "dpc"))
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.log = self.root / "adb.jsonl"
        stub = self.bin / "adb"
        stub.write_text("#!/usr/bin/env python3\nimport json, os, sys\n"
                        "with open(os.environ['MOCK_ADB_LOG'], 'a') as f: "
                        "f.write(json.dumps(sys.argv[1:]) + '\\n')\n")
        stub.chmod(0o755)
        sleep = self.bin / "sleep"
        sleep.write_text("#!/bin/sh\nexit 0\n")
        sleep.chmod(0o755)
        self.env = {**os.environ, "PATH": f"{self.bin}:/usr/bin:/bin",
                    "MOCK_ADB_LOG": str(self.log), "MODE": "compare",
                    "PORTAL_MODEFILE": str(self.root / "portal-mode")}
        self.env.pop("SERIAL", None)

    def run_script(self, path, args, serial=None, source=False):
        self.log.unlink(missing_ok=True)
        env = dict(self.env)
        if serial is not None:
            env["SERIAL"] = serial
        script = str(self.root / path)
        command = (["/bin/bash", "-c", 'source "$1" "$2" && A shell fixture',
                    "test", script, *args] if source else ["/bin/bash", script, *args])
        try:
            result = subprocess.run(command, cwd=self.root, env=env,
                                    capture_output=True, timeout=2)
            code = result.returncode
        except subprocess.TimeoutExpired:
            code = -1
        calls = [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []
        return code, calls

    def test_physical_scripts_refuse_missing_empty_or_emulator_serial_before_adb(self):
        for name, args in PHYSICAL.items():
            for serial in (None, "", "emulator-5590", "emulator-5604", "emulator-5998"):
                with self.subTest(script=name, serial=serial):
                    code, calls = self.run_script("spikes/device-moto/tools/" + name, args, serial)
                    self.assertNotEqual(code, 0)
                    self.assertEqual(calls, [], "refused selection must never reach adb")

    def test_physical_helper_forwards_explicit_serial(self):
        code, calls = self.run_script("spikes/device-moto/tools/pp.sh",
                                      ["cmd", "status"], "fixture-device")
        self.assertEqual(code, 0)
        self.assertTrue(calls)
        self.assertTrue(all(call[:2] == ["-s", "fixture-device"] for call in calls))

    def test_setup_passes_explicit_serial_to_child_helper(self):
        code, calls = self.run_script("spikes/device-moto/tools/q2-setup.sh", [], "fixture-device")
        self.assertEqual(code, 0)
        self.assertTrue(any("broadcast" in call for call in calls))
        self.assertTrue(all(call[:2] == ["-s", "fixture-device"] for call in calls))

    def test_emulator_scripts_refuse_physical_and_protected_serials(self):
        for path in EMULATOR:
            serials = ["fixture-device", "emulator-5590", "emulator-5604"]
            if "grayscale" in path:
                serials.append("emulator-5640")
            for serial in serials:
                with self.subTest(script=path, serial=serial):
                    fixture = path.endswith("fixture.sh")
                    code, calls = self.run_script(path, [serial, "reset"] if fixture else [serial],
                                                  source=not fixture)
                    self.assertNotEqual(code, 0)
                    self.assertEqual(calls, [])

    def test_emulator_scripts_forward_explicit_emulator(self):
        for path in EMULATOR:
            with self.subTest(script=path):
                fixture = path.endswith("fixture.sh")
                code, calls = self.run_script(path, ["emulator-5998", "reset"] if fixture else ["emulator-5998"],
                                              source=not fixture)
                self.assertEqual(code, 0)
                self.assertTrue(calls)
                self.assertTrue(all(call[:2] == ["-s", "emulator-5998"] for call in calls))


if __name__ == "__main__":
    unittest.main()
