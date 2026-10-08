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
- the trusted person's must-have apps are still to come (approval packet, decision 4). Add them here in the same format.

## Summary

| App | Package | Reviewed against (desk) | Web pages inside? | Recommendation |
|---|---|---|---|---|
| Phone | `com.google.android.dialer` | 241.0.991235506 (7 Oct 2026) | Probably only Google help pages | Always on (not a choice) |
| Messages | `com.google.android.apps.messaging` | 20260605_00_RC00 (June 2026; newest found) | **Yes**: business chats can open web pages; Gemini chat | Always on (not a choice). Read the note. |
| Camera | `com.motorola.camera3` | 9.1.52.27 (18 Feb 2026; not specific to this phone) | Not directly; QR codes and Lens lead out | Add. Viewing photos needs a separate app. |
| Slack | `com.Slack` | 26.10.10 (7 Oct 2026) | **Yes**, unless "Open web pages in app" is off | Add, with the setting off |
| Spotify | `com.spotify.music` | 9.1.90.2270 (8 Oct 2026) | A few account pages | Add |
| Telegram | `org.telegram.messenger` | 13.0.0, build 7158 (8 Oct 2026) | **Yes: a full web browser.** *To confirm in PLA-5 B-Q4* | Don't add until Beaux has seen the B-Q4 result |

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
