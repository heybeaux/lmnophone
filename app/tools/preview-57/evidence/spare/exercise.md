# Build 57 exercise on the spare (PLA-569), 2026-10-14 18:35–18:58Z

Driver: Android Platform Engineer (sole spare driver). No sends, no calls. Spare Location stayed off (location_mode 0)
throughout. App under test: Camera (com.motorola.camera3), which asks for Location. Before the exercise Camera was on
the home screen with no fixed choices (all eight grants granted, none POLICY_FIXED; camera-perms-before.txt), left by the
build 54 restore. Helper: `ex.sh` (uses `../../spare_lib.sh`; PIN from the local secrets file, never printed).

1. **Remove** (layouts/01–04): Camera page showed all five groups "Not chosen yet". Remove Camera → "Remove Camera?" →
   Remove → PIN → "Removed." dumpsys: hidden=true. Grants unchanged.
2. **Add with Location Allow** (layouts/05–10): Add a hidden app › Camera › "Add Camera?" (not reviewed yet) › Next ›
   "Add an app nobody has checked?" › Add anyway › choices: Allow for Camera, Location, Microphone, Notifications,
   Photos and videos (layouts/07) › Next › "Add Camera to the person using this phone's phone?" › Add Camera › PIN.
   Result 18:44:47Z: **"Done. Camera is on the home screen."** (build 54 showed "Camera was not added / Couldn't set
   Location" here). dumpsys (camera-perms-after-allow.txt): all 8 runtime permissions granted=true + POLICY_FIXED,
   including ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION; hidden=false. appops COARSE/FINE_LOCATION: foreground.
   Camera page (layouts/10): Camera, Location, Microphone, Notifications, Photos and videos all **Allowed**. Matches.
3. **Remove and add again with Location Don't allow** (layouts/11–13): Remove Camera › Remove › PIN › "Removed."
   (hidden=true), then the same add flow with Allow for every group except Location = Don't allow (layouts/11).
   Result 18:49:17Z: **"Done. Camera is on the home screen."** dumpsys (camera-perms-after-locdeny.txt):
   ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION granted=false + POLICY_FIXED; the other six granted=true +
   POLICY_FIXED. appops COARSE/FINE_LOCATION: ignore. Camera page (layouts/13): Location (where the phone is):
   **Not allowed**; the other four Allowed. Matches.
4. **Text size 2.0** (layouts/14–15): changing text size re-asked for the PIN (expected). After the PIN, the Camera page
   with "Location: Not allowed" fits; the lowest text ends at y 2237, above the nav bar (2280). Font back at 1.0.
5. **Restart** 18:51:19Z: `run-as kill -9` (pid 2491 → 9930), lock task stayed LOCKED. Camera grants byte-identical
   after restart (camera-perms-after-restart.txt). Home (layouts/16) shows Maps, Camera, Messages, Phone, More (screen
   order by bounds: Maps y1450 above Camera y1620; the XML order differs). Apps on this phone (layouts/17): Maps, Camera.
6. **Emergency and saved data**: gate rerun with SKIP_INSTALL=1 at 18:55Z, exit 0, 240 PASS
   (gate-after-exercise.log: More › Emergency prefilled exactly 911, Phone typed 911, zero calls).
   after-exercise.txt vs after-install.txt: only app order (Camera re-added after Maps, as the confirm screen says,
   "below the other apps") and the authority A/B hash (PIN entries) changed. Family store, app-requests store,
   starter list, release keys 57/56, Wi-Fi hash, Location 0, font 1.0, ceDataInode 32221 unchanged.
7. **About** (layouts/18–19, 18-about.png): "Version 0.1.0-preview-57 (57)", What's new lists the two build 57 notes.
   Older versions lists Version 56 first (including its now-fixed Location known problem, kept as history), then 55.

End state: Camera is added with Location Not allowed (fixed); the other four groups Allowed (fixed).
Not exercised on the device: the failed-add rollback (needs an injected refusal; covered on the API 35 AVD in
app/tools/pla564/evidence and by PermissionRollbackTest).
