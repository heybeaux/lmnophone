# Design: optional missed-call and unread-text counts

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
- No other column is requested. No sort order. The count is `Cursor.getCount()`, and no cursor value is ever read, not even `_id`. A unit test asserts the exact URI, projection and selection strings.
- **Missed-call selection.** In both tested Google Phone versions, acknowledging a missed call sets `NEW=0` and `IS_READ=1` together. Acknowledging means leaving call history, swiping the notification, tapping it, or calling back. The extra `is_read IS NOT 1` matches AOSP Dialer's own notification query (`CallLogNotificationsQueryHelper`) and excludes rows that are read but still `NEW=1`. `IS NOT 1` also handles NULL.
- A declined call is `TYPE=5` and voicemail is `TYPE=4`; neither is counted. A voicemail that comes with its own missed-call row (`TYPE=3`) is counted, because the Phone app shows that row as missed too.
- **Notification swipes.** Swiping a missed-call notification *acknowledges* the call in Google Phone, so the count drops; this matches what the Phone app shows. Swiping a text notification does *not* mark it read, so the count stays; this matches Messages, which still shows the conversation as unread. "Mark as read" from the notification marks it read, and the count drops. In every case the count follows the app's own record.
- Sources: [CallLog.Calls](https://developer.android.com/reference/android/provider/CallLog.Calls), [Telephony.TextBasedSmsColumns](https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns), [AOSP Dialer notification query](https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/app/calllog/CallLogNotificationsQueryHelper.java), and the spike's evidence files `*-missed-arrival`, `*-swipe-call`, `*-call-{tap,callback,decline}`, `*-sms-*`, `*-mms-*`.

### D2. Rejected: notification listener

A `NotificationListenerService` could count Phone and Messages notifications. It is rejected: a notification is not an unread item (dismissing a text notification leaves the message unread, and one notification can stand for many messages), and the listener would receive message text and names. Beaux ruled this out explicitly.

### D3. Freshness (C4)

- Query again **every time Home becomes visible**, and on `ContentObserver` changes while Home is visible.
- Observe `content://call_log/calls` and **`content://mms-sms/`**, both with `notifyForDescendants = true`. The combined URI is required: opening a conversation in Messages notified only `content://mms-sms/`, not the separate SMS or MMS URIs.
- Foreground callbacks arrived within about 50–160 ms of the event. Background callbacks can be delayed by about 10 s, so the resume query is what keeps the count correct. It doesn't rely on callbacks.
- Queries run off the UI thread (one API 36 query took 610 ms). Coalesce bursts of callbacks; the number of callbacks is not a count.
- **No count is saved to storage.** A restart starts with no number until the first query succeeds. While a re-query runs, the previous number from the **same visible session** may stay; a failed query clears it.

### D4. Unavailable means hidden (C5, C6)

Each count is computed separately and hidden on its own. A count is hidden when:
- the switch is off;
- `checkSelfPermission` for its permission is not granted;
- the user hasn't unlocked since boot (`UserManager.isUserUnlocked() == false`). Before unlock the spike saw null cursors for calls and MMS and a `SQLiteException` for SMS;
- the cursor is null, or the query throws (`SecurityException`, `SQLiteException`, `IllegalArgumentException`, any other);
- for Messages, **either** the SMS or the MMS query fails. A partial sum is never shown.

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

Settings text (one resource): "Counts texts and picture messages. Chat messages (RCS) and other apps are not counted." That wording is accurate for the default setup. Because RCS isn't verified either way, "may not be counted" would hint at RCS coverage we can't support. A second line covers the permission: "LMNOPhone keeps permission to check calls and texts after you turn this off. It never reads what they say."

**On the spare:** the Phone and Messages versions there are newer than the ones the spike tested. The preview that ships this verifies the C2 and C3 transitions on the spare (D8) before calling them supported there.

### D8. Spare-phone verification (with the preview that ships it)

The spare's sole driver runs these checks, without live emergency calls. Test calls and texts come only from Beaux's own second phone or another number Beaux names; agents never place calls or texts to third parties:
1. Read back `cmd role get-role-holders` for DIALER and SMS, plus both apps' versions. Read back `dumpsys package` restriction flags for both permissions after the in-place install, and after turning the switch on.
2. Switch off: no numbers.
3. Switch on, then a missed call → `1`. Swipe the missed-call notification → the number is gone.
4. A text arrives → `1`. Swipe its notification → still `1`. Open the conversation → the number is gone.
5. Restart the phone: no number before unlock, then a fresh count after unlock.
6. Switch off → both numbers gone at once, with no process restart.
7. Screenshots at 100 % and 200 % with both counts showing.

If the spare's Phone or Messages app behaves differently, the Lead reports the exact difference before shipping. The count is not adjusted to compensate.

## Risks

- **Holding `READ_SMS` and `READ_CALL_LOG` widens what LMNOPhone could read.** Mitigations: a single query site, a projection test, no cursor value reads, grants made only when the user first turns counts on, and disclosure in Settings.
- **RCS gap.** If most messages between Beaux and Deanna are RCS chats, the Messages count will often be low or zero. This is disclosed in Settings. Beaux decides after testing on the spare whether it is useful, and this is called out in the release notes.
- **Dialer and Messages versions.** The flags were proven only on the tested versions. D8 checks the spare's versions.
- **Self-update allowlist.** A future in-app updater could silently break the grant by passing an empty allowlist (D5). The host slice adds a test or a code comment wherever a `SessionParams` is built.
