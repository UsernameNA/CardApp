#!/usr/bin/env python3
"""Fetches FAQ data from curiosa.io and saves it to assets.

The FAQ page embeds all data in a __NEXT_DATA__ JSON blob (Sanity CMS rich
text blocks). This script fetches the page, extracts that blob, flattens the
rich text into plain strings, and writes the result grouped by card name.

Usage:
    ./scripts/fetch_faqs.py

Output: app/src/main/assets/faqs.json
"""

import json
import re
import sys
import urllib.request
from collections import OrderedDict
from pathlib import Path

PAGE_URL = "https://curiosa.io/faqs"

PROJECT_ROOT = Path(__file__).parent.parent
OUTPUT = PROJECT_ROOT / "app/src/main/assets/faqs.json"


def extract_next_data(html: str) -> dict:
    """Pull the __NEXT_DATA__ JSON from the HTML page source."""
    match = re.search(
        r'<script\s+id="__NEXT_DATA__"\s+type="application/json">(.*?)</script>',
        html,
        re.DOTALL,
    )
    if not match:
        print("Error: could not find __NEXT_DATA__ in page", file=sys.stderr)
        sys.exit(1)
    return json.loads(match.group(1))


def blocks_to_text(blocks: list) -> str:
    """Convert Sanity rich-text blocks into a plain text string."""
    parts = []
    for block in blocks:
        if block.get("_type") == "block":
            children = block.get("children", [])
            parts.append("".join(child.get("text", "") for child in children))
    return "\n".join(parts)


def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    print(f"Fetching FAQs from {PAGE_URL} ...")
    req = urllib.request.Request(PAGE_URL, headers={"User-Agent": "CardApp-FAQ-Fetcher/1.0"})
    try:
        with urllib.request.urlopen(req) as response:
            html = response.read().decode("utf-8")
    except urllib.error.URLError as e:
        print(f"Error: failed to fetch {PAGE_URL}: {e}", file=sys.stderr)
        sys.exit(1)

    data = extract_next_data(html)
    faqs_raw = data["props"]["pageProps"]["faqs"]

    # Group by card name → list of {question, answer}
    grouped: dict[str, list] = OrderedDict()
    for entry in faqs_raw:
        card_names = entry.get("cardNames", [])
        question = blocks_to_text(entry.get("question", []))
        answer = blocks_to_text(entry.get("answer", []))

        if not question or not card_names:
            continue

        for name in card_names:
            grouped.setdefault(name, []).append(
                {"question": question, "answer": answer}
            )

    # Sort by card name
    sorted_grouped = OrderedDict(sorted(grouped.items()))

    OUTPUT.write_text(json.dumps(sorted_grouped, indent=2, ensure_ascii=False), encoding="utf-8")

    total_entries = sum(len(v) for v in sorted_grouped.values())
    size_kb = OUTPUT.stat().st_size / 1024
    print(f"Done. {len(sorted_grouped)} cards, {total_entries} Q&A entries, {size_kb:.1f} KB → {OUTPUT}")


if __name__ == "__main__":
    main()
