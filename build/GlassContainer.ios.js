import { jsx as _jsx } from "react/jsx-runtime";
import { GlassContainer as NativeGlassContainer } from 'expo-glass-effect';
import { View } from 'react-native';
import { hasNativeGlass } from './availability.ios';
/** iOS 26+: Apple's glass container. Below that, glass doesn't merge, so a plain View. */
export default function GlassContainer(props) {
    if (hasNativeGlass()) {
        return _jsx(NativeGlassContainer, { ...props });
    }
    const { spacing: _spacing, ...rest } = props;
    return _jsx(View, { ...rest });
}
//# sourceMappingURL=GlassContainer.ios.js.map