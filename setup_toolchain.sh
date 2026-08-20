#!/usr/bin/env bash
# Install a JDK 17 + Android SDK + Gradle into the user's home dir (no sudo).
set -e
export HOME=/home/baba01hacker
export TMPDIR=$HOME/.toolchain_tmp
mkdir -p "$TMPDIR"

echo "[1/4] Installing Temurin JDK 17..."
JDK_DIR="$HOME/jdk17"
if [ ! -d "$JDK_DIR" ]; then
  cd "$TMPDIR"
  curl -L -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
  mkdir -p "$JDK_DIR"
  tar -xzf jdk.tar.gz -C "$JDK_DIR" --strip-components=1
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

echo "[2/4] Installing Android command-line tools + SDK platform 35 + build-tools..."
SDK_DIR="$HOME/android-sdk"
mkdir -p "$SDK_DIR/cmdline-tools"
cd "$TMPDIR"
if [ ! -f "$SDK_DIR/cmdline-tools/bin/sdkmanager" ]; then
  curl -L -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q -o cmdtools.zip -d "$SDK_DIR/cmdline-tools"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
fi
export ANDROID_HOME="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$PATH"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"

echo "[3/4] Installing Gradle 8.7 binary (to generate wrapper)..."
GRADLE_DIR="$HOME/gradle-8.7"
if [ ! -d "$GRADLE_DIR" ]; then
  cd "$TMPDIR"
  curl -L -o gradle.zip "https://services.gradle.org/distributions/gradle-8.7-bin.zip"
  unzip -q -o gradle.zip -d "$HOME"
fi

echo "[4/4] Generating ./gradlew wrapper for the project..."
cd /home/baba01hacker/proj/vody-browser
"$GRADLE_DIR/bin/gradle" wrapper --gradle-version 8.7

echo "TOOLCHAIN_DONE JAVA_HOME=$JAVA_HOME ANDROID_HOME=$ANDROID_HOME"
