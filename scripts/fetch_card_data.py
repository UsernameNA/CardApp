#!/usr/bin/env python3
"""Fetches card data from the Sorcery TCG API and saves it to assets.

Usage:
    ./scripts/fetch_card_data.py

Output: app/src/main/assets/cards.json
"""

import json
import sys
import urllib.request
from pathlib import Path

API_URL = "https://api.sorcerytcg.com/api/cards"

PROJECT_ROOT = Path(__file__).parent.parent
OUTPUT = PROJECT_ROOT / "app/src/main/assets/cards.json"


def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    print(f"Fetching card data from {API_URL} ...")
    try:
        with urllib.request.urlopen(API_URL) as response:
            data = json.loads(response.read())
    except urllib.error.URLError as e:
        print(f"Error: failed to fetch {API_URL}: {e}", file=sys.stderr)
        sys.exit(1)

    OUTPUT.write_text(json.dumps(data, indent=2), encoding="utf-8")

    size_kb = OUTPUT.stat().st_size / 1024
    print(f"Done. {len(data)} cards, {size_kb:.1f} KB → {OUTPUT}")


if __name__ == "__main__":
    main()
