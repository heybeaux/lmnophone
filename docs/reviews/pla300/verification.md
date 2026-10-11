# Communication counts: revision 4 fix round

Issue: [PLA-300](/PLA/issues/PLA-300). Reviewer: Plainphone Lead (Anthropic).
Branch: `feature/home-counts-host`, based on `ba494d4`.
Approved design/spec: `spec/communication-counts` at `06fe2ec` (revision 4).
This is an emulator-only review candidate, not integration or release approval.

## Changes and requirements covered

- D1: one `content://mms-sms/complete-conversations` query, projection
  `[_id, transport_type]`, selection `read = 0`, null arguments/sort, cursor
  count only. No fallback or per-table sum in production.
- D3: observers precede the first read. Every visible interval starts empty.
  A generation fences callbacks, package changes, observed gate changes,
  visibility and switch changes. Each source has its own cancellable worker,
  with no queue; blocked calls cannot consume the other source's worker.
- Debounce is 150 ms from the latest callback. The first callback starts a
  non-sliding 1-second deadline. A per-source timeout makes a 3-second read
  unavailable; late completions cannot publish. Both sources refresh every
  10 seconds. An independent expiry hides a number at 13 seconds from read
  start, measured with `SystemClock.elapsedRealtime`.
- UI timers run on the main handler, independent of provider workers. Provider
  queries and Binder cancellation run off main. If a provider ignores cancel,
  its worker remains bounded to one; later attempts for that source are
  unavailable until it returns, while the other source remains independent.
- D4: check switch/generation, unlocked user, per-source permission, supported
  role holder, installed/enabled package and actual Home intent resolution
  before reading and immediately before publication. Home uses the same
  intent factory as the gate. No roles, approvals, floor or lock-task policy
  are changed by this feature.
- Package ADDED/REPLACED/REMOVED/CHANGED broadcasts for the two supported apps
  invalidate the generation while enabled. Read-only gate checks also run
  once per second while active. This covers permission/role changes without
  relying on an SMS broadcast delivered only to the old/new SMS holders.
- D7: Settings explains the supported apps and qualifies RCS coverage using
  the exact revision 4 copy. Counts remain positive-only/zero-hidden and
  unavailable independently. Only the boolean is stored; startup/resume never
  grants a revoked permission. The permission-retention disclosure remains.

Relevant public API references: [default dialer package](https://developer.android.com/reference/android/telecom/TelecomManager#getDefaultDialerPackage()),
[default SMS package](https://developer.android.com/reference/android/provider/Telephony.Sms#getDefaultSmsPackage(android.content.Context)),
[SMS role-change broadcast recipients](https://developer.android.com/reference/android/provider/Telephony.Sms.Intents#ACTION_DEFAULT_SMS_PACKAGE_CHANGED).
Provider behavior below is established by repeatable emulator tests, not an
assumption about another Android build or a physical phone.

## Tests first and commit sequence

- `a9ee0b7`: host regression contracts first. Offline host test compilation
  failed on the absent `CountKind`, `CountAccess` and asynchronous reader.
- `336838c`: combined query, deadlines, generation/publication gates.
- `ef8e5ef`: Settings tests first; compile failed on absent `unsupportedApps`.
- `257a07c`: Settings copy, host flag and 20 inspected Settings goldens.
- `ac709ee`: independent bounded workers per count source (emulator APK).
- `84392a0`: reproducible count-only provider fixture and off/timer regression.
- Subsequent test/evidence commits expand the MMS fixture to the spike's
  address/part structure and retain the final test results. Production APK
  code is unchanged after `ac709ee`.

Controlled-scheduler/clock tests cover callback during a read, late completion
from an older generation, a stalled refresh's first-callback deadline, steady
callbacks, late/missing callbacks, 10-second polling/13-second age, query timeout,
off/on and no work while off, every publication gate, missing/replaced/disabled
roles, package replacement, stop/resume, process restart, persisted switch and
interrupted write, and the alternating `[5,7,5,7]` single-statement provider
schedule (never 12). Exact query strings and cursor closure are separately tested.

## Verification totals

Offline `--continue test :ui:verifyPaparazziDebug :ui:assembleDebugAndroidTest`,
followed by the narrow host rerun after additional test-only coverage:

| Module | Test executions | Result |
|---|---:|---|
| core | 10 | pass |
| policy | 588 | pass |
| links | 36 | pass |
| UI | 101 debug + 101 release | pass |
| host | 69 in each of 6 variants = 414 | pass |
| device | 493 debug + 493 release | 5 unchanged baseline failures per variant |

Total final reports: **2,236 executions, 2,226 pass, 10 baseline failures**.
Paparazzi verifies **159 goldens**, including 20 Settings images across font
scales 1.0, 1.3, 1.5, 1.8 and 2.0. Every changed/new image was inspected.
API 36 `CommunicationCountsTest`: **4/4 pass**, using the rebuilt final UI test
APK. The scrolling/text-bound test reveals each disclosure/status at all five
scales; the screenshots at 200% show the supported-app line after scrolling.

The five known `Pla241ReviewTest` failures (each debug/release) remain:
`failedPlanMustStillRestoreFixedRestrictions`,
`throwingJournalMustStillRepairEmergencyFloor`,
`lockedBootFlipFailureMustReachFirstUnlockedHome`,
`enabledStateChangingBeforeFinalReadbackMustBlockSetup`, and
`repairMustPreserveFlipFailureFromFirstAttempt`.
No device/policy/emergency source or baseline test changed. The full suite is
still **red**; no failure was suppressed. Host and UI checks are green.

## Combined-provider proof (API 35 and 36)

Owned Google APIs AVDs only: `pla291-a35` / `emulator-5572` and `pla291-a36` /
`emulator-5574`. No account, real contact or physical device is involved.
`CombinedCountInstrumentation` executes as target `org.plainphone`, verifies
its `READ_SMS` permission and verifies it is **not** the default SMS app.
Google Messages is the default. It queries cursor counts only, without any
row-value getter. Per-table queries exist only in this test oracle.

| Fixture | Combined | SMS oracle | MMS oracle | Result on both APIs |
|---|---:|---:|---:|---|
| Unread SMS in two conversations + group MMS PDU | 3 | 2 | 1 | pass |
| Add sent SMS, sent MMS, SMS draft and MMS draft (inserted with read=0) | 3 | 2 | 1 | pass |
| Mark second SMS read; remaining SMS and MMS share the same `_id` | 2 | 1 | 1 | pass |

The group fixture includes the spike's retrieve-conf PDU, group thread,
synthetic sender/recipient address rows and text part. The provider changes
non-inbox inserts' read flags; the combined query excludes sent/draft rows.
This is a provider fixture, **not a carrier MMS delivery claim**.

Reproduce from `app/`: build `:plainphone:assembleDebugAndroidTest`, install
both debug/test APKs with `adb -s <owned serial> install -r -t`, and grant
LMNOPhone's `READ_SMS` on the test AVD (API 35 uses its existing test-owner grant;
API 36 used shell `pm grant` after an exempt adb install). Then run
`plainphone/src/androidTest/tools/run_combined_fixture.py <owned serial>` with
`PAPERCLIP_RUN_SCRATCH_DIR` and two distinct `COUNT_FIXTURE_RECIPIENT_A/B` inputs
from the synthetic 555-01xx range. The script enforces both AVD names/serials,
marks existing synthetic records read, and replaces only its high-ID fixtures.
It logs counts only and stops at any failure. Do not use it on other devices.
Evidence: `provider-api35.json` and `provider-api36.json` alongside this report.

## Host emulator recheck (API 35)

The prior test-owner/starter setup from [PLA-294](/PLA/issues/PLA-294) was
retained. Installed in place with `-r -t`; no reset or data clear. The final APK
uses production code at `ac709ee`. Final flags retained
`RESTRICTION_INSTALLER_EXEMPT` for both permissions. The first off/on grants
also add `POLICY_FIXED`; removing the test admin for a fixture clears that
flag, without changing the feature's own grant behavior.

| Check | Observed |
|---|---|
| Off → in-place update → off | no counts, switch false |
| On → in-place update → on | switch true, fresh Phone 1 / Messages 2 |
| Stored preferences | only `show_communication_counts` boolean, no count/timestamp |
| Two incoming emulator SMS | Messages 2, without host restart |
| Swipe actual Messages notification | Messages still 2 |
| Open its conversation | Messages disappears |
| Incoming emulator call missed | Phone 1 |
| Swipe actual missed-call notification | Phone disappears |
| Off on final build | both disappear; PID **5769** unchanged; zero further reads |
| Reboot, before first unlock | `RUNNING_LOCKED`, zero count-query log lines |
| Unlock | empty first; fresh Phone 1 / Messages 2 once Home is ready |
| `pm revoke READ_SMS`, restart | Messages hidden, Phone 1 remains |
| Settings after revoke | unread-text unavailable line; switch remains on |
| Explicit off/on after revoke | grant/readback restores fresh Messages 2 |
| Disable Google Messages while Home visible | Messages hides; Phone 1 remains, same PID |
| Open Settings with Messages disabled | exact standard-app line appears |
| Re-enable Messages, restore SMS role | Messages returns from a fresh read |

Counts-only detailed transitions: `host-api35.json`. The first unlock sample
was taken before Home was ready and showed no numbers; a second complete reboot
and unlock, with readiness allowed, showed both fresh numbers. This is consistent
with starting empty, not proof that numbers exist immediately on unlock.

The existing preview disables the shade while managed. For the two real
notification swipes, temporarily removed the test admin, stopped Home, swiped
the notification, then restored the same owner/starter list using `--esa`.
A first restoration command incorrectly used `--es`; it returned result 1 and
Home showed no numbers. Correcting the argument restored the unchanged plan;
only the corrected run is claimed as the notification result. This fixture
proves notification semantics plus fresh Home resume, not in-lock-task shade
access. The permission-revoke fixture used the same removable test owner to
clear its fixed permission grant. All changes were restored afterward.

## Limits and handoff

API 36 additionally covers the combined provider gate and real UI text/layout
instrumentation; the full notification/reboot/permission host matrix is API 35.
Permission loss during a query, timer deadlines and late/no callbacks use the
controlled unit scheduler, not wall-clock emulator timing claims.

RCS, carrier MMS delivery/retry, the spare's newer Google app behavior and the
revision 4 D8 human release gate remain unverified, as before. Counts remain
"in testing". No physical phone, preview branch, emergency/floor behavior,
app approvals, public release or hosting was changed.

QA case: [PLA-C14](/PLA/cases/PLA-C14).
Candidate notes: [PLA-C15](/PLA/cases/PLA-C15).
The Lead owns cross-model review and any later integration.
