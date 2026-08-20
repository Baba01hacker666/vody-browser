# Vody Browser

A fast, lightweight, and independent Chromium-based Android browser.

## Architecture

Vody Browser is built on a standalone Chromium Android application architecture:
- **Application & UI Layer**: Located in `java/`, `feed/`, `features/`, `modules/`, and `webapk/`.
- **Tests**: Located in `junit/` and `javatests/`.
- **Build Configurations**: GN/Ninja configurations (`BUILD.gn`, `.gni` files).

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
