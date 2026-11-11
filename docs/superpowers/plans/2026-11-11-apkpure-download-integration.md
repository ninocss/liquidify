# Plan: Automated Stock TIDAL Download via APKPure in Liquidify Manager

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate automated stock TIDAL APK downloading and split merging directly from APKPure into Liquidify Manager, resolving the 404 download error and eliminating the need for manual Google Drive APK downloads, while preserving all Liquidify branding, 99 smali patches, and TIDAL 2.200.0 compatibility.

**Architecture:** Port the lightweight Protobuf reader, APKPure CMS catalog client, APK signature verifier, and `ARSCLib`-based Split APK merger from upstream RadiantLyrics into Liquidify's Manager. In `DownloadTidalStep`, invoke `TidalApkSource` when no local or cached APK is found, downloading the official TIDAL 2.200.0 XAPK from APKPure, validating signatures, merging splits into a unified APK, and caching it.

**Tech Stack:** Kotlin, Ktor HTTP Client, Gradle / AGP, `ARSCLib` (`io.github.reandroid:ARSCLib:1.4.0`), `apksig` (`com.android.tools.build:apksig:9.0.0`), Koin DI.

---

## Global Constraints
- Target host: TIDAL 2.200.0 (`versionCode = 10004`).
- All 99 smali patches, `PreventPlayStoreUpdates`, and Liquidify Design / Player extensions must remain intact and functional.
- Zero breaking changes to `LiquidifyReleaseTest` or Python test suites.
- Code and user-facing messages must be in English.

---

## Tasks

### Task 1: Add ARSCLib Dependency & Gradle Packaging
**Files:**
- Modify: `radiant-base/Manager/gradle/libs.versions.toml`
- Modify: `radiant-base/Manager/app/build.gradle.kts`

- [ ] Add `arsclib = "1.4.0"` to `[versions]` and `arsclib = { module = "io.github.reandroid:ARSCLib", version.ref = "arsclib" }` to `[libraries]` in `libs.versions.toml`.
- [ ] Add `arsclibRaw` configuration and `strippedArsclib` task in `build.gradle.kts` (stripping conflicting Android stub classes).
- [ ] Wire `arsclibRaw(libs.arsclib)` and `implementation(files(strippedArsclib))` into `dependencies`.
- [ ] Verify Gradle configuration by executing a dry-run / assemble check.

---

### Task 2: Implement Protobuf Reader & APKPure API Client
**Files:**
- Create: `radiant-base/Manager/app/src/main/kotlin/com/meowarex/rlmobile/network/utils/ProtoReader.kt`
- Create: `radiant-base/Manager/app/src/main/kotlin/com/meowarex/rlmobile/network/models/ApkPureVersion.kt`
- Create: `radiant-base/Manager/app/src/main/kotlin/com/meowarex/rlmobile/network/services/ApkPureService.kt`

- [ ] Create `ProtoReader.kt` and `ProtoField` implementing zero-dependency binary Protobuf parsing.
- [ ] Create `ApkPureVersion` data model with `versionName`, `versionCode`, `isXapk`, `size`, `sha1`, `url`.
- [ ] Create `ApkPureService` querying `https://api.pureapk.com/m/v3/cms/app_version` with device headers (`x-cv`, `x-sv`, `x-abis`) and parsing the version catalog.
- [ ] Implement `listZipEntries(url)` in `ApkPureService` using HTTP range requests to determine variant ABIs.

---

### Task 3: Implement Signature Verification & Split APK Merger
**Files:**
- Create: `radiant-base/Manager/app/src/main/kotlin/com/meowarex/rlmobile/patcher/util/TidalSignature.kt`
- Create: `radiant-base/Manager/app/src/main/kotlin/com/meowarex/rlmobile/patcher/util/SplitApkMerger.kt`

- [ ] Implement `TidalSignature.kt` checking certificates against Aspiro AS (`2e8a4794...`) and rotated Play Store keys (`2e74cf21...`).
- [ ] Implement `SplitApkMerger.kt` loading split directories into `ApkBundle`, stripping split-only manifest attributes, and merging modules into a unified standalone APK.

---

### Task 4: Implement TidalApkSource & Wire Koin DI
**Files:**
- Create: `radiant-base/Manager/app/src/main/kotlin/com/meowarex/rlmobile/manager/TidalApkSource.kt`
- Modify: `radiant-base/Manager/app/src/main/kotlin/com/meowarex/rlmobile/ManagerApplication.kt`

- [ ] Create `TidalApkSource` to query `ApkPureService`, pick the matching variant for `versionCode` (e.g. 10004), download via `KtorDownloadManager`, verify signatures, merge splits (if XAPK), and check final package info.
- [ ] Register `singleOf(::ApkPureService)` and `singleOf(::TidalApkSource)` in `ManagerApplication.kt`.

---

### Task 5: Integrate Automated Download into DownloadTidalStep
**Files:**
- Modify: `radiant-base/Manager/app/src/main/kotlin/com/meowarex/rlmobile/patcher/steps/download/DownloadTidalStep.kt`

- [ ] Inject `TidalApkSource` into `DownloadTidalStep`.
- [ ] In `DownloadTidalStep.execute()`, attempt `source.download(versionCode, file, log, onProgress)` before failing back to remote URL or manual selection.
- [ ] Update error messaging to explain that APKPure download was attempted.

---

### Task 6: Testing & Verification
**Files:**
- Modify / Test: `radiant-base/Manager/app/src/test/kotlin/com/meowarex/rlmobile/LiquidifyReleaseTest.kt`

- [ ] Add unit test verifying `ProtoReader` and `ApkPureService.parseVersions` on sample data.
- [ ] Run Gradle unit test suite: `./gradlew.bat :app:testDebugUnitTest`.
- [ ] Run Python test suite: `python -m unittest discover -s tests -v`.
- [ ] Run release validation: `python tools/validate_release.py`.
