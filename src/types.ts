import type { ReactNode, Ref } from 'react';
import type { ColorValue, PressableProps, StyleProp, View, ViewProps, ViewStyle } from 'react-native';

export type GlassStyle = 'regular' | 'clear' | 'none';

export type GlassColorScheme = 'auto' | 'light' | 'dark';

export type GlassEffectStyleConfig = {
  style: GlassStyle;
  /** Animate the change to this style. Default `false`. */
  animate?: boolean;
  /** Seconds. Platform default if omitted. */
  animationDuration?: number;
};

export type GlassViewProps = ViewProps & {
  /** Default `'regular'`. */
  glassEffectStyle?: GlassStyle | GlassEffectStyleConfig;
  tintColor?: ColorValue;
  /** Turns on the native press response. Never takes taps away from JS handlers. Default `false`. */
  isInteractive?: boolean;
  /** Default `'auto'`. */
  colorScheme?: GlassColorScheme;
  ref?: Ref<View>;
};

export type GlassButtonProps = Omit<PressableProps, 'style' | 'children'> & {
  glassEffectStyle?: GlassStyle | GlassEffectStyleConfig;
  tintColor?: ColorValue;
  colorScheme?: GlassColorScheme;
  style?: StyleProp<ViewStyle>;
  children?: ReactNode;
  ref?: Ref<View>;
};

export type GlassContainerProps = ViewProps & {
  /** Distance at which glass children start to merge. */
  spacing?: number;
  ref?: Ref<View>;
};
