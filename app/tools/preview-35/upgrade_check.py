# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Reuse the accepted no-call emergency/recovery gate with build 35's exact version."""
import importlib.util
from pathlib import Path
import sys

spec = importlib.util.spec_from_file_location('preview26_gate', Path(__file__).parents[1] / 'preview-26' / 'upgrade_check.py')
previous = importlib.util.module_from_spec(spec)
spec.loader.exec_module(previous)
previous.Gate.VERSION = 35
Gate = previous.Gate
GateFailure = previous.GateFailure

if __name__ == '__main__': sys.exit(previous.main())
