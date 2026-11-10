/* Square Glass for TIDAL. GPL-3.0-or-later. */
package dev.nino.squareglass;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsetsController;
import android.view.Window;

/** Keep system icons readable after native player transitions update the window. */
public final class SystemBarTheme implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    private final View decor;
    private final Window window;
    private boolean player;
    private SystemBarTheme(Window window) {
        this.window = window;
        decor = window.getDecorView();
        decor.getViewTreeObserver().addOnPreDrawListener(this);
        decor.addOnAttachStateChangeListener(this);
    }
    public static void install(Activity activity) { new SystemBarTheme(activity.getWindow()); }
    public static SystemBarTheme installWindow(Window window) { return new SystemBarTheme(window); }
    public static SystemBarTheme installPlayerWindow(Window window) {
        SystemBarTheme theme=new SystemBarTheme(window); theme.player=true; return theme;
    }
    @Override public boolean onPreDraw() {
        if (SquareSettings.themeEnabled() != 0) {
            WindowInsetsController controller = window.getInsetsController();
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            boolean dark = SquarePalette.isDark();
            int desired = dark ? 0 : mask;
            if (controller != null && (controller.getSystemBarsAppearance() & mask) != desired)
                controller.setSystemBarsAppearance(desired, mask);
            if (!player) {
                int baseColor = SquarePalette.base(dark);
                window.setBackgroundDrawable(new ColorDrawable(baseColor));
                window.setStatusBarColor(baseColor);
                window.setNavigationBarColor(baseColor);
                decor.setBackgroundColor(baseColor);
            }
        }
        return true;
    }
    @Override public void onViewAttachedToWindow(View view) {}
    public void release() {
        if (decor.getViewTreeObserver().isAlive()) decor.getViewTreeObserver().removeOnPreDrawListener(this);
        decor.removeOnAttachStateChangeListener(this);
    }
    @Override public void onViewDetachedFromWindow(View view) { release(); }
}
