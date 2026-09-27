# CrazeEngine

Privacy-first search engine foundation for FluXBrowser.

## V0.1

The first backend milestone is intentionally small:

- Search web links
- Extract title, URL, and snippet
- Avoid user accounts and search-history tracking
- Keep the engine modular so the search source can be replaced later

## Architecture

```text
Browser
  ↓
PHP search page
  ↓
Python search engine
  ↓
Search provider / index
  ↓
Results
```

CrazeEngine is not an AI answer engine in V0.1. It focuses on returning useful web links.
