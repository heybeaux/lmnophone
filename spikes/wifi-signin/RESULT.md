# PLA-18: Wi-Fi sign-in session probe (THROWAWAY SPIKE, emulator only)

Everything under `spikes/wifi-signin/` is throwaway. It answers feasibility questions for the Lead's Wi-Fi sign-in override recommendation ([PLA-17]). It is not MVP code.

- **Emulator:** `pp11-a35` (emulator-5620). API 35, Google Play image, `google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.036/12592187:user/release-keys`. The PLA-11 spike DPC was already Device Owner here.
- **Not tested:** API 36 and API 37 images (not enough time in this run), and any physical device. SPARE_DEVICE_SERIAL, emulator-5590 and emulator-5604 were not touched.
- **Spike DPC:** `apps/dpc` is the PLA-11 DPC plus `Signin.kt`, a sign-in session that uses the Play/Settings session contract. In order:
  1. Write the journal with `commit()`.
  2. Add `com.google.android.captiveportallogin` to the lock-task packages, then unhide it.
  3. End the session on Done, screen off, the deadline, leaving to Home, or `NET_CAPABILITY_VALIDATED` on the Wi-Fi network.
  4. On every start (`Application.onCreate`, `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`), revoke anything left in the journal.
- **Baseline hiding:** Chrome, the Google app, Settings and the sign-in app were hidden. Lock-task features were `SYSTEM_INFO|NOTIFICATIONS|HOME|GLOBAL_ACTIONS|KEYGUARD|BLOCK`.

## Summary

| Q | Answer |
|---|---|
| 1 Fixture | **Works on API 35 Play.** A host fake portal plus 4 `settings put global captive_portal_*` lines and a Wi-Fi reconnect. |
| 2 Package | `com.google.android.captiveportallogin`, `/system/app/CaptivePortalLoginGoogle`, cert SHA-256 `5fa42212…bf62c1e`. Nothing else had to be unhidden: it uses the WebView, which is already visible. |
| 3 Launch | **The notification works** once the app is unhidden and in the lock-task allowlist: it opens under lock task. **A Device Owner cannot open the app with the system's sign-in session itself.** The system API is hidden. Once dismissed, the notification only comes back after a Wi-Fi reconnect. |
| 4 Reach | **It reaches the open web.** Outside links, JS redirects, `target=_blank`, `window.open` and Google Search all load inside the sign-in app's own browser view. `intent:`, `mailto:` and `market:` links show an interstitial whose button also ends the sign-in check. Downloads try to open Files, which lock task blocks. `tel:` opens the Dialer. |
| 5 Close | **`VALIDATED` arrives about 1 s after the portal accepts, and Plainphone closed the session 73 ms later.** But `VALIDATED` is **not** proof of sign-in: "Use this network as is" and the interstitial's "Continue anyway" also set it. |
| 6 Revocation | **Process kill: the app stays open and reachable until Plainphone restarts.** On restart it is re-hidden about 100 ms after the process starts. `am force-stop` is refused for a Device Owner. **Reboot mid-session: system_server boot-looped** (56+ crashes, never booted). See Q6b. **This is a blocker until we know when it happens.** |
| 7 Detection | **Yes.** `NET_CAPABILITY_CAPTIVE_PORTAL` from a Wi-Fi `NetworkCallback` worked with mobile data off and on. |

## Q1 Fixture: works (API 35 Play image)

On the host, run `python3 -I spikes/wifi-signin/tools/portal.py 8099 <modefile>`. The mode file holds `captive` or `open`:

- In `captive` mode, every probe gets a `302` to `/login`.
- In `open` mode, probes get a `204`.
- `/accept` switches the mode file to `open`.
- `/login` is the reach-test page.

On the emulator, run `bash tools/fixture.sh emulator-5620 captive`, which does:

```
settings put global captive_portal_http_url  http://10.0.2.2:8099/generate_204
settings put global captive_portal_https_url http://10.0.2.2:8099/generate_204
settings put global captive_portal_fallback_url http://10.0.2.2:8099/generate_204
settings put global captive_portal_other_fallback_urls http://10.0.2.2:8099/generate_204
svc data disable
svc wifi disable; sleep 2; svc wifi enable
```

Result (`evidence/q1-q7-captive-dataoff.txt`):

```
NetworkMonitor/102: isCaptivePortal: isSuccessful()=false isPortal()=true RedirectUrl=http://10.0.2.2:8099/login
ConnectivityService: [102 WIFI] validation detected a portal
```

The Wi-Fi capabilities include `CAPTIVE_PORTAL`. Undo with `fixture.sh <serial> reset`.

**Caveat:** this fixture is not a walled garden. The emulator still has real internet behind the "portal", so the Q4 reach results are the worst case. That is also what a user gets once a real portal lets them through.

## Q2 Package and certificate

From `pm list packages -f -u` and `apksigner verify --print-certs` on the pulled APK:

- **Package:** `com.google.android.captiveportallogin`
- **Path:** `/system/app/CaptivePortalLoginGoogle/CaptivePortalLoginGoogle.apk`
- **Version:** versionCode 350820300 (mainline), targetSdk 35
- **Signer:** `CN=Android, OU=Android, O=Google Inc.`
  - SHA-256 `5fa42212ad403e22dd6efe75f3f31184051fef744c0b05be5c73edf60bf62c1e`
  - SHA-1 `c6ae382ffb7836e34b4f499d11a41fe0fe003cb8`

From the manifest (`aapt2 dump xmltree`):

- `CaptivePortalLoginActivity` is exported, with **no permission** on action `android.net.conn.CAPTIVE_PORTAL`.
- It **queries `CustomTabsService`**, so with a Custom Tabs browser visible it may hand the portal to that browser. Chrome was hidden here, so it used its own WebView. This path was not tested.
- It requests `REQUEST_INSTALL_PACKAGES`, and has a `DownloadService` and a `FileProvider`.

Unhiding only this package was enough. It used `com.google.android.webview`, which Plainphone does not hide.

## Q3 Launch path

`evidence/q3-*.txt`, `q3-b-portal.png`.

- **a) No session (app hidden):** tapping the system "Sign in to Wi-Fi network" notification does nothing, and the tap dismisses the notification.
  - PLA-11 recorded "inconclusive" for this test. That was a tooling bug: `tapt` ran under zsh and never tapped.
- **b) Session active (unhidden, in the lock-task allowlist):** tapping the notification opens `CaptivePortalLoginActivity` in its own task. `mLockTaskModeState` stays `LOCKED`, and the portal page loads.
  - System log: `START u0 {act=android.net.conn.CAPTIVE_PORTAL ... cmp=com.google.android.captiveportallogin/...CaptivePortalLoginActivity} ... from uid 1000`.
- **c) Can the Device Owner open it itself?** Not with the system's sign-in session.
  - `ConnectivityManager.startCaptivePortalApp(Network)` is not in the public SDK (`javap` on android-36 `android.jar`). It is a hidden system API.
  - `CaptivePortal` has no public constructor, so an app cannot supply `EXTRA_CAPTIVE_PORTAL` for `ACTION_CAPTIVE_PORTAL_SIGN_IN`.
  - Tested: `cmd signin-open reflect|intent` exists in the DPC, but the run timed out before I recorded those two results. **Untested.**
- **Bringing the notification back:** `reportNetworkConnectivity(wifi, false)` and `(wifi, true)` both re-probed, but neither reposted a dismissed notification. Only a Wi-Fi reconnect (`svc wifi disable/enable`) reposted it.
  - **Design consequence:** the "Use sign-in page" button needs a reliable way to open the page. Options:
    - (i) reconnect Wi-Fi from the DPC (untested), or
    - (ii) Plainphone's own WebView bound to the Wi-Fi network (untested). Option (ii) would also give Plainphone control over navigation.

## Q4 Reach (session active, inside the sign-in app)

`evidence/q4-reach.txt`, `q4-*.png`, `portal-requests.log`.

| Probe | Result |
|---|---|
| Link to example.com | **Loads** inside the sign-in app (open web) |
| JS redirect to Wikipedia | **Loads**; Wikipedia search works |
| `target=_blank` (bbc.com) | **Loads in the same view** |
| `window.open` (mozilla.org) | **Loads in the same view** |
| Google Search link | **Loads**, and search is usable |
| File picker `<input type=file>` | Nothing opens; stays on the portal |
| `intent:` link to Chrome, `market:`, `mailto:`, `intent:` Settings | Interstitial "The network you are trying to join is requesting to open another application" with **CONTINUE ANYWAY VIA BROWSER**. Tapping it: result `WANTED_AS_IS`, so the network becomes `VALIDATED` (see Q5) and the app closes. No other app opened; Chrome was hidden. |
| `googlechrome://` | Nothing happens; stays on the portal |
| `tel:` | Opens the **Dialer** keypad. Dialer is already allowed. |
| Download (PDF, attachment) | Starts `CREATE_DOCUMENT` → **DocumentsUI blocked by lock task** ("Files is not available right now") |
| Overflow menu | Only "Do not use this network" and "Use this network as is". No "open in browser". |
| Recents key | Blocked by lock task |

The sign-in app is a general-purpose browser for as long as the session lasts. It probes `generate_204` after every page load, which is how it notices sign-in and closes. On a real portal, the open web is reachable either through the portal's walled-garden list or once the portal lets the device through. After that, the app closes itself within about a second (Q5). The exposure is the session time limit. **Navigation cannot be scoped from outside the app.**

## Q5 Close on success

`evidence/q5-accept-validated-close.txt`.

After tapping Accept on the portal:

| Time | Event |
|---|---|
| 17:49:19.996 | Accept tapped; portal page starts loading |
| 17:49:21.016 | `ConnectivityService: Update capabilities for net 108 : -CAPTIVE_PORTAL+VALIDATED` |
| 17:49:21.019 | Plainphone's Wi-Fi callback sees `validated=true`; the sign-in app reports `Result DISMISSED` |
| 17:49:21.092 | `signin end reason=validated hide=true`, home brought to front |

Lock task stayed `LOCKED`. Hiding the package force-stops it (`ActivityManager: Force stopping com.google.android.captiveportallogin ... hiding pkg`).

**Caveat:** `VALIDATED` also appears when the user picks **"Use this network as is"**, or **"Continue anyway via browser"** on the intent interstitial. Log: `Result WANTED_AS_IS` → `NetworkMonitor: Captive portal is used as is` → `-CAPTIVE_PORTAL+VALIDATED` (`evidence/q4-q5-continue-anyway-validates.txt`). Closing on it is safe, because the session ends sooner. But it doesn't prove sign-in, and the Wi-Fi may still not reach the internet.

The other close paths seen:

- **Deadline:** in-process `Handler`. It fired, hid the app and returned to home.
- **Leaving to Home:** `left-to-home`.
- **Spike bug:** the first deadline timer closed a *later* session, because it compared against its own captured deadline. The real implementation must check a session id in the journal.

Screen-off close was coded (`ACTION_SCREEN_OFF` receiver) but not run.

## Q6 Revocation

### a) Plainphone process dies

`evidence/q6a-forcestop.txt`, `q6a-kill.txt`.

- `am force-stop com.plainphone.spike.emu` → `ActivityManager: Ignoring request to force stop protected package`. A Device Owner cannot be force-stopped.
- `run-as ... kill -9 <pid>`, which simulates a crash:
  - The sign-in app stayed on top, unhidden, with lock task still `LOCKED`, for 25 s+ until Home was pressed.
  - Plainphone did not restart by itself, and its in-process deadline timer is gone.
  - **While Plainphone is dead, the sign-in app (and so the open web, per Q4) stays reachable.** A real build needs an `AlarmManager` deadline, which survives a process kill (not tested).
- Pressing Home starts Plainphone (persistent HOME):
  - `Start proc` at 17:51:45.964
  - sign-in app force-stopped and hidden by 17:51:46.032
  - `signin end reason=revoke:app-start` at 17:51:46.067

  The revoke ran in `Application.onCreate`, before the home activity.

### b) Reboot mid-session: system_server boot loop

`evidence/q6b-bootloop.txt`.

What I did: started a session, which unhid the sign-in app and opened it, then ran `adb reboot` about 5 s later. The emulator **never finished booting**. system_server crashed 56+ times in 5 minutes. Every crash had the same cause:

```
Failed to boot service DevicePolicyManagerService$Lifecycle: onBootPhase threw an exception during phase 500
Caused by: IllegalStateException: Cannot broadcast before boot completed
  at BroadcastHelper.sendPackageAddedForUser
  at PackageManagerService$IPackageManagerImpl.setApplicationHiddenSettingAsUser
  at PolicyEnforcerCallbacks.setApplicationHidden
  at DevicePolicyEngine.reapplyAllPoliciesOnBootLocked
  at DevicePolicyManagerService.systemReady
```

What the trace shows:

- On Android 15, the device-policy engine re-applies stored `setApplicationHidden` policies at boot.
- Re-applying "not hidden" made PackageManager send a package-added broadcast before boot completed, which throws inside system_server.
- Plainphone's own boot revoke never got a chance to run.

**Unproven hypothesis:** the trigger needs PackageManager's saved hidden state to differ from the policy engine's. PackageManager delays its settings write, so a reboot seconds after unhiding may be the trigger. A reboot later in a session might be fine.

This was **observed once and not yet reproduced**. If it reproduces, it affects **every** supervised session that unhides an app (Play, Settings, Wi-Fi sign-in), not just this one. A boot loop on a non-technical user's phone is very serious.

The emulator was left boot-looping and then shut down (`emu kill`). pp11-a35 needs a wipe before reuse.

## Q7 Detection

`evidence/q1-q7-captive-dataoff.txt`, `q7-captive-dataon.txt`.

- A `registerNetworkCallback` with `TRANSPORT_WIFI` reports `captive=true validated=false` on the sign-in network, with mobile data off and on.
- The default-network callback also showed it. With data on, Wi-Fi stayed the default network (`Active default network: 102`), possibly because emulator cellular hadn't settled.
- No extra permission was needed beyond `ACCESS_NETWORK_STATE`.
- This also gives the network handle the session needs for Q5.

## Not done in this run (timebox)

- `signin-open reflect|intent` results (Q3c).
- Screen-off close.
- `AlarmManager` deadline.
- Wi-Fi reconnect from the DPC.
- Plainphone-owned WebView alternative.
- API 36/37 repeat.
- **Reproducing the Q6b boot loop**, including timing and a reboot after the 10 s write window. Also: does it happen with the Play/Settings sessions?

## Files

- `apps/dpc`: spike DPC (`Signin.kt`, `Commands.kt` adds `signin-start|end|state|open`)
- `tools/portal.py`: fake portal
- `tools/fixture.sh`: probe URLs, data off, Wi-Fi reconnect
- `tools/session.sh`: fresh session, then tap the notification
- `tools/q3.sh`, `tools/reach.sh`, `tools/tapcase.sh`: test drivers. Run under **bash**.
- `tools/pp.sh`: PLA-11 helpers
- `evidence/`: logs and screenshots named above

[PLA-17]: /PLA/issues/PLA-17
