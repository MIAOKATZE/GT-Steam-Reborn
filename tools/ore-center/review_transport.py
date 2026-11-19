"""Loopback-only browser transport for the private native MUI2 session."""

from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json
import secrets
import threading
import time
from urllib.parse import urlsplit


class ReviewTransport:
    def __init__(self, evidence, asset_dir, port, source):
        self.evidence = evidence
        self.asset_dir = asset_dir
        self.origin = f"http://127.0.0.1:{port}"
        self.source = source
        self.token = secrets.token_urlsafe(24)
        self.lock = threading.Lock()
        self.sequence = 0
        self.stopping = False
        self.files = {
            "/": ("index.html", "text/html; charset=utf-8"),
            "/review.js": ("review.js", "text/javascript; charset=utf-8"),
            "/review.css": ("review.css", "text/css; charset=utf-8"),
        }

    def get_state(self):
        path = self.evidence / "review-state.json"
        if self.stopping:
            return {"status": "closing"}
        if path.exists():
            return {"status": "ready", **json.loads(path.read_text("utf-8"))}
        for name in ("client-checks.json", "server-checks.json"):
            failure = self.evidence / name
            if failure.exists():
                return {"status": "error", "error": json.loads(failure.read_text("utf-8")).get("error")}
        return {"status": "starting"}

    def actions(self, events):
        if not isinstance(events, list) or not 1 <= len(events) <= 32:
            raise ValueError("expected 1–32 input events")
        state = self.get_state()
        if state.get("status") != "ready":
            raise RuntimeError("native panel is still starting")
        for item in events:
            check_event(item, state)
        with self.lock:
            if state["seq"] != self.sequence and self.get_state().get("seq") != self.sequence:
                raise RuntimeError("previous input is still being rendered")
            self.sequence += 1
            self.write("review-input.json", {"seq": self.sequence, "events": events})
            return {"seq": self.sequence}

    def write(self, name, payload):
        tmp = self.evidence / (name + ".tmp")
        tmp.write_text(json.dumps(payload, ensure_ascii=False), "utf-8")
        tmp.replace(self.evidence / name)

    def request(self, handler, post=False):
        path = urlsplit(handler.path).path
        if handler.headers.get("Host") != self.origin.split("://", 1)[1]:
            return handler.data(403, b"", "text/plain")
        if not post and path in self.files:
            filename, kind = self.files[path]
            return handler.data(200, (self.asset_dir / filename).read_bytes(), kind)
        if not post and path == "/status":
            return handler.json(200, {**self.get_state(), "token": self.token, "source": self.source})
        if not post and path == "/frame":
            frame = self.evidence / "review-frame.png"
            if not frame.exists():
                return handler.data(404, b"", "text/plain")
            return handler.data(200, frame.read_bytes(), "image/png")
        if post and path in ("/input", "/stop"):
            if handler.headers.get("Origin") != self.origin or not secrets.compare_digest(
                handler.headers.get("X-Review-Session", ""), self.token
            ):
                return handler.data(403, b"", "text/plain")
            if path == "/stop":
                with self.lock:
                    self.stopping = True
                    self.write("stop.json", {"owner": "local-browser-review"})
                return handler.json(202, {"status": "closing"})
            try:
                size = int(handler.headers.get("Content-Length", "0"))
                if not 1 <= size <= 65536:
                    raise ValueError("input too large")
                data = json.loads(handler.rfile.read(size))
                return handler.json(202, self.actions(data))
            except (TypeError, ValueError) as error:
                return handler.json(400, {"error": str(error)})
            except RuntimeError as error:
                return handler.json(409, {"error": str(error)})
        return handler.data(404, b"", "text/plain")

    @contextmanager
    def open(self):
        transport = self

        class Handler(BaseHTTPRequestHandler):
            def setup(self):
                self.request.settimeout(4)
                super().setup()

            def do_GET(self):
                transport.request(self)

            def do_POST(self):
                transport.request(self, post=True)

            def log_message(self, *_):
                pass

            def json(self, status, value):
                self.data(status, json.dumps(value, ensure_ascii=False).encode("utf-8"), "application/json")

            def data(self, status, body, kind):
                self.send_response(status)
                self.send_header("Content-Type", kind)
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Cache-Control", "no-store")
                self.send_header("X-Content-Type-Options", "nosniff")
                self.send_header("Content-Security-Policy",
                                 "default-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self' file:")
                self.end_headers()
                try:
                    self.wfile.write(body)
                except (BrokenPipeError, ConnectionResetError):
                    pass

        port = int(self.origin.rsplit(":", 1)[1])
        with ThreadingHTTPServer(("127.0.0.1", port), Handler) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                yield self
            finally:
                server.shutdown()
                worker.join()


def integer(item, name, low, high):
    value = item.get(name)
    if type(value) is not int or not low <= value <= high:
        raise ValueError(f"invalid {name}")


def check_event(item, state):
    if not isinstance(item, dict):
        raise ValueError("expected an input event")
    kind = item.get("type")
    if kind not in ("move", "down", "up", "wheel", "key", "text", "leave"):
        raise ValueError("unsupported input event")
    if "mod" in item:
        integer(item, "mod", 0, 7)
    if kind in ("move", "down", "up", "wheel"):
        integer(item, "x", 0, state["width"] - 1)
        integer(item, "y", 0, state["height"] - 1)
    if kind in ("down", "up"):
        integer(item, "button", 0, 2)
    if kind == "wheel":
        integer(item, "steps", -480, 480)
    if kind == "key":
        integer(item, "key", 0, 255)
        if "char" in item:
            integer(item, "char", 0, 65535)
    if kind == "text" and (not isinstance(item.get("text"), str) or not 1 <= len(item["text"]) <= 8192):
        raise ValueError("invalid text length")
