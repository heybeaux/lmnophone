## Context

- **Users.** The two initial users in Canada. Neither is technical. Each is likely the other's trusted person.
- **Architecture.** `docs/decisions/0001-architecture.md` (revision 6) names the leading candidate:
  - a Device Owner app that is also the home screen;
  - running on a certified stock Android phone with long security support.
- **Evidence.**
  - Source files: `research/evidence/`.
  - Device results so far come from the spare Motorola ([PLA-5](/PLA/issues/PLA-5), `spikes/device-moto/RESULT.md`):
    - **A1 / Q3: yes.** Our unapproved, test-only Device Owner app was set up over adb on a certified Android 15 Motorola. No allowlist message appeared.
    - **A2: yes.** `dpm remove-active-admin` and `clearDeviceOwnerApp()` both returned the phone to unmanaged with no wipe.
    - Within 8 minutes of first boot, with Wi-Fi and no account, the committed A0 table shows 7 pushed installs: 4 Digital Turbine games (`com.dti.motorola`), plus Google One, Fit and Wallet via Play. Facebook comes preloaded. The count still needs reconciling with PLA-5's comment, which said 5 games.
- **Reviews.** Revision 5 answers PLA-8 (S1–S5), on top of PLA-7 (R1–R8), PLA-4 (N1–N11) and PLA-3 (F1–F17). Every requirement traces to `docs/verification-matrix.md`.
- **Device A3 (lock task on the XT2415, partial).** Test results with the block-in-task flag:
  - **Flag off:** App info opened inside the task, leading on to Permissions and Notifications settings. The share chooser opened, and the Camera thumbnail opened Google Photos.
  - **Flag on:** App info and the share chooser were blocked, and so were runtime permission prompts ("Permission controller is not available right now"). The Photos thumbnail route was **not run** with the flag on.
  - **Emergency dialer:** it was blocked in both modes until `com.android.phone` was on the lock-task list. With it listed, the dialer screen opened with the flag **off**. This was **not re-run** with the flag on. No call was placed.
  - **Notifications:** Quick Settings showed no tiles, and notifications aimed at apps outside the list were blocked.
  - **Photo sharing:** the Camera → Photos → Share → Messages route failed with the flag off, and was not run with it on. A direct **text** share to Messages opened Messages in both modes. No image attachment was tested.
  - **Permissions:** pre-granting them is proposed, **not tested**.
  - **Not yet tested:** reboot, power menu, lock-screen emergency, calls and SMS.
- **Policy constraints:**
  - Physical work only on the board-approved spare (the spare test device), within its rules: Settings resets only, no recovery wipes, test-only DPC.
  - No remote repo.
  - No paid services.
  - Cross-model review.

## Goals / Non-Goals

**Goals:**
- **One app.** One APK contains the Device Owner, home screen, trusted-person screens and the blocked-link screen.
- **No unapproved app can be opened** through any tested route, from the first interactive frame after unlock. Leftover routes are named, and Beaux accepts them in writing.
- **Emergency first.** LMNOPhone never gets in the way of 9-1-1, calls, texts or emergency alerts, in any state, including while LMNOPhone cannot run.
- **Updates.** Approved apps keep updating, and the trusted person does a supervised update check once a month.
- **Recovery.** Every state has a written recovery path. The last resort is a recovery wipe plus the trusted person's Google account.
- **Simple setup.** A helper finishes setup in 30 minutes or less, measured as defined in guided-setup.
- **Temporary access is custody.** Any temporary relaxation of the lockdown is a supervised session in the trusted person's hands. We do not promise it closes itself if LMNOPhone cannot run.

**Non-Goals:**
- Remote portal or server.
- DNS or VPN filter (Q9 only).
- Monitoring.
- GrapheneOS.
- Phones Beaux hasn't approved.
- Wi-Fi networks that need a sign-in page.
- Any adb-based maintenance mode.
- Promising that no unapproved code ever runs.

## Decisions

### D1. Lock task with in-task blocking, plus hiding

Unapproved apps are hidden with `setApplicationHidden`. Approved apps run in multi-app lock task mode with these features:
- `HOME`, `KEYGUARD`, `GLOBAL_ACTIONS`, `NOTIFICATIONS`, `SYSTEM_INFO`
- `BLOCK_ACTIVITY_START_IN_TASK`
- never `OVERVIEW`

Any system screen a normal day needs goes on a reviewed **UI exceptions list**. Q1 finds these screens. Each exception gets negative tests.

The home activity starts lock task before it draws its first interactive frame. Restrictions, hidden apps and lock-task settings are saved by Android and survive a reboot.

### D2. Supervised sessions are custody, with a journal written first

**Purpose.** A supervised session is the only way the lockdown is ever relaxed. The MVP has two kinds:
- **Play:** add an app, or the monthly update check. Deadline 10 minutes.
- **Settings:** for anything LMNOPhone's own settings screen doesn't cover. Deadline 5 minutes.

**Contract** (trusted-person-controls):
1. A fresh PIN is required to start.
2. The relaxation and its deadline are written to a journal in device-protected storage **before** anything is relaxed.
3. Only the session's packages are added to lock task. Debugging, factory reset and safe mode are never relaxed.
4. The session ends on Done, screen off, the deadline, or leaving the session's packages.
5. On ending, LMNOPhone revokes the relaxation, reads the policy back, and then clears the journal.
6. On every LMNOPhone start, including a Direct-Boot-aware `LOCKED_BOOT_COMPLETED`, LMNOPhone revokes anything still in the journal before doing anything else.

**What we promise (N1, R1).** If LMNOPhone cannot run, nothing closes the session automatically, whatever the cause: a real bad update, a storage fault, or a crash loop. The relaxation stays until a helper fixes the phone.
- The screen says "Stay with the phone until you tap Done".
- The phone is never handed back until the policy reads back correctly.
- Beaux must explicitly accept this limit (ADR go/no-go #7).
- Q12 measures what stays reachable in that state, and checks that revocation runs first whenever LMNOPhone can run. It does not prove the failure never happens.

**Session scope (R2).**
- **Play sessions** add only `com.android.vending`.
- **Settings sessions** add only the build's Settings package and the same-UID siblings listed in the build inventory.
- **On close,** a protected-state check restores anything that breaks a protection, while keeping ordinary changes such as ringtone or Wi-Fi. It covers: restrictions, lock task, the never-hide inventory, browser role, default SMS app, SMS permission, keyboards and accessibility services, and alert settings.
- **A damaged journal** means every relaxation is revoked.
- **The locked-boot path** uses device-protected data only.

**Why not adb maintenance.** It was cut (N2). Its scope can't be narrowed, and it relies on Settings and host authorisation.

### D3. App updates: Play kept but out of reach; per-app record; monthly check

**Play stays.** The Play Store stays installed, enabled and signed in to the user's own account. It is outside lock task except during a Play session. `DISALLOW_INSTALL_APPS` is never set permanently.

**Unapproved installs.**
- Any install, including first-boot preinstalls and Digital Turbine pushes, stays hidden until it is approved.
- LMNOPhone hides on the package-added broadcast, and also checks at boot and every 15 minutes.
- If hiding fails, the app may run in the background, with no time limit. This is disclosed.

**Approval and permission edits.** The app appears on Home only after its permissions are set. Save the pending choices first, hide the app and check that it is hidden. Before writing permissions, check battery, take the app out of lock-task access and Home, and read those exclusions back. Then briefly unhide it with the usual flush and readback. Write every Deny before any Allow, and check all permissions while visible. Re-hide and check hidden on every exit, including a refused write or a failed check. Check permissions again while hidden before the final approval checks and save. Elevated approvals also perform their final grant check while hidden. The existing committed activation then applies the full policy and brings the app onto Home.

This write window addresses the API 35 emulator result recorded in `app/device/evidence/pla86/hidden-permissions-api35.txt` on the device branch at `3bbd1f9`: hidden permission writes were refused, visible writes succeeded, and the saved choices remained readable after re-hiding. Suspending the app is not a substitute: that experiment misread sensor grants. These are build-specific test results, not a claim about every Android build.

A restart first re-hides any PREPARING or EDITING app, before battery, session or eligibility checks can stop recovery. The ordinary policy then hides/excludes and discards PREPARING work; a new approval needs fresh authority and rewrites every choice. EDITING retains its saved choices and retries them after hiding first. Permissions already written to an unapproved app may remain while it is hidden. A failed re-hide leaves the work pending and returns NOT_READY; the next startup retries hiding before anything else. Lock-task and Home exclusions remain in place throughout the window. This implements the existing user-facing promise; it does not promise that an unapproved app can never run background code.

**Update record and check (N9).**
- LMNOPhone records each approved app's version and the time it last updated.
- Once a month (with the due date and last-completed date stored, daily retries for 7 days, then a single line on the home screen: "Ask [trusted person] to do the monthly phone check"; R6) it texts the trusted person to run the update check, a Play session that opens Play's own update list. That list shows which updates are waiting and which have failed. LMNOPhone does not claim to know this itself.
- If Google Play services goes 30 days without an update, LMNOPhone texts the trusted person.

### D4. Web: classifier with self-exemption, scoped link promise, no Wi-Fi sign-in pages

**Classifier (N3).**
- LMNOPhone (exact package name and signing certificate) and never-hide packages are excluded first. A conflict is flagged for review, never hidden automatically.
- A package is a "general browser" if `queryIntentActivities(MATCH_ALL)` lists it for both http and https on the local probe `plainphone-probe.invalid` (never fetched), or if it is in the reviewed catalog of known browsers.
- Apps that only open links to their own verified domains are allowed.

**Link handling.**
- External http(s) links that Android would send to a browser go to LMNOPhone's blocked-link screen.
- Explicit intents to hidden browsers fail.
- Navigation inside an approved app's own WebView is **not** intercepted. This is disclosed.
- The blocked-link screen runs as a separate activity with no access to admin functions.

**Wi-Fi sign-in pages are unsupported (N5).**
- CaptivePortalLogin is hidden, with no exception session.
- When Android reports a captive portal, LMNOPhone shows: "This Wi-Fi needs a sign-in page, which LMNOPhone doesn't allow. Use mobile data or another Wi-Fi." It also offers "Forget this network".
- Q4 checks that normal Wi-Fi still works.
- **Later (approved direction, not in the MVP):** Beaux (Telegram 4481, 2026-10-08, PLA-17 card) approved the **direction**: a Wi-Fi sign-in window that LMNOPhone ships (a companion app with no admin rights; Android's sign-in app stays hidden), which the user can open alone once a trusted person has switched it on with the PIN. Limits: a 5-minute target per try, 3 tries in 24 hours, a text to the trusted person each time with no Wi-Fi name. He accepts that a hotspot can show other websites for the whole window and that some sign-in pages (Google account, bank app) won't work. It is built **after the MVP core**, in a follow-up OpenSpec change shared with the general web window (Telegram 4481, PLA-14), after an emulator spike and cross-model review, and Beaux approves that change before build. Details: `docs/wifi-signin-recommendation.md` revision 3. Until that change is approved and built, this section stands.

**Web-leak notes** are kept per app version.

### D5. Emergency: never-hide list including OEM components; behavioral gate; real-device handover checks

**Never-hide list.** The base list:
- the system dialer
- Telecom
- the cell broadcast receiver and its alert display
- Google Play services
- the default SMS app

Q13 adds the manufacturer's emergency components, such as Motorola's SOS and emergency information. The policy engine refuses to hide, suspend, disable or uninstall any of them, or to disable any of their components.

**Getting to Emergency.**
- Lock task always includes the dialer, and `KEYGUARD` and `HOME` are always set.
- No Emergency button on the home screen (Beaux, Telegram 4487). The routes are: Phone (the system dialer); the lock screen's Emergency call; the power-button SOS gesture where Q14 confirms it works under lock task; and an optional entry inside More. Inside an approved app: press Home, then Phone. The power-menu entry stays where the phone has one.
- When LMNOPhone can't run: the lock screen and the system dialer still work.

**Release gate.** The release gate covers every state that the emulator can reproduce. It must fail on three negative-control builds:
- Phone (or the More emergency entry, if built) doesn't open the system dialer;
- the dialer is missing from lock task;
- the alert display is disabled.

If an alert can't be injected, the result is **inconclusive**, which blocks release. The only way past it is a build-scoped exception that Beaux records; that exception must also say whether the disabled-alert negative control was verified.

**Before handover to either initial user (N8), on their actual model and build:**
- the emergency dialer opens in every state, without placing a call;
- an incoming call can be answered, and its keypad sends tones (E-Comm's automatic callback needs a keypress);
- an alert is **observed** on screen, either from a scheduled Alert Ready test or a test method Beaux approves. Otherwise the phone stays in custody, or Beaux records a build-scoped exception and the promise becomes "LMNOPhone does not block alerts";
- the manufacturer's emergency components are on the never-hide list.

A live 9-1-1 call needs a separate procedure agreed with E-Comm or the carrier, approved by Beaux. Until these checks pass, the phone stays in supervised test custody.

**Calls are never filtered.**

### D6. Trusted person on the device

**PIN (N7).**
- Stored as PBKDF2-HMAC-SHA256 with at least 600,000 iterations and a 16-byte salt.
- Failures 1–4 cause no delay. The 5th failure starts a 30-second delay, which doubles with each further failure, capped at 1 hour.
- Entries made during a delay are rejected without being counted.
- The failure count and the remaining delay are saved together in one write. A reboot keeps the *remaining* delay; it doesn't restart it.

**Recovery code.** 128 bits, stored only as a hash. It has its **own** failure counter, so a PIN lockout never blocks it. Using it changes the PIN and code together in one step.

**Fresh entry for every privileged action.** No external intent or extra can grant authority.

**App requests.** The request text is treated as untrusted. Approval is tied to the installed package and its signing certificate.

**LMNOPhone's own updates (N2).**
- The helper's computer serves the APK on the local Wi-Fi.
- The trusted person enters the PIN and the URL.
- **Binding (R4).**
  - The APK is downloaded to private storage: at most 100 MB, a 2-minute timeout, no redirects.
  - The staged file is checked: exact package name, valid signature, the pinned signer, and a strictly higher `longVersionCode`.
  - Exactly those bytes are installed through a PackageInstaller session with `setAppPackageName` set.
  - Any open session is closed and read back first.
  - After the update, LMNOPhone runs the handover self-check; installing successfully is not the same as starting successfully.
  - There is no rollback promise.
  - The key has an offline backup custodian, plus lost-key and compromised-key procedures.
- LMNOPhone checks that the APK's signing-certificate digest matches the one built into it and that its version is higher. Then it installs silently as Device Owner.
- No adb and no Settings are involved.
- Release builds are `testOnly=false` and `debuggable=false`.
- The signing key is held by Beaux, or by someone he names, never on a phone.
- A bad update falls back to the "LMNOPhone can't run" recovery path, which is tested in Q6 with a build that crashes on purpose.

**Turning off (N6).**
- LMNOPhone restores every change it recorded, each read back.
- Before removing Device Owner, it checks that another home screen and the dialer resolve.
- It resumes from a journal after a restart. If it finds it is no longer Device Owner, it treats turning off as done.
- It never silently falls back to a wipe.

### D7. Our own settings screens

LMNOPhone's own settings screen uses Device Owner APIs:
- Wi-Fi, through `WifiManager.addNetwork` (to be confirmed in Q1)
- Bluetooth pairing
- volume and ringtone
- brightness
- text size
- battery

Anything else needs a supervised Settings session. There is no generic "helper task" fallback.

Build 30 (approved PLA-428) is the read-only Bluetooth subset: the adapter and
bonded-device names are read with BLUETOOTH_CONNECT (legacy BLUETOOTH on API 30),
with a one-second visible-page poll and fresh permission readback. Device Owner
may grant only that permission to this app. No Bluetooth mutation, discovery,
Location dependency or system UI is introduced. Names are never persisted.

### D8. Setup: helper custody, sweep before Wi-Fi, reset-protection IDs without secrets

**Order of steps:**
1. Back up the phone.
2. Reset it through Settings.
3. With the SIM removed and eSIM disabled, go through the first-boot wizard and skip Wi-Fi, accounts and app recommendations. The script refuses to continue if any network is active, or if there is no build inventory for this phone.
4. Turn on USB debugging.
5. Run the script. It checks that the model and build are approved, the bootloader is locked and verified boot is green, then runs `set-device-owner`.
6. The script checks every network connection type is down, and that a build inventory exists. LMNOPhone then runs a local baseline check (it does not place real calls or texts) and hides every package outside the floor, the inventory and the allowlist.
7. Only then does the helper reinsert the SIM or turn the eSIM back on, and join Wi-Fi. Real calls and texts are checked after this, before handover.
8. Sign in to the user's own Google account and turn on Play auto-update.

**Reset protection (N6).**
- On their own device, the trusted person opens the People API `people.get` "Try it" page. They pick the exact account (by address) in Google's account picker and authenticate with `personFields=names`. Then they copy the digits after `people/` as text. Accounts less than 72 hours old are re-tested later; Q10 on the target phone gates handover.
- LMNOPhone shows the digits back for confirmation. It collects no tokens or passwords.
- The helper records that the trusted person can sign in from a device other than either LMNOPhone, and that their second factor isn't only on an LMNOPhone.
- A backup person's ID can be added. This is recommended when the two initial users are each other's trusted person.

**Handover check.** LMNOPhone shows "Ready" only when all of these pass:
- Device Owner set
- restrictions applied
- adb and wireless debugging off
- key attestation passes
- reset protection read back
- browser role held
- never-hide list intact
- emergency self-test passes
- Play auto-update on
- preinstall sweep done
- an end-to-end test text received and confirmed by the trusted person

### D9. Policy storage: atomic, validated, built-in safe policy

- **Writing.** The policy is written atomically and checked against its schema. A last-known-good copy is kept.
- **Direct Boot copy.** A minimal copy is kept in device-protected storage, with a checksum: setup-complete flag, PIN and recovery counters, session journal, and the last supported build fingerprint.
- **Runtime emergency floor (S1, T1).** Computed live at every pass, from OS-managed identities and `MATCH_SYSTEM_ONLY` queries only. Self-declared intent filters never count, and non-system apps never qualify. Members: the system dialer, the default dialer if it is a system app, the system emergency-dialer package, the in-call UI, the SMS role holder if it is a system app, cell broadcast, the WebView provider, the system keyboards and the permission controller. They are never hidden. **Only the emergency UI subset** (system dialer, emergency-dialer package, in-call UI) goes on the lock-task list. The floor outranks everything else.
- **Unknown build after an OTA (S1).**
  - Updates are postponed with `createPostponeInstallPolicy`. On `onSystemUpdatePending`, the trusted person and the helper get a text.
  - On an unknown build, lock task stays on, with the previous list plus the emergency UI subset. All floor packages are unhidden, but only that subset gets UI access. Only user-installed packages get newly hidden. Changed packages are listed under "Phone software changed – review pending".
  - The team builds and reviews the new inventory, the Android Platform Engineer prepares it and the Verifier reviews it, and it ships as a signed local update.
  - The phone is back to normal only after the handover self-check and the emergency re-checks pass on the new build.
  - If the build is unknown **and** the policy is corrupt, the baseline is the floor plus Messages, LMNOPhone and every restriction, with no sweep.
- **Build inventory (R3).** One versioned inventory per supported model and build fingerprint, compiled into the APK. It lists:
  - never-hide dependencies: dialer, Telecom, in-call UI, cell broadcast service, receiver and alert display, Play services, Play Store, default SMS app, selected keyboard, permission controller, WebView provider, setup and account components, and manufacturer emergency components;
  - minimum UI entries.

  The inventory outranks the allowlist and the classifier. If the inventory is missing or invalid, there is no broad sweep and no Ready.
- **If both policy copies are invalid,** the **emergency-safe baseline** applies. It is compiled from the build inventory and does not depend on any copy that can change: never-hide dependencies, minimum UI entries, Messages, and every restriction. LMNOPhone then shows "LMNOPhone needs attention". The baseline helps only while LMNOPhone can run.
- **Order of application:** journal revoke → restrictions → never-hide guard → hides → lock task → home preference. Each step is read back.
- **When it runs:** at locked and unlocked boot, on package events, after trusted-person actions, and every 15 minutes.
- **The policy engine** is a pure function.

### D10. Codebase

- Kotlin and Jetpack Compose. minSdk 30.
- Accessibility: WCAG 2.2 AA, large text.
- No analytics.
- TestDPC and Headwind are reference code only (both Apache-2.0); no fork.
- Spike code stays under `spikes/`. MVP code goes in `app/` and starts only after approval.

## Risks / Trade-offs

- **[QR setup blocked by Google's DPC allowlist]** → adb works on a certified Motorola running Android 15 (device test A1). QR is not tested and not needed. Each new model or build is checked by the script and by Q3 on that model.
- **[The in-task block flag breaks a normal day]** → Q1, run with the flag on and off. The UI exceptions list has negative tests. Otherwise, a revised ADR.
- **[Play doesn't update unattended]** → Q2: the monthly check plus a Play services warning. If Q2 fails outright, a revised ADR.
- **[Must-have apps break]** → Q4 against the real app list counts as go/no-go.
- **[Sessions stay open if LMNOPhone can't run]** → custody, the journal, revoking at every start, a protected-state check before handback, and disclosure. Beaux's explicit acceptance is required. Without it, an independent revocation mechanism becomes a G0 question.
- **[An LMNOPhone update crashes]** → signature and version checks, the Q6 crash test, and the recovery path.
- **[Recovery wipe]** → reset protection with the trusted person's and a backup person's IDs. Their ability to sign in elsewhere is checked. FRP does not reinstall LMNOPhone.
- **[Preinstalls and pushed apps]** → sweep before Wi-Fi, then the package watcher.
- **[Web inside approved apps]** → web-leak notes and disclosure.
- **[Wi-Fi networks that need sign-in]** → unsupported, with a plain message.
- **[Short support life]** → the spare Motorola's security updates end in March 2027, and it is not a target phone. Beaux picks the target model.
- **[Manufacturer extras]** → Q13 on each supported build. Every item ends in a tested outcome or a risk Beaux has accepted.

## Migration Plan

- **Rollout order:**
  1. emulator
  2. spare Motorola (spike only)
  3. Beaux approves the target model
  4. Beaux's phone, after the real-device handover checks
  5. two weeks of use
  6. The partner's phone
- **Rollback:** turn LMNOPhone off (journaled and resumable). As a last resort: a recovery wipe plus the trusted person's account, with Beaux's approval for that phone.

## Open Questions

**Answered by Beaux (Telegram 4446/4448, 2026-10-08, relayed by Scout):**
1. **Device.** The spare XT2415 is the device for the spike and the MVP. The daily-use model is deferred and needs its own OK.
2. **People.** The two initial users are each other's trusted person. The backup is a separate Google account that Beaux controls. *Lead note:* on Beaux's own phone, that backup lets Beaux pass reset protection after a wipe. This weakens the check the partner provides on his phone. It is fine for the partner's phone. **Beaux confirmed (Telegram 4448/4451): keep it.** He accepts the weaker check on his own phone. The partner's phone is unchanged.
3. **License.** Apache-2.0.
4. **Must-have apps.** Beaux: Slack, Spotify, and maybe Telegram. The partner (Telegram 4448/4451): Gmail and Outlook. *Lead notes, all leads to verify in Q4:*
   - Telegram has its own in-app browser, which is a likely web-leak route.
   - Gmail and Outlook both render HTML mail and open links. Each may open links inside the app (an in-app browser or Custom Tab) rather than handing them to Android, so the blocked-link screen may never see them.
   - Gmail is preinstalled on the Motorola, so it is a system app, and it carries other Google surfaces (Chat, Meet tabs).
   - Outlook's sign-in, and adding a Google account to Outlook, may go through a web sign-in page. With every browser hidden, that sign-in may fail.
   - **Found and accepted (Beaux, Telegram 4463):** in Outlook 5.2639.2, Privacy & Cookies opens a WebView that follows off-site links, before sign-in and after sign-out (PLA-5 B-Q4). Outlook stays in the catalog with a plain web-leak note (ADR Risk 3). A later version that makes the route easier goes back to Beaux.
5. **Carrier.** Rogers, which is on Apple's RCS-with-iPhone list.
6. **Helper and signing key.** Beaux is the helper and keeps the key. The offline backup is on an encrypted USB stick or in his password manager, kept away from the laptop.

7. **Plan, leftover risks and custody limit.** Accepted with the plan (PLA-1 confirmation eb7a21ba).

8. **Wi-Fi sign-in and Wi-Fi calling (Telegram 4481, PLA-17).** Sign-in Wi-Fi stays unsupported in the MVP. The approved direction for later is in D4. The two initial users rely on Wi-Fi calling **sometimes**, so the handover checklist (task 6.3) checks that Wi-Fi calling works on ordinary Wi-Fi with LMNOPhone's policy on. Wi-Fi calling on a sign-in network only works once the sign-in is done, which Beaux accepted.

**For the spike:** Q1–Q14 in `docs/feasibility-questions.md`.

Build 33 (board decision on PLA-425, delivery PLA-437) extends the Wi-Fi subset
with DO addNetwork/enableNetwork. Existing configurations are selected unchanged.
A process-wide device adapter runs a deterministic join controller and a temporary
Location controller. Device-protected ownership is committed before enabling;
cleanup is read back, deferred during calls, retried, and recovered at startup.
Passwords stay ephemeral in the visible UI and are passed only to Android's
network configuration. No lock-task list or emergency policy changes.

The PLA-448 board acceptance authorizes the tap-time Forget Location window (PLA-460).
Reuse `WifiLocationController` and its device-protected marker in `WifiJoinSession`.
Wi-Fi identity gets up to two seconds to reflect the enabled Location setting.
The serialized Forget operation then has a ten-second readback bound and never iterates
removals. Opening Location is followed by cleanup on every terminal path; session
exit cancels pending verification and restores owned Location. The adapter rechecks
the live connected ID immediately before removal. Eligibility uses connected and
validated transport facts, so Location need not be enabled merely to render the row.
