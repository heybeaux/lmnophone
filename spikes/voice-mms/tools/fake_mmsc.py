#!/usr/bin/env python3
# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
# THROWAWAY (PLA-458): a fake MMSC on host loopback. The emulator reaches it as http://10.0.2.2:<port>/mms.
# It saves each POSTed PDU, prints a decode of it, and answers with an m-send-conf (Response-Status Ok).
import http.server, sys, os, time, hashlib

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 59580
OUT = sys.argv[2] if len(sys.argv) > 2 else "received"

def uintvar(b, i):
    v = 0
    while True:
        c = b[i]; i += 1
        v = (v << 7) | (c & 0x7F)
        if not c & 0x80:
            return v, i

def cstr(b, i):
    j = b.index(0, i)
    return b[i:j].decode("latin-1"), j + 1

def decode(pdu):
    """Decodes just enough of an m-send-req to list headers and parts."""
    out, i, hdr = [], 0, {}
    names = {0x8C: "Message-Type", 0x98: "Transaction-ID", 0x8D: "MMS-Version", 0x89: "From",
             0x97: "To", 0x84: "Content-Type", 0x85: "Date", 0x96: "Subject", 0x8A: "Message-Class",
             0x88: "Expiry", 0x86: "Delivery-Report", 0x90: "Read-Report", 0x8F: "Priority"}
    while i < len(pdu):
        h = pdu[i]; i += 1
        n = names.get(h, "0x%02X" % h)
        if h == 0x84:
            v = pdu[i]
            if v >= 0x80:
                hdr[n] = "well-known 0x%02X" % (v & 0x7F); i += 1
            elif v < 0x20:
                ln = v; i += 1
                if ln == 31:
                    ln, i = uintvar(pdu, i)
                hdr[n] = "general(%d bytes)" % ln; i += ln
            else:
                hdr[n], i = cstr(pdu, i)
            break
        v = pdu[i]
        if h in (0x98, 0x97, 0x96):
            hdr[n], i = cstr(pdu, i)
        elif h in (0x89, 0x85, 0x88):
            ln = v; i += 1
            if ln == 31:
                ln, i = uintvar(pdu, i)
            hdr[n] = pdu[i:i + ln].hex(); i += ln
        elif v >= 0x80:
            hdr[n] = "0x%02X" % v; i += 1
        else:
            hdr[n], i = cstr(pdu, i)
    out.append("headers: %r" % hdr)
    count, i = uintvar(pdu, i)
    out.append("parts: %d" % count)
    for k in range(count):
        hl, i = uintvar(pdu, i)
        dl, i = uintvar(pdu, i)
        headers = pdu[i:i + hl]; i += hl
        data = pdu[i:i + dl]; i += dl
        printable = "".join(chr(c) if 32 <= c < 127 else "." for c in headers)
        out.append("part %d: headers=%r dataLen=%d dataHead=%r sha256=%s" % (
            k, printable, dl, data[:6], hashlib.sha256(data).hexdigest()[:16]))
    return "\n".join(out), hdr

class H(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        os.makedirs(OUT, exist_ok=True)
        path = os.path.join(OUT, "pdu-%d.bin" % int(time.time() * 1000))
        with open(path, "wb") as f:
            f.write(body)
        print("POST %s from %s len=%d content-type=%s ua=%s" % (self.path, self.client_address[0], len(body),
              self.headers.get("Content-Type"), self.headers.get("User-Agent")), flush=True)
        text, hdr = decode(body)
        print(text, flush=True)
        tid = hdr.get("Transaction-ID", "x").encode()
        conf = bytes([0x8C, 0x81, 0x98]) + tid + b"\0" + bytes([0x8D, 0x92, 0x92, 0x80, 0x8B]) + b"fake-mmsc-1\0"
        self.send_response(200)
        self.send_header("Content-Type", "application/vnd.wap.mms-message")
        self.send_header("Content-Length", str(len(conf)))
        self.end_headers()
        self.wfile.write(conf)

    def log_message(self, *a):
        pass

if __name__ == "__main__":
    print("fake MMSC on 127.0.0.1:%d" % PORT, flush=True)
    http.server.HTTPServer(("127.0.0.1", PORT), H).serve_forever()
