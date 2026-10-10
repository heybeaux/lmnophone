<!-- Copy of the Verifier & Escape Artist's review document on PLA-13 (2026-10-09), committed by the Lead so other agents can read it. Not edited. -->

# Emulator spike cross-model review

**Verdict: REQUEST CHANGES.** The spike supplies useful feasibility evidence, but does not establish a go decision. ADR 0001 gate **#1: inconclusive**; gate **#3, account-free portion: inconclusive**. These are evidence verdicts, not a conclusion that Device Owner cannot work. The review is complete; an adverse verdict is a completed review.

Reviewed [PLA-11](/PLA/issues/PLA-11), authored by the Android Platform Engineer, at merge commit **33a21d73ef631c4496269decfa8bb0ad085118fa** (author branch tip `8457b6e`), against feasibility questions revision 6 and ADR 0001 at that commit. Paths below are relative to `spikes/emulator/` unless stated otherwise. The source files were not edited.

Independent evidence: [logs, screenshots, build output and APK hashes](/api/attachments/47a5a330-a846-40bb-b1c9-9d984bee34df/content). I built the unchanged DPC and two fixtures from an archive of the reviewed commit, offline, and tested only my isolated **emulator-5640**, API 35, build `google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.036/12592187:user/release-keys`. Build succeeded. No account was added. The emulator was stopped after testing. I did not drive the author's emulator, either protected emulator, or any physical phone. API 36 conclusions below are reviews of the submitted evidence, not independent reruns.

## Gate decisions

| Gate | Verdict | Reason |
|---|---|---|
| ADR #1: normal day with BLOCK, pre-grants and image attachment | **Inconclusive — not passed** | Calls, SMS, camera and permission pre-grants have useful observations. The image was never attached in Messages: selection produced an unsupported-attachments dialog with lock task both on and off. That control rules out lock task as a necessary cause of this observed rejection; it neither demonstrates a working image route nor proves the exact SIM/carrier cause. The emergency home button still uses the implicit intent known to fail with the fixture installed. UI-exception coverage is incomplete. |
| ADR #3: account-free Q4 work | **Inconclusive — not passed** | The generic external-link tests do not cover verified App Links, parsed intent URIs, real Custom Tabs fallback, or the actual must-have apps. WebView navigation was omitted from the submitted run. UI menu and captive-portal controls have gaps described below. Account-dependent sign-in, recovery, payment and update checks remain outside this review; they cannot be counted as passes. |

Remove “go, with 3 fixes” from the decision summary until the required observations exist. Do not silently relax the image-attachment gate or substitute device evidence for the requested emulator result; any change to the gate belongs to the Lead's architecture decision process.

## Findings and required corrections

### V1 — High: the UI-exceptions “yes” rests on stale and unsuccessful walks

`evidence/q1/exceptions-messages-a35.txt` records Help, Privacy, Terms, Spam, Archived and Settings repeatedly remaining on `BugleExpressSignInActivity` or `GaiaDittoActivity`. Their screen text is still Device pairing and there is no new activity-start evidence. The heading for “re-run 2” has no results beneath it. This does not establish that those menu destinations were exercised or contained. In `exceptions-dialer-a35.txt`, at least one Learn more attempt is explicitly `NO NODE`.

The Dialer Blocked numbers/Quick responses tests were run before `com.android.server.telecom` was added. The report itself calls for a rerun. Safety Hub is also on the final floor UI list without a corresponding complete escape walk. A list of Dialer/Messages menu labels is not an inventory of every UI-allowed package and every same-UID sibling required by Q1.

**Reproduction/check:** compare each claimed menu row with its actual resulting UI text and START event; the repeated Device pairing screen exposes the missing transitions. Then use the final read-back allowlist, reset to a known menu before every tap, assert the target node exists, and save the destination and nested outgoing links. Cover Telecom, Safety Hub, phone settings, keyboard, photo/file pickers and account/help surfaces. Use an unrestricted successful route as a control for each route claimed blocked.

The sentence “the allowlist is per package, not per UID” must also be narrowed to the measured STK activity launch. Android documents shared-UID eligibility to activate lock task. This is a different claim from whether one external launch to `StkMain` succeeds. Preserve the observation, record UID inventories, and test the relevant activation paths rather than declaring siblings safe. [Android DevicePolicyManager reference](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setLockTaskPackages(android.content.ComponentName,%20java.lang.String%5B%5D)).

### V2 — High: the result detector can turn a failed stimulus into a containment pass

`tools/q1-matrix.sh:22` labels **every** DPC activity `BLOCKED`, including the intended link-catcher. It samples only the foreground activity after 1.5 seconds; it does not assert that the launch happened, distinguish target absence from policy denial, or detect a target that opened briefly and returned. `ActivityNotFoundException` can mean hidden target, absent target, or incorrect stimulus.

The logs do provide genuine `BlockedAppActivity` observations for many cases; those should be retained. The problem is the aggregate claim that all routes have been proven safe. The same oracle labels a working `LinkActivity` as blocked. The menu logs and captive notification log also contain failed tap operations.

**Required correction:** distinguish `POLICY_DENIED`, `LINK_CAUGHT`, `NO_HANDLER`, `TARGET_OPENED`, and `TEST_FAILED`; fail a case on a missing UI target or launch error not expected by that case. Assert starting and ending policy state. Add a successful unrestricted control for every target and capture activity events across the interval. BLOCK-off alone is not a useful control for cases that fail in both configurations.

### V3 — High: Q4's matrix substitutes simpler operations for the requested cases

- The App Links fixture is explicitly **unverified**, state `1024`. Neither approved verified-domain delivery nor the rejected/unapproved verified case was tested.
- `MainActivity.buildIntent` uses `Uri.parse` as raw VIEW data for `intent://…`. It never reconstructs the embedded Intent. The no-handler result therefore does not test a browser/WebView parsing that URI. Android exposes `Intent.parseUri(..., URI_INTENT_SCHEME)` for that operation. [Android Intent reference](https://developer.android.com/reference/android/content/Intent#parseUri(java.lang.String,%20int)).
- `ct=1` adds a null Custom Tabs session extra. It tests that VIEW intent, not an actual client's provider-selection and fallback behavior when no provider is available.
- The URL-bar fixture is classified through a hardcoded catalog entry. No submitted observation navigates its WebView; classification is not containment.
- The never-hide control changes labels for packages that do not match the heuristic. It does not exercise the important conflict case of an essential package that actually matches the browser heuristic. The self-identity control is useful but is a dry-run classification, not a hidden/unhidden behavioral comparison.

**Independent reproduction:** with Chrome hidden by `classify`, add `com.plainphone.fx.urlbar` to the lock-task list, open `com.plainphone.fx.common.UrlBarActivity`, enter `https://example.com`, and press Enter. The live page loads inside its WebView while `lockTaskModeState=1` and BLOCK remains set. Remove the fixture from the list and repeat the explicit launch: `BlockedAppActivity`. Evidence: `approved-webview.png`, `reproduction.txt` and `review-logcat.txt` in the attached archive.

This is the expected approved-app WebView limitation already disclosed by the spec, not a newly discovered Android defect. It must be demonstrated and traced to approval/catalog decisions for real apps. No claim is made that this fixture proves Slack, Spotify, Telegram, Gmail or Outlook exploitable. Rerun the missing matrix cases using approved and unapproved fixtures with working controls.

### V4 — High for recovery claims: clean-state T1 does not establish the unconditional floor invariant

The system-only discovery query correctly rejects the user-installed fake emergency app in the submitted clean-state test. The naive-query negative control is useful. However `Commands.kt:76` applies the floor by **unioning the existing lock-task packages with the floor**. It does not remove stale authority.

**Independent conditional reproduction:** seed `com.plainphone.fx.emergency` into the lock-task list using the authorized test hook; run `floor-apply`; explicitly launch `com.plainphone.fx.emergency/com.plainphone.fx.common.FxActivity`. It opens with BLOCK on because the stale entry remains. Control: replace the list without that fixture, apply the floor, and repeat; the fake app is blocked. Screenshots: `floor-stale-entry.png` and `floor-clean-reject.png`.

The seeding step requires administrative test access. This is **not** evidence that a restricted user can add themselves to the list. It proves only that this spike's helper cannot validate revocation/recovery from a previously bad list. There is no supported/unknown-build/corrupt-policy state machine in this spike. Mark those Q6/T1 states untested; use a replacement baseline with readback when testing that contract.

Separately, the committed home Emergency button still constructs an unpinned intent (`MainActivity.kt:35–36`). Submitted `a35-floor-t1.txt` proves the equivalent implicit launch hits a blocked resolver and the package-pinned launch succeeds. The actual button tap in that log failed with a shell arithmetic error. The diagnostic pinned launch proves an available workaround, not that the committed user route was fixed. Test the corrected actual button and all required emergency entry states; never place an emergency call.

### V5 — Confirmed escape in the throwaway harness: user can stop containment

`MainActivity.kt:40` exposes “Stop lock task” without authentication and clears the saved locked state. `setup.sh` installs no anti-escape user restrictions; the submitted and independent status reads show `restrictions=[]`.

**Independent reproduction, ordinary UI after setup:** Home → Stop lock task → swipe down twice → Settings gear. Settings opens (`com.android.settings/.homepage.SettingsHomepageActivity`); status changes from mode `1`, `locked=true` to mode `0`, `locked=false`. Before the stop, the same Settings launch was blocked. Evidence: `user-stop-to-settings.png`, `stop-button-settings.png` and the transcript.

Treat this as an intentional harness limitation, not an architecture failure. It prevents describing this build as the full restricted-user policy. Keep controls behind the privileged test interface when measuring adversarial containment, and assert the full intended restrictions separately.

I also checked the exported activity's `op=lt-stop` path. In my run it did **not** produce a persistent escape: status remained mode `1`, and the subsequent Settings launch was blocked. Do not report that route as a confirmed bypass. Its unauthenticated command surface still deserves separation from a production launcher.

### V6 — Medium: Wi-Fi and hiding results have a narrower scope than “yes”

`day-a35-controls.txt` shows a PSK configuration accepted, but an actual validated connection only to the **open** AndroidWifi network. This is not the ordinary WPA2 connection required by Q4. `a35-captive.txt` uses host-controlled 204/302 probes: useful for the captive-state/message branches, not sufficient proof of general internet access.

The log's sign-in notification taps include arithmetic errors; unhidden CaptivePortalLogin also never opens. The author appropriately labels this hiding control inconclusive in the detail; the summary should retain that qualification. Showing a Forget button does not establish network removal: no resulting forget/readback test is recorded. Calls and Emergency were not rechecked during the captive condition.

**Required correction:** prove a genuine successful captive notification launch with a control configuration, then test it hidden under the intended policy; exercise Forget and read back connection/configuration changes; complete the WPA2/internet and calls/Emergency cases or explicitly mark the emulator limitation inconclusive.

### V7 — Medium: evidence provenance and causal claims need tightening

The image fingerprints and commands are useful, but no complete app version inventory accompanies the original results. `setup.sh` grants fewer permissions than the later manual run and does not apply browser/CaptivePortalLogin hiding or the full restrictions. A fresh invocation therefore does not recreate every reported “final policy” state.

Record app versions, APK hashes, exact ordered setup, final policy readback and controls per case. The attached independent archive includes my APK hashes, build output and package version readouts; these are **not** retroactive proof of the author's installed app versions.

Use **inconclusive/not exercised**, rather than a platform “no”, for QR enrollment on these already-provisioned images. The repeated-owner control tests owner exclusivity, not the QR protection. The QR script's download URL is emulator-specific (`10.0.2.2`) and must be replaced and checked before any owner-authorized physical-device procedure; the image cannot simply be reused unchanged.

The report generally keeps emulator and device results separate. Preserve that good boundary. “Nothing core breaks” should read “the tested Dialer/Messages opening and assistant-resolution checks passed”: Gemini was absent; Lens/Circle to Search, actual voice typing and the real app list were not covered. The power-button behavior changed, and the report's assistant explanation is a hypothesis, not an isolated cause.

## Result-by-result evidence and controls audit

“Observed” below preserves a narrow result even when the required deliberately failing control is missing. It is not a gate pass.

| Result | Evidence supports | Negative-control assessment |
|---|---|---|
| Q3 adb ownership, API 35/36 | Yes on these two images, no protection screen observed | API 36 repeated-owner rejection works for exclusivity only; no equivalent API 35 control recorded |
| Q3 QR | Inconclusive on these images; setup absent and trusted-source entry permission-denied | No enrollment run; no working QR comparison |
| Incoming/outgoing calls | Simulated calls and incoming ACTIVE state observed | No deliberately broken calling/allowlist control; not carrier call delivery |
| Incoming/outgoing SMS | Incoming notification/UI and outgoing type=2 row observed | No deliberately failing SMS/default-app control; not carrier MMS/RCS |
| Notifications | SMS notification opens Messages | No notification-feature/permission-off comparison |
| Pre-grants | Requested camera/mic/location grants read back; Camera opens | Camera revocation control works; equivalent feature checks for every other grant not supplied |
| Camera capture | File and screenshot support capture | Camera permission control supports this narrow case |
| Image attachment | Picker opens, attachment fails | Lock-task-off comparison also fails; working image route remains inconclusive |
| Power menu | Chord works; diagnostic long-press setting works | No GLOBAL_ACTIONS-off control; no physical-button/device claim |
| Reboot | Lock task returns after boot | No broken-home/relock control or continuous foreground trace; secure first-unlock interval untested |
| Own Wi-Fi | Screen opens, addNetwork accepted, open network validates | System Settings denial tests a different property; promised non-owner addNetwork control has no recorded result |
| Emergency | Package-pinned diagnostic opens keypad | Implicit intent fails with fixture; remove-required-package control not supplied; actual button not corrected |
| Blocked-launch matrix | Many explicit blocked screens on API 35/36; phone RadioInfo/Call settings open | BLOCK-off opens some Settings/share/Photos routes; cases failing both ways lack a successful baseline; V2 applies |
| UI exceptions | A subset of Dialer and initial Messages routes | Incomplete/stale final-list walk; V1 applies |
| Browser role | Hidden Chrome retains role; locked role-request UI blocked | Before/after holder readback is useful; no unlocked role-acquisition control |
| Classifier | Listed fixture labels and browser hiding observed | Self and naive controls useful; essential-browser conflict and catalog false-negative cases absent |
| External link matrix | Narrow ordinary VIEW/explicit hidden-target behavior | No behavioral successful unhidden control per case; verified/parsed/client-fallback cases absent |
| Captive handling | Simulated 204/302 classification and notice | Working captive-launch control missing; forget/WPA2/call continuity missing |
| Google app / Gemini | Google app hidden, selected resolution probes fail, Dialer/Messages open | No matched unhidden behavior comparison; Gemini absent, therefore not tested |
| T1 | Clean computed floor excludes fake fixture; explicit launch denied | Naive query admits it; independent stale-state/clean-list comparison adds the V4 limitation |
| Q9 / Q11 | Deferred | Optional; acceptable to defer. Q9 follow-up should follow revision 6's VPN/per-app attribution questions, not silently substitute only Private DNS. No independent verification of a GrapheneOS image-availability claim in this review |

## Remaining Escape Artist probes

These are **unexecuted attack recipes**, not claimed bypasses. Run only on an owned emulator, under a recorded final policy, with controls that make the target reachable when protection is removed.

| Surface | Reproduction to add / evidence required |
|---|---|
| UI exceptions and shared UIDs | Starting from final floor: Dialer → Calls/Blocked numbers/Quick responses; Emergency → information; Safety Hub menus; enumerate every UI package's UID siblings. Follow help/privacy/account links and distinguish entry denial from self-activation eligibility. |
| In-app browser and recovery | In each approved real app, follow a received external URL, Help, Privacy, password reset and account-add route; test redirect and parsed intent/custom-scheme hops. Account-free entry tests can run now; authenticated recovery needs the separately authorized account path. |
| Keyboard, accessibility, notifications | From a text field exercise GIF/search/help/settings and voice input; from notifications try long-press settings, inline reply, link actions and bubbles; test an already-enabled accessibility service as well as service enablement. |
| Share/photo/file/install | Camera thumbnail; Messages Folders/photo picker; ACTION_SEND with image URI; GET_CONTENT/OPEN_DOCUMENT; open an APK from a reachable provider. Test before/after an app update changes handlers. |
| Floor/reboot | Repeat fake-emergency rejection on supported, unknown and corrupt-policy states, including stale authority and a previously hidden legitimate emergency component; queue activity launches before first unlock and trace the first interactive frame. |
| Safe mode / reset / adb / stores | On a disposable emulator verify the intended restrictions and an already-authorized adb host; exercise safe-mode and Settings-reset routes with broken-control comparisons. Account-free installer/update-window races remain untested. Recovery-reset/FRP, physical buttons, account-dependent Play behavior and OEM paths are not inferred from this spike. |

## Handoff

The Plainphone Lead owns the go/no-go decision in [PLA-14](/PLA/issues/PLA-14). Carry forward the two **inconclusive** gate verdicts and the narrowly supported observations. The Android Platform Engineer should correct the report and evidence under V1–V7 before requesting another approval review; physical-phone work remains with its separately authorized owner in [PLA-5](/PLA/issues/PLA-5). This reviewer has not edited or approved the spike and has not authorized any device work. [PLA-13](/PLA/issues/PLA-13) is complete with the adverse verdict above.

