/* Square Glass for TIDAL. GPL-3.0-or-later. */
package dev.nino.squareglass;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Presents the native adapter in a small window; native callbacks own every action. */
public final class SquareMenus {
    private SquareMenus() {}
    private static final WeakHashMap<Dialog, Presentation> open = new WeakHashMap<>();
    private static WeakReference<Activity> touchedActivity = new WeakReference<>(null);
    private static float touchX, touchY;
    private static long touchedAt;
    private static boolean moved, completedTap;
    public static boolean hasOpenMenu() {return !open.isEmpty();}
    public static boolean owns(Dialog dialog) { return open.containsKey(dialog); }

    public static void touch(Activity activity, MotionEvent event) {
        IconMotion.touch(event);
        PageCoordinator.touch(activity,event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchedActivity = new WeakReference<>(activity);
            touchX = event.getRawX(); touchY = event.getRawY();
            touchedAt = SystemClock.uptimeMillis();
            moved = false; completedTap = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
            if (Math.abs(event.getRawX() - touchX) > slop || Math.abs(event.getRawY() - touchY) > slop) moved = true;
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            moved = true; completedTap = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            completedTap = !moved && SystemClock.uptimeMillis() - touchedAt < ViewConfiguration.getLongPressTimeout();
        }
    }

    public static void show(Dialog dialog, Activity activity, View root, RecyclerView.Adapter<?> adapter) {
        if (SquareSettings.themeEnabled() == 0) { dialog.show(); return; }
        Presentation previous = open.remove(dialog);
        if (previous != null) previous.release();
        for (Dialog other : new java.util.ArrayList<>(open.keySet())) other.dismiss();
        Presentation state = new Presentation(dialog, activity, root, adapter);
        open.put(dialog, state);
        state.start();
    }

    public static boolean sleepTimer(View source) {
        for(Presentation state:new java.util.ArrayList<>(open.values())) {
            if(!state.released && state.shell!=null && state.shell.getRootView()==source.getRootView()) {
                if(state.songMenu!=null) { state.songMenu.release(); state.songMenu=null; }
                state.glass.clearWithin(state.shell); state.shell.removeAllViews();
                state.shell.addView(SleepTimer.content(state.activity,state.glass,state.dialog::dismiss),new FrameLayout.LayoutParams(-1,-2));
                return true;
            }
        }
        return false;
    }

    /** Called by the original dialog's dismiss, including native item callbacks. */
    public static void dismissed(Dialog dialog) {
        Presentation state = open.remove(dialog);
        if (state != null) state.release();
    }

    public static boolean showing(Dialog dialog) {
        Presentation state = open.get(dialog);
        return dialog.isShowing() || (state != null && !state.released);
    }

    private static int dp(View view, float value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable surface(View view, int color) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(view, 22));
        result.setStroke(dp(view, 1), SquarePalette.isDark() ? 0x24ffffff : 0x1f15161a);
        return result;
    }

    private static void header(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView)view;
            text.setTextColor(SquarePalette.ink(SquarePalette.isDark()));
        } else if (view instanceof ImageView) {
            int r = Math.round(10 * view.getResources().getDisplayMetrics().density);
            view.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
                }
            });
            view.setClipToOutline(true);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            // Legacy headers explicitly paint black independently of their parent.
            group.setBackgroundColor(Color.TRANSPARENT);
            for (int i = 0; i < group.getChildCount(); i++) header(group.getChildAt(i));
        } else if (!(view instanceof ImageView) && view.getBackground() instanceof ColorDrawable) {
            view.setBackgroundColor((SquarePalette.ink(SquarePalette.isDark()) & 0xffffff) | 0x1f000000);
        }
    }

    /** Native model resources distinguish destructive actions from ordinary labels. */
    public static void row(View row, int textResource, int iconResource, boolean enabled) {
        if (SquareSettings.themeEnabled() == 0) return;
        int ink = SquarePalette.ink(SquarePalette.isDark());
        styleChildren(row, actionColor(row, textResource, ink), actionColor(row, iconResource, ink), enabled);
        row.setMinimumHeight(dp(row, 56));
        row.setPaddingRelative(dp(row, 16), dp(row, 8), dp(row, 16), dp(row, 8));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf((ink & 0xffffff) | 0x16000000), null,
                surface(row, Color.WHITE)));
    }

    private static int actionColor(View row, int resource, int ink) {
        if (resource != -1) {
            int nativeColor = row.getContext().getColor(resource);
            // Retain semantic red and other chromatic action colors.
            int high = Math.max(Color.red(nativeColor), Math.max(Color.green(nativeColor), Color.blue(nativeColor)));
            int low = Math.min(Color.red(nativeColor), Math.min(Color.green(nativeColor), Color.blue(nativeColor)));
            if (high - low > 45) return nativeColor;
        }
        return ink;
    }

    private static void styleChildren(View view, int textTint, int iconTint, boolean enabled) {
        if (view instanceof TextView) {
            TextView text = (TextView)view;
            text.setTextColor(textTint);
            text.setAlpha(enabled ? 1f : .45f);
            text.setTypeface(view.getResources().getFont(0x7f090005));
        } else if (view instanceof ImageView) {
            ((ImageView)view).setImageTintList(ColorStateList.valueOf(iconTint));
            view.setAlpha(enabled ? 1f : .45f);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) styleChildren(group.getChildAt(i), textTint, iconTint, enabled);
        }
    }

    private static final class Presentation extends RecyclerView.AdapterDataObserver
            implements Application.ActivityLifecycleCallbacks {
        final Dialog dialog;
        final Activity activity;
        final View root;
        final RecyclerView.Adapter<?> adapter;
        final Rect frame = new Rect();
        final boolean anchored;
        final int x, y;
        final int requestedAction;
        final boolean tapAction;
        final int handleVisibility;
        LiquidMenuSurface glass;
        PlayerWindow.Snapshot playerSnapshot;
        View playerBackground;
        FrameLayout shell;
        SystemBarTheme bars;
        SquareSongMenu songMenu;
        PopupWindow popup;
        boolean chosen, released;
        final Runnable timeout = this::timedOut;
        final Runnable afterUpdates = () -> select();

        Presentation(Dialog dialog, Activity activity, View root, RecyclerView.Adapter<?> adapter) {
            this.dialog = dialog; this.activity = activity; this.root = root; this.adapter = adapter;
            playerSnapshot = PlayerWindow.take();
            requestedAction = SquareGestures.request(activity);
            anchored = touchedActivity.get() == activity && SystemClock.uptimeMillis() - touchedAt < 2500;
            x = Math.round(touchX); y = Math.round(touchY);
            activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
            // A completed stationary click can be anywhere: toolbar, song row,
            // playlist card or player. Long presses retain the richer sheet.
            tapAction = anchored && completedTap && !moved;
            View handle = root.findViewById(0x7f0b064a);
            handleVisibility = handle == null ? View.GONE : handle.getVisibility();
            // Consume this gesture so a later programmatic opening cannot reuse it.
            touchedAt = 0;
        }

        void start() {
            root.setBackgroundColor(Color.TRANSPARENT);
            View header = root.findViewById(0x7f0b0336);
            if (header != null) SquareMenus.header(header);
            View list = root.findViewById(0x7f0b05b8);
            if (list != null) list.setBackgroundColor(Color.TRANSPARENT);
            activity.getApplication().registerActivityLifecycleCallbacks(this);
            adapter.registerAdapterDataObserver(this);
            if (requestedAction != 0 && adapter.getItemCount() == 0) activity.getWindow().getDecorView().postDelayed(timeout, 900);
            else if (!anchored && requestedAction == 0) sheet();
            else if (adapter.getItemCount() != 0) select();
            else activity.getWindow().getDecorView().postDelayed(timeout, 900);
        }

        void select() {
            if (chosen || released) return;
            if (activity.isFinishing() || activity.isDestroyed()) { dialog.dismiss(); return; }
            int count = adapter.getItemCount();
            if (count == 0) return;
            if (requestedAction != 0) {
                chosen = true;
                if (!(root instanceof ViewGroup) || !SquareSongMenu.perform(adapter, (ViewGroup)root, requestedAction)) unavailable();
                else dialog.dismiss();
                return;
            }
            activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
            if (!tapAction) { sheet(); return; }
            int margin = dp(root, 12);
            float fontScale = root.getResources().getConfiguration().fontScale;
            int width = Math.min(dp(root, fontScale > 1.3f ? 352 : 304), frame.width() - margin * 2);
            if (width < dp(root, 220)) { sheet(); return; }
            View sheetHandle = root.findViewById(0x7f0b064a);
            if (sheetHandle != null) sheetHandle.setVisibility(View.GONE);
            int maxHeight = Math.min(frame.height() - 2 * margin, dp(root, 560));
            View list = root.findViewById(0x7f0b05b8);
            if (list != null) {
                ViewGroup.LayoutParams params = list.getLayoutParams();
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                list.setLayoutParams(params);
            }
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
            int height = root.getMeasuredHeight();
            if (height <= 0) { sheet(); return; }
            // Long action menus scroll within their own pane instead of being
            // converted back into sheets. Keep the native header above the list.
            if (list != null) {
                ViewGroup.LayoutParams params = list.getLayoutParams();
                int headerHeight = Math.max(0, height - list.getMeasuredHeight());
                // A meaningful action must fit below the native header. Very
                // short windows or extreme text sizes need the sheet fallback.
                if (headerHeight + dp(root, 48) > maxHeight) { sheet(); return; }
                params.height = Math.max(dp(root, 48), Math.min(list.getMeasuredHeight(), maxHeight - headerHeight));
                list.setLayoutParams(params);
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
                height = root.getMeasuredHeight();
            }
            chosen = true;
            activity.getWindow().getDecorView().removeCallbacks(timeout);
            activity.getWindow().getDecorView().removeCallbacks(afterUpdates);
            if (root.getParent() instanceof ViewGroup) ((ViewGroup)root.getParent()).removeView(root);
            makeShell(false);
            int desiredLeft = root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? x - dp(root, 24) : x - width + dp(root, 24);
            int left = Math.max(frame.left + margin, Math.min(desiredLeft, frame.right - width - margin));
            int top = y + dp(root, 12);
            if (top + height > frame.bottom - margin) top = y - height - dp(root, 12);
            top = Math.max(frame.top + margin, Math.min(top, frame.bottom - height - margin));
            popup = new PopupWindow(shell, width, height, true);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.setElevation(dp(root, 10));
            popup.setOutsideTouchable(true);
            popup.setClippingEnabled(true);
            popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            popup.setOnDismissListener(() -> { if (!released) dialog.dismiss(); });
            shell.setPivotX(Math.max(0, Math.min(width, x - left)));
            shell.setPivotY(top >= y ? 0 : height);
            popup.showAtLocation(activity.getWindow().getDecorView(), Gravity.TOP | Gravity.LEFT, left, top);
            SurfaceMotion.enter(shell,true,false);
            root.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }

        void sheet() {
            if (chosen || released) return;
            chosen = true;
            activity.getWindow().getDecorView().removeCallbacks(timeout);
            activity.getWindow().getDecorView().removeCallbacks(afterUpdates);
            if (activity.isFinishing() || activity.isDestroyed()) { dialog.dismiss(); return; }
            View handle = root.findViewById(0x7f0b064a);
            if (handle != null) handle.setVisibility(View.GONE);
            makeShell(true);
            dialog.show();
            // BottomSheetBehavior is the sole owner of the sheet's enter/drag motion.
            bars = SystemBarTheme.installWindow(dialog.getWindow());
            if (playerSnapshot != null) {
                ViewGroup decor=(ViewGroup)dialog.getWindow().getDecorView();
                playerBackground=playerSnapshot.view(activity, true);
                playerBackground.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                decor.addView(playerBackground, 0, new ViewGroup.LayoutParams(-1,-1));
            }
        }

        void makeShell(boolean sheet) {
            View shadow = root.findViewById(0x7f0b0644);
            if (shadow == null && root.getParent() instanceof View) shadow = ((View)root.getParent()).findViewById(0x7f0b0644);
            if (shadow == null && dialog.getWindow() != null) shadow = dialog.getWindow().getDecorView().findViewById(0x7f0b0644);
            if (shadow != null) shadow.setVisibility(View.GONE);
            glass = new LiquidMenuSurface(activity, playerSnapshot);
            shell = new FrameLayout(root.getContext());
            if (root.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup)root.getParent();
                int index = parent.indexOfChild(root);
                ViewGroup.LayoutParams params = root.getLayoutParams();
                parent.removeView(root);
                parent.addView(shell, index, params);
                parent.setBackgroundColor(Color.TRANSPARENT);
            }
            glass.paint(shell, sheet ? 28f : 24f, false, sheet);
            if (sheet) {
                android.widget.LinearLayout column = SheetChrome.layout(shell, root);
                if (playerSnapshot != null) column.addView(SleepTimer.button(activity,glass),
                    new android.widget.LinearLayout.LayoutParams(-1,dp(root,56)));
            } else shell.addView(root, new FrameLayout.LayoutParams(-1, -2));
        }

        void unavailable() {
            Toast.makeText(activity, "This action is unavailable for this track", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }
        void timedOut() { if (requestedAction != 0) unavailable(); else sheet(); }

        void release() {
            if (released) return;
            released = true;
            open.remove(dialog);
            if (shell != null) LiquidViewMotion.cancel(shell,"enter");
            activity.getWindow().getDecorView().removeCallbacks(timeout);
            activity.getWindow().getDecorView().removeCallbacks(afterUpdates);
            adapter.unregisterAdapterDataObserver(this);
            if (songMenu != null) { songMenu.release(); songMenu = null; }
            if (bars != null) { bars.release(); bars = null; }
            if (glass != null) { glass.release(); glass = null; }
            if(playerBackground!=null && playerBackground.getParent() instanceof ViewGroup)
                ((ViewGroup)playerBackground.getParent()).removeView(playerBackground);
            playerBackground=null;
            if(playerSnapshot!=null) { playerSnapshot.release(); playerSnapshot=null; }
            activity.getApplication().unregisterActivityLifecycleCallbacks(this);
            if (popup != null) {
                LiquidViewMotion.cancel(shell,"enter");
                popup.setOnDismissListener(null);
                popup.dismiss();
                popup = null;
            }
        }

        void schedule() {
            if (chosen || released) return;
            // AsyncListDiffer dispatches several notifications per list. Measuring
            // inside one of them sees an incomplete RecyclerView position map.
            View decor = activity.getWindow().getDecorView();
            decor.removeCallbacks(afterUpdates);
            decor.postOnAnimation(afterUpdates);
        }
        @Override public void onChanged() { schedule(); }
        @Override public void onItemRangeInserted(int start, int count) { schedule(); }
        @Override public void onItemRangeChanged(int start, int count) { schedule(); }
        @Override public void onActivityDestroyed(Activity target) { if (target == activity) dialog.dismiss(); }
        @Override public void onActivityStopped(Activity target) { if (target == activity) dialog.dismiss(); }
        @Override public void onActivityCreated(Activity a, Bundle b) {}
        @Override public void onActivityStarted(Activity a) {}
        @Override public void onActivityResumed(Activity a) {}
        @Override public void onActivityPaused(Activity a) {}
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    }
}
