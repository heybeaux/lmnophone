# Prior art and competitors: Plainphone evidence file

Checked 2026-10-08. Repo dates come from the GitHub API (`pushed_at` = last push, plus latest tagged release) or the Codeberg API, all read on 2026-10-08. Quotes come from pages I actually fetched.

## Summary

- **Nobody has built the exact product yet, but two projects come close.** I found no open-source project sold or packaged as a "dumbphone where a trusted person controls the allowlist." Two projects already do the core job:
  - **TimeLimit**: a parent remotely controls which apps are allowed. It can run as Device Owner and you can host its server yourself. Client is GPL-3.0, server is AGPL-3.0.
  - **Headwind MDM**: a Device Owner launcher plus a self-hosted web panel that decides which apps appear. Apache-2.0.
  
  Both are aimed at other markets (parental time limits and enterprise), not adult accountability.
- **Top 3 to reuse:**
  1. Headwind MDM (architecture and license fit).
  2. TimeLimit (closest to the product's behavior).
  3. Google TestDPC (Apache-2.0 reference code for every Device Owner API we need). OwnDroid is a more current GPL alternative.
- **Wisephone is not a custom OS.** Techless's own help center describes stock Samsung hardware managed through Samsung Knox (MDM), with WiseOS as an app layer on top. When a subscription lapses, the phone is "return[ed]… to a standard Android operating environment." That is close to the architecture we plan, so the approach is proven.
- **Google's Android Management API is probably not an option for a consumer product.** Its terms limit use to "commercial Enterprise Mobility Management (EMM) developers… and Original Equipment Manufacturers." That points us toward our own Device Owner (DPC) app.
- **Unpluq and Brick are not phones.** Each is a physical NFC/tap token plus a blocking app on iOS and Android. Light Phone, Punkt and Mudita all ship their own Android-derived OS.

## Prior-art projects

License fit key:

- **A** = can be copied into an Apache-2.0 project.
- **G** = only if Plainphone is GPL-3.0.
- **L** = learn from it only, or call it as a separate component.

| Project | URL | License | Last push / release | Device Owner? | What it does well | Fit |
|---|---|---|---|---|---|---|
| **Headwind MDM launcher** | https://github.com/h-mdm/hmdm-android | Apache-2.0 | 2026-10-05 / v6.39 2026-09-03 | Yes (`dpm set-device-owner com.hmdm.launcher/.AdminReceiver`) | A launcher that only shows the apps the server config allows; silent installs; on F-Droid | **A** (top pick) |
| **Headwind MDM server** | https://github.com/h-mdm/hmdm-server | Apache-2.0 | 2026-09-21 | n/a (web panel) | Self-hosted panel to "Customize the mobile desktop design and available applications". Note: "Restriction of mobile user functions" and "Kiosk mode" are listed as *Enterprise edition* only | **A**, but some lockdown features are paid |
| **TimeLimit** (client + server) | https://codeberg.org/timelimit/timelimit-android, https://codeberg.org/timelimit/timelimit-server | GPL-3.0 (client LICENSE), AGPL-3.0 (server LICENSE) | client "Release 7.8.0" 2026-09-28; server 2026-10-08 | Optional ("device owner permission makes it more difficult to uninstall TimeLimit") | Parent controls an "allowed Apps" list from another device; local mode or server mode; self-hostable server | **G** (client); server is AGPL. Top pick for behavior |
| **Google TestDPC** | https://github.com/googlesamples/android-testdpc | Apache-2.0 | 2026-09-21 / v9.0.12 2024-09-09 | Yes (reference DPC) | Google's sample code for lock task, user restrictions, app suspension and provisioning | **A** (top pick) |
| **Android Management API samples** | https://github.com/google/android-management-api-samples | Apache-2.0 | 2026-09-05 | Via Google's hosted DPC | Samples only. The API itself is enterprise-only (see below) | L |
| **OwnDroid** | https://github.com/BinTianqi/OwnDroid | GPL-3.0 | 2026-10-02 / v8.3.1 2026-08-26 | Yes; also via Dhizuku/Shizuku | Modern Compose UI covering nearly all DevicePolicyManager features: lock task, suspend/hide app, block uninstall, disable app installing | **G** |
| **Dhizuku** | https://github.com/iamr0s/Dhizuku | GPL-3.0 | 2026-10-01 / v2.12.0 2026-06-24 | Yes ("share DeviceOwner permissions to other application") | Lets several apps share one Device Owner | L/G |
| **Déchaîner** | https://github.com/warleysr/dechainer | Apache-2.0 | 2026-10-06 / v1.7.0 | Yes ("Device Owner restrictions, an Accessibility Service and an on-device ML model") | Self-control lock "built so that your future self can't simply turn it off"; per-app limits; recovery flow | **A**. Self-control, not trusted-person |
| **Shelter** | https://github.com/PeterCxy/Shelter (mirror) | GPL-3.0 | 2026-09-27 (GitHub release tag is stale, 2020) | No (work profile) | Work-profile isolation | L. Wrong model for us |
| **Island** / **Insular** | https://github.com/oasisfeng/island, https://gitlab.com/secure-system/Insular | Apache-2.0 / Apache-2.0 | 2025-04-24 / 2026-09-15 | Profile owner, optionally DO | Freezing and hiding apps | L |
| **Olauncher** | https://github.com/tanujnotes/Olauncher | GPL-3.0 | 2026-09-17 / v6.9.17 | No | Polished text-only minimal launcher | **G** |
| **mLauncher** (Olauncher fork) | https://github.com/CodeWorksCreativeHub/mLauncher | GPL-3.0 | 2026-10-08 | No | More features than Olauncher | **G** |
| **Unlauncher** (Slim Launcher fork) | https://github.com/jkuester/unlauncher | MIT | 2026-01-24 | No | Minimal launcher, on F-Droid | **A**. Best permissive launcher base |
| **Stario**, **focus-launcher** | https://github.com/albu-razvan/Stario, https://github.com/C-P-S-P/focus-launcher | GPL-3.0 | 2026-10-08 / 2026-10-06 | No | Minimal launchers; focus-launcher adds app timers | G |
| **dumbphone-launcher** (leddy2606) | https://github.com/leddy2606/dumbphone-launcher | MIT | 2026-07-26 | No (ADB disables plus a PIN-protected "Parent Settings") | Kid dumbphone launcher with a setup guide | A. Small hobby project (1 star) |
| **miniageOS** | https://github.com/ofdryads/miniageos | MIT | 2026-01-19 | No (custom LineageOS build) | Builds LineageOS with no browser and a hosts blocklist; Pixel 7a only | L |
| **nlevi-dev/DumbPhone** | https://github.com/nlevi-dev/DumbPhone | Apache-2.0 | 2026-04-19 | No (ADB suspend guide) | Explains why "Suspended apps cannot [be re-enabled from the UI]"; warns that disabling PackageInstaller causes a boot loop | L (guide) |
| **RethinkDNS** | https://github.com/celzero/rethink-app | Apache-2.0 | 2026-09-28 / v0.5.7 | No (VPN) | DNS-over-HTTPS client plus firewall; could act as a web/DNS filter | **A**. Best filter fit |
| **NetGuard** | https://github.com/M66B/NetGuard | GPL-3.0 | 2026-08-01 / 2.337 | No (VPN) | Per-app internet blocking | G |
| **personalDNSfilter** | https://github.com/IngoZenz/personaldnsfilter | GPL-2.0 (GitHub API) | 2026-09-26 | No | Hosts/DNS filter. Confirm whether "-only": GPL-2.0-only cannot be combined with Apache-2.0 | L |
| **Blokada** | https://github.com/blokadaorg/blokada | MPL-2.0 | 2026-10-08 | No | DNS/ad blocking | A/G (file-level copyleft) |
| **Accrescent** | https://github.com/accrescent/accrescent | Apache-2.0 | 2026-10-08 / 0.28.1 2025-11-10 | No | Secure app store, a model for a curated "approved apps" source | A |
| **Obtainium** | https://github.com/ImranR98/Obtainium | GPL-3.0 | 2026-09-13 / v1.6.17 | No | Pulls app updates straight from the source | L. Would let users bypass the allowlist if left installed |

**Does an open-source Device-Owner "trusted person controls the allowlist" dumbphone already exist?** Not as a packaged dumbphone. Functionally, though, TimeLimit already does "parent picks allowed apps remotely, Device Owner makes it hard to remove, self-hostable server." Headwind MDM already does "admin picks which apps the Device Owner launcher shows, from a self-hosted panel." Plainphone's new work is mainly the product layer:

- an adult accountability UX (approve or deny requests)
- a curated app catalog
- a filter
- simple provisioning

The low-level enforcement layer is not new.

## Per-product facts

### Wisephone II (Techless)

- **Hardware and OS** (https://wisephone.com/pages/wisephone-ii): the spec table says "Hardware Samsung A17 5G (SM-A176U)" and "Operating System Locked Down Android 17 • WiseOS Experience."
- **It is stock Samsung plus an MDM, not an AOSP fork.** From the Techless help center FAQ (https://techless.zendesk.com/hc/en-us/articles/43875807306637-Visit-Wisephone-II-FAQ):
  - "Wisephone utilizes Samsung hardware with our WiseOS software and configurations."
  - "Android Security + MDM Security: Wisephone benefits from default Android security, with an added layer of MDM security."
  - "we've implemented Samsung Knox technology to enhance the security and manageability of the device."
  - Unenrollment "will factory reset the device, remove WiseOS, and return the device to a standard Android operating environment."
- **Knox enrollment at setup** (https://techless.zendesk.com/hc/en-us/articles/38477196837389-Factory-Reset-Wisephone-II): after a reset, "A blue Samsung Knox Manage screen will appear briefly… After setup completes, the WiseOS software will reappear."
- **WiseOS is an app** (https://techless.zendesk.com/hc/en-us/articles/49319049366797-Fix-the-Subscription-Needed-screen): the fix is "Apps… tap WiseOS… Clear data… This only resets the WiseOS app."
- **Trusted-person controls, the Portal** (https://techless.zendesk.com/hc/en-us/articles/36419635682957-Wisephone-II-Portal-Overview):
  - "like a remote control for your phone, letting you or a trusted person decide which features are available."
  - A "device manager" is "Anyone… by creating a portal account and linking a Wisephone to it."
  - Controls listed: "Turn Tool Drawer On", "Allow Access On Device", "Enable Google Apps", "Manage Settings Remotely… (E.g., disable hotspot or factory resets)."
  - "Coming Soon: View app request statuses… screen time and data usage metrics."
  - Devices are linked by IMEI (https://techless.zendesk.com/hc/en-us/articles/36404599396237-Wisephone-II-Portal-How-to-Sign-Up-Sign-In).
- **Apps:**
  - Built in: "WiseOS includes Music, Calculator, Maps, Phone, Messages, Camera, Flashlight, Photos, Clock, Notes, Two-Factor Auth, and Calendar" (product page).
  - Third-party apps come through the Tool Drawer and are vetted with the Healthy Tech Index ("W-Approved"). "Wisephone makes the final decision" (https://techless.zendesk.com/hc/en-us/articles/48431369110925-Want-an-App-on-Your-Wisephone).
  - Excluded: "Internet browsers and search engines, such as Chrome, Perplexity, the Google app, and ChatGPT", plus social media, games, shopping and streaming (FAQ).
- **Prices** (product page):
  - "Wisephone II • $399.00 + $39 Activation Fee"
  - "WiseOS Standalone subscription Third-party carrier service required. $14.99/mo"
  - Bundled carrier plans: $24.99, $44.99 and $69.99 per month
  - "All Wisephones require a WiseOS subscription plan."
- **When the subscription lapses** (https://techless.zendesk.com/hc/en-us/articles/48271253314957-WiseOS-Subscription-FAQs): the phone enters "a restricted Setup mode." The FAQ adds that after "90 consecutive days" it is unenrolled and factory reset.

### Unpluq

https://www.unpluq.com/ says the "Unpluq Tag uses NFC (near-field communication)." It is a keychain tag plus a "Premium Subscription $29.00" app. Compatibility: "iPhones (iOS 16+) & Android devices." The site also advertises "Unpluq Family."

### Brick

https://getbrick.com/ describes a physical block plus an app: "Activate your session by simply tapping your phone against the Brick device." It costs "$59.00" and works with "Android devices running version 12.0 or later and iPhones running iOS 18.0 or later."

### Light Phone III

The official help center doesn't state the OS base outright. It does imply an Android base: "these alerts are coming through as Android notifications, outside of the Light OS" (https://support.thelightphone.com/hc/en-us/articles/4419373636756-Emergency-Alerts-Amber-Weather-etc). The changelog also mentions a "Fix for 'android loophole'" (https://support.thelightphone.com/hc/en-us/articles/360031105751-Software-Versions-Change-Log). thelightphone.com renders with JavaScript and returned no text.

### Punkt MC02

https://www.punkt.ch/products/mc02-5g-secure-phone lists the "Operating System Powered by Apostrophy (Based on AOSP 13)." It adds a "GMS Wizard" for Play Store apps and costs CHF 499.00.

### Mudita Kompakt

https://www.mudita.com/products/phones/mudita-kompakt/full-feature-list/ says "MuditaOS K is based on the Android Open Source Project (AOSP)." There is no app store, but you can sideload apps through Mudita Center.

### Android Management API (constraint)

https://developers.google.com/android/management/permissible-usage says the API is "restricted solely to commercial Enterprise Mobility Management (EMM) developers, Device Trust… solution providers, and Original Equipment Manufacturers." It forbids "Alteration of a commercially available Android Device to subsequently sell, resell or lease such altered device," with exceptions. The initial quota is 500 devices "with a full business justification."

## Not found / unsourced

- **Exact Knox product.** "Samsung Knox Manage" appears only in the factory-reset article. Techless does not explain which Knox services it uses (for example Knox Mobile Enrollment). No FCC filing was searched; the hardware is a standard Samsung model.
- **Wisephone Notion FAQ** (techless.notion.site) is rendered with JavaScript and could not be read. I used the Zendesk API copy instead.
- **Light Phone III base Android version.** Not stated by Light on the pages I could fetch. Wikipedia and blogs say it is AOSP-based (leads only).
- **Punkt MC03 / AphyOS based on GrapheneOS.** Only third-party press (Notebookcheck, The Register) says this; not confirmed on punkt.ch.
- **Before Launcher, Minimalist Phone, LessPhone.** No official source repo found. GitHub hits were unrelated or abandoned student projects (for example aswinmohanme/lessphone-final, no license, last push 2020). Treat them as closed source unless proven otherwise.
- **"Wisp", "Lumi", "LightOS-style" open projects.** No relevant open-source projects found under these names.
- **TimeLimit Device Owner internals.** The site confirms Device Owner support. I did not read the source to see which DevicePolicyManager calls it uses.
- **personalDNSfilter.** Whether it is GPL-2.0-only or "or later" was not confirmed.
- **Market sizing.** Skipped as instructed.
