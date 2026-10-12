# Preview 29 — read-only Wi-Fi status

**Probe result: joining on the spare needs Location on.** Keep the name fallbacks.
No Location toggle or Location write was added to the app.

Approved slice: [PLA-424](/PLA/issues/PLA-424), based on preview 28 at `9e7c7ca4`.
This implements the read-only subset of web-containment's Wi-Fi status messages;
Forget, joining and the separate calls/texts screen remain deferred to build 30.

## Built / installed / exercised

- Version `29` / `0.1.0-preview-29`.
- APK SHA-256 `039506e95ab1153cc6b0c54ce984569e99862c8671a4ebd2bfdccea91944414d`.
- Same signer as installed 28 (`99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`); accepted 23–28 continuity reused.
- In-place emulator install at **2026-10-12 14:22:52Z**, with restart and exact-911/zero-call gate passing. Device Owner, TEST_ONLY and LOCKED retained. No uninstall or reset.
- Location restored to mode 3; saved networks and font scale unchanged. No spare commands. **Spare installation remains the Lead's next step after review.**

## Changes and checks

The Wi-Fi page shows the approved sign-in-page message immediately. An observed
unvalidated connection shows the no-internet message only after 10 seconds.
The observation resets on page opening, network-handle change, validation,
portal, disconnection, Wi-Fi off or an unreadable snapshot. The page does not
launch a browser, open Settings, or offer Forget/OK. A failed overall read shows
“Wi-Fi status can't be read right now.” with no connection claim or network list.

Tests-first commit `ac69cfd9` fails compilation before implementation. Then 16
controller tests, 202 host tests and 45 Wi-Fi Paparazzi checks pass. All 15 new
goldens were inspected at 1.0/1.3/1.5/1.8/2.0; existing goldens are unchanged.
Portal/no-internet/read-failure behavior was exercised in host tests and goldens,
not a live emulator portal or simulated network outage.

## Probe

API 35, foreground Device Owner, fine location already granted:

| Location | connectionInfo | FLAG_INCLUDE_LOCATION_INFO transportInfo | scanResults |
|---|---|---|---|
| 3, before | name returned | name returned | returned 1 named result |
| 0 | unknown-name marker | unknown-name marker | succeeded, 0 results |
| 3, restored | name returned | name returned | returned 1 named result |

Validation remained true in all three reads. Names were neither logged nor
persisted. An initial background-only probe redacted names even with mode 3;
its foreground positive control was therefore necessary. A synchronous activity
launch stalled the first foreground harness; the completed harness uses a
bounded host launch and obtains both positive controls.

This matches the [Android NetworkCallback documentation](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback#FLAG_INCLUDE_LOCATION_INFO),
which says location-sensitive callback data checks permission and the Location
toggle. This emulator result is not a claim that a spare Location-on probe ran.
The Lead owns the resulting product question; no Location change on the spare is
part of this build.

## Reproduce / handoff

From `app/`, JDK 17 and the SDK set:

```
./gradlew :device:testDebugUnitTest --tests '*WifiControllerTest'
./gradlew :plainphone:testDebugUnitTest :plainphone:assembleDebug
./gradlew :ui:verifyPaparazziDebug --tests '*WifiSettingsScreenshotTest'
./gradlew :plainphone:assembleDebugAndroidTest -PpreviewTestRunner=org.plainphone.settings.WifiLocationProbe
```

From the repo root, `python3 app/tools/preview-29/location_probe.py` runs the
throwaway probe on **emulator-5880 only** and restores mode 3 in `finally`.
The upgrade and UI scripts plus privacy-checked screenshots/logs live in
[app/tools/preview-29](../../app/tools/preview-29/).

On the spare, the Lead installs the reviewed APK in place over 28, opens
More → Phone settings → Wi-Fi, and compares the connection and internet line
with platform status/validation. Record Location and saved networks before/after;
neither should change. With Location off the name fallbacks remain expected.
No calls, no settings changes, no separate approval chain.

## Spare install (Lead, sole driver)

Lead review [PLA-424](/PLA/issues/PLA-424): approved (changed product code plus
affected tests; `WifiControllerTest` re-run 16/16 PASS). APK hash above matched
the author's local build.

- **Installed:** in place over 28 at **2026-10-12 14:52:56Z**, installer Success.
  The first gate run used the build-28 script, which correctly failed its
  "version is preview-28" check after a good install
  (`spare-install-wrong-gate-version.txt`). The build-29 gate then ran with
  `SKIP_INSTALL=1`: restart, More › Emergency and Phone both typed exactly
  **911**, **zero calls**, Device Owner, TEST_ONLY and LOCKED kept, exit 0
  (`spare-upgrade-check.txt`).
- **Exercised:** Wi-Fi page reads **Connected to Wi-Fi** and the nearby-network
  fallback (Location is off on the spare, mode 0). The platform reports one
  validated, non-portal Wi-Fi network; the page shows no internet warning, also
  after 12 s (past the 10 s grace). No overall read failure
  (`spare-wifi-check.txt`, `spare-wifi-page.png`; names never read into logs).
- **Not exercised on the spare:** sign-in-page and no-internet messages (no live
  portal or outage); read-failure text.
- **Readback:** Location mode 0, saved networks exactly unchanged, font scale 1.0.
