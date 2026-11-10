"""WCAG contrast regressions against the shipped semantic paints and film floors."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "extension/src/main/java/dev/nino/squareglass"


def rgb(argb):
    return tuple((argb >> shift) & 255 for shift in (16, 8, 0))


def composite(foreground, background, opacity):
    return tuple(a * opacity + b * (1 - opacity) for a, b in zip(foreground, background))


def luminance(color):
    def linear(value):
        value /= 255
        return value / 12.92 if value <= .04045 else ((value + .055) / 1.055) ** 2.4
    return sum(weight * linear(value) for weight, value in zip((.2126, .7152, .0722), color))


def contrast(a, b):
    a, b = sorted((luminance(a), luminance(b)))
    return (b + .05) / (a + .05)


class DesignContrast(unittest.TestCase):
    def setUp(self):
        self.palette = (JAVA / "SquarePalette.java").read_text(encoding="utf-8")

    def colors(self, role):
        match = re.search(rf"int {role}\(boolean dark\).*?dark \? 0x([a-f0-9]+) : 0x([a-f0-9]+)", self.palette)
        self.assertIsNotNone(match, role)
        return [rgb(int(value, 16)) for value in match.groups()]

    def test_semantic_labels_on_page_and_card(self):
        for paint in ("ink", "secondary", "tertiary"):
            for surface in ("base", "surface"):
                for dark, (foreground, background) in enumerate(zip(self.colors(paint), self.colors(surface))):
                    self.assertGreaterEqual(contrast(foreground, background), 4.5, (paint, surface, dark))

    def test_glass_accessory_worst_case_artwork(self):
        settings = (JAVA / "SquareSettings.java").read_text(encoding="utf-8")
        match = re.search(r"readableOpacity\(boolean dark\).*?dark \? ([.\d]+)f : ([.\d]+)f", settings, re.S)
        self.assertIsNotNone(match)
        for ink, film, opacity in zip(self.colors("ink"), ((51, 52, 58), (255, 255, 255)), map(float, match.groups())):
            # Every grayscale seed, including the extremes of any RGB artwork.
            for value in range(256):
                self.assertGreaterEqual(contrast(ink, composite(film, (value,) * 3, opacity)), 4.5)

    def test_accessory_material_uses_the_checked_contrast_floor(self):
        source=(ROOT/'extension/src/main/kotlin/dev/nino/squareglass/AccessoryGlass.kt').read_text()
        self.assertIn('Color(0xFF33343A) else Color.White',source)
        self.assertIn('SquareSettings.readableOpacity(dark)',source)

    def test_library_navigation_has_native_reflection_layer(self):
        tabs = (ROOT / 'extension/src/main/kotlin/dev/nino/squareglass/NativeTabs.kt').read_text()
        source = (ROOT / 'extension/src/main/kotlin/dev/nino/squareglass/material/LiquidBottomTabs.kt').read_text()
        self.assertIn('IconMotion.icon(', tabs)
        self.assertIn('ColorFilter.tint(accentColor)', source)
        self.assertIn('rememberCombinedBackdrop(backdrop, tabsBackdrop)', source)
        self.assertIn('clearAndSetSemantics {}', source)
        self.assertIn('.layerBackdrop(tabsBackdrop)', source)
        self.assertIn('.alpha(0f)', source)
        # Reflection capture row must capture content with accentColor tint for tab refraction
        capture_idx = source.index('.layerBackdrop(tabsBackdrop)')
        capture_end = source.index('content = content', capture_idx)
        self.assertIn('ColorFilter.tint(accentColor)', source[capture_idx:capture_end])

    def test_static_muted_labels_on_all_native_surfaces(self):
        rows={parts[1]:parts for line in (ROOT/'mappings/resource-palette.tsv').read_text().splitlines()
              if line and not line.startswith('#') for parts in [line.split('\t')]}
        for role in ('contrastDark','contrastDarker','contrastDarkest','gray'):
            for surface in ('baseFill','baseBright','baseBrighter','baseBrightest'):
                for column in (4,5):
                    self.assertGreaterEqual(contrast(rgb(int(rows[role][column],16)),
                        rgb(int(rows[surface][column],16))),4.5,(role,surface,column))

    def test_player_secondary_labels_on_extreme_artwork(self):
        paint = (JAVA / "NativePlayerPaint.java").read_text(encoding="utf-8")
        alpha = int(re.search(r"Math.max\(alpha, (\d+)\)", paint)[1]) / 255
        hook = (ROOT / "tools/prepare_runtime_patch.py").read_text(encoding="utf-8")
        film = float(re.findall(r"# (0\.\d+)f", hook)[-1])
        for ink, background in zip(self.colors("ink"), ((0, 0, 0), (255, 255, 255))):
            for value in range(256):
                surface = composite(background, (value,) * 3, film)
                self.assertGreaterEqual(contrast(composite(ink, surface, alpha), surface), 4.5)

    def test_menu_and_quick_action_labels_on_extreme_artwork(self):
        settings = (JAVA / "SquareSettings.java").read_text(encoding="utf-8")
        match = re.search(r"readableOpacity\(boolean dark\).*?dark \? ([.\d]+)f : ([.\d]+)f", settings, re.S)
        pane = (ROOT / "extension/src/main/kotlin/dev/nino/squareglass/LiquidMenuSurface.kt").read_text(encoding="utf-8")
        emphasis = float(re.search(r"if \(emphasized\) drawRect.*?alpha = ([.\d]+)f", pane)[1])
        for ink, film, opacity in zip(self.colors("ink"), ((51, 52, 58), (255, 255, 255)), map(float, match.groups())):
            for value in range(256):
                background = composite(film, (value,) * 3, opacity)
                for alpha in (0, emphasis):
                    self.assertGreaterEqual(contrast(ink, composite(ink, background, alpha)), 4.5)


if __name__ == "__main__":
    unittest.main()
