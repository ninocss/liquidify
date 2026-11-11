/* Square Glass for TIDAL. GPL-3.0-or-later. */
package dev.nino.squareglass;

import android.content.Context;
import android.content.res.Configuration;

/** Theme override is scoped to TIDAL's Activity, never to the device. */
public final class SquareRuntime {
    private static boolean localeInitialized = false;
    private SquareRuntime() {}
    public static Context context(Context base) {
        SquareSettings.initialize(base);
        NativePlayback.initialize(base);
        if (SquareSettings.themeEnabled() != 0) {
            int target = SquareSettings.themeMode() == 0 ? -1 : SquareSettings.themeMode();
            if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != target) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(target);
            }
        }
        if (!localeInitialized) {
            localeInitialized = true;
            android.app.LocaleManager languages = base.getSystemService(android.app.LocaleManager.class);
            if (languages != null) {
                String tags = languages.getApplicationLocales().toLanguageTags();
                if (tags == null || !tags.contains("en")) {
                    languages.setApplicationLocales(android.os.LocaleList.forLanguageTags("en"));
                }
            }
        }
        java.util.Locale.setDefault(java.util.Locale.ENGLISH);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(java.util.Locale.ENGLISH);
        if (SquareSettings.themeEnabled() != 0 && SquareSettings.themeMode() != 0) config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                (SquareSettings.themeMode() == 2 ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(config);
    }
}
