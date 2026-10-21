import { jsx as _jsx } from "react/jsx-runtime";
import { requireNativeView } from 'expo';
const NativeGlazeContainer = requireNativeView('Glaze', 'GlazeContainerView');
/** Merges two glass children closer than `spacing`; with more, each draws as separate glass (v1). */
export default function GlassContainer(props) {
    return _jsx(NativeGlazeContainer, { ...props });
}
//# sourceMappingURL=GlassContainer.android.js.map