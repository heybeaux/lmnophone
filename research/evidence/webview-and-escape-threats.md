# In-app browser / WebView leakage and escape threat list

Researched 2026-10-08. Confidence labels: **documented** (official Android reference or guide says so), **code-confirmed** (AOSP `main` source shows it), **inferred** (follows from docs or code, untested), **lead** (vendor docs or memory, verify before relying on it). Sibling file: `app-updates-under-lockdown.md` covers Play and `DISALLOW_INSTALL_APPS`.

Sources used throughout. "DPM", "UM" and similar abbreviations below refer to these pages.
- DPM: https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- UM: https://developer.android.com/reference/android/os/UserManager
- VPNB: https://developer.android.com/reference/android/net/VpnService.Builder
- CM: https://developer.android.com/reference/android/net/ConnectivityManager
- LTM: https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode
- PKGVIS: https://developer.android.com/training/package-visibility/use-cases
- VPNGUIDE: https://developer.android.com/develop/connectivity/vpn
- DPCSEC: https://developer.android.com/work/dpc/security
- FRPP: https://developer.android.com/reference/android/app/admin/FactoryResetProtectionPolicy
- AOSP-OEM: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/oemlock/OemLockService.java
- AOSP-VPN: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/connectivity/Vpn.java
- AOSP-CPL: https://android.googlesource.com/platform/packages/modules/CaptivePortalLogin/+/refs/heads/main/src/com/android/captiveportallogin/CaptivePortalLoginActivity.java

## Key findings

1. **We cannot route browser intents with `addPersistentPreferredActivity`.** DPM (API 21) says: "Implicit intents that do not trigger this mechanism (like invoking the browser) cannot be configured as they are controlled by other configurations." The DPM has no `setDefaultBrowser` (only `setDefaultDialerApplication` and `setDefaultSmsApplication`). The workable approach is to hide every browser so that our own "link catcher" activity is the only `http(s)` handler. *(documented; whether we then get the browser role automatically is inferred, so emulator test)*
2. **WebView cannot be removed.** If no provider is left, "WebView will simply not work and WebView-based apps will crash" (Chromium WebView docs, lead-grade upstream doc: https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/webview-providers.md). Banking, maps and ride-share apps depend on it.
3. **Network filtering cannot separate an app's API calls from its in-app browser.** An app's WebView traffic leaves under that app's UID *(inferred from WebView's in-process design; confirm with `getConnectionOwnerUid`)*. The only lever is the destination domain.
4. **`DISALLOW_FACTORY_RESET` also forces OEM unlocking off.** *(code-confirmed, AOSP-OEM lines 101–108, 179, 256)*
5. **Lock task mode is the most important control.** Non-allowlisted apps (Settings, Chrome, the Google app, Play) cannot start new tasks. Quick Settings stays disabled even when notifications are enabled. *(documented, LTM and DPM)*
6. **CaptivePortalLogin is a real escape hatch.** It is a system WebView browser that binds to the Wi-Fi network directly and can download APKs. *(code-confirmed, AOSP-CPL)*

## Part 1: Leakage channels and controls

### 1.1 Standalone browsers (Chrome, Samsung Internet, Firefox)

| Control | What it does | Source |
|---|---|---|
| `setApplicationHidden(admin, pkg, true)`, API 21 | "When a package is hidden it is unavailable for use, but the data and actual package file remain." Intents stop resolving to it. | DPM, documented |
| `setPackagesSuspended`, API 24 | "A suspended package will not be able to start activities." The app still resolves, so the user sees a system "app not available" dialog instead of a failure. | DPM, documented |
| No browser left | Apps targeting API 30+ get `ActivityNotFoundException` from `startActivity(ACTION_VIEW, https)`. Google tells apps to catch it. | PKGVIS, documented |
| Custom Tabs | `CustomTabsIntent` "uses the ACTION_VIEW Intent". If no browser supports Custom Tabs, it "will open the user's default browser instead". With no browser at all, `ActivityNotFoundException` is thrown, and many apps then fall back to **their own WebView**, which we can't filter. | Chrome docs (vendor, lead): https://developer.chrome.com/docs/android/custom-tabs/guide-get-started |
| Package visibility, API 30+ | Apps can't see which browsers are installed unless they declare `<queries>`. `CustomTabsClient.getPackageName()` returns null if no Custom Tabs provider exists, and the app picks its own fallback. Our DPC needs `QUERY_ALL_PACKAGES` or explicit queries to list browsers. | PKGVIS, documented |

**Plan.** Hide all browsers (Chrome, plus any OEM browser) with `setApplicationHidden`. Ship a Plainphone "link catcher" activity that handles `VIEW` + `BROWSABLE` + `http`/`https`. It shows "This link is blocked" and offers "Ask [trusted person] to allow this site". It does **not** implement `CustomTabsService`. On Android 10+, hiding Chrome does not break WebView, because the provider is `com.google.android.webview` (Chromium doc above).

**Known trade-off.** Some sign-in flows (OAuth/AppAuth, some 3-D Secure bank checks) need a browser or Custom Tab. With no browser they break, or fall back to the app's own WebView. Alternative design: keep **one** browser installed but exclude it from lock task. A Custom Tab opened inside an allowed app's task still works, because a locked app "can start new activities as long as the activity doesn't start a new task" (LTM). "Open in Chrome" starts a new task and is blocked. Then give that browser's UID a strict per-app domain allowlist in our VPN (auth domains only), since sign-in is its only legitimate use. `LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK` (API 30, DPM) blocks even in-task Custom Tabs. **Emulator spike decides between the two designs.**

### 1.2 System WebView provider

`setApplicationHidden` or disabling `com.google.android.webview` makes the WebView Update Service fall back to another eligible provider. With none left, WebView-based apps crash (Chromium doc). `WebView.getCurrentWebViewPackage()` (API 26) notes that a provider change kills every process that loaded WebView (https://developer.android.com/reference/android/webkit/WebView). **Not a strategy.** WebView must stay, so open-web access inside allowed apps' WebViews is handled at the network layer or accepted.

### 1.3 Network-layer filtering

| Control | Facts | Source |
|---|---|---|
| `setAlwaysOnVpnPackage(admin, ourPkg, lockdownEnabled=true)`, API 24 | Granted automatically and persists across reboot. The VPN must target API 24+. | DPM, documented |
| `setAlwaysOnVpnPackage(..., lockdownAllowlist)`, API 29 | Allowlisted apps reach the network directly *only while the VPN is down*. Quote: "System apps can always bypass VPN." | DPM, documented |
| What lockdown exempts in code | `setVpnForcedLocked` blocks every UID from 1 up to the end of the user's range, except the VPN package itself and the lockdown allowlist. UID 0 (kernel IPsec) is excluded. | AOSP-VPN ~L2075–2100, code-confirmed |
| Bypass by other apps | Apps bypass only if the VPN calls `allowBypass()` (API 21). The default is "not possible for apps to side-step the VPN". | VPNB, documented |
| `addAllowedApplication` / `addDisallowedApplication`, API 21 | You can use one list or the other, not both. Apps not on an allowed list "use networking as if the VPN wasn't running". Under lockdown they are blocked, per the VPN guide caution. **Use neither list: route everything through the VPN.** | VPNB, VPNGUIDE, documented |
| `getConnectionOwnerUid`, API 29 | Returns the owning UID for TCP/UDP flows. Only the active VPN app may call it. Use it for per-app policy. | CM, documented |
| `DISALLOW_CONFIG_VPN`, API 21 | Stops the user from configuring a VPN. Since API 24 the system still starts the DO's always-on VPN. Since API 31, setting it clears VPNs the user configured. | UM, documented |
| `setGlobalPrivateDnsModeSpecifiedHost` / `...Opportunistic`, API 29 | Callable by the DO only. When used with a VPN, "the Private DNS resolver must be reachable both from within and outside the VPN". Set opportunistic mode and make our VPN's DNS refuse port 853, so we see plaintext DNS. | DPM, documented / inferred |
| `DISALLOW_CONFIG_PRIVATE_DNS`, API 29 | Locks the user out of private DNS settings. | UM, documented |

`setPreferentialNetworkService*` (work-profile/5G slicing) is not relevant.

**Can we separate API traffic from in-app browser traffic?** No, not by UID. A banking app's API calls and its "open website" WebView share a UID. The only difference is the destination domain.

**Filtering gaps (leads, verify in the spike):**
- Hard-coded DNS-over-HTTPS in apps. Mitigate by blocking known DoH IPs and SNIs.
- QUIC. Drop UDP/443 so apps fall back to TCP.
- Encrypted Client Hello hides SNI. Mitigate by stripping HTTPS/SVCB (type 65) DNS records in our resolver, so ECH can't bootstrap.

**Recommended policy.**
- Default: a per-app network on/off switch, plus a global category blocklist for all allowed apps (search engines, social, video, web proxies).
- Opt-in strict mode: a per-app domain allowlist for simple apps such as a bank. Unknown domains get logged and shown to the trusted person.
- Strict allowlists are brittle for maps and ride-share apps (CDNs, payment 3-DS domains change), so they are not the default.
- Some web browsing inside allowed apps is an **accepted residual risk**, and we tell the trusted person so plainly.

### 1.4 Share sheet, intents, deep links

- **Share sheet.** The system chooser only lists resolvable targets, so hidden apps drop out *(inferred)*. A non-allowlisted target launched in a new task is blocked by lock task (LTM).
- **Links in allowed apps.** "Open link" in any allowed app (messages, email, notifications, Maps "Website", Play `market://`, YouTube links inside Gmail) is `ACTION_VIEW`. It goes to the link catcher, or fails if the target app is hidden *(inferred)*.
- **Settings deep links.** `ACTION_APPLICATION_DETAILS_SETTINGS`, "Open by default", notification settings and `Settings.Panel` are reachable from allowed apps. If they start a new task, lock task blocks them because Settings is not allowlisted. If they start in-task, they get through unless `BLOCK_ACTIVITY_START_IN_TASK` is set, and that flag may break permission and settings flows. **Emulator test.**
- **App info "Open" on hidden apps.** Hidden apps are "unavailable for use" (DPM), so they should not show in App info *(inferred)*.
- **Crash dialogs.** These offer "App info". `DISALLOW_SYSTEM_ERROR_DIALOGS` (API 28, UM) suppresses them.

### 1.5 Other first-party surfaces

| Surface | Control | Status |
|---|---|---|
| Google app: search widget, Discover, Assistant, Lens, Circle to Search (`com.google.android.googlequicksearchbox`) | Our launcher has no Discover or search widget. Hide the Google app with `setApplicationHidden`, keep it out of lock task. `DISALLOW_ASSIST_CONTENT` (API 35) only stops context sharing, not invocation. | Emulator: does hiding it break anything? Does Circle to Search or Overview "Select/Lens" still launch? |
| Gemini (`com.google.android.apps.bard`) | Hide it. | inferred |
| Notification links | `LOCK_TASK_FEATURE_NOTIFICATIONS` (API 28): Quick Settings "remains disabled". LTM: "Tapping notification actions and buttons that open new panels, doesn't work in lock task mode." | documented |
| Settings Help & feedback (GMS help WebView) | Settings is not reachable under lock task. GMS can't be hidden. | residual if Settings is ever exposed |
| CaptivePortalLogin | A WebView mini-browser. `shouldOverrideUrlLoading` lets any http/https page load. It calls `bindProcessToNetwork(mNetwork)`, so it goes straight to Wi-Fi and is likely outside our VPN. It hides the "continue in browser" link when a VPN is on, and declares `REQUEST_INSTALL_PACKAGES` so portals can serve APKs (manifest L47). Mitigations: `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY` blocks the APK path. Browsing itself is residual. Optionally hide the package (`com.google.android.captiveportallogin` on GMS devices, a lead), at the cost of hotel/airport Wi-Fi. | AOSP-CPL L550, L1215, L1290, code-confirmed; VPN bypass under lockdown is an emulator test |
| Files / DocumentsUI previewing HTML | Hands off via `ACTION_VIEW` to the link catcher. Files by Google may render HTML itself (lead). Keep Files apps out of the allowlist. | Emulator |
| TalkBack / Accessibility tutorials | System accessibility services "are always available ... this method can't disable them" (DPM, `setPermittedAccessibilityServices`). Help links use `ACTION_VIEW`. | residual, low |
| Gboard GIF / web search | Gboard is a system IME, so `setPermittedInputMethods(empty list)` (API 21) can't remove it. Options: give Gboard's UID a narrow VPN policy, or allow a non-Gboard IME and hide Gboard. | Emulator |
| Camera on lock screen, Lens button | `setKeyguardDisabledFeatures` with `KEYGUARD_DISABLE_SECURE_CAMERA` (API 17) and `KEYGUARD_DISABLE_SHORTCUTS_ALL` (API 34). | documented |
| Play Store | See sibling file. Keep it enabled for updates, out of lock task, and launchable by no one. | |

## Part 2: Escape threat list

| ID | Vector | What the user tries | Mitigation (API level, source) | Residual risk | Emulator? |
|---|---|---|---|---|---|
| E01 | Safe mode | Boot to safe mode. The DPC launcher and VPN are third-party, so they don't run and the stock launcher plus Chrome come back. | `DISALLOW_SAFE_BOOT` (23, UM; DO applies globally) | Low | y |
| E02 | Factory reset from Settings | Wipe to get a normal phone. | `DISALLOW_FACTORY_RESET` (21, UM: "from Settings"). Settings is also outside lock task. | Low | n |
| E03 | Factory reset from recovery | Hardware keys, then recovery wipe. No API blocks this. | `setFactoryResetProtectionPolicy` (30, DPM) with the trusted person's Google account ID. FRP applies to "untrusted factory reset" (FRPP). Needs GMS. | **Medium**: a reset wipes Plainphone, but FRP stops the user setting the phone up without the trusted account. | y |
| E04 | OEM unlock / bootloader | Turn on OEM unlocking, unlock, flash. | `DISALLOW_FACTORY_RESET` → OemLockService calls `setOemUnlockAllowedByDevice(false)` and rejects user changes with "Admin does not allow OEM unlock" (AOSP-OEM L101–108, L179). Also DPCSEC: "Factory reset protection is disabled if OEM unlocking is enabled". | Low (code-confirmed) | y (confirm the toggle is greyed out) |
| E05 | Developer options / adb | Enable USB or wireless debugging; `adb install`, `adb shell pm`, `dpm remove`. | `DISALLOW_DEBUGGING_FEATURES` (21, UM: DO "disables debugging features altogether, including USB debugging") | Low | y |
| E06 | Add user / guest | Make a guest or secondary user with no policy. | `DISALLOW_ADD_USER` (21), `DISALLOW_USER_SWITCH` (28, DO only, applies to all users) | Low | y |
| E07 | Work profile | Add a managed profile running a browser. | `DISALLOW_ADD_MANAGED_PROFILE` (26) defaults true with a DO. Deprecated in API 30 because DO+managed profile was removed. | Low | n |
| E08 | Private space / clone apps | Android 15 private space; Samsung Dual Messenger; Xiaomi Dual Apps. | `DISALLOW_ADD_PRIVATE_PROFILE` (35) is "true and the device owner currently cannot change it to false". No public clone-profile restriction found in UM. | **Unknown on OEM skins** | y (Samsung/Xiaomi hardware) |
| E09 | Accessibility services | An automation service drives hidden UI. | `setPermittedAccessibilityServices(empty)` (21) allows only system services | Low | n |
| E10 | Input methods | A third-party keyboard with search or browser features. | `setPermittedInputMethods(empty or our list)` (21). System IMEs (Gboard) stay. | Low–Med (Gboard features) | y |
| E11 | Date/time manipulation | Change the clock to beat time limits or PIN lockouts. | `DISALLOW_CONFIG_DATE_TIME` (28), `setAutoTimeEnabled` / `setAutoTimeZoneEnabled` (30). Use `SystemClock.elapsedRealtime` for lockout timers. | Low | n |
| E12 | Settings app | Reach Settings and undo things. | Keep Settings out of `setLockTaskPackages` (21). Lock task hides Home/Overview and "Other apps can't launch new activities" (LTM). Build our own minimal settings screens (Wi-Fi, brightness, volume). | Medium: in-task deep links (§1.4) | y |
| E13 | Notification shade / Quick Settings | QS tiles open Settings, hotspot, cast. | `setLockTaskFeatures`: omit `LOCK_TASK_FEATURE_NOTIFICATIONS`, or keep it since QS stays disabled; omit `SYSTEM_INFO` if needed (28). `setStatusBarDisabled` (23) does nothing in lock task. | Low | y |
| E14 | Google Assistant / Gemini / Circle to Search | Voice "search for X", Lens. | Hide the packages (§1.5); lock task | Medium until tested | y |
| E15 | Split screen / PiP / Overview menu | Recents "App info" or "Select/Lens" actions. | Omit `LOCK_TASK_FEATURE_OVERVIEW` (28); our launcher does app switching | Low | y |
| E16 | Install via adb | `adb install`. | E05. DPCSEC notes adb installs still work under unknown-sources restrictions alone. | Low | y |
| E17 | Sideload via Files, Bluetooth, Quick Share, captive portal | Open an APK. | `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY` (29). Note it "does not apply to ... registered app stores". (Errata 2026-10-08: `DISALLOW_INSTALL_UNKNOWN_SOURCES_INCLUDING_REGISTERED_APP_STORES` is listed in the current developer.android.com UserManager reference as "Added in version 37.2" (Verifier, PLA-3 F16), but was absent from AOSP `main` UserManager.java as fetched by the Lead. Source/build mismatch: do not assume it on API 35/36; test on API 37 (Q2).) | Low (Play itself: sibling file) | y |
| E18 | Account recovery flows | "Forgot password" opens a browser or Custom Tab. | Link catcher; or the restricted Custom Tab design (§1.1). `DISALLOW_MODIFY_ACCOUNTS` (18). | Medium: recovery may need the trusted person's device | y |
| E19 | Uninstall or disable the DPC | Settings or launcher uninstall. | DO "cannot be deactivated by the user ... It also cannot be uninstalled" (DPM `isDeviceOwnerApp`). `setUninstallBlocked` (21) on the launcher and VPN if they are separate packages. `DISALLOW_APPS_CONTROL` (21). | Low | n |
| E20 | Remove device admin | Settings → Device admin apps. | Same quote as E19; Settings is unreachable anyway | Low | n |
| E21 | Change default launcher | Pick the stock launcher. | `addPersistentPreferredActivity` for HOME (LTM requires it), `DISALLOW_CONFIG_DEFAULT_APPS` (34) | Low | y |
| E22 | SIM swap / eSIM | New carrier data; SMS-based account takeover. | Policies are per device, so the VPN still applies. `DISALLOW_SIM_GLOBALLY` (35) blocks eSIM download. `DISALLOW_CONFIG_MOBILE_NETWORKS` (21). Physical SIM can't be blocked. | Low | n |
| E23 | USB file transfer | Copy APKs or HTML on via MTP. | `DISALLOW_USB_FILE_TRANSFER` (18); `setUsbDataSignalingEnabled(false)` (31, device-dependent) | Low | y |
| E24 | Bluetooth / NFC sharing | Send or receive files. | `DISALLOW_BLUETOOTH_SHARING` (26, outgoing); `DISALLOW_OUTGOING_BEAM` (22). Incoming APKs blocked by E17. | Low | n |
| E25 | VPN tampering | Network reset, user VPN, private DNS. | `DISALLOW_NETWORK_RESET` (23), `DISALLOW_CONFIG_VPN` (21), `DISALLOW_CONFIG_PRIVATE_DNS` (29), lockdown | Low | y |
| E26 | Overlay apps | Floating windows above lock task. | `DISALLOW_CREATE_WINDOWS` (21; LTM recommends it) | Low | n |
| E27 | Captive portal browser | Join open Wi-Fi to reach a WebView. | §1.5; consider hiding the package | **Medium** | y |
| E28 | Trusted-person PIN brute force | Guess the PIN. | Our code: salted slow hash (PBKDF2/Argon2), exponential backoff on monotonic time, persisted counter, notify the trusted person after N failures. Do **not** use `setMaximumFailedPasswordsForWipe` (API 8): it factory-resets the phone. | Low if built well | n (unit tests) |
| E29 | Crash dialog → App info | Force a crash. | `DISALLOW_SYSTEM_ERROR_DIALOGS` (28) | Low | y |

## Part 3: Recommended layered strategy

**1. Block hard. All of these are DO APIs, applied at provisioning.**
- Lock task mode with only our launcher and approved apps. Features: HOME, plus GLOBAL_ACTIONS (power off) and KEYGUARD; NOTIFICATIONS if wanted. No OVERVIEW.
- Hide every browser, the Google app, Gemini, and any non-approved app store. Our link catcher becomes the only http(s) handler.
- Every user restriction and keyguard flag in the Part 2 table, IMEs and accessibility services set to system-only, and an FRP policy with the trusted person's account.
- An always-on lockdown VPN with an empty lockdown allowlist, doing per-app DNS/SNI policy (category blocklist by default, strict allowlist opt-in), with QUIC dropped and ECH records stripped.

**2. Accept and tell the trusted person openly.**
- An allowed app with a built-in WebView can usually reach *some* open web through links inside it. We reduce this with domain policy but can't separate it from the app's real traffic.
- A recovery-mode factory reset can't be blocked. It wipes Plainphone, but FRP stops the user setting the phone up without the trusted person's account.
- Captive-portal Wi-Fi login pages are a small browser, possibly outside our VPN.
- Gboard and TalkBack features can't be removed.
- Some sign-in, "forgot password" and 3-D Secure flows may break and need the trusted person's help.
- Behaviour on OEM skins (Samsung, Xiaomi clone apps; OEM browsers) varies.

**3. Emulator or device spike must answer.**
- (a) With all browsers hidden, does the link catcher get `ROLE_BROWSER` automatically (https://developer.android.com/reference/android/app/role/RoleManager), and what do Custom Tabs and OAuth do in real banking or ride-share apps?
- (b) Do in-task Custom Tabs and Settings deep links get through lock task, with and without `BLOCK_ACTIVITY_START_IN_TASK`?
- (c) Does CaptivePortalLogin bypass a lockdown VPN?
- (d) Confirm with `getConnectionOwnerUid` that WebView traffic carries the app's UID.
- (e) Hiding the Google app: what breaks, and do Circle to Search or Lens still appear?
- (f) Does the hidden state survive OTA and Play updates?
- (g) Is the OEM-unlock toggle greyed out, is safe mode blocked, is the E03 FRP flow enforced?
- (h) Gboard search features under VPN policy.
- (i) Samsung and Xiaomi clone apps on real hardware.
