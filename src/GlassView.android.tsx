import { requireNativeView } from 'expo';
import type { Ref } from 'react';
import { processColor, type View, type ViewProps } from 'react-native';

import { cornerRadiiFrom } from './cornerRadii';
import type { GlassColorScheme, GlassStyle, GlassViewProps } from './types';

type NativeGlazeViewProps = ViewProps & {
  glassStyle: GlassStyle;
  /** [animate (0|1), durationSeconds (-1 = default)] */
  styleAnimation: [number, number];
  glassTint: number;
  interactive: boolean;
  glassColorScheme: GlassColorScheme;
  cornerRadii: [number, number, number, number];
  ref?: Ref<View>;
};

const NativeGlazeView = requireNativeView<NativeGlazeViewProps>('Glaze', 'GlazeView');

export default function GlassView({
  glassEffectStyle = 'regular',
  tintColor,
  isInteractive = false,
  colorScheme = 'auto',
  style,
  ...rest
}: GlassViewProps) {
  const config = typeof glassEffectStyle === 'object' ? glassEffectStyle : { style: glassEffectStyle };
  const tint = tintColor != null ? processColor(tintColor) : null;
  return (
    <NativeGlazeView
      {...rest}
      style={style}
      glassStyle={config.style}
      styleAnimation={[config.animate ? 1 : 0, config.animationDuration ?? -1]}
      // processColor returns an opaque object for PlatformColor; those fall back to no tint
      glassTint={typeof tint === 'number' ? tint : 0}
      interactive={isInteractive}
      glassColorScheme={colorScheme}
      cornerRadii={cornerRadiiFrom(style)}
    />
  );
}
