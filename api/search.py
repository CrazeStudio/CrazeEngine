import json
import os
from http.server import BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlencode, urlparse
from urllib.request import Request, urlopen

MAX_QUERY = 200
MAX_RESULTS = 10
TIMEOUT = 8


def clean(value, limit=500):
    return " ".join(str(value or "").split())[:limit]


def search(query, page=1):
    provider = os.environ.get("SEARCH_PROVIDER_URL", "").strip()
    if not provider or not provider.startswith("https://"):
        return {"query": query, "results": [], "error": "Search provider is not configured."}

    parsed = urlparse(provider)
    if parsed.username or parsed.password or parsed.fragment:
        return {"query": query, "results": [], "error": "Invalid search provider."}

    params = parse_qs(parsed.query, keep_blank_values=True)
    params["q"] = [query]
    params["format"] = ["json"]
    params["pageno"] = [str(max(1, min(page, 100)))]
    target = parsed._replace(query=urlencode(params, doseq=True), fragment="").geturl()

    req = Request(target, headers={"Accept": "application/json", "User-Agent": "CrazeEngine/1.0"})
    try:
        with urlopen(req, timeout=TIMEOUT) as response:
            if response.headers.get_content_type() != "application/json":
                return {"query": query, "results": [], "error": "Invalid provider response."}
            raw = response.read(2_000_001)
            if len(raw) > 2_000_000:
                return {"query": query, "results": [], "error": "Provider response too large."}
        data = json.loads(raw.decode("utf-8"))
    except Exception:
        return {"query": query, "results": [], "error": "Search temporarily unavailable."}

    results = []
    seen = set()
    for item in data.get("results", []):
        url = clean(item.get("url"), 2048)
        title = clean(item.get("title"), 300)
        snippet = clean(item.get("content") or item.get("snippet"), 600)
        u = urlparse(url)
        if u.scheme not in ("http", "https") or not u.netloc or url in seen:
            continue
        seen.add(url)
        results.append({"title": title or url, "url": url, "snippet": snippet})
        if len(results) >= MAX_RESULTS:
            break
    return {"query": query, "results": results}


class handler(BaseHTTPRequestHandler):
    def _send(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path != "/api/search":
            self._send(404, {"error": "Not found"})
            return
        q = parse_qs(parsed.query).get("q", [""])[0].strip()
        if not q:
            self._send(400, {"error": "Missing search query."})
            return
        if len(q) > MAX_QUERY:
            self._send(400, {"error": "Search query is too long."})
            return
        page_raw = parse_qs(parsed.query).get("page", ["1"])[0]
        try:
            page = int(page_raw)
        except ValueError:
            page = 1
        self._send(200, search(q, page))

    def log_message(self, *_):
        return
