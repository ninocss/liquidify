"""Exact native token, artwork and player surface adapters."""
from pathlib import Path
import difflib
import re

ROOT = Path(__file__).resolve().parents[1]
PLAYER = "com/tidal/android/feature/playerscreen/ui/composables/"
PAINT = "Ldev/nino/squareglass/NativePlayerPaint;->"


def save(relative, dex, name, transform, hooked=False):
    path = ROOT / "research/probe-hook-smali" / relative
    original = (path if hooked else ROOT / "research/tidal" / dex / relative).read_text()
    changed = transform(original)
    if original == changed: raise SystemExit("Empty visual adapter: " + name)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(changed, newline="\n")
    (ROOT / "experimental-patches" / name).write_text("".join(difflib.unified_diff(
        original.splitlines(keepends=True), changed.splitlines(keepends=True),
        fromfile="a/" + relative, tofile="b/" + relative)), newline="\n")


def tokens(text):
    signature = ".method public getColorTokens()Lv20/i$b;"
    start = text.index(signature)
    end = text.index(".end method", start)
    body = text[start:end]
    assert body.count("    return-object v0") == 1
    body = body.replace("    return-object v0", """    invoke-static {v0}, Ldev/nino/squareglass/SquareMarketTokens;->select(Lv20/i$b;)Lv20/i$b;

    move-result-object v0

    return-object v0""")
    return text[:start] + body + text[end:]


def artwork(text):
    old = "invoke-static {p1}, Landroidx/compose/foundation/shape/RoundedCornerShapeKt;->RoundedCornerShape-0680j_4(F)Landroidx/compose/foundation/shape/RoundedCornerShape;"
    assert text.count(old) == 1
    return text.replace(old, "invoke-static {p1}, Ldev/nino/squareglass/SquareShapes;->artwork(F)Landroidx/compose/foundation/shape/RoundedCornerShape;")


def fixed_color(text, color, helper, count):
    pattern = rf"invoke-virtual \{{[^}}]+\}}, Landroidx/compose/ui/graphics/Color\$Companion;->get{color}-0d7_KjU\(\)J"
    changed, n = re.subn(pattern, f"invoke-static {{}}, {PAINT}{helper}()J", text)
    assert n == count, (color, n, count)
    return changed


def background(text):
    signature = ".method public static final b(Lcom/tidal/android/feature/playerscreen/ui/model/PlayerBackgroundStyle;"
    start = text.index(signature)
    end = text.index(".end method", start)
    body = text[start:end]
    anchor = "    move-result-object v6"
    assert body.count(anchor) == 1
    body = body.replace(anchor, anchor + """

    const/4 v0, 0x0

    invoke-static {v5, v6, v0}, Ldev/nino/squareglass/NativeBackdrop;->capturePlayer(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;

    move-result-object v5""")
    needle = "    invoke-static/range {v11 .. v16}, Landroidx/compose/foundation/BackgroundKt;->background-bw27NRU$default(Landroidx/compose/ui/Modifier;JLandroidx/compose/ui/graphics/Shape;ILjava/lang/Object;)Landroidx/compose/ui/Modifier;"
    assert body.count(needle) == 1
    # Transform the paint registers, keeping p1 intact for the native
    # recomposition callback; tinting a captured parameter would compound.
    body = body.replace(needle, """    invoke-static/range {v12 .. v13}, Ldev/nino/squareglass/NativePlayerPaint;->background(J)J

    move-result-wide v12

""" + needle)
    text = text[:start] + body + text[end:]
    return fixed_color(text, "Black", "base", 1)


def controls(text):
    text = fixed_color(text, "White", "ink", 2)
    pattern = r"(    sget-wide (v\d+), Lcom/tidal/android/feature/playerscreen/ui/composables/q2;->\w+:J)"
    def adapt(m):
        reg = m[2]
        end = "v" + str(int(reg[1:]) + 1)
        return m[0] + f"\n\n    invoke-static/range {{{reg} .. {end}}}, {PAINT}foreground(J)J\n\n    move-result-wide {reg}"
    text, n = re.subn(pattern, adapt, text)
    assert n > 0
    return text


def dynamic_color(text, owner, expected):
    pattern = rf"(    sget-wide ([vp]\d+), Lcom/tidal/android/feature/playerscreen/ui/composables/{owner};->\w+:J)"
    def adapt(m):
        reg = m[2]
        end = reg[0] + str(int(reg[1:]) + 1)
        return m[0] + f"\n\n    invoke-static/range {{{reg} .. {end}}}, {PAINT}foreground(J)J\n\n    move-result-wide {reg}"
    changed, count = re.subn(pattern, adapt, text)
    assert count == expected, (owner, count)
    return changed


def glass_buttons(text):
    needle = "    invoke-static/range {v1 .. v9}, Landroidx/compose/material3/IconButtonKt;->IconButton("
    assert text.count(needle) == 1
    return text.replace(needle, "    invoke-static/range {v1 .. v9}, Ldev/nino/squareglass/NativeGlass;->playerIconButton(")


def marquee_clip(text):
    needle = "    invoke-static/range {v25 .. v33}, Landroidx/compose/foundation/BasicMarqueeKt;->"
    assert text.count(needle) == 2
    # v25 is shared by both text rows; clip outside the marquee drawing layer.
    text = text.replace(needle, """    invoke-static/range {v25 .. v25}, Landroidx/compose/ui/draw/ClipKt;->clipToBounds(Landroidx/compose/ui/Modifier;)Landroidx/compose/ui/Modifier;

    move-result-object v25

""" + needle, 1)
    old = "    invoke-static {v7}, Landroidx/compose/foundation/shape/RoundedCornerShapeKt;->RoundedCornerShape-0680j_4(F)Landroidx/compose/foundation/shape/RoundedCornerShape;"
    assert text.count(old) == 1
    return text.replace(old, "    invoke-static {v7}, Ldev/nino/squareglass/SquareShapes;->artwork(F)Landroidx/compose/foundation/shape/RoundedCornerShape;")


def player_artwork(text):
    for reg in ("v6", "v9"):
        old = f"invoke-static {{{reg}}}, Landroidx/compose/foundation/shape/RoundedCornerShapeKt;->RoundedCornerShape-0680j_4(F)Landroidx/compose/foundation/shape/RoundedCornerShape;"
        assert text.count(old) == 1
        text = text.replace(old, f"invoke-static {{{reg}}}, Ldev/nino/squareglass/SquareShapes;->playerArtwork(F)Landroidx/compose/foundation/shape/RoundedCornerShape;")
    return text


def broadcast(text):
    needle = "    invoke-static/range {v1 .. v10}, Landroidx/compose/foundation/ImageKt;->Image("
    assert text.count(needle) == 1
    # Enable the explicit ColorFilter argument (default-mask bit 0x40).
    text = text.replace(needle, """    const/16 v10, 0x38

    invoke-static {}, Ldev/nino/squareglass/NativeGlass;->iconTint()Landroidx/compose/ui/graphics/ColorFilter;

    move-result-object v7

""" + needle)
    old = "invoke-static {v2}, Lcom/tidal/android/feature/playerscreen/ui/composables/l;->a(Lcom/squareup/ui/market/core/theme/MarketStylesheet;)J"
    assert text.count(old) == 1
    return text.replace(old, f"invoke-static {{}}, {PAINT}ink()J")


def home_toolbar_icons(text):
    needle = "    invoke-virtual/range {v13 .. v26}, Landroidx/compose/material3/TopAppBarDefaults;->topAppBarColors"
    assert text.count(needle) == 1
    return text.replace(needle, """    move-wide/from16 v18, v20

    move-wide/from16 v22, v20

    const/16 v26, 0x0

""" + needle)


def lyrics_viewport(text):
    synced = "    invoke-static/range {v15 .. v27}, Landroidx/compose/foundation/lazy/LazyDslKt;->LazyColumn("
    unsynced = "    invoke-static/range {v9 .. v21}, Landroidx/compose/foundation/lazy/LazyDslKt;->LazyColumn("
    assert text.count(synced) == text.count(unsynced) == 1
    text = text.replace(synced, """    move-object/from16 v0, v17

    invoke-static {v15, v0}, Ldev/nino/squareglass/SquareLyrics;->viewport(Landroidx/compose/ui/Modifier;Landroidx/compose/foundation/layout/PaddingValues;)Landroidx/compose/ui/Modifier;

    move-result-object v15

""" + synced)
    return text.replace(unsynced, """    invoke-static {v9, v11}, Ldev/nino/squareglass/SquareLyrics;->viewport(Landroidx/compose/ui/Modifier;Landroidx/compose/foundation/layout/PaddingValues;)Landroidx/compose/ui/Modifier;

    move-result-object v9

""" + unsynced)


if __name__ == "__main__":
    save("com/squareup/ui/market/core/theme/MarketStylesheet.smali", "smali_classes23", "056-square-market-tokens.patch", tokens)
    save("j40/c0.smali", "smali_classes3", "070-square-artwork-corners.patch", artwork, hooked=True)
    save(PLAYER + "p3.smali", "smali_classes10", "071-square-player-background.patch", background)
    save(PLAYER + "k3.smali", "smali_classes10", "072-square-player-blur-film.patch", lambda t: fixed_color(t, "Black", "film", 2))
    save(PLAYER + "i3.smali", "smali_classes10", "073-square-player-controls.patch", controls)
    save(PLAYER + "LyricsKt.smali", "smali_classes10", "074-square-lyrics-contrast.patch", lambda t: t.replace("MarketStylesheet$c;->J0()Lf20/a;", "MarketStylesheet$c;->E0()Lf20/a;"))
    save(PLAYER + "e0.smali", "smali_classes10", "075-square-seek-track.patch", lambda t: dynamic_color(t, "t4", 1))
    save(PLAYER + "z4.smali", "smali_classes10", "076-square-seek-thumb.patch", lambda t: dynamic_color(t, "t4", 1))
    save(PLAYER + "v3.smali", "smali_classes10", "077-square-player-action-film.patch", lambda t: dynamic_color(t, "t3", 1))
    save(PLAYER + "z2.smali", "smali_classes10", "078-square-player-glass-buttons.patch", glass_buttons)
    save("com/tidal/android/feature/appscaffold/ui/composable/MiniPlayerKt.smali", "smali_classes5", "079-square-mini-marquee-clip.patch", marquee_clip, hooked=True)
    save(PLAYER + "CoverPagerKt.smali", "smali_classes10", "080-square-player-artwork.patch", player_artwork)
    save(PLAYER + "BroadcastButtonKt.smali", "smali_classes10", "081-square-broadcast-icon.patch", broadcast)
    save("com/tidal/android/feature/home/ui/composables/g.smali", "smali_classes13", "082-square-toolbar-icons.patch", home_toolbar_icons, hooked=True)
    save(PLAYER + "LyricsKt.smali", "smali_classes10", "083-square-lyrics-viewport.patch", lyrics_viewport, hooked=True)
    print("Prepared native component tokens, 10dp artwork and player adapters")
