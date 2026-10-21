import { View } from 'react-native';

import type { GlassViewProps } from './types';

/** Web and other platforms: a plain View. */
export default function GlassView({
  glassEffectStyle: _glassEffectStyle,
  tintColor: _tintColor,
  isInteractive: _isInteractive,
  colorScheme: _colorScheme,
  ...props
}: GlassViewProps) {
  return <View {...props} />;
}
