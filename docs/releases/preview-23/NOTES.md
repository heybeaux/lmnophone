# Build 23 — waiting changes in Phone settings

[PLA-402](/PLA/issues/PLA-402). Based on preview-22 `96db8e29`.

More → Phone settings now shows the grey **A change is waiting** box above the
settings rows when the shared waiting store reports a pending change. The second
line uses the existing U9 cancellation description, including the existing
“(preview)” app-name suffix for turn-off. Tapping opens the existing PIN-free
confirmation and uses the existing `cancelWait` path. The box goes away when the
operation leaves pending. No separate pending-state cache or cancel logic.

**Built:** versionCode 23, `0.1.0-preview-23`, testOnly. Same signing certificate
as build 22. SHA-256 and install time are in `evidence/install.json`.

**Installed:** in-place 22 → 23 on owned API 35 `pla363-policy-a35` /
`emulator-5880`, 2026-10-12T09:51:12Z. No physical device used by the author.

**Exercised:** a synthetic TURN_OFF operation in the real joint store. Home
showed the pending line; Phone settings showed the box with the exact spoken
label; tapping opened “Cancel this change?” with no PIN; “Yes, cancel it” showed
“Cancelled”; returning to Phone settings showed no box. Instrumentation read
CANCELLED, the cancellation receipt, unchanged credentials and zero restore
steps. Device Owner stayed present. Original authority and clock files were
restored byte for byte. This proves fixture cancellation, not a user-driven
waiting-change start or full turn-off.

## Inherited limitation and spare test

**There are no supported steps to start a waiting change through this preview's
UI.** `PinAccessActivity` routes every admin-menu action to “Not in this test
build yet.” `PolicyRuntime` consumes waits but starts none. This is also stated
in the build-17 notes. The issue's supported-start/spare-reproduction criterion
therefore cannot be met on the specified baseline. No D1 start logic was added.

The Lead should decide whether to install this bounded dormant integration or
defer it until a supported waiting-change start is present. This is not a claim
that all acceptance criteria passed. Do not apply the destructive test fixture
to the spare; it deliberately refuses physical hardware.

On a phone with an already-pending operation: More → Phone settings → the grey
box → Yes, cancel it → OK → Phone settings. The box should be gone. Without a
pending operation, the screen remains unchanged. The author provides no
unsupported instructions for creating a pending operation on the spare.

## Repeating the emulator check

From `app/`, build the separate test APK:

```sh
./gradlew :plainphone:assembleDebugAndroidTest -PpreviewTestRunner=org.plainphone.WaitingSettingsInstrumentation
```

From the repository root, with this author's owned AVD on the documented serial:

```sh
adb -s emulator-5880 install -r -t app/plainphone/build/outputs/apk/androidTest/debug/plainphone-debug-androidTest.apk
python3 docs/releases/preview-23/evidence/waiting_route.py
```

The script verifies the AVD name, API and build, backs up the two stores inside
app-private storage, seeds the fixture, exercises the UI, reads cancellation,
and restores in `finally`. If interrupted, restore before further tests:

```sh
adb -s emulator-5880 shell am instrument -w -e phase restore org.plainphone.test/org.plainphone.WaitingSettingsInstrumentation
```

No address, valid PIN or outgoing text is included in the fixture. The test APK
is separate from the shipped application and cannot seed a physical device.

## Checks

- Red test commit `7ad04d96` precedes implementation `5a17995c`.
- 189 app JVM tests, including waiting-line, cancellation-stage, navigator,
  About and settings checks: passed.
- 18 existing device `WaitingCheckpointsTest` cases: passed.
- 232 selected UI screenshot cases: passed. Suites: PhoneSettingsWaiting,
  BasicSettings, CommunicationCounts, Home, About and TrustedScreens.
- 15 new U7 goldens: no wait / wait / long description, at 1.0/1.3/1.5/1.8/2.0,
  360×640 dp; all inspected. Semantic assertions verify one spoken action,
  minimum height and the event. Existing goldens were not changed.
- Nine upgrade-gate self-tests: passed. The copied gate differs only in version
  strings. Live gate passed with `exit=0`: exactly 911 on both routes, zero
  current calls, reboot/readback, owner retained and lock task LOCKED. Results
  are in `evidence/upgrade-gate.txt`. No call or SOS was made.
- Initial fixture attempts exposed test setup/expectation errors: the generated
  manifest used the counts runner, then the expected turn-off text omitted the
  existing “(preview)” suffix. Both were corrected in the test harness; no app
  behavior was changed to pass these checks. The successful run restored state.

Sound, Brightness, Battery, counts, About, Home, emergency routes, D1 policy,
and all previous experimental limitations are unchanged. This branch changes
no policy decision or device restriction.
