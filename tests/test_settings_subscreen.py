"""Tests for Liquidify In-App Settings Subscreen architecture and controls."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
KOTLIN = ROOT / "extension/src/main/kotlin/dev/nino/squareglass"
JAVA = ROOT / "extension/src/main/java/dev/nino/squareglass"


class SettingsSubscreenTest(unittest.TestCase):
    def setUp(self):
        self.design_entry = (KOTLIN / "DesignEntry.kt").read_text(encoding="utf-8")
        self.settings = (JAVA / "SquareSettings.java").read_text(encoding="utf-8")

    def test_main_settings_hook_has_single_liquidify_entry_without_output(self):
        """TIDAL main settings must only expose the Liquidify entry, not a standalone Output row."""
        self.assertNotIn('key = "squareglass.output"', self.design_entry,
                         "Standalone squareglass.output must not be appended to the main settings list")
        self.assertTrue(
            'key = "squareglass.design.v2"' in self.design_entry or 'key = "squareglass.design.entry"' in self.design_entry,
            "Main settings list must register the primary Liquidify design entry"
        )

    def test_live_glass_preview_exists_with_panel_and_now_playing(self):
        """LiveGlassPreview must render a live glass panel simulating playback state."""
        self.assertIn("fun LiveGlassPreview", self.design_entry, "LiveGlassPreview composable must be defined")
        self.assertIn("GlassPanels.panel", self.design_entry, "LiveGlassPreview must use GlassPanels.panel")

    def test_direct_card_slider_defined_without_accordion_expansion(self):
        """DirectCardSlider must display slider directly without requiring an expanded state toggle."""
        self.assertIn("fun DirectCardSlider", self.design_entry, "DirectCardSlider composable must be defined")
        # Ensure CardRange with accordion expanded toggle is replaced or DirectCardSlider does not gate Slider behind if(expanded)
        slider_block = re.search(r"fun DirectCardSlider.*?(?=fun\s|\Z)", self.design_entry, re.DOTALL)
        self.assertIsNotNone(slider_block, "DirectCardSlider function block must exist")
        self.assertNotIn("var expanded by remember", slider_block.group(0),
                         "DirectCardSlider must not require click-to-expand accordion state")

    def test_segmented_pill_row_defined_for_discrete_choices(self):
        """SegmentedPillRow must be defined for selecting options via clickable pills."""
        self.assertIn("fun SegmentedPillRow", self.design_entry, "SegmentedPillRow composable must be defined")

    def test_subscreen_handles_back_navigation(self):
        """LiquidifySettingsSubscreen must integrate BackHandler for system back gestures."""
        self.assertIn("fun LiquidifySettingsSubscreen", self.design_entry,
                      "LiquidifySettingsSubscreen composable must be defined")
        self.assertIn("BackHandler", self.design_entry,
                      "LiquidifySettingsSubscreen must wire system BackHandler to onDismiss")

    def test_all_square_settings_keys_are_referenced(self):
        """All functional SquareSettings keys or methods must remain bound in the new subscreen."""
        required_keys = [
            "theme", "blur", "opacity", "lens", "reduceMotion",
            "nav", "mini", "player", "miniGestures", "swipeRight", "swipeLeft",
            "homeActions", "learnTime"
        ]
        for key in required_keys:
            self.assertIn(f'"{key}"', self.design_entry, f"Settings key {key} must be referenced in DesignEntry")
        self.assertTrue("SquareSettings.preset" in self.design_entry or '"preset"' in self.design_entry,
                        "Preset setting must be bound via SquareSettings.preset")


if __name__ == "__main__":
    unittest.main()
