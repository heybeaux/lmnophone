# Preview 6 A1 freeze

Prepared for [PLA-341](/PLA/issues/PLA-341). Lead owns the single review under
[PLA-339](/PLA/issues/PLA-339), then the assigned spare driver installs.
This record is emulator-only, not physical acceptance.

## Built

Merged `origin/mvp/device`, rather than cherry-picking: all commits after the
accepted `b872680` meet the issue's merge rule:

- `f6466c4`: only `tools/emergency-gate.sh` (late-call/unreadable-cleanup correction).
- `b7af03d`: four A1 regression tests, recorded red by the author.
- `e9a9629`: catches IOException at calling-screen record load/save boundaries.

A1 was completed in [PLA-343](/PLA/issues/PLA-343), replacing cancelled
[PLA-330](/PLA/issues/PLA-330). Merge source is
`8c3674b15f0080f2c8f43627336c35f8713be0d5`, parents `78dfdd0` and `e9a9629`.
No conflicts or UI changes. No launch-order change.

- VersionCode 6; versionName `0.1.0-preview-6`; `org.plainphone`; testOnly.
- APK SHA-256: `4806bada7b7bd0f827312972b8ff4a98af361df5c5299d625fa25a4a41cd5ace`.
- Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
- [Gzipped candidate APK](/api/attachments/045ed326-dcb5-4b6b-8633-ceae82a6beba/content).

From `app/`, JDK 17:

```
./gradlew --offline :device:testDebugUnitTest :policy:test :plainphone:testDebugUnitTest :plainphone:assembleDebug
./gradlew --offline :device:testDebugUnitTest --rerun :policy:test --rerun :plainphone:testDebugUnitTest --rerun
```

Both commands passed. Forced rerun: device **606**, policy **636**, app **119**;
**1,361 tests**, zero failures/errors/skips. Four new A1 regressions pass.
The compiler reports a nullable `File.parentFile` warning in the new test;
no check was disabled. UI **191** including Paparazzi, tool **7**, and lint
results carry forward unchanged from [prep](../verification.md). UI was not rerun.

## Installed

Owned API 35 AVD `pla306-policy-a35`, `emulator-5862` only. Its earlier build-6
prep was downgraded **in place**, with testOnly `adb install -r -d -t`, to the
archived build-5 APK. Pulled installed bytes match exactly
`f43b93978c8994bc25b44ec66bc8d40532a79cead43becd69fed87b3e048204c`.
No uninstall, reset, reprovision, or physical-device commands.

`app/tools/preview-6/upgrade-check.sh` upgraded build 5 to this candidate at
**2026-10-11 17:37:44 UTC** (`lastUpdateTime=2026-10-11 10:37:44`, AVD UTC−07).
PackageManager readback confirms version 6/testOnly. Installed APK bytes also
match the candidate's SHA-256. First install remains `2026-10-10 22:29:07` local.

Readbacks before upgrade, after upgrade/restart, and after recovery retain:

- Device Owner, LOCKED, same nine lock-task packages, features 53.
- Same restriction sections; no new restriction, no_debugging_features absent.
- Same 13 hidden / 0 suspended / 3 disabled packages (244 package records read).
- Counts ON before and immediately after installation; restored ON after testing.
- adb reachable. Complete final safety summary equals the fresh build-5 baseline.

Raw snapshots and parsed comparisons are in `../emulator/freeze-*`.
The reader is the unchanged `../safety_readback.py`.

## Exercised

First run ([log](gate-counts-on/upgrade-check.txt), exit **1**) passed restart,
full readback and More → Emergency exact 911/zero current calls, then reproduced
the established counts-on Phone-selector failure. This is retained, not relabeled.
After confirming preference retention, counts were temporarily switched OFF
through Phone settings, as in the accepted build-5/prep procedure.

The same gate reran with `SKIP_INSTALL=1` ([log](gate/upgrade-check.txt)), exit **0**:
Home followed full readback, both routes exactly **911**, zero current Telecom
calls on each route, digits cleared, final owner/version/LOCKED checks passed.
No Call button, live call, or SOS action was used.

`home-phone-open-kill` was repeated once with the reviewed probe and verified
`su 0 kill -9`. PID 944 died. Phone remained foreground and LOCKED for the entire
~11 second sample; no new app PID appeared in that sample. This proves retained
containment, **not** a recovery relaunch. Zero current calls. The wrapper differs
from prep only in paths so it preserves earlier evidence.

Counts were restored ON through the UI; final durable preference and safety
readback pass. All four new screenshots were inspected before sharing and contain
no personal data. Host paths in the build log are replaced with `<worktree>`.

## Limits and next action

The existing external-dialer identification limit and pending fallback containment
remain. A failed record write retains only in-process state. No physical-device
or full D1 acceptance is claimed. Lead reviews this A1 delta and the accepted
prep candidate once; the existing assigned driver then owns spare installation.

Durable records: [passing gate](/PLA/cases/PLA-C36),
[counts-on selector failure](/PLA/cases/PLA-C35), and
[freeze evidence bundle](/api/attachments/ec0f2b13-7632-475c-940f-cbcf78313eb5/content).
