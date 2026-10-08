# Evidence: Canada (Powell River, BC) and the Motorola moto g power 5G (2024)

Researched 2026-10-08. Target phone: moto g power 5G 2024, XT2415-1, retail Canada. Motorola's Canadian product page lists the SKU as "XT2415-1 CA 8+128" and "Operating System: Android™ 14" at launch (https://www.motorola.com/ca/en/p/phones/moto-g/g-power-5g-gen-2/pmipmgh35m7).

Labels: **documented** (a primary source says it), **inferred**, **unknown** (needs a test), **lead** (not a primary source).

**Fetch problems:**
- crtc.gc.ca returned 403 (Cloudflare) for every page, so CRTC rules are cited from canada.ca news releases, or marked "search excerpt only".
- en-ca.support.motorola.com returned 403, so Motorola support data comes from the US support site and help.motorola.com.

## Summary

1. **The biggest finding: this phone's software support ends soon.** Motorola's security page for this model says "Security updates will stop on March, 2027" and "Next OS: Android 15". Android 16 is not coming. A locked-down phone people keep for years would stop getting patches in about 6 months. The board should reconsider the device.
2. **9-1-1 works through the carrier and the built-in OS.**
   - E-Comm answers 9-1-1 for the Powell River area. BC moves to NG9-1-1 between September and November 2026.
   - Texting 9-1-1 is only for registered Deaf, hard-of-hearing and speech-impaired users. RTT to 9-1-1 has no BC date yet.
   - Android Emergency Location Service (ELS) has been live in Canada since May 2024.
   - Our design is safe if we never touch the dialer, Play services or the cell-broadcast receiver. One new rule: E-Comm's automatic callback comes from a 604 number, so our "approved callers only" filter must step aside after any 9-1-1 call.
3. **RCS:**
   - Apple lists end-to-end encrypted RCS (beta) for Rogers, Fido, Bell, Virgin, Telus, Koodo, Freedom, Public, Lucky and Chatr. Fizz has no RCS listed.
   - Bell, Virgin Plus, TELUS and Freedom name Google Messages as the Android RCS app.
   - RCS uses mobile data, so plans need data.
4. **Developer verification reaches Canada in the "2027 global" wave.** Google gives no month. Google's FAQ says installs over adb are exempt.
5. **Motorola adds escape routes a Pixel doesn't have:** Secure folder (a hidden, disguisable space that can install apps from Play), Cloned Apps, Smart Connect desktop mode, Gametime/Sidebar floating windows, and background app installers (MotoApps, App Box). Hide them all. Every one needs a real-device test, because the emulator can't run Motorola apps.
6. **No sign this model is Android Enterprise Recommended or supported by Moto OEMConfig/ThinkShield.** We don't need either, but Motorola gives us no documented help with Device Owner quirks.

---

## 1. 9-1-1 in Canada

**1a. Who must deliver calls and location.** *Documented (canada.ca); CRTC originals blocked.*
- Under Telecom Regulatory Policy CRTC 2009-40, wireless carriers had to upgrade location "By February 1, 2010, at the latest", giving responders a location "generally within a radius of 10 to 300 metres". They must also tell customers the "availability, characteristics and limitations" of enhanced 9-1-1. https://www.canada.ca/en/news/archive/2009/02/crtc-announces-enhancements-wireless-911-services.html
- NG9-1-1 (CRTC 2017-182): the CRTC "directed all telephone and mobile wireless companies to update their networks". Call centres "are under the jurisdiction of municipalities, provinces and territories." https://www.canada.ca/en/radio-television-telecommunications/news/2017/06/the_crtc_is_creatingasaferenvironmentforcanadiansbyenablingacces.html
  - The deadlines in that release (2020) were later moved. E-Comm now cites "a national deadline of March 31, 2027" (see 1b).
- Handset location (CRTC 2023-235, *search excerpt only*):
  - The location protocol "uses handset-based location technology from either Google's Emergency Location Service (ELS)… or Apple Hybridized Emergency Location (HELO)".
  - These programs "are built into the smartphone's operating system. They are not controlled by the WSP."

**1b. Powell River / qathet.** *Documented.*
- E-Comm took over as "first point of contact for 9-1-1 callers" for "Powell River Regional District (excluding Lasqueti Island)". Calls go to local dispatchers "located in Courtenay, Campbell River and Victoria". (E-Comm release, 21 October 2014.) https://www.ecomm911.ca/north-island-9-1-1-call-answer-service-successfully-transferred-to-e-comm/
- NG9-1-1: "The phased transition will take place from September to November 2026"; "communities on Vancouver Island were the first". qathet is not named, so its cut-over date is *unknown*. https://www.ecomm911.ca/e-comm-begins-phased-migration-to-next-generation-9-1-1-network/
- "A timeline is not yet known for when real-time text to 9-1-1 will be available in B.C." Text with 9-1-1 (T9-1-1): "Registration is required for this service." https://www.ecomm911.ca/ng911/
- Automatic callback after hang-ups: it calls from "a ten-digit number beginning with a 604-area code" and asks the person to press 1 or 3. https://www.ecomm911.ca/ng911/automated-callback-system/

**1c. Texting 9-1-1 and the messaging app.** *Documented.*
- Bell: "Only the default text messaging application on your phone will support T9-1-1." https://www.bell.ca/Accessibility_services/T911
- Rogers: "the handset may simultaneously place a voice call to 9-1-1 and at the same time receive and send text messages." https://www.rogers.com/support/terms/t911
- Forcing Google Messages as the default SMS app fits this. Future RTT will likely run through the system dialer. *Inferred.*

**1d. Android ELS is active in Canada.** *Documented.*
- "Android Emergency Location Service (ELS) is now available for all Android users in Canada", working "regardless of your mobile provider" (May 13, 2024). https://blog.google/intl/en-ca/products/android-chrome-more/android-emergency-location-service/
- Google's global list (https://www.android.com/safety/emergency-help/emergency-location-service/how-it-works/) omits Canada and is out of date. That page also says ELS works only with the default phone dialer.
- Motorola's help page for this model: "When you make an emergency call, Emergency Location Service automatically sends your location". https://help.motorola.com/hc/3509/15/na/en-us/T2002033703.html

**1e. Wireless Public Alerting (Alert Ready).** *Documented.*
- Alerts reach a "WPA-compatible device… capable of connecting to an LTE network" that runs "the latest version of its operating software". https://www.alertready.ca/wireless/
- AOSP CellBroadcastReceiver has a Canada-specific config (MCC 302) that sets `show_main_switch_settings` to false, so there is no master switch for users. https://android.googlesource.com/platform/packages/apps/CellBroadcastReceiver/+/refs/heads/main/res/values-mcc302/config.xml

**1f. Does our lockdown risk any of this?**
- **Lock task.** It lets emergency dialing and WEA through only if we keep `LOCK_TASK_FEATURE_KEYGUARD` (see the earlier hardware file, §5a). *Documented in AOSP.* Whether Motorola's "Emergency SOS" (press Power 5 times, https://help.motorola.com/hc/3509/15/na/en-us/CGT2002042918.html) fires inside lock task is *unknown*. Test on the device.
- **Hidden apps.** Never hide:
  - the system dialer
  - Google Play services (ELS)
  - the cell-broadcast receiver
  - Motorola's Safety & emergency component (package *unknown*)
- **Default SMS = Google Messages.** No conflict. *Inferred.*
- **NEW: call filtering.** Android's own block list stops for a while after an emergency call. "If the user contacts emergency services, number blocking is disabled by the platform for a duration defined by `CarrierConfigManager.KEY_DURATION_BLOCKING_DISABLED_AFTER_EMERGENCY_INT`." https://developer.android.com/reference/android/provider/BlockedNumberContract
  - A Plainphone CallScreeningService allowlist is our own code and gets no such exemption. *Inferred.* It would block E-Comm's 604 callback.
  - Fix: allow all incoming calls for at least 60 minutes after any 9-1-1 call. This is a release-blocking test.

## 2. RCS on Canadian carriers

**iPhone (Apple, published 15 September 2026).** *Documented.* https://support.apple.com/en-us/109526
- Bell, Rogers, Fido, Telus, Koodo, Freedom Mobile, Virgin Mobile, Public Mobile, Lucky, Chatr and Videotron all show "End-to-end encrypted RCS messaging (beta)".
- Fizz shows no RCS. Tbaytel shows plain "RCS messaging".

**Android carrier pages.** *Documented.*
- **Bell.** "Google Messages is the main messaging application". It lists only Samsung for Android, and iOS 18 on "iPhone XS and newer". If either side lacks RCS, "standard text/picture/video messaging will apply". https://support.bell.ca/mobility/rate_plans_features/how_to_use_rcs
  - Motorola is not named. *Unknown* for XT2415 on Bell.
- **Virgin Plus (Bell brand).** "RCS is available on all Android devices released in or after 2021"; "Group messaging for up to 100 users". https://www.virginplus.ca/en/support/rcs.html
- **TELUS (also Koodo and Public Mobile).** Android 5.1+ "with the latest Google Messages"; "iPhone running on iOS 18.1 and up". The other person can be on "TELUS, Koodo or Public Mobile" or "Major Canadian carriers (e.g. Bell, Rogers)". https://www.telus.com/en/support/article/android-next-gen-messaging-explained (text read from the page's embedded data)
- **Freedom.** "We recommend 'Messages' by Google". "Ensure the 'Messages' app is set as the default". "RCS and MMS messages require mobile data connectivity." https://www.freedommobile.ca/en-CA/support/about-rcs
- **Rogers / Fido.** Rogers' Android FAQ is from 2017 and out of date. https://www.rogers.com/support/mobility/messenger-for-android-faqs. Apple's list confirms RCS with iPhone. Google Messages on a Moto on Rogers is *unknown*, but likely works.

**Data.** RCS and MMS need mobile data. Bell: "Standard data charges apply". A talk-and-text plan with no data means MMS and group chats break. Recommend a plan with data. *Documented / inferred.*

**Group MMS default.** Google lets you "Choose mass text or group MMS as your default". It does not say which is the default. *Unknown*: check on the device. https://support.google.com/messages/answer/9367099

**Which carriers serve Powell River** (coverage maps, especially Freedom): not researched. *Unknown.*

## 3. Google developer verification and Canada

- **Timing.** *Documented.* "September 30, 2026: Protections begin… in Brazil, Indonesia, Singapore, and Thailand"; "Verification capability will be expanded globally for all apps on certified Android devices in 2027." Canada is not named, so it falls in the 2027 wave with no month given. https://developer.android.com/developer-verification
- **ADB.** *Documented.* "Apps installed using ADB won't require verification." "There are no changes to how ADB works." https://developer.android.com/developer-verification/guides/faq
- **Managed devices.** The FAQ exempts "Apps distributed through your organization store, on managed devices". Whether a self-built DPC counts is *unknown*.
- **Plan.** Install the first version by adb. Register as a developer before 2027 so DPC self-updates keep working.

## 4. Motorola support life for XT2415

*Documented:* https://en-us.support.motorola.com/app/software-security-update/g_id/7112/productid/11644
- "Device launched on March, 2024"
- "Security updates will stop on March, 2027"
- "This device will receive at least bi-monthly SMRs updates."
- "Launched On: Android 14"
- "Next OS: Android 15"
- "Channels and regions may have different launch dates and support cycle may vary."

Motorola's US "Android 16 Phones" list does not include this model. https://en-us.support.motorola.com/app/answers/detail/a_id/188682/~/android-16-phones

**Conclusion:** no Android 16, and security patches end around March 2027. *Documented (US page); the Canadian page was blocked.*

**Recommendation:** use this unit for the spike only, and choose a production device with support into the 2030s.

## 5. Motorola and Device Owner / Android Enterprise

- **AER list.** androidenterprisepartners.withgoogle.com/devices loads its list with JavaScript, so I couldn't read it. I found no source saying this model is AER. *Unknown.* (A 2021 German list naming older moto g models is a lead only.)
- **Moto OEMConfig.** It configures "ThinkShield for Mobile security and policy features" through an EMM. The page does not list this model and says nothing about Device Owner. https://www.motorola.com/business/moto-oem-config. We don't need it. *Inferred.*
- **`dpm set-device-owner` on Motorola.** I found no Motorola-specific documentation or reports. *Unknown.* The AOSP no-accounts rule (earlier file §3d) applies. Skip the Google and Moto account sign-ins during setup (Moto account is `com.motorola.account` per its Play listing). *Inferred.*

## 6. Motorola OEM escape surfaces (all need the real device)

Feature lists are from help.motorola.com pages for this model (`/hc/3509/15/na/en-us/`). Package ids come from Play URLs (play.google.com/store/apps/details?id=…) unless marked "bayton" (sysapps.bayton.org, lead).

| Feature | Package | What it can do | Escape risk | Recommendation |
|---|---|---|---|---|
| Moto app | `com.motorola.moto` | Hub for Gestures, Moto Secure, Display, Play/Gametime, Tips (CGT1407155202) | Links into Secure folder and gestures | Hide |
| Moto Actions | `com.motorola.actions` | Quick Capture "open the camera from any screen"; Fast Flashlight (chop); **Quick Launch** "Double-tap the back of your phone… open a selected app"; Swipe to split (uses accessibility) | Opens an app or camera outside our launcher. Lock task should block new tasks (*inferred*) | Test first. Hiding loses the chop flashlight. |
| Three-finger screenshot | part of Moto gestures | The screenshot preview offers "analyze it with Google Lens" and share (CGd0e354) | Lens → web | `setScreenCaptureDisabled` (DPM) |
| Sidebar | unknown (Settings > Gestures) | "open freeform apps in a small window" (CG1668486474) | Floating windows over lock task | Test |
| Moto Secure | `com.motorola.securityhub` | Secure folder entry, "network protection", permissions | Entry to the hidden space; may clash with our VPN | Hide |
| **Secure folder** | `com.motorola.securevault` | Own lock; "Stealth mode" hides it; "Disguised icons" with a fake name; "**Add from Google Play** and install it" (CG1542955260) | **Critical**: a hidden app space like Samsung's | Hide. Test whether creation is blocked under DO, and check `pm list users` |
| **Cloned Apps** | in Settings (unknown) | "Settings > Apps > Cloned Apps" clones apps (CG1860312928) | A second copy outside our policy | Settings is unreachable in lock task. UserManager has no public clone-profile restriction (checked). Test |
| Smart Connect / Ready For | `com.motorola.mobiledesktop` | Stream apps to PC/TV, "Mobile Desktop", reply to messages from PC; "requires elevated permissions"; has a quick-settings tile | Runs apps on a PC screen | Hide |
| Moto Display / Peek | `com.motorola.motodisplay` (Play search result; the Canadian store returned 404) | Lock-screen notifications | Low | Keep; test |
| Glance lock screen | `com.glance.lockscreenM` (bayton) | Lock-screen content feed; "Not all carriers and countries support this feature" (CGT2004131131) | Web content on the lock screen | Hide if present |
| Family Space | `com.motorola.spaces` | Kids space; "WILL BE DISCONTINUED ON DEC 31st, 2026" | Second launcher | Hide |
| Moto Unplugged | `com.motorola.om` (lead) | Focus mode that limits apps | Competing lockdown | Hide |
| Gametime | `com.motorola.gamemode` | "Social apps in freeform floating windows"; its own app sidebar | Launches apps | Hide |
| MotoApps (setup app picks) | `com.aura.oobe.motorola` (bayton: INSTALL_PACKAGES) | Installs apps | Silent installs | Hide right after provisioning |
| App Box | `com.motorola.brapps` (bayton: REQUEST_INSTALL_PACKAGES) | App installer | Installs | Hide |
| PlayAutoInstallExt | `com.motorola.playautoinstallext` (bayton) | Part of the preinstall pipeline | Installs | Hide; test |
| Device Help / Moto Feedback | `com.motorola.genie`, `com.motorola.help` | Chatbot, help pages | Web content | Hide |
| Browser | Chrome only. The preloaded list (CGjcb072613301) has no Opera or Motorola browser | | | Hide (per the existing plan) |
| Third-party preloads | Booking, Facebook, LinkedIn, TikTok "may be pre-installed" (CGjcb072613301) | | | Hide |

`DISALLOW_INSTALL_APPS` should also stop the installer packages. *Inferred.*

## 7. Other Canada-specific points

- **Banking apps.** The Moto is a Google-certified retail phone, so Play Integrity should pass. *Inferred.*
- **9-1-1 by voice only.** For our users, a voice call is the only way to reach 9-1-1 (see 1b–1c).

## Corrections to earlier evidence files

1. **hardware §5c (FCC 47 CFR § 9.10)** → Canadian carriers' duties come from CRTC 2009-40 (location) and 2017-182 (NG9-1-1). The PSAP is E-Comm.
2. **hardware §5d (ELS)** → ELS is confirmed live in Canada (Google Canada blog). android.com's country list is stale.
3. **hardware §5 controls** → add "suspend our call allowlist after a 9-1-1 call" (E-Comm callback from a 604 number). Keep Motorola's Safety & emergency component.
4. **hardware §2d/§7 (Pixel, "7 years")** → the target is XT2415-1. Its updates end March 2027 with no Android 16. The recommendation table needs redoing.
5. **hardware GrapheneOS sections** → GrapheneOS doesn't run on Motorola. Those sections are moot unless we switch to a Pixel.
6. **hardware §3e (developer verification)** → Canada is in the 2027 global wave. The adb exemption is confirmed by the FAQ.
7. **hardware §6 (US RCS)** → use the Canadian carrier list in §2 above. Watch out for no-data plans.
8. **escape E08 (Samsung/Xiaomi clones)** → Motorola has Cloned Apps and Secure folder on this exact model.
9. **escape §1.1** → the only browser on this model is Chrome. Add Glance, Smart Connect, Gametime/Sidebar and the installer packages to the hide list.
10. **hardware §5a WEA** → in Canada this is WPA (Alert Ready). Users can't switch it off (MCC 302 config).

## Needs real device (board-gated)

- `dpm set-device-owner` on XT2415-1 after skipping all accounts; check for Motorola-specific errors
- Secure folder and Cloned Apps: can they be created under a Device Owner with Moto Secure hidden? Run `pm list users` before and after
- Quick Launch, Quick Capture, Sidebar and Gametime inside lock task
- Does hiding `com.motorola.moto` / `com.motorola.actions` break anything (chop flashlight)?
- The installer packages: does anything get installed after the DO is set?
- Glance: is it present on the Canadian retail build?
- Emergency SOS (5× Power) and the lock-screen Emergency button with lock task on
- 9-1-1 test call and the E-Comm callback getting past our call filter. Coordinate with E-Comm and the carrier first.
- An Alert Ready test receipt (next national test)
- RCS activation for Google Messages on the chosen carrier (Bell's page names only Samsung); group MMS default
- The OEM-unlock toggle greyed out by `DISALLOW_FACTORY_RESET` on Motorola
- Exact package names: dialer, cell-broadcast receiver, Safety app (`pm list packages`)
