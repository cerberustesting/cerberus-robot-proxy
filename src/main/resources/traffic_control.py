from mitmproxy import ctx, http
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
from datetime import datetime
import os
import tempfile
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

class FlowRecord:
    """
    What is kept in memory for a captured flow: only what the filters need. The HAR entry itself
    is written to disk as soon as the flow is complete, and the flow is released.
    """
    __slots__ = ("flow", "url", "method", "host", "started", "offset", "length", "inline")

    def __init__(self, flow):
        req = flow.request
        self.flow = flow          # only until the flow is complete
        self.url = req.pretty_url
        self.method = req.method
        self.host = req.host
        self.started = req.timestamp_start
        self.offset = None        # position of the HAR entry in the HAR file
        self.length = 0
        self.inline = None        # HAR entry kept in memory if it could not be written to disk


class TrafficControl:
    def __init__(self):
        self.records = []
        self.lock = threading.Lock()   # guards records and the HAR file
        self.har_path = None
        self.har_fh = None
        self.har_size = 0
        self.log_fh = None
        self.log_lock = threading.Lock()

    def load(self, loader):
        loader.add_option(
            "traffic_log_dir", str, "",
            "Directory (mounted, shared with the app) to append real-time JSONL traffic logs. Empty disables it."
        )
        loader.add_option(
            "har_dir", str, "",
            "Directory where the captured HAR entries are kept for the session. Empty means the system temp directory."
        )
        loader.add_option(
            "session_uuid", str, "",
            "Session UUID used to name the real-time JSONL traffic log file and the HAR file."
        )
        loader.add_option(
            "api_port", int, 9999,
            "TCP port for TrafficControl's embedded REST API (getHar/getStats/reset)."
        )

    def running(self):
        with self.lock:
            self._open_har()
        threading.Thread(target=start_api, args=(ctx.options.api_port,), daemon=True).start()

    # ---- HAR file ------------------------------------------------------

    def _open_har(self):
        """(Re)create an empty HAR file. Called with the lock held."""
        try:
            if self.har_path is None:
                base = ctx.options.har_dir or tempfile.gettempdir()
                name = ctx.options.session_uuid or f"session-{os.getpid()}"
                os.makedirs(base, exist_ok=True)
                self.har_path = os.path.join(base, f"{name}.har.jsonl")
            # Unlink rather than truncate: a /har still reading the previous file keeps its inode.
            if os.path.exists(self.har_path):
                os.remove(self.har_path)
            self.har_fh = open(self.har_path, "wb", buffering=1024 * 1024)
            self.har_size = 0
        except Exception as e:
            self.har_fh = None
            ctx.log.error(f"Cannot open HAR file, entries will be kept in memory: {e}")

    def _close_har(self):
        if self.har_fh:
            try:
                self.har_fh.close()
            except Exception:
                pass
            self.har_fh = None

    def reset(self):
        with self.lock:
            self.records.clear()
            self._close_har()
            self._open_har()

    # ---- capture -------------------------------------------------------

    def request(self, flow: http.HTTPFlow):
        with step_state.lock:
            if step_state.current:
                flow.metadata["step"] = step_state.current["id"]

        rec = FlowRecord(flow)
        flow.metadata["cerberus_rec"] = rec
        with self.lock:
            self.records.append(rec)

    def response(self, flow: http.HTTPFlow):
        entry = self._complete(flow)
        if entry is not None:
            self._write_log_entry(entry)

    def error(self, flow: http.HTTPFlow):
        self._complete(flow)

    def _complete(self, flow):
        """Write the HAR entry of a finished flow to disk and release the flow."""
        rec = flow.metadata.get("cerberus_rec")
        if rec is None or rec.flow is None:
            return None

        entry = build_har_entry(flow, mode="full")
        line = json.dumps(entry).encode("utf-8")

        with self.lock:
            if rec.flow is None:
                return None
            if self.har_fh:
                try:
                    self.har_fh.write(line)
                    self.har_fh.write(b"\n")
                    rec.offset = self.har_size
                    rec.length = len(line)
                    self.har_size += len(line) + 1
                except Exception as e:
                    ctx.log.error(f"Cannot write HAR entry, keeping it in memory: {e}")
                    self.har_fh = None
            if rec.offset is None:
                rec.inline = line
            rec.flow = None

        flow.metadata.pop("cerberus_rec", None)
        return entry

    def _write_log_entry(self, entry):
        log_dir = ctx.options.traffic_log_dir
        if not log_dir:
            return

        try:
            with self.log_lock:
                if self.log_fh is None:
                    os.makedirs(log_dir, exist_ok=True)
                    uuid = ctx.options.session_uuid or "session"
                    self.log_fh = open(os.path.join(log_dir, f"{uuid}.jsonl"), "a", buffering=1)

                self.log_fh.write(json.dumps(entry) + "\n")
        except Exception as e:
            ctx.log.error(f"Failed to write real-time traffic log: {e}")

    # ---- read ----------------------------------------------------------

    def select(self, params):
        """
        The records matching the filters, as a list of (started, source) where source is the
        serialized HAR entry or its (offset, length) in the HAR file, plus a reader on that file.
        Flows still in progress are serialized as they are now. The reader must be closed by the caller.
        """
        with self.lock:
            selected = [r for r in self.records if matches(r, params)]
            items = []
            for r in selected:
                if r.flow is not None:
                    items.append(json.dumps(build_har_entry(r.flow, mode="full")).encode("utf-8"))
                elif r.inline is not None:
                    items.append(r.inline)
                else:
                    items.append((r.offset, r.length))
            reader = None
            if any(isinstance(i, tuple) for i in items):
                self.har_fh.flush()
                reader = open(self.har_path, "rb")
            started = selected[0].started if selected else None
        return started, items, reader

    def count(self, params):
        with self.lock:
            return sum(1 for r in self.records if matches(r, params))

    def done(self):
        with self.lock:
            self._close_har()
            if self.har_path:
                try:
                    os.remove(self.har_path)
                except OSError:
                    pass
        with self.log_lock:
            if self.log_fh:
                self.log_fh.close()
                self.log_fh = None

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

def matches(rec, params):
    if "contains" in params and params["contains"] not in rec.url:
        return False
    if "method" in params and params["method"].upper() != rec.method:
        return False
    if "host" in params and params["host"] != rec.host:
        return False
    return True

# ============================================================
# HAR builder (Chrome compatible)
# ============================================================

def build_har_entry(f, mode="full", page_id="page_1"):
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

    return entry


def render_entry(raw, mode):
    """The serialized HAR entry as asked by the mode of the /har call."""
    if mode == "requests":
        entry = json.loads(raw)
        entry.pop("response", None)
        return json.dumps(entry).encode("utf-8")
    if mode == "noresponse":
        entry = json.loads(raw)
        if "response" in entry:
            entry["response"]["content"]["text"] = ""
        return json.dumps(entry).encode("utf-8")
    return raw


def har_head(started):
    """The HAR up to the opening of its entries list, which the caller streams and closes with HAR_TAIL."""
    har = {
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
                "startedDateTime": iso_time(started if started is not None else time.time()),
                "id": "page_1",
                "title": "Captured by mitmproxy",
                "pageTimings": {
                    "onContentLoad": -1,
                    "onLoad": -1
                }
            }],
            "entries": []
        }
    }
    return json.dumps(har)[:-len(HAR_TAIL)].encode("utf-8")

HAR_TAIL = "]}}"

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
                traffic.reset()
                self.reply(200, {"status": "OK"})
                return

            # STATS
            if path == "/stats":
                self.reply(200, {"hits": traffic.count(params)})
                return

            # HAR
            if path == "/har":
                self.stream_har(params)
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

    def stream_har(self, params):
        """
        Writes the HAR entry by entry, straight from the HAR file: a HAR of several tens of MB
        is never held in memory.
        """
        mode = params.get("mode", "full")
        started, items, reader = traffic.select(params)
        try:
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            try:
                self.wfile.write(har_head(started))
                for i, item in enumerate(items):
                    if isinstance(item, tuple):
                        reader.seek(item[0])
                        item = reader.read(item[1])
                    if i:
                        self.wfile.write(b",")
                    self.wfile.write(render_entry(item, mode))
                self.wfile.write(HAR_TAIL.encode("utf-8"))
            except Exception as e:
                # The status is already sent: all that is left is to cut the connection short.
                ctx.log.error(f"HAR streaming interrupted: {e}")
        finally:
            if reader:
                reader.close()

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

def start_api(port):
    server = HTTPServer(("0.0.0.0", port), ApiHandler)
    ctx.log.info(f"TrafficControl API listening on port {port}")
    server.serve_forever()

# ============================================================
# mitmproxy registration
# ============================================================

addons = [traffic]