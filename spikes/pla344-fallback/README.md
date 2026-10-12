# PLA-344 fallback containment: AVD evidence (throwaway)

AVD `pla242-a35` (`emulator-5824`, API 35, Device Owner, PIN 1234), 2026-10-12.
Builds: `3fac0bfd` debug (sha256 `249d8738de6f09cc…`), then in place `1b5bd0fd` debug (`2064ca4cf2c47542…`).

`testapp.apk` is a code-free stand-in app (`org.plainphone.test344`, framework `android.app.Activity`):
`aapt2 link --manifest AndroidManifest.xml -I android-36/android.jar -o testapp.apk`, then signed with the debug key.

## Steps and results (`evidence/`)

| Step | Command | Result |
|---|---|---|
| 00 | baseline suspension dump | nothing suspended |
| set-up | `pm suspend com.google.android.youtube` (someone else's suspension); `am broadcast -n org.plainphone/.boot.TestHook -a org.plainphone.test.FEATURE_FAULT --ez on true`; reboot; PIN | features read 0; floor stage fails readback (PLA-307 finding 1 state); lock task NONE |
| 01–02 | log + dump after the PIN | `fallback containment: suspended=217 … recorded=true`; Settings, permission controller and 9 other system packages refused |
| 03 | attention screen | "LMNOPhone needs attention. You can still make calls.", the Settings disclosure, the other refused apps by name, Phone, Emergency call |
| 04 | explicit Chrome, explicit test app, `VIEW https://example.com` | each lands on `ActionDisabledByAdminDialog` ("Blocked by work policy") |
| 05 | TestHook `NOTIFY` for Chrome and the test app, tap in the shade | START from uid 10207 (org.plainphone), lands on the blocked dialog |
| 06 | `am start com.android.settings/.Settings`, `MANAGE_PERMISSIONS` | both open, as disclosed |
| 07 | power off, wake | `isKeyguardShowing=true` |
| 08 | bouncer Emergency button | safetyhub `DIAL_EMERGENCY` → `DialPadActivity`, digits empty, `mCallState=0`; nothing dialled |
| 09–10 | record before/after the in-place upgrade to `1b5bd0fd` | 217 → 211 entries: exactly the 6 newly kept packages lifted; YouTube never recorded |
| 11–12 | `FEATURE_FAULT --ez on false` | features 119, Home LOCKED, `fallback lifted=211`; the only suspended package is YouTube, as before the test |

Afterwards: `pm unsuspend com.google.android.youtube`, test app uninstalled.

## Delta re-check at `88108093` (PLA-399 F1–F3 fix), 2026-10-12

In-place debug install (sha256 `e125a07691713ae2…`) on the same AVD; then `FEATURE_FAULT --ez on true`, reboot, PIN:
- `fallback containment: suspended=211 … recorded=true` (same count and refused list as `1b5bd0fd`); no `no complete keep set` line, so the floor reads complete here and the new gate does not disable containment.
- `FEATURE_FAULT --ez on false`: `reapply reason=feature-fault-off … applied=[…LOCK_TASK, HOME]`, `fallback lifted=211`, Home LOCKED; `dumpsys package packages` shows 0 `suspended=true`.
The F1–F3 failure paths themselves are JVM-only (failure injection); they were not induced on the AVD.
