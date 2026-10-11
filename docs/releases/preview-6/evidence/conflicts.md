# Merge conflict resolutions

Parents: `91636a2` (preview 5) and `b872680` (reviewed device).

| Conflicted file | Resolution |
|---|---|
| `app/plainphone/src/main/AndroidManifest.xml` | Device KEYGUARD-first normal lockTaskMode; preview permissions, label and adjustResize; omit device more-emergency NOT_BUILT metadata because preview implements it |
| `app/plainphone/src/main/kotlin/org/plainphone/MainActivity.kt` | Device recovery/lock-task entry/checking; preview shell, counts, navigation, branding and More prefilled dialer |
| `app/plainphone/src/main/kotlin/org/plainphone/boot/PolicyRuntime.kt` | Device recovery/boot/floor repair; preview restriction-free starter-list provider and ready-run refresh |
| `app/plainphone/src/main/res/values/strings.xml` | Preview labels/text; device checking_attention added |
| `app/ui/README.md` | Both: preview counts/calm Home plus imported trusted presentation host documentation |
| `app/ui/src/androidTest/kotlin/org/plainphone/ui/HomeScreenTest.kt` | Preview 5 |
| `app/ui/src/main/kotlin/org/plainphone/ui/home/HomeScreen.kt` | Preview 5 |
| `app/ui/src/main/res/values/strings.xml` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_attention[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_attention[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_cancelling[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_cancelling[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_duplicateIds[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_duplicateIds[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_eightApps[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_eightApps[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_firstLook[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_laterDate[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_laterDate[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_longestWaitingNotice[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_longestWaitingNotice[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_noTrustedPeople[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_oneMinute[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_oneMinute[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_oneTrustedPerson[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_oneTrustedPerson[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_overdueMonthlyCheck[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_overdueMonthlyCheck[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_pendingTurnOff[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_pendingTurnOff[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_sameDay[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_sameDay[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_selfManagedReady[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_selfManagedReady[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_twoMinutes[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_twoMinutes[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_weekday[fontScale=1].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeScreenshotTest_weekday[fontScale=2].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeShortScreenshotTest_fallback[height=540].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_HomeShortScreenshotTest_fallback[height=560].png` | Preview 5 |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_UserScreensScreenshotTest_confirmSending[fontScale=1].png` | Device; matching reviewed sending state, unreachable in preview navigation |
| `app/ui/src/test/snapshots/images/org.plainphone.ui_UserScreensScreenshotTest_confirmSending[fontScale=2].png` | Device; matching reviewed sending state, unreachable in preview navigation |
