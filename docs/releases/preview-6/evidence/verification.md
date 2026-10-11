# Preview 6 preparation

Prepared for [PLA-340](/PLA/issues/PLA-340). The Lead owns the single freeze
review under [PLA-339](/PLA/issues/PLA-339); this is not self-approval or physical
device acceptance.

## Built

Source/merge: `b6bba39ae72e51894fa758cdf7ef370b85077974`, a real merge of exactly
`91636a2c94234bb5a31fcbd600bad96aa7b916c3` and
`b872680c275401cb595630ac06cec695c5ed8d94`. No newer device commit is included.
`release/preview-6` is pushed. Version 6, `0.1.0-preview-6`, `org.plainphone`,
testOnly, original debug signer retained. The upgrade script is mode 100755.

- APK SHA-256: `8a2dbbc921a7159a2cb31989d346fa347de764ea14c08a11d33ad3e4bd1e7795`.
- Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
- [Gzipped APK](/api/attachments/1bbe2338-8690-470a-8d3b-0581c395f072/content).
- Every conflicted file and chosen side: [conflicts.md](conflicts.md).

The device recovery/lock-task implementation is retained. The merge keeps the
preview's restriction-free starter-list provider, shell, branding, counts and
ready-run app-list refresh. Main uses the device's KEYGUARD-first entry and
calling-screen journal. More still opens the ordinary dialer with 911 prefilled.
Two explicit permission readbacks at the Telecom calls resolve lint's inability
to follow `phoneStateReadable()`; no lint rule is disabled. Device-only metadata
claiming More Emergency is NOT_BUILT is omitted because preview implements it.

All build-5 Home/More/counts goldens remain. The device merge adds Home fixtures
at 130/150/180% and a four-app fixture: 59 imported pre-calm goldens initially
failed, then were intentionally recorded for the retained calm Home. Every
changed image was inspected. Some long-notice fixtures expose build 5's existing
upper-row clipping; this merge does not change that layout. The two conflicting
confirmSending goldens use the device side, matching the already-reviewed
sending-state implementation. The preview never enters that state: its Send
and Ask routes show NotInBuild, proven by the existing TestBuildNavigatorTest.
Imported trusted presentation stays unexposed; no new trusted flow is wired.

## Host checks

JDK 17, offline, Gradle commands from `app/`:

```
./gradlew --offline :plainphone:testDebugUnitTest :ui:testDebugUnitTest :ui:verifyPaparazziDebug :device:testDebugUnitTest :policy:test :ui:lint :device:lint :plainphone:lint :plainphone:assembleDebug --continue
```

After the recorded initial merge failures, only affected app and UI checks were
rerun. Final results are in [suite-counts.json](suite-counts.json).

| Suite | Tests | Failures/errors/skips |
|---|---:|---|
| App | 115 | 0 / 0 / 0 |
| UI including Paparazzi | 191 | 0 / 0 / 0 |
| Device | 606 | 0 / 0 / 0 |
| Policy | 636 | 0 / 0 / 0 |
| Upgrade tool | 7 | 0 / 0 / 0 |

Paparazzi verify passes. APK assembly passes. Lint errors are zero; app/UI/device
warnings are 8/19/3. The five former Pla241ReviewTest failures pass. The first
merge had 61 screenshot failures (listed individually in initial-ui-failures.txt)
and two MissingPermission lint errors; their failing logs are retained. No
check was disabled or skipped. Imported historical evidence contains trailing
whitespace; it was not rewritten for this merge.

## Installed — emulator only

Owned API 35 AVD `pla306-policy-a35`, `emulator-5862`, 360×640 dp.
The installed build-5 APK was pulled and hashed before any mutation: exact
`f43b93978c8994bc25b44ec66bc8d40532a79cead43becd69fed87b3e048204c`.

Preview-6 upgrade-check.sh installed build 6 in place at **2026-10-11 17:18:42 UTC**
(PackageManager `lastUpdateTime=2026-10-11 10:18:42`, AVD UTC−07:00).
No uninstall, reset or reprovision. Safety snapshots before, after install/reboot,
and after recovery preserve:

- Device Owner `org.plainphone/.PlainphoneAdminReceiver`; LOCKED.
- The same nine lock-task packages, features 53.
- No device-policy user/global restrictions; the same three Android profile
  restrictions (`no_add_clone_profile`, `no_add_managed_profile`,
  `no_add_private_profile`). Full device_policy/user dumps retain all scopes,
  including guest defaults. Summary field `restriction_names_in_dump` also
  contains supported-name tables and is not a claim that all names are enforced.
- The same 13 hidden, zero suspended, three disabled packages out of 244 read.
  Exact lists are in the package-flags JSON. No new exclusion or restriction.
- `firstInstallTime=2026-10-10 22:29:07` AVD local and counts preference true.
- adb reachable; `no_debugging_features` absent.

Readback: [after install](emulator/after-install-safety.json),
[final](emulator/final-safety.json). Reproducible read-only comparison:
[safety_readback.py](safety_readback.py).

## Exercised

The first gate passed restart/full readback and More's exact 911 prefill, with
zero current Telecom calls, then failed the known counts-on Phone selector:
`find non-call tap target ^Phone(?:,.*)?$`. The preserved log is
[gate-counts-on](emulator/gate-counts-on/upgrade-check.txt). Count preference
retention was read back before any test-fixture adjustment.

Counts were temporarily switched off through the UI, as in build 5. The same
upgrade-check.sh ran with SKIP_INSTALL=1 and **exited 0**: restart to Home after
full readback, More Emergency prefilled exactly 911, Phone typed exactly 911,
zero current Telecom calls on each route, digits cleared afterward. No call
control was pressed. [Gate log](emulator/gate/upgrade-check.txt).

Both required PLA-324 scenarios use the reviewed probe with `su 0 kill -9` in
[recovery_probe.py](recovery_probe.py). No force-stop or call is used.

| Scenario | Observed result |
|---|---|
| home-phone-open-kill | Old app process died; Phone remained top and LOCKED throughout the ~11 second sample. App did not restart within that sample; this proves retained containment, not a recovery relaunch. Zero calls. |
| checking-emergency-kill | Old process died; a new process completed the ready pass, brought Home forward from NONE, then reopened EMERGENCY_KEYPAD inside LOCKED. Final top is EmergencyDialer. Zero calls. |

The initial Phone wrapper incorrectly required a new PID within 10 seconds even
when the assigned expected result (Phone still top and LOCKED) held. That extra
assertion failed; the sample is retained under recovery/initial-phone-sample.
The wrapper was corrected to assert that the killed PID is gone, distinguish
restart from retained containment, and check the requested top/LOCKED/no-call
result. The Phone scenario was then repeated. No app change was made.

Counts were restored ON and verified in the actual switch and durable preference.
Home/More screenshots at 100/200%, plus More scrolled to the bottom at 200%, were
inspected before sharing. They match build 5's presentation; the comparison JSON
excludes the changing clock/system bars. Normal text size was restored. All new
screenshots contain only synthetic data. No physical phone was touched.

## Handoff limits

See [draft notes](../NOTES.md). External dialers remain an identification limit;
fallback containment is pending, and A1 belongs to the freeze task. Existing
reviews are retained; Lead freeze review and the assigned driver's physical
delivery are the next stage, not acceptance claimed by this author.
