# Changelog

## 0.1.0 (unreleased)

First version: `GlassView`, `GlassButton`, `GlassContainer`, `isLiquidGlassAvailable`,
`isGlassEffectAPIAvailable`. Props mirror `expo-glass-effect`.

- iOS 26+: Apple's Liquid Glass via `expo-glass-effect`; iOS below 26: `expo-blur` system material.
- Android: lens (API 33+), blur (31–32), tint (24–30) and opaque (high-contrast text) tiers, with native
  press, stretch, bulge and highlight motion.

### Known limitations

- Android `GlassContainer` merges exactly two glass children.
- Android `borderRadius` is static (read from `style` each render).
- Android glass inside an RN `Modal` only sees the modal's own window.
- Not yet measured on reference devices; see `docs/TESTING.md`.
