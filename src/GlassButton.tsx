import { useRef, useState } from 'react';
import {
  Animated,
  Platform,
  Pressable,
  StyleSheet,
  type GestureResponderEvent,
  type LayoutChangeEvent,
  type ViewStyle,
} from 'react-native';

import { isLiquidGlassAvailable } from './availability';
import GlassView from './GlassView';
import type { GlassButtonProps } from './types';

const MIN_TOUCH = 44;

// Style keys that place the button among its siblings; they go on the outer Pressable.
// Everything else (padding, alignment, radius, border) styles the glass itself.
const OUTER_KEYS = new Set<string>([
  'margin', 'marginTop', 'marginBottom', 'marginLeft', 'marginRight', 'marginStart', 'marginEnd',
  'marginHorizontal', 'marginVertical', 'marginBlock', 'marginBlockStart', 'marginBlockEnd',
  'marginInline', 'marginInlineStart', 'marginInlineEnd',
  'position', 'top', 'bottom', 'left', 'right', 'start', 'end', 'inset', 'insetBlock',
  'insetBlockStart', 'insetBlockEnd', 'insetInline', 'insetInlineStart', 'insetInlineEnd',
  'flex', 'flexGrow', 'flexShrink', 'flexBasis', 'alignSelf', 'zIndex',
  'width', 'height', 'minWidth', 'minHeight', 'maxWidth', 'maxHeight', 'aspectRatio',
  'transform', 'opacity',
]);

const RADIUS_KEYS = [
  'borderRadius', 'borderTopLeftRadius', 'borderTopRightRadius', 'borderBottomLeftRadius',
  'borderBottomRightRadius', 'borderTopStartRadius', 'borderTopEndRadius', 'borderBottomStartRadius',
  'borderBottomEndRadius', 'borderStartStartRadius', 'borderStartEndRadius', 'borderEndStartRadius',
  'borderEndEndRadius',
] as const;

// iOS without Liquid Glass gets a small scale on press in place of the system response
const AnimatedGlassView = Animated.createAnimatedComponent(GlassView);
const FALLBACK_PRESS_SCALE = 1.04;

/**
 * A Pressable wrapping interactive glass. Pill-shaped by default (radius = half the height)
 * with a 44 × 44 minimum touch target.
 */
export default function GlassButton({
  glassEffectStyle,
  tintColor,
  colorScheme,
  style,
  children,
  disabled,
  accessibilityRole = 'button',
  accessibilityState,
  onLayout,
  onPressIn,
  onPressOut,
  ref,
  ...pressableProps
}: GlassButtonProps) {
  const [measuredHeight, setMeasuredHeight] = useState<number | undefined>(undefined);
  const scale = useRef(new Animated.Value(1)).current;
  const fallbackPress = Platform.OS === 'ios' && !isLiquidGlassAvailable();

  const flat: ViewStyle = StyleSheet.flatten(style) ?? {};
  const outer: Record<string, unknown> = {};
  const inner: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(flat)) {
    (OUTER_KEYS.has(key) ? outer : inner)[key] = value;
  }
  const hasRadius = RADIUS_KEYS.some((k) => flat[k] != null);
  const height = typeof flat.height === 'number' ? flat.height : measuredHeight;
  const pillRadius = (height ?? MIN_TOUCH) / 2;

  const spring = (toValue: number) => {
    Animated.spring(scale, { toValue, useNativeDriver: true, speed: 40, bounciness: 6 }).start();
  };

  const handleLayout = (e: LayoutChangeEvent) => {
    if (!hasRadius && typeof flat.height !== 'number') setMeasuredHeight(e.nativeEvent.layout.height);
    onLayout?.(e);
  };

  return (
    <Pressable
      {...pressableProps}
      ref={ref}
      disabled={disabled}
      accessibilityRole={accessibilityRole}
      accessibilityState={{ ...accessibilityState, disabled: !!disabled }}
      onLayout={handleLayout}
      onPressIn={(e: GestureResponderEvent) => {
        if (fallbackPress) spring(FALLBACK_PRESS_SCALE);
        onPressIn?.(e);
      }}
      onPressOut={(e: GestureResponderEvent) => {
        if (fallbackPress) spring(1);
        onPressOut?.(e);
      }}
      style={[styles.outer, outer as ViewStyle]}>
      <AnimatedGlassView
        glassEffectStyle={glassEffectStyle}
        tintColor={tintColor}
        colorScheme={colorScheme}
        isInteractive={!disabled}
        style={[
          styles.glass,
          hasRadius ? null : { borderRadius: pillRadius },
          inner as ViewStyle,
          fallbackPress ? { transform: [{ scale }] } : null,
        ]}>
        {children}
      </AnimatedGlassView>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  outer: {
    minWidth: MIN_TOUCH,
    minHeight: MIN_TOUCH,
  },
  glass: {
    flexGrow: 1,
    alignSelf: 'stretch',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
});
