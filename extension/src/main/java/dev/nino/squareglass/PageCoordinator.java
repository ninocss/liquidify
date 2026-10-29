/* GPL-3.0-or-later. Native page layouts and adapters remain owned by TIDAL. */
package dev.nino.squareglass;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.*;
import androidx.recyclerview.widget.*;
import androidx.constraintlayout.widget.ConstraintLayout;
import java.lang.ref.WeakReference;
import java.util.*;

public final class PageCoordinator {
    private static final WeakHashMap<Activity,Boolean> installed=new WeakHashMap<>();
    private static final WeakHashMap<View,WeakReference<Page>> pages=new WeakHashMap<>();
    private static final WeakHashMap<Fragment,HeroSnapshot> artwork=new WeakHashMap<>();
    private static final class HeroSnapshot {
        final ArrayList<Drawable> covers=new ArrayList<>();
        final ArrayList<Integer> seeds=new ArrayList<>();
    }
    private static WeakReference<View> lastClick=new WeakReference<>(null);
    private PageCoordinator() {}
    public static View lastClick() { return lastClick.get(); }
    public static void touch(Activity activity,MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_UP)
            lastClick=new WeakReference<>(hit(activity.getWindow().getDecorView(),event.getRawX(),event.getRawY()));
    }
    static int layoutInset(View root,int status) {
        int top=0;
        for(View v=root;v.getParent() instanceof View;v=(View)v.getParent())top+=v.getTop()-((View)v.getParent()).getScrollY();
        return Math.max(0,Math.min(status,top));
    }
    static void toolbarInset(View toolbar,int status) {
        int origin=0;
        for(View v=toolbar;v.getParent() instanceof View;v=(View)v.getParent())
            origin+=v.getTop()-((View)v.getParent()).getScrollY();
        float target=Math.max(0,status-origin-toolbar.getPaddingTop());
        if(toolbar.getTranslationY()!=target)toolbar.setTranslationY(target);
    }
    private static View hit(View view,float x,float y) {
        if(!view.isShown()) return null;
        int[] xy=new int[2];view.getLocationOnScreen(xy);
        if(x<xy[0] || y<xy[1] || x>=xy[0]+view.getWidth() || y>=xy[1]+view.getHeight()) return null;
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=group.getChildCount()-1;i>=0;i--) { View found=hit(group.getChildAt(i),x,y);if(found!=null)return found; }
        }
        return view.isClickable()?view:null;
    }
    public static void install(Activity activity) {
        if(SquareSettings.themeEnabled()==0 || !(activity instanceof FragmentActivity) || installed.containsKey(activity)) return;
        installed.put(activity,true);
        android.util.Log.d("SquareGlass","Page lifecycle installed");
        FragmentManager manager=((FragmentActivity)activity).getSupportFragmentManager();
        manager.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override public void onFragmentPaused(FragmentManager fm,Fragment fragment) { NativeBackdrop.transition(); }
            @Override public void onFragmentViewCreated(FragmentManager fm,Fragment fragment,View view,Bundle state) { NativeBackdrop.transition();bind(fragment,view); }
            @Override public void onFragmentResumed(FragmentManager fm,Fragment fragment) {
                NativeBackdrop.transition();
                View view=fragment.getView();if(view!=null) bind(fragment,view);
            }
            @Override public void onFragmentViewDestroyed(FragmentManager fm,Fragment fragment) {
                NativeBackdrop.transition();
                View view=fragment.getView();WeakReference<Page> ref=pages.remove(view);
                if(ref!=null && ref.get()!=null) ref.get().destroy();
            }
        },true);
        existing(manager);
    }
    private static void existing(FragmentManager manager) {
        for(Fragment f:manager.getFragments()) {
            if(f.getView()!=null) bind(f,f.getView());
            if(f.isAdded()) existing(f.getChildFragmentManager());
        }
    }
    private static String category(String name) {
        if(name.endsWith("FavoriteTracksFragment"))return "My Tracks";
        if(name.endsWith("MyArtistsView"))return "Artists";
        if(name.endsWith("MyPlaylistsView"))return "Playlists";
        if(name.endsWith("MyAlbumsView"))return "Albums";
        if(name.endsWith("MyMixesAndRadioView") || name.equals("pe.p"))return "Mixes & Radio";
        if(name.contains("mycollection.subpages.downloaded.presentation.items."))return "Downloads";
        if(name.equals("com.tidal.android.feature.feed.ui.FeedView"))return "Updates";
        return null;
    }
    private static void bind(Fragment fragment,View root) {
        String name=fragment.getClass().getName();
        String title=category(name);
        boolean search=name.equals("com.aspiro.wamp.search.v2.UnifiedSearchView") || name.startsWith("a8.");
        boolean detail=name.equals("com.aspiro.wamp.dynamicpages.ui.albumpage.m") ||
            name.equals("com.aspiro.wamp.dynamicpages.ui.contributorpage.m") || name.equals("x7.b");
        if(title==null && !search && !detail)return;
        android.util.Log.d("SquareGlass","Bind native page "+name);
        WeakReference<Page> ref=pages.get(root);Page page=ref==null?null:ref.get();
        if(page==null) {
            HeroSnapshot snapshot=artwork.get(fragment);
            if(snapshot==null) {snapshot=new HeroSnapshot();artwork.put(fragment,snapshot);}
            page=new Page(root,title,search,detail,snapshot);pages.put(root,new WeakReference<>(page));
            root.addOnAttachStateChangeListener(page);
        }
        if(root.isAttachedToWindow()) page.onViewAttachedToWindow(root);
        if(detail) SquareDetails.install(root);
    }
    private static final class Page implements View.OnAttachStateChangeListener,ViewTreeObserver.OnPreDrawListener,
            RecyclerView.OnChildAttachStateChangeListener {
        final View root; final String title;final boolean search,detail;
        final HeroSnapshot snapshot;
        final ArrayList<RecyclerView> lists=new ArrayList<>();
        final WeakHashMap<View,Drawable> materials=new WeakHashMap<>();
        final int[] xy=new int[2];
        RecyclerView primary;HeroDecoration hero;View heroView, movingAction,chromeSurface;boolean watching,dark;int tone;
        ColorDrawable statusScrim;
        int inset=-1,chromeTone,chromeItems=-1;
        Page(View root,String title,boolean search,boolean detail,HeroSnapshot snapshot) { this.root=root;this.title=title;this.search=search;this.detail=detail;this.snapshot=snapshot; }
        int dp(float n) {return Math.round(n*root.getResources().getDisplayMetrics().density);}
        void scan(View view) {
            if(view instanceof RecyclerView) {
                RecyclerView list=(RecyclerView)view;
                boolean pager=((View)list).getParent()!=null && ((View)list).getParent().getClass().getName().contains("ViewPager2");
                if(!pager && !lists.contains(list)) {
                    lists.add(list);list.addOnChildAttachStateChangeListener(this);
                    if(title!=null && primary==null) {
                        primary=list;
                        hero=new HeroDecoration(this);configureHero();
                        list.setClipToPadding(false);
                    }
                    for(int i=0;i<((ViewGroup)list).getChildCount();i++) onChildViewAttachedToWindow(((ViewGroup)list).getChildAt(i));
                }
            }
            if(view instanceof ViewGroup) {
                ViewGroup group=(ViewGroup)view;
                for(int i=0;i<group.getChildCount();i++) scan(group.getChildAt(i));
            }
            if(view instanceof androidx.appcompat.widget.Toolbar) SquareDetails.chrome(view);
            if(search && view instanceof ImageView && (view==findNamed(root,"backIcon") || view==findNamed(root,"searchIcon")))
                ((ImageView)view).setImageTintList(android.content.res.ColorStateList.valueOf(SquarePalette.ink(SquarePalette.isDark())));
            if(view instanceof TextView) {
                TextView text=(TextView)view;int old=text.getCurrentTextColor();
                if((old&0xffffff)==0xffffff || (old&0xffffff)==0 || old==0xff101010)text.setTextColor(SquarePalette.ink(SquarePalette.isDark()));
            }
            if(search) searchMaterial(view);
        }
        void configureHero() {
            View nativeHeader=root.findViewById(0x7f0b0334);
            if(nativeHeader instanceof ConstraintLayout && nativeHeader.findViewById(0x7f0b0568)!=null) {
                ConstraintLayout header=(ConstraintLayout)nativeHeader;
                heroView=new HeroView(root,hero);heroView.setId(View.generateViewId());
                int height=root.getResources().getDisplayMetrics().widthPixels+dp(72);
                for(int i=0;i<((ViewGroup)header).getChildCount();i++) {
                    View child=((ViewGroup)header).getChildAt(i);
                    if(child.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
                        ConstraintLayout.LayoutParams q=(ConstraintLayout.LayoutParams)child.getLayoutParams();
                        if(q.topToTop==0) {q.topToTop=-1;q.topToBottom=heroView.getId();child.setLayoutParams(q);}
                    }
                }
                ConstraintLayout.LayoutParams q=new ConstraintLayout.LayoutParams(0,height);
                q.topToTop=0;q.startToStart=0;q.endToEnd=0;((ViewGroup)header).addView(heroView,0,q);
                ViewGroup.LayoutParams size=((View)header).getLayoutParams();size.height=-2;
                if(size instanceof ViewGroup.MarginLayoutParams)((ViewGroup.MarginLayoutParams)size).topMargin=0;
                ((View)header).setLayoutParams(size);
                if(((View)header).getParent() instanceof View) {
                    View container=(View)((View)header).getParent();ViewGroup.LayoutParams lp=container.getLayoutParams();
                    lp.height=-2;container.setLayoutParams(lp);
                }
                View bar=root.findViewById(0x7f0b06f0);
                if(bar!=null && bar.getParent() instanceof ViewGroup && root instanceof ViewGroup) {
                    ViewGroup old=(ViewGroup)bar.getParent();old.removeView(bar);((View)old).setMinimumHeight(0);
                    if(old.getParent() instanceof View) {((View)old.getParent()).setMinimumHeight(0);((View)old.getParent()).setElevation(0);}
                    NativeArtworkBridge.continuousScroll(old);
                    FrameLayout shell=new FrameLayout(root.getContext());
                    int top=root.getRootWindowInsets()==null?0:root.getRootWindowInsets().getInsets(WindowInsets.Type.statusBars()).top;
                    shell.setPadding(0,top,0,0);shell.addView(bar,new FrameLayout.LayoutParams(-1,dp(56)));
                    ((ViewGroup)root).addView(shell,new ViewGroup.LayoutParams(-1,top+dp(56)));
                    shell.setElevation(dp(4));bar.setBackgroundColor(Color.TRANSPARENT);chromeSurface=shell;
                }
                collectionControls();
            } else {
                primary.addItemDecoration(hero);
                if(((View)primary).getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
                    ConstraintLayout.LayoutParams q=(ConstraintLayout.LayoutParams)((View)primary).getLayoutParams();
                    q.topToTop=0;q.topToBottom=-1;((ViewGroup.MarginLayoutParams)q).topMargin=0;
                    ((View)primary).setLayoutParams(q);
                    movingAction=findNamed(root,"createButton");
                    if(movingAction!=null) {movingAction.setElevation(dp(2));SquareDetails.control(movingAction,true);}
                }
            }
        }
        View findNamed(View view,String entry) {
            if(view.getId()!=View.NO_ID)try {if(view.getResources().getResourceEntryName(view.getId()).equals(entry))return view;}catch(android.content.res.Resources.NotFoundException ignored){}
            if(view instanceof ViewGroup) {ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findNamed(group.getChildAt(i),entry);if(found!=null)return found;}}
            return null;
        }
        void collectionControls() {
            View header=root.findViewById(0x7f0b0334);
            if(header==null)return;
            SquareDetails.controls(header,0x7f0b0568,0x7f0b065c);
            if("My Tracks".equals(title)) {
                // Collapse the stock divider below the action rail; INVISIBLE
                // retained its height and left a stray line-sized gap.
                View divider=header.findViewById(0x7f0b026b);
                if(divider!=null)divider.setVisibility(View.GONE);
                // The header is one continuous surface. Keep only the native
                // controls' ripple/glass paints and the offline switch itself.
                if(header.getBackground()!=null)header.setBackground(null);
                for(View v=header;v!=root && v.getParent() instanceof View;v=(View)v.getParent()) {
                    // AppBarLayout's state animator restores an elevation after
                    // scrolling, which outlines the entire controls header.
                    if(v.getClass().getName().equals("com.google.android.material.appbar.AppBarLayout")) {
                        v.setStateListAnimator(null);v.setElevation(0);v.setTranslationZ(0);
                    }
                }
                View offline=header.findViewById(0x7f0b04c6);
                View label=header.findViewById(0x7f0b027a);
                if(label instanceof TextView)((TextView)label).setTextColor(SquarePalette.secondary(SquarePalette.isDark()));
                if(offline!=null && offline.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
                    ConstraintLayout.LayoutParams p=(ConstraintLayout.LayoutParams)offline.getLayoutParams();
                    ViewGroup.MarginLayoutParams margins=p;
                    int gap=dp(16);
                    if(p.bottomToTop!=-1 || p.bottomToBottom!=-1 || margins.bottomMargin!=gap) {
                        p.bottomToTop=-1;p.bottomToBottom=-1;margins.bottomMargin=gap;
                        offline.setLayoutParams(p);
                    }
                }
            }
        }
        void topEdge() {
            if(inset<0) {WindowInsets insets=root.getRootWindowInsets();inset=insets==null?0:insets.getInsets(WindowInsets.Type.statusBars()).top;}
            if(title!=null && !title.equals("Downloads")) {
                PageInsets.top(root,inset);
            }
            if(movingAction!=null) {
                View first=primary.getLayoutManager()==null?null:primary.getLayoutManager().findViewByPosition(0);
                float top=first==null?-hero.height(primary):first.getTop()-hero.height(primary);
                movingAction.setY(top+((View)primary).getWidth()+dp(60));
            }
        }
        void searchMaterial(View view) {
            String name=view.getClass().getName();
            boolean field=view instanceof androidx.appcompat.widget.SearchView,chip=name.contains("Chip"),button=view instanceof Button;
            if(!(field || chip || button)) return;
            if(field) {SquareExplore.search(view);return;}
            if(materials.containsKey(view) && view.getBackground()==materials.get(view))return;
            Drawable paint=PageMaterial.background(view,tone,field?28:20);
            view.setBackground(paint);materials.put(view,paint);
            if(field || button)PageGlass.install(view,field?28:20);
            if(view instanceof TextView) {
                TextView text=(TextView)view;text.setTextColor(SquarePalette.ink(dark));
                text.setHintTextColor(SquarePalette.secondary(dark));
            }
        }
        void row(View row) {
            SquareDetails.track(row);
            if("My Tracks".equals(title))SquareDetails.collectionTrack(row);
            // Native context actions identify actionable media; never synthesize actions for category cards.
            if(row.isLongClickable() && row.hasOnClickListeners()) SquareGestures.bind(row);
            if(search && row.isClickable() && row.getHeight()>=dp(28)) {
                if(PageGlass.hasSurface(row)) {if(hero!=null)hero.collect(row);return;}
                Drawable current=materials.get(row);
                if(current==null || row.getBackground()!=current) {
                    Drawable paint=PageMaterial.background(row,tone,16);row.setBackground(paint);materials.put(row,paint);PageGlass.install(row,16);
                }
            }
            if(hero!=null) hero.collect(row);
        }
        @Override public boolean onPreDraw() {
            if(!root.isShown())return true;
            if(detail)return true;
            if(title!=null)collectionControls();
            boolean now=SquarePalette.isDark();
            if(now!=dark || tone==0) {
                dark=now;tone=hero==null?SquarePalette.base(dark):hero.surface(dark);materials.clear();scan(root);root.setBackgroundColor(tone);
                if(primary!=null)((View)primary).setBackgroundColor(tone);
            }
            if(hero!=null) {
                for(int i=0;i<((ViewGroup)primary).getChildCount();i++) hero.collect(((ViewGroup)primary).getChildAt(i));
                int color=hero.surface(dark);
                if(tone!=color) {tone=color;root.setBackgroundColor(tone);((View)primary).setBackgroundColor(tone);}
                topEdge();header();
            }
            if(title!=null && hero==null) header();
            if(title!=null && root.getRootView()!=root) {
                root.getRootView().setBackgroundColor(SquarePalette.base(dark));
            }
            return true;
        }
        void header() {
            View bar=root.findViewById(0x7f0b06ef);if(bar==null)bar=root.findViewById(0x7f0b06f0);if(bar==null)bar=root.findViewById(0x7f0b0302);
            if(bar==null)return;
            if (("Playlists".equals(title) || "Albums".equals(title)) && chromeSurface==null && inset>0) {
                if(statusScrim==null) { statusScrim=new ColorDrawable(); root.getOverlay().add(statusScrim); }
                statusScrim.setColor((tone & 0x00ffffff) | 0xe0000000);
                root.getLocationOnScreen(xy);
                statusScrim.setBounds(0,0,root.getWidth(),Math.max(0,inset-xy[1]));
            }
            bar.setElevation(dp(4));if(chromeSurface==null && inset>=0 && !title.equals("Downloads"))toolbarInset(bar,inset);
            View scrim=chromeSurface==null?bar:chromeSurface;
            HeaderFade fade=scrim.getBackground() instanceof HeaderFade?(HeaderFade)scrim.getBackground():new HeaderFade(tone);
            if(bar instanceof androidx.appcompat.widget.Toolbar) {
                androidx.appcompat.widget.Toolbar toolbar=(androidx.appcompat.widget.Toolbar)bar;
                int ink=SquarePalette.ink(dark),count=toolbar.getMenu().size();
                if(chromeTone!=ink || chromeItems!=count) {chromeTone=ink;chromeItems=count;SquareDetails.chrome(bar);}
            }
            int offset=((View)primary).canScrollVertically(-1)?primary.computeVerticalScrollOffset():0;
            if(heroView!=null)for(View v=heroView;v.getParent() instanceof View && v!=root;v=(View)v.getParent())
                if(v.getClass().getName().equals("com.google.android.material.appbar.AppBarLayout"))offset+=Math.max(0,-v.getTop());
            fade.update(tone,Math.max(.88f,HeaderProgress.opacity(offset,dp(64))));if(scrim.getBackground()!=fade)scrim.setBackground(fade);
        }
        @Override public void onViewAttachedToWindow(View view) {
            if(!watching) {
                boolean now=SquarePalette.isDark();
                if(now!=dark) {tone=0;materials.clear();}
                dark=now;
                if(tone==0)tone=hero==null?SquarePalette.base(dark):hero.surface(dark);
                if(!detail)root.setBackgroundColor(tone);
                root.getViewTreeObserver().addOnPreDrawListener(this);watching=true;scan(root);
                onPreDraw();
            }
        }
        @Override public void onViewDetachedFromWindow(View view) {
            if(watching && root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnPreDrawListener(this);
            watching=false;
            for(RecyclerView list:lists)list.removeOnChildAttachStateChangeListener(this);
            lists.clear(); // Decoration stays with this native list across reattachment.
        }
        void destroy() {onViewDetachedFromWindow(root);if(primary!=null && hero!=null)primary.removeItemDecoration(hero);root.removeOnAttachStateChangeListener(this);}
        @Override public void onChildViewAttachedToWindow(View child) {child.post(()->{if(child.isAttachedToWindow()){row(child);if(search || title!=null)scan(child);}});}
        @Override public void onChildViewDetachedFromWindow(View child) {}
    }
    private static final class HeroView extends View {
        final HeroDecoration hero;
        HeroView(View root,HeroDecoration hero) {super(root.getContext());this.hero=hero;((View)this).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas) {hero.draw(canvas,((View)this).getWidth(),0,((View)this).getHeight());}
    }
    private static final class HeroDecoration extends RecyclerView.ItemDecoration {
        final Page page;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final ArrayList<Drawable> covers=new ArrayList<>();final ArrayList<Integer> seeds=new ArrayList<>();
        HeroDecoration(Page page) {
            this.page=page;seeds.addAll(page.snapshot.seeds);
            for(Drawable source:page.snapshot.covers) covers.add(source.getConstantState().newDrawable().mutate());
        }
        int height(RecyclerView list) {
            int width=((View)list).getWidth();
            if(width<=0)width=((View)list).getMeasuredWidth();
            if(width<=0)width=page.root.getResources().getDisplayMetrics().widthPixels;
            return width+page.dp(page.movingAction==null?88:152);
        }
        @Override public void getItemOffsets(Rect out,View view,RecyclerView parent,RecyclerView.State state) {
            if(parent.getChildAdapterPosition(view)==0)out.top=height(parent);
        }
        void collect(View view) {
            if(covers.size()>=4)return;
            if(view instanceof ImageView && (view.getId()==0x7f0b00b8 || view.getId()==0x7f0b03d3)) {
                Drawable source=((ImageView)view).getDrawable();
                while(source instanceof coil.drawable.CrossfadeDrawable)source=((coil.drawable.CrossfadeDrawable)source).k;
                if(source instanceof BitmapDrawable && source.getIntrinsicWidth()>1 && source.getConstantState()!=null) {
                    int seed=SquareDetails.coverSeed(source);
                    if(!seeds.contains(seed)) {
                        Drawable copy=source.getConstantState().newDrawable().mutate();copy.setAlpha(255);covers.add(copy);seeds.add(seed);
                        page.snapshot.covers.add(copy.getConstantState().newDrawable().mutate());page.snapshot.seeds.add(seed);
                        if(page.primary!=null)((View)page.primary).invalidate();if(page.heroView!=null)page.heroView.invalidate();
                    }
                }
            }
            if(view instanceof ViewGroup) {ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i));}
        }
        int surface(boolean dark) {return seeds.isEmpty()?SquarePalette.base(dark):CoverColors.surface(seeds.get(0),dark);}
        @Override public void onDraw(Canvas canvas,RecyclerView parent,RecyclerView.State state) {
            View first=null;
            for(int i=0;i<((ViewGroup)parent).getChildCount();i++)if(parent.getChildAdapterPosition(((ViewGroup)parent).getChildAt(i))==0){first=((ViewGroup)parent).getChildAt(i);break;}
            if(first==null && state.getItemCount()>0)return;
            float top=first==null?0:first.getTop()-height(parent),width=((View)parent).getWidth();
            draw(canvas,width,top,height(parent));
        }
        void draw(Canvas canvas,float width,float top,float height) {
            canvas.save();canvas.translate(0,top);canvas.clipRect(0,0,width,height);
            paint.setShader(null);paint.setColor(surface(page.dark));canvas.drawRect(0,0,width,height,paint);
            if(covers.isEmpty()) {
                View placeholder=page.root.findViewById(0x7f0b0564);
                ImageView icon=placeholder==null?null:placeholder.findViewById(0x7f0b035d);
                Drawable nativeIcon=icon==null?null:icon.getDrawable();
                if(nativeIcon==null) {
                    int id=page.title.equals("Artists")?0x7f0801e1:page.title.equals("Albums")?0x7f08018e:
                        page.title.equals("Downloads")?0x7f080268:page.title.equals("Updates")?0x7f0803f5:
                        page.title.equals("Mixes & Radio")?0x7f08035c:page.title.equals("My Tracks")?0x7f080553:0x7f08045e;
                    nativeIcon=page.root.getContext().getDrawable(id);
                }
                if(nativeIcon!=null && nativeIcon.getConstantState()!=null) {
                    Drawable symbol=nativeIcon.getConstantState().newDrawable().mutate();symbol.setTint(SquarePalette.ink(page.dark));
                    int l=Math.round(width*.34f),r=Math.round(width*.66f);symbol.setBounds(l,l,r,r);symbol.draw(canvas);
                }
            } else {
                int count=covers.size();int cells=count==1?1:4;
                float size=cells==1?width:width/2;
                for(int i=0;i<cells;i++) {
                    Drawable art=covers.get(i%count);int x=Math.round((i%2)*size),y=Math.round((i/2)*size);
                    art.setBounds(x,y,Math.round(x+size),Math.round(y+size));art.draw(canvas);
                }
            }
            int rgb=surface(page.dark)&0xffffff;
            paint.setShader(new LinearGradient(0,0,0,width,new int[]{rgb|0x99000000,rgb,rgb,rgb|0x99000000,surface(page.dark)},new float[]{0,.2f,.55f,.85f,1},Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,width,width,paint);paint.setShader(null);
            paint.setColor(SquarePalette.ink(page.dark));paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            paint.setTextSize(page.dp(28));paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(page.title,width/2,width+page.dp(30),paint);canvas.restore();
        }
    }
}
