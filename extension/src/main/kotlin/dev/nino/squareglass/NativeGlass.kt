/* Square Glass for TIDAL. GPL-3.0-or-later. */
package dev.nino.squareglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import dev.nino.squareglass.backdrop.drawBackdrop
import dev.nino.squareglass.backdrop.effects.blur
import dev.nino.squareglass.backdrop.effects.lens
import dev.nino.squareglass.backdrop.effects.vibrancy
import dev.nino.squareglass.backdrop.highlight.Highlight
import dev.nino.squareglass.backdrop.shadow.Shadow

object NativeGlass {
    private var hostDark: Boolean by mutableStateOf(true)
    val dark: Boolean
        @Composable get() {
            DesignState.revision
            if (SquareSettings.themeEnabled() == 0) return hostDark
            val mode = SquareSettings.themeMode()
            val uiMode = LocalConfiguration.current.uiMode
            return if (mode != 0) mode == 2 else
                (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

    @JvmStatic
    fun observeHostBackground(packedColor: Long) {
        ProbeDiagnostics.hostBackground = packedColor
        hostDark = Color(packedColor).luminance() < .5f
    }

    @JvmStatic
    @Composable
    fun surface(original: Modifier, shape: Shape): Modifier {
        DesignState.revision
        val film = (if (dark) Color(0xFF33343A) else Color.White).copy(alpha = SquareSettings.readableOpacity(dark))
        if (SquareSettings.preset() == 2) return original.clip(shape).background(Color(SquarePalette.surface(dark)))
        return original.drawBackdrop(
            backdrop = NativeBackdrop.active,
            shape = { shape },
            effects = {
                vibrancy(); blur(SquareSettings.blur().dp.toPx())
                if (SquareSettings.motion() && SquareSettings.preset() == 0)
                    lens((24 * SquareSettings.refraction()).dp.toPx(), (32 * SquareSettings.refraction()).dp.toPx())
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(radius = 12.dp) },
            onDrawSurface = { ProbeDiagnostics.glassDraws++; drawRect(film) },
        )
    }

    @JvmStatic
    @Composable
    fun miniSurface(original: Modifier): Modifier {
        DesignState.revision
        val inset = original.padding(bottom = 8.dp)
        return MiniProgress.decorate(AccessoryGlass.surface(inset, NativeBackdrop.accessory, SquareSettings.glass("mini"), component = "mini"))
    }

    @JvmStatic
    @Composable
    fun playerButton(original: Modifier): Modifier = original

    @JvmStatic
    @Composable
    fun playerIconButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
        interactionSource: MutableInteractionSource? = null,
        content: @Composable () -> Unit,
    ) {
        val currentInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
        val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
        Box(
            modifier = playerButton(modifier)
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = currentInteractionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
        }
    }

    @JvmStatic
    @Composable
    fun secondaryBar(original: Modifier): Modifier {
        DesignState.revision
        val shape = RoundedCornerShape(28.dp)
        val base = if (SquareSettings.glass("player")) surface(original, shape)
        else original.clip(shape).background(Color(SquarePalette.surface(dark)))
        return base.padding(horizontal = 12.dp, vertical = 6.dp)
    }

    @JvmStatic
    fun iconTint(): ColorFilter = ColorFilter.tint(Color(SquarePalette.ink(SquarePalette.isDark())))
}
