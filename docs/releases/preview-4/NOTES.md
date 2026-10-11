# preview-4: draft build notes

**Counts are in testing.** This candidate adds optional Home counts to preview-3.
It is prepared for Lead review and the spare's driver; a built APK and an emulator
install do not establish an install on the spare.

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

**Current spare status:** steps 0–8 have not been run for preview-4. Steps 1–7
remain unverified on the spare. The optional RCS observation has not been made.
A working SIM has not been confirmed for this candidate.

## Coverage limits

The counts work with the phone's standard Google Phone and Google Messages apps.
A different app means no number. This emulator's exact app versions are recorded
in the evidence; the spare's driver must record its versions before testing.
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

## TODO(spare): driver completes after Lead review

- Install time (UTC and America/Vancouver), exact APK SHA-256 and build head:
  **TODO(spare)**. Use an in-place upgrade and preserve tester data/settings.
  Run `app/tools/preview-4/upgrade-check.sh` with `PLAINPHONE_SPARE_DRIVER=1`
  as the sole assigned spare driver. Never press Call.
- D8(a): Phone/Messages role holders and versions; RCS state before changes:
  **TODO(spare)**.
- D8(a): permission restriction flags after install and after switching on:
  **TODO(spare)**.
- D8(a): switch off shows no numbers, with the same Home PID:
  **TODO(spare)**.
- D8(a): restart, no number before unlock, fresh reads after unlock:
  **TODO(spare)**.
- D8(a): Settings coverage and permission lines at 100% and 200%, redacted:
  **TODO(spare)**.
- Working SIM, tester D8(b) results and skipped steps: **TODO(spare)**.

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
  swipe up and use Phone or More > Emergency. This candidate's spare state
  still needs the driver's read-back. Never press Call during an agent check.
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
