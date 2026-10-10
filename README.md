# BRAHM Office

Brahm InnerBrain Technologies Pvt. Ltd. — Developed by Sandeep Kushwah
`com.brahm.office` · versionName **1.1**

An all-in-one document app for Android: PDF reader + editor, and a high-level
Excel engine, with Word / PowerPoint viewing and more.

---

## Build the APK (one step)

You need the **Android SDK** installed once (comes with Android Studio).

**macOS:** double-click `build-apk.command` (or run it in Terminal).
**Linux/Terminal:** `./build-apk.sh`

The script finds your SDK, writes `local.properties`, and runs a release build.
On success it leaves **`BRAHM-Office-1.1.apk`** in this folder.

Manual build instead:
```
./gradlew assembleRelease      # release APK
./gradlew assembleDebug        # debug APK (installs over debug builds)
```
First run downloads the Android Gradle Plugin, Gradle 8.14.3 and dependencies
from Google's servers (needs normal internet — this is why it must build on
your machine, not inside the restricted cloud container).

---

## What's inside (this build)

**PDF**
- Reader: continuous scroll, pinch-zoom, page counter, dark background,
  opens password-protected PDFs (via PdfRenderer).
- Editor (PdfBox, pure-Java, no NDK): rotate all / one page, delete page,
  extract page range, split every page, encrypt with password, extract text,
  share. Merge is available in the engine (`PdfTools.merge`).

**Excel engine** (`com.brahm.office.sheet`)
- Virtualized grid: **702 columns (A–ZZ) × 10,000 rows**, draws only visible
  cells → smooth at full size. Sparse storage (empty cells cost nothing).
- Formula engine: full tokenizer + recursive-descent parser + evaluator,
  cycle-safe, cached. Operators `+ - * / ^ & % = <> < > <= >=`, ranges `A1:B2`.
- **~90 functions** across Math, Trig, Statistical, Logical, Lookup
  (VLOOKUP/HLOOKUP/INDEX/MATCH/CHOOSE), Text, Info, Date, Financial.
  Add more in `sheet/Functions.kt` — one line each.
- Open/save **.xlsx** and **.csv** (fastexcel, pure-Java). Save also shares out.

**File browser** — Recent / All files / Tools, search, tap to open.

**Open-with / Share** — tapping a PDF, DOC/DOCX, XLS/XLSX/CSV, PPT/PPTX or TXT
anywhere on the phone offers **BRAHM Office**, and it appears in the share sheet.
The user can set it as the default handler per type.

**Branding** — navy PDF+Excel adaptive icon; "Developed by Sandeep Kushwah".

---

## Planned next phases (not in this build yet)
- Full 400+ Excel functions, charts, conditional formatting
- Word (DOCX) editing, PowerPoint (PPTX) editing
- Convert hub: PDF ↔ Office, Image → PDF

## Tech
Kotlin · AGP 8.7.3 · Gradle 8.14.3 · minSdk 24 · targetSdk 35
PdfBox-Android · fastexcel · Material 3. No native/NDK code.
