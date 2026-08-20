# Vody Browser

A fast, customizable, and independent Android browser built on Chromium with full on-device Extensions and Developer Tools support.

## Features

- **Chrome Extension Support**:
  - Full support for Manifest V2 & Manifest V3 extensions (`.crx`, `.zip`, and unpacked directories).
  - Direct installation from the Chrome Web Store via extension ID or URL.
  - Content script injection (CSS & JavaScript), popup rendering, and `chrome.*` API bridge (`chrome.runtime`, `chrome.storage`, `chrome.tabs`, `chrome.action`).
  - Native Extensions Management UI (`ExtensionManagerActivity`).
- **On-Device Developer Tools**:
  - Interactive on-device DevTools Inspector (Elements, Console, Network, Resources, DOM tree).
  - Built-in JavaScript evaluation console (`DevToolsConsoleDialog`).
- **Rebranded Architecture**:
  - Independent identity (**Vody**) separated from upstream naming.

## Upstream Synchronization

To synchronize or pull features from upstream Chromium without requiring a multi-gigabyte fork:

```bash
# Download and inspect upstream features into a temporary directory
./sync_upstream.sh

# Apply upstream updates directly
./sync_upstream.sh --apply

# Sync against a specific Chromium release tag
./sync_upstream.sh refs/tags/135.0.7049.3
```

## Continuous Integration

Automated builds and unit tests are configured via GitHub Actions in [`.github/workflows/build.yml`](.github/workflows/build.yml).
