# THROWAWAY: Bluetooth discovery/pairing probe

Question: can a named peer be discovered with Location off and paired while
LMNOPhone remains in lock task? Tested on API 35, emulator 37.3.3.

Result: discovery succeeds; pairing cannot complete. On emulator-5880,
`BluetoothPairingDialog` was rejected as a lock-task violation (result 101).
The peer emulator showed its normal pairing confirmation. The initiating
emulator never showed its confirmation. The peer request was cancelled;
BOND_NONE read back and bonded count stayed zero. No package was added to
lock task and no call was placed. Build 32 therefore ships discovery only.

Two separate netsim processes cannot discover each other's radios. Start the
private peer with `-packet-streamer-endpoint localhost:<assigned netsim port>`.
Find that port from the assigned emulator's netsimd process using lsof; do not
reset or modify a shared netsim scene. Confirm both private AVD names in
`netsim -p <port> devices`. Use `-read-only -port 5934` for the peer.

Build with `ANDROID_HOME`, JDK 17, `REVIEW_DEBUG_KEYSTORE` (the existing debug
signer), and `PAPERCLIP_SCRATCH_DIR` set, using `bash build.sh`. Probe output
contains counts, not device names/addresses. The named peer is synthetic.
The target is build 31 plus only a temporary neverForLocation BLUETOOTH_SCAN
manifest entry. No product pairing implementation is included.

For the private peer, build the same instrumentation targeting its own package,
with BLUETOOTH_CONNECT declared/granted. Run `-e mode peer` to set its synthetic
name, then launch REQUEST_DISCOVERABLE and accept its dialog. On the assigned
Device Owner emulator, turn Location off, install the probe with `-r -t`, then
run `am instrument -w -e pair true org.plainphone.spike434/.BluetoothProbe`.
Every adb command must select its owned emulator explicitly. The probe grants
SCAN to the Device Owner, reads permission/Location/lock task back, scans,
optionally requests a bond to that one synthetic peer, and waits 60 seconds.
Never confirm access to contacts/call history. Restore build 31 and original
Location afterward before the real 31-to-32 upgrade gate.

Evidence: `docs/previews/preview-32/probe.txt`.
