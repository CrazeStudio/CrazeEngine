"""Security and privacy controls for CrazeEngine.

The crawler is deliberately conservative: only HTTP(S) URLs are accepted,
private/local destinations are rejected, downloads are size-limited, and
redirects are validated before being followed.
"""

from __future__ import annotations

import ipaddress
import socket
from urllib.parse import urlparse

ALLOWED_SCHEMES = {"http", "https"}
MAX_URL_LENGTH = 2048
BLOCKED_HOSTNAMES = {"localhost", "localhost.localdomain"}


def validate_url(url: str) -> str:
    """Validate a URL before any network request is made.

    Returns the normalized URL or raises ValueError. This is an SSRF defense,
    not a guarantee that a hostile network cannot change DNS after validation.
    """
    if not isinstance(url, str) or len(url) > MAX_URL_LENGTH:
        raise ValueError("invalid URL")

    parsed = urlparse(url.strip())
    if parsed.scheme.lower() not in ALLOWED_SCHEMES:
        raise ValueError("only http and https URLs are allowed")
    if not parsed.hostname:
        raise ValueError("URL has no hostname")

    host = parsed.hostname.rstrip(".").lower()
    if host in BLOCKED_HOSTNAMES:
        raise ValueError("local hostname is blocked")

    try:
        addresses = socket.getaddrinfo(host, parsed.port or (443 if parsed.scheme == "https" else 80), type=socket.SOCK_STREAM)
    except (socket.gaierror, OSError):
        raise ValueError("hostname could not be resolved") from None

    for address in addresses:
        ip = ipaddress.ip_address(address[4][0])
        if (
            ip.is_private
            or ip.is_loopback
            or ip.is_link_local
            or ip.is_multicast
            or ip.is_reserved
            or ip.is_unspecified
        ):
            raise ValueError("private or non-public destination is blocked")

    return parsed.geturl()


def validate_redirect(url: str) -> str:
    """Validate a redirect target using the same SSRF rules."""
    return validate_url(url)
