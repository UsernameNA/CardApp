#!/usr/bin/env python3
"""Fetches card data from the Sorcery TCG API and saves it to assets.

The API's format (engine/printings) differs from the one the app reads
(guardian/sets/variants), so this converts it. The API is the source of truth;
the existing cards.json fills fields the API no longer returns (flavour text,
some avatar rarity/life, a few subtypes) and fixes set/variant order so existing
cards keep the same display image.

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

MIN_CARDS = 1000

SET_NAMES = {"Promo": "Promotional"}

# The API's set.releasedAt is a database timestamp, not a release date. Sets the app
# already knows keep their date (it orders sets and picks display images); a new set
# uses the API's value, which has so far been its real release date.
SET_RELEASES = {
    "Alpha": "2023-04-19T00:00:00.000Z",
    "Beta": "2023-11-10T00:00:00.000Z",
    "Arthurian Legends": "2024-10-04T00:00:00.000Z",
    "Dragonlord": "2025-08-01T20:11:58.215Z",
    "Promotional": "2025-11-20T22:09:50.983Z",
    "Gothic": "2025-12-05T16:59:59.639Z",
    "Frost Mage Vs. Lavamancer": "2026-11-13T00:00:00.000Z",
}

ELEMENT_ORDER = ["Earth", "Fire", "Water", "Air"]


def text(s):
    return s.replace("\r\n", "\n") if isinstance(s, str) else s


def stats(engine, old_guardian):
    g = {
        "rarity": engine.get("rarity"),
        "type": engine.get("type"),
        "rulesText": text(engine.get("rules")),
        "cost": engine.get("cost"),
        "attack": engine.get("attack"),
        "defence": engine.get("defense"),
        "life": engine.get("life"),
        "thresholds": {k: engine.get(k) or 0 for k in ("air", "earth", "fire", "water")},
    }
    if old_guardian:
        for k in ("rarity", "life"):
            if g[k] is None:
                g[k] = old_guardian.get(k)
    if g["type"] == "Avatar" and g["life"] is None:
        g["life"] = 20
    # Units have a single power value; the app shows it once when attack == defence.
    if g["type"] in ("Minion", "Avatar") and g["defence"] is None:
        g["defence"] = g["attack"]
    return g


def elements(engine):
    els = engine.get("elements") or []
    if not els or els == ["None"]:
        return "None"
    return ", ".join(sorted(els, key=lambda x: ELEMENT_ORDER.index(x) if x in ELEMENT_ORDER else len(ELEMENT_ORDER)))


def convert(card, old):
    engine = card["engine"]
    g = stats(engine, old["guardian"] if old else None)
    old_sets = [s["name"] for s in old["sets"]] if old else []
    old_slugs = [v["slug"] for s in old["sets"] for v in s["variants"]] if old else []
    old_flavor = {v["slug"]: v.get("flavorText") or "" for s in old["sets"] for v in s["variants"]} if old else {}

    sets = {}
    for p in card["printings"]:
        name = SET_NAMES.get(p["set"]["name"], p["set"]["name"])
        s = sets.get(name)
        if s is None:
            s = sets[name] = {
                "name": name,
                "releasedAt": SET_RELEASES.get(name) or p["set"]["releasedAt"],
                "metadata": g,
                "variants": [],
            }
        meta = p["meta"]
        s["variants"].append({
            "slug": p["slug"],
            "finish": meta.get("finish"),
            "product": meta.get("product"),
            "artist": (meta.get("artist") or {}).get("name"),
            "flavorText": text(meta.get("flavor")) or old_flavor.get(p["slug"], ""),
            "typeText": text(meta.get("typeline")),
        })

    def set_key(s):
        if s["name"] in old_sets:
            return 0, old_sets.index(s["name"]), ""
        return 1, 0, s["releasedAt"]

    def variant_key(v):
        if v["slug"] in old_slugs:
            return 0, old_slugs.index(v["slug"]), ""
        return 1, 0 if (v["finish"] or "").lower() == "standard" else 1, v["slug"]

    ordered = sorted(sets.values(), key=set_key)
    for s in ordered:
        s["variants"].sort(key=variant_key)

    return {
        "name": card["name"],
        "guardian": g,
        "elements": elements(engine),
        "subTypes": ", ".join(sorted(engine.get("subtypes") or [])) or (old["subTypes"] if old else ""),
        "sets": ordered,
    }


def main():
    print(f"Fetching card data from {API_URL} ...")
    req = urllib.request.Request(API_URL, headers={"User-Agent": "CardApp-Data-Fetcher/2.0"})
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            data = json.loads(response.read())
    except (urllib.error.URLError, TimeoutError) as e:
        print(f"Error: failed to fetch {API_URL}: {e}", file=sys.stderr)
        sys.exit(1)

    if not isinstance(data, list) or len(data) < MIN_CARDS or "printings" not in data[0]:
        print(f"Error: unexpected response ({type(data).__name__}, {len(data)} items); "
              "the API format may have changed. cards.json left untouched.", file=sys.stderr)
        sys.exit(1)

    old_cards = json.loads(OUTPUT.read_text(encoding="utf-8")) if OUTPUT.exists() else []
    old_by_name = {c["name"]: c for c in old_cards}
    old_order = {c["name"]: i for i, c in enumerate(old_cards)}

    cards = [convert(c, old_by_name.get(c["name"])) for c in data]
    cards.sort(key=lambda c: (old_order.get(c["name"], len(old_order)), c["name"]))

    unknown_sets = sorted({s["name"] for c in cards for s in c["sets"]} - SET_RELEASES.keys())
    if unknown_sets:
        print(f"Note: new set(s) {unknown_sets} use the API's releasedAt; "
              "add their release date to SET_RELEASES.")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(cards, indent=2), encoding="utf-8")

    added = [c["name"] for c in cards if c["name"] not in old_by_name]
    removed = [n for n in old_by_name if n not in {c["name"] for c in cards}]
    size_kb = OUTPUT.stat().st_size / 1024
    print(f"Done. {len(cards)} cards (+{len(added)}, -{len(removed)}), {size_kb:.1f} KB → {OUTPUT}")
    if removed:
        print(f"Removed: {removed}")


if __name__ == "__main__":
    main()
