# BRAHM Auto-Editor — Android app (source)

Standalone one-tap AI video editor. Same engine as the web/Windows version,
on-device via **FFmpeg-kit**; transcription via **Groq Whisper** (key in the app).

- `applicationId` : `com.brahm.autoeditor`
- `versionName`   : **1.1**   ·  `versionCode` : **1**  (fixed pre-release)
- Free, no pro-locks. NMC-compliant: "AI-assisted" mark auto, no guarantee/claim wording.

## Features
- Theme picker (5 looks) · multi-language animated captions (10 languages bundled)
- Color grade + auto-zoom + BRAHM branding
- Audio: noise-remove (off/light/medium/strong) · voice-clear · pitch (±6 semitones)
- Groq API key saved on-device · or paste a transcript (no AI)

## Build

**Easiest — Android Studio:** `File ▸ Open` this `android/` folder → wait for Gradle sync →
`Build ▸ Build APK(s)` (or Run on a device). Android Studio downloads the SDK/deps.

**Command line** (machine with Android SDK + internet):
```bash
cd android
# if gradle-wrapper.jar is missing, create the wrapper once:
gradle wrapper --gradle-version 8.9
./gradlew assembleRelease
# APK -> app/build/outputs/apk/release/app-release.apk  (unsigned; sign for Play)
# or a quick installable debug build:
./gradlew assembleDebug    # -> app/build/outputs/apk/debug/app-debug.apk
```

> ⚠️ **FFmpeg-kit dependency:** `com.arthenica:ffmpeg-kit-full:6.0-2` was retired from
> Maven Central in 2025. If Gradle can't resolve it, use a community mirror / self-hosted
> AAR and point `settings.gradle.kts` → `dependencyResolutionManagement.repositories` at it
> (a `maven { url = ... }` of the mirror, or drop the `.aar` into `app/libs/` and use
> `implementation(files("libs/ffmpeg-kit-full-6.0-2.aar"))` plus its `smart-exception` deps).
> The rest of the app is standard AndroidX/Material and resolves normally.

## Background remove (optional)
ML Kit Selfie Segmentation is included (`com.google.mlkit:segmentation-selfie`) for a
future on-device bg-remove pass (the web version already does it with ISNet). The core
one-tap flow (captions/grade/zoom/audio/branding) is wired and complete.

## Structure
```
android/
  app/src/main/java/com/brahm/autoeditor/
    MainActivity.kt      UI + flow
    AutoEditEngine.kt    FFmpeg-kit pipeline (mirrors web engine)
    Captions.kt          animated ASS captions (multi-language)
    Themes.kt            5 theme presets + language->font map
    AudioChain.kt        denoise / voice-clear / pitch
    GroqTranscriber.kt   Groq Whisper word timings
    Prefs.kt             saves Groq key + options
  app/src/main/assets/fonts/   Poppins + Noto Indic (bundled)
```
*Brahm InnerBrain Technologies Pvt. Ltd. · Developed by Sandeep Kushwah*
