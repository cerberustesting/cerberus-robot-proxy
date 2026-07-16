from mitmproxy import ctx, http
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
from datetime import datetime
import threading
import json
import time
import mitmproxy.version

# ============================================================
# Step state (proxy-driven tagging)
# ============================================================

class StepState:
    def __init__(self):
        self.current = None
        self.history = []
        self.lock = threading.Lock()

step_state = StepState()

# ============================================================
# Traffic capture (mitmproxy addon)
# ============================================================

class TrafficControl:
    def __init__(self):
        self.flows = []
        self.lock = threading.Lock()

    def request(self, flow: http.HTTPFlow):
        with self.lock:
            with step_state.lock:
                 if step_state.current:
                      flow.metadata["step"] = step_state.current["id"]

            self.flows.append(flow)

traffic = TrafficControl()

# ============================================================
# Helpers
# ============================================================

def iso_time(ts):
    return datetime.utcfromtimestamp(ts).isoformat() + "Z"

def safe_text(message):
    try:
        return message.get_text()
    except Exception:
        return ""

# ============================================================
# Filtering
# ============================================================

def filter_flows(flows, params):
    def match(flow):
        req = flow.request
        if "contains" in params and params["contains"] not in req.pretty_url:
            return False
        if "method" in params and params["method"].upper() != req.method:
            return False
        if "host" in params and params["host"] != req.host:
            return False
        return True

    return [f for f in flows if match(f)]

# ============================================================
# HAR builder (Chrome compatible)
# ============================================================

def build_har(flows, mode="full"):
    entries = []
    page_id = "page_1"

    for f in flows:
        req = f.request
        resp = f.response

        # timing
        duration = 0
        if req.timestamp_end:
            duration = int((req.timestamp_end - req.timestamp_start) * 1000)

        entry = {
            "startedDateTime": iso_time(req.timestamp_start),
            "time": duration,

            "request": {
                "method": req.method,
                "url": req.pretty_url,
                "httpVersion": "HTTP/1.1",
                "cookies": [],
                "headers": [{"name": k, "value": v} for k, v in req.headers.items()],
                "queryString": [{"name": k, "value": v} for k, v in req.query.items()],
                "headersSize": -1,
                "bodySize": len(req.raw_content or b""),
            },

            "cache": {},
            "timings": {
                "blocked": -1,
                "dns": -1,
                "connect": -1,
                "send": 0,
                "wait": 0,
                "receive": 0,
                "ssl": -1
            },

            "pageref": page_id
        }

        # POST body
        body = safe_text(req)
        if body:
            entry["request"]["postData"] = {
                "mimeType": req.headers.get("Content-Type", ""),
                "text": body
            }

        # RESPONSE
        if mode != "requests":
            entry["response"] = {
                "status": resp.status_code if resp else 0,
                "statusText": resp.reason if resp else "",
                "httpVersion": "HTTP/1.1",
                "cookies": [],
                "headers": [{"name": k, "value": v} for k, v in (resp.headers.items() if resp else [])],
                "content": {
                    "size": len(resp.raw_content or b"") if resp else 0,
                    "mimeType": resp.headers.get("Content-Type", "") if resp else "",
                    "text": "" if mode == "noresponse" else safe_text(resp)
                },
                "redirectURL": "",
                "headersSize": -1,
                "bodySize": len(resp.raw_content or b"") if resp else 0
            }

        entry["cerberus"] = {
             "step": f.metadata.get("step")
        }

        entries.append(entry)

    return {
        "log": {
            "version": "1.2",
            "creator": {
                "name": "mitmproxy",
                "version": mitmproxy.version.VERSION
            },
            "browser": {
                "name": "Chrome",
                "version": "Unknown"
            },
            "pages": [{
                "startedDateTime": iso_time(flows[0].request.timestamp_start) if flows else iso_time(time.time()),
                "id": page_id,
                "title": "Captured by mitmproxy",
                "pageTimings": {
                    "onContentLoad": -1,
                    "onLoad": -1
                }
            }],
            "entries": entries
        }
    }

# ============================================================
# Embedded REST API
# ============================================================

class ApiHandler(BaseHTTPRequestHandler):

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        params = {k: v[0] for k, v in parse_qs(parsed.query).items()}

        try:
            # RESET
            if path == "/reset":
                with traffic.lock:
                    traffic.flows.clear()
                self.reply(200, {"status": "OK"})
                return

            # STATS
            if path == "/stats":
                with traffic.lock:
                    filtered = filter_flows(traffic.flows, params)
                self.reply(200, {"hits": len(filtered)})
                return

            # HAR
            if path == "/har":
                mode = params.get("mode", "full")
                with traffic.lock:
                    filtered = filter_flows(traffic.flows, params)
                    har = build_har(filtered, mode)
                self.reply(200, har)
                return

            # Steps
            if path == "/step":
                step_id = params.get("id", "default")
                with step_state.lock:
                    step_state.current = {
                        "id": step_id,
                        "start": time.time(),
                        "end": None
                    }
                self.reply(200, {"status": "started", "step": step_id})
                return

            self.reply(404, {"error": "Unknown endpoint"})

        except Exception as e:
            ctx.log.error(f"API error: {e}")
            self.reply(500, {"error": str(e)})

    def reply(self, code, payload):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(payload).encode())

    def log_message(self, *_):
        return

# ============================================================
# API thread
# ============================================================

def start_api():
    server = HTTPServer(("0.0.0.0", 9999), ApiHandler)
    ctx.log.info("TrafficControl API listening on port 9999")
    server.serve_forever()

threading.Thread(target=start_api, daemon=True).start()

# ============================================================
# mitmproxy registration
# ============================================================

addons = [traffic]