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

if __name__ == '__main__':
    unittest.main()
