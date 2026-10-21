package dev.glaze

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import dev.glaze.engine.GlassLensRenderer

/** Material quality tiers. Same API and motion in every tier; only the material changes. */
internal enum class GlazeTier { LENS, BLUR, TINT, OPAQUE }

internal object GlazeTiers {

    private const val TAG = "Glaze"

    @Volatile
    private var lensBroken = false
    private var base: GlazeTier? = null

    /** Chosen once per process from the OS version and device class, never by users. */
    fun base(context: Context): GlazeTier {
        base?.let { return it }
        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.isLowRamDevice == true
        val tier = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !lowRam && GlassLensRenderer.probe() -> GlazeTier.LENS
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlazeTier.BLUR
            else -> GlazeTier.TINT
        }
        base = tier
        return tier
    }

    fun current(context: Context, reduceTransparency: Boolean): GlazeTier {
        if (reduceTransparency) return GlazeTier.OPAQUE
        val b = base(context)
        return if (b == GlazeTier.LENS && lensBroken) GlazeTier.BLUR else b
    }

    /** A lens failed at draw time (driver issue): demote the whole process, log once. */
    fun markLensBroken() {
        if (lensBroken) return
        lensBroken = true
        Log.w(TAG, "Lens tier failed on this device; using blur tier for this process")
    }

    fun isLensActive(context: Context): Boolean = base(context) == GlazeTier.LENS && !lensBroken
}
