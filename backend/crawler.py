"""Privacy-conscious, bounded web crawler for CrazeEngine."""

from __future__ import annotations

from html.parser import HTMLParser
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request, build_opener

from .security import validate_redirect, validate_url

USER_AGENT = "CrazeEngine/1.0 (+https://github.com/CrazeStudio/CrazeEngine)"
MAX_BYTES = 2 * 1024 * 1024
MAX_REDIRECTS = 5
TIMEOUT = 8


class LinkParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.title_parts: list[str] = []
        self.text_parts: list[str] = []
        self.links: list[str] = []
        self._in_title = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_dict = dict(attrs)
        if tag.lower() == "title":
            self._in_title = True
        if tag.lower() == "a" and attrs_dict.get("href"):
            self.links.append(attrs_dict["href"] or "")

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() == "title":
            self._in_title = False

    def handle_data(self, data: str) -> None:
        clean = " ".join(data.split())
        if not clean:
            return
        if self._in_title:
            self.title_parts.append(clean)
        self.text_parts.append(clean)


class SafeRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        validate_redirect(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


class Crawler:
    """Fetch and parse public HTTP(S) pages without collecting visitor data."""

    def __init__(self) -> None:
        self.opener = build_opener(SafeRedirectHandler())

    def fetch(self, url: str) -> dict:
        url = validate_url(url)
        request = Request(
            url,
            headers={"User-Agent": USER_AGENT, "Accept": "text/html,application/xhtml+xml;q=0.9"},
            method="GET",
        )
        try:
            with self.opener.open(request, timeout=TIMEOUT) as response:
                content_type = response.headers.get_content_type()
                if content_type not in {"text/html", "application/xhtml+xml"}:
                    return {"url": response.geturl(), "title": "", "text": "", "links": []}

                body = response.read(MAX_BYTES + 1)
                if len(body) > MAX_BYTES:
                    raise ValueError("response exceeds size limit")
                final_url = validate_redirect(response.geturl())
                charset = response.headers.get_content_charset() or "utf-8"
                text = body.decode(charset, errors="replace")
        except (HTTPError, URLError, TimeoutError, ValueError, OSError) as exc:
            raise RuntimeError("page fetch failed") from exc

        parser = LinkParser()
        parser.feed(text)
        return {
            "url": final_url,
            "title": " ".join(parser.title_parts)[:500],
            "text": " ".join(parser.text_parts)[:100_000],
            "links": parser.links[:200],
        }
