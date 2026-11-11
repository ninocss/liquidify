"""Adapt explicit always-dark native chrome at reviewed component boundaries."""
from pathlib import Path
import difflib

ROOT = Path(__file__).resolve().parents[1]
OWNER = "Lcom/squareup/ui/market/core/theme/MarketStylesheet$c;->"


def patch(relative, dex, name, rules, custom_replacements=None, already_hooked=False):
    path = ROOT / "research/tidal" / dex / relative
    original = (ROOT / "research/probe-hook-smali" / relative).read_text(encoding="utf-8") if already_hooked else path.read_text(encoding="utf-8")
    modified = original
    for old, new, expected in rules:
        token = OWNER + old + "()Lf20/a;"
        if modified.count(token) != expected:
            raise SystemExit(f"Component ABI differs: {relative}: {old}")
        modified = modified.replace(token, OWNER + new + "()Lf20/a;")
    if custom_replacements:
        for old_code, new_code, expected in custom_replacements:
            if modified.count(old_code) != expected:
                raise SystemExit(f"Custom replacement failed for {relative}: expected {expected}, found {modified.count(old_code)}")
            modified = modified.replace(old_code, new_code)
    target = ROOT / "research/probe-hook-smali" / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(modified, newline="\n", encoding="utf-8")
    (ROOT / "experimental-patches" / name).write_text("".join(difflib.unified_diff(
        original.splitlines(keepends=True), modified.splitlines(keepends=True),
        fromfile="a/" + relative, tofile="b/" + relative)), newline="\n", encoding="utf-8")


if __name__ == "__main__":
    HOME_APPBAR_TRANSPARENT = (
        "    sget-object v3, Landroidx/compose/ui/graphics/Color;->Companion:Landroidx/compose/ui/graphics/Color$Companion;\n\n"
        "    .line 136\n"
        "    .line 137\n"
        "    invoke-virtual {v3}, Landroidx/compose/ui/graphics/Color$Companion;->getTransparent-0d7_KjU()J\n\n"
        "    .line 138\n"
        "    .line 139\n"
        "    .line 140\n"
        "    move-result-wide v16"
    )
    HOME_APPBAR_SOLID = (
        "    move-wide/from16 v16, v14"
    )

    patch("com/tidal/android/feature/home/ui/k.smali", "smali_classes13", "060-square-home-surface.patch", [("q0", "C0", 1)])
    patch("com/tidal/android/feature/home/ui/composables/g.smali", "smali_classes13", "061-square-home-appbar.patch",
          [("q0", "C0", 2), ("J0", "E0", 1)],
          custom_replacements=[(HOME_APPBAR_TRANSPARENT, HOME_APPBAR_SOLID, 1)])
    patch("com/tidal/android/feature/home/ui/composables/i0.smali", "smali_classes13", "062-square-home-refresh.patch", [("q0", "C0", 1), ("J0", "E0", 1)])
    patch("com/tidal/android/feature/appscaffold/ui/composable/MiniPlayerKt.smali", "smali_classes5", "063-square-mini-contrast.patch", [("J0", "E0", 1), ("q0", "r0", 1), ("s0", "l0", 2)], already_hooked=True)
    patch("com/tidal/android/feature/home/ui/composables/a.smali", "smali_classes8", "064-square-home-heading.patch", [("J0", "E0", 1)])
    patch("com/tidal/android/feature/home/ui/composables/r.smali", "smali_classes8", "065-square-home-error.patch", [("J0", "E0", 1)])
    patch("com/tidal/android/feature/home/ui/composables/w.smali", "smali_classes13", "066-square-home-loading.patch", [("J0", "E0", 1)])
    # Text below artwork and native catalogue rows must follow the page theme.
    # White text painted directly over media is intentionally left alone.
    patch("j40/q2.smali", "smali_classes3", "067-square-primary-row.patch", [("J0", "E0", 1)])
    patch("j40/d3.smali", "smali_classes13", "068-square-text-row.patch", [("J0", "E0", 1)])
    patch("j40/c0.smali", "smali_classes3", "069-square-artist-caption.patch", [("J0", "E0", 1)])
    print("Prepared native Home chrome and mini-player contrast adapters")
