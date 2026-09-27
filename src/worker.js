const MAX_QUERY = 200;
const MAX_RESULTS = 10;
const PROVIDER_TIMEOUT_MS = 5000;

function headers(extra = {}) {
  return new Headers({
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'referrer-policy': 'no-referrer',
    'x-content-type-options': 'nosniff',
    'x-frame-options': 'DENY',
    'permissions-policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
    'content-security-policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
    ...extra,
  });
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: headers() });
}

function normalizeUrl(value) {
  try {
    const u = new URL(String(value));
    if (u.protocol !== 'https:' && u.protocol !== 'http:') return null;
    u.username = '';
    u.password = '';
    return u.href;
  } catch { return null; }
}

function cleanText(value, max = 600) {
  return String(value ?? '').replace(/[\u0000-\u001f\u007f]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, max);
}

function normalizeResults(payload) {
  const raw = Array.isArray(payload?.results) ? payload.results : [];
  const seen = new Set();
  const out = [];
  for (const item of raw) {
    const url = normalizeUrl(item?.url);
    if (!url || seen.has(url)) continue;
    seen.add(url);
    out.push({
      title: cleanText(item?.title, 180) || url,
      url,
      snippet: cleanText(item?.content ?? item?.snippet, 600),
    });
    if (out.length >= MAX_RESULTS) break;
  }
  return out;
}

async function providerSearch(env, query) {
  const base = String(env.SEARCH_PROVIDER_URL || '').trim();
  if (!base) throw new Error('Search provider is not configured.');
  let endpoint;
  try { endpoint = new URL(base); } catch { throw new Error('Search provider configuration is invalid.'); }
  if (endpoint.protocol !== 'https:') throw new Error('Search provider must use HTTPS.');
  endpoint.searchParams.set('q', query);
  endpoint.searchParams.set('format', 'json');
  endpoint.searchParams.set('language', 'all');
  endpoint.searchParams.set('safesearch', '1');
  endpoint.searchParams.set('pageno', '1');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), PROVIDER_TIMEOUT_MS);
  try {
    const response = await fetch(endpoint, {
      method: 'GET',
      redirect: 'error',
      signal: controller.signal,
      headers: { accept: 'application/json', 'user-agent': 'CrazeEngine/1.0' },
    });
    if (!response.ok) throw new Error(`Search provider returned ${response.status}.`);
    const type = response.headers.get('content-type') || '';
    if (!type.toLowerCase().includes('application/json')) throw new Error('Search provider returned an invalid response.');
    return normalizeResults(await response.json());
  } finally { clearTimeout(timer); }
}

function sameOrigin(request) {
  const origin = request.headers.get('origin');
  if (!origin) return true;
  try { return new URL(origin).origin === new URL(request.url).origin; } catch { return false; }
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method !== 'GET' && request.method !== 'HEAD') return new Response(null, { status: 405, headers: headers({ allow: 'GET, HEAD' }) });
    if (!sameOrigin(request)) return json({ error: 'Cross-origin requests are not allowed.' }, 403);

    if (url.pathname === '/api/health') return json({ ok: true, engine: 'CrazeEngine', version: '1.0' });
    if (url.pathname === '/api/search') {
      const query = cleanText(url.searchParams.get('q'), MAX_QUERY);
      if (!query) return json({ error: 'Missing search query.' }, 400);
      if (query.length > MAX_QUERY) return json({ error: 'Search query is too long.' }, 400);
      try {
        const results = await providerSearch(env, query);
        return json({ query, results });
      } catch (error) {
        ctx.waitUntil(Promise.resolve());
        const message = error?.name === 'AbortError' ? 'Search provider timed out.' : (error?.message || 'Search failed.');
        return json({ error: message }, 502);
      }
    }

    return env.ASSETS.fetch(request);
  },
};
