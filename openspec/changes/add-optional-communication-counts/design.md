# Design: optional missed-call and unread-text counts

## Context

Beaux (Telegram 3152) wants missed-call and unread-message counts on Home, as an option, in calm black and white. The risk is not drawing a number. The risk is showing a number that is wrong. A count that disagrees with the Phone or Messages app teaches the user to ignore it, and a stale count is worse than none. So the design rule is: **count from the system record, never from notifications, and hide when unsure.**

## Decisions

### D1. Data sources (to be confirmed by the spike)

| Count | Source | Rule | Permission |
|---|---|---|---|
| Missed calls | `CallLog.Calls.CONTENT_URI` | `TYPE = MISSED_TYPE (3)` and `NEW = 1` (the dialer's "not yet acknowledged" flag; C2 decides `NEW` vs `IS_READ`) | `READ_CALL_LOG` |
| Unread texts | `Telephony.Sms.Inbox` and `Telephony.Mms.Inbox` | `READ = 0`; MMS limited to `MSG_BOX = 1` | `READ_SMS` |

- Projection is `_id` only (or a count). Selection uses only the flag columns. No other column is ever requested. A unit test asserts the exact projection and selection.
- Voicemail (4), rejected (5) and blocked (6) calls are **not** missed calls. Calls the user declined are not "missed" in the Phone app either.
- We count **messages**, not conversations, and **calls**, not callers. Notifications are not counted, so grouped or duplicate notifications can't change the number.
- Sources: [CallLog.Calls](https://developer.android.com/reference/android/provider/CallLog.Calls) (`NEW`, `IS_READ`, `MISSED_TYPE`), [Telephony.TextBasedSmsColumns](https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns) (`READ`, `SEEN`), [Telephony.BaseMmsColumns](https://developer.android.com/reference/android/provider/Telephony.BaseMmsColumns) (`MSG_BOX`, `READ`). Behaviour with the real Phone and Messages apps is unproven until C2–C3.

### D2. Rejected: notification listener

A `NotificationListenerService` could count Phone and Messages notifications. It is rejected: a notification is not an unread item (dismissing it doesn't read the message, and one notification can stand for many messages), and the listener would receive message text and names. Beaux ruled this out explicitly.

### D3. Freshness

- Re-count when Home becomes visible, and on `ContentObserver` changes for the two URIs while Home is visible. No count is saved to storage. After a restart the first number shown comes from a fresh query.
- While a query is running, the previous number for the **same** visible session can stay. A failed query clears it.
- Before the first unlock after boot, the call log and text store are unavailable (credential-encrypted). No number shows.

### D4. Unavailable means hidden

Any of these hides that count, with no error on Home: switch off; permission not held; query throws; provider missing; cursor null. Settings shows one plain line under the switch when the switch is on but a count can't be read: "LMNOPhone can't read missed calls right now" (or "unread texts").

### D5. Permission handling (C1 decides the mechanism)

- LMNOPhone holds `READ_CALL_LOG` and `READ_SMS` only while the switch is on, if releasing them doesn't restart Home in a way the user sees (C5). Otherwise it keeps them and never queries while the switch is off.
- Both are hard-restricted permissions. They can only be granted when the installer allowlists them. C1 finds out which of our install paths (adb test install, LMNOPhone's own update installer, setup) allowlists them, and whether the Device Owner can self-grant with `setPermissionGrantState`.
- This is LMNOPhone's own permission. It doesn't touch the app-approval permission lists or any approved app's grants.

### D6. Visual

- Number to the right of the Phone and Messages labels, inside the existing row and touch target. Same ink colour and typeface as the label, regular weight, tabular figures, no shape around it. 1–99, then "99+".
- Accessibility: the row is one control. Name "Phone, 1 missed call" / "Phone, 3 missed calls" / "Messages, 1 unread text" / "Messages, 4 unread texts". Plurals through Android string resources.
- At the largest text size the number may wrap below the label inside the same row, but must never be cut off or push Phone and Messages off-screen.

### D7. Coverage disclosure

Settings text under the switch states what is counted. Default (until C3 says otherwise): "Counts texts and picture messages. Chat messages (RCS) may not be counted." Unsupported: RCS kept only in Google Messages' own database, any third-party app, a non-default SMS app's private store.

## Feasibility questions (spike, emulator only)

- **C1. Permission path.** On API 35 and 36: can LMNOPhone (Device Owner) hold `READ_CALL_LOG` and `READ_SMS` when installed by (a) `adb install`, (b) its own `PackageInstaller` update path with `setWhitelistedRestrictedPermissions` or the equivalent, (c) a fresh install during setup? Does `DevicePolicyManager.setPermissionGrantState` on itself work for each? Record `dumpsys package` restriction flags.
- **C2. Missed-call semantics.** With the stock dialer on the AVD (and the spare's dialer package named from read-only readback by its driver): which of `NEW` / `IS_READ` changes when the user (i) opens call history, (ii) swipes away the missed-call notification, (iii) taps the notification, (iv) calls back, (v) the call is rejected or goes to voicemail. Pick the column that matches the dialer's own "missed" state.
- **C3. Message semantics.** With the default SMS app on the AVD: does `READ` flip to 1 on opening the conversation? On swiping the notification? On "Mark as read" from the notification? MMS group message counted once? Are RCS messages in the provider at all (test if any RCS path exists; otherwise state "not verified")?
- **C4. Change signals.** Do `ContentObserver`s on the two URIs fire for each transition in C2–C3, and how fast?
- **C5. Permission loss.** Revoke each permission while Home is visible: does the process die, does the query throw, and what does Home show after?
- **C6. Restart and locked state.** After reboot, before unlock and after unlock: what do the queries return?
- **C7. Privacy proof.** Show the exact projection and selection, and that nothing else is read or logged.

If C1 fails for every install path we can use, or C2/C3 show the system flags don't track what the user sees, that's a product limit. The Lead reports it to Beaux with the exact limit; we don't ship a guess.

## Risks

- **Holding `READ_SMS` widens what LMNOPhone could read.** Mitigation: projection test, no content columns, hold only while on (if C5 allows), and disclose.
- **RCS gap.** If most messages between Beaux and Deanna are RCS, the Messages count may often be low. Disclosed in Settings; Beaux decides after testing whether that's useful.
- **Dialer differences.** The spare's dialer may set flags differently from the AVD's. Verified on the spare during the preview that ships this.
