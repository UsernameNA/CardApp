#!/usr/bin/env bash
# Fetches card data from the Sorcery TCG API and saves it to assets.
#
# Usage:
#   ./scripts/fetch_card_data.sh
#
# Output: app/src/main/assets/cards.json

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
OUTPUT="$PROJECT_ROOT/app/src/main/assets/cards.json"
API_URL="https://api.sorcerytcg.com/api/cards"

if ! command -v curl &>/dev/null; then
    echo "Error: curl is not installed." >&2
    exit 1
fi

mkdir -p "$(dirname "$OUTPUT")"

echo "Fetching card data from $API_URL ..."
curl -fsSL "$API_URL" -o "$OUTPUT"

COUNT=$(python3 -c "import json; data=json.load(open('$OUTPUT')); print(len(data))" 2>/dev/null || echo "unknown")
SIZE=$(du -sh "$OUTPUT" | cut -f1)

echo "Done. $COUNT cards, $SIZE → $OUTPUT"
