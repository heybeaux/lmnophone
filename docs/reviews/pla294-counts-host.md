# Optional communication counts: host verification

Issue: [PLA-294](/PLA/issues/PLA-294). Branch: `feature/home-counts-host`.
Approved design: `spec/communication-counts` at `a16e60e`, revision 2.
Base: preview-2 `82648be`. This is a review candidate, not a release approval.

## Implementation

Exact D1 count-only queries live in `CommunicationCountSource`. Its Android port
uses only cursor count and close; there are no cursor value getters. Work runs
on one background executor, with cancellation signals and generation checks.
SMS and MMS must both succeed. Locked storage, missing permission, null cursors
and exceptions mean unavailable. Observer bursts merge over 150 ms; Home resume
always reads afresh. Home hiding unregisters observers and clears counts.
Settings performs a fresh availability read without registering Home observers.

No preference storage existed in this preview host. Added a private
`home_preferences` device-protected SharedPreferences file containing only the
`show_communication_counts` boolean. Missing key defaults off. Count values are
never persisted. Switch-off clears state, cancels work and unregisters observers;
it never revokes. New DPM calls stay in `:device` and target self only. DPM's return
is not availability; permission readback and a successful query are required.
No emergency, approval-list or lockdown decisions changed.

## Commits

Original -> cherry-pick:
- `811412e` -> `5d4e66f`
- `1090396` -> `be90141`
- `31acad3` -> `ac544dd`
- `8298d34` -> `11fa738`
- `dfe553a` -> `6033381`

- `5d4e66f` test(ui): require centered plain-text More across font sizes
- `be90141` feat(ui): make Home More centered and borderless
- `ac544dd` test(ui): specify optional Home counts and settings contract
- `11fa738` feat(ui): add optional calm Home counts and phone settings switch
- `6033381` docs(ui): record count copy, host seams and verification evidence
- `4d11b8c` test(host): specify reliable communication counts and settings navigation
- `ed18bd4` feat(host): read fresh optional call and SMS MMS counts on Home
- `2b93f69` test(ui): record calm Home counts on the preview layout
- `13ae509` copy(ui): disclose SMS MMS coverage and retained count permissions
- `4be34ba` test(ui): verify disclosure scrolling and preview test imports

Tests-first evidence: `4d11b8c` was committed after offline
`:plainphone:testDebugUnitTest` failed compilation on the absent count contracts
and settings route; implementation followed in `ed18bd4`.

## Offline tests

`./gradlew --offline --continue test :ui:verifyPaparazziDebug :plainphone:test`
ran 2,152 unit-test executions: 2,142 passed, 10 failed, none skipped.

| Module | Tests | Result |
|---|---:|---|
| core | 10 | pass |
| policy | 588 | pass |
| links | 36 | pass |
| UI | 101 debug + 101 release | pass |
| host | 55 in each of 6 variants = 330 | pass |
| device | 493 debug + 493 release | 5 known probes fail per variant |

`:ui:verifyPaparazziDebug` passes, covering all 154 tracked goldens. Inspected all
37 changed Home images and all 15 changed settings images. Removed inherited
Home golden variants whose fixtures do not exist on preview-2.

The five `Pla241ReviewTest` failures are:
`failedPlanMustStillRestoreFixedRestrictions`,
`throwingJournalMustStillRepairEmergencyFloor`,
`lockedBootFlipFailureMustReachFirstUnlockedHome`,
`enabledStateChangingBeforeFinalReadbackMustBlockSetup`, and
`repairMustPreserveFlipFailureFromFirstAttempt`.
The probe file, `device/boot` sources and policy module are byte-identical to
`82648be` (`git diff 82648be -- <those paths>` is empty). They are outside this
assignment's explicit scope. The complete workspace suite is therefore **red**;
the requested host JVM and Paparazzi checks are green. No baseline failures were suppressed or rewritten.

Additional API 36 UI instrumentation used owned AVD `pla291-a36` on
`emulator-5574`, with the synthetic lock credential cleared before testing.
`CommunicationCountsTest` passed all 4 tests (including real text bounds and
scrolling of both D7 paragraphs across five font scales). `HomeScreenTest`
passed 4 of 5, including the new centered-More test. Its unchanged
`platformAccessibilityCanRevealEarlierApps` failed with null
`uiAutomation.rootInActiveWindow`; a focused retry with wake/stay-awake and
keyguard dismissed failed the same way. This legacy platform-accessibility
probe is not claimed as passing. No test was disabled.

## Emulator reproduction and results

Used owned Google APIs API 35 AVD `pla291-a35`, serial `emulator-5572`, at
360x640 with density 160. No account, physical device or external call. Calls
and texts are emulator-generated synthetic fixtures; no content, contact or
number is included in this evidence. Opened prior synthetic conversations first
to establish an unread count of zero.

Installed the existing preview-2 APK through `app/tools/preview-2/setup.sh`,
after removing the spike's test admin. Provisioned `org.plainphone` as Device
Owner and used the same Phone/Messages/Camera/Maps starter list. Upgraded with
`adb -s emulator-5572 install -r -t <candidate.apk>`; no reset or app-data clear.
The debug restriction filter retains adb. The upgrade readback was:

```text
READ_SMS: granted=false, flags=[ RESTRICTION_INSTALLER_EXEMPT]
READ_CALL_LOG: granted=false, flags=[ RESTRICTION_INSTALLER_EXEMPT]
```

Both grants became true only after enabling, with `POLICY_FIXED` and installer
exemption; successful provider queries then supplied counts.

| Check | Observed |
|---|---|
| Upgrade default | switch off; neither number shown |
| Missed call | Phone 1; swiping the Phone notification then resuming cleared it |
| Two incoming texts | Messages 2 while Home visible, without restart |
| Swipe text notification | Messages still 2 |
| Open conversation | Messages number disappears |
| Reboot before first unlock | `RUNNING_LOCKED`, zero count-query log lines |
| First unlock | fresh Phone 1 and Messages 2; preference remained on |
| Switch off | both disappear; PID 5246 before and after |
| Idle with switch off | zero additional count-query log lines |
| `pm revoke READ_SMS` then restart | Phone 1 retained; Messages hidden; settings unavailable line shown |
| Explicit off/on after revoke | grants/readback restored Messages 2 |

**Notification fixture limitation:** preview-2 disables the notification shade
in lock task. Shell `am task lock stop` did not stop managed lock task. For each
actual swipe, removed the test admin, stopped Home, swiped the real notification,
then restored the owner and broadcast the same `SET_STARTER` list before reading
Home again. This proves provider semantics plus fresh resume after reprovision,
not in-lock-task access to the shade. No production policy was changed. For the
`pm revoke` path the same test-admin removal cleared `POLICY_FIXED` first; the
switch stayed on and startup did not auto-grant the revoked permission.

Before unlock, the preview uses its existing locked/checking flow rather than
showing Home. The JVM source test independently proves the unlocked-user gate
performs no queries.

## Screenshots and review limits

Issue attachments include actual emulator Home off/on and Phone settings at
100% and 200%, plus the scrolled permission disclosure and permission-loss line.
Baseline preview-2 Home screenshots have no counts or settings feature; the
reviewed counts UI's old-copy fixtures supply additional before screenshots.
All shared images were inspected and contain no personal details.

As directed, kept the preview's reverse-ordered list and lack of `appTopGap`.
Some notice fixtures retain pre-existing clipping at the top of the optional
app list. Phone, Messages and More remain visible. Preview branding is retained
outside the exact new D7 disclosure; availability text uses the preview app name.

No hardware claim: the eventual preview's Android Platform Engineer still
verifies the spare's Phone/Messages versions and transitions per D8. RCS and
third-party apps are not counted. Lead is the cross-model reviewer; this report
does not approve integration.

## Uploaded evidence

QA case: [PLA-C12](/PLA/cases/PLA-C12).

- Artifact: [home-off-1](/api/attachments/806b631e-c4c1-4006-ad27-a6cd2366ca97/content)
- Artifact: [home-on-1](/api/attachments/94c71b2c-70ea-4476-8f0e-57934d873dd8/content)
- Artifact: [settings-on-1](/api/attachments/aeb93a87-2b8b-4833-9cbb-ed8c4a7a067c/content)
- Artifact: [home-off-2](/api/attachments/c15d552b-2d85-4a62-8021-1464aaff228c/content)
- Artifact: [home-on-2](/api/attachments/06efd154-635c-4180-b868-e9c3b523a25f/content)
- Artifact: [settings-on-2](/api/attachments/0cf4de6b-a24e-49ea-8d29-0ae5bf4d39fa/content)
- Artifact: [settings-scrolled-2](/api/attachments/d141346f-857a-41d9-b058-06b150ef1534/content)
- Artifact: [permission-loss-settings](/api/attachments/9d178b78-f201-466e-b7d9-bb8db7459603/content)
- Artifact: [pla294-screenshots.zip](/api/attachments/8a24c6fd-7e9b-45ad-920b-a9cbf299fb5a/content)
- Artifact: [counts-evidence.json](/api/attachments/fc443153-5622-48bd-8eef-77aa05dd008d/content)
