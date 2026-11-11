"""Artwork hero paint and scoped removal of the host's rectangular press feedback."""
import re
from prepare_visual_patch import save, PLAYER


def player_background(text):
    start = text.index('.method public static final b(')
    end = text.index('.end method', start) + len('.end method')
    signature = text[start:text.index('\n', start)]
    return text[:start] + signature + '''
    .locals 7
    .annotation build Landroidx/compose/runtime/Composable;
    .end annotation

    move-wide v0, p1
    move v2, p3
    move-object v3, p4
    move-object/from16 v4, p8
    move-object/from16 v5, p9
    const/4 v6, 0x0

    invoke-static/range {v0 .. v6}, Ldev/nino/squareglass/PlayerArtwork;->Background(JILjava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)V

    return-void
.end method''' + text[end:]


def player_cover(text):
    # Only still/animated album artwork; video surfaces retain their own decoration.
    start = text.index('.method public static final a(')
    end = text.index('.method public static final c(', start)
    body = text[start:end]
    for source, dest in [('v15', 'v5'), ('v13', 'v6')]:
        pattern = rf'    invoke-static/range \{{{source} \.\. v\d+\}}, Landroidx/compose/ui/draw/ShadowKt;->[^\n]+\n\n    move-result-object {dest}'
        body, count = re.subn(pattern, f'    move-object {dest}, {source}', body)
        assert count == 1, ('shadow', source, count)
    for args, dest, composer in [('v5, v9, v13, v14, v6', 'v13', 'v12'), ('v6, v7, v8, v9, v15', 'v6', 'v10')]:
        pattern = rf'    invoke-static \{{{args}\}}, Landroidx/compose/foundation/BorderKt;->[^\n]+\n\n    move-result-object {dest}'
        source = args.split(',')[0]
        replacement = f'''    const/4 {dest}, 0x0

    invoke-static {{{source}, {composer}, {dest}}}, Ldev/nino/squareglass/PlayerArtwork;->cover(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;

    move-result-object {dest}'''
        # v6 is both input and output in the animated case; use the now-dead width register.
        if source == dest:
            replacement = replacement.replace(f'const/4 {dest},', 'const/4 v7,').replace(f'{composer}, {dest}}}', f'{composer}, v7}}')
        body, count = re.subn(pattern, replacement, body)
        assert count == 1, ('border', args, count)
    return text[:start] + body + text[end:]


def artwork_spacing(text):
    # The native pager supplies page margins separately for both artwork types.
    pattern = r'(    invoke-static \{p2, v5, v2, p4, (?:v1|v4)\}, Landroidx/compose/foundation/layout/PaddingKt;->padding-VpY3zN4\$default)'
    text, count = re.subn(pattern, '    const/4 v5, 0x0\n\n' + r'\1', text)
    assert count == 2
    return text


def tab_feedback(text):
    start = text.index('.method public static final d(')
    body = text[start:]
    needle = '    invoke-static/range {v2 .. v10}, Landroidx/compose/foundation/selection/SelectableKt;->'
    assert body.count(needle) == 1
    body = body.replace(needle, '    const/4 v5, 0x0\n\n' + needle)
    # This legacy route must not add a second square selection surface either.
    pattern = r'    invoke-static/range \{v3 \.\. v8\}, Landroidx/compose/foundation/BackgroundKt;->[^\n]+\n\n    move-result-object v3'
    body, count = re.subn(pattern, '', body)
    assert count == 1
    return text[:start] + body


def shell_feedback(text):
    needle = '    invoke-static/range {v40 .. v48}, Landroidx/compose/foundation/ClickableKt;->'
    assert text.count(needle) == 1
    # Native outer accessory ripple sits outside the glass pill. Keep its callback.
    return text.replace(needle, '    const/16 v42, 0x0\n\n' + needle)


def player_top_edge(text):
    # Scope to the portrait body; the landscape layout remains native.
    start = text.index('.method public static final e(')
    end = text.index('.method public static final f(', start)
    body = text[start:end]
    needle = '    .line 151\n    invoke-static {v10, v3}, Landroidx/compose/ui/ComposedModifierKt;->materializeModifier('
    assert body.count(needle) == 1
    body = body.replace(needle, '''    invoke-static {v3, v10, v12}, Ldev/nino/squareglass/PlayerArtwork;->frame(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;

    move-result-object v3

''' + needle)
    needle = '    .line 293\n    invoke-static/range {v3 .. v9}, Landroidx/compose/foundation/layout/BoxWithConstraintsKt;->'
    assert body.count(needle) == 1
    body = body.replace(needle, '''    const/4 v8, 0x0

    invoke-static {v3, v7, v8}, Ldev/nino/squareglass/PlayerArtwork;->hero(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;

    move-result-object v3

    invoke-virtual/range {v54 .. v54}, Landroidx/compose/ui/Alignment$Companion;->getTopStart()Landroidx/compose/ui/Alignment;

    move-result-object v4

    const/16 v8, 0xc30

''' + needle)
    needle = '    .line 200\n    invoke-static {v3, v6, v8, v15, v11}, Landroidx/compose/foundation/layout/PaddingKt;->'
    assert body.count(needle) == 1
    body = body.replace(needle, '''    invoke-static {v3, v7, v8}, Ldev/nino/squareglass/PlayerArtwork;->header(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;

    move-result-object v3

''' + needle)
    text = text[:start] + body + text[end:]
    # Keep the real title, collection, seek and transport callbacks.
    needle = '    .line 8\n    invoke-static {v11, v3}, Landroidx/compose/ui/ComposedModifierKt;->materializeModifier('
    assert text.count(needle) == 1
    text = text.replace(needle, '''    const/4 v9, 0x0

    invoke-static {v3, v11, v9}, Ldev/nino/squareglass/PlayerArtwork;->media(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;

    move-result-object v9

    .line 8
    invoke-static {v11, v9}, Landroidx/compose/ui/ComposedModifierKt;->materializeModifier(''')
    needle = 'Ldev/nino/squareglass/NativeGlass;->secondaryBar('
    assert text.count(needle) == 1
    return text.replace(needle, 'Ldev/nino/squareglass/PlayerArtwork;->footer(')


def player_menu_source(text):
    needle = '    move-object/from16 v1, p1'
    assert text.count(needle) == 1
    return text.replace(needle, needle + '\n\n    invoke-static {v1}, Ldev/nino/squareglass/PlayerWindow;->menuAction(Lam0/a;)Lam0/a;\n\n    move-result-object v1')


if __name__ == '__main__':
    save(PLAYER + 'p3.smali', 'smali_classes10', '137-square-cover-page-color.patch', player_background, hooked=True)
    save(PLAYER + 'CoverPagerKt.smali', 'smali_classes10', '138-square-player-cover-fade.patch', player_cover, hooked=True)
    save(PLAYER + 't0.smali', 'smali_classes10', '139-square-player-cover-spacing.patch', artwork_spacing)
    save('com/tidal/android/feature/appscaffold/ui/composable/x.smali', 'smali_classes5', '140-square-native-tab-feedback.patch', tab_feedback, hooked=True)
    save('com/tidal/android/feature/appscaffold/ui/composable/i.smali', 'smali_classes5', '141-square-accessory-press-feedback.patch', shell_feedback, hooked=True)
    save('com/tidal/android/feature/playerscreen/ui/PlayerScreenKt.smali', 'smali_classes10', '142-square-player-top-edge.patch', player_top_edge, hooked=True)
    save(PLAYER + 'l0.smali', 'smali_classes10', '143-square-player-menu-source.patch', player_menu_source, hooked=True)
    print('Prepared full artwork, cover fade, page color and capsule press feedback')
