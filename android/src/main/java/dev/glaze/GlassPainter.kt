package dev.glaze

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import dev.glaze.engine.GlassLensRenderer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Glass material for one style and scheme. `alpha` fades the whole material ('none' = 0). */
internal data class Material(
    val blurScale: Float,
    val dim: Float,
    val spec: Float,
    val adaptive: Boolean,
    val tint: Int,
    val alpha: Float,
    val dark: Boolean
) {
    fun lerp(to: Material, t: Float): Material = Material(
        blurScale = mix(blurScale, to.blurScale, t),
        dim = mix(dim, to.dim, t),
        spec = mix(spec, to.spec, t),
        adaptive = if (t < 0.5f) adaptive else to.adaptive,
        tint = mixArgb(tint, to.tint, t),
        alpha = mix(alpha, to.alpha, t),
        dark = if (t < 0.5f) dark else to.dark
    )

    companion object {
        fun of(style: String, dark: Boolean): Material = when (style) {
            "clear" -> Material(
                GlazeTuning.CLEAR_BLUR_SCALE,
                if (dark) GlazeTuning.CLEAR_DIM_DARK else GlazeTuning.CLEAR_DIM_LIGHT,
                GlazeTuning.CLEAR_SPEC,
                adaptive = false,
                tint = if (dark) GlazeTuning.CLEAR_TINT_DARK else GlazeTuning.CLEAR_TINT_LIGHT,
                alpha = 1f,
                dark = dark
            )
            else -> Material(
                GlazeTuning.REGULAR_BLUR_SCALE,
                0f,
                GlazeTuning.REGULAR_SPEC,
                // Light: per-pixel adaptive tint, like iOS. Dark: a fixed dark tint.
                adaptive = !dark,
                tint = if (dark) GlazeTuning.REGULAR_TINT_DARK else 0,
                alpha = if (style == "none") 0f else 1f,
                dark = dark
            )
        }

        private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

        fun mixArgb(a: Int, b: Int, t: Float): Int = Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        )
    }
}

/** Glass geometry in the drawing view's local pixels. A second shape merges via smooth-min. */
internal class GlassShape {
    val rect = RectF()
    /** TL, TR, BR, BL in px */
    val radii = FloatArray(4)
    val rect2 = RectF()
    var radius2 = 0f
    var hasSecond = false
    var blend = 0f
}

/** Press state the painter reads; written by the host's motion code. */
internal class GlassTouch {
    var press = 0f
    var x = 0f
    var y = 0f
    var bulge = true
}

/**
 * Renders one glass surface in the current tier. Backdrop-sampling tiers re-record in
 * [record] (called from onPreDraw); [draw] only references the recorded node.
 */
internal class GlassPainter(private val view: View) {

    var tier: GlazeTier = GlazeTier.TINT
        set(value) {
            if (field != value) {
                field = value
                release()
            }
        }

    private val density = view.resources.displayMetrics.density

    private var lens: GlassLensRenderer? = null
    private var blurNode: RenderNode? = null
    private var blurRadiusSet = -1f

    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = GlazeTuning.RIM_STROKE_DP * view.resources.displayMetrics.density
    }
    private val tmpRadii = FloatArray(8)
    private val viewLoc = IntArray(2)
    private val sourceLoc = IntArray(2)

    val samplesBackdrop: Boolean
        get() = tier == GlazeTier.LENS || tier == GlazeTier.BLUR

    /** Re-records the backdrop. Call from onPreDraw only: it never invalidates. */
    fun record(source: View, shape: GlassShape, material: Material, glassTint: Int, touch: GlassTouch) {
        if (material.alpha <= 0.001f || view.width <= 0 || view.height <= 0) return
        view.getLocationOnScreen(viewLoc)
        source.getLocationOnScreen(sourceLoc)
        val ox = (viewLoc[0] - sourceLoc[0]).toFloat()
        val oy = (viewLoc[1] - sourceLoc[1]).toFloat()
        when (tier) {
            GlazeTier.LENS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                recordLens(source, shape, material, glassTint, touch, ox, oy)
            }
            GlazeTier.BLUR -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                recordBlur(source, material, ox, oy)
            }
            else -> Unit
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun recordLens(
        source: View,
        shape: GlassShape,
        m: Material,
        glassTint: Int,
        touch: GlassTouch,
        ox: Float,
        oy: Float
    ) {
        val r = lens ?: GlassLensRenderer().also { lens = it }
        val p = lensParams(shape, m, glassTint, touch)
        val margin = marginFor(p.blurRadius)
        val ok = r.record(view.width, view.height, margin, p, ox, oy) { c ->
            GlazeRegistry.drawBackdrop(c, source)
        }
        r.setAlpha(m.alpha)
        if (!ok) {
            GlazeTiers.markLensBroken()
            tier = GlazeTier.BLUR
            view.invalidate()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun recordBlur(source: View, m: Material, ox: Float, oy: Float) {
        val node = blurNode ?: RenderNode("GlazeBlur").also { blurNode = it }
        val radius = quantize(GlazeTuning.BLUR_DP * density * 2f * m.blurScale, 0.5f).coerceAtLeast(1f)
        if (radius != blurRadiusSet) {
            val sat = ColorMatrix().apply { setSaturation(GlazeTuning.SATURATION / 100f) }
            node.setRenderEffect(
                RenderEffect.createColorFilterEffect(
                    ColorMatrixColorFilter(sat),
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                )
            )
            blurRadiusSet = radius
        }
        val margin = marginFor(radius)
        val w = view.width
        val h = view.height
        node.setPosition(-margin, -margin, w + margin, h + margin)
        val c = node.beginRecording(w + 2 * margin, h + 2 * margin)
        try {
            c.translate(margin - ox, margin - oy)
            GlazeRegistry.drawBackdrop(c, source)
        } finally {
            node.endRecording()
        }
        node.setAlpha(m.alpha)
    }

    fun draw(canvas: Canvas, shape: GlassShape, material: Material, glassTint: Int) {
        if (material.alpha <= 0.001f) return
        when (tier) {
            GlazeTier.LENS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) lens?.draw(canvas)
            GlazeTier.BLUR -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) drawBlur(canvas, shape, material, glassTint)
            GlazeTier.TINT -> drawFlat(
                canvas, shape, material,
                if (material.dark) GlazeTuning.TINT_TIER_FILL_DARK else GlazeTuning.TINT_TIER_FILL_LIGHT,
                glassTint,
                if (material.dark) GlazeTuning.BLUR_TIER_RIM_DARK else GlazeTuning.BLUR_TIER_RIM_LIGHT
            )
            GlazeTier.OPAQUE -> {
                val base = if (material.dark) GlazeTuning.OPAQUE_FILL_DARK else GlazeTuning.OPAQUE_FILL_LIGHT
                // Keep the hue of tinted glass, capped so contrast survives
                val mixed = Material.mixArgb(base, glassTint or 0xFF000000.toInt(), Color.alpha(glassTint) / 255f * 0.45f)
                drawFlat(
                    canvas, shape, material, mixed or 0xFF000000.toInt(), 0,
                    if (material.dark) GlazeTuning.OPAQUE_BORDER_DARK else GlazeTuning.OPAQUE_BORDER_LIGHT
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun drawBlur(canvas: Canvas, shape: GlassShape, m: Material, glassTint: Int) {
        val node = blurNode ?: return
        if (!node.hasDisplayList()) return
        buildPath(shape)
        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawRenderNode(node)
        val tint = if (m.dark) GlazeTuning.BLUR_TIER_TINT_DARK else GlazeTuning.BLUR_TIER_TINT_LIGHT
        fill.color = tint
        fill.alpha = (Color.alpha(tint) * m.alpha).toInt()
        canvas.drawPath(path, fill)
        if (Color.alpha(glassTint) > 0) {
            fill.color = glassTint
            fill.alpha = (Color.alpha(glassTint) * 0.6f * m.alpha).toInt()
            canvas.drawPath(path, fill)
        }
        canvas.restoreToCount(save)
        val rim = if (m.dark) GlazeTuning.BLUR_TIER_RIM_DARK else GlazeTuning.BLUR_TIER_RIM_LIGHT
        stroke.color = rim
        stroke.alpha = (Color.alpha(rim) * m.alpha).toInt()
        canvas.drawPath(path, stroke)
    }

    private fun drawFlat(canvas: Canvas, shape: GlassShape, m: Material, base: Int, glassTint: Int, rim: Int) {
        buildPath(shape)
        fill.color = base
        fill.alpha = (Color.alpha(base) * m.alpha).toInt()
        canvas.drawPath(path, fill)
        if (Color.alpha(glassTint) > 0) {
            fill.color = glassTint
            fill.alpha = (Color.alpha(glassTint) * 0.6f * m.alpha).toInt()
            canvas.drawPath(path, fill)
        }
        stroke.color = rim
        stroke.alpha = (Color.alpha(rim) * m.alpha).toInt()
        canvas.drawPath(path, stroke)
    }

    private fun buildPath(shape: GlassShape) {
        path.reset()
        val inset = stroke.strokeWidth / 2f
        for (i in 0 until 4) {
            tmpRadii[i * 2] = shape.radii[i]
            tmpRadii[i * 2 + 1] = shape.radii[i]
        }
        val r = shape.rect
        path.addRoundRect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset, tmpRadii, Path.Direction.CW)
        if (shape.hasSecond) {
            val r2 = shape.rect2
            path.addRoundRect(
                r2.left + inset, r2.top + inset, r2.right - inset, r2.bottom - inset,
                shape.radius2, shape.radius2, Path.Direction.CW
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun lensParams(shape: GlassShape, m: Material, glassTint: Int, t: GlassTouch): GlassLensRenderer.LensParams {
        val r = shape.rect
        val hw = (r.width() / 2f).coerceAtLeast(1f)
        val hh = (r.height() / 2f).coerceAtLeast(1f)
        val maxR = min(hw, hh)
        var minDim = 2f * min(hw, hh)
        val hw2 = if (shape.hasSecond) (shape.rect2.width() / 2f).coerceAtLeast(1f) else 0f
        val hh2 = if (shape.hasSecond) (shape.rect2.height() / 2f).coerceAtLeast(1f) else 0f
        if (shape.hasSecond) minDim = min(minDim, 2f * min(hw2, hh2))

        val refPx = GlazeTuning.ADAPTIVE_REF_DP * density
        val bevel = min(GlazeTuning.BEVEL_DP * density, minDim * GlazeTuning.ADAPTIVE_BEVEL_RATIO).coerceAtLeast(2f)
        val refractCap = GlazeTuning.ADAPTIVE_REFRACT_RATIO * minDim * min(1f, minDim / refPx)
        val refract = min(GlazeTuning.REFRACT_DP * density, refractCap)
        val rimBand = min(GlazeTuning.RIM_BAND_MAX_PX, minDim * GlazeTuning.ADAPTIVE_RIM_RATIO).coerceAtLeast(2f)
        val blur = quantize(GlazeTuning.BLUR_DP * density * m.blurScale, 0.5f)

        val press = quantize(t.press.coerceIn(0f, 1.2f), 0.01f)
        // Highlight swings toward the finger while pressed
        var lx = GlazeTuning.LIGHT_X
        var ly = GlazeTuning.LIGHT_Y
        if (press > 0f) {
            val dx = r.centerX() - t.x
            val dy = r.centerY() - t.y
            val len = sqrt(dx * dx + dy * dy)
            if (len > 1f) {
                val k = (press.coerceAtMost(1f)) * GlazeTuning.TOUCH_LIGHT_FOLLOW
                lx += (dx / len - lx) * k
                ly += (dy / len - ly) * k
                val n = sqrt(lx * lx + ly * ly).coerceAtLeast(0.0001f)
                lx = quantize(lx / n, 0.005f)
                ly = quantize(ly / n, 0.005f)
            }
        }
        val amp = if (t.bulge) press * GlazeTuning.TOUCH_BULGE else 0f

        return GlassLensRenderer.LensParams(
            blurRadius = blur,
            shape1CX = r.centerX(), shape1CY = r.centerY(), shape1HW = hw, shape1HH = hh,
            radius1TL = shape.radii[0].coerceIn(0f, maxR),
            radius1TR = shape.radii[1].coerceIn(0f, maxR),
            radius1BR = shape.radii[2].coerceIn(0f, maxR),
            radius1BL = shape.radii[3].coerceIn(0f, maxR),
            rimSoft = 0f,
            shape2CX = if (shape.hasSecond) shape.rect2.centerX() else 0f,
            shape2CY = if (shape.hasSecond) shape.rect2.centerY() else 0f,
            shape2HW = hw2, shape2HH = hh2,
            radius2 = if (shape.hasSecond) shape.radius2.coerceIn(0f, min(hw2, hh2)) else 0f,
            blendK = if (shape.hasSecond) shape.blend else 0f,
            bevel = bevel,
            refract = refract,
            falloff = GlazeTuning.REFRACT_FALLOFF,
            rimBandMax = rimBand,
            dispersion = GlazeTuning.DISPERSION,
            lightX = lx, lightY = ly,
            spec = m.spec,
            tint = if (m.adaptive) 0 else m.tint,
            adaptiveTint = m.adaptive,
            glassTint = glassTint,
            dim = m.dim,
            saturation = GlazeTuning.SATURATION,
            press = press,
            touchX = quantize(t.x, 0.5f), touchY = quantize(t.y, 0.5f), touchAmp = amp
        )
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) lens?.release()
        lens = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blurNode?.discardDisplayList()
        blurNode = null
        blurRadiusSet = -1f
    }

    private companion object {
        fun quantize(v: Float, step: Float): Float = (v / step).toInt() * step

        /** Recording margin so blur and refraction sample real content at the edges. */
        fun marginFor(blurRadius: Float): Int {
            val need = max(blurRadius * 3f, 32f)
            return (((need.toInt() + 15) / 16) * 16).coerceAtLeast(32)
        }
    }
}
