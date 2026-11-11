/* GPL-3.0-or-later. Decorative drawing only; native actions remain the sole callbacks. */
package dev.nino.squareglass

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*

object IconMotion {
    private class Entry(val available: () -> Boolean,val fire: (Int) -> Unit)
    private val entries=ArrayList<Entry>()
    private val handler=Handler(Looper.getMainLooper())
    private val policy=MotionPolicy(SystemClock.uptimeMillis())
    private var cursor=0
    private var idleStyle=0
    private val tick=object: Runnable {
        override fun run() {
            val now=SystemClock.uptimeMillis()
            if(policy.ready(now,SquareSettings.motion() && !SquareMenus.hasOpenMenu())) {
                var tried=0
                while(tried<entries.size) {
                    if(cursor>=entries.size)cursor=0
                    val entry=entries[cursor++];tried++
                    if(entry.available()) {
                        entry.fire(when(idleStyle++%3) {1->4;2->5;else->0})
                        ProbeDiagnostics.idleAnimations++;policy.fired(now);break
                    }
                }
            }
            val delay = if (!policy.ready(now, SquareSettings.motion() && !SquareMenus.hasOpenMenu())) 3000L else 12000L
            if (entries.isNotEmpty() && SquareSettings.motion()) handler.postDelayed(this, delay)
        }
    }
    @JvmStatic fun touch(event: MotionEvent) {
        val action=event.actionMasked
        policy.touch(SystemClock.uptimeMillis(),action!=MotionEvent.ACTION_UP && action!=MotionEvent.ACTION_CANCEL)
    }
    private fun interacted(down: Boolean) {policy.touch(SystemClock.uptimeMillis(),down)}
    @JvmStatic fun registerNative(view: View,fire: java.util.function.IntConsumer): Any {
        val entry=Entry({
            val rect=android.graphics.Rect()
            view.isShown && view.hasWindowFocus() && view.isEnabled && view.getGlobalVisibleRect(rect) &&
                view.rootWindowInsets?.isVisible(WindowInsets.Type.ime())!=true
        },{fire.accept(it)})
        val start=entries.isEmpty();entries.add(entry)
        if(start)handler.postDelayed(tick,500)
        return entry
    }
    @JvmStatic fun unregisterNative(token: Any) {
        entries.remove(token as Entry);if(entries.isEmpty())handler.removeCallbacks(tick)
    }
    fun watch(original: Modifier): Modifier = original.pointerInput(Unit) {
        awaitPointerEventScope {
            while(true) {
                val event=awaitPointerEvent(PointerEventPass.Initial)
                interacted(event.changes.any {it.pressed})
            }
        }
    }

    @JvmStatic @Composable fun settings(original: Modifier): Modifier = decorate(original,6,0,null)
    @JvmStatic @Composable fun settingsResource(original: Modifier,resource: Int): Modifier =
        if(resource==0x7f08048e)settings(original) else original
    @JvmStatic @Composable fun favorite(original: Modifier,liked: Boolean): Modifier = decorate(original,7,0,liked)
    @Composable fun icon(original: Modifier,key: Int,pulse: Int): Modifier = decorate(original,key,pulse,null)

    @Composable private fun decorate(original: Modifier,key: Int,pulse: Int,liked: Boolean?): Modifier {
        DesignState.revision
        val view=LocalView.current
        val lifecycle=LocalLifecycleOwner.current.lifecycle
        val progress=remember {Animatable(1f)}
        var event by remember {mutableIntStateOf(0)}
        var kind by remember {mutableIntStateOf(0)}
        var bounds by remember {mutableStateOf(Rect.Zero)}
        var lastTap by remember {mutableStateOf(-20000L)}
        var previousLike by remember {mutableStateOf(liked)}
        var previousPulse by remember {mutableIntStateOf(pulse)}
        val motion=SquareSettings.motion()
        val fire: (Int)->Unit = {if(SquareSettings.motion()) {kind=it;event++}}
        val latestFire=rememberUpdatedState(fire)
        val latestBounds=rememberUpdatedState(bounds)
        DisposableEffect(view,lifecycle,key) {
            val origin=IntArray(2)
            val entry=Entry({
                val b=latestBounds.value
                view.getLocationInWindow(origin)
                val ime=view.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true
                view.isShown && view.hasWindowFocus() && !ime &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    MotionPolicy.visible(b.left,b.top,b.right,b.bottom,origin[0].toFloat(),origin[1].toFloat(),view.width.toFloat(),view.height.toFloat())
            },{latestFire.value(it)})
            val start=entries.isEmpty();entries.add(entry)
            if(start)handler.postDelayed(tick,500)
            onDispose {entries.remove(entry);if(entries.isEmpty())handler.removeCallbacks(tick)}
        }
        LaunchedEffect(pulse) {
            if(previousPulse!=pulse) {previousPulse=pulse;fire(1)}
        }
        LaunchedEffect(liked) {
            if(previousLike!=liked) {
                previousLike=liked
                if(SystemClock.uptimeMillis()-lastTap<10000) {
                    fire(if(liked==true)2 else 3)
                    if(liked==true)ProbeDiagnostics.likeAnimations++
                }
                lastTap=-20000L
            }
        }
        LaunchedEffect(event,motion) {
            if(event>0 && motion) {
                progress.snapTo(0f)
                progress.animateTo(1f,tween(if(kind==2)620 else 460))
            } else progress.snapTo(1f)
        }
        return original.onGloballyPositioned {bounds=it.boundsInWindow()}
            .pointerInput(key) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                    interacted(true)
                    fire(1)
                    var moved=false
                    var cancelled=false
                    var pressed=true
                    try {while(pressed) {
                        val e=awaitPointerEvent(PointerEventPass.Initial)
                        val point=e.changes.firstOrNull {it.id==down.id}
                        if(point==null || e.changes.size>1) {cancelled=true;break}
                        if((point.position-down.position).getDistance()>viewConfiguration.touchSlop)moved=true
                        pressed=point.pressed
                    }} finally {interacted(false)}
                    if(!moved && !cancelled) {
                        lastTap=SystemClock.uptimeMillis()
                    }
                }
            }.drawWithContent {
                val t=if(SquareSettings.motion())progress.value else 1f
                val wave=sin(PI.toFloat()*t)
                val scale=when(kind) {3->1f-.12f*wave;5->1f+.12f*abs(sin(t*PI.toFloat()*2f))*(1f-t);else->1f+.15f*wave*(1f-t)}
                val angle=when(kind) {2,3,5->0f;4->sin(t*PI.toFloat()*2f)*(1f-t)*10f;else->sin(t*PI.toFloat()*4f)*(1f-t)*7f}
                val shift=-wave*2.5f*density
                withTransform({
                    translate(0f,shift)
                    rotate(angle,center);scale(scale,scale,center)
                }) {this@drawWithContent.drawContent()}
                if(kind==2 && t<1f) {
                    val color=Color(0xFFFF6899).copy(alpha=(1f-t).coerceIn(0f,1f))
                    for(i in 0 until 6) {
                        val a=(i*60f-90f)*PI.toFloat()/180f
                        val radius=(12f+24f*t)*density
                        val x=center.x+cos(a)*radius
                        val y=center.y+sin(a)*radius-8f*density*t
                        val r=(2.5f+1.5f*wave)*density
                        val path=Path().apply {
                            moveTo(x,y+r);cubicTo(x-r*2,y-r*.3f,x-r,y-r*2,x,y-r*.7f)
                            cubicTo(x+r,y-r*2,x+r*2,y-r*.3f,x,y+r);close()
                        }
                        drawPath(path,color)
                    }
                }
            }
    }
}
