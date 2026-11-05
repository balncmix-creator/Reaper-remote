"""REAPER Remote Windows companion — authenticated loopback API.

Standard-library-only preview server. Expose privately with Tailscale Serve,
never by router port forwarding. The REAPER Lua bridge polls /internal/state
and /internal/commands on localhost.
"""
from __future__ import annotations

import json
import os
import secrets
import threading
import time
import uuid
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse

HOST = "127.0.0.1"
PORT = int(os.environ.get("REAPER_REMOTE_PORT", "8765"))
TOKEN = os.environ.get("REAPER_REMOTE_TOKEN", "")
PROJECTS = Path(os.environ.get("REAPER_REMOTE_PROJECTS", str(Path.home() / "Documents" / "REAPER Projects"))).resolve()
ROOT = Path(__file__).resolve().parent
STATE_FILE = ROOT / "reaper_state.json"
COMMAND_FILE = ROOT / "commands.json"
MAX_UPLOAD = 2 * 1024 * 1024 * 1024  # 2 GiB per upload
LOCK = threading.Lock()


def read_json(path: Path, fallback):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return fallback


def write_json(path: Path, value):
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
    tmp.replace(path)


def enqueue(name: str, args=None):
    command = {"id": uuid.uuid4().hex, "name": name, "args": args or {}, "created": time.time()}
    with LOCK:
        queue = read_json(COMMAND_FILE, [])
        queue.append(command)
        write_json(COMMAND_FILE, queue)
    return command["id"]


def safe_project_path(relative: str) -> Path:
    candidate = (PROJECTS / relative).resolve()
    if candidate != PROJECTS and PROJECTS not in candidate.parents:
        raise ValueError("Path escapes the configured projects directory")
    return candidate


class Handler(BaseHTTPRequestHandler):
    server_version = "ReaperRemote/0.2"

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.address_string(), fmt % args))

    def send_json(self, code, data):
        raw = json.dumps(data).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(raw)

    def authorized(self):
        supplied = self.headers.get("Authorization", "")
        expected = "Bearer " + TOKEN
        return bool(TOKEN) and secrets.compare_digest(supplied, expected)

    def body_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length > 1024 * 1024:
            raise ValueError("JSON request too large")
        return json.loads(self.rfile.read(length) or b"{}")

    def do_GET(self):
        if self.path == "/health":
            return self.send_json(200, {"ok": True, "service": "REAPER Remote companion"})
        if not self.authorized():
            return self.send_json(401, {"error": "Unauthorized"})
        path = urlparse(self.path).path
        if path == "/api/status":
            return self.send_json(200, read_json(STATE_FILE, {"connected": False, "projects": [], "transport": {"state": "unknown"}}))
        if path == "/api/projects":
            state = read_json(STATE_FILE, {})
            return self.send_json(200, {"projects": state.get("projects", []), "activeProject": state.get("activeProject")})
        if path == "/api/render/settings":
            return self.send_json(200, {"settings": read_json(ROOT / "render_settings.json", {})})
        if path.startswith("/api/files/"):
            rel = unquote(path[len("/api/files/"):])
            try:
                target = safe_project_path(rel)
                if not target.is_file():
                    return self.send_json(404, {"error": "File not found"})
                size = target.stat().st_size
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Length", str(size))
                self.send_header("Content-Disposition", 'attachment; filename="' + target.name.replace('"', "") + '"')
                self.end_headers()
                with target.open("rb") as f:
                    while chunk := f.read(1024 * 1024):
                        self.wfile.write(chunk)
                return
            except ValueError as exc:
                return self.send_json(400, {"error": str(exc)})
        return self.send_json(404, {"error": "Not found"})

    def do_POST(self):
        if not self.authorized():
            return self.send_json(401, {"error": "Unauthorized"})
        path = urlparse(self.path).path
        try:
            if path == "/api/commands":
                data = self.body_json()
                name = data.get("command")
                allowed = {"play", "pause", "stop", "record", "next_marker", "prev_marker", "project_activate", "project_open", "project_close", "render_start"}
                if name not in allowed:
                    return self.send_json(400, {"error": "Unsupported command"})
                command_id = enqueue(name, data.get("args", {}))
                return self.send_json(202, {"queued": True, "commandId": command_id})
            if path == "/api/render/settings":
                settings = self.body_json()
                # Store the requested settings for the ReaScript bridge; validation is expanded with render UI work.
                write_json(ROOT / "render_settings.json", settings)
                enqueue("render_settings", settings)
                return self.send_json(202, {"saved": True})
            if path == "/api/upload":
                length = int(self.headers.get("Content-Length", "0"))
                if length <= 0 or length > MAX_UPLOAD:
                    return self.send_json(413, {"error": "Upload must be between 1 byte and 2 GiB"})
                filename = Path(unquote(self.headers.get("X-File-Name", "upload.rpp"))).name
                if not (filename.lower().endswith(".rpp") or filename.lower().endswith(".zip")):
                    return self.send_json(400, {"error": "Only .RPP and .ZIP project uploads are accepted"})
                PROJECTS.mkdir(parents=True, exist_ok=True)
                dest = (PROJECTS / filename).resolve()
                if dest.parent != PROJECTS:
                    return self.send_json(400, {"error": "Invalid filename"})
                with dest.open("wb") as f:
                    remaining = length
                    while remaining:
                        chunk = self.rfile.read(min(1024 * 1024, remaining))
                        if not chunk:
                            raise ValueError("Upload ended unexpectedly")
                        f.write(chunk)
                        remaining -= len(chunk)
                if filename.lower().endswith(".zip"):
                    extract_to = (PROJECTS / (dest.stem + "_project")).resolve()
                    if PROJECTS not in extract_to.parents:
                        raise ValueError("Invalid extraction path")
                    extract_to.mkdir(parents=True, exist_ok=True)
                    with zipfile.ZipFile(dest) as archive:
                        for item in archive.infolist():
                            target = (extract_to / item.filename).resolve()
                            if extract_to not in target.parents and target != extract_to:
                                raise ValueError("ZIP contains an unsafe path")
                        archive.extractall(extract_to)
                    return self.send_json(201, {"uploaded": True, "file": str(dest), "extractedTo": str(extract_to.relative_to(PROJECTS))})
                return self.send_json(201, {"uploaded": True, "file": str(dest), "relativePath": filename})
            return self.send_json(404, {"error": "Not found"})
        except (ValueError, json.JSONDecodeError, zipfile.BadZipFile) as exc:
            return self.send_json(400, {"error": str(exc)})


if __name__ == "__main__":
    if not TOKEN:
        raise SystemExit("Set REAPER_REMOTE_TOKEN to a long random secret before starting.")
    PROJECTS.mkdir(parents=True, exist_ok=True)
    print(f"REAPER Remote companion listening on http://{HOST}:{PORT}")
    print(f"Projects directory: {PROJECTS}")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
