import { GlassContainer as NativeGlassContainer } from 'expo-glass-effect';
import { View } from 'react-native';

import { hasNativeGlass } from './availability.ios';
import type { GlassContainerProps } from './types';

/** iOS 26+: Apple's glass container. Below that, glass doesn't merge, so a plain View. */
export default function GlassContainer(props: GlassContainerProps) {
  if (hasNativeGlass()) {
    return <NativeGlassContainer {...props} />;
  }
  const { spacing: _spacing, ...rest } = props;
  return <View {...rest} />;
}
