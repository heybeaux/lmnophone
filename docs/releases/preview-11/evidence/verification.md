# Preview 11 verification

Author evidence for [PLA-368](/PLA/issues/PLA-368). Lead owns the one opposite-model
review and the spare installation. No physical device was accessed.

## Built

Base `release/preview-10` at `bfb7906`. Timing test red `2675ad1`, instrumentation
`736cb2d`; narrow-query tests red `9069f47`, implementation `7b5d7c4`; version and
unchanged gate at `f67a2fa`. Red commits precede their fixes. The timing red test
fails its missing-label assertion; the narrow-query red tests fail compilation
because the new port method does not yet exist. Logs are included.

Run from `app/` with JDK 17 and Android SDK 36:

```sh
./gradlew :device:test :plainphone:test :plainphone:assembleDebug
python3 -m unittest discover -s tools/preview-11 -v
```

Both debug and release variants pass: **646 device + 138 app tests per variant**,
zero failures, errors or skipped tests. **7 upgrade-tool tests pass**. No unrelated
baseline failure was encountered. Existing compiler warnings remain in unchanged
storage/authority/update code; no checks were disabled or goldens recorded.

Timing labels aggregate package query time without logging package identities:
`read/verify.HIDES.hiddenPackages`, `read/verify.HIDES.shownPackages`, and
`read/verify.NEVER_HIDE_GUARD.shownPackages` (also available during guard repair).
The unchanged total stage intervals include these nested intervals.

The HIDES implementation formerly requested full package state even where it
used only hidden state. The new internal port query preserves the installed-user
lookup and live `isApplicationHidden` call, omitting the unused enabled-setting
and suspension queries. It runs before a hide, after the write, and in independent
final verification. **No cache and no skipped verification.** Null still means
confirmed absence; unreadable live queries throw and fail the stage. Visible,
unsuspended and never-hide checks still use full package state. Host tests prove
hidden-expected/visible-actual, visible-expected/hidden-actual and unreadable
hidden/visible queries fail at both apply and final verification. A second pass
must re-query every hidden target. Cross-module API signatures are unchanged;
the internal device seam is recorded in `docs/mvp/lanes.md`.

This preserves device-lockdown order/readback and locked-boot repair, and the
essential-calling floor. No package-event coalescing, D1 wiring, UI, emergency,
Home, recovery or checking-screen fallback change is included.

VersionCode **11**, `0.1.0-preview-11`, `org.plainphone`, testOnly.
APK SHA-256: `9ec7321905ddf09b5638acee03f19c8fba06d6473760529d8414dc0977ffaa2f`.
Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
[Candidate APK, gzip](/api/attachments/cf89f831-4c36-4b21-99af-f723fb338409/content).
Downloaded from the attachment, decompressed and hash-verified after upload.

## Installed (emulator)

Retained API 35 AVD `pla363-policy-a35`, target `emulator-5880`. Preview 10 was
read back first. Installed a version-10 timing-only candidate in place, then
preview 11 at **2026-10-11 23:42:50 UTC**, without uninstall, reset or reprovision.
Version 11 readback and the pulled installed APK hash match the candidate.
First-install time remains `2026-10-11 14:05:10`.

The complete before/final safety summaries are exactly equal: Device Owner,
lock-task list/features, active restrictions, count preference, first-install
time and all hidden/disabled/suspended package sets. Home is LOCKED; adb remains
reachable and `no_debugging_features` is absent. See the three safety JSON files.

## Exercised

The versioned preview-10 gate was copied with **only version identifiers changed**.
Gate **exit 0**: More → Emergency prefills exactly `911`; Phone accepts exactly
`911`; both routes report zero current Telecom calls. Digits cleared, Home
restored, final version/owner/LOCKED readback passes. Both keypad screenshots
were visually checked and show no personal data. No call intent, call button or
SOS gesture was used.

The accepted preview-9 host harness restores adb root after the gate reboot to
work around this AVD's shell-user `/sdcard` EFAULT. No gate assertion is replaced
or skipped; the app remains at its ordinary app UID. Reproduce from repo root:

```sh
SKIP_INSTALL=1 python3 docs/releases/preview-9/evidence/gate_on_root_avd.py \
  app/tools/preview-11 emulator-5880 \
  app/plainphone/build/outputs/apk/debug/plainphone-debug.apk <evidence-directory>
```

## Timings: observations only

One no-PIN reboot/unlocked sample per candidate on a shared host:

| User-unlocked stage (ms) | Build 10 + timing labels | Build 11 |
|---|---:|---:|
| Whole pass | 590 | 511 |
| Session start | 475 | 396 |
| HIDES read hidden / shown | 19 / 43 | 2 / 37 |
| HIDES verify hidden / shown | 68 / 35 | 12 / 4 |
| Guard read / verify shown | 8 / 45 | 62 / 92 |

These samples locate query work; they **do not establish speedup**, PIN-unlock
latency, or spare performance. Guard query cost varied substantially. Raw stage
lines are in `timing-baseline.txt` and `build11.txt`. Lead must measure the spare.

[Release notes](/PLA/cases/PLA-C52) and [emulator gate](/PLA/cases/PLA-C53).
Known preview limitations are carried forward in [notes](../NOTES.md).
