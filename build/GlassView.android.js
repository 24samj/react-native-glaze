import { jsx as _jsx } from "react/jsx-runtime";
import { requireNativeView } from 'expo';
import { processColor } from 'react-native';
import { cornerRadiiFrom } from './cornerRadii';
const NativeGlazeView = requireNativeView('Glaze', 'GlazeView');
export default function GlassView({ glassEffectStyle = 'regular', tintColor, isInteractive = false, colorScheme = 'auto', style, ...rest }) {
    const config = typeof glassEffectStyle === 'object' ? glassEffectStyle : { style: glassEffectStyle };
    const tint = tintColor != null ? processColor(tintColor) : null;
    return (_jsx(NativeGlazeView, { ...rest, style: style, glassStyle: config.style, styleAnimation: [config.animate ? 1 : 0, config.animationDuration ?? -1], 
        // processColor returns an opaque object for PlatformColor; those fall back to no tint
        glassTint: typeof tint === 'number' ? tint : 0, interactive: isInteractive, glassColorScheme: colorScheme, cornerRadii: cornerRadiiFrom(style) }));
}
//# sourceMappingURL=GlassView.android.js.map