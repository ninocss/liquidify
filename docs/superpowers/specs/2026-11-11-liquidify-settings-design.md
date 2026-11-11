# Liquidify In-App Settings Subscreen Design Spec

## Intent & The Why

Liquidify currently injects its in-app preferences directly into TIDAL's main settings `LazyColumn` via `DesignEntry.kt`. The current implementation relies on multiple levels of nested inline accordions:
1. An expandable top-level row in TIDAL's main settings list ("Customize Design, Gestures & Modules").
2. Seven category cards expanding within the same list item.
3. Nested expandable rows inside each card (`CardRange` for sliders, `CardChoice` with radio buttons).

This causes scroll jumps, visual clutter in TIDAL's main settings, and awkward multi-click interactions for everyday slider adjustments (e.g., tweaking blur or refraction). Furthermore, users have no visual feedback on how adjustments affect the glass appearance without navigating away to the player or home screen.

This redesign elevates Liquidify's settings into a dedicated, elegant full-screen subscreen with a clean entry point in TIDAL's settings, a live glass preview card, and direct, modern controls (visible sliders with numerical badges and segmented pill buttons).

## Goals, Non-Goals, and Anti-Goals

### Goals
- **Single, clean entry point:** TIDAL's main settings list displays exactly one row for `Liquidify` with title, subtitle, version badge (`v1.0 Beta`), and chevron `›`.
- **Dedicated Full-Screen Subscreen:** Tapping the row opens a full-screen Compose overlay screen featuring a sticky top bar (`← Liquidify Settings`), status bar integration, and hardware back button (`BackHandler`) support.
- **Live Glass Preview:** A visual card at the top of the subscreen rendering sample content through Liquidify's glass backdrop, updating in real time as blur, opacity, and refraction are adjusted.
- **Direct & Modern Controls:**
  - Segmented pill buttons for theme (`System`, `Light`, `Dark`) and glass style (`Liquid`, `Frosted`, `Off`).
  - Directly visible sliders with live value badges (`16 dp`, `65 %`, `40 %`) for blur, opacity, and lens refraction—no accordion expanding needed.
  - Segmented pills for track swipe actions (`Off`, `Play Next`, `Queue`, `Favorite`).
  - Clean Material3 switches for component toggles and reduced motion.
- **Audio & Maintenance Actions:**
  - Move the "Output Device Picker" action button inside the subscreen under "Audio & Streaming".
  - Clean reset actions ("Reset Listening Profile" with toast, "Reset Design to Defaults" with activity reload).

### Non-Goals
- Adding new `SharedPreferences` keys or redesigning backend settings logic.
- Redesigning TIDAL's own native settings rows (e.g. Account, Streaming quality).
- Localizing to German (English was chosen for consistency with project standards).

### Anti-Goals
- No nested accordions for sliders or basic choices.
- No cluttering of TIDAL's main settings screen with multiple separate Liquidify rows (e.g. standalone "Output" row is removed).
- No blocking or freezing of the main UI thread during live shader adjustments.

## Constraints
- **Target App:** TIDAL 2.200.0 (versionCode 10004).
- **Android Level:** Android 13+ (API 33+).
- **Libraries Available:** Jetpack Compose Runtime 1.10.2, Compose UI 1.9.2, Material3 1.3.2, Foundation 1.9.2 (all `compileOnly`).
- **Compatibility:** Must strictly preserve existing `SquareSettings` preferences keys (`theme`, `preset`, `blur`, `opacity`, `lens`, `reduceMotion`, `nav`, `mini`, `player`, `miniGestures`, `swipeRight`, `swipeLeft`, `homeActions`, `learnTime`).
- **Accent Color:** Liquid Purple `0xFF8054D9`.

## Approaches Considered

1. **Inline Accordion Optimization (Rejected):**
   - Keep settings inline in TIDAL's LazyColumn, but flatten cards and replace sub-accordions with tabs.
   - *Why rejected:* The list remains cluttered, scrolling performance in TIDAL's main settings page is impacted, and there is insufficient screen real estate for a dedicated live preview card.
2. **Modal BottomSheet (Considered):**
   - Use `ModalBottomSheet` to slide up settings from the bottom.
   - *Why rejected:* BottomSheets with multiple deep categories and sliders can conflict with vertical scroll gestures, dismiss gestures, and slider touch bounds.
3. **Dedicated Full-Screen Subscreen with Native AppBar (Selected):**
   - Tap row in main settings -> opens full-screen overlay with top bar, back button, live preview, and categorized cards.
   - *Why selected:* Matches standard Android sub-page navigation, allows full vertical scrolling with zero gesture conflicts, provides room for a live glass visualizer, and completely unburdens the main TIDAL settings list.

## UI Architecture & Layout

### 1. Main Settings List Hook (`DesignEntry.append`)
- Condition: Main settings page identified by offline mode callback `jm.f.a`.
- Single item injected:
  - `key = "squareglass.design.entry"`
  - Title: "Liquidify"
  - Subtitle: "Customize Design, Gestures & Modules"
  - Badge: "v1.0 Beta"
  - Chevron: `›`
  - OnClick: sets `isSubscreenOpen = true`.
- (The previous `squareglass.output` row is omitted from the main list and moved inside the subscreen).

### 2. Full-Screen Subscreen (`LiquidifySettingsSubscreen`)
- Container: When `isSubscreenOpen == true`, renders a full-screen surface (`Modifier.fillMaxSize()`) backed by `BackHandler { isSubscreenOpen = false }`.
- Top App Bar:
  - Back IconButton (`←`) dismissing the subscreen.
  - Title: "Liquidify Settings" (Font: `SquareTypography.family`, SemiBold).
  - Subtitle: "v1.0 Beta"
- Scrollable Content Column:
  - **Live Preview Card:**
    - Box with sample background gradient / mock album artwork + text.
    - Glass overlay applied using `GlassPanels.panel` with dynamic parameters from `SquareSettings`.
  - **CategoryCard "Design & Appearance":**
    - `SegmentedChoiceRow` for Color Scheme: [ System | Light | Dark ]
    - `SegmentedChoiceRow` for Glass Style: [ Liquid | Frosted | Off ]
    - `CardSlider` for Blur Strength (0 to 24 dp)
    - `CardSlider` for Surface Opacity (20% to 90%)
    - `CardSlider` for Refraction / Lens (0% to 100%)
    - `CardToggle` for Reduce Motion
  - **CategoryCard "Navigation & Surfaces":**
    - `CardToggle` for Glass Navigation Bar
    - `CardToggle` for Glass Mini-Player
    - `CardToggle` for Glass Full Player
  - **CategoryCard "Player & Gestures":**
    - `CardToggle` for Mini-Player Swipe Gestures
    - `SegmentedChoiceRow` for Track Swipe Right: [ Off | Play Next | Queue | Favorite ]
    - `SegmentedChoiceRow` for Track Swipe Left: [ Off | Play Next | Queue | Favorite ]
  - **CategoryCard "Home & Listening Habits":**
    - `SegmentedChoiceRow` for Home Quick Actions: [ Recommendations | Autoplay & History ]
    - `CardToggle` for Time-of-Day Habits
    - `ActionRow` for "Reset Listening Profile" (clears local playback history)
  - **CategoryCard "Audio & Streaming":**
    - `ActionRow` for "Output Device Picker" (opens `SquareOutput.showFromSettings`)
  - **CategoryCard "System & Maintenance":**
    - `ActionRow` for "Reset Design to Defaults" (clears prefs & recreates activity)
