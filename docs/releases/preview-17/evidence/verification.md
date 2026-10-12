# Preview 17 author verification

[PLA-387](/PLA/issues/PLA-387), final scope from the Lead’s 2026-10-12T04:23Z
comment: real PIN setup and entry only. No waiting-start route.

## Built

Merge `ae71cca3` pins device commit `5e5810f9`; enrollment `c6ed292c`; host/bump
`d48e7395`. The preview starter plan, grouped skip, Home-before-snapshot and
`captureAfterPublished` remain. Update has no app references or construction.
Lost-record reconciliation remains held without a full policy input; it cannot
replace the preview plan or write reset-protection accounts.

Required `:device:test :plainphone:test :plainphone:assembleDebug` passed: 715
device tests per debug/release variant and 183 app tests per six variants. Zero
failures/errors/skips in the final run; no unrelated baseline failures. Nine
copied gate tests pass; comparison confirms only version strings changed.

Tests were added before host implementation and failed on its missing types
(`pin-access-red.log`, `pin-flow-red.log`). Focused tests then passed. The first
full run found the older non-test navigation expectation still listed the newly
wired trusted-person row as unavailable. Both navigation suites now assert the
new effect and retain every other unwired row. `candidate-tests-first.log` records
that failure; `candidate-tests.log` and `test-totals.json` record final success.

The UI reuses accepted PIN/menu components and their strings. No golden images
were rerecorded. Fresh vault grants are immediately consumed for this informational
menu only. No privileged action is hosted. Access and input clear on pause; tests
cover duplicate submissions and stale successful callbacks after leaving.

## Installed

Only retained API 35 AVD `pla363-policy-a35`, `emulator-5880`, was driven.
In-place 16 → 17 at **2026-10-12T04:33:54Z**. Readback: `17`,
`0.1.0-preview-17`, testOnly and same signer. First install remains
`2026-10-11 14:05:10`. No uninstall, reset or reprovision.

Built APK, pulled installed APK and decompressed upload all match:
`d662781d994c22134a6991f630eea82a1618ab939fa3c9faac1735f9d5c29f12`.
Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
[APK gzip](/api/attachments/ad19ed97-5b2f-48fb-94b5-66aa850df3a2/content).

## Exercised

Real UI: More → For Sam and Jo → Choose a new PIN → confirm → fresh PIN entry;
wrong PIN refused; correct PIN opens Your settings; Add an app unavailable;
Home then reentry requires PIN. Reboot: setup is not offered, the wrong PIN still
fails, and the same correct PIN opens the controls. See `pin-route.jsonl`, which
contains only visible text after inputs were cleared; no PIN is printed or saved.
The driver initially expected “not right”; the actual existing string is “isn’t
right”. Correcting that host text assertion allowed the same run to continue.
No app change, re-enrollment or seeded authority was used.

**Required no-profile check passed:** enrollment uses SELF_MANAGED credentials
without a recovery profile; the enrollment pass is ready, attention=false, with
the same preview plan. No lost-record reconciliation appears after enrollment or
reboot. The first locked-start log has attention=true in both build 16 and 17;
this is the carried-forward pre-unlock baseline, not a new PIN-triggered notice.
All unlocked full passes report attention=false, and Home has no attention/pending
line. Before/enrolled/final safety records exactly match: owner, active restrictions,
lock list/features/state, first-install time, counts preference, and every hidden,
suspended and disabled package set. Adb remains reachable.

Unchanged gate **exit=0** (`gate.log`): More → Emergency prefilled exactly 911;
Phone typed exactly 911; Telecom zero current calls on each route. Digits cleared,
Home restored. No Call button, call intent or SOS gesture. All four screenshots
were visually inspected. The protected controls screenshot is black as intended;
PIN screens were never made capturable for evidence. No personal data.

Reproduce the gate from repo root:

```sh
SKIP_INSTALL=1 python3 docs/releases/preview-9/evidence/gate_on_root_avd.py \
  app/tools/preview-17 emulator-5880 \
  app/plainphone/build/outputs/apk/debug/plainphone-debug.apk \
  docs/releases/preview-17/evidence
python3 docs/releases/preview-9/evidence/readback.py final emulator-5880
```

The existing AVD sdcard EFAULT workaround restores the host adb root after reboot;
app UID and gate assertions are unchanged.

One natural unlock sample (`boot-gate.txt`) versus build 16’s retained sample 2:
first Home at **14.008 s since boot**, compared with **14.893 s**; **3 full unlocked
passes** for each, with the same reasons (user-unlocked, android package change,
package burst). First snapshot remains user-unlocked pass 4 and follows Home
(14.358 s versus Home 14.008 s). This is one emulator observation, not a claimed
performance improvement or physical-device result.

Review and the physical spare install/readback remain with the Lead. Pending
changes, profile enrollment, Update and turn-off remain unavailable; see notes.
