# Design: optional missed-call and unread-text counts

**Revision 5** (Lead, PLA-287). Applies the Verifier's APPROVE WITH CHANGES on revision 4 (PLA-301, items 1–4). It states the coverage the caller actually sees, and the cases where the combined query and the per-table oracle can differ (D1). It permits the transport label in the spec. It makes the RCS step observational and the switch step non-vacuous (D8). It aligns the proposal's freshness wording. Revision 4 is `06fe2ec`.

**Revision 4** (Lead, PLA-287). Resolves the Verifier's REQUEST CHANGES on revision 3 (PLA-297, findings 1–3). Messages now uses one SQL statement, and the double-read fallback is removed (D1). The 1 s deadline is measured from the received invalidation, and a periodic re-read bounds age (D3). D8 (b) gets a baseline, exact expected numbers, MMS swipe and "Mark as read" steps, and an RCS-off condition. Revision 3 is `78cbbee`.

**Revision 3** (Lead, PLA-287). Resolves the Verifier's REQUEST CHANGES on revision 2 (PLA-295, findings 1–6): publication fencing and a refresh deadline (D3), a stable combined Messages read (D1, D4), a supported-app gate (D4), a two-part spare check with Beaux's test as the release gate (D8), qualified coverage wording (D7), and the default-versus-update scenario (spec). Revision 2 is `a16e60e`.

**Revision 2** (Lead, PLA-287). The spike PLA-291 (`spike/communication-counts` @ `983e6f1`, `spikes/communication-counts/RESULT.md`) answered C1–C7 on Google APIs API 35 and 36 emulators, with Google Phone and Google Messages as the role holders. Its verdict is **feasible**, with the limits listed under Coverage. Revision 1 is commit `091243b`.

## Context

Beaux (Telegram 3152) wants missed-call and unread-message counts on Home, as an option, in calm black and white. Drawing a number is easy. The risk is a number that is wrong: a count that disagrees with the Phone or Messages app teaches the user to ignore it, and a stale count is worse than none. So the rule is: **count from the system record, never from notifications, and hide when unsure.**

## Decisions

### D1. Data sources and exact queries (verified, C2, C3, C7)

| Count | URI | Projection | Selection |
|---|---|---|---|
| Missed calls | `content://call_log/calls` | `_id` | `type = 3 AND new = 1 AND is_read IS NOT 1` |
| Unread texts (SMS + MMS, one statement) | `content://mms-sms/complete-conversations` | `_id`, `transport_type` | `read = 0` |

The revision 2 per-table queries (`content://sms`: `type = 1 AND read = 0`; `content://mms`: `msg_box = 1 AND read = 0`) are kept **only as the test oracle** for parity checks. They are never added together for display.

- **Messages count = unread SMS rows + unread MMS rows.** We count messages, not conversations. A group MMS is one row (the spike's group fixture counted 1, not 3).
- **One statement, one snapshot** (PLA-295 finding 2, PLA-297 finding 1). Two separate reads can add up to a total that never existed, and comparing repeated counts can't detect that: the Verifier built a `[5,7,5,7]` schedule with delayed callbacks that publishes 12 when the real total never went above 7. So the Messages number comes from **one** query, and the double read is removed. Source (AOSP `MmsSmsProvider`, android15-release):
  - `complete-conversations` → `getCompleteConversations` → `buildConversationQuery`, run as **one** `rawQuery`: a `UNION` of an `sms` sub-select and a `pdu` sub-select. SQLite runs one statement against one read snapshot. `getCount()` fills the first cursor window with `countAllRows`, which steps that single statement once.
  - The caller's selection is added to both sub-selects, so it may use only columns both tables have. `read` is one of them; `type` and `msg_box` aren't.
  - The provider itself excludes drafts in both tables, and limits MMS to sent, received and download-notification PDUs (`MMS_CONVERSATION_CONSTRAINT`). **For a caller that isn't the default SMS app (LMNOPhone)**, the provider serves restricted views (`MmsSmsDatabaseHelper`): SMS inbox and sent only, and MMS inbox and sent **without download-notification PDUs** (`m_type` 130). So an MMS that hasn't been downloaded yet is **not** counted. Pending and failed downloads stay outside verified coverage (D7, D8).
  - `SmsProvider` and `MmsProvider` both set `read = 1` on insert for every non-inbox message ("Mark all non-inbox messages read"). So `read = 0` selects unread inbox messages, which is what the oracle counts.
  - The projection must include `transport_type` (the `sms`/`mms` discriminator). The union is `DISTINCT`, so an SMS and an MMS with the same `_id` would otherwise merge into one.
  - **Subscription filter.** `ProviderUtil.getSelectionBySubIds` limits rows to the user's subscriptions. On the normal main-user path it adds `sub_id = -1` (messages restored from another device), as long as the subscription service is available. If no subscription list is available, the route returns an empty cursor.
  - **Where the combined query and the oracle can differ** (PLA-301 item 1). `SmsProvider.query` adds an `OR` exception for emergency-number messages on calling-capable devices. That lets such an SMS be counted even on a subscription the user's filter excludes. `MmsSmsProvider`'s combined route adds only the subscription filter. So one unread emergency-number SMS on an excluded subscription counts in the SMS oracle but not in the combined number. **Parity is therefore expected on the bounded host fixtures, not on every possible row in the store.** We don't widen permissions or read addresses to close this gap. The count may leave out rare emergency-number texts on another SIM's subscription.
  
  **Host proof required (PLA-300):** on the emulator, (i) parity with the oracle on the spike fixtures, using rows on an **included subscription** and **restored rows with `sub_id = -1`**; (ii) a row on an **excluded subscription** is counted by neither, if the AVD lets you create one, otherwise recorded as not tested; (iii) sent and outbox messages, drafts and an unread **download-notification MMS** (`m_type` 130) are not counted; (iv) an SMS and an MMS with **equal `_id` and equal normalized date** count as 2 (equal `_id` alone wouldn't expose a missing discriminator); (v) it works for a `READ_SMS` holder that isn't the default SMS app. If this query fails, returns null or isn't supported, **the Messages number is hidden**. There is no fallback. **Residual:** a non-inbox row that some app later sets back to `read = 0` would be counted. Neither provider nor the tested Messages app does that.
- No other column is requested (`transport_type` is the provider's fixed `sms`/`mms` label, not message data). No sort order. The count is `Cursor.getCount()`, and no cursor value is ever read, not even `_id`. A unit test asserts the exact URI, projection and selection strings.
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

  Each read carries the generation it started with. It may publish only if that generation is still current when the read finishes (meaning **no invalidation was received** during the read; this doesn't prove the data didn't change) **and** every D4 gate still passes, checked again right before publishing. Otherwise its result is thrown away, and a new read is scheduled.
- **Refresh deadline.** When LMNOPhone **receives** a change callback while Home is visible, the affected number may stay for at most **1 s** after that callback while a new read runs. If no fresh result is published by then, the number is hidden until one is. Bursts are merged into one read 200 ms after the last callback; the 1 s still runs from the first callback.
- **Age bound** (PLA-297 finding 2). The 1 s deadline can't cover Android's own delay in delivering the callback. That delay is extra, and this mechanism doesn't limit it. So, independently, Home re-reads both counts **every 10 s while it is visible**. A shown number must come from a read that **started at most 13 s ago** (10 s period plus the 3 s timeout); otherwise it is hidden. This is the strict bound: a change to the record shows on Home, or the number disappears, within about 13 s at most, and usually within about 1.2 s (callback about 0.05–0.16 s, then a read).
- **Query timeout.** If a read takes longer than 3 s, that count is unavailable for this attempt, and it is hidden. The next callback or visible interval tries again. Queries run off the UI thread (one API 36 query took 610 ms).
- **No count is ever saved to storage.**
- **Measurement limit.** Observer latency was measured, not guaranteed. The spike saw about 50–160 ms in the foreground and about 10 s in the background. The guarantee is the age bound above, not callback speed. Controlled-scheduler tests cover:
  - a callback delivered late (1.5 s after the change), and no callback at all: the periodic re-read corrects or hides the number within 13 s;
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
- for Messages, the single combined query (D1) fails, returns null or times out. No partial, per-table or summed number is ever shown;
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
- A picture message that hasn't been downloaded yet. LMNOPhone isn't the default SMS app, so the provider hides download notices from it (D1).
- An emergency-number text on a subscription outside the user's filter (D1, rare).
- Carrier edge cases not tested on the emulator: real MMS download and retry, MMS notification swipe and "Mark as read", and carrier voicemail.

Settings text (one resource, PLA-295 finding 5): "Counts texts and picture messages. Chat (RCS) messages and other apps aren't supported, so they may be missing from the number." This doesn't claim RCS rows can never be counted. It says RCS isn't supported, so the number may leave them out. The final wording and the permission note go into `communication_counts.xml`, the Phone settings screen and `docs/ux/flows-user.md`, with large-text screenshots reviewed (task 2.2b). A second line covers the permission: "LMNOPhone keeps permission to check calls and texts after you turn this off. It never reads what they say."

**On the spare:** the Phone and Messages versions there are newer than the ones the spike tested. The preview that ships this verifies the C2 and C3 transitions on the spare (D8) before calling them supported there.

### D8. Spare-phone checks and the release gate (PLA-295 finding 4)

The emulator gate (PLA-294) covers these: unavailable sources, a partial or interleaved message read, permission loss, the generation fence, the supported-app gate, restart and Direct Boot. The spare adds two parts.

**(a) Driver checks.** The spare's sole driver does these with no calls or texts:
- read back the DIALER and SMS role holders and both apps' versions, and whether chat features (RCS) are on in Messages;
- read back the `dumpsys package` restriction flags for both permissions, after the in-place install and after the switch is turned on;
- switch off: no numbers, and the same PID (no Home restart);
- restart: no number before unlock, then a fresh read after unlock;
- Settings coverage and permission lines, screenshots at 100 % and 200 %, redacted.

**(b) Beaux's test (the release gate for calling a count supported).** In the preview notes' how-to-test, Beaux uses only his own phones; agents never call or text anyone. Spike evidence gives the expected result for each step (PLA-297 finding 3):

0. **Setup.** The driver turns off chat features (RCS) in the spare's Messages app, so test texts arrive as SMS or MMS. The driver records whether RCS was on before this. **Baseline:** open Phone's call history and every unread conversation in Messages until Home shows no numbers with the switch on.
1. Missed call from his phone → Phone `1`. Swipe the missed-call notification → no number.
2. Two texts from his phone (one conversation) → Messages `2`. Swipe the notification → still `2`. A third text → `3`. Tap "Mark as read" on its notification → no number. "Mark as read" marks the whole conversation read (spike C3).
3. One text → `1`. Open that conversation → no number.
4. A picture message → `1`. Swipe its notification → still `1`. A second picture message → `2`. Tap "Mark as read" on its notification → no number.
5. A picture message → `1`. Open the conversation → no number.
6. If Beaux has a second number of his own: a group picture message to the spare and that number → `1`, not one per person. Open it → no number.
7. **Positive baseline first:** one missed call and one text, left unopened → Phone `1`, Messages `1`. Turn the switch off → both numbers gone at once. Turn it on → Phone `1`, Messages `1` again (the same unacknowledged items).
8. Optional, **observation only**: turn chat features back on, send a chat and note what Messages shows. RCS isn't supported (D7), so whatever happens is recorded as an observation in the notes. It is not an acceptance step, and it can't turn off the SMS/MMS count. It helps Beaux judge how useful the number is if most of his chats are RCS.

He replies with what he saw. Until he does, the notes call the counts "in testing". If any of steps 1–7 disagrees with its expected result, the next build turns off that count; a partial SMS-only number is never shown under the SMS and MMS promise. If the spare has no working SIM, the notes say so, and the counts stay "in testing". **Any step he can't run (for example step 6) is listed as not verified in the notes.**

**Not verified, and disclosed in the notes:**
- any step from (b) that wasn't run, by number. This includes the MMS notification swipe and "Mark as read" (step 4), which stay unverified until Beaux runs them;
- MMS download failure and retry, and pending downloads (the spare's own record is still what's counted);
- carrier voicemail;
- RCS.

## Risks

- **Provider route.** `complete-conversations` is a provider URI, not a public SDK constant. If a later Android or provider changes it, the query fails and the Messages number is hidden. It is never replaced with a two-query sum.
- **Holding `READ_SMS` and `READ_CALL_LOG` widens what LMNOPhone could read.** Mitigations: a single query site, a projection test, no cursor value reads, grants made only when the user first turns counts on, and disclosure in Settings.
- **RCS gap.** If most messages between the first two users are RCS chats, the Messages count will often be low or zero. This is disclosed in Settings. Beaux decides after testing on the spare whether it is useful, and this is called out in the release notes.
- **Dialer and Messages versions.** The flags were proven only on the tested versions. D8 checks the spare's versions.
- **Self-update allowlist.** A future in-app updater could silently break the grant by passing an empty allowlist (D5). The host slice adds a test or a code comment wherever a `SessionParams` is built.
