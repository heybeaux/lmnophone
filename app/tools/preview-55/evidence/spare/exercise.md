# Build 55 exercise on the spare (PLA-558), 2026-10-14 15:44–15:50Z

Driver: Android Platform Engineer (sole spare driver). No sends, no calls.

- Apps on this phone → Camera: the button reads "Remove Camera" (layouts/02). The clickable
  node has no content-desc, so TalkBack reads the merged child text "Remove Camera". The confirm
  screen title is "Remove Camera?" (layouts/03). Go back pressed; Camera was not removed and is still on Home.
- More › Ask for an app: "No one is set to get requests. This phone's PIN holder can set it up in More,
  then PIN settings, then Who gets requests." This appears once, and the saved request row (13 Oct, Not sent) is
  still there (layouts/04).
- PIN wait: the wait appeared after 5 wrong PINs ("Wait 27 seconds", layouts/05). font_scale went 1.0 → 2.0 at
  15:48:12Z. The activity window was recreated, and the wait page stayed up showing "Wait 14 seconds", then
  "Wait 6 seconds" (layouts/06). It did not fall back to Enter PIN. Font went back to 1.0. After the wait, the right PIN opened PIN settings.
- About: "Version 0.1.0-preview-55 (55)", with the three notes plus the Location known problem under What's new.
  Older versions lists Version 54 first, then 53 and 52 (layouts/07, 08, 07-about.png).
- after-exercise.txt vs after-install.txt: only the authority A/B hash changed, because the record stores PIN
  failures and the remaining wait. Everything else is the same (font 1.0, Location 0, app requests,
  family, order, ceDataInode).

## Second pass, 16:25–16:49Z (resumed run; same results)

The run that did the first pass hit a quota limit before it reported, so the resumed run did the exercise again.
Its layouts are 00–16 in `layouts/` and its About screenshot is `14-about.png`. `after-exercise.txt` is this pass, and
`after-exercise-pass1.txt` is the first.

- Camera page: "Remove Camera" (03). Build 54 said "Remove from the person using this phone's phone". Confirm: "Remove Camera?" (04). Not removed (05).
- Ask for an app: the not-set-up sentence appears once, and the saved row is still there (07, 08).
- PIN wait: the wait came after the 5th wrong PIN at 16:46:06Z ("Wait 27 seconds"; 10 shows 25). Font went 1.0 → 2.0 at 16:46:24Z, and the
  wait page stayed with "Wait 7 seconds" (11; PinAccessActivity still resumed). By the font-1.0 dump (12), the wait had already run out,
  so it showed Enter PIN. The right PIN then opened PIN settings.
- About: 55 with its notes (14, 15). Older versions: 54, 53, 52… (16).
- Snapshot: as in the first pass, only the authority hash changed (PIN failure counter); font is 1.0.
