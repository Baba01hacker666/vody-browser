# Vody Browser

A fast, customizable, privacy-first Android browser built on **Mozilla GeckoView** (the real
Firefox rendering engine) — no Chromium, no WebView hybrid.

## Engine

- **100% GeckoView** (`org.mozilla.geckoview`). Every tab is a `GeckoSession` inside one shared
  `GeckoRuntime`. There is no Android System WebView and no Chromium in this build.
- Tabs, navigation history and back/forward are handled by the app over GeckoView sessions.
- **WebExtensions** are installed and managed through GeckoView's `WebExtensionController`
  (Chrome Web Store ID/URL or unpacked `file://` paths).
- **On-device DevTools console** evaluates JavaScript in the active session via a built-in
  WebExtension bridge (`app/src/main/assets/extensions/vodyeval`). Full DOM/Network inspection is
  also available over USB through GeckoView's remote debugger (about:debugging / WebIDE).

## Security hardening (ported from WebLibre)

On first launch `VodyApplication` applies WebLibre's "privacy by default" posture via GeckoView
runtime settings (Enhanced Tracking Protection + strict social tracking, Global Privacy Control,
remote debugging for DevTools) and bundles the full enforced preference set in
`app/src/main/assets/weblibre_hardening.json` (telemetry off, HTTPS-first, OCSP required, CRLite,
Kyber/TLS 1.3, referrer cross-site hardening, fingerprinting resistance, etc.). The manifest is
hardened WebLibre-style (`allowBackup=false`, `enableOnBackInvokedCallback`, `queries` block,
non-required camera hardware).

## Build

```bash
export JAVA_HOME=<jdk17> ANDROID_HOME=<android-sdk>   # needs platforms;android-35 + build-tools;35.0.0
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/vody-browser-debug-1.0.0.apk
./gradlew testDebugUnitTest    # unit tests
```

Pinned toolchain: AGP 8.7.2, Gradle 8.9, compileSdk 35, GeckoView 134.0.20250113121357
(pinned because newer GeckoView releases require compileSdk >= 37, which is not yet published).

## Features

- Multi-tab browsing with address bar, reload, back/forward.
- Bookmarks and history (persisted to private app files).
- Extensions manager (install / enable / disable / remove).
- On-device DevTools JS console.
- Settings (homepage, JavaScript toggle, clear browsing data, about).

## Upstream

The previous Chromium-derived tree was removed; this repository is now a self-contained GeckoView
browser. Security-hardening prefs are mirrored from [WebLibre](https://github.com/FaFre/WebLibre)
(AGPL-3.0), used here under the same license.
