#!/usr/bin/env python3
"""Fetch Sorcery: Contested Realm card prices from TCGPlayer and write to prices.json.

Usage:
    python3 scripts/fetch_prices.py

Output:
    app/src/main/assets/prices.json

Prices are fetched from TCGPlayer's internal marketplace search API.
Run this daily or weekly to keep prices current.
"""

import json
import os
import sys
import time
import requests

API_URL = "https://mp-search-api.tcgplayer.com/v1/search/request?q=&isList=false&mpfev=2952"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36",
    "Accept": "application/json",
    "Content-Type": "application/json",
}
PAGE_SIZE = 50
PRODUCT_LINE = "sorcery-contested-realm"

OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets", "prices.json",
)


def build_request_body(set_name: str, offset: int, size: int) -> dict:
    return {
        "algorithm": "price_guide_browse",
        "from": offset,
        "size": size,
        "filters": {
            "term": {
                "productLineName": [PRODUCT_LINE],
                "setName": [set_name],
            },
            "range": {},
            "match": {},
        },
        "listingSearch": {
            "filters": {
                "term": {},
                "range": {},
                "exclude": {"channelExclusion": 0},
            },
            "context": {"cart": {}},
        },
        "context": {"shippingCountry": "US", "userProfile": {}, "cart": {}},
        "settings": {"useFuzzySearch": False, "didYouMean": {}},
        "sort": {},
    }


def fetch_set_names() -> list[tuple[str, int]]:
    """Return list of (setName, cardCount) for all Sorcery sets."""
    body = build_request_body("alpha", 0, 1)
    # Remove setName filter to get all sets in aggregations
    del body["filters"]["term"]["setName"]
    resp = requests.post(API_URL, headers=HEADERS, json=body, timeout=30)
    resp.raise_for_status()
    data = resp.json()
    aggs = data["results"][0]["aggregations"]
    sets = []
    for s in aggs.get("setName", []):
        sets.append((s["value"], int(s["count"])))
    return sets


def fetch_set_prices(set_name: str, total: int) -> list[dict]:
    """Fetch all card prices for a single set."""
    cards = []
    offset = 0
    while offset < total:
        body = build_request_body(set_name, offset, PAGE_SIZE)
        resp = requests.post(API_URL, headers=HEADERS, json=body, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        results = data["results"][0]["results"]
        if not results:
            break
        for r in results:
            card = {
                "productName": r.get("productName"),
                "productId": int(r.get("productId", 0)),
                "setName": r.get("setName"),
                "marketPrice": r.get("marketPrice"),
                "medianPrice": r.get("medianPrice"),
                "lowestPrice": r.get("lowestPrice"),
                "lowestPriceWithShipping": r.get("lowestPriceWithShipping"),
            }
            cards.append(card)
        offset += PAGE_SIZE
        # Be polite — small delay between pages
        time.sleep(0.5)
    return cards


def main():
    print("Fetching Sorcery: Contested Realm sets...")
    sets = fetch_set_names()
    print(f"Found {len(sets)} sets:")
    for name, count in sets:
        print(f"  {name}: {count} cards")

    all_cards = []
    for set_name, count in sets:
        print(f"\nFetching {set_name} ({count} cards)...")
        cards = fetch_set_prices(set_name, count)
        all_cards.extend(cards)
        print(f"  Got {len(cards)} cards")

    output = {
        "fetchedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "source": "tcgplayer",
        "totalCards": len(all_cards),
        "cards": all_cards,
    }

    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w") as f:
        json.dump(output, f, indent=2)

    print(f"\nWrote {len(all_cards)} card prices to {OUTPUT_PATH}")
    print(f"Timestamp: {output['fetchedAt']}")


if __name__ == "__main__":
    main()
