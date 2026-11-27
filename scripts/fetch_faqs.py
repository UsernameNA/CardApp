#!/usr/bin/env python3
"""Fetches card FAQs from sorcerytcg.com and saves them to assets.

The site's codex serves every FAQ as JSON (Sanity rich-text blocks tagged with card
slugs). This flattens the rich text into plain strings and groups the entries by
card name, using the card API to map slugs to names.

Usage:
    ./scripts/fetch_faqs.py

Output: app/src/main/assets/faqs.json
"""

import json
import sys
import urllib.request
from collections import OrderedDict
from pathlib import Path

FAQS_URL = "https://sorcerytcg.com/api/trpc/codex.listFaqs"
CARDS_URL = "https://api.sorcerytcg.com/api/cards"

PROJECT_ROOT = Path(__file__).parent.parent
OUTPUT = PROJECT_ROOT / "app/src/main/assets/faqs.json"

MIN_ENTRIES = 300


def fetch_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "CardApp-FAQ-Fetcher/2.0"})
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            return json.loads(response.read())
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
        print(f"Error: failed to fetch {url}: {e}", file=sys.stderr)
        sys.exit(1)


def blocks_to_text(blocks):
    """Convert Sanity rich-text blocks into a plain text string."""
    parts = []
    for block in blocks or []:
        kind = block.get("_type")
        if kind == "block":
            parts.append("".join(child.get("text", "") for child in block.get("children", [])))
        elif kind == "damageGrid":
            for row in block.get("grid", {}).get("rows", []):
                parts.append("  ".join(row.get("cells", [])))
    return "\n".join(parts).strip()


def main():
    print(f"Fetching FAQs from {FAQS_URL} ...")
    try:
        faqs_raw = fetch_json(FAQS_URL)["result"]["data"]["json"]
    except (KeyError, TypeError):
        print("Error: unexpected FAQ response shape; the site may have changed. "
              "faqs.json left untouched.", file=sys.stderr)
        sys.exit(1)

    print(f"Fetching card names from {CARDS_URL} ...")
    names = {c["slug"]: c["name"] for c in fetch_json(CARDS_URL)}

    grouped = OrderedDict()
    unknown = set()
    for entry in sorted(faqs_raw, key=lambda e: e.get("orderRank", "")):
        question = blocks_to_text(entry.get("question"))
        answer = blocks_to_text(entry.get("answer"))
        if not question:
            continue
        for slug in entry.get("cards") or []:
            name = names.get(slug)
            if name is None:
                unknown.add(slug)
                continue
            grouped.setdefault(name, []).append({"question": question, "answer": answer})

    total_entries = sum(len(v) for v in grouped.values())
    if total_entries < MIN_ENTRIES:
        print(f"Error: only {total_entries} FAQ entries found; faqs.json left untouched.", file=sys.stderr)
        sys.exit(1)

    sorted_grouped = OrderedDict(sorted(grouped.items()))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(sorted_grouped, indent=2, ensure_ascii=False), encoding="utf-8")

    size_kb = OUTPUT.stat().st_size / 1024
    print(f"Done. {len(sorted_grouped)} cards, {total_entries} Q&A entries, {size_kb:.1f} KB → {OUTPUT}")
    if unknown:
        print(f"Skipped FAQs for unknown card slugs: {sorted(unknown)}")


if __name__ == "__main__":
    main()
