# Design: optional missed-call and unread-text counts

**Revision 3** (Lead, PLA-287). Resolves the Verifier's REQUEST CHANGES on revision 2 (PLA-295, findings 1–6): publication fencing and a refresh deadline (D3), a stable combined Messages read (D1, D4), a supported-app gate (D4), a two-part spare check with Beaux's test as the release gate (D8), qualified coverage wording (D7), and the default-versus-update scenario (spec). Revision 2 is `a16e60e`.

**Revision 2** (Lead, PLA-287). The spike PLA-291 (`spike/communication-counts` @ `983e6f1`, `spikes/communication-counts/RESULT.md`) answered C1–C7 on Google APIs API 35 and 36 emulators, with Google Phone and Google Messages as the role holders. Its verdict is **feasible**, with the limits listed under Coverage. Revision 1 is commit `091243b`.

## Context

Beaux (Telegram 3152) wants missed-call and unread-message counts on Home, as an option, in calm black and white. Drawing a number is easy. The risk is a number that is wrong: a count that disagrees with the Phone or Messages app teaches the user to ignore it, and a stale count is worse than none. So the rule is: **count from the system record, never from notifications, and hide when unsure.**

## Decisions

### D1. Data sources and exact queries (verified, C2, C3, C7)

| Count | URI | Projection | Selection |
|---|---|---|---|
| Missed calls | `content://call_log/calls` | `_id` | `type = 3 AND new = 1 AND is_read IS NOT 1` |
| Unread SMS | `content://sms` | `_id` | `type = 1 AND read = 0` |
| Unread MMS | `content://mms` | `_id` | `msg_box = 1 AND read = 0` |

- **Messages count = unread SMS rows + unread MMS rows.** We count messages, not conversations. A group MMS is one row (the spike's group fixture counted 1, not 3).
- **The two message reads must agree with each other** (PLA-295 finding 2). Two separate queries can each succeed and still add up to a total that never existed, if messages change between them. The host uses one of two methods:
  - **(a) Preferred: a single SQL statement.** Use one statement through `content://mms-sms/` if the host engineer proves on the emulator that it uses only the D1 flag columns and returns exactly the two-query total on the spike fixtures. One SQLite statement reads one snapshot.
  - **(b) Otherwise: a stable double read.** Read SMS, then MMS, then both again. Publish only if both pairs match and the generation (D3) didn't change. Retry up to 3 times, then treat the count as unavailable.
  
  Any change between the reads also fires a `content://mms-sms/` callback while Home is visible (C4), which bumps the generation, so an interleaved total is thrown away rather than shown. **Residual risk, accepted by the Lead:** a change that is made and undone inside one read window, with its callback lost. Nothing in the spike suggests foreground callbacks get lost.
- No other column is requested. No sort order. The count is `Cursor.getCount()`, and no cursor value is ever read, not even `_id`. A unit test asserts the exact URI, projection and selection strings.
- **Missed-call selection.** In both tested Google Phone versions, acknowledging a missed call sets `NEW=0` and `IS_READ=1` together. Acknowledging means leaving call history, swiping the notification, tapping it, or calling back. The extra `is_read IS NOT 1` matches AOSP Dialer's own notification query (`CallLogNotificationsQueryHelper`) and excludes rows that are read but still `NEW=1`. `IS NOT 1` also handles NULL.
- A declined call is `TYPE=5` and voicemail is `TYPE=4`; neither is counted. A voicemail that comes with its own missed-call row (`TYPE=3`) is counted, because the Phone app shows that row as missed too.
- **Notification swipes.** Swiping a missed-call notification *acknowledges* the call in Google Phone, so the count drops; this matches what the Phone app shows. Swiping a text notification does *not* mark it read, so the count stays; this matches Messages, which still shows the conversation as unread. "Mark as read" from the notification marks it read, and the count drops. In every case the count follows the app's own record.
- Sources: [CallLog.Calls](https://developer.android.com/reference/android/provider/CallLog.Calls), [Telephony.TextBasedSmsColumns](https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns), [AOSP Dialer notification query](https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/app/calllog/CallLogNotificationsQueryHelper.java), and the spike's evidence files `*-missed-arrival`, `*-swipe-call`, `*-call-{tap,callback,decline}`, `*-sms-*`, `*-mms-*`.

### D2. Rejected: notification listener

A `NotificationListenerService` could count Phone and Messages notifications. It is rejected: a notification is not an unread item (dismissing a text notification leaves the message unread, and one notification can stand for many messages), and the listener would receive message text and names. Beaux ruled this out explicitly.

### D3. Freshness and publication fence (C4; PLA-295 finding 1)

- **Each visible interval starts empty.** When Home becomes visible, it shows no numbers. A number appears only after a read that started during this visible interval succeeds. The number may take up to about 0.6 s to appear; we accept that small pop-in.
- **Observers first.** While Home is visible, register `content://call_log/calls` and **`content://mms-sms/`**, both with `notifyForDescendants = true`, **before** the first read. The combined URI is required: opening a conversation in Messages notified only `content://mms-sms/`, not the separate SMS or MMS URIs. Unregister the observers when Home is hidden.
- **Generation.** One counter goes up on each of these:
  - Home becomes visible or hidden;
  - the switch changes;
  - a permission or the supported-app status changes (D4);
  - the package for either role holder is added, replaced, removed, enabled or disabled;
  - any observer callback.

  Each read carries the generation it started with. It may publish only if that generation is still current when the read finishes **and** every D4 gate still passes, checked again right before publishing. Otherwise its result is thrown away, and a new read is scheduled.
- **Refresh deadline.** When a callback arrives while Home is visible, the affected number may stay for at most **1 s** while a new read runs. If no fresh result is published by then, the number is hidden until one is. Bursts of callbacks are merged into one read 200 ms after the last callback, and the 1 s deadline still applies from the first callback.
- **Query timeout.** If a read takes longer than 3 s, that count is unavailable for this attempt, and it is hidden. The next callback or visible interval tries again. Queries run off the UI thread (one API 36 query took 610 ms).
- **No count is ever saved to storage.**
- **Measurement limit.** Observer latency was measured, not guaranteed. The spike saw about 50–160 ms in the foreground and about 10 s in the background. While Home is visible, a change can be reflected late only within the 1 s deadline, after which the number is hidden. If a change ever produced no callback while Home was visible (the spike never saw this), it would be fixed at the next visible interval. Controlled-scheduler tests cover:
  - a callback during a read;
  - a delayed completion after a newer generation;
  - off → on;
  - permission loss during a read;
  - stop then resume;
  - process restart;
  - a hung query.

### D4. Unavailable means hidden (C5, C6)

Each count is computed separately and hidden on its own. A count is hidden when:
- the switch is off;
- `checkSelfPermission` for its permission is not granted;
- the user hasn't unlocked since boot (`UserManager.isUserUnlocked() == false`). Before unlock the spike saw null cursors for calls and MMS and a `SQLiteException` for SMS;
- the cursor is null, or the query throws (`SecurityException`, `SQLiteException`, `IllegalArgumentException`, any other);
- for Messages, **either** the SMS or the MMS query fails, or the stable read (D1) doesn't settle. A partial or interleaved sum is never shown;
- **the app behind the Home entry isn't a supported one** (PLA-295 finding 3; see below).

**Supported-app gate.** A count's meaning depends on the app that writes the flags:
- **Missed calls** are supported only while the DIALER role holder is `com.google.android.dialer`, installed and enabled, and Home's Phone entry opens that same package.
- **Unread texts** are supported only while the SMS role holder is `com.google.android.apps.messaging`, installed and enabled, and Home's Messages entry opens that same package.

When any of these fails (another app, no holder, the app disabled or hidden, or the entry pointing elsewhere), that count is hidden, and Settings shows "Counts work only with the phone's standard Phone and Messages apps." The gate is checked at each visible interval and again right before publishing. A role or package change also bumps the generation, so a read that was already running when the change happened is thrown away.

**Versions aren't gated.** Play keeps both apps up to date automatically, so gating on exact versions would turn counts off after almost every update. Instead, the spike records the versions it tested, D8 records the spare's versions and checks how they behave, and the release notes say the counts were checked on those versions. **Residual risk, accepted by the Lead:** a later app version could change how it sets these flags. If Beaux's checks or later use show that, we turn off that count in the next build rather than adjust the number. No emergency-floor, lock-task or app-approval behaviour is involved in this gate; it only reads role holders and package state.

Null and exceptions are **never** treated as zero. Settings shows the existing line when the switch is on but a count can't be read: "LMNOPhone can't read missed calls right now" (or "unread texts"). Logs record only the count source name, the outcome and the exception **class**, never the message, URI arguments or values.

### D5. Permissions (C1, C5)

- **Manifest:** `READ_CALL_LOG` and `READ_SMS`. Both are hard-restricted. They can be granted only if the installer allowlisted them (`RESTRICTION_INSTALLER_EXEMPT`).
- **Install paths:**
  - `adb install` without `--restrict-permissions` allowlists them. This is how previews are installed on the spare, including in-place upgrades from a build that didn't declare them (spike matrix, "update APK that previously declared neither permission, default").
  - Any LMNOPhone `PackageInstaller` self-update path must leave the allowlist at its default or set `{READ_CALL_LOG, READ_SMS}` explicitly. It must **never** pass an empty set. An empty set leaves `APPLY_RESTRICTION`, and the grant then fails silently.
- **Grant:** when the user turns the switch on, LMNOPhone (Device Owner) calls `setPermissionGrantState(self, …, GRANTED)` for both. It then checks `checkSelfPermission` **and** runs one successful query before counting that source as available. The DPM call's `true` return is not proof (spike: `result=true state=0` with the grant still denied).
- **Switch off keeps the grants.** Revoking a runtime permission kills the process, and LMNOPhone is the Home app (C5: the Home process died on both API levels). So turning the switch off immediately unregisters observers, cancels pending queries, clears the shown numbers and stops all reads. The grants stay in place, unused. The Settings note discloses that LMNOPhone keeps permission to check calls and texts after the switch is turned off.
- These are LMNOPhone's own permissions. Nothing here touches the approved-app permission lists, other apps' grants or any lockdown policy.

### D6. Visual

Unchanged from revision 1, and built and reviewed in PLA-292 (`mvp/home-counts-ui` @ `dfe553a`):
- Plain digits to the right of the Phone and Messages labels, inside the existing row and touch target, in ink, with tabular figures. `99+` above 99.
- Spoken names include the count, with plurals.
- At large text the number may wrap inside the row, but is never clipped.

### D7. Coverage (C3)

**Counted:** SMS and MMS messages that the default Messages app stores in Android's public text store (`content://sms`, `content://mms`), using its read flags.

**Not counted, or not verified:**
- **Chat (RCS) messages: not verified.** No RCS account could be set up on the emulator. Google Messages normally keeps RCS chats in its own storage. A separate, managed "archival" mode can copy RCS into the public store, but it isn't enabled and doesn't promise correct read flags. So we don't claim RCS is counted, and we don't claim it never appears.
- Messages from any other app (WhatsApp, Signal and so on).
- Carrier edge cases not tested on the emulator: real MMS download and retry, MMS notification swipe and "Mark as read", and carrier voicemail.

Settings text (one resource, PLA-295 finding 5): "Counts texts and picture messages. Chat (RCS) messages and other apps aren't supported, so they may be missing from the number." This doesn't claim RCS rows can never be counted. It says RCS isn't supported, so the number may leave them out. The final wording and the permission note go into `communication_counts.xml`, the Phone settings screen and `docs/ux/flows-user.md`, with large-text screenshots reviewed (task 2.2b). A second line covers the permission: "LMNOPhone keeps permission to check calls and texts after you turn this off. It never reads what they say."

**On the spare:** the Phone and Messages versions there are newer than the ones the spike tested. The preview that ships this verifies the C2 and C3 transitions on the spare (D8) before calling them supported there.

### D8. Spare-phone checks and the release gate (PLA-295 finding 4)

The emulator gate (PLA-294) covers these: unavailable sources, a partial or interleaved message read, permission loss, the generation fence, the supported-app gate, restart and Direct Boot. The spare adds two parts.

**(a) Driver checks.** The spare's sole driver does these with no calls or texts:
- read back the DIALER and SMS role holders and both apps' versions;
- read back the `dumpsys package` restriction flags for both permissions, after the in-place install and after the switch is turned on;
- switch off: no numbers, and the same PID (no Home restart);
- restart: no number before unlock, then a fresh read after unlock;
- Settings coverage and permission lines, screenshots at 100 % and 200 %, redacted.

**(b) Beaux's test (the release gate for calling a count supported).** In the preview notes' how-to-test, Beaux uses only his own phones; agents never call or text anyone:
1. Missed call → `1`. Swipe the notification → gone.
2. Two texts → `2`. Swipe → still `2`. "Mark as read" on one → `1`. Open the conversation → gone.
3. A picture message → `1`. Open it → gone.
4. A group picture message to the spare and one other of his numbers → `1`, not one per person.
5. Turn the switch off and on again.

He replies with what he saw. Until he does, the notes call the counts "in testing". If a step fails, the next build turns off that count; a partial SMS-only number is never shown under the SMS and MMS promise. If the spare has no working SIM, the notes say so, and the counts stay "in testing".

**Not verified, and disclosed in the notes:**
- MMS download failure and retry, and pending downloads (the spare's own record is still what's counted);
- carrier voicemail;
- RCS.

## Risks

- **Holding `READ_SMS` and `READ_CALL_LOG` widens what LMNOPhone could read.** Mitigations: a single query site, a projection test, no cursor value reads, grants made only when the user first turns counts on, and disclosure in Settings.
- **RCS gap.** If most messages between Beaux and Deanna are RCS chats, the Messages count will often be low or zero. This is disclosed in Settings. Beaux decides after testing on the spare whether it is useful, and this is called out in the release notes.
- **Dialer and Messages versions.** The flags were proven only on the tested versions. D8 checks the spare's versions.
- **Self-update allowlist.** A future in-app updater could silently break the grant by passing an empty allowlist (D5). The host slice adds a test or a code comment wherever a `SessionParams` is built.
