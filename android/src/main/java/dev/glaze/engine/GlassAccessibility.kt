/*
 * Vendored from QWEA0/Liquid-Glass-Android v2.0.11 (MIT, see THIRD_PARTY_NOTICES.md).
 * Changes: package renamed, the unused mode enum dropped.
 *
 * Closest Android signals for iOS's Reduce Transparency / Reduce Motion:
 * high-contrast text, "remove animations", and battery saver.
 */
package dev.glaze.engine

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

internal object GlassAccessibility {

    fun prefersReducedTransparency(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun prefersReducedMotion(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return !ValueAnimator.areAnimatorsEnabled()
        return try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }

    fun isPowerSaveMode(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isPowerSaveMode
    }
}
