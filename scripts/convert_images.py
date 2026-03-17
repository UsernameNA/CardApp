#!/usr/bin/env python3
"""Converts PNG card images to WebP for bundling in the app.

Usage:
    ./scripts/convert_images.py [INPUT_DIR] [QUALITY]

Defaults:
    INPUT_DIR  scripts/cards_png
    QUALITY    80

Output is always written to app/src/main/assets/images/

Requires Pillow: pip install Pillow
"""

import argparse
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.parent
DEFAULT_INPUT = PROJECT_ROOT / "scripts/cards_png"
OUTPUT_DIR = PROJECT_ROOT / "app/src/main/assets/images"


def convert(png: Path, output_dir: Path, quality: int) -> Path:
    from PIL import Image
    dest = output_dir / png.with_suffix(".webp").name
    with Image.open(png) as img:
        img.save(dest, format="WEBP", quality=quality)
    return dest


def main():
    try:
        from PIL import Image  # noqa: F401
    except ImportError:
        print("Error: Pillow is not installed. Run: pip install Pillow", file=sys.stderr)
        sys.exit(1)

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("input_dir", nargs="?", default=DEFAULT_INPUT, type=Path)
    parser.add_argument("quality", nargs="?", default=80, type=int)
    args = parser.parse_args()

    if not args.input_dir.is_dir():
        print(f"Error: input directory not found: {args.input_dir}", file=sys.stderr)
        sys.exit(1)

    pngs = sorted(args.input_dir.glob("*.png"))
    if not pngs:
        print(f"No PNG files found in {args.input_dir}")
        sys.exit(0)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Converting {len(pngs)} PNGs → WebP (quality {args.quality})")
    print(f"Output: {OUTPUT_DIR}")

    errors = []
    with ThreadPoolExecutor() as pool:
        futures = {pool.submit(convert, png, OUTPUT_DIR, args.quality): png for png in pngs}
        for future in as_completed(futures):
            png = futures[future]
            try:
                future.result()
            except Exception as e:
                errors.append(f"{png.name}: {e}")

    if errors:
        print(f"\n{len(errors)} conversion(s) failed:", file=sys.stderr)
        for err in errors:
            print(f"  {err}", file=sys.stderr)
        sys.exit(1)

    written = len(list(OUTPUT_DIR.glob("*.webp")))
    print(f"Done. {written} WebP files written.")


if __name__ == "__main__":
    main()
