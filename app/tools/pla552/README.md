# App permissions candidate evidence — 2026-10-14

Source: `pla-app-permissions/page`, based on `origin/release/preview-53` (`88501590`). Implementation through `c49c08a5`; subsequent report-only commit does not change the APK.

- Built: debug APK, SHA-256 `07ee90b04e1c98a7953a868ca60fead749cc51bfe504eee3cce4e9d6bfc9e017`.
- Installed: **private emulator only**, in-place at 2026-10-14 09:46:57 UTC (guest readback 02:46:57 PDT). Package readback: `org.plainphone`, versionCode 53, versionName `0.1.0-preview-53`. This is an author candidate retaining the base version; the release driver must number the next device build and incorporate `docs/releases/pending-app-permissions.md`.
- Exercised: private `emulator-5780`, API 35, Device Owner, self-managed mode, 360×640 dp. **Spare installed: no. Spare exercised: no.** No calls or sends.

## Result

The real production path was exercised: Home → More → PIN settings → Apps on this phone → Permission test app. Initial camera and microphone states were Not chosen yet. The page has no Change action.

Remove → fresh PIN → Add a hidden app → add warning → T4c. Tapping Next with no choices stays on T4c. Camera Allow + Microphone Don't allow → add confirmation → fresh PIN. A wrong PIN in the initial manual run left the app hidden and wrote no fixed permission state. With the correct PIN, the final APK replay read back:

```text
android.permission.CAMERA: granted=true, flags=[ POLICY_FIXED|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]
android.permission.RECORD_AUDIO: granted=false, flags=[ USER_SET|POLICY_FIXED|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]
```

The production page showed Camera: Allowed / Microphone: Not allowed. An app force-stop/restart preserved both the page readback and the fixture's private `files/permission-marker` (`synthetic-data`). This was an app process restart, not a guest reboot.

Repeating remove/add with both groups Allow read back:

```text
android.permission.CAMERA: granted=true, flags=[ POLICY_FIXED|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]
android.permission.RECORD_AUDIO: granted=true, flags=[ USER_SET|POLICY_FIXED|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]
```

Saved fixture data survived both cycles. Neither tested group was refused. Refusal UI is covered by host/UI tests; other permission groups are not claimed as emulator-tested.

## Reproduction

Use a private disposable API 35 emulator at port 5780, never the spare or another team's emulator. `ui_driver.py` deliberately pins every command to this serial. Install the candidate with `adb -s emulator-5780 install -r -t`, set `org.plainphone/.PlainphoneAdminReceiver` as Device Owner on the clean emulator, and use the preview setup path (which retains debugging). Do not apply full lockdown restrictions.

Run `bash app/tools/pla552/build-fixture.sh`; it builds the CAMERA/RECORD_AUDIO fixture into `$PAPERCLIP_RUN_SCRATCH_DIR/permission-fixture/permission-fixture.apk`. Install it with the same explicit emulator target. Seed the fixture alongside the emulator's dialer, SMS and camera apps through `org.plainphone.preview.SET_STARTER` on `.preview.SetupCommandReceiver` using the `packages` string-array extra. Enroll the synthetic test PIN used by `ui_driver.py` through the normal setup UI. Do not capture recovery codes. Create the fixture's private marker with value `synthetic-data`, then run `python3 app/tools/pla552/emulator-check.py`. The script checks grants, page readback, restart, and data preservation; it does not call, send, or operate the spare.

## Changed-path validation

Passed on the final source:

- Device host: 25 tests across AppPermissionReaderTest, PreviewPermissionAdditionTest, PermissionGrantsTest and PreviewPinAccessTest.
- App host: 18 tests across AppPermissionsMapperTest, PreviewAppsFlowTest and ProductionCleanupTest. Production activity/runtime wiring is asserted, including fresh authentication, visible grant window and real PermissionGrants writes.
- AppPermissionsScreenshotTest: 18 parameterized cases, 36 individually inspected golden images at 1.0, 1.5 and 2.0 text size. Long app name, four groups, top/scrolled positions, chosen/unselected/refused and empty/unreadable states. Text has no ellipsis or overflow; primary actions remain above the reserved nav area.
- Existing PreviewAppsScreenshotTest and TrustedScreensScreenshotTest passed against unchanged goldens.
- `:plainphone:assembleDebug` passed.

Tests were committed before implementation; failing-test checkpoints are in branch history. No failing checks were disabled.

## Scope and limitations

This is the authorized self-managed route. There is no supervised entry or one-step permission change. The add flow uses the existing visible permission window, closes it before adding Home membership, and requires every write to return DONE plus an exact-identity readback. Partial/refused/replaced writes never show success. Protected apps and current dialer/SMS role holders are excluded at reads, navigation and write boundaries.

The current preview has no timed-opening stage. T4c therefore uses the existing remove-and-add explanation for its second self-managed block instead of promising the full MVP's ten-minute wait. This limitation is also recorded in the UX flow. Credentials and mode are unchanged. Permission state remains Android's fixed state; no invented state or recipient is stored.

## Review F1 correction — 2026-10-14

The reviewer found that an undefined vendor permission made the entire manifest snapshot unreadable. Regression fixture commit `62296154` adds `org.plainphone.undefined.permission.BADGE` alongside Camera and Microphone. The previous APK reproduced **Couldn't check** on the real app page.

Correction `357e0f9a` catches only `PackageManager.NameNotFoundException` around each permission lookup and omits that entry. Other exceptions still fail the complete snapshot closed. There is no new lookup seam or policy change. The unchanged snapshot equality checks still fence a newly defined permission.

The corrected debug APK SHA-256 is `5852da310209b27d296f6383d5149296813b5c62ae87fa8573a6c499ac14f004`. Its in-place private-emulator install read back at 2026-10-14 10:07:55 UTC (03:07:55 PDT), versionCode 53 / `0.1.0-preview-53`. The focused AppPermissionReaderTest passed (3 tests); `:plainphone:assembleDebug` passed. Previous screenshot and transaction evidence is unchanged.

The revised fixture completed remove/add with exactly Camera Allow and Microphone Don't allow choices, fresh PIN, and fixed Android readback. The production page showed the same states and saved fixture data remained intact. The undefined permission no longer prevents adding the app. Spare installed: no; spare exercised: no.

The corrected fixture's page/grants and private marker also survived an app force-stop/restart.
