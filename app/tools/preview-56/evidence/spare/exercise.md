# Build 56 exercise on the spare (PLA-562), 2026-10-14 17:20–17:45Z

Driver: Android Platform Engineer (sole spare driver). No sends, no calls. Uses the one request already saved
on the spare ("Bus times app", 13 Oct, Not sent). No one is set to get requests and no name is configured.

- PIN settings menu (layouts/01): Needs you shows "1 request from the person using this phone". The Requests
  row ("Requests from the person using this phone") shows a small "1". Tapping the Needs you row opened Requests (layouts/02).
- Not now (layouts/03): "Remove this request? The person using this phone won't be told." with Bus times app,
  Keep it and Remove. This is the no-name variant of the 907880bc correction, and this run is its first device check.
  The screenshot was black because PIN screens are FLAG_SECURE, so it was not kept.
- Keep it (layouts/04): the card is unchanged and the store is byte-identical (sha e51308013abac6d5, 124 bytes).
- Not now → Remove (layouts/05): Requests shows "No requests saved on this phone." The store went from format 1
  with 1 request (day 20739 = 13 Oct) and no tombstones to format 2 with 0 requests and 1 tombstone (day 20739).
  `decode_requests.py` decodes it. Go back asked for the PIN again. After the PIN, the menu has no Needs you row and
  the Requests row has no count (layouts/06).
- More › Ask for an app (layouts/07–09): the Bus times app request is no longer listed, and no "removed" message appears.
- Restart: at 17:38:42Z all org.plainphone processes were killed with `run-as kill -9` (pid 2494 → 8439). Lock task
  stayed LOCKED and MainActivity was on top. The store decoded the same (0 requests, 1 tombstone at day 20739).
  Ask for an app and Requests still had no request, and there was no Needs you row (layouts/10–12).
- Daily count: AppRequests counts requests plus tombstones per day. Before: 13 Oct = 1, 14 Oct (today) = 0.
  After: 13 Oct = 1 (via the tombstone), today = 0. It is unchanged. The saved request is from yesterday, so this
  shows the tombstone keeping its day's count. It is not a same-day check.
- Text size 1.0: the confirm screen (layouts/03) and the menu with the count row (layouts/01) fit above the
  nav bar (lowest text y 2218 < 2280). Text size 2.0 at about 17:41Z: the PIN menu (layouts/13) and the empty
  Requests screen (layouts/14) fit, and Done ends at y 2240. **Not exercised at 2.0 on the device:** the confirm screen and
  the menu count. The only request had already been removed, and making a new one needs a send. The host
  Paparazzi goldens cover both at scale 2 (RequestRemovalScreenshotTest confirm/menuCount). The font is back at 1.0.
- About (layouts/15–17, 15-about.png): "Version 0.1.0-preview-56 (56)" with the three notes plus the Location known
  problem under What's new. Older versions lists Version 55 first, then 54.
- after-exercise.txt vs after-install.txt: only the authority A/B hash changed (PIN entries) and the
  app-requests store changed (the removal). Everything else is unchanged (ceDataInode 32221, font 1.0, Location 0, Family, order, Wi-Fi).
