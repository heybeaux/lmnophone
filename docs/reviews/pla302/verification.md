# Counts revision 5 follow-up

Issue: [PLA-302](/PLA/issues/PLA-302). Lead (Anthropic) reviews this candidate.
Branch: `feature/home-counts-host`, starting at `67a50b6`.
Spec/design: `spec/communication-counts` at `3693fcc`, D1 host proof and D7.

## Settings availability

`CountController.availability(kind)` separates the last completed read from the
currently displayed number. A disallowed gate is unavailable immediately.
A null/invalid result or a 3-second timeout records unavailable for that source.
Before the first completed read it is unknown. A successful zero is available.
Pending retries retain the previous completed result; a first read has no error
line. Clearing a number at the 1-second deadline does not invent a read failure.
Switch-off clears availability; late completions remain fenced out.
`MainActivity` maps only UNAVAILABLE to the two existing Settings lines.
No text, layout, permission, device, policy or emergency code changes.

Tests first: `940728e` adds six regressions, observed failing to compile because
CountAvailability/availability did not exist; implementation is `e4d45e4`.
The six cover slow first Settings read, failure and recovery, timeout and late
completion, disallowed gate, deadline-only hide, and switch-off/stale completion.
`CombinedUnionModelTest` adds two JVM relational DISTINCT tests, using the
provider's normalized date in its inner projection. The equal ID/date pair
has size 2 with transport and 1 without; unequal dates demonstrate why the
previous weaker fixture could mask a missing discriminator.

## Verification

From `app/`, JDK 17 and Android SDK 36:

```sh
./gradlew --offline :plainphone:testDebugUnitTest :plainphone:assembleDebug :plainphone:assembleDebugAndroidTest
```

**77 host JVM tests pass**, zero failures/errors/skips, including 8 new tests.
Debug app and instrumentation APKs build and install on both owned AVDs.
No full-suite claim or new screenshots: UI layout/resources did not change.
The inherited device test failures recorded in the previous report were not
changed or rerun by this counts-only task.

## Provider evidence

Only owned AVDs `pla291-a35` / `emulator-5572` and `pla291-a36` /
`emulator-5574`. No physical phone or other team's emulator was used.
Each instrumentation run verifies READ_SMS and non-default-SMS identity.
The total always uses the exact production combined query; per-table queries
are test oracles only. Focused per-table counts verify each new fixture.

| Fixture, cumulative | Combined | SMS oracle | MMS oracle | Focused SMS/MMS |
|---|---:|---:|---:|---:|
| Included subscription, SMS/MMS equal ID and normalized date | 2 | 1 | 1 | 1 / 1 |
| Add restored SMS and MMS (`sub_id = -1`) | 4 | 2 | 2 | 1 / 1 |
| Add excluded-subscription SMS and MMS | 4 | 2 | 2 | 0 / 0 |
| Add unread inbox download notification (`m_type = 130`) | 4 | 2 | 2 | 0 / 0 |
| Add sent, draft and outbox SMS/MMS | 4 | 2 | 2 | 0 / 0 |

The MMS fixture has a synthetic group thread, sender/recipient rows and text
part; the restored SMS uses a second conversation. No address, content, account
or contact data is read or logged. Root fixture verification reads only IDs
and counts matching numeric metadata predicates from the credential-encrypted
`mmssms.db`; the target UID only uses cursor counts. Insertion and cleanup use
the root content tool on the owned emulator; no app permission is widened.

### Corrections the stronger fixture exposed

- MmsProvider discards caller `_id` on insert. The earlier report's claimed
  collision was therefore not established. This script discovers the new MMS
  ID, inserts an SMS with that ID and asserts both dates and IDs from storage.
- SMS milliseconds require a 64-bit content binding. The script now uses it
  and fails on command errors even when the shell exits zero.
- Both images rejected direct MMS insertion on an unassociated subscription.
  Inserting known synthetic rows on the included subscription and updating only
  their `sub_id` through the fixture writer succeeded. Metadata verification
  confirms the excluded rows exist and stay unread; neither query counts them.
  Excluded rows have no SMS address, so they cannot exercise the emergency-number
  exception. No emergency-number data or calls were used.
- The MMS delete route also filters excluded subscriptions. Cleanup now returns
  each created row to the included subscription, deletes it and verifies its
  ID is absent from storage. Preliminary leftovers were removed the same way.
- A test-only `_id` predicate on the combined route raised SQLiteException.
  Removed that predicate: the test now measures the exact production query's
  cumulative total and uses ID selection only for the per-table fixture oracle.
- API 35 initially resumed locked from the prior Direct Boot fixture. The run
  stopped before insertion, then was repeated after synthetic first unlock.
  The script now explicitly requires RUNNING_UNLOCKED. The metadata verifier
  uses the active credential-encrypted store, not the empty Direct Boot store.

The DISTINCT model follows AOSP android15-release
[MmsSmsProvider.buildConversationQuery](https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/refs/heads/android15-release/src/com/android/providers/telephony/MmsSmsProvider.java#1314).
MMS allocation matches
[MmsProvider.filterUnsupportedKeys](https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/refs/heads/android15-release/src/com/android/providers/telephony/MmsProvider.java#1151).
The emulator runs are the evidence for these specific API 35/36 images.

### Reproduce

Build and install the debug/test APKs with `adb -s <owned serial> install -r -t`.
Ensure the existing READ_SMS grant and unlock the test user. Run
`plainphone/src/androidTest/tools/run_combined_fixture.py <owned serial>` from
`app/`, supplying PAPERCLIP_RUN_SCRATCH_DIR and distinct synthetic
COUNT_FIXTURE_RECIPIENT_A/B from the fixture's accepted range. The script checks
both serial and AVD name, marks the old synthetic baseline read, creates its
fixtures, emits counts only, and removes its new rows in a finally block.

RCS, carrier MMS delivery/retry and the spare's D8 release checks remain outside
this emulator-only candidate. Counts remain in testing. The Lead owns review
and integration; this report is not approval of our own work.

Final provider gate: **5/5 cases on API 35 and 5/5 on API 36**, zero skips.

Durable records: [PLA-C17](/PLA/cases/PLA-C17), [PLA-C18](/PLA/cases/PLA-C18), [PLA-C19](/PLA/cases/PLA-C19).
