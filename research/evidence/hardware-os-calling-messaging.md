# Evidence: hardware, OS, calling and messaging for Plainphone

Researched 2026-10-08. Only primary sources are used: grapheneos.org, GrapheneOS GitHub and forum posts by project accounts, developer.android.com, developers.google.com, support.google.com, AOSP source, eCFR, and support.apple.com. Anything else is marked as a lead.

Each finding has a confidence label:
- **documented**: a primary source says it directly.
- **inferred**: follows from primary sources or AOSP code, but nobody states it outright.
- **unknown**: needs a test.

---

## 1. GrapheneOS and a third-party Device Owner

**1a. ADB `dpm set-device-owner` is currently the only way to set a Device Owner on GrapheneOS. The setup wizard has no QR provisioning.** *Confidence: documented.*
- On 2025-02-19 Headwind MDM wrote that "the only way to use MDM on GrapheneOS [is to] grant the Device Owner permissions by the ADB utility", and said six-tap provisioning did not start. The official **GrapheneOS** account replied the same day: "There isn't a better way to do it yet but we'd like for it to be supported. It would need to be integrated into our SetupWizard2 app." Source: https://discuss.grapheneos.org/d/13587-mdm-on-grapheneos
- Status of the QR-provisioning code, from GitHub:
  - PR #40 was closed without merging on 2026-10-07.
  - On 2026-05-22, thestinger (GrapheneOS lead developer) wrote on that PR: "the main blocker is that it needs major cleanup and changes to meet our requirements." Source: https://github.com/GrapheneOS/platform_packages_apps_SetupWizard2/pull/40
  - A follow-up PR, #48, was closed on 2026-07-31 with the comment "our team members will handle it." Source: https://github.com/GrapheneOS/platform_packages_apps_SetupWizard2/pull/48
- Conclusion: QR or NFC provisioning on GrapheneOS is not available today and has no committed date.

**1b. A fully managed device with a non-Google DPC is expected to work. Managed Google Play and Google-dependent EMMs are not.** *Confidence: documented in the forum; grapheneos.org has no Android Enterprise page.*
- **GrapheneOS** account, same thread: sandboxed Google Play is "optional and unprivileged so it can't do the special device management stuff that it usually does."
- I searched grapheneos.org /features, /usage and /faq for "Android Enterprise" and "device owner" (as a management feature). There are no matches. The official docs say nothing about Android Enterprise, so treat it as unsupported.
- Plainphone uses its own DPC and does not need managed Google Play, so this limitation does not block us. *Confidence: inferred.*

## 2. GrapheneOS and everyday apps

**2a. Sandboxed Google Play runs as an ordinary app with no special privileges.** *Confidence: documented.*
- "Google Play receives absolutely no special access or privileges on GrapheneOS." Source: https://grapheneos.org/features
- "Functionality depending on the OS integrating Play services and using it as a backend is unavailable." Source: https://grapheneos.org/usage

**2b. Banking apps: GrapheneOS is not Google-certified, so apps that require the certified or device-integrity verdict will fail.** *Confidence: documented.*
- "GrapheneOS passes the basicIntegrity check but isn't certified by Google so it fails the ctsProfileMatch check… GrapheneOS doesn't attempt to bypass the checks." Source: https://grapheneos.org/usage (Banking apps section; the wording still refers to SafetyNet)
- GrapheneOS asks app developers to accept it through the hardware attestation API instead: "Apps using the Play Integrity API… can support GrapheneOS by using the standard Android hardware attestation API and permitting our official release signing keys." Source: https://grapheneos.org/articles/attestation-compatibility-guide
- Whether a specific bank works depends on that bank. *Confidence: unknown.* The community list GrapheneOS links to (PrivSec.dev) is a lead only.

**2c. RCS in Google Messages works on GrapheneOS, but only with some conditions met.** *Confidence: documented.* Source: https://grapheneos.org/usage (RCS section)
- "GrapheneOS has support for RCS in Google Messages with sandboxed Google Play."
- "RCS with Google Messages is currently only known to work in the Owner user profile."
- Required steps:
  - "Google Play services needs to be granted the Phone permission."
  - "Google Messages needs to be set as the default SMS app."
  - Some carriers use GSMA TS.43 verification. For those, Play services also needs the "ICC authentication with device identifiers" special permission.

**2d. Supported devices.** *Confidence: documented.* Source: https://grapheneos.org/faq
- The list: Pixel 10a, 10 Pro Fold, 10 Pro XL, 10 Pro and 10; 9a, 9 Pro Fold, 9 Pro XL, 9 Pro and 9; 8a, 8 Pro and 8; Fold; Tablet; 7a, 7 Pro and 7; 6a, 6 Pro and 6.
- **The Pixel 11 series is not listed**, even though Google already sells it (Google's update page lists it: https://support.google.com/pixelphone/answer/4457705).
- US carrier-locked phones are a risk: "Devices sold in partnership with specific carriers may be locked by the carrier, which will prevent installing GrapheneOS. This is primarily an issue with US carriers." Buy unlocked phones.
- Support length: "8th generation and later Pixels provide a minimum guarantee of 7 years of support from launch." Google's page says the same for stock Pixel 8 and later: "7 years of OS and security updates." Source: https://support.google.com/pixelphone/answer/4457705

## 3. Stock Pixel: Device Owner provisioning for a custom open-source DPC without an EMM

**3a. QR, NFC and ADB provisioning all exist in the Android API.** *Confidence: documented.* Source: https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- `EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION` is described as "a url that specifies the download location of the device admin package… It can also be used for QR code provisioning."
- `EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` is "SHA-256 checksum of any signature" of the APK. "If the checksum does not match… the user will be asked to factory reset."
- `MIME_TYPE_PROVISIONING_NFC` "is used for starting the device owner provisioning."
- Google's own management docs describe the QR trigger: "the user… taps the screen six times in the same spot." Source: https://developers.google.com/android/management/provision-device

**3b. Since about 2025, Google blocks non-approved DPCs during QR/NFC enrollment on Google-certified devices. This is the biggest risk for a stock-Pixel MVP.** *Confidence: documented.*
- "Only DPCs verified and approved by Android Enterprise are permitted to install apps during enterprise device enrollment provisioning. The installation of any non-approved DPCs… will be blocked." The device shows "App blocked to protect your device."
- Approval excludes "Solutions developed exclusively for the purpose of device monitoring or eavesdropping independent of enterprise device management." A family dumbphone layer may or may not pass review. *Confidence: unknown.*
- Source: https://support.google.com/work/android/answer/16694822
- The exact start date is not on the page. bayton.org/blog/2025/12/the-dpc-allowlist is a lead for dates.
- Whether the allowlist also applies to `adb dpm set-device-owner` is not documented. My guess is that it does not, because ADB skips the enrollment app-install flow. *Confidence: unknown. Needs a real-device test.*

**3c. Google no longer accepts new custom DPCs for managed Google Play (Play EMM API).** *Confidence: documented.*
- "Android Enterprise is no longer accepting new registrations for custom device policy controllers (DPC) using the Google Play EMM API. All new EMM solutions should now use Android Management API." Source: https://developers.google.com/android/work/play/emm-api/register
- The page gives no date.
- This only matters if we wanted managed Google Play. A DPC that only uses on-device `DevicePolicyManager` APIs does not need it. *Confidence: inferred.*
- `afw#setup` downloads Google's own Android Device Policy, not a custom DPC. Source: https://developers.google.com/android/management/provision-device

**3d. Is a factory reset required? Yes, unless the phone has no accounts and no extra users.** *Confidence: documented in AOSP code.*
- AOSP `DevicePolicyManagerService` rules:
  - Non-ADB provisioning: "Only provision DO before setup wizard completes."
  - ADB after setup is allowed only if there are no other users and no accounts. Otherwise it fails with "Not allowed to set the device owner because there are already some accounts on the device."
  - Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/devicepolicy/java/com/android/server/devicepolicy/DevicePolicyManagerService.java
- In practice, a phone already in use has a Google account, so we would have to remove the account or factory reset.

**3e. A new sideloading rule is coming that affects how we distribute the APK.** *Confidence: documented.*
- Google's developer verification: "September 30, 2026 Protections begin… in Brazil, Indonesia, Singapore, and Thailand… Verification capability will be expanded globally for all apps on certified Android devices in 2027." Source: https://developer.android.com/developer-verification
- For a stock-Pixel build, plan to register as a verified developer.

## 4. Bootloader and anti-flash

**4a. A Device Owner can block "OEM unlocking" by setting `DISALLOW_FACTORY_RESET`.** *Confidence: documented in AOSP code.*
- OemLockService says: "The admin can prevent OEM unlock with the DISALLOW_FACTORY_RESET user restriction". When the restriction is set, it calls `setOemUnlockAllowedByDevice(false)`. Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/oemlock/OemLockService.java
- The Settings OEM-unlock toggle is greyed out when that restriction is present. Source: https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/development/OemUnlockPreferenceController.java
- `DISALLOW_OEM_UNLOCK` cannot be used by a DPC. It is `@hide`, deprecated, and in `IMMUTABLE_BY_OWNERS`. Sources: UserManager.java and UserRestrictionsUtils.java under https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/
- The public documentation for `DISALLOW_FACTORY_RESET` only says it blocks factory reset "from Settings". Source: https://developer.android.com/reference/android/os/UserManager
- **Gap:** a wipe from recovery mode using the hardware buttons is not blocked. *Confidence: inferred.* Factory Reset Protection (`setFactoryResetProtectionPolicy`, API 30) relies on an "FRP management agent", which is Google Play services on stock. Its behavior on GrapheneOS is unknown.
- Also set `DISALLOW_DEBUGGING_FEATURES` ("disables debugging features altogether, including USB debugging") and `DISALLOW_SAFE_BOOT`. Source: https://developer.android.com/reference/android/os/UserManager

**4b. GrapheneOS relocks the bootloader with its own keys and turns off OEM unlocking.** *Confidence: documented.*
- "Locking the bootloader is important as it enables full verified boot. It also prevents using fastboot to flash, format or erase partitions." Source: https://grapheneos.org/install/web
- "Our setup wizard also disables OEM unlocking by default at the end of the process." Source: https://grapheneos.org/features

## 5. Emergency calls (we must never block 911)

**5a. With lock task mode active, emergency calls from the lock screen are only allowed automatically if `LOCK_TASK_FEATURE_KEYGUARD` is set.** *Confidence: documented in AOSP code.*
- `LockTaskController` says: "Allow emergency calling when the device is protected by a locked keyguard" `if (isKeyguardAllowed(userId) && isEmergencyCallIntent(intent))`.
- It treats three things as emergency calls: `EMERGENCY_DIALER_COMPONENT`, `ACTION_CALL_EMERGENCY`, and the system dialer package.
- Wireless Emergency Alerts are always allowed through.
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/wm/LockTaskController.java
- The lock task mode guide on developer.android.com does not mention emergency calls at all: https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode
- `LOCK_TASK_FEATURE_GLOBAL_ACTIONS` covers the power menu: "the user may not be able to power off the device if this flag is not set." Source: DevicePolicyManager reference.

**5b. Suspending versus hiding the dialer.** *Confidence: documented for suspend; inferred for hide.*
- Suspend: "Some apps cannot be suspended, such as… the default dialer." Source: DevicePolicyManager `setPackagesSuspended`.
- Hide: `setApplicationHidden` has no dialer check in AOSP `PackageManagerService`. A buggy DPC could therefore hide the dialer. *Confidence: inferred from code.*
- A real GrapheneOS case: a user disabled the system Phone app and used Google Phone as the default. Google Phone then "did nothing" when they dialed 911, while normal calls worked. The **GrapheneOS** account replied: "Disabling system components via ADB is dangerous." Source: https://discuss.grapheneos.org/d/19726
- GrapheneOS then changed Settings so that the system Dialer cannot be disabled (PR https://github.com/GrapheneOS/platform_packages_apps_Settings/pull/345, linked by a forum moderator).

**5c. FCC rules put basic 911 duties on carriers, covering any compliant handset.** *Confidence: documented.* Source: 47 CFR § 9.10(b), https://www.ecfr.gov/current/title-47/section-9.10
- "CMRS providers… must transmit all wireless 911 calls without respect to their call validation process… 'all wireless 911 calls' is defined as 'any call initiated by a wireless user dialing 911 on a phone using a compliant radio frequency protocol.'"
- Phase II location (latitude and longitude) is a licensee (carrier) duty under § 9.10(e).
- FCC.gov consumer pages returned "Access Denied" to our fetcher, so we cited eCFR instead.

**5d. Android Emergency Location Service (ELS) runs inside Google Play services.**
- Google lists it under "Google Play services": it "Recognizes when a user starts an emergency call… Computes the location data from the Fused Location Provider… Sends the information directly to an endpoint". Source: https://developers.google.com/android/els/fundamentals. *Confidence: documented.*
- On GrapheneOS, sandboxed Play has no OS-level integration (see 2a), so ELS is very likely missing. Carrier Phase II location still applies. *Confidence: inferred.*
- The **GrapheneOS** account on emergency calls (2025-05-10): an emergency call "only enables the minimal functionality required to make an emergency call and provide a location estimate"; "doesn't change settings like SUPL, PSDS or network location." Source: https://discuss.grapheneos.org/d/22208
- GrapheneOS docs have no ELS or AML page. *Confidence: unknown.*

**Recommended controls to guarantee 911:**
1. Never hide, suspend, disable or uninstall the system dialer (`TelecomManager.getSystemDialerPackage()`), Telecom/Phone services, the cell-broadcast receiver, or (on stock) Google Play services.
2. Always include the system dialer in `setLockTaskPackages`. Always set `LOCK_TASK_FEATURE_KEYGUARD` and `LOCK_TASK_FEATURE_GLOBAL_ACTIONS`.
3. Put a permanent "Emergency 911" button in the launcher that fires `ACTION_DIAL` with `tel:911` to the system dialer.
4. Have the DPC check its own policy at startup and refuse any policy that touches the items in step 1.
5. Use an automated test that blocks release if the dialer is missing or blocked.

## 6. MMS, RCS and group texting in the US

- **Pixel's default SMS app is Google Messages.** *Confidence: inferred.* Google's pages assume it but none says "default on Pixel" outright. Lead: https://support.google.com/pixelphone/answer/6271667
- **RCS needs the carrier, Google Messages as the default SMS app, a data connection, and up-to-date Play services.** *Confidence: documented.*
  - "Make sure that Google Messages is your device's default app for SMS"; "Make sure Google Play Services is updated"; "If the message 'RCS chats has been disabled by your carrier' shows, contact your carrier." Source: https://support.google.com/messages/answer/9363493
  - "everyone in a group or 1-on-1 conversation must have RCS turned on." Same source.
  - Turning RCS off and on "you'll be removed from group chats". Source: https://support.google.com/messages/answer/7189714
- **A Device Owner can force the default SMS app.** *Confidence: documented.* Source: DevicePolicyManager reference.
  - `setDefaultSmsApplication`: "Added in API level 29… Must be called by a device owner".
  - `setDefaultDialerApplication`: "Added in API level 34".
- **RCS with iPhone.** *Confidence: documented.*
  - "To use RCS, you need iOS 18 and a cellular plan from a carrier that supports RCS messaging"; "RCS messaging is a carrier-provided service." Source: https://support.apple.com/en-us/122195
  - Apple's comparison page (https://support.apple.com/en-us/104972) covers iMessage vs RCS vs SMS.

## 7. Recommendation

**MVP target: an unlocked (not carrier-locked) Pixel 9a or Pixel 8 on stock Android, with Device Owner set by ADB from a clean, account-free setup. Run a GrapheneOS spike in parallel and decide on it for v2.**

| | Stock Pixel (8/9 class) | GrapheneOS on Pixel 8–10 |
|---|---|---|
| Setup effort | Factory reset, then ADB `set-device-owner` before adding any account. QR enrollment probably blocked by the DPC allowlist (3b). | Unlock the bootloader, flash, relock, then ADB `set-device-owner`. More steps, and needs a computer. |
| Banking apps | Work (certified device) | Some fail (2b) |
| RCS / iPhone interop | Works through Google Messages | Works only in the Owner profile, with extra permissions (2c) |
| 911 location | Carrier plus Google ELS | Carrier only; ELS likely missing (5d) |
| Updates | 7 years from Google | 7-year floor; Pixel 11 not supported yet |
| Anti-tamper | `DISALLOW_FACTORY_RESET` locks OEM unlocking; recovery wipe still possible | Same, plus relocked custom-key verified boot |
| Support burden | Lower: one vendor, familiar apps | Higher: compatibility questions, sandboxed Play setup |
| Policy risk | DPC allowlist (now); developer verification (2027) | Neither applies (not Google-certified) |

For two non-technical US users, stock Pixel is the safer MVP choice: banking apps work, RCS and iPhone texting work, and Google ELS adds 911 location. GrapheneOS is strong on privacy and avoids Google's DPC allowlist and developer-verification gates, but costs more support effort.

**Emulator can answer:**
- DPC policy logic and `dpm set-device-owner` preconditions
- Lock task allowlist and emergency-dialer launch from lock task, with and without `LOCK_TASK_FEATURE_KEYGUARD`
- `setPackagesSuspended` rejecting the dialer, and `setApplicationHidden` on the dialer
- `setDefaultSmsApplication`
- The `DISALLOW_FACTORY_RESET` effect on the OEM-unlock toggle (needs an image with a persistent data block)

**Needs a real device (gated by the board):**
- Whether the DPC allowlist blocks QR provisioning of our DPC on Pixel and on GrapheneOS
- Whether the allowlist affects ADB provisioning
- A real 911 test (use test procedures coordinated with the PSAP or carrier), with ELS present (stock) or absent (GrapheneOS)
- RCS activation on each target US carrier, including GSMA TS.43 on GrapheneOS
- MMS and group texting with iPhones
- The specific banking apps our users need
- Whether a recovery-mode wipe gets around Plainphone, and what FRP does after it
- The carrier-lock and OEM-unlock state of the purchased units

## Errata (2026-10-08, after board correction and cross-model review PLA-3)

- **The users are in Canada ([town]), not the US.** §5c (FCC), §6 (US RCS), §3e (verification timing) and §7 (Pixel recommendation) are replaced by `canada-and-motorola.md`, which covers CRTC/E-Comm, Canadian carriers, Canada in the 2027 wave, and Motorola support life.
- **§5d, ELS quote:** the Verifier found that https://developers.google.com/android/els/fundamentals does not contain the quoted "Google Play services" wording. Treat that attribution as **unverified**. That ELS is live in Canada is documented separately (Google Canada blog, May 2024; see `canada-and-motorola.md` §1d). That ELS is absent on GrapheneOS remains an inference.
- **§5a, keyguard:** the `LOCK_TASK_FEATURE_KEYGUARD` exemption is a rule about launching screens. It is not proof that a call goes through, and not the only path to the dialer: an allowlisted dialer task also works (AOSP 15 LockTaskController L387–401, L452–474). Do not phrase it as "911 works only with KEYGUARD" or "KEYGUARD guarantees 911".
- **§3e:** Google's verification page also lists a free limited-distribution option for up to 20 devices. It is a candidate to evaluate, not a decision.
- **§2c, RCS on GrapheneOS:** it is conditional (Owner profile, extra permissions). It is not incompatible.
