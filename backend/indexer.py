"""Small SQLite search index for CrazeEngine V1."""

from __future__ import annotations

import sqlite3
from pathlib import Path
from urllib.parse import urljoin


class SearchIndex:
    def __init__(self, path: str | Path = "data/crazeengine.db") -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(self.path)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.execute("""
            CREATE TABLE IF NOT EXISTS pages (
                id INTEGER PRIMARY KEY,
                url TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                text TEXT NOT NULL
            )
        """)
        self.db.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS pages_fts USING fts5(
                title, text, content='pages', content_rowid='id'
            )
        """)
        self.db.commit()

    def add_page(self, page: dict) -> None:
        self.db.execute(
            "INSERT INTO pages(url,title,text) VALUES(?,?,?) "
            "ON CONFLICT(url) DO UPDATE SET title=excluded.title,text=excluded.text",
            (page["url"], page.get("title", ""), page.get("text", "")),
        )
        self.db.execute("INSERT INTO pages_fts(pages_fts) VALUES('rebuild')")
        self.db.commit()

    def search(self, query: str, limit: int = 10) -> list[dict]:
        limit = max(1, min(int(limit), 50))
        # FTS5 MATCH is used only after conservative tokenization to avoid
        # allowing arbitrary FTS operators supplied by a remote caller.
        tokens = [token for token in query.split() if token.replace("_", "").isalnum()]
        if not tokens:
            return []
        match = " AND ".join(f'"{token.replace(chr(34), "")}"' for token in tokens[:12])
        rows = self.db.execute(
            "SELECT pages.url, pages.title, snippet(pages_fts, 1, '', '', '…', 24) "
            "FROM pages_fts JOIN pages ON pages.id=pages_fts.rowid "
            "WHERE pages_fts MATCH ? ORDER BY bm25(pages_fts) LIMIT ?",
            (match, limit),
        ).fetchall()
        return [{"title": row[1], "url": row[0], "snippet": row[2]} for row in rows]

    def close(self) -> None:
        self.db.close()
