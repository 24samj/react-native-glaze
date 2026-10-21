import { View } from 'react-native';

import type { GlassContainerProps } from './types';

/** Web and other platforms: a plain View. */
export default function GlassContainer({ spacing: _spacing, ...props }: GlassContainerProps) {
  return <View {...props} />;
}
