# CrazeEngine Security Policy

## V1 security baseline

CrazeEngine V1 uses conservative network and input controls:

- SSRF protection for crawler URLs and redirects
- HTTP/HTTPS scheme allowlist
- private/local IP rejection
- URL length limits
- request timeouts
- response-size limits
- HTML-only indexing
- bounded extracted text and links
- parameterized SQLite queries
- conservative FTS query tokenization
- no execution of downloaded page scripts
- no persistence of visitor search history in the core engine

## Threats considered

- SSRF against local services
- redirect-based SSRF
- oversized responses / memory exhaustion
- malicious search input
- SQL injection
- FTS operator abuse
- untrusted HTML execution
- accidental privacy logging

## Remaining risks

DNS can change after validation (DNS rebinding). A production deployment should isolate the crawler in a restricted network namespace/container and apply outbound firewall policy. Run the service as a non-root user with a read-only filesystem where practical.

A production deployment should also add:

- dependency pinning and automated vulnerability scanning
- TLS at the edge
- strict outbound network policy
- rate limiting
- resource quotas
- process isolation
- regular security review

## Reporting

Do not publish sensitive vulnerability details in a public issue. Contact the project maintainer privately through the repository's available security channel.
