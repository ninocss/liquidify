/* Square Glass for TIDAL. GPL-3.0-or-later. */
package dev.nino.squareglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

object SquareScreens {
    @JvmStatic
    @Composable
    fun settingsRow(original: Modifier): Modifier {
        DesignState.revision
        return GlassPanels.panel(original.padding(horizontal = 16.dp, vertical = 4.dp),20f)
    }

    @JvmStatic
    @Composable
    fun enter(original: Modifier): Modifier {
        DesignState.revision
        // Native navigation owns page transitions; a second arrival animation
        // flashes retained compositions when the old page is shown again.
        return original
    }

    /** Legacy settings do not receive the Compose scaffold's accessory insets. */
    @JvmStatic
    fun settingsBottomInset(native: Float): Float = maxOf(native, 180f)

    @JvmStatic
    fun upload(view: android.view.View) {
        val bottom = Math.round(96 * view.resources.displayMetrics.density)
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom)
    }
}
