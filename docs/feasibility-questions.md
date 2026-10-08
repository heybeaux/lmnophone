# Feasibility questions for the emulator spike

- Date: 2026-10-08
- Owner: Plainphone Lead
- Spike author: Android Platform Engineer
- Spike reviewer: Verifier & Escape Artist

The questions are ranked by how much a "no" would change the plan. Q1 and Q2 decide whether ADR 0001 holds.

Spike rules:
- Emulator only. Use a Google Play system image at API 35 and one at API 36 or the newest available.
- All code goes under `spikes/` and is labelled throwaway.
- Each question gets a short write-up in `spikes/<id>/RESULT.md`: yes / no / partly, the exact steps, logcat excerpts, and screenshots.
- A question that needs real hardware is marked **[device]**. The emulator part is answered now; the device part waits for Beaux to approve a specific phone.

## Make-or-break

**Q1. Can multi-app lock task mode be a normal phone all day?**
- Set Plainphone as Device Owner and home. Put Phone, Messages, Camera and a sample third-party app in lock task.
- Check that each of these works and that Settings never appears:
  - an incoming call
  - an outgoing call
  - notifications for allowed apps
  - a runtime permission prompt
  - taking a photo
  - sharing a photo from Camera to Messages
  - Wi-Fi changes from our own settings screen
  - the power menu
  - reboot (does lock task come back by itself?)
- Also check that 911 can be dialed (to the emulator's fake network) from the lock screen, from our home screen, and from within lock task. Test with and without `LOCK_TASK_FEATURE_KEYGUARD`.
- *If no:* fall back to hiding apps without lock task, and redo the escape review.

**Q2. Do approved apps keep getting updates while the user can't reach the Play Store?** (Updates research tests S1–S4 and S7.)
- Confirm that `DISALLOW_INSTALL_APPS` blocks Play updates (S1).
- Confirm that the Device Owner can clear the restriction, install, and set it again (S2).
- Check whether Play can be suspended (S3).
- Check whether hidden Play stops updates, and whether Play shut out by lock task still updates in the background (S4).
- Measure how fast Plainphone hides an app that was installed without approval (S7).
- *If no:* use a supervised update window, or Plainphone's own updater for apps outside Play.

**Q3. Can our unapproved Device Owner app be set up on a Google Play image?**
- Check adb `dpm set-device-owner` right after the first-boot wizard.
- Check QR provisioning on the same image. Do we see "App blocked to protect your device"?
- **[device]:** repeat both on a real unlocked Pixel.
- *If adb is blocked on a real Pixel:* GrapheneOS becomes the MVP base (ADR 0001).

## Shapes the MVP design

**Q4. Is web containment good enough?**
- With all browsers hidden, does Plainphone's link screen become the browser automatically (`ROLE_BROWSER`)?
- What happens to Custom Tabs and sign-in or "forgot password" flows in two sample apps, one with OAuth and one with a WebView?
- Do in-task Settings deep links get through lock task? Test with and without `LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK`.
- What breaks when the Google app and Gemini are hidden? Do Circle to Search or Lens still appear?

**Q5. Does the baseline escape list hold?** Run these entries from the escape threat list on the emulator:
- E01 safe mode
- E04 OEM unlock toggle greyed out
- E05 adb off
- E06 add user
- E12/E15 Settings and Overview
- E13 Quick Settings
- E21 default launcher
- E25 network reset and user VPN
- E29 crash dialog

**Q6. Does the hidden and locked state survive?** Check that hidden apps, restrictions and lock task survive a reboot, a Play update of an allowed app, a Play update of a hidden app, and an emulator system-image update where possible.

**Q7. Can the trusted person turn Plainphone off without wiping the phone?**
- Test `clearDeviceOwnerApp` (deprecated but present) after clearing restrictions and unhiding apps.
- Confirm that photos and messages survive.
- *If no:* the only off switch is a factory reset, and setup must say so up front.

**Q8. Texting and calling defaults.**
- Does `setDefaultSmsApplication` to Google Messages work and persist?
- Do SMS and MMS arrive through the emulator console while in lock task?
- **[device]:** RCS activation on the user's carrier, and group texts with iPhones.

## Answers we need, but not before the MVP

**Q9. Is a DNS filter reliable?**
- Always-on lockdown VPN with per-app DNS policy.
- Confirm with `getConnectionOwnerUid` that an app's WebView traffic carries the app's UID.
- Check whether the Wi-Fi sign-in page (CaptivePortalLogin) bypasses the VPN.
- Measure the effect of dropping QUIC and stripping HTTPS DNS records.
- *If yes:* it becomes a follow-up change after the MVP.

**Q10. Factory reset protection.** Does `setFactoryResetProtectionPolicy` stop setup after a recovery-mode wipe on a Google Play image? **[device]** to confirm on a Pixel.

**Q11. GrapheneOS.** Can we answer Q1, Q3 and Q6 on a GrapheneOS emulator build? Only if building one costs less than a day; otherwise defer to the v2 decision.

## Not spike questions (decided by policy, not testing)

- **911 must never be blockable.** This is a requirement (spec `essential-calling-and-texting`), with a release-blocking test.
- **No market-size claims** appear in any Plainphone document until a primary source exists.
