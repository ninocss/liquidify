/* Square Glass for TIDAL. GPL-3.0-or-later. */
package dev.nino.squareglass;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.*;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/** Projects the native list; every button still uses a natively bound row callback. */
@SuppressWarnings({"rawtypes", "unchecked"})
final class SquareSongMenu extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    final RecyclerView.Adapter source;
    final RecyclerView list;
    final View listView;
    final LinearLayout layout;
    final GridLayout quick;
    final ScrollView footer;
    final LiquidMenuSurface glass;
    final View panel;
    final Runnable fit = this::fit;
    final View.OnLayoutChangeListener fitListener = (v,l,t,r,b,ol,ot,or,ob) -> {
        v.removeCallbacks(fit); v.post(fit);
    };
    boolean released;
    final ArrayList<Integer> mapping = new ArrayList<>();
    final RecyclerView.AdapterDataObserver observer = new RecyclerView.AdapterDataObserver() {
        @Override public void onChanged() { schedule(); }
        @Override public void onItemRangeInserted(int a, int b) { schedule(); }
        @Override public void onItemRangeRemoved(int a, int b) { schedule(); }
        @Override public void onItemRangeChanged(int a, int b) { schedule(); }
        @Override public void onItemRangeMoved(int a, int b, int c) { schedule(); }
    };
    final Runnable rebuild = this::rebuild;
    static int kind(Object action) {
        switch (action.getClass().getName()) {
            case "m3.i": return 1;
            case "m3.f": return 2;
            case "m3.a": case "m3.o": return 3;
            case "m3.d": return 4;
            default: return 0;
        }
    }
    static int group(Object action) {
        switch (action.getClass().getName()) {
            case "m3.h0": return 0;
            case "m3.y": case "m3.b0": case "m3.e0": return 1;
            case "m3.p": case "m3.s": case "m3.v": return 3;
            default: return 2;
        }
    }
    static Object model(RecyclerView.Adapter adapter, int index) {
        return adapter instanceof ListAdapter ? ((ListAdapter)adapter).getCurrentList().get(index) : null;
    }
    static boolean perform(RecyclerView.Adapter adapter, ViewGroup parent, int requested) {
        for (int i = 0; i < adapter.getItemCount(); i++) {
            Object model = model(adapter, i);
            if (model != null && kind(model) == requested && ((v40.a)model).a()) {
                RecyclerView.ViewHolder holder = adapter.createViewHolder(parent, adapter.getItemViewType(i));
                adapter.bindViewHolder(holder, i);
                return holder.itemView.performClick();
            }
        }
        return false;
    }
    static SquareSongMenu install(View root, RecyclerView.Adapter source, LiquidMenuSurface glass, boolean playerMenu) {
        if (!(source instanceof ListAdapter)) return null;
        boolean song = false;
        for (int i=0; i<source.getItemCount(); i++) {
            Object model = model(source, i);
            if (model != null && model.getClass().getName().startsWith("m3.")) { song = true; break; }
        }
        View candidate = root.findViewById(0x7f0b05b8);
        if ((!song && source.getItemCount() != 0) || !(candidate instanceof RecyclerView) || !(candidate.getParent() instanceof ViewGroup)) return null;
        return new SquareSongMenu((RecyclerView)candidate, source, glass, playerMenu);
    }
    private int dp(int value) { return Math.round(value * listView.getResources().getDisplayMetrics().density); }
    private SquareSongMenu(RecyclerView list, RecyclerView.Adapter source, LiquidMenuSurface glass, boolean playerMenu) {
        this.source = source; this.list = list; this.listView = list; this.glass = glass;
        ViewGroup parent = (ViewGroup)((View)list).getParent(); int index = parent.indexOfChild(list);
        panel = parent;
        ViewGroup.LayoutParams original = listView.getLayoutParams(); parent.removeView(list);
        layout = new LinearLayout(listView.getContext()); layout.setOrientation(LinearLayout.VERTICAL);
        // Keep the native list's header-relative placement on its replacement container.
        parent.addView(layout, index, original);
        list.setNestedScrollingEnabled(true);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(16));
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        if(playerMenu) {
            View timer=SleepTimer.button(listView.getContext(),glass);
            LinearLayout.LayoutParams timerParams=new LinearLayout.LayoutParams(-1,dp(56));
            timerParams.setMargins(dp(12),dp(6),dp(12),dp(4)); layout.addView(timer,timerParams);
        }
        quick = new GridLayout(listView.getContext()); quick.setPadding(dp(12), dp(10), dp(12), dp(16));
        quick.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).bottom;
            view.setPadding(dp(12), dp(10), dp(12), Math.max(dp(16), bottom));
            return insets;
        });
        footer = new ScrollView(listView.getContext()) {
            @Override protected void onMeasure(int width, int height) {
                int cap = dp(listView.getResources().getConfiguration().screenHeightDp) * 2/5;
                if (View.MeasureSpec.getMode(height) != View.MeasureSpec.UNSPECIFIED)
                    cap = Math.min(cap, View.MeasureSpec.getSize(height));
                super.onMeasure(width, View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.AT_MOST));
            }
        };
        footer.setFillViewport(false); footer.setClipToPadding(false);
        footer.addView(quick,new ScrollView.LayoutParams(-1,-2));
        layout.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        panel.addOnLayoutChangeListener(fitListener);
        source.registerAdapterDataObserver(observer); rebuild(); list.setAdapter(this);
    }
    private void fit() {
        if (released || panel.getHeight() == 0) return;
        android.graphics.Rect frame = new android.graphics.Rect();
        int[] position = new int[2]; layout.getLocationOnScreen(position);
        panel.getWindowVisibleDisplayFrame(frame);
        int available = Math.min(panel.getHeight()-layout.getTop()-panel.getPaddingBottom(), frame.bottom-position[1]);
        ViewGroup.LayoutParams params = layout.getLayoutParams();
        if (available >= dp(96) && params.height > available) {
            params.height = available; layout.setLayoutParams(params);
        }
    }
    private void schedule() { if (!released) { listView.removeCallbacks(rebuild); listView.post(rebuild); } }
    private static CharSequence label(View view) {
        if (view instanceof TextView && ((TextView)view).length() > 0) return ((TextView)view).getText();
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup)view;
            for (int i=0; i<parent.getChildCount(); i++) { CharSequence found = label(parent.getChildAt(i)); if (found.length()>0) return found; }
        }
        return "";
    }
    private void rebuild() {
        if (released) return;
        mapping.clear(); glass.clearWithin(quick); quick.removeAllViews();
        int[] shortcuts = {-1,-1,-1,-1};
        for (int i=0; i<source.getItemCount(); i++) {
            Object model = model(source,i); int kind = model == null ? 0 : kind(model);
            if (kind > 0 && shortcuts[kind-1] < 0) shortcuts[kind-1] = i;
        }
        for (int group=0; group<4; group++) {
            boolean heading = false;
            for (int i=0; i<source.getItemCount(); i++) {
                Object model = model(source,i);
                boolean extracted = false; for (int shortcut : shortcuts) if (i==shortcut) extracted=true;
                if (extracted || model == null || group(model) != group) continue;
                if (!heading) { mapping.add(-group-1); heading=true; }
                mapping.add(i);
            }
        }
        float scale = listView.getResources().getConfiguration().fontScale;
        int availableWidth = listView.getResources().getConfiguration().screenWidthDp - 36;
        int columns = availableWidth >= 4 * 140 * scale ? 4 : availableWidth >= 2 * 128 * scale ? 2 : 1;
        int shortcutCount = 0; for (int position : shortcuts) if (position >= 0) shortcutCount++;
        columns = Math.max(1, Math.min(columns, shortcutCount));
        quick.setColumnCount(columns);
        for (int position : shortcuts) {
            if (position < 0) continue;
            v40.a model = (v40.a)model(source,position);
            RecyclerView.ViewHolder holder = source.createViewHolder(layout, source.getItemViewType(position));
            source.bindViewHolder(holder,position);
            FrameLayout button = new FrameLayout(listView.getContext()) {
                @Override public CharSequence getAccessibilityClassName() { return Button.class.getName(); }
            };
            glass.paint(button, 22f, true);
            int ink = SquarePalette.ink(SquarePalette.isDark());
            GradientDrawable mask = new GradientDrawable(); mask.setColor(0xffffffff); mask.setCornerRadius(dp(22));
            button.setForeground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf((ink & 0xffffff) | 0x20000000),null,mask));
            LinearLayout content = new LinearLayout(listView.getContext()); content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER); content.setPadding(dp(10),dp(14),dp(10),dp(14));
            content.setMinimumHeight(dp(88));
            content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            ImageView icon = new ImageView(listView.getContext()); icon.setImageResource(model.b);
            icon.setImageTintList(ColorStateList.valueOf(ink));
            content.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));
            CharSequence description = label(holder.itemView);
            TextView title = new TextView(listView.getContext()); title.setText(description);
            title.setTextColor(ink); title.setTextSize(13); title.setGravity(Gravity.CENTER);
            title.setTypeface(listView.getResources().getFont(0x7f090005));
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1,-2); titleParams.topMargin=dp(8);
            content.addView(title,titleParams);
            button.addView(content,new FrameLayout.LayoutParams(-1,-2));
            button.setContentDescription(description); button.setTooltipText(description);
            button.setFocusable(true); button.setEnabled(model.a()); content.setAlpha(model.a() ? 1f : .45f);
            button.setOnClickListener(v -> holder.itemView.performClick());
            button.setOnTouchListener((view,event) -> {
                int action=event.getActionMasked();
                if(action==android.view.MotionEvent.ACTION_DOWN || action==android.view.MotionEvent.ACTION_UP ||
                        action==android.view.MotionEvent.ACTION_CANCEL)
                    LiquidViewMotion.press(view,action==android.view.MotionEvent.ACTION_DOWN);
                return false;
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = 0; params.height = -2;
            int cell = quick.getChildCount();
            int span = cell == shortcutCount-1 && shortcutCount % columns == 1 ? columns : 1;
            params.columnSpec = GridLayout.spec(cell % columns, span, GridLayout.FILL, 1f);
            params.rowSpec = GridLayout.spec(cell / columns, GridLayout.FILL);
            params.setMargins(dp(5),dp(5),dp(5),dp(5)); quick.addView(button,params);
        }
        footer.setVisibility(quick.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        // Compose backgrounds must first attach to their window before measure.
        // Reserve footer space here; its ScrollView measures real label wrapping
        // after attach and caps itself for large text / short landscape windows.
        int rows = (quick.getChildCount()+columns-1)/columns;
        int footerHeight = Math.min(dp(listView.getResources().getConfiguration().screenHeightDp) * 2/5,
                dp(Math.round(rows*(98+20*Math.max(0,scale-1))+26)));
        ViewGroup.LayoutParams params = layout.getLayoutParams();
        params.height = Math.min(dp(listView.getResources().getConfiguration().screenHeightDp) * 2/3,
            dp(Math.max(144, Math.round(mapping.size()*48*scale))) + footerHeight);
        layout.setLayoutParams(params); notifyDataSetChanged();
    }
    void release() {
        released = true; listView.removeCallbacks(rebuild); panel.removeCallbacks(fit);
        panel.removeOnLayoutChangeListener(fitListener); source.unregisterAdapterDataObserver(observer);
    }
    @Override public int getItemCount() { return mapping.size(); }
    @Override public int getItemViewType(int position) {
        int original = mapping.get(position); return original < 0 ? original : source.getItemViewType(original);
    }
    @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
        if (type < 0) {
            TextView heading = new TextView(parent.getContext()); heading.setTextSize(13);
            heading.setTextColor(SquarePalette.isDark() ? 0xffb4b8c4 : 0xff5d626d);
            heading.setPadding(dp(20),dp(20),dp(20),dp(8));
            heading.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            heading.setAccessibilityHeading(true); return new RecyclerView.ViewHolder(heading) {};
        }
        return new NativeHolder(source.createViewHolder(parent,type));
    }
    @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int original = mapping.get(position);
        if (original < 0) ((TextView)holder.itemView).setText(new String[]{"Playback", "Discover", "More options", "Manage"}[-original-1]);
        else source.bindViewHolder(((NativeHolder)holder).nativeHolder,original);
    }
    @Override public void onViewRecycled(RecyclerView.ViewHolder holder) {
        if (holder instanceof NativeHolder) source.onViewRecycled(((NativeHolder)holder).nativeHolder);
    }
    private static final class NativeHolder extends RecyclerView.ViewHolder {
        final RecyclerView.ViewHolder nativeHolder;
        NativeHolder(RecyclerView.ViewHolder holder) { super(holder.itemView); nativeHolder=holder; }
    }
}
