# Play policy-health preflight

Run: 2026-10-15 UTC, build-63 base plus the Play eligibility/default-limits changes.
This is an emulator preflight, not timed-session acceptance or a spare install.

## Environment and artifact

- Read-only `pla104-a36` (API 36, Play Store image); a free 59xx console/ADB port pair
  was checked before launch. `emu avd name` returned `pla104-a36` before setup.
- App remains version 63, `0.1.0-preview-63`, TEST_ONLY. No version bump for delivery.
- Debug APK SHA-256: `20ca51ba4a38b6be7e8e8b814e73ebc5b22dfc91aa010b890eae896e6d3f5c61`.
- Built app and androidTest APK with:

```sh
cd app
./gradlew :plainphone:assembleDebug :plainphone:assembleDebugAndroidTest \
  -PpreviewTestRunner=org.plainphone.play.PlayPolicyPreflightInstrumentation
```

`PlayPolicyPreflightInstrumentation` exists only in androidTest. It reads existing
standard policy records and OS facts; it has no enrollment, policy-write, session,
countdown-shortening or sign-in action. It refuses non-emulator hardware.

## Reproduction

Choose the verified read-only emulator as `PLAY_EMULATOR`; never target a physical
phone. `setup.sh` installs the supplied test APK and provisions this emulator.

```sh
bash tools/preview-2/setup.sh "$PLAY_EMULATOR" \
  plainphone/build/outputs/apk/debug/plainphone-debug.apk
adb -s "$PLAY_EMULATOR" install -r -t \
  plainphone/build/outputs/apk/androidTest/debug/plainphone-debug-androidTest.apk
adb -s "$PLAY_EMULATOR" shell am instrument -w \
  org.plainphone.test/org.plainphone.play.PlayPolicyPreflightInstrumentation
```

## Observed

The preview setup pass returned `ready=true`, with no flip failures, disabled floor
entries or unreadable floor roles. Home resolved to `org.plainphone/.HomeEntry`;
lock task read back `LOCKED`. Play was hidden and unsuspended after setup.

The read-only instrumented result was:

```text
INSTRUMENTATION_RESULT: floorUnreadable=
INSTRUMENTATION_RESULT: hasSystemDialer=true
INSTRUMENTATION_RESULT: health=BOTH_INVALID
INSTRUMENTATION_RESULT: inventory=absent
INSTRUMENTATION_RESULT: policyRecords=0
INSTRUMENTATION_RESULT: result=READ_ONLY_PREFLIGHT_COMPLETE
INSTRUMENTATION_CODE: -1
```

There are no `policy.rec` or `policy.lkg` records under either the credential-
protected or device-protected app files root. No production code in this base
constructs `FilePolicyStore`; the preview uses `starter-list.txt`. Reading the
absent standard store through the real `LivePolicyInputAssembler` returns
`BOTH_INVALID`. The system dialer and complete OS floor read succeeded, so the
retained VALID-health gate is the identified blocker, not the inventory exception.
No store record was seeded and no health value was replaced.

## Coverage and limit

- Host: full `:policy:test` (690 tests) and `:device:testDebugUnitTest`
  (1,198 tests) passed. Tests were committed red before the implementation.
- Built: app and read-only test APK passed. Installed: these artifacts on the
  owned emulator only. Exercised: setup and the read-only preflight above.
- Not exercised: countdown, Start, timed Play lock task, end triggers, new-app
  intake, reboot revocation, or sign-in behavior. No session was opened.
- No physical-device commands, Google sign-in, messages or calls were performed.

Next integration needs a truthful, persistent preview policy-health source
selected by the Lead. The existing starter list is present; mapping its health
requires an explicit contract for missing/unreadable/malformed data. Creating an
unused full-policy record solely to pass the gate is not this preflight's outcome.
