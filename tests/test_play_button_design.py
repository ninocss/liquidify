import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

class PlayButtonDesignTest(unittest.TestCase):
    def test_no_muddy_overlay_in_glass_panels(self):
        source = (ROOT / 'extension/src/main/kotlin/dev/nino/squareglass/GlassPanels.kt').read_text()
        # Must not overlay ink black tint onto light mode buttons
        self.assertNotIn("drawRect(Color(SquarePalette.ink(dark)).copy(alpha=.10f))", source)
        # Emphasized buttons should have high-contrast crisp white glass in light mode
        self.assertIn("emphasized", source)

    def test_no_lens_distortion_on_flat_backdrops(self):
        source = (ROOT / 'extension/src/main/kotlin/dev/nino/squareglass/GlassPanels.kt').read_text()
        # Flat backdrop must not apply lens refraction
        self.assertNotIn("lens(", source)

    def test_details_button_uses_clean_capsule_radius(self):
        source = (ROOT / 'extension/src/main/java/dev/nino/squareglass/SquareDetails.java').read_text()
        # For 48dp button, capsule radius should be 24dp
        self.assertTrue("PageGlass.install(view, 24, primary)" in source or "PageGlass.install(view,24,primary)" in source)

    def test_player_buttons_remove_circular_press_ripple(self):
        native_glass = (ROOT / 'extension/src/main/kotlin/dev/nino/squareglass/NativeGlass.kt').read_text()
        # Custom playerIconButton must disable Material 3 ripple indication to eliminate white/black circle
        self.assertIn("fun playerIconButton(", native_glass)
        self.assertIn("indication = null", native_glass)

        # Patch 078 must hook playerIconButton in place of stock Material 3 IconButton
        patch_078 = (ROOT / 'experimental-patches/078-square-player-glass-buttons.patch').read_text()
        self.assertIn("Ldev/nino/squareglass/NativeGlass;->playerIconButton(", patch_078)
        self.assertIn("Landroidx/compose/material3/IconButtonKt;->IconButton(", patch_078)

if __name__ == '__main__':
    unittest.main()
