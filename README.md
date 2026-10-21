# react-native-glaze

[![CI](https://github.com/24samj/react-native-glaze/actions/workflows/ci.yml/badge.svg)](https://github.com/24samj/react-native-glaze/actions/workflows/ci.yml)

One liquid glass API for React Native on iOS and Android.

- **iOS 26+**: Apple's native Liquid Glass via [`expo-glass-effect`](https://docs.expo.dev/versions/latest/sdk/glass-effect/).
- **iOS < 26** (or Liquid Glass opted out): a system blur material via `expo-blur`, so it still reads as glass.
- **Android 13+**: a GPU refraction lens (AGSL shader over a `RenderNode`-recorded backdrop, adapted from
  [QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android)), with press, stretch, bulge and
  highlight motion owned natively.
- **Android 12**: GPU blur + tint + rim. **Android 7–11**: translucent tint + rim.

You choose placement, style and tint. Motion is fixed by the library, as it is on iOS. The API mirrors
`expo-glass-effect` prop for prop, so switching between the two is a find-and-replace on the import.

New Architecture only. Expo SDK 57 and 58. Needs a development build (not Expo Go).

## Install

From GitHub (no npm release yet):

```sh
npm install --allow-git=all github:24samj/react-native-glaze   # npm 12 blocks git deps without the flag
# or: bun add github:24samj/react-native-glaze
npx expo install expo-glass-effect expo-blur
npx expo prebuild --clean
npx expo run:android   # and/or: npx expo run:ios
```

`expo-glass-effect` and `expo-blur` are peer dependencies; `npx expo install` picks the versions that match your SDK.
No config plugin is needed.

## Usage

```tsx
import Animated from 'react-native-reanimated';
import { GlassView, GlassButton, GlassContainer } from 'react-native-glaze';

const AnimatedGlassView = Animated.createAnimatedComponent(GlassView);

<GlassView style={{ borderRadius: 24, padding: 16 }} glassEffectStyle="regular">
  <Text>Hello</Text>
</GlassView>

<GlassContainer spacing={8} style={{ flexDirection: 'row', gap: 6 }}>
  <GlassButton onPress={save}><Icon name="check" /></GlassButton>
  <GlassButton glassEffectStyle="clear" onPress={close}><Icon name="x" /></GlassButton>
</GlassContainer>

<AnimatedGlassView style={[styles.sheet, animatedStyle]} />
```

## API

### `GlassView`

| Prop | Type | Default | |
| --- | --- | --- | --- |
| `glassEffectStyle` | `'regular' \| 'clear' \| 'none' \| { style, animate?, animationDuration? }` | `'regular'` | `animationDuration` is in seconds |
| `tintColor` | `ColorValue` | none | The colour's alpha sets the strength |
| `isInteractive` | `boolean` | `false` | Native press response. Never takes taps away from JS handlers |
| `colorScheme` | `'auto' \| 'light' \| 'dark'` | `'auto'` | |
| ...`ViewProps` | | | `ref` is forwarded to the native view |

The corner radius comes from `style` (`borderRadius` and the per-corner variants). Children render on top of the
glass and are never refracted.

### `GlassButton`

A `Pressable` around an interactive `GlassView`. It takes `Pressable` props plus `glassEffectStyle`, `tintColor`,
`colorScheme`, `style` and `children`.

- It's a pill by default (radius = half the height) unless `style` sets a radius. The minimum touch target is 44 × 44.
- `accessibilityRole="button"`; `disabled` sets `accessibilityState.disabled` and turns the press response off.
- Layout keys in `style` (margins, position, flex, size, transform, opacity) go on the `Pressable`. The rest
  (padding, alignment, radius, border) style the glass.

### `GlassContainer`

`spacing?: number`. Glass children closer than `spacing` merge. Android v1 merges exactly two glass children;
with three or more, each one draws as separate glass.

### Helpers

| | iOS | Android |
| --- | --- | --- |
| `isLiquidGlassAvailable()` | Apple's Liquid Glass is in use | The full lens tier is active (API 33+, not demoted) |
| `isGlassEffectAPIAvailable()` | The glass API exists at runtime | Always `true` |

## Things to know

- **Fading glass.** Don't put `opacity` below 1 on glass or its parents (Apple's advice; on Android it also forces
  an offscreen layer). To fade, animate a wrapper's opacity and switch `glassEffectStyle` to `'none'` near zero.
- **Press growth is drawn outside the bounds.** A parent with `overflow: 'hidden'` clips it.
- **`borderRadius` is static on Android**: it's read from `style` each render, so an animated radius doesn't reach native.
- **Android `Modal`.** A modal is a separate window, so its glass only sees the modal's own content, not the app behind it.
- **Android video and maps.** Content drawn on a `SurfaceView` isn't in the display list. Use a `TextureView`
  surface (for example `expo-video`'s texture option) for video behind glass.
- **Android `WebView` behind glass**: give the WebView a hardware layer (`androidLayerType="hardware"`).
- **Glass never refracts glass**, matching iOS.
- **Accessibility.** High-contrast text on Android (Reduce Transparency on iOS) switches to an opaque material.
  "Remove animations" or battery saver turns the press into a short highlight fade. Text contrast on glass is the
  app's job; aim for 4.5:1 against the regular material in both schemes.

## How Android stays cheap

- **Idle costs nothing.** Glass re-records its backdrop in `onPreDraw`, only on frames the window is already drawing.
  It never invalidates itself to do this, so a static screen produces no frames. (Invalidating in `onPreDraw` *would*
  schedule another frame, because `ViewRootImpl` clears `mWillDrawSoon` before dispatching pre-draw listeners.)
- **No pixel copies.** The backdrop is a `RenderNode` recording: other views go in by reference to their existing
  display lists. Only views on the path to a glass view are re-walked.
- **Motion runs on the UI thread.** Springs come from `androidx.dynamicanimation`, applied as a canvas transform in
  `dispatchDraw`. The view's own `scale`/`translation` stay free for Reanimated, so `createAnimatedComponent`
  composes with the press motion.
- **Automatic suspension.** Glass stops when hidden, detached, in an inactive tab, or under a pushed screen.

All motion and material constants live in `android/src/main/java/dev/glaze/GlazeTuning.kt`.

## Example app

`example/` is an Expo SDK 57 app with a 200-photo `FlatList`, a glass tab bar, merging header buttons, an animated
glass view, and press tests (Pressable, `GestureDetector`, long press, disabled).

```sh
cd example
npm install
npx expo run:android   # or run:ios
```

## Development

```sh
npm install
npm test          # Jest, iOS + Android projects
npm run typecheck
npm run lint
npm run build     # emits build/ (committed so GitHub installs work; CI checks it's current)
```

Device testing checklist: [`docs/TESTING.md`](docs/TESTING.md). See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the
repo layout, the rules that keep Android cheap, and how releases work.

## License

MIT. Includes code adapted from QWEA0/Liquid-Glass-Android (MIT); see `THIRD_PARTY_NOTICES.md`.
