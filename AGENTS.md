# AGENTS.md

Guidance for AI coding agents and humans working on **Vody Browser** — a privacy-first
Android browser built on Mozilla GeckoView (real Firefox engine, no Chromium/WebView).

## Toolchain (this machine)

| Tool       | Path / version                                   |
|------------|--------------------------------------------------|
| JDK 17     | `$HOME/jdk17` (set as `JAVA_HOME`)               |
| Android SDK| `$HOME/android-sdk` (`platforms;android-35`, `build-tools;35.0.0`) |
| Gradle     | wrapper 8.9 (`./gradlew`); AGP 8.7.2             |

`local.properties` is generated locally (gitignored) with `sdk.dir=...`.

## Build & test

```bash
export JAVA_HOME=$HOME/jdk17 ANDROID_HOME=$HOME/android-sdk
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease        # falls back to debug signing locally
./gradlew testDebugUnitTest      # unit tests (JUnit 4)
```

The gradle-wrapper.jar is intentionally NOT committed; if `./gradlew` is missing it,
regenerate with a local Gradle install (`gradle wrapper --gradle-version 8.9`) — CI does
the same.

## Architecture map (all Java, no Kotlin)

- `app/src/main/java/org/vody/browser/`
  - `MainActivity.java` — single-activity browser UI: GeckoView host, tabs
    (`Tab`), URL pill, bottom toolbar, tab-switcher + menu bottom sheets,
    AMO install prompt delegate.
  - `ListActivity.java` — bookmarks/history list screen (mode via extra).
  - `extensions/ExtensionManagerActivity.java` — WebExtension manager: installs from
    Firefox Add-ons store (AMO), URL/CWS ID, local `.xpi` file picker (SAF), or
    unpacked folder; enable/disable/remove with persisted state.
  - `settings/SettingsActivity.java` + `res/xml/prefs.xml` — homepage, JS toggle,
    anti-fingerprinting options, data clearing.
  - `VodyApplication.java` — owns the shared `GeckoRuntime`, built-in extensions
    (`vodyeval`, `vodyprivacy` in `assets/extensions/`), privacy config push,
    extension state restore.
  - `BrowseStore.java` — JSON persistence for bookmarks/history/extensions/privacy.
- GeckoView is pinned to `134.0.20250113121357` (newer releases need compileSdk >= 37).
- Native libs are restricted to `arm64-v8a` (`ndk.abiFilters`).

## Conventions

- Java 17, Groovy DSL build scripts, Material 3 components, bottom sheets over dialogs.
- Vector icons use white fill (`@android:color/white`) and get tinted at usage sites
  (`app:tint="..."`), so they work in light and dark themes.
- Colors live in `values/colors.xml` (light) and `values-night/colors.xml` (dark);
  keep both in sync when adding tokens.
- Never commit secrets: keystores (`*.jks`), `.env`, passwords.

## Signing & releases

Release builds are signed by CI using repo secrets (never store keys in git):

| Secret            | Meaning                                  |
|-------------------|------------------------------------------|
| `KEYSTORE_BASE64` | base64 of the release keystore (.jks)    |
| `KEYSTORE_PASSWORD` | keystore password                      |
| `KEY_ALIAS`       | key alias (`vody`)                       |
| `KEY_PASSWORD`    | key password                             |

`.github/workflows/release.yml`: run manually via `gh workflow run release.yml`
(optionally with `version_name`). It bumps the patch version from the latest `v*` tag
when no input is given, uses the workflow run number as `versionCode`, builds a signed
release APK, verifies it with apksigner, and publishes a GitHub Release.

Local override without CI:
```bash
VERSION_NAME=1.2.3 VERSION_CODE=99 KEYSTORE_FILE=/path/vody-release.jks \
KEYSTORE_PASSWORD=... KEY_ALIAS=vody KEY_PASSWORD=... ./gradlew assembleRelease
```

The keystore backup lives outside the repo at `~/keystores/vody-release.jks`
(credentials noted in `~/keystores/vody-signing-info.txt`). Losing it means users
cannot upgrade in place — back it up.

## Verification checklist before committing

1. `./gradlew assembleDebug testDebugUnitTest` passes.
2. No hardcoded colors where theme attributes exist (dark-mode safety).
3. New user-facing strings go in `values/strings.xml`.
