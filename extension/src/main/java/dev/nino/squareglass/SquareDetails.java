/* Square Glass for TIDAL. GPL-3.0-or-later. */
package dev.nino.squareglass;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import java.util.WeakHashMap;

/** Scoped legacy mix and catalogue adapters; native models and actions are retained. */
public final class SquareDetails {
    private SquareDetails() {}
    private static final int ARTWORK = 0x7f0b00b8, TITLE = 0x7f0b06e3, SUBTITLE = 0x7f0b069b;
    private static final int FIRST = 0x7f0b056e, SECOND = 0x7f0b056f;
    private static final WeakHashMap<View, java.lang.ref.WeakReference<Binding>> bindings = new WeakHashMap<>();
    private static final WeakHashMap<View, java.lang.ref.WeakReference<Hero>> heroes = new WeakHashMap<>();
    private static final WeakHashMap<View, Drawable> buttonPaints = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> buttonColors = new WeakHashMap<>();
    private static final WeakHashMap<View, Drawable> trackPaints = new WeakHashMap<>();
    private static final WeakHashMap<Drawable, Integer> seedCache = new WeakHashMap<>();
    private static final WeakHashMap<Bitmap, Integer> bitmapSeedCache = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> appliedTints = new WeakHashMap<>();

    public static boolean isPlaceholder(Drawable drawable) {
        if (drawable == null) return true;
        Bitmap sample = Hero.sample(drawable);
        if (sample == null) return false;
        int[] pixels = new int[24 * 24];
        sample.getPixels(pixels, 0, 24, 0, 0, 24, 24);
        sample.recycle();
        int c0 = pixels[0] & 0xffffff;
        int c1 = pixels[23] & 0xffffff;
        int c2 = pixels[pixels.length - 24] & 0xffffff;
        int c3 = pixels[pixels.length - 1] & 0xffffff;
        if (c0 == c1 && c1 == c2 && c2 == c3) {
            int r = (c0 >> 16) & 0xff;
            int g = (c0 >> 8) & 0xff;
            int b = c0 & 0xff;
            if (r < 40 && g < 40 && b < 45 && Math.abs(r - g) <= 6 && Math.abs(g - b) <= 8) {
                return true;
            }
        }
        return false;
    }

    public static int coverSeed(Drawable drawable) {
        if (drawable == null) return 0xff808080;
        Integer cached = seedCache.get(drawable);
        if (cached != null) return cached;
        if (drawable instanceof BitmapDrawable) {
            Bitmap bm = ((BitmapDrawable)drawable).getBitmap();
            if (bm != null) {
                Integer bmCached = bitmapSeedCache.get(bm);
                if (bmCached != null) {
                    seedCache.put(drawable, bmCached);
                    return bmCached;
                }
            }
        }
        Bitmap sample = Hero.sample(drawable);
        if (sample == null) return 0xff808080;
        int[] pixels = new int[24 * 24];
        sample.getPixels(pixels, 0, 24, 0, 0, 24, 24);
        sample.recycle();
        int seed = CoverColors.seed(pixels);
        seedCache.put(drawable, seed);
        if (drawable instanceof BitmapDrawable) {
            Bitmap bm = ((BitmapDrawable)drawable).getBitmap();
            if (bm != null) bitmapSeedCache.put(bm, seed);
        }
        return seed;
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
    private static void rounded(View view, int radius) {
        if (view == null) return;
        final int pixels = dp(view, radius);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View target, Outline outline) {
                outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(), pixels);
            }
        });
        view.setClipToOutline(true);
    }
    public static void track(View row) {
        if (SquareSettings.themeEnabled() == 0) return;
        if (row.findViewById(FIRST) != null || row.findViewById(0x7f0b0568) != null) return;
        rounded(row.findViewById(ARTWORK), 10);
        if (row.findViewById(ARTWORK) != null) SquareGestures.bind(row);
    }
    /** Removes native one-off outlines while keeping each row's press feedback and click listeners. */
    public static void collectionTrack(View row) {
        if (SquareSettings.themeEnabled()==0) return;
        Drawable current=trackPaints.get(row);
        if(current!=null && row.getBackground()==current)return;
        Drawable paint=new RippleDrawable(ColorStateList.valueOf((SquarePalette.ink(SquarePalette.isDark())&0xffffff)|0x16000000),
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),null);
        row.setBackground(paint);trackPaints.put(row,paint);
    }
    private static void foreground(View view, int color) {
        if (view == null) return;
        Integer current = appliedTints.get(view);
        if (view instanceof androidx.appcompat.widget.Toolbar) {
            if (current == null || current != color) {
                androidx.appcompat.widget.Toolbar toolbar = (androidx.appcompat.widget.Toolbar)view;
                toolbar.setTitleTextColor(color);
                if (toolbar.getNavigationIcon() != null) toolbar.getNavigationIcon().mutate().setTint(color);
                if (toolbar.getOverflowIcon() != null) toolbar.getOverflowIcon().mutate().setTint(color);
                android.view.Menu menu = toolbar.getMenu();
                for (int i = 0; i < menu.size(); i++) {
                    android.view.MenuItem item = menu.getItem(i);
                    if (item.getIcon() != null) item.getIcon().mutate().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                    ColorStateList tint = item.getIconTintList();
                    if (tint == null || tint.getDefaultColor() != color) item.setIconTintList(ColorStateList.valueOf(color));
                }
                appliedTints.put(view, color);
            }
        }
        else if (view instanceof TextView) {
            if (current == null || current != color) {
                TextView text = (TextView)view;
                if (text.getCurrentTextColor() != color) text.setTextColor(color);
                for (android.graphics.drawable.Drawable icon : text.getCompoundDrawablesRelative())
                    if (icon != null) icon.mutate().setTint(color);
                appliedTints.put(view, color);
            }
        }
        else if (view instanceof ImageView) {
            if (current == null || current != color) {
                ImageView iv = (ImageView)view;
                ColorStateList tint = iv.getImageTintList();
                if (tint == null || tint.getDefaultColor() != color) iv.setImageTintList(ColorStateList.valueOf(color));
                appliedTints.put(view, color);
            }
        }
        else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) foreground(group.getChildAt(i), color);
        }
    }
    private static void button(View view, boolean primary) {
        if (view == null) return;
        boolean dark = SquarePalette.isDark();
        int ink = SquarePalette.ink(dark);
        int fill = SquarePalette.surface(dark);
        view.setBackgroundTintList(null);
        if (buttonPaints.get(view) == view.getBackground() && Integer.valueOf(fill).equals(buttonColors.get(view))) {
            foreground(view, ink);
            return;
        }
        GradientDrawable shape = new GradientDrawable();
        Drawable fallback = PageMaterial.background(view, SquarePalette.base(dark), 24);
        shape.setColor(fill);
        shape.setCornerRadius(dp(view, 24));
        PageGlass.install(view, 24, primary);
        if (PageGlass.hasSurface(view)) shape.setColor(android.graphics.Color.TRANSPARENT);
        int rippleColor = dark ? (primary ? 0x33ffffff : 0x22ffffff) : (primary ? 0x22000000 : 0x18000000);
        Drawable background = PageGlass.hasSurface(view)
                ? new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, null)
                : fallback;
        view.setBackground(background);
        buttonPaints.put(view, background);
        buttonColors.put(view, fill);
        foreground(view, ink);
    }
    /** Equal native play controls with a 12dp gap and scalable touch targets. */
    public static void controls(View root,int playId,int shuffleId) {
        View play=root.findViewById(playId),shuffle=root.findViewById(shuffleId);
        if(play==null || shuffle==null || play.getParent()!=shuffle.getParent())return;
        for(View v:new View[]{play,shuffle}) {
            if(!(v.getLayoutParams() instanceof ConstraintLayout.LayoutParams))continue;
            ConstraintLayout.LayoutParams p=(ConstraintLayout.LayoutParams)v.getLayoutParams();
            ViewGroup.LayoutParams size=p;
            ViewGroup.MarginLayoutParams margins=p;
            int height=dp(v,Math.round(48*Math.max(1f,v.getResources().getConfiguration().fontScale)));
            boolean first=v==play;
            int left=dp(v,first?16:6),right=dp(v,first?6:16);
            if(size.width==0 && size.height==height && margins.leftMargin==left && margins.rightMargin==right &&
                    p.horizontalWeight==1f && p.bottomToTop==-1 && p.bottomToBottom==-1)continue;
            size.width=0;size.height=height;
            p.horizontalWeight=1f;p.horizontalChainStyle=ConstraintLayout.LayoutParams.CHAIN_SPREAD;
            p.bottomToTop=-1;p.bottomToBottom=-1;
            p.leftToLeft=first?0:-1;p.leftToRight=first?-1:playId;
            p.rightToLeft=first?shuffleId:-1;p.rightToRight=first?-1:0;
            p.startToStart=-1;p.startToEnd=-1;p.endToStart=-1;p.endToEnd=-1;
            margins.setMargins(left,margins.topMargin,right,margins.bottomMargin);margins.setMarginStart(left);margins.setMarginEnd(right);
            v.setLayoutParams(p);
        }
        button(play,true);button(shuffle,false);
    }
    /** Compact unfilled native actions beneath the primary play controls. */
    private static void actionRail(View root,int anchor,int... ids) {
        if(SquareSettings.themeEnabled()==0)return;
        java.util.ArrayList<View> actions=new java.util.ArrayList<>();
        for(int id:ids) {
            View action=root.findViewById(id);if(action==null || action.getVisibility()==View.GONE)continue;
            unfilledAction(action);
            below(action,anchor,12);
            if(action.getParent() instanceof ConstraintLayout && action.getLayoutParams() instanceof ConstraintLayout.LayoutParams)
                actions.add(action);
        }
        if(actions.size()<2)return;
        Object parent=actions.get(0).getParent();
        for(View action:actions)if(action.getParent()!=parent)return;
        for(int i=0;i<actions.size();i++) {
            View action=actions.get(i);ConstraintLayout.LayoutParams p=(ConstraintLayout.LayoutParams)action.getLayoutParams();
            ViewGroup.LayoutParams size=p;
            int height=dp(action,Math.round(64*Math.max(1f,action.getResources().getConfiguration().fontScale)));
            int start=dp(action,i==0?16:6),end=dp(action,i==actions.size()-1?16:6);
            if(size.width==0 && size.height==height && p.horizontalWeight==1f && p.startToStart==(i==0?0:-1) &&
                    p.startToEnd==(i==0?-1:actions.get(i-1).getId()) && p.endToEnd==(i==actions.size()-1?0:-1) &&
                    p.endToStart==(i==actions.size()-1?-1:actions.get(i+1).getId()) &&
                    p.bottomToTop==-1 && p.bottomToBottom==-1 &&
                    ((ViewGroup.MarginLayoutParams)p).getMarginStart()==start && ((ViewGroup.MarginLayoutParams)p).getMarginEnd()==end)continue;
            size.width=0;size.height=height;p.horizontalWeight=1f;p.horizontalChainStyle=ConstraintLayout.LayoutParams.CHAIN_SPREAD;
            p.leftToLeft=-1;p.leftToRight=-1;p.rightToLeft=-1;p.rightToRight=-1;
            p.bottomToTop=ConstraintLayout.LayoutParams.UNSET;p.bottomToBottom=ConstraintLayout.LayoutParams.UNSET;
            p.startToStart=i==0?ConstraintLayout.LayoutParams.PARENT_ID:ConstraintLayout.LayoutParams.UNSET;
            p.startToEnd=i==0?ConstraintLayout.LayoutParams.UNSET:actions.get(i-1).getId();
            p.endToStart=i==actions.size()-1?ConstraintLayout.LayoutParams.UNSET:actions.get(i+1).getId();
            p.endToEnd=i==actions.size()-1?ConstraintLayout.LayoutParams.PARENT_ID:ConstraintLayout.LayoutParams.UNSET;
            ((ViewGroup.MarginLayoutParams)p).setMarginStart(start);((ViewGroup.MarginLayoutParams)p).setMarginEnd(end);
            ((ViewGroup.MarginLayoutParams)p).leftMargin=start;((ViewGroup.MarginLayoutParams)p).rightMargin=end;
            ((ViewGroup.MarginLayoutParams)p).bottomMargin=dp(action,12);
            action.setLayoutParams(p);
        }
    }
    private static void unfilledAction(View action) {
        int ink=SquarePalette.ink(SquarePalette.isDark());
        if(!buttonPaints.containsKey(action) || action.getBackground()!=buttonPaints.get(action)) {
            action.setBackgroundTintList(null);
            Drawable ripple=new RippleDrawable(ColorStateList.valueOf((ink&0xffffff)|0x18000000),null,null);
            action.setBackground(ripple);buttonPaints.put(action,ripple);
        }
        // SecondaryActionButton owns a separate native background view.
        // Clear that decoration as well; keep its live icon/label/state binding.
        View decoration=action.findViewById(0x7f0b05b4);
        if(decoration!=null && decoration.getBackground()!=null)decoration.setBackground(null);
        View downloadDecoration=action.findViewById(0x7f0b0277);
        if(downloadDecoration!=null && downloadDecoration.getForeground()!=null)downloadDecoration.setForeground(null);
        View icon=action.findViewById(0x7f0b035d),label=action.findViewById(0x7f0b06c8);
        boolean download=icon==null;
        if(download)icon=action.findViewById(0x7f0b0276);
        if(label==null)label=action.findViewById(0x7f0b0279);
        if(icon!=null && icon.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams p=(ConstraintLayout.LayoutParams)icon.getLayoutParams();
            ViewGroup.LayoutParams size=p;ViewGroup.MarginLayoutParams margins=p;
            int top=dp(icon,download?10:8);
            if(size.width!=dp(icon,24) || size.height!=dp(icon,24) || margins.topMargin!=top || p.topToTop!=0 || p.topToBottom!=-1) {
                size.width=dp(icon,24);size.height=dp(icon,24);margins.topMargin=top;
                p.topToTop=0;p.topToBottom=-1;icon.setLayoutParams(p);
            }
        }
        if(label!=null && label.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams p=(ConstraintLayout.LayoutParams)label.getLayoutParams();
            ViewGroup.MarginLayoutParams margins=p;
            if(p.bottomToBottom!=-1 || p.topToTop!=0 || p.topToBottom!=-1 || margins.topMargin!=dp(label,38)) {
                p.bottomToBottom=-1;p.topToTop=0;p.topToBottom=-1;
                margins.topMargin=dp(label,38);label.setLayoutParams(p);
            }
        }
        foreground(action,ink);
    }
    public static void chrome(View view) {foreground(view,SquarePalette.ink(SquarePalette.isDark()));}
    private static void labels(View view) {
        if(view instanceof TextView) {
            TextView text=(TextView)view;int color=text.getCurrentTextColor();
            if((color&0xffffff)==0xffffff || (color&0xffffff)==0 || color==0xff15161a)
                text.setTextColor(SquarePalette.ink(SquarePalette.isDark()));
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)labels(((ViewGroup)view).getChildAt(i));
    }
    public static void control(View view,boolean primary) {button(view,primary);}
    public static void mixHeader(View root) {
        if (SquareSettings.themeEnabled() == 0) return;
        boolean dark = SquarePalette.isDark();
        root.setBackgroundColor(SquarePalette.base(dark));
        ImageView artwork = root.findViewById(ARTWORK);
        if (artwork != null && artwork.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams)artwork.getLayoutParams();
            ViewGroup.LayoutParams size = params;
            int width = Math.min(root.getResources().getDisplayMetrics().widthPixels, dp(root, 560));
            size.width = width; size.height = width;
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            params.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            ViewGroup.MarginLayoutParams margins = params;
            margins.topMargin = 0; margins.bottomMargin = 0;
            artwork.setLayoutParams(params);
            artwork.setForeground(new CoverFade(SquarePalette.base(dark)));
            artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
            artwork.setClipToOutline(false);
            artwork.setElevation(0);
            ViewGroup.LayoutParams page = root.getLayoutParams();
            page.height = size.height + dp(root, Math.round(232 * root.getResources().getConfiguration().fontScale));
            root.setLayoutParams(page);
            hero(root, artwork);
        }
        foreground(root.findViewById(TITLE), SquarePalette.ink(dark));
        foreground(root.findViewById(SUBTITLE), dark ? 0xffb4b8c4 : 0xff5d626d);
        button(root.findViewById(FIRST), true);
        button(root.findViewById(SECOND), false);
        for (int id : new int[]{0x7f0b02c6, 0x7f0b0274, 0x7f0b064c})
            foreground(root.findViewById(id), SquarePalette.ink(dark));
    }
    private static GradientDrawable fade(View view, float top, float bottom) {
        int base = SquarePalette.base(SquarePalette.isDark()) & 0xffffff;
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                base | Math.round(255*top)<<24, base | Math.round(255*.25f)<<24,
                base | Math.round(255*bottom)<<24});
    }
    /** Native animated covers remain mounted and keep their playback lifecycle. */
    public static void albumHeader(View root) {
        if (SquareSettings.themeEnabled()==0) return;
        boolean dark=SquarePalette.isDark();
        View cover=root.findViewById(0x7f0b00a9);
        if(cover!=null && cover.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams p=(ConstraintLayout.LayoutParams)cover.getLayoutParams();
            ((ViewGroup.LayoutParams)p).width=root.getResources().getDisplayMetrics().widthPixels; ((ViewGroup.LayoutParams)p).height=((ViewGroup.LayoutParams)p).width;
            p.topToTop=0; p.topToBottom=-1; p.bottomToTop=-1; p.bottomToBottom=-1;
            p.startToStart=0; p.endToEnd=0; p.verticalBias=0;
            ((ViewGroup.MarginLayoutParams)p).topMargin=0; ((ViewGroup.MarginLayoutParams)p).bottomMargin=0;
            ViewGroup.MarginLayoutParams margins=p;margins.setMargins(0,0,0,0);margins.setMarginStart(0);margins.setMarginEnd(0);
            cover.setPadding(0,0,0,0);cover.setLayoutParams(p); cover.setElevation(0); cover.setClipToOutline(false);
            View card=cover.findViewById(0x7f0b008b);
            if(card!=null) {
                NativeArtworkBridge.square(card);ViewGroup.LayoutParams cp=card.getLayoutParams();cp.width=-1;cp.height=-1;
                if(cp instanceof ViewGroup.MarginLayoutParams) {ViewGroup.MarginLayoutParams cm=(ViewGroup.MarginLayoutParams)cp;cm.setMargins(0,0,0,0);cm.setMarginStart(0);cm.setMarginEnd(0);}
                card.setLayoutParams(cp);
            }
            cover.setForeground(new CoverFade(SquarePalette.base(dark)));
            below(root.findViewById(TITLE),0x7f0b00a9,8);
            below(root.findViewById(0x7f0b00b6),TITLE,8);
            below(root.findViewById(0x7f0b05c1),0x7f0b00b6,8);
            below(root.findViewById(FIRST),0x7f0b05c1,20);
            View secondary=root.findViewById(SECOND);
            if(secondary!=null && secondary.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams q=(ConstraintLayout.LayoutParams)secondary.getLayoutParams();
                q.topToTop=FIRST;q.topToBottom=-1;q.bottomToBottom=-1;q.bottomToTop=-1;((ViewGroup.MarginLayoutParams)q).topMargin=0;
                secondary.setLayoutParams(q);
            }
            actionRail(root,FIRST,0x7f0b02c6,0x7f0b0370,0x7f0b0274,0x7f0b064c);
            ViewGroup.LayoutParams size=root.getLayoutParams();size.height=-2;root.setLayoutParams(size);
        }
        ImageView actualCover = null;
        if (cover instanceof ImageView) actualCover = (ImageView)cover;
        else if (cover instanceof ViewGroup) {
            View candidate = cover.findViewById(0x7f0b00b8);
            if (candidate instanceof ImageView) actualCover = (ImageView)candidate;
            else {
                for (int i = 0; i < ((ViewGroup)cover).getChildCount(); i++) {
                    View ch = ((ViewGroup)cover).getChildAt(i);
                    if (ch instanceof ImageView) { actualCover = (ImageView)ch; break; }
                }
            }
        }
        ImageView backdrop=root.findViewById(0x7f0b00b9);
        if(backdrop!=null) backdrop.setVisibility(View.GONE);
        if(actualCover!=null) hero(root, actualCover);
        else if(backdrop!=null) hero(root, backdrop);
        for(int id:new int[]{TITLE,0x7f0b00b6,0x7f0b05c1,0x7f0b02c6,0x7f0b0370,0x7f0b0274,0x7f0b064c})
            foreground(root.findViewById(id),SquarePalette.ink(dark));
        controls(root,FIRST,SECOND);
    }
    private static void hero(View root, ImageView image) {
        java.lang.ref.WeakReference<Hero> existing=heroes.get(root);
        if (existing != null && existing.get() != null) { existing.get().last=null; return; }
        Hero hero = new Hero(root,image); heroes.put(root,new java.lang.ref.WeakReference<>(hero)); root.addOnAttachStateChangeListener(hero);
        if (root.isAttachedToWindow()) hero.onViewAttachedToWindow(root);
    }
    public static void playlist(View page) {
        install(page);
        View header=page.findViewById(0x7f0b057a);
        if (header == null) return;
        ImageView image=header.findViewById(ARTWORK);
        if (image != null && image.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams params=(ConstraintLayout.LayoutParams)image.getLayoutParams();
            ViewGroup.LayoutParams size=params;
            size.width=Math.min(page.getResources().getDisplayMetrics().widthPixels,dp(page,560));
            size.height=size.width;
            params.topToTop=0; params.bottomToTop=-1; params.bottomToBottom=-1;
            params.startToStart=0; params.endToEnd=0;
            ((ViewGroup.MarginLayoutParams)params).topMargin=0;
            ((ViewGroup.MarginLayoutParams)params).bottomMargin=0;
            image.setLayoutParams(params); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setForeground(new CoverFade(SquarePalette.base(SquarePalette.isDark())));
            image.setElevation(0); image.setClipToOutline(false);
            ViewGroup.LayoutParams headerSize=header.getLayoutParams();
            headerSize.height=ViewGroup.LayoutParams.WRAP_CONTENT;
            header.setLayoutParams(headerSize); hero(header,image);
        }
        below(header.findViewById(TITLE),ARTWORK,-20);
        below(header.findViewById(0x7f0b021f),TITLE,8);
        below(header.findViewById(0x7f0b0239),0x7f0b021f,8);
        below(header.findViewById(0x7f0b057b),0x7f0b0239,12);
        below(header.findViewById(0x7f0b0568),0x7f0b057b,20);
        View shuffle=header.findViewById(0x7f0b065c);
        if (shuffle != null) {
            ConstraintLayout.LayoutParams params=(ConstraintLayout.LayoutParams)shuffle.getLayoutParams();
            params.topToTop=0x7f0b0568; params.topToBottom=-1; params.bottomToBottom=-1; params.bottomToTop=-1;
            ((ViewGroup.MarginLayoutParams)params).topMargin=0; shuffle.setLayoutParams(params);
        }
        actionRail(header,0x7f0b0568,0x7f0b02c6,0x7f0b0290,0x7f0b0274,0x7f0b0591,0x7f0b064c);
        ImageView background=header.findViewById(0x7f0b00b9);
        if (background != null) background.setVisibility(View.GONE);
    }
    private static void below(View view,int anchor,int gap) {
        if (view == null || !(view.getLayoutParams() instanceof ConstraintLayout.LayoutParams)) return;
        ConstraintLayout.LayoutParams params=(ConstraintLayout.LayoutParams)view.getLayoutParams();
        if(params.topToTop==-1 && params.topToBottom==anchor && params.bottomToTop==-1 && params.bottomToBottom==-1 &&
            ((ViewGroup.MarginLayoutParams)params).topMargin==dp(view,gap) && ((ViewGroup.MarginLayoutParams)params).bottomMargin==0)return;
        params.topToTop=-1; params.topToBottom=anchor; params.bottomToTop=-1; params.bottomToBottom=-1;
        ((ViewGroup.MarginLayoutParams)params).topMargin=dp(view,gap);
        ((ViewGroup.MarginLayoutParams)params).bottomMargin=0; view.setLayoutParams(params);
    }
    private static void playlistPaint(View page) {
        View header=page.findViewById(0x7f0b057a);
        if (header == null) return;
        boolean dark=SquarePalette.isDark();
        foreground(header.findViewById(TITLE),SquarePalette.ink(dark));
        for (int id : new int[]{0x7f0b021f,0x7f0b0239,0x7f0b057b,0x7f0b0579})
            foreground(header.findViewById(id),dark ? 0xffb4b8c4 : 0xff5d626d);
        controls(header,0x7f0b0568,0x7f0b065c);
        // Native ownership/availability arrives after onViewCreated. Reflow the
        // action rail once those initially GONE controls become visible too.
        actionRail(header,0x7f0b0568,0x7f0b02c6,0x7f0b0290,0x7f0b0274,0x7f0b0591,0x7f0b064c);
        for (int id : new int[]{0x7f0b02c6,0x7f0b0290,0x7f0b0274,0x7f0b0591,0x7f0b064c})
            foreground(header.findViewById(id),SquarePalette.ink(dark));
    }
    /** Small cached artwork sample gives the entire page its cover-derived atmosphere. */
    private static final class Hero implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        final View root; final ImageView artwork; Drawable last; boolean watching, lastDark;
        Hero(View root,ImageView artwork) { this.root=root; this.artwork=artwork; }
        @Override public boolean onPreDraw() {
            Drawable current=artwork.getDrawable();
            while (current instanceof coil.drawable.CrossfadeDrawable) current=((coil.drawable.CrossfadeDrawable)current).k;
            boolean dark=SquarePalette.isDark();
            if (current != null && (current != last || dark != lastDark) && current.getIntrinsicWidth()>0) {
                Bitmap sample=sample(current);
                if (sample == null) return true;
                last=current; lastDark=dark;
                int[] pixels=new int[24*24]; sample.getPixels(pixels,0,24,0,0,24,24); sample.recycle();
                int surface=CoverColors.surface(CoverColors.seed(pixels),dark);
                root.setBackgroundColor(surface);
                if (artwork.getId() == ARTWORK) artwork.setForeground(new CoverFade(surface));
                else {
                    View cover=root.findViewById(0x7f0b00a9);
                    if(cover!=null) cover.setForeground(new CoverFade(surface));
                    artwork.setForeground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{(surface&0xffffff)|0xe6000000,surface}));
                }
                View parent=root;
                while (parent.getParent() instanceof View) {
                    parent=(View)parent.getParent();
                    View candidate=parent instanceof RecyclerView ? parent : parent.findViewById(0x7f0b05b8);
                    if (candidate instanceof RecyclerView) {
                        candidate.setBackgroundColor(surface);
                        // The toolbar and the list share one page surface, including overscroll.
                        if (candidate.getParent() instanceof View) ((View)candidate.getParent()).setBackgroundColor(surface);
                        break;
                    }
                }
            }
            return true;
        }
        private static Bitmap sample(Drawable source) {
            // Coil crossfades wrap hardware-backed BitmapDrawables on real devices too.
            while (source instanceof coil.drawable.CrossfadeDrawable) {
                source=((coil.drawable.CrossfadeDrawable)source).k;
                if (source == null) return null;
            }
            Bitmap sample=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(sample);
            if (source instanceof BitmapDrawable) {
                Bitmap original=((BitmapDrawable)source).getBitmap();
                Bitmap readable=original.getConfig()==Bitmap.Config.HARDWARE ? original.copy(Bitmap.Config.ARGB_8888,false) : original;
                if (readable == null) { sample.recycle(); return null; }
                canvas.drawBitmap(readable,null,new Rect(0,0,24,24),new Paint(Paint.FILTER_BITMAP_FLAG));
                if (readable != original) readable.recycle();
            } else {
                Rect original=new Rect(source.getBounds());
                try { source.setBounds(0,0,24,24); source.draw(canvas); }
                catch (IllegalArgumentException unsupported) { sample.recycle(); return null; }
                finally { source.setBounds(original); }
            }
            return sample;
        }
        @Override public void onViewAttachedToWindow(View view) {
            if (!watching) { root.getViewTreeObserver().addOnPreDrawListener(this); watching=true; }
        }
        @Override public void onViewDetachedFromWindow(View view) {
            if (watching && root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
            watching=false; last=null;
        }
    }
    static final class CoverFade extends Drawable {
        final int surface;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        CoverFade(int surface) { this.surface=surface; }
        @Override protected void onBoundsChange(Rect bounds) {
            int rgb=surface&0xffffff;
            paint.setShader(new LinearGradient(0,bounds.top,0,Math.max(bounds.top+1,bounds.bottom),
                new int[]{rgb|0xc7000000,rgb,rgb,rgb|0x3d000000,rgb|0xc2000000,surface},
                new float[]{0,.20f,.56f,.74f,.90f,1},Shader.TileMode.CLAMP));
        }
        @Override public void draw(Canvas canvas) {
            canvas.drawRect(((Drawable)this).getBounds(),paint);
        }
        @Override public void setAlpha(int value) { paint.setAlpha(value); ((Drawable)this).invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter value) { paint.setColorFilter(value); ((Drawable)this).invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
    public static void install(View root) {
        if (SquareSettings.themeEnabled() == 0) return;
        java.lang.ref.WeakReference<Binding> existing=bindings.get(root);
        Binding binding=existing==null?null:existing.get();
        if(binding!=null) { if(root.isAttachedToWindow()) binding.onViewAttachedToWindow(root); return; }
        binding=new Binding(root); bindings.put(root,new java.lang.ref.WeakReference<>(binding));
        root.addOnAttachStateChangeListener(binding);
        if(root.isAttachedToWindow()) binding.onViewAttachedToWindow(root);
    }
    private static final class Binding extends RecyclerView.OnScrollListener implements View.OnAttachStateChangeListener,
            RecyclerView.OnChildAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnPreDrawListener {
        final View root;
        final Runnable paint;
        RecyclerView list;
        boolean observing;
        HeaderFade toolbarFade;
        int topInset=-1;
        int toolbarTone;
        final int[] location=new int[2];
        final WeakHashMap<View,Drawable> floatingSurfaces=new WeakHashMap<>();
        Binding(View root) {
            this.root = root;
            paint = () -> {
                boolean dark = SquarePalette.isDark();
                foreground(root.findViewById(0x7f0b06ef), SquarePalette.ink(dark));
                foreground(root.findViewById(0x7f0b0302), SquarePalette.ink(dark));
                playlistPaint(root);
                artistPaint(root);
                View play=root.findViewById(FIRST);
                if(play!=null && play.getParent() instanceof View) {
                    View header=(View)play.getParent();
                    controls(header,FIRST,SECOND);
                    actionRail(header,FIRST,0x7f0b02c6,0x7f0b0370,0x7f0b0274,0x7f0b064c);
                }
                topEdge();
            };
        }
        private static void artistPaint(View root) {
            boolean dark = SquarePalette.isDark();
            int ink = SquarePalette.ink(dark);
            View artistImage = root.findViewById(0x7f0b00a9);
            if (artistImage == null) artistImage = root.findViewById(0x7f0b00b8);
            if (artistImage == null) artistImage = root.findViewById(0x7f0b03d3);
            if (artistImage != null) {
                artistImage.setForeground(new CoverFade(SquarePalette.base(dark)));
            }
            for (int id : new int[]{0x7f0b0324, 0x7f0b0569, 0x7f0b064c, 0x7f0b0108, 0x7f0b0109, 0x7f0b00e3, 0x7f0b00e4}) {
                View v = root.findViewById(id);
                if (v != null) {
                    if (v.isClickable()) button(v, false);
                    else foreground(v, ink);
                }
            }
        }
        private void topEdge() {
            View header=root.findViewById(0x7f0b057a);
            View image=header==null?root.findViewById(0x7f0b00a9):header.findViewById(ARTWORK);
            if(image==null)image=root.findViewById(ARTWORK);
            if(image==null)return;
            if(header==null && image.getParent() instanceof View) header=(View)image.getParent();
            if(header==null)return;
            if(list!=null && ((View)list).getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams lp=(ConstraintLayout.LayoutParams)((View)list).getLayoutParams();
                if(lp.topToTop!=0 || lp.topToBottom!=-1 || ((ViewGroup.MarginLayoutParams)lp).topMargin!=0) {
                    lp.topToTop=0;lp.topToBottom=-1;((ViewGroup.MarginLayoutParams)lp).topMargin=0;((View)list).setLayoutParams(lp);
                }
            }
            header.setPadding(header.getPaddingLeft(),0,header.getPaddingRight(),header.getPaddingBottom());
            if (image.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams p=(ConstraintLayout.LayoutParams)image.getLayoutParams();
                ViewGroup.MarginLayoutParams margins=p;
                if (margins.topMargin != 0 || p.topToBottom != -1 || p.verticalBias != 0) {
                    margins.topMargin=0; p.topToTop=0; p.topToBottom=-1; p.verticalBias=0;
                    image.setLayoutParams(p);
                }
            }
            // Use layout geometry without transient navigation translations.
            android.view.WindowInsets insets=root.getRootWindowInsets();
            int status=insets==null?0:insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
            PageInsets.top(root,status);
            int top=status;
            View toolbar=root.findViewById(0x7f0b0302);if(toolbar==null)toolbar=root.findViewById(0x7f0b06ef);
            if (toolbar != null) {
                PageCoordinator.toolbarInset(toolbar,top);toolbar.setElevation(dp(root,4));
                int tone=SquarePalette.surface(SquarePalette.isDark());
                if (toolbarTone != tone) { toolbarTone=tone; floatingSurfaces.clear(); }
                if (toolbar instanceof ViewGroup) {
                    Drawable pagePaint=header.getBackground();
                    int pageTone=pagePaint instanceof android.graphics.drawable.ColorDrawable ? ((android.graphics.drawable.ColorDrawable)pagePaint).getColor() : tone;
                    float distance=list==null?0:list.computeVerticalScrollOffset();
                    // Ignore window/page transition transforms; only native scroll
                    // and the local collapsing app bar contribute to the scrim.
                    for(View v=header;v.getParent() instanceof View && v!=root;v=(View)v.getParent())
                        if(v.getClass().getName().equals("com.google.android.material.appbar.AppBarLayout"))distance+=Math.max(0,-v.getTop());
                    if(list!=null && !((View)list).canScrollVertically(-1) && distance<=0) distance=0;
                    if(toolbarFade==null) toolbarFade=new HeaderFade(pageTone);
                    toolbarFade.update(pageTone,HeaderProgress.opacity(distance,dp(root,64)));
                    if(toolbar.getBackground()!=toolbarFade) toolbar.setBackground(toolbarFade);
                    ViewGroup group=(ViewGroup)toolbar;
                    int edge=dp(toolbar,12);
                    if(toolbar.getPaddingStart()!=edge || toolbar.getPaddingEnd()!=edge)
                        toolbar.setPaddingRelative(edge,toolbar.getPaddingTop(),edge,toolbar.getPaddingBottom());
                    for (int i=0;i<group.getChildCount();i++) {
                        View child=group.getChildAt(i);
                        if (child instanceof android.widget.ImageButton) floating(child,tone);
                        else if (child instanceof ViewGroup && child.getClass().getName().contains("ActionMenuView")) {
                            ViewGroup menu=(ViewGroup)child;
                            for (int j=0;j<menu.getChildCount();j++) floating(menu.getChildAt(j),tone);
                        }
                    }
                }
            }
        }
        private void floating(View view, int tone) {
            if (floatingSurfaces.containsKey(view) && floatingSurfaces.get(view) == view.getBackground()) return;
            int target = dp(view, 48);
            if (view.getMinimumWidth() != target) view.setMinimumWidth(target);
            if (view.getMinimumHeight() != target) view.setMinimumHeight(target);
            ViewGroup.LayoutParams p = view.getLayoutParams();
            if (p != null && (p.width != target || p.height != target)) {
                p.width = target; p.height = target; view.setLayoutParams(p);
            }
            int padding = dp(view, 12);
            if (view instanceof ImageView) {
                if (view.getPaddingLeft() != padding || view.getPaddingTop() != padding)
                    view.setPadding(padding, padding, padding, padding);
                if (((ImageView)view).getScaleType() != ImageView.ScaleType.FIT_CENTER)
                    ((ImageView)view).setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
            if (view.getBackgroundTintList() != null) view.setBackgroundTintList(null);
            Drawable ripple = new android.graphics.drawable.InsetDrawable(PageMaterial.background(view, tone, 20), dp(view, 4));
            view.setBackground(ripple);
            floatingSurfaces.put(view, ripple);
        }
        private int lastDistance = -1;
        private int lastTop = -1;
        @Override public boolean onPreDraw() {
            int top = 0;
            android.view.WindowInsets insets = root.getRootWindowInsets();
            if (insets != null) top = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
            int distance = list == null ? 0 : list.computeVerticalScrollOffset();
            int tone = SquarePalette.surface(SquarePalette.isDark());
            if (distance != lastDistance || top != lastTop || tone != toolbarTone) {
                lastDistance = distance;
                lastTop = top;
                topEdge();
            }
            return true;
        }
        @Override public void onViewAttachedToWindow(View view) {
            if(observing)return;
            View candidate = root.findViewById(0x7f0b05b8);
            if (candidate instanceof RecyclerView && list == null) {
                list = (RecyclerView)candidate; list.addOnScrollListener(this);
                list.addOnChildAttachStateChangeListener(this);
                ViewGroup children = list;
                for (int i = 0; i < children.getChildCount(); i++) onChildViewAttachedToWindow(children.getChildAt(i));
            }
            if (!observing) {
                root.getViewTreeObserver().addOnGlobalLayoutListener(this);
                root.getViewTreeObserver().addOnPreDrawListener(this);
                observing = true;
            }
            paint.run();
        }
        @Override public void onGlobalLayout() { paint.run(); }
        @Override public void onChildViewAttachedToWindow(View child) {
            child.post(() -> { if (child.isAttachedToWindow()) {track(child);if(child.findViewById(FIRST)==null && child.findViewById(0x7f0b0568)==null)labels(child);} });
        }
        @Override public void onChildViewDetachedFromWindow(View child) {}
        @Override public void onScrolled(RecyclerView view, int dx, int dy) {
            root.removeCallbacks(paint); root.post(paint);
        }
        @Override public void onViewDetachedFromWindow(View view) {
            root.removeCallbacks(paint);
            if (list != null) {
                list.removeOnScrollListener(this); list.removeOnChildAttachStateChangeListener(this); list = null;
            }
            if (observing && root.getViewTreeObserver().isAlive()) {
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                root.getViewTreeObserver().removeOnPreDrawListener(this);
            }
            observing = false;
            // Retain geometry and surfaces across native exit/return transitions.
        }
    }
}
