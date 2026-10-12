# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# THROWAWAY (PLA-458): host regression test, never contacts a device.
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class BuildFailureTest(unittest.TestCase):
    def test_ui_helper_rejects_wrong_avd_before_ui_commands(self):
        helper = Path(__file__).resolve().parents[1] / "tools" / "ui.py"
        with tempfile.TemporaryDirectory(dir=os.environ["PAPERCLIP_RUN_SCRATCH_DIR"]) as temporary:
            fake_adb = Path(temporary) / "adb"
            fake_adb.write_text("#!/bin/sh\necho another-avd\n")
            fake_adb.chmod(0o755)
            result = subprocess.run([os.sys.executable, "-B", str(helper), "emulator-5984", "dump"],
                                    env=dict(os.environ, PATH=temporary), capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("private pla458-a35", result.stderr)

    def test_ui_helper_rejects_non_spike_devices_before_adb(self):
        helper = Path(__file__).resolve().parents[1] / "tools" / "ui.py"
        for serial in ("physical-device", "emulator-5590", "emulator-5604", "emulator-5960;bad"):
            result = subprocess.run([os.sys.executable, "-B", str(helper), serial, "dump"],
                                    env=dict(os.environ, PATH=""), capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("private emulator-59xx", result.stderr)

    def test_compiler_failure_stops_before_dex(self):
        scratch = os.environ["PAPERCLIP_RUN_SCRATCH_DIR"]
        with tempfile.TemporaryDirectory(dir=scratch) as temporary:
            root = Path(temporary)
            spike = root / "spike"
            shutil.copytree(Path(__file__).resolve().parents[1], spike,
                            ignore=shutil.ignore_patterns("build", "evidence", "__pycache__"))
            binaries = root / "bin"
            tools = root / "sdk" / "build-tools" / "35.0.0"
            binaries.mkdir()
            tools.mkdir(parents=True)
            marker = root / "dex-invoked"
            compiler = binaries / "javac"
            compiler.write_text("#!/bin/sh\nexit 42\n")
            compiler.chmod(0o755)
            dex = tools / "d8"
            dex.write_text(f"#!/bin/sh\ntouch '{marker}'\nexit 43\n")
            dex.chmod(0o755)
            environment = dict(os.environ, ANDROID_HOME=str(root / "sdk"),
                               PATH=str(binaries) + os.pathsep + os.environ["PATH"])
            result = subprocess.run(["bash", "build.sh"], cwd=spike, env=environment,
                                    capture_output=True, text=True)
            self.assertEqual(42, result.returncode, result.stdout + result.stderr)
            self.assertFalse(marker.exists(), "d8 ran after javac failed")


if __name__ == "__main__":
    unittest.main()
