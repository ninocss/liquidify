"""Exact native context-menu lifecycle and legacy settings presentation hooks."""
from prepare_visual_patch import save


def menu_presenter(text):
    old = "    invoke-virtual {v0}, Landroid/app/Dialog;->show()V"
    assert text.count(old) == 1
    text = text.replace("invoke-virtual {v0}, Landroid/app/Dialog;->isShowing()Z", "invoke-static {v0}, Ldev/nino/squareglass/SquareMenus;->showing(Landroid/app/Dialog;)Z")
    return text.replace(old, """    invoke-virtual {v0}, Landroid/app/Dialog;->getOwnerActivity()Landroid/app/Activity;

    move-result-object p1

    iget-object v1, v0, Ly40/c;->b:Landroid/view/View;

    iget-object v2, v0, Ly40/c;->c:Ly40/a;

    invoke-static {v0, p1, v1, v2}, Ldev/nino/squareglass/SquareMenus;->show(Landroid/app/Dialog;Landroid/app/Activity;Landroid/view/View;Landroidx/recyclerview/widget/RecyclerView$Adapter;)V""")


def menu_dismiss(text):
    needle = "    invoke-super {p0}, Landroidx/appcompat/app/AppCompatDialog;->dismiss()V"
    assert text.count(needle) == 1
    return text.replace(needle, """    invoke-static {p0}, Ldev/nino/squareglass/SquareMenus;->dismissed(Landroid/app/Dialog;)V

""" + needle)


def menu_rows(text):
    needle = "    invoke-virtual {p1, v1}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V"
    assert text.count(needle) == 1
    return text.replace(needle, needle + """

    iget v2, v0, Lv40/a;->f:I

    iget v3, v0, Lv40/a;->e:I

    invoke-virtual {v0}, Lv40/a;->a()Z

    move-result v4

    invoke-static {p1, v2, v3, v4}, Ldev/nino/squareglass/SquareMenus;->row(Landroid/view/View;IIZ)V""")


def touch(text):
    assert ".method public dispatchTouchEvent(" not in text
    return text + """
.method public dispatchTouchEvent(Landroid/view/MotionEvent;)Z
    .locals 1

    invoke-static {p0, p1}, Ldev/nino/squareglass/SquareMenus;->touch(Landroid/app/Activity;Landroid/view/MotionEvent;)V

    invoke-super {p0, p1}, Landroidx/appcompat/app/AppCompatActivity;->dispatchTouchEvent(Landroid/view/MotionEvent;)Z

    move-result v0

    return v0
.end method
"""


def settings_list(text):
    needle = "    invoke-static/range {v2 .. v7}, Landroidx/compose/foundation/layout/PaddingKt;->PaddingValues-a9UjIt4$default(FFFFILjava/lang/Object;)Landroidx/compose/foundation/layout/PaddingValues;"
    assert text.count(needle) == 1
    text = text.replace(needle, """    invoke-static {v5}, Ldev/nino/squareglass/SquareScreens;->settingsBottomInset(F)F

    move-result v5

""" + needle)
    needle = "    invoke-static/range {v9 .. v21}, Landroidx/compose/foundation/lazy/LazyDslKt;->LazyColumn("
    assert text.count(needle) == 1
    return text.replace(needle, """    move-object/from16 v10, v19

    invoke-static {v9, v10, v12}, Ldev/nino/squareglass/SquareScreens;->enter(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;

    move-result-object v9

    const/4 v10, 0x0

""" + needle)


def settings_row(text):
    needle = "    invoke-static {v6, v8}, Lcom/tidal/wave/theme/WavePaddingKt;->c(Landroidx/compose/ui/Modifier;Lcom/tidal/wave/designtokens/WaveSpacing;)Landroidx/compose/ui/Modifier;"
    assert text.count(needle) == 1
    return text.replace(needle, """    invoke-static {v6, v7, v9}, Ldev/nino/squareglass/SquareScreens;->settingsRow(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;

    move-result-object v6

""" + needle)


def switch_paint(text):
    # The legacy switch used two white thumb tokens. The adaptive palette maps
    # both to ink, so a checked thumb vanished into its dark track in light mode.
    for signature, owner, rules in (
        (".method public static final b(", "v0", {"c": ("thumbEnabled", 1), "w": ("thumbEnabled", 1), "k": ("thumbDisabled", 2)}),
        (".method public static final c(", "v1", {"x": ("trackOff", 1), "s": ("trackOn", 1), "v": ("trackDisabled", 2)}),
    ):
        start = text.index(signature)
        end = text.index(".end method", start)
        body = text[start:end]
        for method, (helper, count) in rules.items():
            needle = f"invoke-interface {{{owner}}}, Lyk0/c;->{method}()J"
            assert body.count(needle) == count
            body = body.replace(needle, f"invoke-static {{}}, Ldev/nino/squareglass/SquareSwitchPaint;->{helper}()J")
        text = text[:start] + body + text[end:]
    return text


def settings_toggle(text):
    needle = "    sget-object v8, Lcom/tidal/wave/designtokens/WaveSpacing;->Regular:Lcom/tidal/wave/designtokens/WaveSpacing;"
    assert text.count(needle) == 1
    # v9 is the previous fillMaxWidth call's null/default argument here.
    return text.replace(needle, """    invoke-static {v7, v11, v9}, Ldev/nino/squareglass/SquareScreens;->settingsRow(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;

    move-result-object v7

""" + needle)


def explore(text):
    signature = ".method public final onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V"
    start = text.index(signature)
    end = text.index(".end method", start)
    body = text[start:end]
    assert body.count("    return-void") == 1
    # p1 is reused by this method; obtain the actual Fragment root at the end.
    body = body.replace("    return-void", """    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->requireView()Landroid/view/View;

    move-result-object p1

    invoke-static {p1}, Ldev/nino/squareglass/SquareExplore;->install(Landroid/view/View;)V

    return-void""")
    return text[:start] + body + text[end:]


def search_view(text):
    assert ".method protected onAttachedToWindow()V" not in text
    return text + """
.method protected onAttachedToWindow()V
    .locals 0

    invoke-super {p0}, Landroidx/appcompat/widget/SearchView;->onAttachedToWindow()V

    invoke-static {p0}, Ldev/nino/squareglass/SquareExplore;->search(Landroid/view/View;)V

    return-void
.end method
"""


def module_header(text):
    needle = "    invoke-static/range {v4 .. v28}, Landroidx/compose/material/TextKt;->Text--4IGK_g("
    assert text.count(needle) == 2
    return text.replace(needle, """    invoke-static {}, Ldev/nino/squareglass/SquarePalette;->inkColor()J

    move-result-wide v6

    and-int/lit8 v28, v28, -0x5

""" + needle)


def mix_page(text):
    signature = ".method public final onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V"
    start = text.index(signature)
    end = text.index(".end method", start)
    body = text[start:end]
    assert body.count("    return-void") == 1
    body = body.replace("    return-void", """    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->requireView()Landroid/view/View;

    move-result-object p1

    invoke-static {p1}, Ldev/nino/squareglass/SquareDetails;->install(Landroid/view/View;)V

    return-void""")
    return text[:start] + body + text[end:]


def mix_header(text):
    needle = "    invoke-virtual {v1, v3}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V"
    assert text.count(needle) == 1
    return text.replace(needle, needle + """

    move-object/from16 v0, p2

    iget-object v0, v0, Landroidx/recyclerview/widget/RecyclerView$ViewHolder;->itemView:Landroid/view/View;

    invoke-static {v0}, Ldev/nino/squareglass/SquareDetails;->mixHeader(Landroid/view/View;)V""")


def track_row(text):
    needle = "    invoke-virtual {p1, p2}, Landroid/view/View;->setOnCreateContextMenuListener(Landroid/view/View$OnCreateContextMenuListener;)V"
    assert text.count(needle) == 1
    return text.replace(needle, needle + """

    invoke-static {p1}, Ldev/nino/squareglass/SquareDetails;->track(Landroid/view/View;)V""")


def collection_track_row(text):
    needle = "    invoke-virtual {v7, v0}, Landroid/widget/TextView;->setTextColor(Landroid/content/res/ColorStateList;)V"
    assert text.count(needle) == 1
    return text.replace(needle, needle + """

    move-object/from16 v0, p2

    iget-object v0, v0, Landroidx/recyclerview/widget/RecyclerView$ViewHolder;->itemView:Landroid/view/View;

    invoke-static {v0}, Ldev/nino/squareglass/SquareDetails;->track(Landroid/view/View;)V""")


def legacy_track_row(text):
    start = text.index(".method public constructor <init>(Landroid/view/View;Z)V")
    end = text.index(".end method", start)
    body = text[start:end]
    assert body.count("    return-void") == 1
    body = body.replace("    return-void", """    iget-object p1, p0, Landroidx/recyclerview/widget/RecyclerView$ViewHolder;->itemView:Landroid/view/View;

    invoke-static {p1}, Ldev/nino/squareglass/SquareDetails;->track(Landroid/view/View;)V

    return-void""")
    return text[:start] + body + text[end:]


def upload_page(text):
    needle = "    check-cast p1, Landroidx/compose/ui/platform/ComposeView;"
    assert text.count(needle) == 1
    return text.replace(needle, needle + """

    invoke-static {p1}, Ldev/nino/squareglass/SquareScreens;->upload(Landroid/view/View;)V""")


if __name__ == "__main__":
    save("i4/a.smali", "smali", "090-square-context-menu-presentation.patch", menu_presenter)
    save("y40/c.smali", "smali_classes3", "091-square-context-menu-dismiss.patch", menu_dismiss)
    save("y40/a.smali", "smali_classes3", "092-square-context-menu-contrast.patch", menu_rows)
    save("com/aspiro/wamp/MainActivity.smali", "smali", "093-square-menu-anchor.patch", touch, hooked=True)
    save("com/aspiro/wamp/settings/compose/SettingsScreenKt.smali", "smali_classes21", "094-square-settings-page.patch", settings_list)
    save("com/aspiro/wamp/settings/compose/c0.smali", "smali_classes21", "095-square-settings-cards.patch", settings_row)
    save("com/tidal/wave/components/WaveSwitchKt.smali", "smali_classes17", "096-square-switch-contrast.patch", switch_paint)
    save("com/aspiro/wamp/settings/compose/h0.smali", "smali_classes21", "097-square-settings-toggle-cards.patch", settings_toggle)
    save("a8/g.smali", "smali_classes19", "098-square-explore-page.patch", explore)
    save("com/aspiro/wamp/widgets/TidalSearchView.smali", "smali_classes21", "099-square-native-search-field.patch", search_view)
    save("t7/d.smali", "smali_classes19", "100-square-module-header-contrast.patch", module_header)
    save("d8/d.smali", "smali_classes19", "101-square-mix-page.patch", mix_page)
    save("e7/d.smali", "smali_classes19", "102-square-mix-header.patch", mix_header)
    save("t6/q.smali", "smali_classes19", "103-square-track-artwork.patch", track_row)
    save("v6/e.smali", "smali_classes19", "104-square-collection-track-artwork.patch", collection_track_row)
    save("n8/d.smali", "smali_classes19", "105-square-legacy-track-artwork.patch", legacy_track_row)
    save("com/aspiro/wamp/features/upload/uploads/UploadTabsFragment.smali", "smali_classes20", "144-square-upload-padding.patch", upload_page)
    print("Prepared native contextual menus, settings cards and page arrival")
