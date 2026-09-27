"""Search provider interface and a safe development provider.

The development provider lets the engine run before we connect a real web
index/provider. It does not contact external websites.
"""

from .engine import SearchResult


class DemoProvider:
    def search(self, query: str, limit: int = 10) -> list[SearchResult]:
        return [
            SearchResult(
                title=f"Search result for {query}",
                url="https://example.com/",
                snippet="CrazeEngine development result. Replace DemoProvider with the real search backend.",
            )
        ][:limit]
