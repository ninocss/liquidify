import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

class LightModeSurfacesTest(unittest.TestCase):
    def test_glass_host_synchronizes_window_and_decor_background(self):
        source = (ROOT / 'extension/src/main/kotlin/dev/nino/squareglass/GlassHost.kt').read_text()
        self.assertIn('SquarePalette.base(', source)
        self.assertIn('activity.window.setBackgroundDrawable(', source)
        self.assertIn('activity.window.statusBarColor =', source)
        self.assertIn('activity.window.decorView.setBackgroundColor(', source)
        self.assertIn('page?.setBackgroundColor(', source)

    def test_system_bar_theme_applies_base_color_to_window(self):
        source = (ROOT / 'extension/src/main/java/dev/nino/squareglass/SystemBarTheme.java').read_text()
        self.assertIn('SquarePalette.base(', source)
        self.assertIn('window.setBackgroundDrawable(', source)
        self.assertIn('window.setStatusBarColor(', source)
        self.assertIn('decor.setBackgroundColor(', source)

if __name__ == '__main__':
    unittest.main()
