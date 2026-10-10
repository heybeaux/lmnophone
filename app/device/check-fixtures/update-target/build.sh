#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
#
# Builds the UpdateInstallerCheck targets into OUT (default ./out), with throwaway keys made there:
#   v1.apk          org.plainphone.updatetarget 1, key A, small (installed by adb before the check)
#   v2.apk .. v5.apk  same package, key A, each with a 40 MB incompressible asset so an install takes
#                   about 100 ms (X6), long enough to act while it is still pending
#   v6-keyb.apk     same package, version 6, key B
#   v6-otherpkg.apk org.plainphone.updateother 6, key A
#   v6-tampered.apk v6 signed with key A, then one byte of its signed asset flipped
# and prints the signers. Keys never leave OUT; do not commit them.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="${1:-$here/out}"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
bt="$sdk/build-tools/${BUILD_TOOLS:-36.0.0}"
jar="$sdk/platforms/android-${PLATFORM:-35}/android.jar"
mkdir -p "$out"
cd "$out"
for k in a b; do
  [ -f "key-$k.jks" ] || keytool -genkeypair -keystore "key-$k.jks" -storepass checkpass -keypass checkpass \
    -alias "$k" -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Plainphone update check key $k" >/dev/null 2>&1
done
mkdir -p assets-small assets-big
[ -f assets-big/pad.bin ] || head -c 41943040 /dev/urandom > assets-big/pad.bin
echo small > assets-small/pad.bin

build() { # name version key assets [package]
  "$bt/aapt2" link -o "$1.unsigned.apk" -I "$jar" --manifest "$here/AndroidManifest.xml" \
    --version-code "$2" --version-name "$2" -A "$4" -0 bin ${5:+--rename-manifest-package "$5"}
  "$bt/zipalign" -f -p 4 "$1.unsigned.apk" "$1.aligned.apk"
  "$bt/apksigner" sign --out "$1.apk" --ks "key-$3.jks" --ks-pass pass:checkpass --ks-key-alias "$3" "$1.aligned.apk"
  rm -f "$1.unsigned.apk" "$1.aligned.apk" "$1.apk.idsig"
}
build v1 1 a assets-small
for v in 2 3 4 5; do build "v$v" "$v" a assets-big; done
build v6-keyb 6 b assets-big
build v6-otherpkg 6 a assets-big org.plainphone.updateother
build v6-tampered 6 a assets-big
python3 - v6-tampered.apk <<'PY'
import sys, zipfile
p = sys.argv[1]
with zipfile.ZipFile(p) as z:
    info = z.getinfo("assets/pad.bin")
data = bytearray(open(p, "rb").read())
# Local header is 30 bytes plus name and extra; the stored asset follows. Flip a byte in it.
name_len = int.from_bytes(data[info.header_offset+26:info.header_offset+28], "little")
extra_len = int.from_bytes(data[info.header_offset+28:info.header_offset+30], "little")
start = info.header_offset + 30 + name_len + extra_len
data[start + 1000] ^= 0xFF
open(p, "wb").write(bytes(data))
PY
for f in v1 v2 v6-keyb v6-otherpkg v6-tampered; do
  echo "== $f.apk $(stat -f %z "$f.apk" 2>/dev/null || stat -c %s "$f.apk") bytes sha256 $(shasum -a 256 "$f.apk" | cut -d' ' -f1)"
  "$bt/apksigner" verify --print-certs "$f.apk" 2>&1 | grep -E "SHA-256|DOES NOT VERIFY|ERROR" | head -3 || true
done
