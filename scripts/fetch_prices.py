#!/usr/bin/env python3
"""Fetches Sorcery: Contested Realm market prices from TCGPlayer and saves them to assets.

Uses TCGPlayer's marketplace search API (the one behind its price guide pages).

Usage:
    ./scripts/fetch_prices.py

Output: app/src/main/assets/prices.json
"""

import json
import sys
import time
import urllib.request
from pathlib import Path

# mpfev is TCGPlayer's frontend version; if requests start failing, copy the current
# value from a price-guide page's network requests.
API_URL = "https://mp-search-api.tcgplayer.com/v1/search/request?q=&isList=false&mpfev=2952"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36",
    "Accept": "application/json",
    "Content-Type": "application/json",
}
PRODUCT_LINE = "sorcery-contested-realm"
PAGE_SIZE = 50
MIN_PRICES = 1000

PROJECT_ROOT = Path(__file__).parent.parent
OUTPUT = PROJECT_ROOT / "app/src/main/assets/prices.json"


def search(set_name, offset, size):
    term = {"productLineName": [PRODUCT_LINE]}
    if set_name is not None:
        term["setName"] = [set_name]
    body = {
        "algorithm": "price_guide_browse",
        "from": offset,
        "size": size,
        "filters": {"term": term, "range": {}, "match": {}},
        "listingSearch": {
            "filters": {"term": {}, "range": {}, "exclude": {"channelExclusion": 0}},
            "context": {"cart": {}},
        },
        "context": {"shippingCountry": "US", "userProfile": {}, "cart": {}},
        "settings": {"useFuzzySearch": False, "didYouMean": {}},
        "sort": {},
    }
    req = urllib.request.Request(API_URL, data=json.dumps(body).encode(), headers=HEADERS, method="POST")
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                return json.loads(response.read())["results"][0]
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, KeyError, IndexError) as e:
            if attempt == 2:
                print(f"Error: TCGPlayer request failed ({set_name or 'all sets'}, offset {offset}): {e}",
                      file=sys.stderr)
                sys.exit(1)
            time.sleep(2 * (attempt + 1))


def number(value):
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def main():
    print("Fetching Sorcery: Contested Realm sets...")
    sets = [(s["value"], int(s["count"])) for s in search(None, 0, 1)["aggregations"].get("setName", [])]
    for name, count in sets:
        print(f"  {name}: {count} products")

    all_cards = []
    for set_name, total in sets:
        got = 0
        for offset in range(0, total, PAGE_SIZE):
            results = search(set_name, offset, PAGE_SIZE)["results"]
            if not results:
                break
            for r in results:
                all_cards.append({
                    "productName": r.get("productName"),
                    "productId": number(r.get("productId")),
                    "setName": r.get("setName"),
                    "marketPrice": r.get("marketPrice"),
                    "medianPrice": r.get("medianPrice"),
                    "lowestPrice": r.get("lowestPrice"),
                    "lowestPriceWithShipping": r.get("lowestPriceWithShipping"),
                })
            got += len(results)
            time.sleep(0.5)
        print(f"  {set_name}: got {got}")

    if len(all_cards) < MIN_PRICES:
        print(f"Error: only {len(all_cards)} prices fetched; prices.json left untouched.", file=sys.stderr)
        sys.exit(1)

    output = {
        "fetchedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "source": "tcgplayer",
        "totalCards": len(all_cards),
        "cards": all_cards,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(output, indent=2), encoding="utf-8")
    print(f"Done. {len(all_cards)} prices → {OUTPUT}")


if __name__ == "__main__":
    main()
