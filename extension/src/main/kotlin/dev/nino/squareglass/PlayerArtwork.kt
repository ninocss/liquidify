/* Square Glass for TIDAL. GPL-3.0-or-later. */
package dev.nino.squareglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import java.util.WeakHashMap
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.animateColorAsState
import dev.nino.squareglass.material.LiquidMotion
import androidx.compose.animation.core.snap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Immersive portrait artwork with the native pager, actions and media lifecycle. */
object PlayerArtwork {
    private var surface by mutableStateOf(Color(SquarePalette.base(SquarePalette.isDark())))
    private class Frame {
        var coordinates: LayoutCoordinates? = null
        var height by mutableStateOf(0)
        fun top(child: LayoutCoordinates): Int {
            val parent = coordinates ?: return 0
            return if (parent.isAttached && child.isAttached)
                parent.localPositionOf(child, Offset.Zero).y.roundToInt() else 0
        }
    }
    private val frames = WeakHashMap<android.view.View, Frame>()
    @Composable private fun currentFrame(): Frame {
        val view = LocalView.current
        return remember(view) { frames.getOrPut(view) { Frame() } }
    }
    /** Anchor inside the native moving portrait container, never the window. */
    @JvmStatic @Composable fun frame(original: Modifier): Modifier {
        val view = LocalView.current
        val frame = currentFrame()
        DisposableEffect(frame) { onDispose { if (frames[view] === frame) frames.remove(view) } }
        return IconMotion.watch(original).onGloballyPositioned {
            frame.coordinates = it
            if (frame.height != it.size.height) frame.height = it.size.height
        }
    }

    /** Move the portrait pager behind its header, retaining the native control layout. */
    @JvmStatic
    @Composable
    fun hero(original: Modifier): Modifier {
        val view = LocalView.current
        val frame = currentFrame()
        var top by remember { mutableStateOf(0) }
        return original.onGloballyPositioned {
            val next = frame.top(it)
            if (top != next) top = next
        }.layout { measurable, constraints ->
            val width = constraints.maxWidth
            // Scale uniformly, then crop the sides. Never stretch the album image.
            val side = minOf(maxOf(width, ((frame.height.takeIf { it > 0 } ?: view.height) * .74f).roundToInt()), width * 2)
            val placeable = measurable.measure(Constraints.fixed(side, side))
            layout(width, constraints.maxHeight) { placeable.place((width - side) / 2, -top) }
        }
    }

    @JvmStatic
    @Composable
    fun header(original: Modifier): Modifier = original.zIndex(2f)

    @Composable
    private fun atBottom(original: Modifier): Modifier {
        val view = LocalView.current
        val frame = currentFrame()
        var top by remember { mutableStateOf(0) }
        return original.onGloballyPositioned {
            val next = frame.top(it)
            if (top != next) top = next
        }.layout { measurable, constraints ->
            val child = measurable.measure(constraints)
            val height = frame.height.takeIf { it > 0 } ?: view.height
            val insets = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                view.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
            } else {
                @Suppress("DEPRECATION")
                view.rootWindowInsets?.systemWindowInsetBottom ?: 0
            }
            val bottomPadding = if (insets > 0) insets else (view.resources.displayMetrics.density * 8).roundToInt()
            val targetY = maxOf(0, height - child.height - bottomPadding)
            layout(child.width, child.height) { child.place(0, targetY - top) }
        }
    }

    @Composable
    private fun atHeight(original: Modifier, fraction: Float): Modifier {
        val view = LocalView.current
        val frame = currentFrame()
        var top by remember { mutableStateOf(0) }
        return original.onGloballyPositioned {
            val next = frame.top(it)
            if (top != next) top = next
        }.layout { measurable, constraints ->
            val child = measurable.measure(constraints)
            val height = frame.height.takeIf { it > 0 } ?: view.height
            layout(child.width, child.height) { child.place(0, minOf((height * fraction).roundToInt(), height - child.height) - top) }
        }
    }

    /** A tint on this media-only layer retains alpha, hit targets and native semantics. */
    @JvmStatic
    fun ink(original: Modifier): Modifier = original.drawWithCache {
        val paint = Paint().apply { colorFilter = ColorFilter.tint(Color(SquarePalette.ink(SquarePalette.isDark())), BlendMode.SrcIn) }
        onDrawWithContent {
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawContent()
            drawContext.canvas.restore()
        }
    }

    @JvmStatic
    @Composable
    fun media(original: Modifier): Modifier {
        val view = LocalView.current
        return original.layout { measurable, constraints ->
            val child = measurable.measure(constraints)
            layout(child.width, child.height) { child.place(0, -(view.height * .035f).roundToInt()) }
        }.then(ink(Modifier))
    }

    @JvmStatic
    @Composable
    fun footer(original: Modifier): Modifier = atBottom(original).then(ink(Modifier)).zIndex(2f)

    @JvmStatic
    @OptIn(InternalComposeApi::class)
    @Composable
    fun Background(nativeColor: Long, albumId: Int, coverId: String?, original: Modifier) {
        DesignState.revision
        val context = LocalContext.current
        val loader = ArtworkLoader.provider(currentComposer)
        // This pinned host supplies packed sRGB Color values (ARGB in the high word).
        var sampled by remember(albumId, coverId) { mutableStateOf<Int?>(null) }
        LaunchedEffect(albumId, coverId, loader) {
            if (!coverId.isNullOrEmpty()) try {
                val builder = xd0.g.a(context)
                builder.a(albumId, coverId)
                val result = loader.a(builder.c())
                if (result is com.tidal.android.image.core.a.b) {
                    sampled = withContext(Dispatchers.Default) { SquareDetails.coverSeed(result.a) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (unavailable: Exception) { android.util.Log.d("SquareGlass", "Cover palette unavailable") }
        }
        val target = Color(CoverColors.playerSurface(sampled ?: (nativeColor ushr 32).toInt(), NativeGlass.dark))
        val color by animateColorAsState(target, if (SquareSettings.motion()) LiquidMotion.settle() else snap(), label = "artwork surface")
        SideEffect { surface = color }
        // This same flat tone meets the final pixel of the cover's fade.
        Box(NativeBackdrop.capturePlayer(original).fillMaxSize().background(color))
    }

    @JvmStatic
    @Composable
    fun cover(original: Modifier): Modifier {
        DesignState.revision
        val isDark = NativeGlass.dark
        return original.drawWithCache {
            val color = surface
            val topFilm = Color(SquarePalette.base(isDark))
            val saturation = ColorMatrix().apply { setToSaturation(1.28f) }
            val artworkPaint = Paint().apply { colorFilter = ColorMatrixColorFilter(saturation) }
            val fade = Brush.verticalGradient(
                Pair(0f, topFilm.copy(alpha = .32f)),
                Pair(.12f, color.copy(alpha = 0f)),
                Pair(.48f, color.copy(alpha = 0f)),
                Pair(.66f, color.copy(alpha = .18f)),
                Pair(.84f, color.copy(alpha = .80f)),
                Pair(.96f, color),
                Pair(1f, color),
                endY = size.height,
            )
            onDrawWithContent {
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), artworkPaint)
                drawContent()
                drawContext.canvas.restore()
                drawRect(fade)
            }
        }
    }
}
