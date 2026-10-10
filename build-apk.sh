#!/bin/bash
# BRAHM Office — one-click APK build (macOS / Linux)
# Brahm InnerBrain Technologies Pvt. Ltd. — Developed by Sandeep Kushwah
cd "$(dirname "$0")" || exit 1

echo "============================================"
echo "  BRAHM Office — building APK"
echo "============================================"

# Locate Android SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
  for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "/usr/local/share/android-sdk"; do
    if [ -d "$d" ]; then export ANDROID_HOME="$d"; break; fi
  done
fi
SDK="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  echo "ERROR: Android SDK not found. Install Android Studio, or set ANDROID_HOME."
  echo "Then run this script again."
  exit 1
fi
echo "Using Android SDK: $SDK"
echo "sdk.dir=$SDK" > local.properties

chmod +x ./gradlew
echo "Building release APK (first run downloads Gradle + dependencies)…"
./gradlew clean assembleRelease --no-daemon || { echo "BUILD FAILED"; exit 1; }

APK=$(find app/build/outputs/apk/release -name "*.apk" | head -1)
echo "============================================"
if [ -n "$APK" ]; then
  echo "  SUCCESS!"
  echo "  APK: $APK"
  cp "$APK" "./BRAHM-Office-1.1.apk" 2>/dev/null && echo "  Copied to: ./BRAHM-Office-1.1.apk"
else
  echo "  Build finished but APK not found — check output above."
fi
echo "============================================"
