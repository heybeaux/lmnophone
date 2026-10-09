| trial | change | package | delay | reboot | result | system_server crashes |
|---|---|---|---|---|---|---|
| t01-unhide-captive-5s-adb | unhide | com.google.android.captiveportallogin | 5s | adb | LOOP | 44 |
| t02-sanity-unhide-captive-20s-adb | unhide | com.google.android.captiveportallogin | 20s | adb | BOOTED | 0 |
| t03-unhide-captive-5s-adb | unhide | com.google.android.captiveportallogin | 5s | adb | LOOP | 31 |
| t04-unhide-captive-5s-adb | unhide | com.google.android.captiveportallogin | 5s | adb | BOOTED | 0 |
| t05-unhide-captive-2s-adb | unhide | com.google.android.captiveportallogin | 2s | adb | LOOP | 31 |
| t06-unhide-captive-15s-adb | unhide | com.google.android.captiveportallogin | 15s | adb | BOOTED | 0 |
| t07-unhide-captive-30s-adb | unhide | com.google.android.captiveportallogin | 30s | adb | BOOTED | 0 |
| t08-unhide-captive-60s-adb | unhide | com.google.android.captiveportallogin | 60s | adb | BOOTED | 0 |
| t09-unhide-captive-2s-svc | unhide | com.google.android.captiveportallogin | 2s | svc | BOOTED | 0 |
| t10-hide-captive-2s-adb | hide | com.google.android.captiveportallogin | 2s | adb | BOOTED | 0 |
| t11-unhide-settings-2s-adb | unhide | com.android.settings | 2s | adb | LOOP | 27 |
| t12-unhide-vending-2s-adb | unhide | com.android.vending | 2s | adb | LOOP | 24 |
| t13-unhide-userapp-2s-adb | unhide | com.plainphone.spike.emu | 2s | adb | LOOP | 19 |
| t14-unhide-captive-8s-adb | unhide | com.google.android.captiveportallogin | 8s | adb | BOOTED | 0 |
| t15-unhide-captive-12s-adb | unhide | com.google.android.captiveportallogin | 12s | adb | BOOTED | 0 |
