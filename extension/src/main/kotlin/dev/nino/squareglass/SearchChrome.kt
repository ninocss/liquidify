/* GPL-3.0-or-later. The real native search decoration; callbacks and geometry stay native. */
package dev.nino.squareglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nino.squareglass.material.shapes.ContinuousCapsule

object SearchChrome {
    @JvmStatic fun container(): @Composable () -> Unit = {
        DesignState.revision
        val dark = SquarePalette.isDark()
        val shape = ContinuousCapsule()
        val borderModifier = if (SquareSettings.themeEnabled() != 0) {
            Modifier.border(
                1.dp,
                Color(SquarePalette.ink(dark)).copy(alpha = if (dark) 0.12f else 0.08f),
                shape
            )
        } else Modifier
        Box(
            GlassPanels.panel(Modifier.fillMaxSize(), 28f, emphasized = true)
                .then(borderModifier)
        )
    }

    @JvmStatic fun shape(): Shape = ContinuousCapsule()

    @JvmStatic fun cursor(): Brush = SolidColor(Color(SquarePalette.ink(SquarePalette.isDark())))

    @JvmStatic
    @Composable
    fun colors(original: TextFieldColors): TextFieldColors {
        DesignState.revision
        if (SquareSettings.themeEnabled() == 0) return original
        val dark = SquarePalette.isDark()
        val ink = Color(SquarePalette.ink(dark))
        val dim = Color(SquarePalette.secondary(dark))
        return TextFieldDefaults.colors(
            focusedTextColor = ink,
            unfocusedTextColor = ink,
            cursorColor = ink,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedPlaceholderColor = dim,
            unfocusedPlaceholderColor = dim,
            focusedLeadingIconColor = dim,
            unfocusedLeadingIconColor = dim,
            focusedTrailingIconColor = ink,
            unfocusedTrailingIconColor = dim,
        )
    }

    @JvmStatic fun bottomPadding(): PaddingValues = PaddingValues(bottom = 112.dp)

    @JvmStatic fun filterRowPadding(): PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp)

    @JvmStatic fun filterShape(): Shape = CircleShape

    @JvmStatic
    fun filterColors(selected: Boolean): Pair<Color, Color> {
        val dark = SquarePalette.isDark()
        val ink = Color(SquarePalette.ink(dark))
        val base = Color(SquarePalette.base(dark))
        val surface = Color(SquarePalette.surface(dark))
        val textColor = if (selected) base else ink
        val backgroundColor = if (selected) ink else surface
        return Pair(textColor, backgroundColor)
    }

    @JvmStatic
    fun filterChipModifier(original: Modifier, selected: Boolean): Modifier {
        if (SquareSettings.themeEnabled() == 0) return original
        val dark = SquarePalette.isDark()
        val shape = CircleShape
        val ink = Color(SquarePalette.ink(dark))
        val borderMod = if (selected) Modifier else Modifier.border(1.dp, ink.copy(alpha = 0.12f), shape)
        return original.clip(shape).then(borderMod)
    }

    @JvmStatic
    @Composable
    fun Filter(label: String, selected: Boolean, onClick: () -> Unit) {
        DesignState.revision
        val dark = SquarePalette.isDark()
        val ink = Color(SquarePalette.ink(dark))
        val base = Color(SquarePalette.base(dark))
        val surface = Color(SquarePalette.surface(dark))
        val duration = if (SquareSettings.motion()) 220 else 0
        val fill by animateColorAsState(if (selected) ink else surface, tween(duration), label = "searchFilterFill")
        val text by animateColorAsState(if (selected) base else ink, tween(duration), label = "searchFilterInk")
        val action = rememberUpdatedState(onClick)
        Box(
            Modifier.sizeIn(minHeight = 44.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.dp, ink.copy(alpha = if (selected) 0f else .12f), CircleShape)
                .selectable(selected, role = Role.RadioButton, onClick = { if (!selected) action.value() })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = text, fontFamily = SquareTypography.family, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
