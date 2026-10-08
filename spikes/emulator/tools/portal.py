# THROWAWAY SPIKE (PLA-11). Fake captive-portal probe target. Mode file: "open" -> 204, "captive" -> 302.
import http.server, sys, os
MODE = sys.argv[2]
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        mode = open(MODE).read().strip() if os.path.exists(MODE) else "open"
        if mode == "captive" and not self.path.startswith("/login"):
            self.send_response(302); self.send_header("Location", "http://10.0.2.2:%s/login" % sys.argv[1]); self.end_headers()
        elif self.path.startswith("/login"):
            self.send_response(200); self.end_headers(); self.wfile.write(b"<html>Sign in</html>")
        else:
            self.send_response(204); self.end_headers()
        sys.stderr.write("%s %s -> %s\n" % (mode, self.path, "302" if mode == "captive" else "204")); sys.stderr.flush()
    do_HEAD = do_GET
http.server.ThreadingHTTPServer(("0.0.0.0", int(sys.argv[1])), H).serve_forever()
