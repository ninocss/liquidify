"""Package the versioned Liquidify Beta. Never includes stock TIDAL."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = json.loads((ROOT / "release.json").read_text())["version"]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    resources = json.loads((ROOT / "reports/design-resources-build.json").read_text())
    if resources.get("themeMode", 0) != 0:
        raise SystemExit("Rebuild System resources before packaging the selectable-theme ZIP")
    report = json.loads((ROOT / "reports/probe-linkage.json").read_text())
    dex = (ROOT / "research/probe-remapped.dex").read_bytes()
    if not report["passed"] or report["sourceDexSha256"] != digest(dex):
        raise SystemExit("Current extension must pass linkage audit")
    upstream = ROOT / "radiant-base/patches"
    manifest = json.loads((upstream / "manifest.json").read_text())
    helpers = sorted(p.relative_to(ROOT / "research/probe-remapped").as_posix()
                     for p in (ROOT / "research/probe-remapped").rglob("*.smali"))
    patches = sorted(p.name for p in (ROOT / "experimental-patches").glob("*.patch"))
    core_files = sorted([
        '000-square-glass-host.patch', '010-square-glass-native-tabs.patch', '020-square-glass-native-backdrop.patch',
        '030-square-glass-native-shell.patch', '040-square-glass-native-mini-player.patch', '094-square-settings-page.patch',
        '095-square-settings-cards.patch', '096-square-switch-contrast.patch', '097-square-settings-toggle-cards.patch',
        '106-square-runtime-theme.patch', '107-square-design-settings.patch',
    ])
    palette_files = sorted([
        '050-square-wave-colors.patch', '051-square-wave-theme.patch', '052-square-market-colors.patch',
        '053-square-market-system-theme.patch', '054-square-wave-corners.patch', '055-square-wave-one-colors.patch',
        '056-square-market-tokens.patch',
    ])
    home_files = sorted([
        '060-square-home-surface.patch', '061-square-home-appbar.patch', '062-square-home-refresh.patch',
        '064-square-home-heading.patch', '065-square-home-error.patch', '066-square-home-loading.patch',
        '067-square-primary-row.patch', '068-square-text-row.patch', '069-square-artist-caption.patch',
        '084-square-home-filters.patch', '085-square-home-shortcuts.patch', '086-square-home-shortcut-icon.patch',
        '087-square-home-toolbar-surface.patch', '088-square-library-grid.patch', '089-square-home-artwork-shadow.patch',
        '098-square-explore-page.patch', '099-square-native-search-field.patch', '133-square-listening-observer.patch',
        '134-square-home-actions.patch', '136-square-clear-listening-on-logout.patch', '147-square-recommendation-batch.patch',
        '148-square-recommendation-result.patch', '149-square-recommendation-position.patch', '150-square-search-input-container.patch',
        '151-square-search-input-cursor.patch', '161-square-search-results-inset.patch', '162-square-search-filter-pills.patch',
    ])
    player_files = sorted([
        '063-square-mini-contrast.patch', '070-square-artwork-corners.patch', '071-square-player-background.patch',
        '072-square-player-blur-film.patch', '073-square-player-controls.patch', '074-square-lyrics-contrast.patch',
        '075-square-seek-track.patch', '076-square-seek-thumb.patch', '077-square-player-action-film.patch',
        '078-square-player-glass-buttons.patch', '079-square-mini-marquee-clip.patch', '080-square-player-artwork.patch',
        '081-square-broadcast-icon.patch', '082-square-toolbar-icons.patch', '083-square-lyrics-viewport.patch',
        '109-square-player-artwork-background.patch', '110-square-player-background-contrast.patch', '111-square-player-secondary-bar.patch',
        '112-square-player-footer-s3.patch', '113-square-player-footer-e5.patch', '114-square-player-footer-l0.patch',
        '138-square-player-cover-fade.patch', '139-square-player-cover-spacing.patch', '142-square-player-top-edge.patch',
    ])
    details_files = sorted([
        '100-square-module-header-contrast.patch', '101-square-mix-page.patch', '102-square-mix-header.patch',
        '103-square-track-artwork.patch', '104-square-collection-track-artwork.patch', '105-square-legacy-track-artwork.patch',
        '108-square-album-header.patch', '115-square-playlist-hero.patch', '137-square-cover-page-color.patch',
        '144-square-upload-padding.patch',
    ])
    menu_files = sorted([
        '090-square-context-menu-presentation.patch', '091-square-context-menu-dismiss.patch', '092-square-context-menu-contrast.patch',
        '093-square-menu-anchor.patch', '143-square-player-menu-source.patch', '144-square-queue-glass.patch',
        '145-square-text-field-glass.patch',
    ])
    gesture_files = sorted([
        '132-square-mini-gestures.patch', '140-square-native-tab-feedback.patch', '141-square-accessory-press-feedback.patch',
        '146-square-gesture-mini-controls.patch', '152-square-favorite-motion.patch', '153-square-settings-icon-motion.patch',
        '154-square-collection-settings-motion.patch', '155-square-native-favorite-motion.patch', '156-square-slider-thumb-motion.patch',
    ])
    linn_files = sorted([
        '157-square-output-popup.patch', '158-square-output-anchor.patch', '159-square-linn-provider.patch',
        '160-square-linn-player.patch',
    ])
    all_assigned = (core_files + palette_files + home_files + player_files +
                    details_files + menu_files + gesture_files + linn_files)
    if set(all_assigned) != set(patches):
        raise SystemExit(f"Patch allocation mismatch: missing {set(patches) - set(all_assigned)}, extra {set(all_assigned) - set(patches)}")

    specs = [
        dict(id="LiquidifyCore", source="liquidify", category="patch", order=-50, fileNames=core_files, extensionFiles=helpers,
             title="Liquidify · Glass Core",
             description="Foundation for Android Liquid Glass: host architecture, shell backdrop, adaptive tab bar, and settings hooks. Required for all Liquidify extensions.",
             default=True, disables=["EnableLegacyUi", "MiniPlayerRedesign", "PlayerBackdrop"],
             advancedOptions=[dict(type="choice", key="theme", title="Theme", entries=["System", "Light", "Dark"], values=["0x0", "0x1", "0x2"], defaultIndex=0, token="RL_SQUARE_THEME_MODE", inline=True)]),
        dict(id="LiquidifyPalette", source="liquidify", category="patch", order=-45, fileNames=palette_files, requires=["LiquidifyCore"],
             title="Liquidify · Palettes & Contrasts",
             description="Wave and Market color schemes, optimized OLED contrasts, and semantic surface color tokens.", default=True),
        dict(id="LiquidifyHome", source="liquidify", category="patch", order=-40, fileNames=home_files, requires=["LiquidifyCore", "LiquidifyPalette"],
             title="Liquidify · Home & Explore",
             description="Translucent app bar, liquid filter pills, shortcuts, clean Explore search, and time-of-day listening habits.", default=True),
        dict(id="LiquidifyPlayer", source="liquidify", category="patch", order=-35, fileNames=player_files, requires=["LiquidifyCore", "LiquidifyPalette"],
             disables=["PlayerBackdrop"],
             title="Liquidify · Now Playing & Player",
             description="1:1 uncropped square artwork, bottom cover gradient fade, floating glass action buttons, and dynamic seekbar.", default=True),
        dict(id="LiquidifyDetails", source="liquidify", category="patch", order=-30, fileNames=details_files, requires=["LiquidifyCore", "LiquidifyPalette"],
             title="Liquidify · Albums, Mixes & Playlists",
             description="Full-bleed headers for playlists and mixes, transparent scrolling toolbars, smooth artist fade, and 10dp rounded cover thumbnails.", default=True),
        dict(id="LiquidifyMenus", source="liquidify", category="patch", order=-25, fileNames=menu_files, requires=["LiquidifyCore", "LiquidifyPalette"],
             title="Liquidify · Liquid Menus & Queue",
             description="Floating frosted glass context menus without shadow bars, queue glass backdrop, and refined submenu presentation.", default=True),
        dict(id="LiquidifyGestures", source="liquidify", category="patch", order=-20, fileNames=gesture_files, requires=["LiquidifyCore"],
             title="Liquidify · Gestures & Haptics",
             description="Track swipe to queue with circular cover preview, mini-player swipe gestures, haptic tab feedback, and inertial animations.", default=True),
        dict(id="LiquidifyLinn", source="liquidify", category="patch", order=-15, fileNames=linn_files, requires=["LiquidifyCore"],
             title="Liquidify · Linn OpenHome Streaming",
             description="Liquid Glass output device picker with native TIDAL Connect integration, remote volume control, and built-in Linn OpenHome renderer.", default=True),
        dict(id="LiquidifyTypography", source="liquidify", category="patch", order=-10, fileNames=[], requires=["LiquidifyCore"],
             title="Liquidify · Inter Typography",
             description="Static Inter font family instances while fully preserving all original labels, font weights, and accessibility strings.", default=True),
    ]
    allowed_radiant = {"PreventPlayStoreUpdates", "LyricsRlApi", "DebugMenuUnlock"}
    for p in manifest["patches"]:
        if p["id"] in allowed_radiant:
            p["compatibleWith"] = ["LiquidifyCore"]
        else:
            p.pop("compatibleWith", None)
    manifest["patches"] = specs + manifest["patches"]
    manifest["releaseVersion"] = VERSION
    manifest["name"] = "Liquidify"
    manifest["editor"] = "ninocss"
    manifest["compatibility"] = dict(apkSha256=json.loads((ROOT / "upstream-lock.json").read_text())["tidal"]["sha256"],
                                     minSdk=33, capabilities=["exactBaseSha256", "squareResourcesV1", "squareRuntimeThemeV1"], strictSquarePatches=True)
    manifest["designResources"] = json.loads((ROOT / "research/design-resources.json").read_text())
    outdir = ROOT / "dist"
    outdir.mkdir(exist_ok=True)
    target = outdir / f"Liquidify-{VERSION}-patches.zip"
    payload = {}
    for p in upstream.rglob("*"):
        if p.is_file() and p.name != "manifest.json":
            payload[p.relative_to(upstream).as_posix()] = p.read_bytes()
    for p in (ROOT / "experimental-patches").glob("*.patch"):
        payload[p.name] = p.read_bytes()
    for relative in helpers:
        text = (ROOT / "research/probe-remapped" / relative).read_text()
        if relative == "dev/nino/squareglass/SquareSettings.smali":
            for method, value, token in (("initialTheme", "0x0", "RL_SQUARE_THEME_MODE"), ("managerReady", "0x1", "RL_SQUARE_MANAGER_READY"), ("themeEnabled", "0x1", "RL_SQUARE_THEME_ENABLED")):
                pattern = rf"(\.method public static {method}\(\)I.*?const/4 v0, ){value}"
                text, n = re.subn(pattern, rf"\g<1>__{token}__", text, flags=re.S)
                if n != 1:
                    raise SystemExit(f"Unexpected settings ABI for {method}")
        payload["extension/" + relative] = text.encode()
    for p in (ROOT / "research/design-fonts").glob("*.ttf"):
        payload["fonts/" + p.name] = p.read_bytes()
    manifest_json = json.dumps(manifest, indent=2) + "\n"
    payload["manifest.json"] = manifest_json.encode()
    (ROOT / "patches/manifest.json").write_text(manifest_json)
    manager_asset = ROOT / "radiant-base/Manager/app/src/main/assets/patches-manifest.json"
    if manager_asset.parent.exists():
        manager_asset.write_text(manifest_json)
    payload["README.md"] = (ROOT / "README.md").read_bytes()
    payload["LINN_OUTPUT.md"] = (ROOT / "LINN_OUTPUT.md").read_bytes()
    payload["LICENSE"] = (ROOT / "LICENSE").read_bytes()
    payload["upstream-lock.json"] = (ROOT / "upstream-lock.json").read_bytes()
    for p in (ROOT / "licenses").glob("*"):
        if p.is_file(): payload["licenses/" + p.name] = p.read_bytes()
    checksums = {name: digest(data) for name, data in sorted(payload.items())}
    payload["checksums.json"] = (json.dumps(checksums, indent=2) + "\n").encode()
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for name, data in sorted(payload.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 10, 9, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, data)
    release_info = json.loads((ROOT / "release.json").read_text())
    release_target = ROOT / release_info["patches"]
    release_target.parent.mkdir(parents=True, exist_ok=True)
    import shutil
    shutil.copyfile(target, release_target)
    result = dict(file=target.name, sha256=digest(target.read_bytes()), helpers=len(helpers), squarePatches=len(patches), status="1.0.0 Beta; scoped hardware acceptance documented in LINN_OUTPUT.md")
    (ROOT / "reports/package-alpha.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
