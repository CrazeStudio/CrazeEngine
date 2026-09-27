# CrazeEngine

**CrazeEngine V1** is a privacy-first web-search frontend and secure search gateway for FluXBrowser.

## V1 goals

- Clean, fast search homepage
- Web links, titles and snippets only
- No accounts
- No application-level search history
- No analytics or tracking scripts
- No ads injected by CrazeEngine
- Same-origin search API
- Strict HTTPS provider requirement
- Query and result-size limits
- No redirects through the search gateway
- Security headers and `no-store` responses
- Pluggable search provider

## Architecture

```text
FluXBrowser / Web
        |
        v
Cloudflare Worker
   |          |
   |          +--> Static frontend
   |
   +--> /api/search
          |
          v
Configured search provider
          |
          v
     links + snippets
```

The Worker does not build a search index itself. V1 uses a configured search provider. A future independent crawler/index can replace that provider without changing the frontend contract.

## Cloudflare Workers

Cloudflare Workers is a good fit for the **frontend and lightweight search gateway**. Static assets are served from `public/`, while `src/worker.js` handles `/api/search` and `/api/health`.

Set `SEARCH_PROVIDER_URL` to an HTTPS endpoint that accepts `q`, `format=json`, `language`, `safesearch`, and `pageno` parameters and returns a SearXNG-compatible JSON response.

Do not put provider secrets in `wrangler.toml`; use Cloudflare secrets/variables where appropriate.

## Privacy model

CrazeEngine does not intentionally create user profiles or store search history in application code. The frontend has no analytics, cookies, tracking pixels, or third-party client scripts.

Privacy has limits: Cloudflare and the configured upstream provider are separate infrastructure operators and may process network metadata or requests under their own policies. CrazeEngine therefore does **not** claim anonymous or zero-log operation at the infrastructure level.

## Security model

The gateway validates query length, enforces same-origin browser requests, requires HTTPS for the configured provider, disables redirects, validates provider response content type, bounds result count/text, normalizes result URLs, removes duplicates, and sends restrictive security/privacy headers.

The upstream provider is administrator-controlled. Do not allow end users to supply an arbitrary provider URL, or the Worker could become an SSRF proxy.

## Testing

Python dependencies are declared in `requirements.txt`. Keep security regression tests in `tests/` and run them with:

```bash
python -m pytest -q
python -m compileall backend tests
```

## Important limitation

This is a search **gateway**, not a Google-scale independent web index. Building a genuinely independent index requires a crawler, scheduler, storage, deduplication, robots/abuse controls, ranking pipeline, and substantial compute/storage. Those components should be added separately rather than pretending that a provider proxy is an independent search engine.
