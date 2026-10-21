package dev.glaze

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import dev.glaze.engine.GlassAccessibility
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import kotlin.math.max

/**
 * Groups glass so neighbours merge when closer than `spacing`. v1 merges exactly two glass
 * views (smooth-min of two shapes in one lens); with more, each draws as separate glass.
 */
class GlazeContainerView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {

    private var spacingPx = 0f
    private val density = resources.displayMetrics.density
    private val painter = GlassPainter(this)
    private val shape = GlassShape()
    private val touch = GlassTouch()
    private var first: GlazeView? = null
    private var second: GlazeView? = null
    private var source: View? = null
    private var observer: ViewTreeObserver? = null
    private var visibleAggregated = true

    private val found = ArrayList<GlazeView>(4)
    private val myLoc = IntArray(2)
    private val childLoc = IntArray(2)
    private val rectA = RectF()
    private val rectB = RectF()

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        onFrame()
        true
    }

    init {
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(true)
    }

    fun setSpacing(dp: Double?) {
        spacingPx = ((dp ?: 0.0).toFloat() * density).coerceAtLeast(0f)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        painter.tier = GlazeTiers.current(context, GlassAccessibility.prefersReducedTransparency(context))
        updateListening()
    }

    override fun onDetachedFromWindow() {
        stopListening()
        unmerge()
        painter.release()
        source = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        visibleAggregated = isVisible
        updateListening()
    }

    private fun updateListening() {
        if (isAttachedToWindow && visibleAggregated) {
            if (observer == null) {
                observer = viewTreeObserver.also { it.addOnPreDrawListener(preDraw) }
            }
        } else {
            stopListening()
        }
    }

    private fun stopListening() {
        val vto = observer ?: return
        if (vto.isAlive) vto.removeOnPreDrawListener(preDraw)
        else viewTreeObserver.removeOnPreDrawListener(preDraw)
        observer = null
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Fabric positions the children
    }

    private fun collectGlass(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            when (val c = group.getChildAt(i)) {
                is GlazeView -> found.add(c)
                is GlazeContainerView -> Unit // nested containers handle their own
                is ViewGroup -> collectGlass(c)
            }
        }
    }

    private fun rectOf(v: View, out: RectF) {
        v.getLocationOnScreen(childLoc)
        val x = (childLoc[0] - myLoc[0]).toFloat()
        val y = (childLoc[1] - myLoc[1]).toFloat()
        out.set(x, y, x + v.width, y + v.height)
    }

    /** Pre-draw: decide whether the pair merges, then re-record the merged lens. */
    private fun onFrame() {
        found.clear()
        if (spacingPx > 0f) collectGlass(this)
        val a = found.getOrNull(0)
        val b = found.getOrNull(1)
        var merge = found.size == 2 && a != null && b != null &&
            a.material.alpha > 0.001f && b.material.alpha > 0.001f
        if (merge && a != null && b != null) {
            getLocationOnScreen(myLoc)
            rectOf(a, rectA)
            rectOf(b, rectB)
            val gapX = max(0f, max(rectA.left - rectB.right, rectB.left - rectA.right))
            val gapY = max(0f, max(rectA.top - rectB.bottom, rectB.top - rectA.bottom))
            merge = max(gapX, gapY) < spacingPx
        }
        if (!merge || a == null || b == null) {
            unmerge()
            return
        }
        if (first !== a || second !== b) {
            unmerge()
            first = a
            second = b
            a.mergedInto = this
            b.mergedInto = this
            invalidate()
        }

        a.updateShape()
        b.updateShape()
        val oldRect = RectF(shape.rect)
        val oldRect2 = RectF(shape.rect2)
        shape.rect.set(rectA)
        System.arraycopy(a.shape.radii, 0, shape.radii, 0, 4)
        shape.rect2.set(rectB)
        shape.radius2 = b.shape.radii.average().toFloat()
        shape.hasSecond = true
        shape.blend = spacingPx

        // Touch from whichever glass is pressed harder, in our coordinates
        val pressed = if (a.touch.press >= b.touch.press) a else b
        val pr = if (pressed === a) rectA else rectB
        touch.press = pressed.touch.press
        touch.x = pr.left + pressed.touch.x
        touch.y = pr.top + pressed.touch.y
        touch.bulge = pressed.touch.bulge

        if (painter.samplesBackdrop) {
            val src = source?.takeIf { it.isAttachedToWindow } ?: resolveSource().also { source = it } ?: return
            painter.record(src, shape, a.material, a.glassTint, touch)
        } else if (oldRect != shape.rect || oldRect2 != shape.rect2) {
            // Flat tiers draw straight into our display list, so geometry changes need a redraw
            invalidate()
        }
    }

    private fun unmerge() {
        first?.mergedInto = null
        second?.mergedInto = null
        if (first != null) invalidate()
        first = null
        second = null
    }

    private fun resolveSource(): View? {
        var p = parent
        var reactRoot: View? = null
        while (p is View) {
            if (p.javaClass.name == "com.swmansion.rnscreens.Screen") return p
            if (reactRoot == null && p is com.facebook.react.uimanager.RootView) reactRoot = p
            p = p.parent
        }
        return reactRoot ?: rootView.findViewById<View>(android.R.id.content) ?: rootView
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Never draw our lens into a backdrop being recorded (glass never refracts glass)
        val a = first
        if (a != null && second != null && GlazeRegistry.capturing == 0) {
            painter.draw(canvas, shape, a.material, a.glassTint)
        }
        super.dispatchDraw(canvas)
    }
}
