#!/bin/bash
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# Build 68 (slot 8c, copied from 67) readbacks on top of spare_lib.sh (sole spare driver): lock-task packages,
# Play's per-user state, package counts and HOME. Counts and package names only.
locktask_pkgs(){ a shell dumpsys device_policy | grep -m1 -o 'LockTaskPolicy {mPackages= [^;]*' | sed 's/.*mPackages= //' | tr ',' '\n' | tr -d ' ' | sort | paste -sd, -; }
live_locktask(){ a shell dumpsys activity activities | grep -m1 -o 'mLockTaskModeState=[A-Z]*'; }
play_state(){ a shell dumpsys package com.android.vending | grep -m1 'User 0:' | grep -oE '(installed|hidden|suspended|stopped|enabled)=[a-z0-9]*' | paste -sd' ' -; }
extra(){ echo "locktask_packages=$(locktask_pkgs)"; echo "play_user0=$(play_state)"
  echo "packages_all/visible=$(a shell pm list packages -u | wc -l | tr -d ' ')/$(a shell pm list packages | wc -l | tr -d ' ')"
  echo "home_resolve=$(a shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
  echo "play_market_resolve=$(a shell cmd package resolve-activity --brief -a android.intent.action.VIEW -d 'market://details?id=org.example' | tail -1)"; }
