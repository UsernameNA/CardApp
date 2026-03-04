#!/usr/bin/env bash
# Converts PNG card images to WebP for bundling in the app.
#
# Usage:
#   ./scripts/convert_images.sh [INPUT_DIR] [QUALITY]
#
# Defaults:
#   INPUT_DIR  app/src/main/assets/cards_png
#   QUALITY    80
#
# Output is always written to app/src/main/assets/images/

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

INPUT_DIR="${1:-$PROJECT_ROOT/scripts/cards_png}"
QUALITY="${2:-80}"
OUTPUT_DIR="$PROJECT_ROOT/app/src/main/assets/images"

if [[ ! -d "$INPUT_DIR" ]]; then
    echo "Error: input directory not found: $INPUT_DIR" >&2
    exit 1
fi

if ! command -v magick &>/dev/null; then
    echo "Error: ImageMagick (magick) is not installed." >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

PNGS=("$INPUT_DIR"/*.png)
TOTAL=${#PNGS[@]}

if [[ $TOTAL -eq 0 ]]; then
    echo "No PNG files found in $INPUT_DIR"
    exit 0
fi

echo "Converting $TOTAL PNGs → WebP (quality $QUALITY)"
echo "Output: $OUTPUT_DIR"

export OUTPUT_DIR QUALITY
printf '%s\n' "${PNGS[@]}" | xargs -P"$(nproc)" -I{} bash -c '
    f="{}"
    base="${f##*/}"
    magick "$f" -quality "$QUALITY" "$OUTPUT_DIR/${base%.png}.webp"
'

echo "Done. $(ls "$OUTPUT_DIR"/*.webp 2>/dev/null | wc -l) WebP files written."
