import tempfile
import unittest
from pathlib import Path

from backend.indexer import SearchIndex
from backend.service import CrazeService


class EngineTests(unittest.TestCase):
    def test_search_returns_indexed_result(self):
        with tempfile.TemporaryDirectory() as tmp:
            index = SearchIndex(Path(tmp) / "test.db")
            index.add_page({
                "url": "https://example.com/minecraft",
                "title": "Minecraft Guide",
                "text": "A simple Minecraft building guide.",
            })
            results = index.search("Minecraft building")
            self.assertEqual(len(results), 1)
            self.assertEqual(results[0]["url"], "https://example.com/minecraft")
            index.close()

    def test_empty_query_returns_no_results(self):
        with tempfile.TemporaryDirectory() as tmp:
            index = SearchIndex(Path(tmp) / "test.db")
            self.assertEqual(index.search("   "), [])
            index.close()


if __name__ == "__main__":
    unittest.main()
