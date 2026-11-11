/* GPL-3.0-or-later. Paint only; native tabs, queue and actions remain bound. */
package dev.nino.squareglass;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.widget.ImageView;
import com.google.android.material.tabs.TabLayout;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.appcompat.widget.Toolbar;

public final class QueueGlass {
    private QueueGlass() {}
    private static java.lang.ref.WeakReference<Dialog> current=new java.lang.ref.WeakReference<>(null);
    public static void dialog(Dialog dialog) {
        current=new java.lang.ref.WeakReference<>(dialog);
        if(SquareSettings.themeEnabled()!=0 && dialog.getWindow()!=null) SystemBarTheme.installWindow(dialog.getWindow());
    }
    private static void icons(View view) {
        if(view instanceof ImageView && (view.getId()==0x7f0b04de || view.getId()==0x7f0b0447))
            ((ImageView)view).setImageTintList(ColorStateList.valueOf(SquarePalette.ink(SquarePalette.isDark())));
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) icons(((ViewGroup)view).getChildAt(i));
    }
    private static void roundThumbnails(View view) {
        if (view instanceof ImageView && (view.getId() == 0x7f0b00b8 || view.getId() == 0x7f0b03d3)) {
            int r = Math.round(10 * view.getResources().getDisplayMetrics().density);
            view.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
                }
            });
            view.setClipToOutline(true);
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) roundThumbnails(((ViewGroup)view).getChildAt(i));
    }
    public static void install(View root) {
        if(SquareSettings.themeEnabled()==0 || !(root instanceof ViewGroup)) return;
        Context context=root.getContext();
        while(context instanceof ContextWrapper && !(context instanceof Activity)) {
            Context next=((ContextWrapper)context).getBaseContext(); if(next==context) break; context=next;
        }
        if(!(context instanceof Activity)) return;
        LiquidMenuSurface glass=new LiquidMenuSurface((Activity)context,null);
        FrameLayout paint=new FrameLayout(root.getContext());
        glass.paint(paint,0,false);
        paint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        // The first native child is its artwork background. Insert above that
        // image and below the toolbar, tabs and list, preserving all constraints.
        ((ViewGroup)root).addView(paint,1,new ViewGroup.LayoutParams(-1,-1));
        View background=root.findViewById(0x7f0b04bd); if(background!=null) background.setAlpha(0f);
        View toolbar=root.findViewById(0x7f0b06ef);
        int ink=SquarePalette.ink(SquarePalette.isDark());
        View tabs=root.findViewById(0x7f0b06b4);
        if(tabs instanceof TabLayout) {
            ((TabLayout)tabs).setSelectedTabIndicatorColor(ink);
            ((TabLayout)tabs).setTabTextColors((ink&0xffffff)|0x99000000,ink);
        }
        if(toolbar instanceof Toolbar) {
            if(((Toolbar)toolbar).getNavigationIcon()!=null) ((Toolbar)toolbar).getNavigationIcon().mutate().setTint(ink);
            if(((Toolbar)toolbar).getOverflowIcon()!=null) ((Toolbar)toolbar).getOverflowIcon().mutate().setTint(ink);
            ((Toolbar)toolbar).getMenu().add("Sleep timer").setOnMenuItemClickListener(item -> { Dialog host=current.get();
                Context base=root.getContext(); while(base instanceof ContextWrapper && !(base instanceof Activity)) base=((ContextWrapper)base).getBaseContext();
                if(host!=null && base instanceof Activity) SleepTimer.showInDialog(host,(Activity)base);
                else SleepTimer.show(root.getContext()); return true; });
        }
        SquareExplore.install(root);
        SurfaceMotion.enter(root,false,true);
        root.post(() -> { if(root.isAttachedToWindow()) { icons(root); roundThumbnails(root); } });
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) {}
            public void onViewDetachedFromWindow(View view) { glass.release(); root.removeOnAttachStateChangeListener(this); }
        });
    }
}
