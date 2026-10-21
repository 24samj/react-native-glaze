package dev.glaze

/**
 * Every motion and material constant in one place.
 *
 * Rule: change these only with a side-by-side video against iOS 26 in the PR.
 * No device-specific values; tiers change what runs, not how it feels.
 * Sizes are in dp unless the name says px.
 */
internal object GlazeTuning {

    // —— Material (lens tier). Upstream QWEA0 defaults converted from px at ~3x density. ——
    // Matched against iOS 26 control-centre tiles and tab bar, regular style.
    const val BLUR_DP = 4.5f
    const val BEVEL_DP = 16f
    const val REFRACT_DP = 53f
    const val REFRACT_FALLOFF = 2f
    const val DISPERSION = 0.10f
    const val SATURATION = 140f
    const val RIM_BAND_MAX_PX = 6f

    // Small controls: clamp bevel/refraction to the short side so a 44dp button isn't all edge
    const val ADAPTIVE_REF_DP = 110f
    const val ADAPTIVE_BEVEL_RATIO = 0.3f
    const val ADAPTIVE_REFRACT_RATIO = 0.7f
    const val ADAPTIVE_RIM_RATIO = 0.05f

    // Light comes from the top-left, as in iOS 26
    const val LIGHT_X = 0.866f
    const val LIGHT_Y = 0.5f

    // —— Style presets ——
    // Regular favours legibility: full blur, adaptive tint. Clear favours transparency.
    const val REGULAR_BLUR_SCALE = 1.0f
    const val CLEAR_BLUR_SCALE = 0.35f
    const val CLEAR_DIM_LIGHT = 0.16f
    const val CLEAR_DIM_DARK = 0.28f
    const val REGULAR_SPEC = 1.0f
    const val CLEAR_SPEC = 1.2f
    const val REGULAR_TINT_DARK = 0x3D1C1C1E
    const val CLEAR_TINT_LIGHT = 0x14FFFFFF
    const val CLEAR_TINT_DARK = 0x14000000

    /** Default style transition when `animate` is set without a duration (iOS uses ~0.35s). */
    const val STYLE_ANIMATION_MS = 350L

    // —— Blur tier (API 31–32) ——
    const val BLUR_TIER_TINT_LIGHT = 0x40FFFFFF
    const val BLUR_TIER_TINT_DARK = 0x4D1C1C1E
    const val BLUR_TIER_RIM_LIGHT = 0x66FFFFFF
    const val BLUR_TIER_RIM_DARK = 0x33FFFFFF

    // —— Tint tier (API 24–30): no backdrop sampling ——
    const val TINT_TIER_FILL_LIGHT = 0xB8F2F2F7.toInt()
    const val TINT_TIER_FILL_DARK = 0xB82C2C2E.toInt()

    // —— Opaque (reduce transparency) ——
    const val OPAQUE_FILL_LIGHT = 0xFFF2F2F6.toInt()
    const val OPAQUE_FILL_DARK = 0xFF2A2A2E.toInt()
    const val OPAQUE_BORDER_LIGHT = 0x33000000
    const val OPAQUE_BORDER_DARK = 0x40FFFFFF
    const val RIM_STROKE_DP = 0.75f

    // —— Press motion. iOS interactive glass grows on press, so the target is above 1. ——
    const val PRESS_SCALE = 1.06f
    const val PRESS_STIFFNESS = 700f
    const val PRESS_DAMPING = 0.75f
    const val RELEASE_STIFFNESS = 380f
    const val RELEASE_DAMPING = 0.55f

    // —— Elastic stretch while dragging (fraction of size, capped) ——
    const val STRETCH_MAX = 0.08f
    const val STRETCH_SHIFT_DP = 6f
    const val STRETCH_STIFFNESS = 520f
    const val STRETCH_DAMPING = 0.6f

    /** Moving this far outside the bounds releases visually, like Pressable's cancel. */
    const val TOUCH_SLOP_DP = 8f

    // —— Lens response under the finger ——
    const val TOUCH_BULGE = 1.0f
    /** How far the highlight swings toward the finger at full press (0–1). */
    const val TOUCH_LIGHT_FOLLOW = 0.8f

    /** Reduce motion / battery saver: press becomes a short highlight fade only. */
    const val REDUCED_MOTION_FADE_MS = 120L
}
