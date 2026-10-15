# Build 64 exercise on the spare (PLA-618)

Sole spare driver, 2026-10-15 06:20–06:38 UTC. No sends, no emergency calls.

Home on the spare has 3 apps in the list (Maps, Camera, Messages) and the bottom row (Phone, More).

| Check | Result | File |
|---|---|---|
| 1.0 text, Tab from Home | Maps, Camera, Messages, Phone, More, then wraps to Maps | tab-1.0.txt |
| 1.0 text, Shift+Tab | exact reverse: More, Phone, Messages, Camera, Maps | tab-1.0.txt |
| 2.0 text, Tab after a touch | Maps, Camera, Messages, Phone, More | tab-2.0-touch-then-tab.txt |
| 2.0 text right after the size change, still in keyboard mode | the recreated window had already focused Maps, so the first Tab lands on Camera; order is the same | tab-2.0-after-size-change.txt |
| 2.0 text, restart (boot 36 s, LOCKED, HOME .HomeEntry) | same order both ways; Messages ends just above Phone (2070 vs bottom row 2100) | tab-2.0-after-restart.txt |
| Text size 2.0 → 1.0 → 2.0 | same order at each size | tab-size-change-back-and-forth.txt |
| Scrolling Home | **not reachable on the spare**: with 3 list apps the page does not scroll at 2.0, nor with Display size raised to density 600 (reset to 400 afterwards). The scroll and anchor cases are emulator-tested only (HomeAnchorTest, PLA-617). | tab-2.0-density600.txt |
| TalkBack + Tab (speech recorded and transcribed) | Camera (in list), Messages, out of list, Phone, More, Maps (in list); Shift+Tab: More, Phone. Same cyclic order as the screen. The first Tab started after Maps, as above. TalkBack swipes not tried. | talkback/home-tab-64.srt |
| About | Version 0.1.0-preview-64 (64), What's new: the Version 64 line; no "since" section (previous was 63) | about-64.txt |

Text size restored to 1.0, TalkBack off, density 400, ring volume unchanged (5).
