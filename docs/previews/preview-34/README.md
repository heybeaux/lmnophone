# Preview 34 candidate: build-33 review corrections

**Forget/message-route blocker:** on the assigned API-35 emulator with Location 0,
Android returns current network ID -1 and no CURRENT configuration. With Location
3, the same queries identify the connection. Forget passed the live synthetic-AP
check at Location 3 but cannot satisfy the spare's required Location-0 button
check. The final candidate therefore contains **F1/F2 only**, for the Lead's
single review and delivery decision. No new Location behavior or cached-ID
removal was substituted. Full work is preserved at commit `c3a64c60`.

## Final candidate

- Version code 34; `0.1.0-preview-34`; same signer as accepted build 33.
- APK: `app/plainphone/build/outputs/apk/debug/plainphone-debug.apk`.
- SHA-256: `6138736a8fd4d34a11d17ba063fe334b3edda7091549eecdf28f121baacf7557`.
- **Built:** final F1/F2 subset; no probe receiver in its manifest.
- **Installed:** in-place accepted 33 → final 34 on `emulator-5880`,
  2026-10-12 19:25:48 UTC. DO, TEST_ONLY and LOCKED retained.
- **Exercised:** focused JVM tests; unchanged Wi-Fi goldens at
  1.0/1.3/1.5/1.8/2.0; emulator upgrade/reboot and two no-call emergency routes.
  See `app/tools/preview-34/evidence/`. No physical-device commands were issued.
- **Not yet installed on the spare.** The Lead owns review and the spare upgrade.

## What changed

When owned Location needs cleanup but call state remains unknown for 60 seconds,
the existing warning appears: **Location is still on. LMNOPhone couldn't turn it
off.** Cleanup keeps retrying. A known active call still prevents disabling
Location. The timer starts when cleanup first observes unknown call state;
repeated leave events do not postpone it.

Wi-Fi name/passphrase quoting now adds only the two outer quotation marks.
Embedded quote and backslash bytes reach Android unchanged. No saved network
configuration is rewritten. F1 and F2 were each reproduced by failing tests
before their fixes (commits `78dffa85` and `a6e3c07d`).

## Validation and limits

Final checks: 33 device tests, 24 navigator tests and 95 Wi-Fi screenshot checks,
all passing. No changed final screen or golden versus accepted build 33.
The call-state timeout and special credential bytes were exercised by JVM tests;
no live call or real password test is claimed. Location, Bluetooth, ringtone,
font scale and the original saved-network SHA-256 stayed unchanged on upgrade.

The full deferred candidate passed 39 device tests, 25 navigator tests and 128
screen checks; all 30 new/changed goldens were inspected. Its live Forget test
joined an added synthetic AP, removed that one saved ID, preserved the original
AP's saved-list hash, and retained LOCKED with zero calls. The Location-off gate
then failed. These results are stored under `evidence/deferred-wifi/` and are
**not** evidence that Forget ships in the final subset.

## Reviewer and spare driver

The Lead reviews the final delta against `b13e2a35`, then installs this exact hash
in place over spare build 33 if accepted. Preserve data/settings and the existing
no-call emergency checks. The original Forget-button spare gate does not apply
to this subset: there is no new Forget action or message route to tap. Do not
interpret the deferred UI as shipped. The Lead should decide delivery of this
bounded subset and retain the Location-0 identity limitation as the concrete
remaining blocker for Forget.

Paperclip rejected the raw APK at its 10 MB attachment limit. The uploaded ZIP
contains the unchanged final APK (verified after extraction), compressed below
that limit. The pushed branch, local path and exact hash also identify it. The
separate review packet contains the redacted evidence and screenshots.
