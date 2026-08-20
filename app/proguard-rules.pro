# Vody Browser ProGuard / R8 rules
# -------------------------------------------------------------
# GeckoView relies heavily on reflection / JNI and loads code by
# name, so we keep its whole API surface. R8 minification/shrinking
# still applies to OUR app code and the AndroidX/JSON libraries.

# GeckoView: keep every class/member so the native engine can find
# the Java symbols it expects at runtime.
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }

# Keep line numbers for saner crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Our model classes are serialized via org.json to disk; keep them
# (needed only if you obfuscate — harmless either way).
-keep class org.vody.browser.Bookmark { *; }
-keep class org.vody.browser.ExtensionInfo { *; }
-keep class org.vody.browser.Tab { *; }

# Don't warn about GeckoView's own (expected) missing-reference notes.
-dontwarn org.mozilla.geckoview.**

# snakeyaml (pulled in transitively) references java.beans which doesn't exist on Android.
# It's never actually invoked at runtime on Android, so just silence R8.
-dontwarn java.beans.**
