# Liquidify In-App Settings Subscreen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the in-app Liquidify settings within TIDAL from a deeply nested inline accordion into a dedicated full-screen subscreen featuring a live glass preview, direct visible sliders with live value badges, segmented pill buttons, and a single clean entry row in TIDAL's main settings list.

**Architecture:**
1. Clean up TIDAL's main settings hook in `DesignEntry.kt` to inject exactly one row for Liquidify (removing the separate "Output" row from the main list and embedding it in the subscreen).
2. Build modern, direct Compose components in `DesignEntry.kt`: a segmented pill button row (`SegmentedPillRow`), directly visible sliders with live numerical badges (`DirectCardSlider`), and clean toggles (`CardSwitch`).
3. Add a live glass visualizer card (`LiveGlassPreview`) at the top of the subscreen that reacts in real-time to blur, opacity, and refraction adjustments.
4. Render a full-screen subscreen (`LiquidifySettingsSubscreen`) with top app bar, back navigation, status bar awareness, and system `BackHandler` integration.

**Tech Stack:** Kotlin, Jetpack Compose (Runtime 1.10.2, UI 1.9.2, Foundation 1.9.2, Material3 1.3.2), Android SDK 33+, Python test harness.

**Spec:** [docs/superpowers/specs/2026-11-11-liquidify-settings-design.md](file:///c:/Users/nino/Documents/Projects/Tidal/docs/superpowers/specs/2026-11-11-liquidify-settings-design.md)

## Global Constraints
- Target App: TIDAL 2.200.0 (versionCode 10004).
- Android Version floor: Android 13+ (API 33+).
- ABI and SharedPreferences compatibility: All existing keys in `SquareSettings` (`theme`, `preset`, `blur`, `opacity`, `lens`, `reduceMotion`, `nav`, `mini`, `player`, `miniGestures`, `swipeRight`, `swipeLeft`, `homeActions`, `learnTime`) must be strictly preserved.
- Accent color: Liquid Purple `0xFF8054D9`.
- Language: English labels and descriptions.
- All unit tests in `tests/` must pass.

## Review Focus
1. Tapping "Liquidify" in TIDAL settings opens the full-screen subscreen smoothly; pressing system back or tapping the back arrow returns cleanly to TIDAL's main settings without scroll jumping.
2. Sliders for Blur, Opacity, and Refraction are directly visible and adjustable without multi-level accordion expanding, updating both `SquareSettings` and the live preview card instantly.
3. Segmented buttons for Color Scheme, Glass Style, and Track Gestures correctly read and write their corresponding integer preferences.
4. The separate "Output" item is removed from TIDAL's main settings list and works properly from within the subscreen.
5. "Reset Design to Defaults" and "Reset Listening Profile" function correctly without crashing or leaving corrupt state.

---

### Task 1: Create Settings Subscreen Usability & Specification Tests

**Files:**
- Create: `tests/test_settings_subscreen.py`

**Interfaces:**
- Consumes: `extension/src/main/kotlin/dev/nino/squareglass/DesignEntry.kt`, `extension/src/main/java/dev/nino/squareglass/SquareSettings.java`
- Produces: Automated Python test suite verifying structural, component, and preference linkage contracts.

- [ ] **Step 1: Write test suite covering subscreen architecture and controls**

Create `tests/test_settings_subscreen.py` asserting:
1. `DesignEntry.append` injects a single entry `squareglass.design.entry` (or `squareglass.design.v2`) and does NOT inject `squareglass.output` in the main lazy list.
2. `DesignEntry.kt` contains `LiveGlassPreview` composable utilizing `GlassPanels.panel` and reading settings.
3. `DesignEntry.kt` contains direct sliders for `blur`, `opacity`, and `lens` without nested `expanded` accordion state wrappers.
4. `DesignEntry.kt` contains segmented controls for `themeMode` and `preset`.
5. Back navigation is wired to dismiss the subscreen.

- [ ] **Step 2: Run test to verify it fails on the current codebase**

Run: `python -m unittest tests/test_settings_subscreen.py`
Expected: FAIL (missing `LiveGlassPreview`, `squareglass.output` still in main list, sliders still using accordion `expanded`).

- [ ] **Step 3: Commit initial test suite**

```bash
git add tests/test_settings_subscreen.py
git commit -m "test(settings): add verification suite for liquidify settings subscreen"
```

---

### Task 2: Build Direct Controls (Segmented Pills, Direct Sliders, Toggles) in `DesignEntry.kt`

**Files:**
- Modify: `extension/src/main/kotlin/dev/nino/squareglass/DesignEntry.kt`
- Test: `tests/test_settings_subscreen.py`

**Interfaces:**
- Consumes: `dev.nino.squareglass.SquareSettings`, `dev.nino.squareglass.SquarePalette`, `dev.nino.squareglass.NativeGlass`, `dev.nino.squareglass.SliderMotion`
- Produces:
  - `@Composable fun SegmentedPillRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit)`
  - `@Composable fun DirectCardSlider(title: String, subtitle: String? = null, key: String, low: Int, high: Int, current: Int, suffix: String)`
  - `@Composable fun CardToggle(title: String, subtitle: String? = null, key: String, fallback: Boolean = true)`

- [ ] **Step 1: Implement `SegmentedPillRow`**

In `DesignEntry.kt`:
Build a horizontal row of rounded capsule pills inside a subtle container. The selected item has a vibrant highlight (`Color(0xFF8054D9)`), white text, and smooth animation; unselected items have semi-transparent ink text and clickable interaction.

- [ ] **Step 2: Implement `DirectCardSlider`**

In `DesignEntry.kt`:
Replace the collapsible `CardRange` with `DirectCardSlider`:
- Header row with title, optional subtitle, and a live pill badge displaying `$currentVal $suffix` (e.g. `16 dp`, `65 %`, `40 %`).
- Slider bar directly below the header (no click-to-expand required).
- Seamless integration with `SquareSettings.put(key, ...)` and `SliderMotion.thumb`.

- [ ] **Step 3: Verify compilation & component unit checks**

Run: `python -m unittest tests/test_settings_subscreen.py`
Expected: Passes component tests for `DirectCardSlider` and `SegmentedPillRow`.

---

### Task 3: Build `LiveGlassPreview` Card in `DesignEntry.kt`

**Files:**
- Modify: `extension/src/main/kotlin/dev/nino/squareglass/DesignEntry.kt`
- Test: `tests/test_settings_subscreen.py`

**Interfaces:**
- Consumes: `dev.nino.squareglass.GlassPanels`, `dev.nino.squareglass.SquarePalette`, `dev.nino.squareglass.SquareTypography`, `dev.nino.squareglass.SquareSettings`
- Produces: `@Composable fun LiveGlassPreview()`

- [ ] **Step 1: Implement `LiveGlassPreview` composable**

In `DesignEntry.kt`:
1. Render a card with a rich, colorful mock background (gradient mesh simulating album artwork with purple, teal, and deep blue accents).
2. Place a floating glass panel across the card using `GlassPanels.panel(..., 18f)`.
3. Inside the glass panel, render a mini mock playback header: a small album thumbnail, "Now Playing (Preview)", track title "Liquid Atmosphere", and playback indicator.
4. As `SquareSettings` blur, opacity, or lens values change, `DesignState.revision` updates the panel in real time.

- [ ] **Step 2: Verify `LiveGlassPreview` in test suite**

Run: `python -m unittest tests/test_settings_subscreen.py`
Expected: Live preview assertions pass.

---

### Task 4: Implement Full-Screen Subscreen Architecture (`LiquidifySettingsSubscreen`)

**Files:**
- Modify: `extension/src/main/kotlin/dev/nino/squareglass/DesignEntry.kt`
- Test: `tests/test_settings_subscreen.py`

**Interfaces:**
- Consumes: `dev.nino.squareglass.DesignState`, `dev.nino.squareglass.SquareOutput`, `dev.nino.squareglass.NativePlayback`
- Produces: `@Composable fun LiquidifySettingsSubscreen(onDismiss: () -> Unit)`

- [ ] **Step 1: Implement `LiquidifySettingsSubscreen`**

In `DesignEntry.kt`:
1. Wrap in a full-screen Box (`fillMaxSize()`) with background color matching `SquarePalette.base(dark)`.
2. Add `BackHandler(onBack = onDismiss)` for hardware back button support.
3. Top App Bar:
   - Row with 56dp height and system insets padding.
   - Back button (`←`) invoking `onDismiss()`.
   - Title "Liquidify Settings" in `SquareTypography.family`, SemiBold 18sp.
   - Version badge "v1.0 Beta".
4. Vertical scrollable column containing:
   - `LiveGlassPreview()`
   - CategoryCard "Design & Appearance":
     - `SegmentedPillRow` for Color Scheme (`System`, `Light`, `Dark`). Recreates activity on change.
     - `SegmentedPillRow` for Glass Style (`Liquid`, `Frosted`, `Off`).
     - `DirectCardSlider` for Blur Strength (0-24 dp).
     - `DirectCardSlider` for Surface Opacity (20-90%).
     - `DirectCardSlider` for Refraction (0-100%).
     - `CardToggle` for Reduce Motion.
   - CategoryCard "Navigation & Surfaces":
     - Toggles for Glass Navigation Bar, Glass Mini-Player, Glass Full Player.
   - CategoryCard "Player & Gestures":
     - Toggle for Mini-Player Swipe Gestures.
     - `SegmentedPillRow` for Track Swipe Right (`Off`, `Next`, `Queue`, `Fav`).
     - `SegmentedPillRow` for Track Swipe Left (`Off`, `Next`, `Queue`, `Fav`).
   - CategoryCard "Home & Listening Habits":
     - `SegmentedPillRow` for Home Quick Actions.
     - Toggle for Time-of-Day Habits.
     - ActionRow for "Reset Listening Profile".
   - CategoryCard "Audio & Streaming":
     - ActionRow for "Output Device Picker" triggering `SquareOutput.showFromSettings(activity)`.
   - CategoryCard "System & Maintenance":
     - ActionRow for "Reset Design to Defaults" (`SquareSettings.reset()` and activity recreate).
5. Bottom spacer (100dp) ensuring the last card clears navigation bars and insets.

- [ ] **Step 2: Verify subscreen assembly in test suite**

Run: `python -m unittest tests/test_settings_subscreen.py`
Expected: PASS.

---

### Task 5: Clean Main Settings Entry Hook & Remove Standalone Output Row

**Files:**
- Modify: `extension/src/main/kotlin/dev/nino/squareglass/DesignEntry.kt`
- Test: `tests/test_settings_subscreen.py`

**Interfaces:**
- Consumes: `LazyListScope.append(items: List<*>)`
- Produces: Injected single item into TIDAL main settings lazy column.

- [ ] **Step 1: Update `DesignEntry.append`**

1. Remove `scope.item(key = "squareglass.output")`.
2. Keep only `scope.item(key = "squareglass.design.v2") { Entry() }`.
3. In `Entry()`:
   - Maintain state `var isSubscreenOpen by remember { mutableStateOf(false) }`.
   - Render single neat row: Title "Liquidify", subtitle "Customize Design, Gestures & Modules", badge "v1.0 Beta", chevron `›`.
   - Tapping row sets `isSubscreenOpen = true`.
   - When `isSubscreenOpen` is true, render `LiquidifySettingsSubscreen(onDismiss = { isSubscreenOpen = false })`.

- [ ] **Step 2: Run test suite to verify all checks pass**

Run: `python -m unittest tests/test_settings_subscreen.py`
Expected: ALL PASS.

---

### Task 6: End-to-End Build, Linkage, and Regression Verification

**Files:**
- Test: `tests/`
- Build: `:extension`

- [ ] **Step 1: Compile extension Kotlin with Gradle**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :extension:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full Python test suite**

Run: `python -m unittest discover -s tests -v`
Expected: All tests pass with zero failures.

- [ ] **Step 3: Run patch packaging and release validation**

Run: `python tools/validate_release.py`
Expected: Validation passes without errors.

- [ ] **Step 4: Commit all changes**

```bash
git add extension/src/main/kotlin/dev/nino/squareglass/DesignEntry.kt tests/test_settings_subscreen.py
git commit -m "feat(settings): elevate liquidify settings to full-screen subscreen with live preview and direct controls"
```
