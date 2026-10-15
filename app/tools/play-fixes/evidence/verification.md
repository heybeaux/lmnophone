# Play opening fixes — verification

PLA-633. Base: reviewed 8b `2c522231`. Runtime implementation: `fb8990a8`.
Tests-first checkpoint: `69fc89d7`; focused test compilation failed on the new
scheduling and presentation contracts, then passed after implementation.

## Built and installed

- Debug APK built; version stays 63 / `0.1.0-preview-63` for this source branch.
  The delivery driver assigns the next device version and release history.
- APK SHA-256: `4bb22428b9932a463aae931afd7066806a7c07cc68a9fa1a015ff470f6fd455b`.
- Instrumentation APK SHA-256: `93b66d1b11b42f143cb870a856e938c0bf5c2a17e24c09b5d8bdfbfe0b554c06`.
- Installed only on an isolated read-only instance of the team's `pla104-a36` AVD.
  No physical phone was used. This does not claim a spare-device install or exercise.

## Exercised

The androidTest-only `PlaySessionProofInstrumentation` uses the production runtime,
PIN flow, shared journal and DPM effects. Only its running clock is accelerated;
no clock or opening shortcut exists in the installed app.

- Final screen-off run: **713 ms** from sleep command to closed/handback-ready state.
  The screen remained non-interactive. Play was hidden, the exact baseline lock-task
  set was restored, and the shared journal was empty. Earlier run: 1,433 ms.
- Play remained in lock task during access; only Play was added to the baseline.
  After closure, no launch intent resolved, market did not reopen Play, and Recents
  did not put Play in front.
- Production UI, using real debits: two openings uses the plural; one uses the
  singular; zero shows “No openings left for now” without “Start the wait”.
  See `emulator-readback.txt` for the assertions.
- No Google sign-in, messages, contact data, calls or emergency calls were exercised.

Reproduce with the two APKs installed on a private emulator, explicitly selecting
that emulator for every adb command. Run `am instrument -w -e phase screen` or
`-e phase copy` with component
`org.plainphone.test/org.plainphone.play.PlaySessionProofInstrumentation`.
Build the test APK with
`-PpreviewTestRunner=org.plainphone.play.PlaySessionProofInstrumentation`.
The runner refuses non-emulator hardware. Wake the display before each run.
Do not use `connectedAndroidTest`.

## Screen-off cause and repair

The old timer's one-second delay was on the main Handler, not a one-second sleep
on the policy dispatcher. A tick nevertheless queued in front of a close, and its
phone-state/storage/alarm work ran before the revoke. The host regression now
places a tick and SCREEN_OFF in the queue and proves that closing runs first,
without a tick or a screen-on event. Already-running policy work is not interrupted;
all privileged effects still have one serialized writer.

The asynchronous receiver also had no CPU wake hold. `goAsync` retains the pending
broadcast but is not a CPU wake-lock API. The close now holds a partial wake lock
until its callback finishes, with a 10-second timeout; it never lights the screen.
Android documents partial locks for CPU work while the screen is off:
[Set a wake lock](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/set).

The build-65 spare trace cannot distinguish queue latency, CPU suspension, and
platform work inside the close. These changes remove the two identified scheduling
risks, and the emulator meets the two-second target. The sole device driver must
measure the spare again; this evidence does not establish its exact original cause
or promise the same timing on that hardware.

The pulse now starts only for an outstanding countdown/Ready window/grant and
cancels when none remains. A grant awaiting restrictive readback still retries.
The existing exact PlayDeadline alarm remains intact.

## Presentation

Refusal and failed launch show “This didn't open. Try again.” with OK, without
claiming that a debit was refunded. Only failed readback shows Not ready; launch
cleanup shows Checking until its callback finishes. The production menu mapper
receives localized detail using the current wait. Before and Ready counts use
Android plurals. Zero quota routes directly to the existing No openings screen.

Supplemental 360 × 2200 captures show all Before/disclosure and Not ready copy at
2.0; the original 360 × 640 captures remain. Refusal has 1.0/2.0 captures plus the
existing named-variant fixture matrix. Every new golden was visually inspected.

## Host checks

| Check | Result |
|---|---|
| `:policy:test` | 692 tests, 0 failures |
| `:device:testDebugUnitTest` | 1,223 tests, 0 failures |
| `:plainphone:testDebugUnitTest` | 355 tests, 0 failures |
| `:plainphone:assembleDebug` | Passed |
| `:plainphone:assembleDebugAndroidTest` | Passed |
| `:ui:testDebugUnitTest` (without golden comparison) | 1,430 tests, 0 failures before adding the two refusal captures |
| `:ui:verifyPaparazziDebug` (final) | 1,432 tests, exactly 11 established baseline failures |
| `openspec validate --all` | 6 passed, 0 failed |

The unchanged screenshot failures are Branding attention/turnOff at 1.3, 1.5 and
1.8 (six) and PreviewTurnOff maturityNotice at 1, 1.3, 1.5, 1.8 and 2 (five).
No baseline images were replaced. Only new full-body and refusal-stage images were
recorded; existing Play and trusted-screen goldens remain byte-identical.

Review is assigned to PLA-634; spare delivery is PLA-635 after that review.
