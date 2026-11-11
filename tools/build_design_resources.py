"""Build fingerprinted palette instructions and static Inter font instances."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import zipfile
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from build_runtime import java_tool

ROOT = Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--theme-mode", type=int, choices=(0, 1, 2), default=0)
    args = parser.parse_args()
    classpath = os.pathsep.join(["build/dex-tools/*", "research/resource-tools"])
    subprocess.run([str(java_tool("javac")), "-cp", "build/dex-tools/*", "-d", "research/resource-tools", "tools/ResourcePalettePatcher.java", "tools/ColorSelectorPatcher.java"], cwd=ROOT, check=True)
    subprocess.run([str(java_tool("java")), "-cp", classpath, "tools/PatchResources.java",
        "research/tidal-stock-2.200.0.apk", "mappings/resource-palette.tsv", "research/square-resources.arsc", str(args.theme_mode)], cwd=ROOT, check=True)
    palette = []
    for line in (ROOT / "mappings/resource-palette.tsv").read_text().splitlines():
        if line.startswith("#") or not line:
            continue
        palette.append(dict(zip(("id", "name", "expectedType", "expectedData", "light", "dark"), line.split("\t"))))
    work = ROOT / "research/design-fonts"
    work.mkdir(exist_ok=True)
    files = []
    source = ROOT / "square-reference/app/src/main/res/font/inter.ttf"
    # Pin the input in metadata alongside the app source revision.
    font_sha = sha(source.read_bytes())
    prior_report = ROOT / "reports/design-resources-build.json"
    prior_design = ROOT / "research/design-resources.json"
    cache_ok = False
    if prior_report.exists() and prior_design.exists():
        cached = json.loads(prior_report.read_text())
        fonts = json.loads(prior_design.read_text())["fonts"]
        cache_ok = cached.get("fontRecipe") == 1 and cached.get("sourceFontSha256") == font_sha and all(
            (work / Path(f["source"]).name).exists() and sha((work / Path(f["source"]).name).read_bytes()) == f["sha256"] for f in fonts)
    for weight in (400, 500, 600, 700, 900):
        target = work / f"squareglass_{weight}.ttf"
        if cache_ok:
            continue
        font = instantiateVariableFont(TTFont(source, recalcTimestamp=False), {"wght": weight, "opsz": 14}, inplace=True)
        for platform, encoding, language in ((3, 1, 0x409), (1, 0, 0)):
            for name_id, value in ((1, "SquareGlass Sans"), (2, str(weight)), (4, f"SquareGlass Sans {weight}"), (6, f"SquareGlassSans-{weight}")):
                font["name"].setName(value, name_id, platform, encoding, language)
        font.save(target)
    xml_specs = json.loads((ROOT / "mappings/theme-xml.json").read_text())
    with zipfile.ZipFile(ROOT / "research/tidal-stock-2.200.0.apk") as apk:
        for spec in xml_specs:
            original = apk.read(spec["target"])
            spec["originalSha256"] = sha(original)
            source_xml = ROOT / "research/design-xml-original" / spec["target"]
            target_xml = ROOT / "research/design-xml" / spec["target"]
            source_xml.parent.mkdir(parents=True, exist_ok=True)
            target_xml.parent.mkdir(parents=True, exist_ok=True)
            source_xml.write_bytes(original)
            subprocess.run([str(java_tool("java")), "-cp", classpath, "tools/PatchThemeXml.java",
                str(source_xml), str(target_xml), spec["attributeId"], str(spec["expectedType"]), spec["fromResource"], spec["toResource"], str(spec["expectedCount"])], cwd=ROOT, check=True)
            spec["sha256"] = sha(target_xml.read_bytes())
        for name in apk.namelist():
            if not name.startswith(("res/font/nationale_", "res/font/square_sans_text_", "res/raw/square_sans_text_", "res/raw/square_sans_display_")) or not name.endswith(".otf") or "italic" in name:
                continue
            weight = 900 if "black" in name else 600 if "semi_bold" in name or "demi_bold" in name else 700 if "bold" in name else 500 if "medium" in name else 400
            files.append(dict(target=name, source=f"fonts/squareglass_{weight}.ttf", originalSha256=sha(apk.read(name)), sha256=sha((work / f"squareglass_{weight}.ttf").read_bytes())))
    design = dict(patchId="LiquidifyPalette", palette=palette, fontsPatchId="LiquidifyTypography", fonts=files, themeXml=xml_specs)
    (ROOT / "research/design-resources.json").write_text(json.dumps(design, indent=2) + "\n")
    (ROOT / "reports/design-resources-build.json").write_text(json.dumps(dict(fontRecipe=1, themeMode=args.theme_mode, sourceFontSha256=font_sha, colors=len(palette), fontResources=len(files), outputArscSha256=sha((ROOT / "research/square-resources.arsc").read_bytes())), indent=2) + "\n")
    print(f"Built {len(palette)} palette instructions and {len(files)} font replacements")


if __name__ == "__main__":
    main()
