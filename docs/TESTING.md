# Device testing checklist

CI covers types, lint, Jest and the Android compile. Everything below needs a real device or
simulator. Use release builds for performance numbers (`npx expo run:android --variant release`).

The `example/` app has the screens: Scroll (default tab), Animated, Press, and the header pair
inside a `GlassContainer`.

## Both platforms

- [ ] Glass renders on the Scroll tab over the photo list; the tab bar and header buttons look like glass.
- [ ] Header "regular/clear" button switches the style with an animation; "hide" fades to `'none'` and back.
- [ ] Press tab: `onPress`, `onLongPress`, `GestureDetector` tap and Pressable-around-GlassView each log on
      every tap (try ~50 taps each).
- [ ] Press then scroll: the press releases visually and `onPress` does not fire.
- [ ] Disabled button: no press response, never logs.
- [ ] Animated tab: the glass moves and resizes; refraction follows it with no lag or freeze.
- [ ] Dark mode: material switches with `colorScheme="auto"`.

## iOS

- [ ] iOS 26 simulator: Apple's Liquid Glass (`isLiquidGlassAvailable: true` in the header).
- [ ] iOS 18 simulator: blur fallback; buttons scale slightly on press.

## Android

- [ ] API 33+ (`isLiquidGlassAvailable: true`): lens with refraction at the edges; press grows, stretches
      toward the drag, bulges under the finger, springs back.
- [ ] Labels on glass never show up inside their own refraction.
- [ ] A glass button on the glass tab bar does not show the tab bar's glass in its refraction.
- [ ] Header pair merges into one shape (GlassContainer, spacing 12).
- [ ] **Static screen costs nothing:** leave the Scroll tab untouched, then
      `adb shell dumpsys gfxinfo dev.glaze.example reset`, wait 10 s,
      `adb shell dumpsys gfxinfo dev.glaze.example | grep "Total frames"` → expect 0.
      If it's not 0, something is invalidating every frame. Check that nothing else on screen animates first.
- [ ] Scroll jank: fling for 10 s, then `adb shell dumpsys gfxinfo dev.glaze.example` →
      "Janky frames" ≤ 1% (Snapdragon 695-class device and a flagship).
- [ ] API 31–32 emulator: blur tier (no refraction), press still scales.
- [ ] API 29 emulator: tint tier (translucent fill and rim).
- [ ] Settings → Accessibility → high-contrast text: glass turns opaque after returning to the app.
- [ ] Developer options → Remove animations, or battery saver: press is a short fade, no scale.
- [ ] Navigate away and back for 5 minutes: memory stays flat (Android Studio profiler).

## Tuning

Every motion and material constant is in `android/src/main/java/dev/glaze/GlazeTuning.kt`. Compare
press, stretch and release against an iOS 26 device side by side and adjust there.
