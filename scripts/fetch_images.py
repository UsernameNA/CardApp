#!/usr/bin/env python3
"""Downloads missing card art from the publisher's Google Drive folder as WebP.

Every printing in cards.json needs scripts/images/<slug>.webp for release builds.
This lists the public "Card Images (API)" Drive folder, downloads the PNG for each
printing that has no WebP yet, and converts it (quality 80, original size).

Run fetch_card_data.py first so cards.json lists the new printings.

Usage:
    pip3 install -r scripts/requirements.txt
    ./scripts/fetch_images.py

Output: scripts/images/*.webp
"""

import concurrent.futures
import html
import io
import json
import re
import sys
import urllib.request
from pathlib import Path

DRIVE_FOLDER_ID = "17IrJkRGmIU9fDSTU2JQEU9JlFzb5liLJ"
FOLDER_URL = f"https://drive.google.com/embeddedfolderview?id={DRIVE_FOLDER_ID}"
DOWNLOAD_URL = "https://drive.usercontent.google.com/download?id={}&export=download"

PROJECT_ROOT = Path(__file__).parent.parent
CARDS = PROJECT_ROOT / "app/src/main/assets/cards.json"
OUTPUT_DIR = PROJECT_ROOT / "scripts/images"

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


def fetch(url, timeout=120):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return response.read()


def list_folder():
    """Return {filename: file id} for the Drive folder."""
    page = fetch(FOLDER_URL).decode("utf-8", errors="replace")
    files = {}
    for m in re.finditer(r'<a href="[^"]*/file/d/([^/"]+)/[^"]*"[^>]*>.*?flip-entry-title">(.*?)</div>', page, re.S):
        files[html.unescape(m.group(2)).strip()] = m.group(1)
    return files


def main():
    try:
        from PIL import Image
    except ImportError:
        print("Error: Pillow is not installed. Run: pip3 install -r scripts/requirements.txt", file=sys.stderr)
        sys.exit(1)

    cards = json.loads(CARDS.read_text(encoding="utf-8"))
    slugs = {v["slug"] for c in cards for s in c["sets"] for v in s["variants"]}
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    missing = sorted(s for s in slugs if not (OUTPUT_DIR / f"{s}.webp").exists())
    print(f"{len(slugs)} printings, {len(missing)} without an image in {OUTPUT_DIR}")
    if not missing:
        return

    print("Listing Drive folder ...")
    files = list_folder()
    if not files:
        print("Error: no files found in the Drive folder; its page format may have changed.", file=sys.stderr)
        sys.exit(1)
    available = [s for s in missing if f"{s}.png" in files]
    unavailable = [s for s in missing if f"{s}.png" not in files]
    print(f"{len(files)} files in folder; downloading {len(available)}")

    def download(slug):
        for attempt in range(3):
            try:
                data = fetch(DOWNLOAD_URL.format(files[f"{slug}.png"]))
                if not data.startswith(PNG_MAGIC):
                    raise ValueError("response is not a PNG")
                with Image.open(io.BytesIO(data)) as img:
                    img.save(OUTPUT_DIR / f"{slug}.webp", format="WEBP", quality=80)
                return slug, None
            except Exception as e:  # noqa: BLE001 - reported per file below
                error = e
        return slug, error

    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        failures = [(s, e) for s, e in pool.map(download, available) if e is not None]

    print(f"Done. {len(available) - len(failures)} images written to {OUTPUT_DIR}")
    for slug, error in failures:
        print(f"  failed: {slug}: {error}", file=sys.stderr)
    if unavailable:
        print(f"Not in the Drive folder ({len(unavailable)}): {unavailable}")
    if failures:
        sys.exit(1)


if __name__ == "__main__":
    main()
