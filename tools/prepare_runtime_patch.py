"""Pinned runtime preferences and settings entry hooks."""
from prepare_visual_patch import save

def activity(text):
    needle = "    invoke-super {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->attachBaseContext(Landroid/content/Context;)V"
    assert text.count(needle) == 1
    return text.replace(needle, """    invoke-static {p1}, Ldev/nino/squareglass/SquareRuntime;->context(Landroid/content/Context;)Landroid/content/Context;

    move-result-object p1

""" + needle)

def settings(text):
    needle = "    invoke-interface {p1, v2, v1, v3, v0}, Landroidx/compose/foundation/lazy/LazyListScope;->items(ILam0/l;Lam0/l;Lam0/q;)V"
    assert text.count(needle) == 1
    return text.replace(needle, needle + """

    iget-object v0, p0, Lcom/aspiro/wamp/settings/compose/n0;->a:Ljava/util/List;

    invoke-static {p1, v0}, Ldev/nino/squareglass/DesignEntry;->append(Landroidx/compose/foundation/lazy/LazyListScope;Ljava/util/List;)V""")

def album(text):
    start=text.index(".method public final c(")
    end=text.index(".end method",start)
    body=text[start:end]
    needle="    invoke-virtual {v4, v1}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V"
    assert body.count(needle)==1
    body=body.replace(needle,needle+"""

    move-object/from16 v0, p2

    iget-object v0, v0, Landroidx/recyclerview/widget/RecyclerView$ViewHolder;->itemView:Landroid/view/View;

    invoke-static {v0}, Ldev/nino/squareglass/SquareDetails;->albumHeader(Landroid/view/View;)V""")
    return text[:start]+body+text[end:]

def player_style(text):
    anchor="""    sget-object v0, Lcom/tidal/android/feature/playerscreen/ui/composables/p3$a;->a:[I

    invoke-virtual/range {p0 .. p0}, Ljava/lang/Enum;->ordinal()I"""
    assert text.count(anchor)==1
    return text.replace(anchor,"""    sget-object v0, Lcom/tidal/android/feature/playerscreen/ui/composables/p3$a;->a:[I

    invoke-static/range {p0 .. p0}, Ldev/nino/squareglass/NativePlayerPaint;->style(Lcom/tidal/android/feature/playerscreen/ui/model/PlayerBackgroundStyle;)Lcom/tidal/android/feature/playerscreen/ui/model/PlayerBackgroundStyle;

    move-result-object v11

    invoke-virtual {v11}, Ljava/lang/Enum;->ordinal()I""")

def player_film(text):
    needle="    const v15, 0x3f0ccccd    # 0.55f"
    assert text.count(needle)==1
    return text.replace(needle,"    const v15, 0x3f570a3d    # 0.84f")

def secondary_bar(text):
    needle="""    .line 348
    invoke-static {v7, v0}, Landroidx/compose/ui/ComposedModifierKt;->materializeModifier(Landroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;)Landroidx/compose/ui/Modifier;"""
    assert text.count(needle)==1
    return text.replace(needle,"""    invoke-static {v0, v7, v6}, Ldev/nino/squareglass/NativeGlass;->secondaryBar(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;

    move-result-object v0

"""+needle)

def footer_icon(text):
    old="IconButton$Rank;->SECONDARY:Lcom/squareup/ui/market/core/components/properties/IconButton$Rank;"
    assert text.count(old)==1
    return text.replace(old,old.replace("->SECONDARY:","->TERTIARY:"))

def playlist(text):
    from prepare_screens_patch import mix_page
    return mix_page(text).replace("SquareDetails;->install(Landroid/view/View;)","SquareDetails;->playlist(Landroid/view/View;)")

if __name__ == "__main__":
    save("com/aspiro/wamp/MainActivity.smali", "smali", "106-square-runtime-theme.patch", activity, hooked=True)
    save("com/aspiro/wamp/settings/compose/n0.smali", "smali_classes21", "107-square-design-settings.patch", settings)

    save("u6/d.smali", "smali_classes19", "108-square-album-header.patch", album)

    save("com/tidal/android/feature/playerscreen/ui/composables/p3.smali", "smali_classes10", "109-square-player-artwork-background.patch", player_style, hooked=True)
    save("com/tidal/android/feature/playerscreen/ui/composables/k3.smali", "smali_classes10", "110-square-player-background-contrast.patch", player_film, hooked=True)
    save("com/tidal/android/feature/playerscreen/ui/PlayerScreenKt.smali", "smali_classes10", "111-square-player-secondary-bar.patch", secondary_bar)
    for number, name in enumerate(("s3", "e5", "l0"),112):
        save(f"com/tidal/android/feature/playerscreen/ui/composables/{name}.smali", "smali_classes10", f"{number:03}-square-player-footer-{name}.patch", footer_icon)
    save("com/aspiro/wamp/playlist/ui/fragment/PlaylistFragment.smali", "smali_classes21", "115-square-playlist-hero.patch", playlist)
