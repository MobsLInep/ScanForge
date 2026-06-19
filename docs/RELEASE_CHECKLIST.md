# ScanForge — Release Checklist (Phase 8: launch readiness)

Status legend: ✅ done · 🟡 scaffolded / follow-up · ⬜ to do before public launch

## Build & signing
- ✅ `applicationId` `com.scanforge.app`, `versionCode 1`, `versionName "1.0.0"`, `minSdk 26`, `target/compileSdk 35`.
- ✅ Release signing config reads `keystore.properties` (kept out of VCS); falls back to debug signing when absent so CI/dev still assemble.
- ✅ R8 enabled on release: `isMinifyEnabled = true`, `isShrinkResources = true`, with curated `proguard-rules.pro` (kotlinx.serialization, OpenCV JNI, ML Kit, persisted enum names, PDF writer).
- ✅ `assembleRelease` produces a **signed** APK (verified with `apksigner verify`).
- ⬜ Generate a Play **upload key** / enroll in Play App Signing; replace the throwaway keystore.
- 🟡 App Bundle (`bundleRelease`) for Play upload — buildable via the same config; not generated this phase.

## Performance & stability
- ✅ Baseline profile shipped via `androidx.profileinstaller` (`app/src/main/baseline-prof.txt`, startup path).
- ✅ `StrictMode` thread + VM policies enabled in **debug** (penaltyLog) to catch main-thread I/O / leaks.
- ✅ `LeakCanary` wired as `debugImplementation` (auto-installs; never ships in release).
- ✅ Heavy work off the main thread (repos use `Dispatchers.IO`/`Default`; OCR/export via WorkManager).
- 🟡 Macrobenchmark module + generated (measured) baseline profile — deferred (emulator numbers are noisy); the hand-authored profile is in place.

## App identity
- ✅ Adaptive launcher icon (`mipmap-anydpi-v26`): slate background + forge-amber scanner-bracket/spark foreground + monochrome (themed-icon) layer.
- ✅ Android 12 splash via `core-splashscreen` (`Theme.ScanForge.Starting`, `installSplashScreen()`), back-ported to API 26.
- ⬜ Play Store **512×512** icon + **1024×500** feature graphic + phone/tablet screenshots (export from the in-app assets).

## Accessibility & localization
- ✅ `android:supportsRtl="true"`; content descriptions on icon buttons; 48dp touch targets via Sf components.
- ✅ Localization plumbing proven: `values-hi` scaffold (nav, onboarding, permissions, home, settings/privacy); rest fall back to English.
- 🟡 Full Hindi translation + native review; complete RTL + large-font visual pass on every screen.

## Privacy & compliance
- ✅ Crash + analytics are interface seams with **no-op** implementations, gated behind off-by-default opt-in toggles (Settings → Privacy).
- ✅ `docs/PRIVACY_POLICY.md` (on-device-first messaging).
- ✅ `docs/DATA_SAFETY.md` (Play Data Safety form answers).
- ⬜ Host the privacy policy at a public URL and link it in the Play listing.

## CI / quality gates
- ✅ `.github/workflows/ci.yml`: lint + unit tests (all modules) + `assembleDebug`/`assembleRelease` on PR.
- 🟡 Screenshot tests (Roborazzi) job present but disabled (`if: false`) pending committed goldens.

## Robustness
- ✅ Low-storage guard before export (`StorageGuard`, unit-tested) with a friendly in-sheet message.
- ✅ Corrupted images skipped gracefully on import (per-URI `runCatching`); OCR failures surface as a "failed" page state; camera-permission denial has a rationale + import fallback.
- 🟡 Extend the storage guard to backup/import write paths; huge-PDF streaming export (currently holds compressed pages in memory).

## Pre-submission smoke test
- ⬜ Fresh install → onboarding → scan → OCR → export searchable PDF → backup/restore on a physical device.
- ⬜ Verify the release (minified) build runs the OCR + PDF-export paths (R8 keep-rule coverage).
