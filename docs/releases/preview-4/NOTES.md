# preview-4: build notes

**Counts are in testing.** This build adds optional Home counts to preview-3.
It is installed in place on the spare (2026-10-11 08:39 UTC; see "Spare install
and driver checks" below). The counts switch is left off for the tester to turn on.

## What's new

- More > Phone settings has one switch, **Show missed calls and unread texts on Home**.
  It starts off, including when upgrading from preview-3. It needs no PIN.
- Turn it on to show a quiet number beside Phone for unacknowledged missed calls
  and Messages for unread texts and picture messages. Zero and unavailable numbers stay hidden.
  Numbers above 99 appear as 99+. Other apps never show a number.
- Turning it off hides both numbers immediately, without restarting Home.
  Plainphone **keeps permission after the switch is off, never reads content**.
  The switch stops count reads; the permission stays to avoid restarting Home.
- Preview-3's centered, borderless More and app-list refresh after checks remain.

## How to test

The following is copied from approved design D8(b), revision 5 (`4c749bf`), steps
0–8. These are the tester's own-phone checks. Agents do not make calls or send
texts to real phones. RCS step 8 is optional observation only.

0. **Setup.** The driver turns off chat features (RCS) in the spare's Messages app, so test texts arrive as SMS or MMS. The driver records whether RCS was on before this. **Baseline:** open Phone's call history and every unread conversation in Messages until Home shows no numbers with the switch on.
1. Missed call from his phone → Phone `1`. Swipe the missed-call notification → no number.
2. Two texts from his phone (one conversation) → Messages `2`. Swipe the notification → still `2`. A third text → `3`. Tap "Mark as read" on its notification → no number. "Mark as read" marks the whole conversation read (spike C3).
3. One text → `1`. Open that conversation → no number.
4. A picture message → `1`. Swipe its notification → still `1`. A second picture message → `2`. Tap "Mark as read" on its notification → no number.
5. A picture message → `1`. Open the conversation → no number.
6. If Beaux has a second number of his own: a group picture message to the spare and that number → `1`, not one per person. Open it → no number.
7. **Positive baseline first:** one missed call and one text, left unopened → Phone `1`, Messages `1`. Turn the switch off → both numbers gone at once. Turn it on → Phone `1`, Messages `1` again (the same unacknowledged items).
8. Optional, **observation only**: turn chat features back on, send a chat and note what Messages shows. RCS isn't supported (D7), so whatever happens is recorded as an observation in the notes. It is not an acceptance step, and it can't turn off the SMS/MMS count. It helps Beaux judge how useful the number is if most of his chats are RCS.

Record each expected and actual number. Until the tester reports steps 1–7,
counts remain **in testing**. List every skipped step by number (including step 6
if a second owned number is unavailable). If a step disagrees, the next build
turns off that count. The Messages count is never reduced to SMS alone.

**Current spare status:** the driver's part of step 0 is done (chat features off; the switch is
left off for the tester). The spare has **no working SIM** (out of service), so steps 1–7 can't
run until it has one. Steps 1–8 have not been run and remain unverified. The optional RCS
observation has not been made.

## Coverage limits

The counts work with the phone's standard Google Phone and Google Messages apps.
A different app means no number. The emulator's and the spare's exact app versions
are recorded in the evidence (spare: Phone 241.0.991235506, Messages
messages.android_20261002_00_RC00).
Later app updates may change what gets counted.

Messages counts texts (SMS) and picture messages (MMS) saved in Android's shared
text record. Chat (RCS) messages and other apps such as Signal or WhatsApp are not
supported and may be missing from the number. A picture message still waiting to
download is not counted. Rare emergency-number texts on another SIM's subscription
may also be left out. Real picture-message downloads, failed downloads and retries,
carrier voicemail, and picture-message notification swipes and “Mark as read”
are not verified by this emulator run. The latter two need tester step 4.
RCS is not verified; an observation cannot make it supported.

## Build and emulator verification

See [verification.md](evidence/verification.md) for the exact base and build head,
APK SHA-256, test totals, signing comparison, and emulator read-back.
Screenshots and the upgrade record are in [emulator/](evidence/emulator/).
No physical device was used for preparation.

## Spare install and driver checks (D8 a)

Installed in place on the spare on 2026-10-11 at **08:39:29 UTC (01:39:29 PDT)** by the sole
spare driver (PLA-303). APK SHA-256
`cbf8ad4a80f7cbb1a0eb3eb63b09a4f1d53e1b36de0a55e783db8fa31f4a5232`; build head `354bcc1`,
candidate `release/preview-4` @ `5642080`. `upgrade-check.sh` (counts off) ended `exit=0`:
preview-4 `versionCode=4`, Device Owner and lock task kept, first-install date unchanged (tester
data kept), and both 9-1-1 routes asserted exactly `911` with zero calls. Call was never pressed.
See [install read-back](evidence/spare/install-readback.txt) and
[gate log](evidence/spare/upgrade-check.txt).

D8(a) checks passed; the locked-storage (before-unlock) part was not exercised on this device; covered on the emulator (PLA-294/PLA-300 evidence) ([results](evidence/spare/d8a-results.md)):

- **Apps:** Phone (DIALER role) is Google Phone 241.0.991235506. Messages (SMS role) is Google
  Messages messages.android_20261002_00_RC00.phone_dynamic.
- **Chat features (RCS):** they were **on** (carrier status "Not supported"). The driver turned
  them **off** for step 0. To reach Messages settings, the driver chose "Use Messages without an
  account" on Messages' first-run screen. No account was added.
- **Permissions:** after the install, both are not granted and are installer-allowlisted. After the
  switch is turned on, both are granted and stay granted after it is turned off.
- **Switch off:** no numbers, and Home did not restart (same process before and after on/off).
  The spare's counts were zero, so no number showed even with the switch on.
- **Restart:** no number before unlock, then fresh reads after unlock. The spare has no PIN, so
  Android unlocks storage during boot. The locked Direct Boot case is covered on the emulator only.
- **Screenshots:** Home and Phone settings at 100 % and 200 %, switch off and on
  ([d8a/](evidence/spare/d8a/)). They show no names or numbers.
- **The switch is left off**, so the tester turns it on as step 0.

**No working SIM.** The spare's SIM is out of service ("No service"). Steps 1–7 need calls
and texts to reach the spare, so the counts stay **in testing** until the tester has a working
SIM and reports steps 1–7. Tester results and skipped steps: **none reported yet.**

## Known gaps carried from preview-3

- No PIN, recovery code, approvals, “Ask for an app”, trusted-person controls,
  link rules or waits. Unwired actions still say “Not in this test build yet”.
  Sam and Jo on More are sample names. Phone settings currently provides the
  counts switch; other basic settings are not part of this slice.
- This preview keeps existing Plainphone branding in places and the
  `org.plainphone` ID. The count disclosure retains its reviewed LMNOPhone wording;
  the broader name update is separate. More's menu still has thin divider lines;
  Home has none.
- Preview restrictions do not prevent safe mode or factory reset. Android's
  Device Owner defaults still apply. This remains an incomplete preview.
- The spare had no PIN in preview-3, so no lock-screen Emergency button there;
  swipe up and use Phone or More > Emergency. On preview-4 the spare still
  has no lock credential (read back 2026-10-11). Never press Call during an agent check.
- Five known `Pla241ReviewTest` failures are carried from preview-3. They cover
  emergency-floor recovery after journal failure, locked-boot flip failure,
  preservation of an earlier flip failure, enabled-state changes during setup,
  and restoration of fixed restrictions after a failed plan. Their fixes are
  outside this candidate; see the exact failure list in verification.md.
- Unhide persistence remains best effort: power loss can lose the saved unhide,
  and the previously accepted worst case is a restart loop. A successful boot
  read-back does not remove that risk. See [preview-2 notes](../preview-2/NOTES.md).
- The preview-3 spare screenshots reused the post-restart Home image for its
  after-install slot. Preview-4 preparation supplies separately captured emulator
  frames; it does not repair or relabel that older evidence.

The prior Device Owner removal, unhide save-flip and power-cut results remain
historical evidence from preview-2/3, not reruns for preview-4.

## Recovery

The assigned driver names the approved device on both commands:

```sh
adb -s <spare> shell dpm remove-active-admin org.plainphone/.PlainphoneAdminReceiver
adb -s <spare> uninstall org.plainphone
```
