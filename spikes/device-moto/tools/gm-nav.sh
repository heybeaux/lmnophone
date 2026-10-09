#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-5 B-Q4 Gmail). Print ONLY known navigation labels and their centres, never mail content.
set -euo pipefail
a() { adb -s "${SERIAL:-SPARE_DEVICE_SERIAL}" "$@"; }
a shell uiautomator dump /sdcard/pp-ui.xml >/dev/null
a shell cat /sdcard/pp-ui.xml | tr '>' '\n' \
 | grep -E '(text|content-desc)="(Open navigation drawer|Mail|Chat|Meet|Compose|Search in mail|Settings|Help (&amp;|and) feedback|Got it|Take me to Gmail|OK|Turn on|Not now|No thanks|Skip|Close|Navigate up|New meeting|Join with a code|Start a chat|New chat|General settings|Help|Send feedback|Privacy Policy|Terms of Service|Open source licen[cs]es|Manage your Google Account|Inbox|Primary|Promotions|Social|Updates|Forums|Snoozed|Sent|Drafts|Spam|Trash|All inboxes|Add account|App is not available|[A-Za-z ]+ is not available right now\.|Allow|Don.t allow|Continue|Dismiss|Turn on notifications|Get started)"' \
 | sed -nE 's/.*(text|content-desc)="([^"]+)".*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\2 @ \3 \4 \5 \6/p' \
 | awk '{n=NF; x=int(($(n-3)+$(n-1))/2); y=int(($(n-2)+$n)/2); $(n-3)=$(n-2)=$(n-1)=$n=""; sub(/ +@ *$/,""); print $0 " @ " x "," y}' | sort -u
a shell rm -f /sdcard/pp-ui.xml
