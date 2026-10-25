"""本地模拟 OpenAI 兼容的图片接口，只为在模拟器里跑通 UI 流程。

- GET  /v1/models               返回两个模型
- POST /v1/images/generations   延时 2 秒后返回一张渐变 PNG 的 b64_json
"""
import base64
import json
import struct
import sys
import time
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def make_png(width: int = 1024, height: int = 1024, warm: bool = False) -> bytes:
    raw = bytearray()
    for y in range(height):
        t = y / (height - 1)
        if warm:
            r = int(210 - 70 * t)
            g = int(120 - 50 * t)
            b = int(60 + 40 * t)
        else:
            r = int(12 + 60 * t)
            g = int(20 + 30 * t)
            b = int(70 + 140 * t)
        raw.append(0)
        raw.extend(bytes((r, g, b)) * width)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 6)) + chunk(b"IEND", b""))


PNG_B64 = base64.b64encode(make_png()).decode()
EDITED_B64 = base64.b64encode(make_png(warm=True)).decode()

_PNG_CACHE: dict = {}


def png_for(size: str, warm: bool = False) -> str:
    """按请求的 size 生成同比例的图（长边压到 1024，只验证比例与布局，不烧 CPU）。"""
    width = height = 1024
    try:
        parts = size.lower().replace("*", "x").split("x")
        request_w, request_h = int(parts[0]), int(parts[1])
    except Exception:
        request_w = request_h = 0
    if request_w > 0 and request_h > 0:
        long_edge = 1024
        if request_w >= request_h:
            width = long_edge
            height = max(64, round(long_edge * request_h / request_w))
        else:
            height = long_edge
            width = max(64, round(long_edge * request_w / request_h))
    key = (width, height, warm)
    if key not in _PNG_CACHE:
        _PNG_CACHE[key] = base64.b64encode(make_png(width, height, warm=warm)).decode()
    return _PNG_CACHE[key]


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.command, self.path))

    def _json(self, payload, code=200):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.rstrip("/").endswith("/models"):
            self._json({"data": [{"id": "gpt-image-2"}, {"id": "gpt-image-2.5-flare"}]})
        else:
            self._json({"error": {"message": "not found"}}, 404)

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        prompt = ""
        size = ""
        try:
            payload = json.loads(body.decode("utf-8"))
            prompt = str(payload.get("prompt") or "")
            size = str(payload.get("size") or "")
        except Exception:
            pass
        # 提示词里带 "slow" 就拖 30 秒，用来测前台服务保活
        delay = 30 if "slow" in prompt.lower() else 2
        if "/images/generations" in self.path:
            time.sleep(delay)
            self._json({"created": int(time.time()), "data": [{"b64_json": png_for(size)}]})
        elif "/images/edits" in self.path:
            time.sleep(delay)
            self._json({"created": int(time.time()), "data": [{"b64_json": png_for(size, warm=True)}]})
        else:
            self._json({"error": {"message": "not found"}}, 404)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
