import { requireNativeView } from 'expo';
import type { Ref } from 'react';
import type { View, ViewProps } from 'react-native';

import type { GlassContainerProps } from './types';

type NativeGlazeContainerProps = ViewProps & {
  spacing?: number;
  ref?: Ref<View>;
};

const NativeGlazeContainer = requireNativeView<NativeGlazeContainerProps>(
  'Glaze',
  'GlazeContainerView'
);

/** Merges two glass children closer than `spacing`; with more, each draws as separate glass (v1). */
export default function GlassContainer(props: GlassContainerProps) {
  return <NativeGlazeContainer {...props} />;
}
