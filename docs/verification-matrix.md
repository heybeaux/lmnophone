# Verification matrix: requirement → evidence → test → gate

- Revision 3, 2026-10-08 (PLA-3 F17, PLA-4 N8–N10). Device results from PLA-5 are marked **[device ✓]** where they are already in.
- Owners: **AE** = Android Platform Engineer; **VE** = Verifier & Escape Artist; **Lead**.
- Gates:
  - **G0**: go/no-go for the architecture
  - **G1**: before handover on Beaux's approved phone
  - **G2**: release
  - **opt**: optional
- Evidence file abbreviations:
  - **upd** = `app-updates-under-lockdown.md`
  - **web** = `webview-and-escape-threats.md`
  - **hw** = `hardware-os-calling-messaging.md`
  - **ca** = `canada-and-motorola.md`

| Spec → requirement | Source or hypothesis | Test ID | Expected observable outcome | Gate | Owner |
|---|---|---|---|---|---|
| device-lockdown → Device Owner + home | DPM reference (documented); **[device ✓] adb setup works on XT2415 / Android 15 (PLA-5 A1)** | Q3, Q5-E21 | `dpm list-owners` shows Plainphone; Home always opens Plainphone; no chooser | G0 | AE |
| device-lockdown → first-frame invariant, built-in safe policy | PLA-4 N4 | Q6 | No unapproved app opens from unlock to lock task; both copies corrupt → safe policy | G1 | AE, VE |
| device-lockdown → escape restrictions | UserManager (documented); OemLockService (code-confirmed) | Q5 | Safe mode refused; OEM unlock greyed out; adb refuses an authorised host; add-user is absent | G1 | AE, VE |
| device-lockdown → in-task blocking | Lock task guide; DPM `BLOCK_ACTIVITY_START_IN_TASK` (documented); usability **unknown** | Q1 | All normal-day items pass with the flag on; Settings, panels and Play never open (both launch modes); every UI exception has a negative test | G0 | AE, VE |
| device-lockdown → reset protection | dpc/security FRP section (documented); effect **unknown** | Q10 | Readback matches the `userId`; after a wipe, the user's account is refused and a listed account is accepted | G1 on the emulator; on the target phone G1 **before handover**, with a separate board-approved recovery-wipe procedure (never on the spare) | AE |
| device-lockdown → safe storage and boot | Direct Boot docs (documented); hypothesis | Q6 | Locked-boot path uses device-protected data only; a corrupt policy falls back to last-known-good, then to the emergency-safe baseline; a damaged journal revokes everything; no never-hide package touched | G1 | AE |
| approved-apps → can't be opened | Hypothesis (reactive hiding + lock task) | Q2-S7, Q5 | No tested entry point opens an unapproved app; install-to-hidden time recorded | G0 | AE, VE |
| approved-apps → updates and monthly check | upd 1b (code-confirmed); Play path **inferred** | Q2-S1..S4 | Rolled-back preinstalled app updates **unattended** within 72 h while Play is unreachable, compared with an unrestricted run; no update offered = inconclusive; monthly SMS fires | G0 | AE |
| trusted-person-controls → supervised session contract | Design D2; PLA-4 N1 | Q12 | Journal written before the relaxation; revoked at every start, including locked boot; for a build that crashes on every start, what stays reachable is **measured and reported against the custody limit Beaux accepted** (not pass/fail); both session kinds; the protected-state check restores protections | G0 (revocation-first); custody limit = board acceptance | AE, VE |
| approved-apps → remove/re-approve | DPM `setApplicationHidden` (documented) | unit + Q6 | Data survives hide and unhide | G2 | AE |
| calm-home-screen → plain home, always-present items | Design | UI tests | Time, date, Emergency, Phone, Messages and approved apps only | G2 | AE |
| calm-home-screen → own settings | `WifiManager.addNetwork` Device Owner exemption (**to confirm**) | Q1 | Wi-Fi joins without Android Settings appearing | G0 | AE |
| calm-home-screen → readable | WCAG 2.2 AA | UI and a11y tests | Largest font: primary action visible, no horizontal scroll | G2 | AE, UX |
| trusted-person-controls → PIN | Design D6 | unit + Q12 | Count survives reboot; delay ≤1 h; no wipe | G1 | AE, VE |
| trusted-person-controls → recovery code | Design D6 | unit + fault injection | Atomic rotation; old or new works, never neither | G1 | AE |
| trusted-person-controls → fresh authority, no outside input | Design D6 | Q5 (forged intents) | Forged extras change nothing | G1 | VE |
| trusted-person-controls → requests | Design D6 | unit + Q8 | Approval is bound to the installed package and signing certificate; SMS failure shown | G2 | AE |
| trusted-person-controls → Plainphone update over local Wi-Fi | Design D6; Android same-key update rule (documented) | Q6, Q12 | Exact package, valid signature, pinned signer, strictly higher `longVersionCode`, staged bytes, session bound to the package; refused: wrong signer, other package signed with the same key, same or lower version, a response that changes, a redirect, a truncated file; post-update self-check; crash-on-start build → recovery path | G1 | AE |
| trusted-person-controls → turning off | `clearDeviceOwnerApp` (deprecated; **unknown**) | Q7 | Resumes after a kill at every step; "Off" only when complete; data kept | G1 | AE, VE |
| trusted-person-controls → disclosure | web Part 3; PLA-3 F13 | UX review | Plain-language screen lists every accepted risk | G1 | UX, Lead |
| web-containment → classifier and hiding | DPM; App Links docs (documented) | Q4 | Fixtures classify correctly; the Wi-Fi sign-in page is hidden and normal Wi-Fi still works | G0 | AE |
| web-containment → link matrix | DPM ("invoking the browser… cannot be configured") (documented); browser role **unknown** | Q4 | Plainphone holds the browser role; the matrix behaves as specified | G0 | AE, VE |
| web-containment → safe blocked-link screen | Design D4 | unit + Q5 | Deceptive URLs show the real host; userinfo and query removed; extras ignored | G1 | AE, VE |
| web-containment → web-leak notes per version | Design D4 | UX + unit | "Reviewed for an older version" appears after an update | G2 | UX |
| essential → never obstruct emergency access | AOSP LockTaskController (code-confirmed); the dialer can't be suspended (documented) | Q14, unit | Never-hide guard refuses changes; dialer reachable in every state | G0 | AE, VE |
| essential → behavioral release gate | PLA-3 F2; PLA-4 N8 | Q14 | Gate passes on a good build and **fails** on all three negative-control builds; alert check = pass or **not verified**, never assumed | G2 | AE |
| essential → real-device checks before handover | PLA-4 N8; E-Comm callback page | Q14 [device], Q13 | Emergency dialer opens in every state (no call placed); incoming-call keypad sends tones; alert receiver present; manufacturer emergency components protected | G1 (before handing to a user) | AE, VE |
| essential → no call filtering | ca §1 (E-Comm callback) | Q8 | Unknown-number call rings during lock task | G1 | AE |
| essential → calls, texts, alerts | DPM `setDefaultSmsApplication`; support.google.com/messages (documented) | Q8; [device] | Google Messages is default; SMS and MMS arrive; RCS and iPhone groups work on the real carrier | G1, including the device part before handover | AE |
| guided-setup → preinstall sweep with no network active; build inventory | **[device ✓] 7 pushed installs + 1 preload within 8 min on XT2415 (PLA-5 A0 table; count to reconcile)** | Q5, setup dry run | No preinstalled or pushed app can be opened; handover check lists them as hidden | G1 | AE |
| web-containment → Wi-Fi sign-in unsupported | PLA-4 N5 | Q4 | Normal WPA2 works with CaptivePortalLogin hidden; a sign-in network shows the message and can be forgotten | G0 | AE |
| guided-setup → guide and checking script | `dpm` preconditions (code-confirmed) | dry run by a non-author | ≤30 min; refuses an unlocked bootloader or unapproved model | G1 | UX, AE |
| guided-setup → helper custody, authenticated resume | PLA-3 F11 | Q12 | Resume needs the PIN; setup can't be re-entered after handover | G1 | AE, VE |
| guided-setup → handover check | Key attestation docs (documented) | Q5, Q10, Q14 | No "Ready" with any failing item | G1 | AE |
| Architecture fallback | ADR 0001 go/no-go list | Q1–Q4, Q7, Q10 | Any failure → revised ADR + Beaux's approval | G0 | Lead |
| Manufacturer extras | ca §6 | Q13 [device] | Every extra feature has a hide/keep/test decision | G1 | AE, VE |
| DNS filter | web §1.3 | Q9 | Not a protection in the MVP | opt | AE |
| approved-apps → monthly check can't silently lapse | PLA-7 R6; SmsManager / SEND_SMS references (documented) | unit + Q2 + handover | Stored due date; retries; home-screen line after 7 days overdue; completion only after a PIN in a Play session; end-to-end test text at handover | G1 | AE |
| essential → alert display observed before handover | PLA-7 R5 | Q14 [device] | Alert seen on the target build, or else custody / Beaux's recorded acceptance of a narrower promise; emulator with no injection = inconclusive (blocks release) | G1 | AE, VE |
| device-lockdown → build inventory and emergency-safe baseline | PLA-7 R3 | Q6, Q13, unit | Inventory present for the build; baseline keeps calls, in-call UI, Emergency, alerts and SMS working; missing inventory → no sweep, no Ready | G1 | AE, VE |
