# Preview 33 — Wi-Fi joining

Candidate for the Lead's single review on [PLA-437](/PLA/issues/PLA-437).
The temporary-Location probe passed under LOCKED: connection stayed up after
Location returned to 0 and Wi-Fi off/on reconnected with Location 0. Fallback
is not selected. Built and installed on the assigned emulator; **not installed
on the spare by the author**.

## Changed behavior

Phone settings → Wi-Fi now offers **Show nearby networks** when Location is off.
Only that tap enables Location. A network row opens native open/personal-network
joining, or the unsupported-network message. Password text is transient, masked
unless requested, cleared on submit/leave, and the join window uses FLAG_SECURE.
A join waits at most 30 seconds for the selected Android network to connect;
internet validation is still reported independently using build 29's messages.

Only Location enabled by this flow is restored. Success, Back, Cancel, Home,
page pause and the five-minute deadline request cleanup. Telecom reports whether
any call is in progress; unknown call state defers cleanup too. The ownership
marker is committed in device-protected storage before enabling and recovered
at application start. A failed off readback retains the marker and shows the
approved failure line. Existing saved network configurations are selected,
never rewritten; there is no Forget or Android Settings route.

## Evidence and limits

- Tests first: `a95b549a`; 29 Wi-Fi controller tests and 203 host tests pass.
- 95 affected Wi-Fi goldens pass across 1.0/1.3/1.5/1.8/2.0; every changed image
  inspected. Primary buttons stay pinned, rows are at least 56dp, body scrolls.
- The API 35 probe verifies DO add/remove of a new synthetic configuration,
  association to the existing AP, auto-off, transport retention and reconnection.
  The emulator has one already-saved AP, so adding and association are separate
  checks. New-AP/WPA2/WPA3 association is not claimed.
- The live UI gate verifies button → names → open-network join → Location 0,
  Back/Home/Cancel restoration and process-death marker recovery. A temporary
  shell-only disconnect receiver keeps the emulator's sole AP selectable; it
  changes no saved configuration. It is absent from the final APK. The final
  package passed its own accepted-32 → 33 upgrade/emergency gate and a final
  Show-nearby/Back smoke check with all captured settings unchanged.
- Both dialer routes show exactly 911, zero current calls. DO, TEST_ONLY and
  LOCKED retained. No call intent/button or spare command used.
- Saved network list, original Location mode, font size, ringtone and Bluetooth
  settings read back unchanged after the UI gate.
- Lint still reports unchanged errors in `AndroidWifi.kt:55` (permission-flow
  analysis) and `PinAccessActivity.kt:99` (StateFlow access in composition).
  New join code has no lint errors; no checks were disabled.

Artifact/install details: `app/tools/preview-33/evidence/install-readback.txt`.
Probe: `docs/previews/preview-33/probe.txt`. Live/emergency evidence and scripts:
`app/tools/preview-33/`. No network names/passwords are retained in that evidence.

## Lead's spare check

Install in place over 32 using the same signer. With Location 0, open Phone
settings → Wi-Fi → Show nearby networks, record the count of names only, then
Back and read Location 0. **Do not join on the spare** and never forget its saved
network. Read saved networks, Bluetooth, ringtone and font size back unchanged.
No live emergency calls. Update the `preview-33` release-notes case with the
spare install time, hash and readback after successful installation.

Android API basis: [WifiManager](https://developer.android.com/reference/android/net/wifi/WifiManager)
(DO legacy network-management exemption) and
[TelecomManager.isInCall](https://developer.android.com/reference/android/telecom/TelecomManager#isInCall())
(ongoing managed or self-managed calls), backed by the emulator probe above.
