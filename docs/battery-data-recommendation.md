# Battery and data: Lead's recommendation (PLA-123)

Status: **revision 1, desk assessment; measurements pending.** Board request from Beaux (Telegram 4537, 2026-10-09):
can Own Phone cut trackers and background processes, so the battery lasts longer and less data is used?
This comes **after the MVP core** and must not hold up the test build. Measurements on the spare wait for the MVP test
build ([PLA-15](/PLA/issues/PLA-15)) and run in the spike in [PLA-125](/PLA/issues/PLA-125) (question Q15).

Inputs:
- the MVP design, `openspec/changes/add-plainphone-spike-and-mvp/design.md` (D1 hiding, D3 updates, D5 never-hide list);
- the spare's evidence, `spikes/device-moto/RESULT.md`: A5 (21 packages hide cleanly), S3 (Play can't be suspended),
  S4 (hidden Play runs no jobs), Q2 (72 h update run, cut short, moved to the MVP build);
- Android sources listed under [Sources](#sources).

## Short answer

1. **Most of the win is already in the MVP, at no extra cost.** At setup the MVP hides every package outside the
   emergency floor, the reviewed build inventory and the allowlist (design D8 step 6, approved-apps spec). That
   includes background-only packages with no icon. Android treats a hidden app as not installed: hiding force-stops
   it and removes its alarms, and nothing can start it again (see [What hiding does](#what-hiding-does)). On the spare,
   the hidden set already includes the usual phone-home and bloat packages: Glance lock screen, the three Facebook
   system packages, Digital Turbine (`com.dti.motorola`), Chrome and 14 Motorola extras (A5). This claim is sourced but **not yet measured**. Q15 measures it.
2. **Don't add more Device Owner controls yet.** The remaining levers are weak or risky (table below). The apps that
   still run are the ones the two initial users chose, plus the never-hide list. Throttling those would make them less
   useful, and the never-hide list keeps calls, texts and 9-1-1 working. Revisit only if Q15 shows an approved app,
   or a never-hide package, using a lot of battery or data while the phone is idle.
3. **Measure before promising.** Q15 runs two 24-hour idle windows on the spare: the stock phone, then the same phone
   under the MVP policy. It reports per-app background work (CPU time, wakeups, jobs, data) and an estimated drain.
   The battery and data budget below becomes a pass/fail check in the MVP acceptance run.
4. **What we tell Beaux now:** "Apps you didn't approve are switched off completely, so they can't run in the
   background or use data. We'll measure how much battery and data that saves on the spare once the test build is
   on it." No percentage until Q15 has a number.

## What hiding does

Sources: Android 15 AOSP (`android15-release`); full list under [Sources](#sources).

| What | After `setApplicationHidden` | Source |
|---|---|---|
| Running processes | Force-stopped at once (processes, services, providers) | PMS `setApplicationHiddenSettingAsUser` → AMS `killApplication` |
| Broadcast | A real `PACKAGE_REMOVED` goes out (data kept) | `BroadcastHelper.sendApplicationHiddenForUser` |
| Alarms | Removed | `AlarmManagerService.UninstallReceiver` |
| Scheduled jobs | Kept in storage but can't run once the service stops resolving. The resolver cache refreshes on unlock, so a stale job may try once before that (inference, **Q15 checks it**) | `JobSchedulerService.isComponentUsable`, `ComponentController` |
| Receivers, sync, push (FCM) | Package lookups skip hidden apps, so manifest receivers don't resolve. Sync and FCM: **unverified** (Play services is closed source); Q15 measures it | `PackageUserStateUtils.isAvailable` |
| Doze and standby | Irrelevant: a hidden app has no process and can't start | — |

So "hidden apps run no background work" is supported by the source, with one gap (jobs before the cache refresh,
and FCM) that Q15 measures. Own Phone itself is a device admin, so Android keeps it in the exempt standby bucket
(`AppStandbyController.getAppMinBucket`). That is why its 15-minute check keeps running; it is small and stays.

## Other Device Owner levers

Android gives a Device Owner few extra levers. None is worth adding before Q15 shows a problem.

| Lever | What it does | Verdict |
|---|---|---|
| Hide (`setApplicationHidden`) | Already used for every unapproved package | **Keep. This is the win.** |
| Suspend (`setPackagesSuspended`) | Blocks the app's screens and notifications, but its jobs, alarms and network keep running. Android refuses it for the dialer, launcher, Play (S3) and device admins | **No.** Weaker than hiding |
| Disable a system app | No Device Owner API; only `enableSystemApp` exists | Not available |
| Block mobile data per app (`setMeteredDataDisabledPackages`, API 28) | Blocks the app on mobile data **even in the foreground**; Wi-Fi only if marked metered. Skips system UIDs and hidden apps | **No for approved apps** (it would break them on mobile). Possible later for an approved app the trusted person wants Wi-Fi-only, as a visible setting |
| Background data per app, Data Saver, standby bucket, "Restricted" battery mode | Need system permissions (`MANAGE_NETWORK_POLICY`, `CHANGE_APP_IDLE_STATE`); adb only | **Not available** to Own Phone. Doing it by adb at setup would not survive and isn't supportable |
| Tracker-blocking private DNS (`setGlobalPrivateDnsModeSpecifiedHost`, API 29) | Forces a DNS-over-TLS host for the whole phone. If the host is down, Android marks the network "No internet access" | **Not now.** It is the existing optional Q9 (DNS filter). One outside service could cut off every app's data, and its effect on Wi-Fi calling and captive portals is unverified. Revisit only if Q15 shows approved apps sending lots of tracker traffic |
| Always-on VPN filter | System apps can bypass a VPN (javadoc), so it doesn't reach preinstalled bloat | **No** for this purpose (Q9 again) |
| Lock task | Changes nothing for Doze or standby; the phone dozes normally when the screen is off | Neutral |

Useful for later: as Device Owner, Own Phone can read per-app data use for every app with no extra permission
(`NetworkStatsManager`, `NetworkStatsAccess.checkAccessLevel` returns `DEVICE`). A future "data used this month"
line for the trusted person is cheap. Per-app battery stats are not readable without `BATTERY_STATS` (unverified).

## What must not be touched

These stay exactly as the MVP already has them. No battery or data change may hide, suspend, disable, uninstall,
restrict the data of, or block the DNS of any of these:

- **Calls, texts, 9-1-1** (design D5): the system dialer, Telecom (`com.android.phone`, `com.android.server.telecom`),
  the cell broadcast receiver and its alert display, the default SMS app, and Motorola's emergency components,
  including Personal Safety (`com.google.android.apps.safetyhub`, PLA-40).
- **Google Play services.** It carries push messages for Messages, Telegram and others, and Play's updates. It is on
  the never-hide list.
- **Play Store.** Hidden Play runs no jobs, so nothing updates (S4); Android refuses to suspend it (S3).
- **Carrier and IMS packages** (VoLTE, Wi-Fi calling, RCS), and the system update client (Motorola's OTA updater).
  Because the MVP hides everything outside the floor, inventory and allowlist, these must be in the spare's reviewed
  build inventory, or they get hidden too. The inventory review (Android Platform Engineer prepares, Verifier
  reviews) already owns this. Q15 also checks it on the phone and reports any miss as blocking.
- **The custody contract** (design D2). Any new protection must be checked by the protected-state check at the end
  of every supervised session. A session must never be able to remove it quietly.
- **Own Phone's own checks.** It hides new installs at once and re-checks every 15 minutes (D3). That costs a little
  battery and stays.

## Battery and data budget for the MVP acceptance run

The spare has no SIM (`gsm.sim.state=ABSENT`), so it can't measure mobile data. It measures Wi-Fi data per app;
the same apps use the same data on mobile. During the 72-hour update run, the phone is on USB power. So battery is
measured with Android's own idle test mode (`dumpsys battery unplug`), which makes the phone behave as if unplugged,
including Doze. This gives per-app CPU time, wakeups, jobs and data, and an estimated drain from the phone's power
profile. A real unplugged day is Beaux's own check on his phone, not a gate.

Proposed budget. The hard checks apply now; the numeric ones are set from Q15's window A and B results.

| Check | Budget | How it is verified |
|---|---|---|
| Hidden packages | **Zero** CPU time, wakeups, jobs run and bytes over 24 h idle | Per-UID batterystats, jobscheduler history, netstats |
| Own Phone itself | **Zero bytes** (the app has no `INTERNET` permission on `main`); at most about 5 wakeups an hour (the 15-minute check plus events) | Same, for Own Phone's UID |
| Whole phone, idle 24 h, screen off | Estimated drain **no higher than stock** (window A), target to be set from Q15 | batterystats estimated mAh ÷ 5000 mAh |
| Wi-Fi data, idle 24 h, excluding Play and Play services updates | **No higher than stock**, target to be set from Q15 | `dumpsys netstats detail`, per UID |
| Doze | The phone enters deep Doze within about an hour of screen-off | `dumpsys deviceidle` history |

**In the 72-hour unattended update run** (task 6.3): add a netstats and batterystats readback at the start, every
24 h and at the end, using the same commands. It is read-only, so it doesn't count as touching the phone. Update
traffic is expected there and is reported separately, not counted against the budget.

## Follow-up tasks

1. **[PLA-125](/PLA/issues/PLA-125), spike Q15** (Android Platform Engineer, after [PLA-15](/PLA/issues/PLA-15),
   low priority): the A/B measurement, the negative control, and the never-hide check. Then cross-model review by
   the Verifier.
2. **After Q15, if the budget numbers hold:** the Lead adds the budget checks to the 72-hour run (task 6.3) and to
   `docs/verification-matrix.md`. No product code change is expected.
3. **Only if Q15 shows a problem:** a named approved or never-hide package that is expensive goes to Beaux with
   options (for example Wi-Fi-only data for that app, or the Q9 DNS filter). Nothing is added without his choice.

## Sources

Gathered by a Lead research pass on 2026-10-09; AOSP at `android15-release`
(`https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/`, and
`.../platform/packages/modules/Connectivity/+/refs/heads/android15-release/` for network stats).

- Hiding: `services/core/java/com/android/server/pm/PackageManagerService.java` (`setApplicationHiddenSettingAsUser`),
  `.../pm/BroadcastHelper.java` (`sendApplicationHiddenForUser`), `.../pm/pkg/PackageUserStateUtils.java` (`isAvailable`),
  `apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java` (`UninstallReceiver`),
  `.../job/JobSchedulerService.java` (`isComponentUsable`), `.../job/controllers/ComponentController.java`.
- Suspending: [setPackagesSuspended](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setPackagesSuspended(android.content.ComponentName,%20java.lang.String[],%20boolean)),
  `.../pm/SuspendPackageHelper.java` (`canSuspendPackageForUser`); spare result S3.
- Metered data: [setMeteredDataDisabledPackages](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setMeteredDataDisabledPackages(android.content.ComponentName,%20java.util.List%3Cjava.lang.String%3E)),
  `NetworkPolicyManagerService.java` (`updateRulesForDataUsageRestrictionsULInner`, `setRestrictBackground`).
- Standby: `apex/jobscheduler/service/java/com/android/server/usage/AppStandbyController.java` (`getAppMinBucket`),
  `core/java/android/app/usage/UsageStatsManager.java` (`setAppStandbyBucket`).
- Doze: [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).
- Private DNS and VPN: [setGlobalPrivateDnsModeSpecifiedHost](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setGlobalPrivateDnsModeSpecifiedHost(android.content.ComponentName,%20java.lang.String)),
  [DNS over TLS in Android P](https://android-developers.googleblog.com/2018/04/dns-over-tls-support-in-android-p.html),
  [setAlwaysOnVpnPackage](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setAlwaysOnVpnPackage(android.content.ComponentName,%20java.lang.String,%20boolean,%20java.util.Set%3Cjava.lang.String%3E)).
- Measuring: [Battery Historian setup](https://developer.android.com/topic/performance/power/setup-battery-historian),
  `framework-t/src/android/app/usage/NetworkStatsManager.java`, `framework-t/src/android/net/NetworkStatsAccess.java`.
- Spare evidence: `spikes/device-moto/RESULT.md` (A5, S3, S4, Q2), `spikes/device-moto/evidence/a5/hide-results.txt`.
