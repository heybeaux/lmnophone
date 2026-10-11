# preview-3: build notes (PLA-289)

**preview-3** (tag `preview-3`, `0.1.0-preview-3`, versionCode 3) was **installed in place on the spare Moto** (<spare>,
Android 15) on 2026-10-11 at 04:50:50 UTC (Sat 10 Oct, 21:50 PDT), over preview-2. It was not reset or re-provisioned,
and the tester's starter list and settings were kept. APK sha256 `ec3e05bb…d329f720cb`, `testOnly=true`, same
signing key. It was built from `release/preview-2` at `f33cd44`; later commits are evidence and notes only.

**Owner observation (not install evidence):** Beaux reported seeing the change on his spare (board relay,
Telegram3188, 2026-10-11). The install and readback evidence is `evidence/spare/upgrade-check.txt`.

## What changed since preview-2
- **Calmer Home.** "More" is now plain centred text: no outline, no arrow, no "Ask for an app, settings, help" line.
  It is still a big touch target and still opens More, including Emergency 9-1-1 (PLA-285, cherry-picked as `6fc9698`, `bf1f29c`).
- **Home keeps up with changes.** Before, if the app list changed while Home was on screen, Home could keep showing
  an app that had been removed, until something else redrew it. Now Home re-reads the list after every successful
  check (PLA-266 finding 2, fixed in `ba4014a`, test first in `85e03f1`).
- Wording-only fixes in the preview-2 evidence (PLA-266 findings 1 and 3). The before-unlock 9-1-1 typing check now
  passes on the emulator (`preview-2/evidence/emulator-3/check1-prelock.txt`).

## How to test (about 2 minutes)
1. Look at Home: "More" sits alone, centred, at the bottom, with no box around it.
2. Tap **More**: the More screen opens. Tap **‹ Home** to go back.
3. More > **Emergency 9-1-1** opens the dial pad with 9-1-1 ready. **Don't press Call.** Go back.
4. Restart the phone: you see "checking the phone" briefly, then Home, same as preview-2.
5. Anything that feels slow, odd or unclear: tell us in your own words; screenshots welcome.

## Checks run for this build
| Check | Emulator (API 35, `evidence/emulator/`) | Spare (`evidence/spare/upgrade-check.txt`) |
|---|---|---|
| In-place upgrade, no uninstall | versionCode 1 → 3, still Device Owner, lock task LOCKED | same |
| More centred, no subtitle | centre offset 0 px | centre offset 0 px; subtitle gone from the screen text |
| Restart: Home only after the full read-back | ORDER OK | ORDER OK (read-back 21:51:45.891, Home 21:51:46.179) |
| More > Emergency: 9-1-1 typed, no call | dial pad `911`, no calls in Telecom | same |
| Phone: 9-1-1 typed, no call | **not an exact pass**: the dial pad still held the earlier digits and read `911-911` (`5-phone-911.png`); no calls in Telecom | dial pad `911` (text record), no calls in Telecom |
| Unit tests | `:ui` 91/91 + Paparazzi, `:plainphone` 50/50 | — |

Carried forward from preview-2: safety check 2 (Device Owner removal brings every app back) and safety check 3 (unhide
then save flip, best effort). `:device` and the policy plans are byte-identical to preview-2, so those observations still
apply. The locked-restart and power-cut checks were **not rerun** on preview-3. Their policy and unhide observations
carry forward, but the part that withholds Home until the full read-back did change (`HomePublisher`,
`PolicyRuntime.refresh`, `MainActivity`). For preview-3 that Home gate rests on the new `HomePublisherTest` unit tests,
the fixed-build before-unlock record (`../preview-2/evidence/emulator-3/check1-prelock.txt`) and this build's restart
records above. The power-cut sequence was not repeated. See `../preview-2/NOTES.md`.

## Known gaps
- Everything listed under "Not in this build yet" in preview-2 still applies: no PIN, approvals, trusted person, link
  rules or waits. The names on the More screen (Sam, Jo) are sample text. The app still calls itself "Plainphone"
  in places (LMNOPhone strings are PLA-261).
- The More *screen* still has a thin divider line; only Home was in scope.
- `:device` unit tests: 488/493. The 5 failures are the Verifier's PLA-241 probe tests, which already fail the same way
  on preview-2. Their fixes live on `mvp/device` (PLA-242). preview-3 doesn't change `:device`.
- Evidence: spare screenshots `1-after-install-home.png` and `2-after-restart-home.png` are byte-identical, and both show
  the post-restart clock (9:51). So the after-install Home is evidenced by the text read-back only, not by its own
  screenshot. `4-` and `5-` are also identical (the same dial pad reached two ways).
- Home at 100 % text, 200 % text and on a short screen is covered by the Paparazzi goldens only
  (`HomeScreenshotTest[fontScale=1|2]`, `HomeShortScreenshotTest[height=480|540|560]`). Those variants were not
  captured on the emulator or the spare. The `HomeScreenTest` instrumented test was not run for this build.
- Emulator screenshots `0-before-home.png` and `1-after-install-home.png` are byte-identical: `0-` was taken in the
  second pass, after the upgrade. The real before state (v1, with the "Ask for an app…" line) is the text in
  `evidence/emulator/upgrade-install.txt`.
- The emulator Phone 9-1-1 check is not an exact pass (`911-911`, above). The check script prints the dial pad
  without asserting it. The next evidence run must clear the field, read it back empty, type 9-1-1 and assert exactly
  `911`, and fail on any failed read or tap (PLA-305 finding 1).
- Same known risk as preview-2: the unhide save flip is best effort (see `../preview-2/NOTES.md`).

## Way out
Only the spare's current driver runs these, and both name the spare explicitly:
`adb -s <spare> shell dpm remove-active-admin org.plainphone/.PlainphoneAdminReceiver`, then
`adb -s <spare> uninstall org.plainphone`.
