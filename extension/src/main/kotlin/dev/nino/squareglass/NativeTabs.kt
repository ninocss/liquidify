/* Square Glass for TIDAL. GPL-3.0-or-later. */
package dev.nino.squareglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tidal.android.feature.appscaffold.ui.composable.y as NativeTab
import dev.nino.squareglass.material.LiquidBottomTab
import dev.nino.squareglass.material.LiquidBottomTabs
import dev.nino.squareglass.material.LocalAppBackdrop
import dev.nino.squareglass.material.LocalGlassEffectConfig
import dev.nino.squareglass.material.shapes.ContinuousCapsule

/** Square's actual draggable control, connected to TIDAL's original models and callbacks. */
object NativeTabs {
    data class TabOption(val key: String, val label: String)
    var options by androidx.compose.runtime.mutableStateOf<List<TabOption>>(emptyList())
        private set
    private fun key(context: android.content.Context, item: NativeTab): String =
        "navTab_" + context.resources.getResourceEntryName(item.b)

    @JvmStatic
    @Composable
    fun Render(selected: Int, items: List<NativeTab>, onItem: (NativeTab) -> Unit, modifier: Modifier) {
        if (items.isEmpty()) return
        DesignState.revision
        val context = LocalContext.current
        val available = items.map { TabOption(key(context, it), context.getString(it.b)) }
        androidx.compose.runtime.SideEffect { if (options != available) options = available }
        val visible = items.filter { SquareSettings.flag(key(context, it), true) }.ifEmpty { listOf(items.first()) }
        var visibleSelected = 0
        for (i in visible.indices) if (visible[i] == items.getOrNull(selected)) visibleSelected = i
        val dark = NativeGlass.dark
        val ink = Color(SquarePalette.ink(dark))
        val iconInk = ink
        val here = rememberUpdatedState(visibleSelected)
        val readHere = remember { { here.value } }
        val currentItems = rememberUpdatedState(visible)
        val currentAction = rememberUpdatedState(onItem)
        val pulses=remember {mutableStateOf(List(visible.size) {0})}
        val select: (Int) -> Unit = remember {
            { index ->
                val list = currentItems.value
                if (index >= 0 && index < list.size) {
                    pulses.value=List(list.size) {i -> (pulses.value.getOrNull(i) ?: 0)+if(i==index)1 else 0}
                    ProbeDiagnostics.nativeTabActions++
                    NativeBackdrop.transition()
                    currentAction.value(list[index])
                }
            }
        }
        CompositionLocalProvider(LocalAppBackdrop provides NativeBackdrop.accessory,
            LocalGlassEffectConfig provides DesignState.config()) {
            LiquidBottomTabs(
                selectedTabIndex = readHere,
                onTabSelected = select,
                backdrop = NativeBackdrop.accessory,
                tabsCount = visible.size,
                modifier = modifier.clip(ContinuousCapsule()).selectableGroup(),
            ) {
                var index = 0
                while (index < visible.size) {
                    val item = visible[index]
                    val tabIndex = index
                    val isSelected = item == items.getOrNull(selected)
                    LiquidBottomTab(onClick = { select(tabIndex) }, modifier = Modifier.semantics { this.selected = isSelected }) {
                        Box {
                            Icon(painterResource(item.a), contentDescription = null, tint = iconInk, modifier = IconMotion.icon(Modifier.size(25.dp),tabIndex,pulses.value.getOrNull(tabIndex) ?: 0))
                            if (item.d) Box(Modifier.align(Alignment.TopEnd).size(5.dp).background(iconInk, CircleShape))
                        }
                        Text(stringResource(item.b).let { if (it == "My Collection") "Collection" else it }, color = ink, fontFamily = SquareTypography.family, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    index++
                }
            }
        }
    }
}
