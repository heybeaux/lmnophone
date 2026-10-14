# Design

Authorization: PLA-525 and the Lead's answered recipient-source interaction.
Base: `pla-prod/cleanup` at `711fc4f0`. One author, one Product & UX review, one driver.

One serialized policy controller writes the request before dispatch. Each SMS part
has a unique explicit sent-intent identity and persisted callback slot. All parts
must report RESULT_OK for Sent. A mixed outcome is Not confirmed. Pending work
from an old process or older than one minute is Not confirmed; there is no resend.
Late matching callbacks may refine the persisted result. Duplicate IDs/callbacks
are ignored. Failed preflight attempts are saved and count toward the daily limit.

Atomic CE file replacement (fsync before rename) contains the optional recipient
and request ledger. Missing is empty; malformed data is unavailable, never reset
or Sent. Data is private, excluded from backup, and unaffected by app update.
Recipient edits preserve all requests; the send resolves the recipient under the
same controller lock. No number is logged or placed in a sent-intent.

The number has a fresh-PIN read gate and each mutation consumes a new purpose-bound
PIN check on the existing policy dispatcher. Pause invalidates pending editor work.
Optional Family selection is deferred; no implicit selection is permitted.

SMS adapter uses the default SMS subscription; no new chooser or SIM configuration
is introduced. Missing permission/service produces an honest failure. Source:
https://developer.android.com/reference/android/telephony/SmsManager#sendMultipartTextMessage

Bounded limitations: no automatic/manual retry control, request action, reply,
Family picker, or changes to trusted-person credentials. The last ten requests
are displayed; the quota ledger is retained (10,000-record storage ceiling).
