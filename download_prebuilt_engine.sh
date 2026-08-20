#!/usr/bin/env bash
set -e

# Target directories
JNILIBS_DIR="app/src/main/jniLibs/arm64-v8a"
ASSETS_DIR="app/src/main/assets"
TEMP_DIR=".engine_temp"

mkdir -p "${JNILIBS_DIR}"
mkdir -p "${ASSETS_DIR}"
mkdir -p "${TEMP_DIR}"

echo "Fetching latest Chromium Android Arm64 snapshot build ID..."
BUILD_ID=$(curl -s "https://www.googleapis.com/download/storage/v1/b/chromium-browser-snapshots/o/Android_Arm64%2FLAST_CHANGE?alt=media" || echo "1682818")
echo "Latest Build ID: ${BUILD_ID}"

DOWNLOAD_URL="https://www.googleapis.com/download/storage/v1/b/chromium-browser-snapshots/o/Android_Arm64%2F${BUILD_ID}%2Fchrome-android.zip?alt=media"

echo "Downloading prebuilt Chromium Android native engine..."
curl -L "${DOWNLOAD_URL}" -o "${TEMP_DIR}/chrome-android.zip"

echo "Extracting native shared libraries (.so) and pak assets..."
unzip -q -o "${TEMP_DIR}/chrome-android.zip" -d "${TEMP_DIR}/extracted"

# Copy native libraries (.so)
find "${TEMP_DIR}/extracted" -name "*.so" -exec cp {} "${JNILIBS_DIR}/" \;

# Copy pak resource files
find "${TEMP_DIR}/extracted" -name "*.pak" -exec cp {} "${ASSETS_DIR}/" \;

# Cleanup
rm -rf "${TEMP_DIR}"

echo "Prebuilt Chromium engine installed successfully into ${JNILIBS_DIR}!"
