"""Command-line interface for local CrazeEngine V1 operation."""

from __future__ import annotations

import argparse
import json

from .service import CrazeService


def main() -> None:
    parser = argparse.ArgumentParser(description="CrazeEngine privacy-first search engine")
    sub = parser.add_subparsers(dest="command", required=True)

    index = sub.add_parser("index", help="fetch and index one public webpage")
    index.add_argument("url")

    search = sub.add_parser("search", help="search the local index")
    search.add_argument("query")
    search.add_argument("--limit", type=int, default=10)

    args = parser.parse_args()
    service = CrazeService()
    try:
        if args.command == "index":
            result = service.index_url(args.url)
        else:
            result = service.search(args.query, args.limit)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    finally:
        service.close()


if __name__ == "__main__":
    main()
