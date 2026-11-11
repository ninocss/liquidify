/* Square Glass for TIDAL. GPL-3.0-or-later. */
package dev.nino.squareglass;

import android.content.res.Configuration;
import android.content.res.Resources;
import androidx.compose.ui.graphics.ColorKt;
import java.util.HashMap;
import java.util.Map;

/** Semantic palette adapter, never a filter over artwork or a screenshot. */
public final class SquarePalette {
    private SquarePalette() {}
    private static final Map<String, f20.a> lightColors = new HashMap<>();
    private static final Map<String, f20.a> darkColors = new HashMap<>();

    public static boolean isDark() {
        if (SquareSettings.themeEnabled() == 0) return true;
        int mode = SquareSettings.themeMode();
        if (mode != 0) return mode == 2;
        return (Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
    public static int base(boolean dark) { return dark ? 0xff0d0e11 : 0xfff1f2f6; }
    public static int ink(boolean dark) { return dark ? 0xfff7f8fa : 0xff15161a; }
    public static int surface(boolean dark) { return dark ? 0xff191b22 : 0xffffffff; }
    public static long inkColor() { return ColorKt.Color(ink(isDark())); }
    public static int secondary(boolean dark) { return dark ? 0xffb4b8c4 : 0xff5d626d; }
    // Keep secondary and tertiary text readable on the lightest native card
    // (#e0e2eb) as well as the page base; muted text still needs 4.5:1.
    private static int tertiary(boolean dark) { return dark ? 0xff9a9fac : 0xff60656f; }
    private static int alpha(int color, int alpha) { return (color & 0xffffff) | (alpha << 24); }

    public static long wave(long original, String role) {
        if (ProbeDiagnostics.enabled) ProbeDiagnostics.wavePaletteCalls++;
        boolean dark = isDark();
        int argb = (int)(original >>> 32);
        int color;
        switch (role) {
            case "solidBaseFill": color = base(dark); break;
            case "solidBaseBright": color = surface(dark); break;
            case "solidBaseBrighter": color = dark ? 0xff22252e : 0xffe9eaf0; break;
            case "solidBaseBrightest": color = dark ? 0xff30343f : 0xffe0e2eb; break;
            case "solidContrastFill": color = ink(dark); break;
            case "solidContrastDark": color = secondary(dark); break;
            case "solidContrastDarker": color = tertiary(dark); break;
            // This role is also used for small labels and icons. Previous values
            // fell below 3:1 in both themes; use a contrast-safe muted ink.
            case "solidContrastDarkest": color = tertiary(dark); break;
            case "solidAccentFill": color = ink(dark); break;
            case "solidAccentBright": color = dark ? 0xffd5d8df : 0xff454951; break;
            case "solidAccentBrighter": color = dark ? 0xffe8eaf0 : 0xff626873; break;
            case "solidAccentDark": color = dark ? 0xffb4b8c4 : 0xff353941; break;
            case "solidAccentDarker": color = dark ? 0xff828796 : 0xff24272e; break;
            case "solidAccentDarkest": color = dark ? 0xff5d626d : 0xff191b22; break;
            default:
                if (role.startsWith("opacityBaseFill")) color = alpha(base(dark), argb >>> 24);
                else if (role.startsWith("opacityBaseBright")) color = alpha(surface(dark), argb >>> 24);
                else if (role.startsWith("opacityContrastFill")) color = alpha(ink(dark), argb >>> 24);
                else if (role.startsWith("opacityAccent")) color = alpha(ink(dark), argb >>> 24);
                else return original; // Keep semantic warning, service and quality colors.
        }
        return ColorKt.Color(color);
    }

    public static f20.a market(f20.a original, String role) {
        if (ProbeDiagnostics.enabled) ProbeDiagnostics.marketPaletteCalls++;
        boolean dark = isDark();
        int color;
        switch (role) {
            case "text10": case "fill10": case "surfaceInverse": color = ink(dark); break;
            case "text20": color = secondary(dark); break;
            case "text30": color = tertiary(dark); break;
            case "textInverse": case "fillInverse": case "surface5": color = base(dark); break;
            case "surface10": color = surface(dark); break;
            case "surface20": color = dark ? 0xff22252e : 0xffe9eaf0; break;
            case "surface30": color = dark ? 0xff30343f : 0xffe0e2eb; break;
            case "fill20": color = alpha(ink(dark), 0xb3); break;
            case "fill30": color = alpha(ink(dark), 0x66); break;
            case "fill40": color = alpha(ink(dark), 0x33); break;
            case "fill50": color = alpha(ink(dark), 0x1a); break;
            case "divider10": color = alpha(ink(dark), 0x1f); break;
            case "divider20": color = alpha(ink(dark), 0x33); break;
            case "emphasisText": case "emphasisFill": color = ink(dark); break;
            case "emphasis10": color = alpha(ink(dark), 0x1a); break;
            case "emphasis20": color = alpha(ink(dark), 0x33); break;
            case "emphasis30": color = alpha(ink(dark), 0x66); break;
            case "emphasis40": color = alpha(ink(dark), 0xb3); break;
            default: return original;
        }
        Map<String, f20.a> cache = dark ? darkColors : lightColors;
        f20.a result = cache.get(role);
        if (result == null) { result = new f20.a(color); cache.put(role, result); }
        ProbeDiagnostics.palette(role, original.k(), result.k());
        return result;
    }
}
