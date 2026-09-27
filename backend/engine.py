"""CrazeEngine V0.1 search core.

This module intentionally keeps the search layer small. It accepts a query,
asks a pluggable provider for results, and returns normalized result objects.
"""

from dataclasses import dataclass, asdict
from typing import Protocol


@dataclass(frozen=True)
class SearchResult:
    title: str
    url: str
    snippet: str = ""


class SearchProvider(Protocol):
    def search(self, query: str, limit: int = 10) -> list[SearchResult]:
        ...


class CrazeEngine:
    def __init__(self, provider: SearchProvider):
        self.provider = provider

    def search(self, query: str, limit: int = 10) -> list[dict]:
        query = " ".join(query.strip().split())
        if not query:
            return []

        limit = max(1, min(int(limit), 50))
        results = self.provider.search(query, limit)

        # De-duplicate by normalized URL while preserving provider order.
        seen: set[str] = set()
        output: list[dict] = []
        for result in results:
            if not result.url or result.url in seen:
                continue
            seen.add(result.url)
            output.append(asdict(result))

        return output
