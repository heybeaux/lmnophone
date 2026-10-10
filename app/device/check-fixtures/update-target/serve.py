# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
"""Host side of UpdateInstallerCheck: serves OUT on PORT and answers every request on REDIRECT with a
302 to PORT/v3.apk. Usage: python3 -I serve.py OUT PORT REDIRECT. Logs each request line to stdout."""
import functools, http.server, sys, threading

out, port, redirect = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])

class Files(http.server.SimpleHTTPRequestHandler):
    def log_message(self, fmt, *a):
        print(f"serve :{port} {self.requestline} -> {a[1] if len(a) > 1 else ''}", flush=True)

class Redirect(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        print(f"redirect :{redirect} {self.requestline} -> 302", flush=True)
        self.send_response(302)
        self.send_header("Location", f"http://10.0.2.2:{port}/v3.apk")
        self.send_header("Content-Length", "0")
        self.end_headers()
    def log_message(self, *a): pass

files = http.server.ThreadingHTTPServer(("127.0.0.1", port), functools.partial(Files, directory=out))
redir = http.server.ThreadingHTTPServer(("127.0.0.1", redirect), Redirect)
threading.Thread(target=redir.serve_forever, daemon=True).start()
files.serve_forever()
