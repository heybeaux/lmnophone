# Communication counts: C1–C7 results

**THROWAWAY feasibility spike — not production code.** Recorded 2026-10-10.
Issue PLA-291; spec base
`091243b`. Tested on owned, newly created Google APIs arm64 AVDs only. No physical
phone, account, real contact, or real call was used. Both APKs remain `testOnly`.
The probe was never the Phone or Messages role holder.

## Recommendation for the Lead

The system stores support the bounded feature on the two tested builds, subject
to the coverage limits below. There is no basis here for a universal Messages
unread count or a guarantee about an untested dialer.

- **D1:** project only `_id`; count missed calls with
  `type = 3 AND new = 1 AND is_read IS NOT 1`. The observed actions changed NEW and
  IS_READ together. The extra read predicate follows AOSP's own notification
  selection and excludes an already-read entry whose NEW flag is still set.
  `IS NOT 1` also handles NULL. Count SMS with `type = 1 AND read = 0`, and MMS
  with `msg_box = 1 AND read = 0`; sum message rows, not conversations/addresses.
- **D5:** explicitly allowlist the two restricted permissions in the installer,
  then self-grant through DPM and verify `checkSelfPermission` **and** a successful
  query. A `true` DPM return is insufficient. Grant on first enable; keep grants
  on switch-off, but unregister observers, cancel work, hide counts, and stop all
  queries immediately. Revoking either permission kills foreground Home in this
  probe. DEFAULT changes who controls the permission; it does not revoke it.
- **Freshness:** observe `content://call_log/calls` and `content://mms-sms/`, with
  descendant notifications. The combined message URI is necessary: opening a
  conversation can notify it without notifying the separate SMS/MMS observers.
  Always query again when Home becomes visible. Background callbacks can be
  delayed by 10 seconds; they are not an immediate acknowledgement mechanism.
- **Direct Boot:** keep the `UserManager.isUserUnlocked` gate. Null cursor and
  SQLiteException are unavailable, never a zero count. Re-query after unlock.
- **RCS wording:** retain the spec's “Counts texts and picture messages. Chat
  messages (RCS) may not be counted.” Engineering scope is SMS/MMS rows in the
  public provider; reliable RCS unread coverage is **not verified**. Do not say
  that RCS can never appear there: Google's managed archival feature explicitly
  writes RCS to Telephony. That does not establish normal unread/read semantics.

The shipping spare's Phone/Messages versions still need validation by its sole
hardware driver. This is a release-verification limit, not a request to expand
this emulator-only task. No notification counting is proposed.

## Builds and reproduction

| | API 35 | API 36 |
|---|---|---|
| AVD / serial | `pla291-a35` / `emulator-5572` | `pla291-a36` / `emulator-5574` |
| Fingerprint | `google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.043/12960925:userdebug/dev-keys` | `google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys` |
| Phone role | `com.google.android.dialer` | same |
| Phone version | `127.0.620688474` | `166.0.738953385` |
| SMS role | `com.google.android.apps.messaging` | same |
| Messages version | `messages.android_20240528_00_RC01_alldpi.arm64-v8a.phone` | `messages.android_20250311_04_RC01.phone_dynamic` |

Role evidence: `cmd role get-role-holders android.app.role.DIALER` and
`android.app.role.SMS`, always through `adb -s` with the named serial. See
[API 35 environment](evidence/emulator-5572-init.txt),
[API 36 environment](evidence/emulator-5574-init.txt), and the `*-apps.txt` files.
Both images are **google_apis**, not Play Store images; no claim about all image
variants or enterprise provisioning installers follows from these results.

From this folder, set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to the SDK, then:

```sh
./gradlew :probe:testDebugUnitTest :probe:assembleDebug
python3 run.py emulator-5572 init
python3 run.py emulator-5574 init
```

`run.py` refuses any serial other than the two above. The installed package is
`com.plainphone.spike.counts`; admin `.Admin`, activity `.ProbeActivity`, command
receiver `.Control`. The following are the underlying commands (`S` must be an
owned serial and `P=com.plainphone.spike.counts`):

```sh
adb -s "$S" install -t probe/build/outputs/apk/debug/probe-debug.apk
adb -s "$S" shell dpm set-device-owner "$P/.Admin"
adb -s "$S" shell am start -W -n "$P/.ProbeActivity" --es cmd watch
adb -s "$S" shell am broadcast -n "$P/.Control" --es cmd grant
adb -s "$S" shell am broadcast -n "$P/.Control" --es cmd hist
adb -s "$S" logcat -d -v brief -s 'CountsProbe:I' '*:S'
```

Only the probe's log tag is saved. Histograms are labelled
`call-TYPE-NEW-IS_READ`, `sms-BOX-READ-SEEN`, `mms-BOX-READ-SEEN`. Empty histogram
buckets may be omitted in the committed logs; the principal query results,
including zero, null and error, are retained. Timestamps are monotonic device
uptime, not call/message dates. UI steps use `ui.py`, structural resource IDs and
fixed action labels; no message content is used to calculate counts.

## C1 — Permission paths

**Verdict: feasible, but installer exemption and grant readback are mandatory.**
No functional API 35/36 difference in this matrix. Both permissions behaved the
same. `E` below means `RESTRICTION_INSTALLER_EXEMPT`; `R` means
`APPLY_RESTRICTION`. Successful self-grants add `POLICY_FIXED`.

| Install / upgrade path | API 35 | API 36 |
|---|---|---|
| Fresh `adb install -t`, then provision DO and self-grant | E; granted; queries work | same |
| Fresh `adb install -t --restrict-permissions`, then self-grant | R; denied; SecurityException | same |
| Self-update, existing E, session default | E retained; works | same |
| Self-update, existing E, explicit empty allowlist | E retained; works | same |
| Self-update, missing E, explicit empty allowlist | R; cannot grant | same |
| Self-update, missing E, no allowlist setter call | E added; self-grant works | same |
| Self-update, missing E, explicit `{READ_CALL_LOG, READ_SMS}` | E added; self-grant works | same |
| Update APK that previously declared neither permission, empty list | R; cannot grant | same |
| Same new-permission update, default / explicit two-permission list | E; self-grant works | same |
| Fresh app-driven session, empty list, then make new app DO | R; self-grant ineffective | same |
| Fresh app-driven session, default / explicit two-permission list, then DO | E; self-grant works | same |

The fresh-session fixture uses a second application ID,
`com.plainphone.spike.countsfresh`. After installation, the original removable
admin is removed and the freshly installed app is made DO; **that app grants
itself**, then the original admin is restored. No accounts are present.

Representative denied readback (both builds):

```text
READ_CALL_LOG: granted=false, flags=[...|APPLY_RESTRICTION]
READ_SMS: granted=false, flags=[...|APPLY_RESTRICTION]
permission=READ_CALL_LOG result=true state=0
permission=READ_SMS result=true state=0
unlocked=true grants=[-1, -1]
query=calls-new error=SecurityException
query=sms-unread error=SecurityException
query=mms-unread error=SecurityException
```

Successful session and subsequent self-grant:

```text
installerPackageName=com.plainphone.spike.counts
install-status=0 legacy=1
READ_CALL_LOG: granted=true, flags=[POLICY_FIXED|...|RESTRICTION_INSTALLER_EXEMPT]
READ_SMS: granted=true, flags=[POLICY_FIXED|...|RESTRICTION_INSTALLER_EXEMPT]
permission=READ_CALL_LOG result=true state=1
permission=READ_SMS result=true state=1
unlocked=true grants=[0, 0]
```

**Test-only qualification.** Ordinary app sessions reject this removable APK:
`install-status=4 legacy=-15` (`INSTALL_FAILED_TEST_ONLY`). Simply setting the
hidden `INSTALL_ALLOW_TEST` flag is insufficient: PackageInstallerService strips
it without `INSTALL_TEST_ONLY_PACKAGE`. The test instrumentation therefore
adopts **only that one permission**, sets only the test-APK flag, and drops the
adopted permission after session submission. The installer UID/package remains
the probe. No runtime-grant, restricted-permission, installer, or DO privilege is
adopted. Hidden API access was enabled only on these test AVDs for that flag.
This is fixture machinery, **not a production solution**. The table establishes
restricted-permission behavior with this qualification; a production signed,
non-testOnly self-update has not been executed in this spike.

Reproduce with `permission_matrix.py` and `upgrade_permissions.py`. Build the
alternate artifacts first, copying them into `PAPERCLIP_RUN_SCRATCH_DIR`:

```sh
./gradlew :probe:assembleDebug -PprobeId=com.plainphone.spike.countsfresh
cp probe/build/outputs/apk/debug/probe-debug.apk "$PAPERCLIP_RUN_SCRATCH_DIR/fresh.apk"
./gradlew :probe:assembleDebug -PwithoutCounts=true
cp probe/build/outputs/apk/debug/probe-debug.apk "$PAPERCLIP_RUN_SCRATCH_DIR/nocounts.apk"
./gradlew :probe:assembleDebug
adb -s "$S" shell settings put global hidden_api_policy 1
# Apply the preceding setting to each owned AVD before running the matrices.
python3 permission_matrix.py
python3 upgrade_permissions.py
```

Matrix evidence families, for both serials: `*-adb-grant.txt`,
`*-adb-restricted.txt`, `*-session-instrumented-{default,none,both}.txt`,
`*-restricted-session-{default,none,both}.txt`,
`*-fresh-session-{default,none,both}.txt`, and
`*-new-permissions-{default,none,both}.txt`.
Earlier `*-session-{default,none,both}.txt` are **failed testOnly installation
attempts**, not successful restricted-permission experiments.

Sources:
[SessionParams defaults and allowlist behavior](https://android.googlesource.com/platform/frameworks/base/+/android15-release/core/java/android/content/pm/PackageInstaller.java),
[API 35 hard-restriction grant guard](https://android.googlesource.com/platform/frameworks/base/+/android15-release/services/core/java/com/android/server/pm/permission/PermissionManagerServiceImpl.java),
[API 36 same guard](https://android.googlesource.com/platform/frameworks/base/+/android16-release/services/core/java/com/android/server/pm/permission/PermissionManagerServiceImpl.java),
[testOnly flag enforcement](https://android.googlesource.com/platform/frameworks/base/+/android15-release/services/core/java/com/android/server/pm/PackageInstallerService.java).
The guard returns without granting a hard-restricted non-exempt permission;
allowlisting and runtime granting are separate operations.

## C2 — Missed calls

**Verdict: system flags track acknowledgement in both tested Google Phone versions.**
These are observed flags, not a claim that every dialer behaves the same.

| Action | Before `(TYPE, NEW, IS_READ)` | After | API comparison |
|---|---|---|---|
| Inject call, let remote caller cancel | no row | `(3,1,0)` | same |
| Open Recents, stay there | `(3,1,0)` | still `(3,1,0)` in initial query | same |
| Leave Recents for probe Home | `(3,1,0)` | `(3,0,1)` | same |
| Swipe missed-call notification | `(3,1,0)` | `(3,0,1)` | same |
| Tap missed-call notification | `(3,1,0)` | `(3,0,1)` | same |
| Notification “Call back” | `(3,1,0)` | old row `(3,0,1)`; outgoing row `(2,1,NULL)` | same |
| Decline ringing call | no row | `(5,1,NULL)` | same |
| Carrier voicemail reception | — | **not verified** | no carrier voicemail service |

Steps: `adb -s "$S" emu gsm call 5550100`, wait two seconds, then
`emu gsm cancel 5550100` for a missed call. For decline, use
`shell input keyevent KEYCODE_ENDCALL` while ringing. Only the synthetic number
was called back; no emergency number was dialled. Open Google Phone's main
activity, close its dialpad, select Recents, then return to `.ProbeActivity`.
For notification actions, expand with `cmd statusbar expand-notifications`, use
the “Missed call” row / “Call back” action, and collapse afterward.

Trimmed histogram proof:

```text
arrival:           query=call-3-1-0 count=1
after history exit:query=call-3-0-1 count=1
after swipe:       query=call-3-0-1 count=2
callback:          query=call-2-1-null count=1
                   query=call-3-0-1 count=4
decline:           query=call-5-1-null count=1
```

The snapshots accumulate test calls; counts of older acknowledged rows are not
new unread counts. Evidence: `*-missed-arrival.txt`, `*-history-return-home.txt`,
`*-swipe-call.txt`, and `*-call-{tap,callback,decline}.txt`.

The SDK defines voicemail as type 4; the proposed type=3 selection excludes it.
That is a **source-backed type definition**, not an observed voicemail delivery.
A voicemail can coexist with a distinct missed-call row; that missed row must
still count. Acknowledged/read-only flag combinations were additionally tested
with a synthetic provider update; do not equate that fixture with restoration
from backup. See `*-acknowledged-new-guard.txt`: NEW-only includes those rows,
while the combined selection returns zero.

Sources: [CallLog column/type contract](https://developer.android.com/reference/android/provider/CallLog.Calls),
[AOSP notification query and acknowledgement writes](https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/app/calllog/CallLogNotificationsQueryHelper.java),
[AOSP notification actions](https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/app/calllog/CallLogNotificationsService.java).
AOSP selects NEW=1 plus IS_READ IS NOT 1, and cancellation writes NEW=0 / IS_READ=1.
These explain the recommendation; Google Phone is closed-source and is supported
here by the emulator observations rather than by an assumed source identity.

## C3 — Texts, MMS and RCS

**Verdict: SMS works; synthetic group-MMS row/read semantics work. Carrier MMS
notification actions and RCS are not verified.** No observed API-level difference
in the verified cases despite different Messages versions.

Messages was used without an account. Inject two SMS messages into one fake
conversation and one into another:

```sh
adb -s "$S" emu sms send 5550100 TEST
adb -s "$S" emu sms send 5550100 TEST
adb -s "$S" emu sms send 5550101 TEST
```

| SMS action | Observed unread count / flags, both APIs |
|---|---|
| Three received messages, two conversations | 3; READ=0 (SEEN was already 1) |
| Open the conversation containing two | 3 → 1; those two rows READ=1 |
| Send another to that conversation, swipe its notification | 2 → 2; READ stays 0 |
| Send another, then notification “Mark as read” | 3 → 1; two rows READ=1 |

Use `sms_actions.py` with the conversation list visible and the three-message
fixture. Evidence: `*-sms-arrival.txt`, `*-sms-open-conversation.txt`,
`*-sms-confirmed-swipe.txt`, `*-sms-confirmed-markread.txt`. Files containing an
unmatched action during exploration are not used as action proof.

**MMS fixture:** `mms_fixture.py` creates one retrieve-confirmation PDU
(`m_type=132`, inbox, unread), a group thread, three address rows (one sender,
two recipients), and one synthetic text part. The probe still projects `_id`
from the PDU provider, never from addresses or parts. The count is **1**, not 3.
After restarting Messages so it imports the synthetic PDU, opening the group
conversation changes READ=0 to READ=1 and SEEN=1, and the count becomes zero.
In this fixture the group was the third conversation, not the first; the early
first-row attempt did not acknowledge it and is not successful read evidence.

```text
before: query=mms-1-0-0 count=1; query=mms-unread count=1
after:  query=mms-1-1-1 count=1; query=mms-unread count=0
```

Evidence: [API 35 group read](evidence/emulator-5572-mms-open-each-conversation.txt),
[API 36 group read](evidence/emulator-5574-mms-open-confirmed.txt), and
`*-mms-group-before.txt`. The fixture demonstrates provider cardinality and the
Messages conversation-read action. It does **not** test carrier download,
retry/deduplication, or an incoming MMS notification. MMS notification swipe and
notification “Mark as read” remain **not verified**. The modem test interface has
SMS injection; no end-to-end carrier MMS transport was available here. No
notification-count substitute was used.

**RCS: not verified.** No provisioned carrier/RCS identity or real messaging
account was used. The RCS settings pages were inspected; their redacted status
labels are in `*-rcs-settings.txt`. API 36 remained “Setting up…” / “Trying to
verify your phone number…”, not a connected RCS account. Do not infer RCS storage or read flags from SMS/MMS fixtures.
Google's official managed archival documentation says that, when explicitly
configured on fully managed devices, Google Messages writes RCS events into
Telephony for an app holding READ_SMS. It does not promise normal provider
coverage or an accurate unread flag for this Home feature. Archival configuration
was not enabled; it is a separate, untested mechanism. Therefore the assertion
“RCS never appears in SMS/MMS” is too strong, and the assertion “all messages are
counted” is unsupported. Keep coverage qualified in Settings.

Sources: [SMS READ/SEEN contract](https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns),
[MMS provider / PDU queries](https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/android15-release/src/com/android/providers/telephony/MmsProvider.java),
[AOSP conversation read action](https://android.googlesource.com/platform/packages/apps/Messaging/+/refs/heads/main/src/com/android/messaging/datamodel/action/MarkAsReadAction.java),
[Google's RCS archival documentation](https://developer.android.com/work/dpc/rcs-messages-archival).

## C4 — Observers and latency

**Verdict: use call-log plus combined message observers and a foreground refresh.**
Register with `notifyForDescendants=true`. Repeated callbacks are normal; their
number is not a count of calls/messages.

- Call arrival, history acknowledgement, notification swipe/tap/callback and
  decline produced call-log callbacks in both builds.
- SMS arrival and notification “Mark as read” produced SMS and combined callbacks.
  A notification swipe that leaves provider flags unchanged need not produce a
  callback; the count remains correct. The saved SMS action traces also contain
  callbacks from arrival, so they do not establish that swipe itself emitted one.
- Opening a SMS/MMS conversation notified the **combined** URI. The separate
  SMS/MMS observers alone did not cover every conversation-read update.
- Synthetic direct SMS/MMS flag updates also notified their respective URI and
  the combined URI. Group MMS transport/notification actions remain unverified.

Measured first callback upper bounds from the device-uptime sample immediately
before the emulator command (one sample/event/build; includes command dispatch):

| Event while probe visible | API 35 | API 36 |
|---|---:|---:|
| Incoming SMS | 48 ms | 65 ms |
| Remote hang-up creating missed call | 79 ms | 158 ms |
| Synthetic provider flag writes | 91–134 ms | 112–151 ms |

These are observations, not deadlines or a distribution. `/proc/uptime` has
coarser precision than the probe's milliseconds. Query durations themselves are
logged per query. A post-unlock SMS query on API 36 took 610 ms, so move queries
off the production UI thread even though this throwaway probe runs them there.

Evidence: `*-natural-latency-{sms,call}.txt`, `*-latency-*.txt`,
`*-background-sms-open.txt`, `*-call-tap-resume-observer.txt`, and API 36 `*-mms-open-confirmed.txt` (read update
around uptime 47–49 s, combined callback at 57.391 s). Background delivery around
10 seconds is consistent with the source's explicit delay; refresh on resume
instead of waiting for it. `observer_latency.py` reproduces the synthetic checks.

Sources: [ContentService foreground/background dispatch](https://android.googlesource.com/platform/frameworks/base/+/android15-release/services/core/java/com/android/server/content/ContentService.java),
[combined-provider update notification](https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/android15-release/src/com/android/providers/telephony/MmsSmsProvider.java).

## C5 — Revocation with foreground Home

**Verdict: revocation kills this Home process on both API levels. Keep grants
on display-switch-off.** The test made `.ProbeActivity` the default Home and
explicitly brought it to foreground for every trial. This measures the minimal
Home probe, not the final LMNOPhone UI.

`permission_loss.py` tests each permission independently. Shell revoke follows
DPM DEFAULT first to clear POLICY_FIXED, then runs:

```sh
adb -s "$S" shell pm revoke "$P" android.permission.READ_CALL_LOG
# Repeat with android.permission.READ_SMS.
```

| Operation on one held permission | API 35 | API 36 |
|---|---|---|
| DPM DEFAULT | same PID; grant remains; queries work | same |
| Shell revoke after DEFAULT | PID disappears | same |
| DPM DENIED on self | PID disappears, often before setter returns | same |
| Restart query after READ_CALL_LOG denial | call query SecurityException; messages work | same |
| Restart query after READ_SMS denial | SMS and MMS SecurityException; calls work | same |
| DPM DEFAULT after DENIED | policy becomes DEFAULT; grant remains denied | same |

Example API 35: PID `8237` disappeared on self-denial; restarted PID `8291`
reported grants `[-1,0]` and call SecurityException. API 36: `8511` disappeared,
then `8567` reported the same state. A process can die before finishing its own
self-revocation path, so do not rely on code after the setter to update UI/state.

Evidence: `*-READ_{CALL_LOG,SMS}-{default,shell-revoke,deny}.txt` and
`*-denied-to-default-*.txt`.
Sources: [permission-revocation callback kills UID](https://android.googlesource.com/platform/frameworks/base/+/android15-release/services/core/java/com/android/server/pm/permission/PermissionManagerServiceImpl.java),
[DPM grant-state contract](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setPermissionGrantState(android.content.ComponentName,%20java.lang.String,%20java.lang.String,%20int)).

## C6 — Reboot before and after first unlock

**Verdict: unavailable before first unlock despite both permissions being granted.**
The same result occurred on both API levels, from a Direct-Boot-aware receiver:

```text
command=query action=LOCKED_BOOT_COMPLETED
unlocked=false grants=[0, 0]
query=calls-new count=null
query=calls-unread count=null
query=sms-unread error=SQLiteException
query=mms-unread count=null
```

The explicit broadcast query while still locked reproduced those outcomes.
After entering the synthetic test PIN, `unlocked=true` and queries succeeded.
API 35 recovered the seeded five unread SMS / one unread MMS; API 36's read
fixtures correctly returned zero unread SMS/MMS. Call queries also succeeded.
Those different counts reflect deliberate fixture state, not API differences.

`boot_check.py reboot`, then `locked` after boot, then `unlock` reproduce the
stages. It sets a **synthetic test PIN** on these disposable AVDs only. The probe
is directBootAware and its receiver declares LOCKED_BOOT_COMPLETED; ordinary
post-unlock app startup would not test this question. Do not interpret
`sys.boot_completed=1` as proof the user has unlocked.

Evidence: `*-before-reboot.txt`, `*-before-unlock.txt`, `*-after-unlock.txt`.
Sources: [Direct Boot model](https://developer.android.com/privacy-and-security/direct-boot),
[Telephony CE database helper](https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/android15-release/src/com/android/providers/telephony/MmsSmsDatabaseHelper.java),
[MMS provider CE helper use](https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/android15-release/src/com/android/providers/telephony/MmsProvider.java).

## C7 — Projection, selection and privacy proof

**Verdict: no content column is read or logged by the count probe.** The same
APK query code executes on both API levels.

| Query | URI | Projection | Selection |
|---|---|---|---|
| Recommended missed count | `content://call_log/calls` | `_id` | `type = 3 AND new = 1 AND is_read IS NOT 1` |
| NEW comparison | same | `_id` | `type = 3 AND new = 1` |
| IS_READ comparison | same | `_id` | `type = 3 AND is_read = 0` |
| SMS unread | `content://sms` | `_id` | `type = 1 AND read = 0` |
| MMS unread | `content://mms` | `_id` | `msg_box = 1 AND read = 0` |

`QueryPlanTest` asserts the exact strings and single-column projection. The
initial contract was committed failing before QueryPlan existed (`3b9aa0d`).
The additional combined missed-call selection was also shown failing before
implementation. `:probe:testDebugUnitTest :probe:assembleDebug` passes.

There is one query call site in `Probe.kt`. It uses `QueryPlan.projection`,
selection-only flag filters, no sort, and `Cursor.count`; it never calls a cursor
value getter, even for IDs. Histogram diagnostics add only TYPE/NEW/IS_READ and
box/READ/SEEN predicates. Logs contain aggregate counts, permission state, PID,
monotonic uptime, query duration and exception **class**. They omit exception
messages, change URIs, message contents, addresses, contact names and message
or call dates. No content observer URI is logged. The probe has no network
permission. Only synthetic fixture creation writes addresses/parts; it does not
read them back. `_id` is the only projection in the production-candidate path.

Review points: [query plans](probe/src/main/java/com/plainphone/spike/counts/QueryPlan.kt),
[sole query/logging implementation](probe/src/main/java/com/plainphone/spike/counts/Probe.kt),
[contract test](probe/src/test/java/com/plainphone/spike/counts/QueryPlanTest.kt).
SDK contracts: [ContentResolver.query](https://developer.android.com/reference/android/content/ContentResolver#query(android.net.Uri,%20java.lang.String[],%20java.lang.String,%20java.lang.String[],%20java.lang.String)),
[Cursor.getCount](https://developer.android.com/reference/android/database/Cursor#getCount()).

## Boundaries for the spec and review

- Do not promise immediate background observer delivery, or assume non-null
  cursors during Direct Boot.
- Do not use a DPM boolean as proof of a restricted permission grant.
- Do not revoke runtime permissions as the ordinary off-toggle behavior if Home
  must remain running. Off must still stop all feature reads.
- Do not promise RCS coverage, every carrier MMS edge case, carrier voicemail
  behavior, or the spare's app-specific flags from this emulator evidence.
- The synthetic MMS group tests prove one-PDU cardinality and conversation-read
  behavior, not real network receipt or notification actions.
- Keep this app, instrumentation, exported controls, root fixtures, and UI-thread
  queries out of the production modules. The Lead folds these results into the
  spec; the author does not approve the feature or edit the design decision.
