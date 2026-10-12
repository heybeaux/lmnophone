#!/usr/bin/env bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# Usage: upgrade-check.sh <owned-emulator-or-approved-spare> <APK> <output-dir> [label]
# Only the sole spare driver may set PLAINPHONE_SPARE_DRIVER=1 PLAINPHONE_SPARE_SERIAL=<spare-serial>.
# SKIP_INSTALL=1 carries forward an earlier install. Always restart and assert both routes.
# Emulator negative control: PLAINPHONE_CHECK_SELF_TEST=1 EXPECT_DIAL=912.
# The override changes only the expected result; digits typed are always 9, 1, 1.
set -euo pipefail
OUT=${3:?output directory required}
mkdir -p "$OUT"
# pipefail preserves Python failure through tee. Python emits PASS/FAIL and exit=N.
python3 -u "$(dirname "$0")/upgrade_check.py" "$@" 2>&1 | tee "$OUT/${LOG:-upgrade-check.txt}"
