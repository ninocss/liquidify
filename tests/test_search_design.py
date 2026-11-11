"""Search page design and usability verification tests for Liquidify / SquareGlass."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
KOTLIN = ROOT / "extension/src/main/kotlin/dev/nino/squareglass"
JAVA = ROOT / "extension/src/main/java/dev/nino/squareglass"


def rgb(argb):
    return tuple((argb >> shift) & 255 for shift in (16, 8, 0))


def luminance(color):
    def linear(value):
        value /= 255
        return value / 12.92 if value <= .04045 else ((value + .055) / 1.055) ** 2.4
    return sum(weight * linear(value) for weight, value in zip((.2126, .7152, .0722), color))


def contrast(a, b):
    a, b = sorted((luminance(a), luminance(b)))
    return (b + .05) / (a + .05)


class SearchDesignTest(unittest.TestCase):
    def setUp(self):
        self.search_chrome = (KOTLIN / "SearchChrome.kt").read_text(encoding="utf-8")
        self.palette = (JAVA / "SquarePalette.java").read_text(encoding="utf-8")

    def color(self, role, dark):
        match = re.search(rf"int {role}\(boolean dark\).*?dark \? 0x([a-f0-9]+) : 0x([a-f0-9]+)", self.palette)
        self.assertIsNotNone(match, role)
        return rgb(int(match.group(1 if dark else 2), 16))

    def test_search_input_shape_uses_continuous_capsule(self):
        """Search input field must use ContinuousCapsule for seamless superellipse curvature."""
        self.assertIn("ContinuousCapsule()", self.search_chrome)
        self.assertIn("fun shape(): Shape = ContinuousCapsule()", self.search_chrome)
        self.assertNotIn("fun shape(): Shape = RoundedCornerShape", self.search_chrome)

    def test_search_input_container_has_subtle_frosted_rim(self):
        """Search input glass panel must render emphasized glass with a subtle frosted border."""
        self.assertIn("GlassPanels.panel(Modifier.fillMaxSize(), 28f, emphasized = true)", self.search_chrome)
        self.assertIn("borderModifier", self.search_chrome)

    def test_search_results_bottom_inset_clears_accessories(self):
        """Search results lazy list must provide >= 96dp bottom padding so bottom items are never obscured."""
        match = re.search(r"bottomPadding\(\): PaddingValues = PaddingValues\(bottom = (\d+)\.dp\)", self.search_chrome)
        self.assertIsNotNone(match, "bottomPadding definition not found")
        bottom_dp = int(match.group(1))
        self.assertGreaterEqual(bottom_dp, 96, "Bottom padding must be at least 96dp to clear tabs and mini player")

    def test_search_filter_row_has_horizontal_breathing_room(self):
        """Category filter lazy row must have at least 16dp horizontal padding."""
        match = re.search(r"filterRowPadding\(\): PaddingValues = PaddingValues\(horizontal = (\d+)\.dp", self.search_chrome)
        self.assertIsNotNone(match, "filterRowPadding definition not found")
        h_dp = int(match.group(1))
        self.assertGreaterEqual(h_dp, 16)

    def test_search_filter_contrast_meets_wcag_floor(self):
        """Filter chips must satisfy WCAG 4.5:1 minimum contrast in both Light and Dark modes."""
        for dark in (True, False):
            ink = self.color("ink", dark)
            base = self.color("base", dark)
            surface = self.color("surface", dark)

            # Selected chip: Ink background with Base text
            selected_contrast = contrast(base, ink)
            self.assertGreaterEqual(selected_contrast, 4.5, f"Selected filter contrast too low (dark={dark}): {selected_contrast:.2f}")

            # Unselected chip: Surface background with Ink text
            unselected_contrast = contrast(ink, surface)
            self.assertGreaterEqual(unselected_contrast, 4.5, f"Unselected filter contrast too low (dark={dark}): {unselected_contrast:.2f}")

    def test_search_patches_strictly_generated_and_allocated(self):
        """All search page patches must exist in experimental-patches."""
        patches_dir = ROOT / "experimental-patches"
        for name in (
            "150-square-search-input-container.patch",
            "151-square-search-input-cursor.patch",
            "161-square-search-results-inset.patch",
            "162-square-search-filter-pills.patch",
        ):
            patch_file = patches_dir / name
            self.assertTrue(patch_file.exists(), f"Patch {name} missing")
            content = patch_file.read_text(encoding="utf-8")
            self.assertIn("SearchChrome", content, f"Patch {name} should link to SearchChrome")

    def test_all_patches_respect_smali_register_limits(self):
        """Non-range invoke instructions in all patches must only use registers v0..v15."""
        pattern = re.compile(r'invoke-(?:virtual|direct|static|super|interface)\s*\{([^}]+)\}')
        patches_dir = ROOT / "experimental-patches"
        violations = []
        for patch_file in patches_dir.glob("*.patch"):
            lines = patch_file.read_text(encoding="utf-8").splitlines()
            for line_no, line in enumerate(lines, 1):
                if line.startswith("+") and not line.startswith("+++"):
                    m = pattern.search(line)
                    if m:
                        regs = [r.strip() for r in m.group(1).split(",") if r.strip()]
                        for r in regs:
                            if (r.startswith("v") or r.startswith("p")) and r[1:].isdigit():
                                if int(r[1:]) > 15:
                                    violations.append((patch_file.name, line_no, r, line.strip()))
        self.assertEqual(violations, [], f"Found non-range invoke instructions using register > 15: {violations}")


if __name__ == "__main__":
    unittest.main()
