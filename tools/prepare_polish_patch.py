"""Pinned native queue and shared text fields; retain native callbacks."""
from prepare_visual_patch import save

def queue(text):
    start=text.index('.method public final onViewCreated(')
    end=text.index('.end method',start)
    body=text[start:end]
    assert body.count('    return-void')==1
    body=body.replace('    return-void','''    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->requireView()Landroid/view/View;

    move-result-object p1

    invoke-static {p1}, Ldev/nino/squareglass/QueueGlass;->install(Landroid/view/View;)V

    return-void''')
    text=text[:start]+body+text[end:]
    start=text.index('.method public final onCreateDialog(')
    end=text.index('.end method',start)
    body=text[start:end]
    assert body.count('    return-object p1')==1
    body=body.replace('    return-object p1',"    invoke-static {p1}, Ldev/nino/squareglass/QueueGlass;->dialog(Landroid/app/Dialog;)V\n\n    return-object p1")
    return text[:start]+body+text[end:]

def fields(text):
    needle='    invoke-static/range {v16 .. v34}, Landroidx/compose/foundation/text/BasicTextFieldKt;->BasicTextField('
    assert text.count(needle)==1
    return text.replace(needle,'''    invoke-static/range {v18 .. v18}, Ldev/nino/squareglass/GlassPanels;->field(Landroidx/compose/ui/Modifier;)Landroidx/compose/ui/Modifier;

    move-result-object v18

'''+needle)

if __name__=='__main__':
    save('com/aspiro/wamp/nowplaying/view/playqueue/l.smali','smali','144-square-queue-glass.patch',queue)
    save('com/tidal/wave/components/WaveTextFieldKt.smali','smali_classes17','145-square-text-field-glass.patch',fields)
    print('Prepared native queue and shared text-field glass')


def mini_skip(text):
    padding = '''    .line 37
    invoke-static {v9, v10, v12, v6, v8}, Landroidx/compose/foundation/layout/PaddingKt;->padding-qDBjuR0(Landroidx/compose/ui/Modifier;FFFF)Landroidx/compose/ui/Modifier;'''
    assert text.count(padding)==1
    # The stock row uses 12 dp above and 8 dp below. Equal padding keeps
    # artwork, labels and transport centered within the capsule.
    text=text.replace(padding, '    move v8, v12\n\n'+padding)
    start=text.index('    .line 156\n    sget v4, Lcom/tidal/android/strings/R$string;->play_next:I')
    end=text.index('    .line 162',start)
    body=text[start:end]
    assert body.count('MarketIconButtonKt;->b(')==1
    return text[:start]+"    invoke-static {}, Ldev/nino/squareglass/NativeMiniGestures;->showSkip()Z\n\n    move-result v4\n\n    if-eqz v4, :square_no_skip\n\n"+body+'    :square_no_skip\n'+text[end:]

if __name__=='__main__':
    save('com/tidal/android/feature/appscaffold/ui/composable/MiniPlayerKt.smali','smali_classes5','146-square-gesture-mini-controls.patch',mini_skip,hooked=True)
