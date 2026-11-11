/* GPL-3.0-or-later. One material for the two persistent playback/navigation pills. */
package dev.nino.squareglass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import dev.nino.squareglass.backdrop.Backdrop
import dev.nino.squareglass.backdrop.drawBackdrop
import dev.nino.squareglass.backdrop.effects.blur
import dev.nino.squareglass.backdrop.effects.lens
import dev.nino.squareglass.backdrop.effects.vibrancy
import dev.nino.squareglass.backdrop.highlight.Highlight
import dev.nino.squareglass.material.shapes.ContinuousCapsule

object AccessoryGlass {
    @Composable fun surface(original: Modifier, backdrop: Backdrop, enabled: Boolean,
        component: String,
        layer: (GraphicsLayerScope.() -> Unit)? = null): Modifier {
        DesignState.revision
        // A sample swap must repaint even when only the native fragment changed
        // and the separate scaffold ComposeView itself has no invalidation.
        val native = backdrop as? NativeViewBackdrop
        native?.handoverVersion
        val hasSample = native?.hasSample == true
        val dark = NativeGlass.dark
        val shape = ContinuousCapsule()
        val navigation = component == "nav"
        if (!enabled) return original.clip(shape).background(Color(SquarePalette.surface(dark && !navigation)))
        // The first composition can precede the native page's first recording.
        // Draw a readable capsule immediately, then repaint both accessories
        // when that recording becomes available, even on an otherwise idle page.
        val film = if (navigation) Color.White.copy(alpha = Math.max(.50f, SquareSettings.opacity()))
            else (if (dark) Color(0xFF33343A) else Color.White).copy(alpha = SquareSettings.readableOpacity(dark))
        return original.drawBackdrop(backdrop = backdrop, shape = { shape }, effects = {
            vibrancy()
            blur(8.dp.toPx())
            if (SquareSettings.preset() == 0 && SquareSettings.motion()) {
                val refraction = SquareSettings.refraction()
                lens((24 * refraction).dp.toPx(), (32 * refraction).dp.toPx())
            }
        }, highlight = {
            // The library's 0.5dp default rim is nearly invisible on these
            // moving native accessories. Keep a subtle but persistent edge glint.
            Highlight(width = 1.dp, blurRadius = 2.dp, alpha = 0.8f)
        }, layerBlock = layer, onDrawSurface = {
            ProbeDiagnostics.glassDraws++
            if (hasSample) {
                val now = android.os.SystemClock.uptimeMillis()
                if (component == "nav" && ProbeDiagnostics.navFirstSampleDrawUptime == 0L) {
                    ProbeDiagnostics.navFirstSampleDrawUptime = now
                    ProbeDiagnostics.navFirstSampleTraversal = ProbeDiagnostics.nativeDrawTraversals
                }
                if (component == "mini" && ProbeDiagnostics.miniFirstSampleDrawUptime == 0L) {
                    ProbeDiagnostics.miniFirstSampleDrawUptime = now
                    ProbeDiagnostics.miniFirstSampleTraversal = ProbeDiagnostics.nativeDrawTraversals
                }
            }
            drawRect(film)
        })
    }
}
