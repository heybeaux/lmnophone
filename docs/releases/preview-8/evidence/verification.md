# Preview 8 preparation

Prepared for [PLA-357](/PLA/issues/PLA-357), under [PLA-356](/PLA/issues/PLA-356).
Author testing only. The Lead owns the single review and the assigned driver owns
the spare install. No physical device was accessed.

## Built

Base `f0b9945` is current `origin/release/preview-7`, including the recorded spare
install. Red test commit `21714a4` fails compilation because the new dispatcher
and enum value do not exist. Fix `c7aeddd` adds `EMERGENCY_911` and shares the calling
action dispatcher between MainActivity's shell effect and reopen path. Phone,
checking-screen Emergency and in-call callbacks keep their existing behavior.
The actual file store reloads the new value; unknown/obsolete names still yield
null. A failed save still opens the keypad and retains in-process recovery.
Version/tools commit `4159241` is the built code head. No policy plan, UI, count
source, dependency or public module interface changes.

VersionCode 8 (`0.1.0-preview-8`), `org.plainphone`, testOnly.
APK SHA-256: `eca349611bb75566ed1e9f1dfb9b45f70085b94070ed66f9c40a98b19230715f`.
Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
[Candidate APK, gzip](/api/attachments/e8417827-7cbc-4dc7-97d0-81f5cdc2ed92/content).
The uploaded gzip was downloaded and its decompressed APK hash independently
matched the candidate. The issue attachment and its work product were confirmed by API readback.

From `app/`, JDK 17:

```
./gradlew :plainphone:testDebugUnitTest :ui:testDebugUnitTest :ui:verifyPaparazziDebug :device:testDebugUnitTest :policy:test :ui:lint :device:lint :plainphone:lint :plainphone:assembleDebug --continue
python3 -m unittest discover -s tools/preview-8 -v
```

| Suite | Tests | Failures / errors / skipped |
|---|---:|---|
| App | 137 | 0 / 0 / 0 |
| UI, including Paparazzi verify | 191 | 0 / 0 / 0 |
| Device | 632 | 0 / 0 / 0 |
| Policy | 636 | 0 / 0 / 0 |
| Upgrade tool | 7 | 0 / 0 / 0 |

Gradle: 1,596 tests, successful build, no baseline failures. No tests disabled,
skipped or golden images recorded. Lint: zero errors; app 7, UI 23, device 3
warnings. Logs and parsed counts accompany this report.

## Installed

Owned API 35 AVD `pla353-ui-a35`, target `<emulator>`, reused with build 7 and its
data intact. The prescribed `tools/preview-8/upgrade-check.sh` installed version 8
in place at **2026-10-11 20:09:17 UTC** (`lastUpdateTime` 13:09:17, AVD UTC−07).
No uninstall, reset or reprovision. Installed APK pulled from the emulator matches
the candidate hash exactly. First install remains `2026-10-11 12:12:01` local.

Before, immediately before install, after upgrade and after all crash tests retain:

- Same Device Owner; LOCKED; same nine lock-task packages; features 53.
- Same active user restriction sections; no new restriction; no_debugging_features absent.
- Same 13 hidden, zero suspended and three disabled packages; 244 records read.
- Same firstInstallTime and saved count preference OFF; adb reachable.

Safety summaries contain the complete before/after package sets, lock-task list,
restriction sections and preference. `readback.py` can repeat the read-only checks.
No new restriction or newly hidden/suspended/disabled package was observed.

## Exercised

Upgrade gate **exit 0**: Home follows full readback after restart; More → Emergency
prefills exactly `911`; Home → Phone accepts exactly `911` via keypad taps; zero
current Telecom calls on both routes. Digits cleared afterward; final owner,
version, testOnly and LOCKED pass. No call intent, Call button or SOS gesture.

**Timing matters in this reproduction.** The first prescribed build-7 attempt
waited until 911 was visible, then used `su 0 kill -9 <LMNOPhone pid>`. The kill
worked, but the dialer remained foreground and LOCKED with 911. No new LMNOPhone
process or reopen appeared in 45 seconds. This attempt does not demonstrate the
bug or recovery; its failed observation log and screenshot are retained.
The same settled-keypad kill on build 8 also retained 911 and LOCKED without a
reopen for 45 seconds, with zero current calls. Neither is counted as a recovery
PASS. The ordinary Home action restarted LMNOPhone afterward.

A separately labelled immediate-tap/kill run reproduced the process-death race.
First confirm the ordinary route displays 911 and inspect its persisted screen;
clear it, return Home → More, then send the Emergency tap and root kill in one
shell command. Neither a force-stop nor a policy/test-hook change is used.

| Immediate tap/kill | Build 7 | Build 8 |
|---|---|---|
| Ordinary route before kill | 911, record PHONE | 911, record EMERGENCY_911 |
| Process | New PID after kill | New PID after kill |
| Recovery log | `reopening PHONE` | `reopening EMERGENCY_911` |
| Recovered keypad | Empty (`Phone Number` hint) | Exactly `911` |
| Lock task | LOCKED | LOCKED |
| Telecom current calls | 0 | 0 |

Logs and before/after recovery screenshots accompany these observations. This
proves recovery for the immediate-tap race on this AVD; it is not a claim that
every kill triggers recovery or that a host test is a physical-device test.
`crash_recovery.py` records the two timings separately, fails a race without a
reopen, and never reports a settled no-recovery observation as a recovery PASS.

## Handoff

All shared images were visually inspected: system dialer only, no personal data.
New evidence omits device serials and host paths. The other preview-7 limitations
remain in [notes](../NOTES.md), including external-dialer recovery, record-I/O
persistence limits, unwired controls and SMS/MMS-only counts. The checking-screen
emergency fallback remains host-tested only. No full D1 or spare acceptance claim.

Lead: review the code/tests/version delta and these results once, then route the
same attached bytes to the sole spare driver. No spare crash test is requested.

Durable records: [emulator gate](/PLA/cases/PLA-C46) and
[draft release notes](/PLA/cases/PLA-C45).
