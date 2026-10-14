# PLA-525 request-send candidate

Base `711fc4f0` (`pla-prod/cleanup`); branch `pla-requests/send`.
Tests-first checkpoints: `2e5c441c`, `190c24f3` (neither changes src/main).
APK implementation: `abbaada6`. No author approval or spare install is claimed.

## Changed behavior

More → Ask for an app opens the real request host. PIN settings → Who gets
requests requires a fresh PIN to view the number and another for each committed
change/removal. The optional name never establishes authority. Family selection
is deliberately absent. PIN settings → Requests shows text, date and status;
there are no approval/reply/delete actions. Last ten rows are shown in full.

Each explicit attempt is saved before handover, consumes the five-per-day quota,
and has an idempotent ID. SMS parts have separate persistent results. All accepted
means Sent; complete failure means Not sent with a reason; partial/unknown means
Not confirmed. No Delivered, arrival/read claim or automatic retry. Recipient and
history use a dedicated atomic CE no-backup file, not the PIN-vault format.
Pause revokes recipient-editor work; late callbacks only update their own attempt.

## Verification

- Policy: **674 passed**. Includes duplicate tap/result, optional/invalid recipient,
  quota across restart/recipient changes, failed writes, missing recipient, partial
  result, timeout/process change, and late callbacks after uncertain handover.
- Device debug: **1,083 passed**, including file reopen/preservation and corruption.
- App debug: **304 passed**, including production More route, mapper, setup flag
  with no name, full request text, and fresh-PIN/live-screen write guard.
- UI: initial broad debug run **1,236 passed**; final focused Paparazzi verification
  **36 passed** at 360×640, font scales 1.0/1.5/2.0. Every image inspected. Added
  assertions check last-row scroll reach, pinned actions and absence of Delivered.
- OpenSpec: **6 passed**. No checks disabled.
- Debug APK and dedicated emulator instrumentation APK build successfully.
- Inherited evidence kept separate: release unit test `VoiceSendRouteTest` asserts
  the debug-only family-send gate and fails in release; this source/gate is unchanged.
  The established broader Paparazzi/lint baselines were not used as new serial gates.

## Private emulator

Private AVD `pla525-ui-a35`, API 35, serial `emulator-5756`, 360×640 @160dpi.
Only this emulator was driven; no connectedAndroidTest or physical-device command.
Airplane mode enabled and read back by the test before any attempt. Only a fixed
fictional test number is configured. No emergency number or live call is used.

1. Install development artifact version 49 with the new outbox. Set fictional
   recipient and send: **Not sent / NoSignal**, saved. This is a development
   artifact, not a claim that shipped preview-49 contained request sending.
2. In-place install candidate 50. Existing recipient and saved request read back.
3. Through the actual RequestsActivity field and Send touch target, enter another
   request: **Not sent / NoSignal**, exactly one new saved row.
4. Force-stop, in-place reinstall candidate 50, set font scale 2.0, restart the
   instrumentation in read-only mode: recipient, requests and NoSignal results
   preserved; Not sent is present in the live accessibility tree.

The initial native test queried the Send text child as a clickable node. The
Android tree showed a clickable parent; the test now taps the labelled button's
screen bounds. No production button behavior was changed for that test issue.
FLAG_SECURE keeps real-device screen captures black; it was not weakened. The
committed screen images are Paparazzi fixtures, not claimed emulator screenshots.
The fresh-PIN guard is host-tested; interactive PIN setup was not device-exercised.

Original reviewed candidate APK: `org.plainphone`, 50 / `0.1.0-preview-50`, debug, testOnly.
SHA-256: `30f8c25391580573cdc5ee70d16c4f9767db8ff45c08af8d1500d248b9a77eaf`.
Emulator package readback: versionCode=50, versionName=0.1.0-preview-50,
lastUpdateTime=2026-10-13 19:42:08 (emulator-reported time).

## Focused review / handoff

Assigned reviewer: Product & UX Designer (Claude). Review the full branch range
once, including tests, recipient authorization boundary, durable send transitions,
production routes and `TODO(copy)` neutral SMS/result wording in the flow docs and
resources. No extra plan/review chain. Driver: Android Platform Engineer after
review, through the Lead's existing delivery path. Emergency floor/lock task are
unchanged. The driver preserves device data with an in-place install and records
built / installed / exercised separately.

Limitations: uses the phone's default SMS line; no new SIM selector. No Family
picker, retry button, replies or actions on requests. Last ten rows shown, ledger
retained to a 10,000-record cap (then saving refuses). Actual carrier success has
not been exercised: only host callback tests cover Sent. No live message is authorized
by this emulator evidence. The neutral copy was resolved by the assigned UX reviewer; see the correction pass below.

## One correction pass after Product & UX review

The reviewer approved the code/tests in `711fc4f0..715071b9` and requested only
C1–C7 copy corrections. All seven are applied verbatim: ASCII-quoted SMS with the
Apps on this phone destination, exact setup path, neutral recipient wording,
truthful Sent/Not confirmed explanations, Set up for an empty recipient, and
matching docs with both TODO(copy) markers removed.

- Merged the approved cleanup branch tip `06ad4863` in `c42e28cb`, retaining the
  setup-helper wording, unique About history and removed practice-send hook.
  The sole merge conflict was the adjacent flow-doc additions; both were kept.
- Tests-first delta `a608583d`: empty recipient must offer Set up, observed failing
  at 100/150/200% before the change. A combined mapper check saves named/unnamed
  recipients and removes them, verifying More and Ask availability together while
  PIN help remains the actual setup-helper role. A request recipient does not gain
  PIN authority; `pinHolderOnly` keeps the self-managed wait rows out of this UI.
- Final app debug unit suite: **307 passed**. Request screenshots: **39 passed**
  at 360×640, 100/150/200%; **14 changed/new goldens inspected**. The 200% More
  image is unchanged because the revised subtitle is below its initial viewport.
- Candidate 50 debug APK builds; SHA-256:
  `0c8749f042b4d724777a875a7ecaebee5d0bc4562eddd4ea72ba0df2a408ec21`.
- This revised artifact is **built, not installed**. Private emulator-5756 is no
  longer running. Earlier emulator send/restart/update evidence above is retained;
  no new device exercise or carrier success is claimed. No physical device touched.
- D1–D4 remain explicitly deferred per the reviewer: large-ledger byte cap, read-error
  wording, polling cadence, unreachable legacy Ask route. They do not hold this preview.

Next: Lead checks only this correction delta, as the reviewer requested, then the
existing sole driver installs candidate 50 in place over 49. No additional review
round or approval of the unchanged implementation is requested.
