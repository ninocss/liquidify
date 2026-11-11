# Nav Bar Reflection, Playlist Play Button, and Light Mode Flash Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the upstream AndroidLiquidGlass reflection on the bottom navigation tab switcher, eliminate the buggy/muddy appearance of the Play Button in Playlist View, and resolve black flashing behind the "Home" toolbar card and status bar in White (Light) mode.

**Architecture:**
1. Align `LiquidBottomTabs` with the upstream `AndroidLiquidGlass` reference catalog by restoring the second tab row captured into `tabsBackdrop` with `ColorFilter.tint(accentColor)` so that the draggable indicator lens (`rememberCombinedBackdrop`) refracts the icons and content underneath.
2. Clean up detail page controls in `SquareDetails.java` and `GlassPanels.kt` by replacing the muddy 10% black overlay on primary buttons in light mode with high-contrast, crisp surfaces and removing flat-color lens distortion.
3. Configure `MainActivity`'s Window, DecorView, status bar color, and root fragment container in `GlassHost.kt` and `SystemBarTheme.java` to match `SquarePalette.base(dark)` so White mode never shows the underlying stock `#000000` window background during loading or transitions.

**Tech Stack:** Kotlin, Java, Compose Multiplatform / Android Compose, AndroidLiquidGlass (`backdrop`), Smali, Android SDK 34/35.

**Spec:** User request:
- "Nav bar wieder reflection an dem tabswitcher div hat. Also der soll wieder komplett nativ nach der libary sein."
- "in der Playlist View soll der Play Button nicht so verbugt aussehen."
- "in der Homepage, flashed die Card 'Home' und der Background hinter der Statusleiste schwarz, wenn man im Whitemode ist."

## Global Constraints
- Pinned upstream `AndroidLiquidGlass` tag: `2.0.0` (commit `bebb11a91bd97bf1dabde479f3b332ad9898731f`).
- TIDAL stock target: version 2.200.0 (versionCode 10004).
- Contrast ratio floor: minimum 4.5:1 for readable text and primary controls.
- Maintain ABI compatibility with Radiant Manager and Tidal bytecode.
- All Python unit tests in `tests/` must pass with `JAVA_HOME` pointing to JDK 17+.

## Review Focus
1. Tab switcher dragging across tabs correctly refracts the underlying tab icons and accent tint without rendering a duplicate visible row.
2. Playlist Play button is crisp, centered, and has high contrast in both Dark and Light modes without distorted lens artifacts or muddy gray tinting.
3. Homepage top app bar ("Home") and status bar do not flash black when launching, switching tabs, or pulling to refresh in White mode.
4. Dark mode continues to function properly across all navigation, playlist header, and status bar surfaces.
5. No regressions in build tools (`package_alpha.py`, `build_probe.py`) or unit tests.

---

### Task 1: Restore Native `LiquidBottomTabs` Tabswitcher Reflection

**Files:**
- Modify: `extension/src/main/kotlin/dev/nino/squareglass/material/LiquidBottomTabs.kt`
- Modify: `extension/src/main/kotlin/dev/nino/squareglass/NativeTabs.kt`
- Modify: `tests/test_design_contrast.py:59-72`

**Interfaces:**
- Consumes: `dev.nino.squareglass.backdrop.backdrops.rememberLayerBackdrop`, `dev.nino.squareglass.backdrop.backdrops.rememberCombinedBackdrop`, `dev.nino.squareglass.SquarePalette`, `dev.nino.squareglass.NativeGlass`
- Produces: `dev.nino.squareglass.material.LiquidBottomTabs(selectedTabIndex: () -> Int, onTabSelected: (Int) -> Unit, backdrop: Backdrop, tabsCount: Int, modifier: Modifier, content: @Composable RowScope.() -> Unit)`

- [ ] **Step 1: Write test reflecting native AndroidLiquidGlass tab switcher structure**

Update `tests/test_design_contrast.py` method `test_library_navigation_has_one_animated_icon_row` to verify that `LiquidBottomTabs.kt`:
1. Uses `rememberCombinedBackdrop(backdrop, tabsBackdrop)` on the selector indicator.
2. Uses `.layerBackdrop(tabsBackdrop)` on an invisible layer (`alpha(0f)`) with `ColorFilter.tint(accentColor)` capturing `content` so the indicator has live tab reflections.
3. Retains single accessible row semantics (`clearAndSetSemantics {}` on the reflection capture row).

- [ ] **Step 2: Run test to verify it fails on current implementation**

Run: `python -m unittest tests/test_design_contrast.py`
Expected: FAIL (current `LiquidBottomTabs.kt` omits the reflection capture row and `accentColor`).

- [ ] **Step 3: Update `LiquidBottomTabs.kt` and `NativeTabs.kt` to upstream AndroidLiquidGlass catalog structure**

In `extension/src/main/kotlin/dev/nino/squareglass/material/LiquidBottomTabs.kt`:
1. Define `accentColor` adapting to theme (`if (!dark) Color(0xFF0088FF) else Color(0xFF0091FF)`).
2. Restore Layer 1: Base panel with `drawBackdrop(backdrop, ...)` rendering primary `content = content`.
3. Restore Layer 2: Invisible reflection capture row with `alpha(0f)`, `clearAndSetSemantics {}`, `.layerBackdrop(tabsBackdrop)`, `graphicsLayer(colorFilter = ColorFilter.tint(accentColor))` rendering `content = content`.
4. Restore Layer 3: Indicator box with `translationX`, `gestureModifier`, `dampedDragAnimation.modifier`, and `drawBackdrop(rememberCombinedBackdrop(backdrop, tabsBackdrop), ...)` with lens, chromatic aberration, highlight, shadow, innerShadow, and surface draw (`Color.Black.copy(0.1f)` in light theme, `Color.White.copy(0.1f)` in dark theme).

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests/test_design_contrast.py`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add extension/src/main/kotlin/dev/nino/squareglass/material/LiquidBottomTabs.kt extension/src/main/kotlin/dev/nino/squareglass/NativeTabs.kt tests/test_design_contrast.py
git commit -m "feat(navigation): restore native library tabswitcher reflection in LiquidBottomTabs"
```

---

### Task 2: Redesign and Fix Playlist View Play Button

**Files:**
- Modify: `extension/src/main/java/dev/nino/squareglass/SquareDetails.java:160-205`
- Modify: `extension/src/main/kotlin/dev/nino/squareglass/PageGlass.kt:76-86`
- Modify: `extension/src/main/kotlin/dev/nino/squareglass/GlassPanels.kt:23-44`
- Create: `tests/test_play_button_design.py`

**Interfaces:**
- Consumes: `SquarePalette.isDark()`, `SquarePalette.base()`, `SquarePalette.surface()`, `SquarePalette.ink()`
- Produces: `SquareDetails.controls(View root, int playId, int shuffleId)` with clean high-contrast primary play button and translucent secondary shuffle button.

- [ ] **Step 1: Write unit test for button appearance and contrast**

Create `tests/test_play_button_design.py` verifying:
1. Primary buttons do not receive dark muddy overlay (`drawRect` ink black) in light mode.
2. `GlassPanels.panel` does not apply refractive lens on flat solid backdrops.
3. Button corner radius matches half-height (capsule shape) to prevent boundary clipping.

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests/test_play_button_design.py`
Expected: FAIL.

- [ ] **Step 3: Fix `GlassPanels.kt`, `PageGlass.kt`, and `SquareDetails.java`**

1. In `GlassPanels.kt`:
   - Remove the `lens` effect on flat color backdrops in `panel()` to prevent edge distortion and fringing artifacts.
   - For `emphasized = true` (primary button):
     - In light mode (`!dark`): draw crisp solid white / bright translucent glass (`Color.White.copy(alpha = 0.92f)`) with high contrast against the page base.
     - In dark mode (`dark`): draw elevated vibrant glass (`Color.White.copy(alpha = 0.20f)`).
     - Remove the `drawRect(Color(ink).copy(alpha = .10f))` dirty tint overlay.
2. In `PageGlass.kt`:
   - Use capsule radius `min(width, height) / 2` or `24.dp` for 48dp controls so corners never distort.
3. In `SquareDetails.java`:
   - For `button(play, true)`: ensure foreground text/icon tint maintains high contrast (contrast ratio >= 4.5:1).
   - Ensure `RippleDrawable` uses a clean, responsive color state list.

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests/test_play_button_design.py`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add extension/src/main/java/dev/nino/squareglass/SquareDetails.java extension/src/main/kotlin/dev/nino/squareglass/PageGlass.kt extension/src/main/kotlin/dev/nino/squareglass/GlassPanels.kt tests/test_play_button_design.py
git commit -m "fix(playlist): eliminate buggy play button styling and lens distortion"
```

---

### Task 3: Prevent Black Flashing of "Home" Card and Status Bar in White Mode

**Files:**
- Modify: `extension/src/main/kotlin/dev/nino/squareglass/GlassHost.kt`
- Modify: `extension/src/main/java/dev/nino/squareglass/SystemBarTheme.java`
- Create: `tests/test_light_mode_surfaces.py`

**Interfaces:**
- Consumes: `SquarePalette.base(dark)`, `SquarePalette.isDark()`, `SquareSettings.themeEnabled()`, `Activity.getWindow()`, `Window.getDecorView()`
- Produces: Guaranteed non-black window background, decorView background, status bar background, and root fragment container background in White mode.

- [ ] **Step 1: Write test for window and status bar background synchronization**

Create `tests/test_light_mode_surfaces.py` verifying:
1. `GlassHost.kt` sets `window.setBackgroundDrawable`, `decorView.setBackgroundColor`, `statusBarColor`, `navigationBarColor`, and `fragmentContainer.setBackgroundColor` to `SquarePalette.base(dark)`.
2. `SystemBarTheme.java` applies `baseColor` to window background and status bar color in addition to `setSystemBarsAppearance`.

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests/test_light_mode_surfaces.py`
Expected: FAIL.

- [ ] **Step 3: Update `GlassHost.kt` and `SystemBarTheme.java`**

1. In `extension/src/main/kotlin/dev/nino/squareglass/GlassHost.kt`:
   - When wrapping native Activity, immediately set:
     ```kotlin
     val baseColor = SquarePalette.base(NativeGlass.dark)
     activity.window.setBackgroundDrawable(ColorDrawable(baseColor))
     activity.window.statusBarColor = baseColor
     activity.window.navigationBarColor = baseColor
     activity.window.decorView.setBackgroundColor(baseColor)
     page?.setBackgroundColor(baseColor)
     ```
   - In `SideEffect`, update `statusBarColor`, `navigationBarColor`, `decorView`, and `page` backgrounds whenever `dark` changes.
2. In `extension/src/main/java/dev/nino/squareglass/SystemBarTheme.java`:
   - In `onPreDraw()`, ensure:
     ```java
     int baseColor = SquarePalette.base(SquarePalette.isDark());
     window.setBackgroundDrawable(new ColorDrawable(baseColor));
     window.setStatusBarColor(baseColor);
     window.setNavigationBarColor(baseColor);
     decor.setBackgroundColor(baseColor);
     ```

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests/test_light_mode_surfaces.py`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add extension/src/main/kotlin/dev/nino/squareglass/GlassHost.kt extension/src/main/java/dev/nino/squareglass/SystemBarTheme.java tests/test_light_mode_surfaces.py
git commit -m "fix(theme): eliminate black status bar and home card flash in light mode"
```

---

### Task 4: Integration Build, Packaging, and Emulator Verification

**Files:**
- Modify: `patches/manifest.json` (if new smali classes generated)
- Modify: `patches/patches.zip`
- Modify: `radiant-base/Manager/app/src/main/assets/patches-manifest.json`

**Interfaces:**
- Consumes: Updated Kotlin and Java source code in `extension/`
- Produces: Compiled extension smali, rebuilt patch archive, and verified live APK on emulator.

- [ ] **Step 1: Compile extension and verify full unit test suite**

Run:
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-26.0.2.1"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
python -m unittest discover tests
```
Expected: All tests PASS.

- [ ] **Step 2: Build probe and rebuild patches package**

Run:
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-26.0.2.1"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
python tools/build_probe.py
python tools/package_alpha.py
```
Expected: Clean build without unresolved references.

- [ ] **Step 3: Deploy to emulator and test live behavior**

1. Install updated test build or reload patched Tidal on `emulator-5554`.
2. Verify bottom navigation bar tab switcher reflection when dragging between tabs.
3. Verify Playlist View play button: check clean visual styling without muddy color or distorted borders.
4. Verify Homepage and status bar in White mode: test switching tabs and page reloads, ensuring no black flashing behind status bar or top "Home" card.
5. Capture screenshots of:
   - Bottom navigation tab switcher with reflection.
   - Playlist view play button.
   - Homepage top app bar and status bar in White mode.
   Save images to artifact directory.

- [ ] **Step 4: Commit**

```bash
git add patches/ tools/ reports/ radiant-base/
git commit -m "build: package updated patches with navbar reflection, playlist play button, and white mode flash fixes"
```
