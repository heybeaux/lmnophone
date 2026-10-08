# Starter catalog: web-leak notes (task 5.2)

**Spec:** web-containment ("Web-leak notes are tied to an app version"). Each entry has:
- the **note** the trusted person sees before adding the app (T4a in
  [flows-trusted-person.md](flows-trusted-person.md#t4a-add-app)), in 2–4 plain lines;
- the **version it was reviewed against**, so Plainphone can show "Reviewed for an older version" when the app
  moves on;
- the routes behind the note, with sources, for reviewers;
- suggested settings inside the app, and sign-in advice (with no web browser on the phone, browser-based sign-in
  fails).

## How these were reviewed

- **Desk review only, 2026-10-08.** Sources: each app's own release notes or release channel, help pages,
  Telegram's open-source Android code, and download sites that list versions. Google Play's own pages and
  APKMirror couldn't be read by our research tool, so versions come from the sources named in each entry.
- **No app here has been tested under Plainphone yet.** The device test is PLA-5 Phase B, question Q4
  ("B-Q4": must-have apps, link matrix, reachable system screens). Every route below marked *(to confirm)* waits
  for that result. Device facts already recorded are cited from `spikes/device-moto/RESULT.md` and
  `spikes/device-moto/evidence/` (XT2415, build `V1UDS35H.26-14-6-2-11`).
- **Versions.** "Reviewed against" is the newest version the sources showed on 2026-10-08. The AE records the
  version actually installed on the test phone in B-Q4 (`dumpsys package <pkg> | grep versionName`); if it
  differs, this file is updated and the note re-read.
- the trusted person's must-have apps are **Gmail and Outlook** (Beaux, Telegram 4448/4451, recorded in the approval packet,
  decision 4; passed on by the Lead on PLA-12). Their entries were added on 2026-10-08, after the first batch. How
  each one opens links is marked *to confirm in PLA-5 B-Q4* until PLA-5 reports it.

## Summary

| App | Package | Reviewed against (desk) | Web pages inside? | Recommendation |
|---|---|---|---|---|
| Phone | `com.google.android.dialer` | 241.0.991235506 (7 Oct 2026) | Probably only Google help pages | Always on (not a choice) |
| Messages | `com.google.android.apps.messaging` | 20260605_00_RC00 (June 2026; newest found) | **Yes**: business chats can open web pages; Gemini chat | Always on (not a choice). Read the note. |
| Camera | `com.motorola.camera3` | 9.1.52.27 (18 Feb 2026; not specific to this phone) | Not directly; QR codes and Lens lead out | Add. Viewing photos needs a separate app. |
| Slack | `com.Slack` | 26.10.10 (7 Oct 2026) | **Yes**, unless "Open web pages in app" is off | Add, with the setting off |
| Spotify | `com.spotify.music` | 9.1.90.2270 (8 Oct 2026) | A few account pages | Add |
| Telegram | `org.telegram.messenger` | 13.0.0, build 7158 (8 Oct 2026) | **Yes: a full web browser.** *To confirm in PLA-5 B-Q4* | Don't add until Beaux has seen the B-Q4 result |
| Gmail | `com.google.android.gm` | 2026.09.28.992490113.Release (8 Oct 2026) | **Maybe.** How links open is *to confirm in PLA-5 B-Q4*; interactive emails; Google account pages | Add after B-Q4, with Chat and Meet tabs off. Preinstalled. |
| Outlook | `com.microsoft.office.outlook` | 5.2637.3 (30 Sep 2026) | **Maybe.** How links open is *to confirm in PLA-5 B-Q4*; ads; Copilot | Don't add until B-Q4 shows sign-in works (see the account warning) |

---

## Phone (Google)

- **Package:** `com.google.android.dialer` · **Reviewed against:** 241.0.991235506, 7 Oct 2026
  ([Uptodown version list](https://google-phone.en.uptodown.com/android/versions)). Which build Play serves this
  phone is not confirmed; record it in B-Q4.
- **Note on screen:**
  > Phone doesn't open web pages for calling. Its "Help & feedback" page may show Google help pages; testing will
  > tell us whether that opens on this phone.
- **Routes:**
  - Help & feedback opens Google's help (a Google Play services screen). Because Play services isn't on the
    lock-task list, the in-task block flag should stop it. *(Inference; to confirm in Q1/B-Q4.)*
  - "Nearby places" business search was removed in 2024
    ([9to5Google](https://9to5google.com/2024/03/16/google-phone-nearby-places-search/)).
  - Caller ID & spam shows business names only. Call Screen is not web content
    ([Google help](https://support.google.com/phoneapp/answer/9118387?hl=en)).
- **Settings inside the app:** none needed.
- **Device facts:** opens under lock task on the XT2415 (`evidence/a5/breakage.txt`).

## Messages (Google)

- **Package:** `com.google.android.apps.messaging` · **Reviewed against:** 20260605_00_RC00 (June 2026), the
  newest version our sources showed ([Wikipedia, citing APKMirror](https://en.wikipedia.org/wiki/Google_Messages)).
  This is older than what Play is likely serving; B-Q4 must record the real version, and this note is re-read
  then.
- **Note on screen:**
  > Messages can show web pages in one case: chats from businesses can open a page inside Messages. Messages also
  > has Gemini, Google's AI chat, which can answer any question. Links people send you go to Plainphone's
  > "can't open" screen.
- **Routes:**
  - **Business chats (RCS Business Messaging)** can open a URL in a web view inside Messages, full or part screen
    ([Google RBM docs](https://developers.google.com/business-communications/rcs-business-messaging/guides/build/messages/send#open-url-in-webview)).
    From there, the page's own links may lead further. *(Confirmed by Google's docs; to confirm on device in
    B-Q4.)*
  - **Gemini in Messages** is available in Canada in English and French, needs a personal Google account, age
    18+, and chat features (RCS) on ([Google help](https://support.google.com/messages/answer/14599070?hl=en)).
    The user signs in to their own Google account at setup (step 3), so it is likely available. Hiding the Gemini
    app does not remove it. *(To confirm in B-Q4: can it be turned off, and does it stay off?)*
  - **Link previews** show a title and picture; tapping goes to the blocked-link screen (by design; to confirm).
    Previews can be turned off: profile picture → Messages settings → Automatic previews
    ([Google help](https://support.google.com/messages/answer/9053541?hl=en)).
  - **YouTube links** may play inside Messages in a small video player
    ([9to5Google via Mezha](https://mezha.ua/en/news/google-messages-otrimuye-nove-vidobrazhennya-posilan-306666/)).
    *(Likely; to confirm.)*
  - **GIFs and stickers** come from Tenor through the keyboard. They are pictures, not web pages
    ([9to5Google](https://9to5google.com/2026/06/30/google-tenor-api-gif-updates/)).
  - **Google sign-in prompt.** On the XT2415, opening Messages showed Google's "express sign-in" screen
    (`BugleExpressSignInActivity`, `evidence/a5/breakage.txt`). After setup, adding accounts is blocked, so this
    should be harmless; B-Q4 checks it doesn't lead anywhere.
- **Settings inside the app (setup step 5):** turn off Automatic previews if {tp} prefers; turn off Gemini if the
  app allows it (record whether it can be turned back on by the user).
- **Not a choice:** Messages is always on (calm-home-screen). The note is still shown, in "Apps on this phone".

## Camera (Motorola)

- **Package:** `com.motorola.camera3` · **Reviewed against:** 9.1.52.27, 18 Feb 2026
  ([Uptodown](https://moto-camera-3.en.uptodown.com/android/versions)). This listing is not specific to the
  XT2415; B-Q4 records the version on the phone.
- **Note on screen:**
  > Camera doesn't show web pages itself. Scanning a QR code with a web link goes to Plainphone's "can't open"
  > screen. To look at photos afterwards, you'll need to add a photos app too, and that app has its own note.
- **Routes:**
  - **Google Lens** is built into Moto Camera on this phone family
    ([moto g 5G 2024 spec sheet](https://webcdn.cox.com/content/dam/cox/residential/support/Mobile/Print_Media/motog_5G_2024_Spec_Sheet.pdf)).
    Lens comes from the Google app, which Plainphone hides, so the Lens button probably fails. *(To confirm.)*
  - **QR codes:** the camera reads them and offers to open the link
    ([HowardForums](https://www.howardforums.com/threads/qr-code-reader-but-which-one-is-it-two-diff-devices.1932153/)).
    A web link then goes to the blocked-link screen. *(By design; to confirm.)*
  - **The photo thumbnail opens Google Photos.** On the XT2415 with the in-task block flag off, it opened Google
    Photos inside the locked task; it has not been run with the flag on (`RESULT.md` § A3). With the flag on,
    it should be blocked unless Photos is added. Google Photos has its own help, account and print-shop pages,
    so it needs its own catalog entry before anyone adds it.
  - Taking a photo works under lock task (`evidence/a5/breakage.txt`: MediaStore images 1 → 2).
- **Permissions to suggest at T4c:** Camera (Allow: "Take photos and videos"), Microphone (Allow: "Record sound in
  videos"), Where the phone is (Don't allow unless wanted: "Add the place to photos"). Note: the camera's own
  permission prompt is blocked under lock task (`RESULT.md` § A3), so these choices matter.

## Slack

- **Package:** `com.Slack` · **Reviewed against:** 26.10.10, 7 Oct 2026
  ([Slack release notes](https://slack.com/release-notes/android)).
- **Note on screen:**
  > Slack can open web pages inside itself when someone posts a link. Turn this off in Slack: You → Preferences →
  > Advanced → "Open web pages in app". {user} could turn it back on. Sign in with the code Slack emails you.
- **Routes:**
  - **"Open web pages in app"** (You → Preferences → Advanced) is on by default
    ([Atlassian guide](https://support.atlassian.com/jira-cloud-android/docs/open-jira-directly-from-slack-deep-links)).
    When on, any link posted in any chat opens inside Slack, and from there the web is open. When off, links go to
    a Custom Tab; Plainphone provides none, so they should reach the blocked-link screen. *(To confirm in B-Q4.)*
  - The setting is inside Slack, so the user can switch it back on. Plainphone can't lock it.
  - "View in browser" for images, and links inside canvases
    ([release notes](https://slack.com/release-notes/android)) follow the same setting. *(Likely.)*
  - Slackbot can act as an AI assistant connected to other apps; we found no proof it searches the web.
- **Sign-in:** use the **emailed confirmation code**. Single sign-on (SSO) needs a web browser and will fail.
  "Continue with Google" may work through the phone's Google account; unverified
  ([Slack help](https://slack.com/help/articles/212681477-Sign-in-to-Slack)). A workspace admin can force a
  specific browser for sign-in; if so, Slack can't be signed in on Plainphone.
- **Permissions to suggest:** Notifications (Allow), Microphone (for huddles and voice clips), Camera (for
  photos), Photos and videos (to share pictures), Contacts (Don't allow).

## Spotify

- **Package:** `com.spotify.music` · **Reviewed against:** 9.1.90.2270, 8 Oct 2026
  ([Uptodown](https://spotify.en.uptodown.com/android/versions)).
- **Note on screen:**
  > Spotify mostly doesn't show web pages. Some account and plan pages open as web pages inside it, and links
  > in podcast descriptions and concert listings go to Plainphone's "can't open" screen. Podcasts and videos
  > in Spotify are open to anyone who publishes them.
- **Routes:**
  - Some plan changes show a web page inside the app
    ([Spotify community](https://community.spotify.com/t5/Android/Can-t-change-premium-plan-on-Android/td-p/6691211)).
    *(Reported; to confirm whether it can reach the open web.)*
  - Podcast show notes contain links
    ([community](https://community.spotify.com/t5/Android/Links-in-podcast-show-notes-are-broken-because-of-removed-space/m-p/4788890/highlight/true));
    Live Events and concerts link to ticket sellers
    ([Spotify support](https://support.spotify.com/id-en/article/live-events-feed/)). Both should leave the app
    and reach the blocked-link screen. *(To confirm.)*
  - Cancelling Premium is done on Spotify's website, so it can't be done on the phone; do it from a computer
    ([Business Insider](https://www.businessinsider.nl/how-to-cancel-spotify-premium-on-android/)).
- **Sign-in:** use **email** or **phone number with a texted code**. Apple and Facebook sign-in are web-based and
  will fail ([Spotify support](https://support.spotify.com/in-en/article/phone-number-login-help/)).
- **Permissions to suggest:** Notifications (Allow, "Show what's playing"), Nearby devices (Allow, "Play on
  speakers and headphones"), Microphone (Don't allow unless voice search is wanted).

## Telegram

- **Package:** `org.telegram.messenger` · **Reviewed against:** 13.0.0, build 7158, 8 Oct 2026
  ([Telegram release channel](https://t.me/s/tgstable);
  [Uptodown](https://telegram.en.uptodown.com/android/versions)). The Play build may lag by a few days.
- **Status: in-app browser to confirm in PLA-5 B-Q4.** The approval packet calls Telegram's browser a lead.
  Source code now supports it strongly, but nothing has been tested on the phone, so the note stays provisional.
- **Note on screen (provisional):**
  > Telegram has its own web browser inside it *(to confirm in PLA-5 B-Q4)*. It looks like it lets anyone type a
  > web address or search Google, which would get around Plainphone. "Mini apps" inside Telegram are web pages
  > too, and Telegram has no setting that fully turns these off.
- **Routes:**
  - **In-app browser with an address bar and web search.** Telegram's Android code includes an address bar with
    suggestions and a search-engine picker that falls back to `https://www.google.com/search?q=`
    ([SearchEngine.java](https://raw.githubusercontent.com/DrKLO/Telegram/master/TMessagesProj/src/main/java/org/telegram/ui/web/SearchEngine.java),
    [web/ folder](https://github.com/DrKLO/Telegram/tree/master/TMessagesProj/src/main/java/org/telegram/ui/web)).
    Tabs, history and bookmarks arrived with the browser overhaul of 31 Jul 2024
    ([Telegram blog](https://telegram.org/blog/w3-browser-mini-app-store)). *(To confirm in PLA-5 B-Q4.)*
  - **The setting** is Settings → Chat Settings → In-App Browser. When off, links go to a Custom Tab or the
    default browser, which on Plainphone should mean the blocked-link screen. But:
    - it has "Always open in" exceptions;
    - it appears to be stored with the account and may sync from another device;
    - a 2026 bug report says some links still opened in the in-app browser with the setting off
      ([bugs.telegram.org/c/63131](https://bugs.telegram.org/c/63131)).
    So the setting is not a block. *(To confirm in PLA-5 B-Q4.)*
  - **Mini Apps** (bot web apps, Mini App Store) always run in Telegram's own web view; the browser setting
    doesn't remove them. **Instant View** opens articles in the same viewer. *(Likely; to confirm in PLA-5
    B-Q4.)*
  - Inline bots (for example @gif, @wiki) and payment pages are probably further routes. *(Inference.)*
- **Sign-in:** phone number and a code by text or Telegram. No browser needed. *(Inference.)*
- **Recommendation:** don't add Telegram until Beaux has seen the B-Q4 result, as the approval packet promised
  ("We'll show you what we find before you approve it"). If added, the T4b-style extra confirmation should be
  shown even though a catalog entry exists.

## Gmail

the trusted person's must-have. **Preinstalled on the Motorola**, so "adding" it means Plainphone stops hiding it; there is
nothing to download.

- **Package:** `com.google.android.gm` · **Reviewed against:** 2026.09.28.992490113.Release, 8 Oct 2026
  ([Uptodown version list](https://gmail.en.uptodown.com/android/versions)). The preinstalled copy on the XT2415
  is probably older until Play updates it; B-Q4 records the real version.
- **Status: how links open is to confirm in PLA-5 B-Q4.**
- **Note on screen (provisional):**
  > Gmail shows emails, and emails can contain links and pictures. Where a tapped link goes is still being
  > tested *(to confirm in PLA-5 B-Q4)*. Some emails let you fill in a form or reply to an invitation inside the
  > email. Gmail may also include Gemini, Google's AI chat, which can answer questions.
- **Routes:**
  - **Links in emails.** Gmail has a setting, "Open web links in Gmail" (Settings → General settings), which opens
    links inside Gmail rather than in a separate browser. Sources disagree on what that "inside" view is: a Custom
    Tab from the phone's browser ([SamMobile, 2024](https://www.sammobile.com/?p=15057589);
    [tt-rss report](https://community.tt-rss.org/t/dont-hardcode-chrome-when-opening-custom-tabs/319)) or Gmail's
    own web view ([droidwin](https://droidwin.com/?p=46137)). With every browser hidden, a Custom Tab has nothing
    to open in, so the link should fall through to Plainphone's "can't open" screen. If Gmail uses its own web
    view, the page opens inside Gmail and the web is reachable from there. *(To confirm in PLA-5 B-Q4, with the
    setting on and off.)*
  - **Interactive emails (AMP "dynamic email").** On by default in the Android app since 2019. A sender's email
    can show live content, forms and replies inside the message
    ([Google Workspace blog](https://workspaceupdates.googleblog.com/2019/11/dynamic-mail-android-ios.html)). It
    stays inside that one email: no address bar, and links behave as above. *(Desk finding; to confirm.)*
  - **Pictures in emails** load from the sender through Google's servers. They are pictures, not pages.
  - **Chat and Meet tabs.** Gmail can show Google Chat and Meet tabs. Each can be hidden per account in Gmail's
    settings: "Show the Meet tab for video calling", and "Show the chat and spaces tab"
    ([Android Central](https://www.androidcentral.com/how-disable-google-meet-tab-gmail-android)). Hiding them
    doesn't remove the services; Chat links behave like email links. *(To confirm in B-Q4: where Meet and Chat
    links go with the tabs off.)*
  - **Gemini in Gmail** (summaries, drafting replies) has been reported reaching personal accounts on Android
    in 2026 ([CO/AI, citing Mishaal Rahman](https://getcoai.com/news/gemini-ai-may-come-to-personal-gmail-accounts-on-android)).
    Whether it is on the trusted person's account in Canada, and whether it can be turned off, is unknown. *(To confirm.)*
  - **"Manage your Google Account"** (tap the profile picture) opens Google's account pages from Google Play
    services. Those pages contain links to Google help and policies. Play services isn't on the lock-task list,
    so the in-task block flag should stop it. *(Inference; to confirm in B-Q4.)* **Help & feedback** is the
    same.
  - **Adding another mail account inside Gmail** (for example the trusted person's Outlook address) goes through Android's
    accounts, which Plainphone blocks after setup. This is expected to fail. *(Inference from the device-lockdown
    spec; to confirm.)*
- **Sign-in:** none needed. Gmail uses the Google account signed in at setup (step 3).
- **Settings inside the app (setup step 5):** hide the Meet tab and the Chat tab unless {user} uses them; turn
  "Open web links in Gmail" to whichever setting B-Q4 shows ends at the "can't open" screen.
- **Permissions to suggest at T4c:** Notifications (Allow, "Tell you about new email"), Contacts (Allow,
  "Suggest addresses as you type"), Camera and Photos and videos (Allow only if wanted, "Attach photos").

## Outlook (Microsoft)

the trusted person's must-have.

- **Package:** `com.microsoft.office.outlook` · **Reviewed against:** 5.2637.3, 30 Sep 2026
  ([Uptodown version list](https://microsoft-outlook.en.uptodown.com/android/versions)).
- **Status: how links open is to confirm in PLA-5 B-Q4.** Sign-in may not work at all (see the warning below).
- **Note on screen (provisional):**
  > Outlook shows emails, and emails can contain links and pictures. Where a tapped link goes is still being
  > tested *(to confirm in PLA-5 B-Q4)*. Free Outlook accounts show adverts in the inbox. Outlook may suggest
  > installing Microsoft Edge; Edge is a web browser, so Plainphone keeps it hidden.
- **Routes:**
  - **Links in emails.** Outlook's "Open links" setting chooses between Microsoft Edge and the phone's default
    browser. Without Edge installed, links go to the default browser, and Outlook from time to time offers to
    install Edge ([Microsoft notice MC859848](https://mc.merill.net/message/MC859848);
    [Ablebits](https://www.ablebits.com/office-addins-blog/change-outlook-default-browser/)). On Plainphone, the
    "default browser" should be Plainphone itself, so links should reach the "can't open" screen. Whether Outlook
    also has its own web view for some links is unknown. *(To confirm in PLA-5 B-Q4.)*
  - **Edge prompts.** If someone installs Edge through the prompt (only possible in a Play session), the browser
    classifier should hide it. *(By design; to confirm.)*
  - **Adverts.** Free Outlook.com accounts show ads in the inbox, marked "Ad". Turning on Focused Inbox keeps them
    in the "Other" tab; a Microsoft 365 subscription removes them
    ([Microsoft support](https://support.microsoft.com/office/ads-in-outlook-mobile-b172e506-ef2c-458d-9c2d-265445ba82e5)).
    Tapping an ad should go to the "can't open" screen. *(To confirm.)*
  - **Copilot** is in Outlook for Android for Microsoft 365 Personal or Family subscribers, as long as "Connected
    experiences" is on ([Microsoft support](https://support.microsoft.com/en-us/office/sharing-copilot-in-outlook-cf2656c0-57f9-4962-97d7-ba0c7b74858a)).
    We found no proof that it searches the web from Outlook. *(To confirm, only if the trusted person has a subscription.)*
  - **Help, privacy and Microsoft account pages** inside Outlook settings probably open as web pages.
    *(Inference; to confirm in B-Q4.)*
- **⚠ Sign-in may not work on Plainphone. To confirm in PLA-5 B-Q4 before anyone adds Outlook.**
  1. **No browser.** Signing in to a Microsoft account, or adding a Google account to Outlook, may open a web
     sign-in page. Google blocks its sign-in inside apps' own web views, so Outlook likely needs a browser for a
     Google account ([Microsoft support](https://support.microsoft.com/office/how-do-i-add-a-google-apps-account-490b1025-e74f-440c-853d-8cdec5fa7bc8)
     describes the steps but not the window). With every browser hidden, that page may fail.
  2. **Accounts are locked.** Outlook normally adds its accounts to Android's account list so contacts and
     calendars can sync. Plainphone blocks changes to that list after setup (`DISALLOW_MODIFY_ACCOUNTS`,
     device-lockdown spec), and no supervised session lifts it. Outlook may then refuse to sign in, or sign in
     without contact and calendar sync. *(Inference; to confirm.)*

  If either one fails, Outlook can't be offered as written. That needs a decision from the Lead (open question 7
  in [README.md](README.md#open-questions-for-the-lead)). **Which account the trusted person uses in Outlook** (an
  Outlook.com address, a work address, or her Gmail) is also unknown, and it changes which sign-in is tested.
- **Settings inside the app (setup step 5):** turn on Focused Inbox, to keep ads out of the main view;
  "Open links": default browser, not Edge.
- **Permissions to suggest at T4c:** Notifications (Allow, "Tell you about new email"), Contacts and Calendar
  (Don't allow unless wanted: "Copy Outlook contacts and events to the phone"), Microphone (Don't allow:
  "Dictate emails"), Camera and Photos and videos (Allow only if wanted, "Attach photos").

---

## The keyboard (Gboard): not an app choice, but worth knowing

Gboard is the system keyboard and can't be removed (it is part of the emergency floor). Its GIF and sticker
search shows Tenor pictures, not web pages; its built-in Google Search was removed in 2020
([9to5Google](https://9to5google.com/2020/06/24/gboard-removes-google-search/)). It is open-ended picture search,
so it belongs in "What Plainphone can't stop" item 2 if B-Q4 finds it reachable.

## For B-Q4 (handed to the AE)

For each app above, record on the XT2415 with the in-task block flag on:
1. `versionName` of the installed app.
2. Each *(to confirm)* route: does it open, and if it opens web content, can it reach an arbitrary address?
3. For Telegram: with the In-App Browser setting on and off, can an address be typed? Do Mini Apps and Instant View
   open? Does the setting survive a sign-in on a second device?
4. For Slack: with "Open web pages in app" off, does a posted link reach the blocked-link screen?
5. For Messages: can Gemini be turned off and stay off; does a business-chat web view open?
6. For Camera: does the Lens button do anything; where does a QR web link go; what does the thumbnail do?
7. For Gmail: with "Open web links in Gmail" on and off, where does a link in an email go (Custom Tab, a view
   inside Gmail, or the "can't open" screen)? Does an interactive (AMP) email open anything beyond itself? Where do
   "Manage your Google Account" and Help & feedback go? With the Chat and Meet tabs hidden, where does a Meet link go?
8. For Outlook: can the trusted person's account sign in with every browser hidden and adding accounts blocked? If it signs in,
   do contacts and calendar sync? Where does a link in an email go, and where does an ad go? Which account type
   does the trusted person use (ask Beaux)?
