"""V1 service layer: index-backed search and controlled page ingestion."""

from __future__ import annotations

from .crawler import Crawler
from .engine import CrazeEngine, SearchResult
from .indexer import SearchIndex


class IndexProvider:
    def __init__(self, index: SearchIndex):
        self.index = index

    def search(self, query: str, limit: int = 10) -> list[SearchResult]:
        return [SearchResult(**item) for item in self.index.search(query, limit)]


class CrazeService:
    def __init__(self, database: str = "data/crazeengine.db"):
        self.index = SearchIndex(database)
        self.engine = CrazeEngine(IndexProvider(self.index))
        self.crawler = Crawler()

    def search(self, query: str, limit: int = 10) -> list[dict]:
        return self.engine.search(query, limit)

    def index_url(self, url: str) -> dict:
        page = self.crawler.fetch(url)
        self.index.add_page(page)
        return {
            "url": page["url"],
            "title": page["title"],
            "indexed": True,
            "discovered_links": len(page["links"]),
        }

    def close(self) -> None:
        self.index.close()
