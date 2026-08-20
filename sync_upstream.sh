#!/usr/bin/env bash
set -e

# Usage:
#   ./sync_upstream.sh                  # Downloads latest main chrome/android into .sync_temp
#   ./sync_upstream.sh --apply          # Downloads and overwrites current directory with upstream main
#   ./sync_upstream.sh <ref>            # Downloads specific branch or commit ref (e.g., refs/tags/135.0.7049.3)

REF="${1:-refs/heads/main}"
APPLY=0

if [ "$1" = "--apply" ]; then
    REF="refs/heads/main"
    APPLY=1
fi

TEMP_DIR=".sync_temp"
ARCHIVE_URL="https://chromium.googlesource.com/chromium/src/+archive/${REF}/chrome/android.tar.gz"

echo "Fetching upstream chrome/android from ${REF}..."
mkdir -p "${TEMP_DIR}"
curl -L "${ARCHIVE_URL}" -o "${TEMP_DIR}/chrome_android.tar.gz"

echo "Extracting upstream files..."
mkdir -p "${TEMP_DIR}/extracted"
tar -xzf "${TEMP_DIR}/chrome_android.tar.gz" -C "${TEMP_DIR}/extracted"

if [ "${APPLY}" -eq 1 ]; then
    echo "Applying upstream files into repository root..."
    cp -r "${TEMP_DIR}/extracted/"* .
    echo "Upstream files applied successfully."
else
    echo "Upstream files extracted to '${TEMP_DIR}/extracted/'."
    echo "You can inspect, diff, or copy specific features into your codebase."
    echo "To compare: diff -ur java/ ${TEMP_DIR}/extracted/java/"
fi
