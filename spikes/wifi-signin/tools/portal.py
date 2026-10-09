# THROWAWAY SPIKE (PLA-18). Fake sign-in Wi-Fi for the emulator.
# Usage: python3 -I portal.py <port> <modefile>
# Mode file: "captive" -> probes get 302 to /login; "open" -> probes get 204. /accept flips captive -> open.
# /login is a reach-test page: each link probes a way out of the sign-in screen.
import http.server, sys, os

PORT = int(sys.argv[1]); MODE = sys.argv[2]
BASE = "http://10.0.2.2:%d" % PORT

LOGIN = """<!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1">
<title>Ferry Wi-Fi</title></head><body style="font-size:18px">
<h2>Ferry Wi-Fi (fake portal)</h2>
<p><a id=accept href="/accept">1 Accept and connect</a></p>
<p><a href="https://example.com/">2 Outside site (example.com)</a></p>
<p><button onclick="location.href='https://en.wikipedia.org/wiki/Ferry'">3 JS redirect to Wikipedia</button></p>
<p><a href="https://www.bbc.com/" target="_blank">4 target=_blank (bbc.com)</a></p>
<p>5 File picker: <input type=file id=f></p>
<p><a href="intent://www.youtube.com/#Intent;scheme=https;package=com.android.chrome;end">6 intent: link to Chrome</a></p>
<p><a href="googlechrome://navigate?url=https://example.org">7 googlechrome: scheme</a></p>
<p><a href="market://details?id=com.whatsapp">8 market: link</a></p>
<p><a href="tel:5551234">9 tel: link</a></p>
<p><a href="mailto:a@example.com">10 mailto: link</a></p>
<p><a href="/download.pdf">11 Download a PDF</a></p>
<p><a href="https://www.google.com/search?q=plainphone">12 Google search</a></p>
<p><button onclick="window.open('https://www.mozilla.org/','_blank')">13 window.open</button></p>
<p><a href="intent:#Intent;action=android.settings.SETTINGS;end">14 intent: Settings</a></p>
</body></html>"""

ACCEPTED = """<!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1"></head>
<body style="font-size:18px"><h2>You're online</h2><p><a href="https://example.com/">Continue to example.com</a></p></body></html>"""

PDF = b"%PDF-1.1\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n"

class H(http.server.BaseHTTPRequestHandler):
    def send(self, code, body=b"", ctype="text/html", extra=()):
        self.send_response(code)
        for k, v in extra: self.send_header(k, v)
        if body: self.send_header("Content-Type", ctype); self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body and self.command != "HEAD": self.wfile.write(body)

    def do_GET(self):
        mode = open(MODE).read().strip() if os.path.exists(MODE) else "open"
        p = self.path
        if p.startswith("/login"): self.send(200, LOGIN.encode())
        elif p.startswith("/accept"):
            open(MODE, "w").write("open"); self.send(200, ACCEPTED.encode())
        elif p.startswith("/download.pdf"):
            self.send(200, PDF, "application/pdf", [("Content-Disposition", "attachment; filename=ferry.pdf")])
        elif mode == "captive": self.send(302, extra=[("Location", BASE + "/login")])
        else: self.send(204)
        sys.stderr.write("%s %s %s ua=%s\n" % (mode, self.command, p, self.headers.get("User-Agent", "")[:60])); sys.stderr.flush()
    do_HEAD = do_GET

http.server.ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
