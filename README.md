# ADWIO Player — Clean Master

Clean Android source baseline for ADWIO Player.

## Baseline
- Version: 5.0.5
- Package: `com.adwio.player`
- Source baseline: original ADWIO 5.0.5 Premium Continuity snapshot
- Cleaned from historical update ZIPs, patch packages, recovery files, and obsolete workflows.

## Repository structure
- `app/` — Android application source
- `build.gradle.kts` — root Gradle configuration
- `settings.gradle.kts` — Gradle settings
- `gradle.properties` — Gradle properties
- `.github/workflows/build-apk.yml` — APK build workflow

## Build
Open **Actions → Build Signed APK** to build the application.

This repository is the official clean baseline for future ADWIO development.
New changes should be applied directly to the source and validated with a build before the next change.
