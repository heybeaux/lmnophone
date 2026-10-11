# Preview-5 divider correction

Work: [PLA-336](/PLA/issues/PLA-336), delta to the accepted branding evidence
from [PLA-335](/PLA/issues/PLA-335). Lead owns the delta check and spare routing
under [PLA-333](/PLA/issues/PLA-333).

## Built

- Candidate source: `28506e3e0f077a49a90deb719d9fbae82cc1cfb4`.
- Golden-first checkpoint: `9b454b9`; eight More images at font scales 1 and 2.
- APK SHA-256: `f43b93978c8994bc25b44ec66bc8d40532a79cead43becd69fed87b3e048204c`.
- Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
- `org.plainphone`, versionCode 5, `0.1.0-preview-5`, testOnly retained.
- Artifact: [preview-5.apk.gz](/api/attachments/86a0f931-87de-4bb0-8822-a247977fe08f/content).

Deleted only the More `HorizontalDivider` import and invocation. No extra
spacing was necessary: existing 16 dp row padding separates the rows. The
64 dp minimum, chevrons, hierarchy, text-link underlines and navigation remain.
Every changed golden was viewed before commit, including the trusted one/two
person and self-managed one/ten-minute variants.

## Source scan

`separator-scan.txt` records the requested scan over both main source trees.
Before the change the only divider was More's import and invocation. Afterwards:

- No `Divider`, `drawBehind`, `drawLine` or `height(1.dp)` rules remain.
- The only `.border(` is Ask for an app's text field; kept as requested.
- `Box` matches are task buttons, text links, Home measurement/content containers,
  the preview explanation, boot checking and shell containers; none are row rules.
- `ScreenBox` is a filled content card, not a separator.
- The shared action's `drawWithContent` / `drawRoundRect` focus border is retained.
- Phone settings and Ask for an app need no visual change. Android Settings was
  not changed.

## Changed-path checks

JDK 17, offline, from `app/`:

```
./gradlew --offline :ui:testDebugUnitTest :ui:verifyPaparazziDebug :ui:lint :plainphone:testDebugUnitTest :plainphone:assembleDebug
```

| Check | Result |
|---|---|
| UI JVM / Paparazzi tests | 110/110 pass |
| App JVM | 84/84 pass |
| Paparazzi verification | Pass, all 168 committed images |
| UI lint | 0 errors, 11 warnings |
| APK build / signing | Pass |

The eight affected old goldens failed after removing the lines, before the
intentional re-record (`divider-red.txt`). Only those eight images were
re-recorded, then the complete requested checks passed (`divider-checks.txt`).
`:device` was not rerun because this delta changes no device path. Its five
established baseline failures remain recorded in the prior verification report;
this run neither fixes nor reports them as passing.

## Installed

Owned AVD `pla306-policy-a35`, API 35, `emulator-5862`. The AVD had the old
preview 5. Restored the exact accepted preview-4 APK (SHA-256
`cbf8ad4a80f7cbb1a0eb3eb63b09a4f1d53e1b36de0a55e783db8fa31f4a5232`)
with `install -r -t -d`, retaining data instead of resetting or uninstalling.

The new APK was installed in place at **2026-10-11 16:20:58 UTC** (installer
completion; package timestamp 16:20:57 UTC). The count preference and actual
switch read on before and after the upgrade. Original first-install time,
Device Owner and LOCKED state were preserved. See
[install-readback.json](../emulator/divider-correction/install-readback.json).

The accepted upgrade tool is unchanged. After retention proof, counts were
temporarily switched off for its existing Phone selector, as in the previous
accepted run. It uses `SKIP_INSTALL=1` to carry this recorded install forward.
Physical-device installation and screenshots remain the sole driver's work.
This report is emulator evidence only, not physical acceptance.

## Exercised

The repeated `upgrade-check.sh` exited **0**. Restart returned Home only after
full readback. More > Emergency prefilled exactly 911; Home > Phone accepted
exactly 911 after clearing the field. Both routes reported zero current Telecom
calls. Digits were cleared afterwards. No call control was pressed and no call
or message was sent. Version, testOnly, Device Owner and LOCKED read back after
the route checks. See [upgrade-check.txt](../emulator/divider-correction/upgrade-check.txt).

Final count switch was restored on and read back after restart/routes. Normal
text size was restored after screenshots. At 200%, a scroll exposes For Sam and
Jo and About this phone without a trailing rule. The first capture swipe used
DP-sized coordinates against a 1080×1920-pixel display and did not reach About;
a physical-pixel swipe corrected the capture. No app change was needed.

Inspected screenshots (360×640 dp, synthetic data only):

- [More 100%](/api/attachments/24e9e8f9-9189-4393-bb54-068507676777/content)
- [More 200%, top](/api/attachments/429dd60c-b83f-41b5-95cf-25ffd1981907/content)
- [More 200%, bottom](/api/attachments/854375b3-0587-4a39-ac27-0f8da2da05fb/content)
- [Phone settings, counts on](/api/attachments/f00b0fca-7c30-464f-972a-f1ab0e1c013c/content)

The 200% Phone settings capture is also committed as `settings-2.0.png` in the
same emulator evidence directory. Prior evidence is retained; the current More
and upgrade proof is this correction directory. Physical normal/large-text More
screenshots after the spare install remain the driver's acceptance proof.
