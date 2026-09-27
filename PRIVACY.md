# CrazeEngine Privacy Model

CrazeEngine V1 is designed to minimize data collection.

## No search profiling

CrazeEngine does not require accounts and the core engine does not persist user search queries, IP addresses, user-agent strings, or personal identifiers.

## Search index

The index stores public webpage data needed for search: URL, page title, extracted text, and search metadata. It is not intended to contain visitor identity data.

## Crawler

The crawler sends only the information needed to retrieve a public HTTP(S) page. It uses a fixed, non-personal user-agent and bounded requests.

## Security boundaries

- Only HTTP and HTTPS URLs are accepted.
- Localhost, loopback, private, link-local, multicast, reserved, and unspecified IP destinations are rejected.
- Redirect targets are validated too.
- Response bodies have a hard size limit.
- Requests have a timeout.
- Only HTML/XHTML is indexed.
- The search layer does not execute returned HTML or JavaScript.

## Important limitation

No software can honestly guarantee perfect privacy or security. Hosting configuration, TLS termination, reverse proxies, operating-system logs, CDN logs, DNS logs, and third-party infrastructure can still collect data outside the core CrazeEngine process. Operators should disable unnecessary access logs and avoid analytics or third-party tracking.

## Operator responsibility

Only crawl content you are permitted to access. Respect applicable robots.txt policies, website terms, copyright rules, rate limits, and local law before operating a larger crawler.
