/* GPL-3.0-or-later. Page-local AndroidLiquidGlass paint behind native content. */
package dev.nino.squareglass

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.nino.squareglass.material.shapes.ContinuousCapsule
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import dev.nino.squareglass.material.isGlassAllowed

object PageGlass {
    private val installed=WeakHashMap<View,WeakReference<ComposeView>>()
    @JvmStatic fun hasSurface(target: View): Boolean = installed[target]?.get()!=null
    @JvmStatic @JvmOverloads fun install(target: View,radius: Float,emphasized: Boolean = false) {
        if(installed[target]?.get()!=null || SquareSettings.themeEnabled()==0)return
        val parent=target.parent
        val paint=ComposeView(target.context)
        (paint as View).isClickable=false
        (paint as View).isFocusable=false
        (paint as View).importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        paint.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        // SearchView owns its children by index. Paint a constrained sibling instead.
        if(parent is ConstraintLayout && target.id!=View.NO_ID) {
            val p=ConstraintLayout.LayoutParams(0,0)
            p.startToStart=target.id;p.endToEnd=target.id;p.topToTop=target.id;p.bottomToBottom=target.id
            (parent as ViewGroup).addView(paint,0,p)
            // A sibling also supports native LinearLayout buttons without adding
            // a child that would change their icon/text measurement.
            (paint as View).visibility=target.visibility
            (paint as View).alpha=target.alpha
            val sync = ViewTreeObserver.OnPreDrawListener {
                val visibility=target.visibility
                val alpha=if(target.isEnabled) target.alpha else target.alpha * .45f
                if((paint as View).visibility!=visibility)(paint as View).visibility=visibility
                if((paint as View).alpha!=alpha)(paint as View).alpha=alpha
                true
            }
            target.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { v.viewTreeObserver.addOnPreDrawListener(sync) }
                override fun onViewDetachedFromWindow(v: View) {
                    if(v.viewTreeObserver.isAlive)v.viewTreeObserver.removeOnPreDrawListener(sync)
                }
            })
            if(target.isAttachedToWindow)target.viewTreeObserver.addOnPreDrawListener(sync)
            target.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        } else if(target is ConstraintLayout) {
            val p=ConstraintLayout.LayoutParams(0,0)
            p.startToStart=0;p.endToEnd=0;p.topToTop=0;p.bottomToBottom=0
            (target as ViewGroup).addView(paint,0,p)
        } else if(target is FrameLayout) {
            // The native children determine wrap-content size; paint contributes no height.
            (target as ViewGroup).addView(paint,0,FrameLayout.LayoutParams(0,0))
            (target as View).addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ ->
                val width=(target as View).width;val height=(target as View).height
                (paint as View).measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
                (paint as View).layout(0,0,width,height)
            }
        } else return
        target.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        installed[target]=WeakReference(paint)
        paint.setContent { Surface(radius,emphasized) }
    }
    @Composable private fun Surface(radius: Float,emphasized: Boolean) {
        DesignState.revision
        val shape = if (radius >= 24f) ContinuousCapsule() else RoundedCornerShape(radius.dp)
        val modifier=if(SquareSettings.preset()!=2 && isGlassAllowed()) {
            GlassPanels.panel(Modifier.fillMaxSize(),radius,emphasized)
        } else {
            Modifier.fillMaxSize().clip(shape).background(Color(SquarePalette.surface(SquarePalette.isDark())))
        }
        Box(modifier)
    }
}
