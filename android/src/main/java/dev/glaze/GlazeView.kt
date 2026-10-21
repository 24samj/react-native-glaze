package dev.glaze

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import dev.glaze.engine.GlassAccessibility
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import kotlin.math.abs

/**
 * The Android glass host. Draws the glass under its React children, observes touches for the
 * native press motion without consuming them from JS, and re-records its backdrop only on
 * frames the window is already drawing.
 */
class GlazeView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {

    // —— Props (applied in commitProps) ——
    private var styleName = "regular"
    private var animateStyle = false
    private var styleDurationMs = GlazeTuning.STYLE_ANIMATION_MS
    internal var glassTint = 0
        private set
    private var interactive = false
    private var scheme = "auto"
    private val radiiDp = FloatArray(4)

    private val density = resources.displayMetrics.density
    private val painter = GlassPainter(this)
    internal val shape = GlassShape()
    internal val touch = GlassTouch()

    internal var material = Material.of("regular", false)
        private set
    private var styleAnimator: ValueAnimator? = null

    /** Set by a GlassContainer that draws this glass merged with a neighbour. */
    internal var mergedInto: GlazeContainerView? = null
        set(value) {
            if (field !== value) {
                field = value
                updateListening()
                invalidate()
            }
        }

    private var source: View? = null
    private var visibleAggregated = true
    private var observer: ViewTreeObserver? = null
    private var reduceMotion = false
    private var reduceTransparency = false

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        recordBackdrop()
        true
    }

    // —— Motion ——
    private val pressHolder = FloatValueHolder(0f)
    private val pressSpring = SpringAnimation(pressHolder).apply {
        spring = SpringForce(0f)
        setMinimumVisibleChange(1f / 500f)
        addUpdateListener { _, _, _ -> onMotionFrame() }
    }
    private val stretchXHolder = FloatValueHolder(0f)
    private val stretchYHolder = FloatValueHolder(0f)
    private val stretchX = stretchSpring(stretchXHolder)
    private val stretchY = stretchSpring(stretchYHolder)
    private var fadeAnimator: ValueAnimator? = null
    private var tracking = false
    private var downX = 0f
    private var downY = 0f

    init {
        // Press growth draws past the bounds; don't clip it here
        clipToPadding = false
        clipChildren = false
        setWillNotDraw(true)
    }

    private fun stretchSpring(holder: FloatValueHolder) = SpringAnimation(holder).apply {
        spring = SpringForce(0f).apply {
            stiffness = GlazeTuning.STRETCH_STIFFNESS
            dampingRatio = GlazeTuning.STRETCH_DAMPING
        }
        setMinimumVisibleChange(1f / 500f)
        addUpdateListener { _, _, _ -> onMotionFrame() }
    }

    // —— Prop setters ——

    fun setGlassStyle(value: String) {
        styleName = if (value == "clear" || value == "none") value else "regular"
    }

    fun setStyleAnimation(animate: Boolean, durationSeconds: Double?) {
        animateStyle = animate
        styleDurationMs = durationSeconds?.let { (it * 1000).toLong().coerceAtLeast(0L) }
            ?: GlazeTuning.STYLE_ANIMATION_MS
    }

    fun setGlassTint(color: Int) {
        glassTint = color
    }

    fun setInteractive(value: Boolean) {
        if (interactive && !value) release()
        interactive = value
    }

    fun setColorScheme(value: String) {
        scheme = value
    }

    fun setCornerRadii(dp: List<Double>?) {
        for (i in 0 until 4) radiiDp[i] = dp?.getOrNull(i)?.toFloat()?.coerceAtLeast(0f) ?: 0f
    }

    /** Called once after each batch of prop updates. */
    fun commitProps() {
        applyMaterial(animateStyle)
        updateListening()
        invalidate()
    }

    // —— Material ——

    private fun isDark(): Boolean = when (scheme) {
        "dark" -> true
        "light" -> false
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyMaterial(animate: Boolean) {
        val target = Material.of(styleName, isDark())
        if (target == material && styleAnimator == null) return
        styleAnimator?.cancel()
        styleAnimator = null
        if (!animate || styleDurationMs == 0L || !isAttachedToWindow) {
            material = target
            return
        }
        val from = material
        styleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = styleDurationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                material = from.lerp(target, it.animatedValue as Float)
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (styleAnimator === animation) {
                        material = target
                        styleAnimator = null
                        updateListening()
                        invalidate()
                    }
                }
            })
            start()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (scheme == "auto") {
            applyMaterial(false)
            invalidate()
        }
    }

    // —— Lifecycle and repaint policy ——

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        GlazeRegistry.add(this)
        refreshEnvironment()
        source = resolveSource()
        applyMaterial(false)
        updateListening()
    }

    /** Accessibility settings and battery saver can change while the app is in the background. */
    private fun refreshEnvironment() {
        reduceTransparency = GlassAccessibility.prefersReducedTransparency(context)
        reduceMotion = GlassAccessibility.prefersReducedMotion(context) || GlassAccessibility.isPowerSaveMode(context)
        painter.tier = GlazeTiers.current(context, reduceTransparency)
    }

    override fun onDetachedFromWindow() {
        stopListening()
        GlazeRegistry.remove(this)
        cancelMotion()
        styleAnimator?.cancel()
        styleAnimator = null
        painter.release()
        source = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        visibleAggregated = isVisible
        updateListening()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) return
        // Returning from Settings or a dialog: settings may have changed, and the source may have moved
        val tier = painter.tier
        refreshEnvironment()
        source = resolveSource()
        if (tier != painter.tier) {
            updateListening()
            invalidate()
        }
    }

    private fun shouldListen(): Boolean =
        isAttachedToWindow && visibleAggregated && mergedInto == null && painter.samplesBackdrop &&
            (material.alpha > 0.001f || styleAnimator != null)

    private fun updateListening() {
        if (shouldListen()) startListening() else stopListening()
    }

    private fun startListening() {
        if (observer != null) return
        val vto = viewTreeObserver
        vto.addOnPreDrawListener(preDraw)
        observer = vto
    }

    private fun stopListening() {
        val vto = observer ?: return
        if (vto.isAlive) vto.removeOnPreDrawListener(preDraw)
        else viewTreeObserver.removeOnPreDrawListener(preDraw)
        observer = null
    }

    /**
     * Runs on every frame the window draws, before drawing. Re-recording the lens node here
     * adds no frame of its own: nothing is invalidated, the new display list is picked up by
     * the frame already in progress. (Invalidating here would schedule a new traversal,
     * since ViewRootImpl clears mWillDrawSoon before dispatching onPreDraw.)
     */
    private fun recordBackdrop() {
        if (width <= 0 || height <= 0) return
        val src = source?.takeIf { it.isAttachedToWindow } ?: resolveSource().also { source = it } ?: return
        updateShape()
        painter.record(src, shape, material, glassTint, touch)
    }

    /**
     * The view the glass samples, found by walking up: the nearest react-native-screens
     * Screen, else the React root, else the window content. Users never pass it.
     */
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

    // —— Drawing ——

    internal fun updateShape() {
        shape.rect.set(0f, 0f, width.toFloat(), height.toFloat())
        for (i in 0 until 4) shape.radii[i] = radiiDp[i] * density
        shape.hasSecond = false
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Fabric positions the children; don't let LinearLayout move them
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        applyMotionTransform(canvas)
        if (mergedInto == null) {
            updateShape()
            painter.draw(canvas, shape, material, glassTint)
        }
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    /** Press scale and elastic stretch as a canvas transform: the view's own transform stays Reanimated's. */
    private fun applyMotionTransform(canvas: Canvas) {
        if (reduceMotion) return
        val p = pressHolder.value
        val sx = stretchXHolder.value
        val sy = stretchYHolder.value
        if (p == 0f && sx == 0f && sy == 0f) return
        val scale = 1f + (GlazeTuning.PRESS_SCALE - 1f) * p
        val stretch = if (painter.tier == GlazeTier.OPAQUE) 0f else GlazeTuning.STRETCH_MAX
        val ax = abs(sx)
        val ay = abs(sy)
        val scaleX = scale * (1f + stretch * ax - stretch * 0.5f * ay)
        val scaleY = scale * (1f + stretch * ay - stretch * 0.5f * ax)
        if (stretch > 0f) {
            val shift = GlazeTuning.STRETCH_SHIFT_DP * density
            canvas.translate(sx * shift, sy * shift)
        }
        canvas.scale(scaleX, scaleY, width / 2f, height / 2f)
    }

    // —— Touch: observe, never consume from JS ——

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (interactive && isEnabled) observeTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    // Like RN's own views: be the native touch target when interactive so we keep seeing the
    // stream. JS still receives every event through the root view.
    // No performClick: taps belong to JS (Pressable, Gesture Handler), which handle accessibility
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = interactive || super.onTouchEvent(event)

    private fun observeTouch(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = true
                downX = ev.x
                downY = ev.y
                touch.x = ev.x
                touch.y = ev.y
                animatePress(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return
                touch.x = ev.x
                touch.y = ev.y
                val slop = GlazeTuning.TOUCH_SLOP_DP * density
                if (ev.x < -slop || ev.y < -slop || ev.x > width + slop || ev.y > height + slop) {
                    release()
                    return
                }
                if (!reduceMotion && width > 0 && height > 0) {
                    stretchX.animateToFinalPosition(((ev.x - downX) / width).coerceIn(-1f, 1f))
                    stretchY.animateToFinalPosition(((ev.y - downY) / height).coerceIn(-1f, 1f))
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release()
        }
    }

    private fun release() {
        if (!tracking && pressHolder.value == 0f) return
        tracking = false
        animatePress(false)
        stretchX.animateToFinalPosition(0f)
        stretchY.animateToFinalPosition(0f)
    }

    private fun animatePress(pressed: Boolean) {
        val target = if (pressed) 1f else 0f
        touch.bulge = !reduceMotion
        if (reduceMotion) {
            // Reduce motion / battery saver: a short highlight fade, no scale or bulge
            pressSpring.cancel()
            fadeAnimator?.cancel()
            fadeAnimator = ValueAnimator.ofFloat(pressHolder.value, target).apply {
                duration = GlazeTuning.REDUCED_MOTION_FADE_MS
                addUpdateListener {
                    pressHolder.value = it.animatedValue as Float
                    onMotionFrame()
                }
                start()
            }
            return
        }
        fadeAnimator?.cancel()
        pressSpring.spring.apply {
            stiffness = if (pressed) GlazeTuning.PRESS_STIFFNESS else GlazeTuning.RELEASE_STIFFNESS
            dampingRatio = if (pressed) GlazeTuning.PRESS_DAMPING else GlazeTuning.RELEASE_DAMPING
        }
        pressSpring.animateToFinalPosition(target)
    }

    private fun onMotionFrame() {
        touch.press = pressHolder.value
        invalidate()
        mergedInto?.invalidate()
    }

    private fun cancelMotion() {
        tracking = false
        pressSpring.cancel()
        stretchX.cancel()
        stretchY.cancel()
        fadeAnimator?.cancel()
        pressHolder.value = 0f
        stretchXHolder.value = 0f
        stretchYHolder.value = 0f
        touch.press = 0f
    }
}
