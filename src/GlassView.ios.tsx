import { BlurView, type BlurTint } from 'expo-blur';
import { GlassView as NativeGlassView } from 'expo-glass-effect';
import { StyleSheet, useColorScheme, View } from 'react-native';

import { hasNativeGlass } from './availability.ios';
import type { GlassViewProps } from './types';

/**
 * iOS 26+: Apple's Liquid Glass via expo-glass-effect, props forwarded untouched.
 * Below iOS 26, or when the app opts out of Liquid Glass: a system blur material, so the UI
 * still reads as glass instead of expo-glass-effect's plain View.
 */
export default function GlassView(props: GlassViewProps) {
  // Hooks run unconditionally; hasNativeGlass() is constant for the process
  const systemScheme = useColorScheme();
  if (hasNativeGlass()) {
    return <NativeGlassView {...props} />;
  }

  const { glassEffectStyle, tintColor, isInteractive: _isInteractive, colorScheme, style, children, ...rest } = props;
  const styleName = typeof glassEffectStyle === 'object' ? glassEffectStyle.style : (glassEffectStyle ?? 'regular');
  if (styleName === 'none') {
    return (
      <View style={style} {...rest}>
        {children}
      </View>
    );
  }

  const scheme = !colorScheme || colorScheme === 'auto' ? systemScheme : colorScheme;
  const dark = scheme === 'dark';
  const tint: BlurTint =
    styleName === 'clear'
      ? dark
        ? 'systemUltraThinMaterialDark'
        : 'systemUltraThinMaterialLight'
      : dark
        ? 'systemMaterialDark'
        : 'systemMaterialLight';

  return (
    <View style={[styles.clip, style]} {...rest}>
      <BlurView tint={tint} intensity={100} style={StyleSheet.absoluteFill} pointerEvents="none" />
      {tintColor != null ? (
        <View style={[StyleSheet.absoluteFill, { backgroundColor: tintColor, opacity: 0.6 }]} pointerEvents="none" />
      ) : null}
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  // The blur must follow the corner radius
  clip: { overflow: 'hidden' },
});
