import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { BlurView } from 'expo-blur';
import { GlassView as NativeGlassView } from 'expo-glass-effect';
import { StyleSheet, useColorScheme, View } from 'react-native';
import { hasNativeGlass } from './availability.ios';
/**
 * iOS 26+: Apple's Liquid Glass via expo-glass-effect, props forwarded untouched.
 * Below iOS 26, or when the app opts out of Liquid Glass: a system blur material, so the UI
 * still reads as glass instead of expo-glass-effect's plain View.
 */
export default function GlassView(props) {
    // Hooks run unconditionally; hasNativeGlass() is constant for the process
    const systemScheme = useColorScheme();
    if (hasNativeGlass()) {
        return _jsx(NativeGlassView, { ...props });
    }
    const { glassEffectStyle, tintColor, isInteractive: _isInteractive, colorScheme, style, children, ...rest } = props;
    const styleName = typeof glassEffectStyle === 'object' ? glassEffectStyle.style : (glassEffectStyle ?? 'regular');
    if (styleName === 'none') {
        return (_jsx(View, { style: style, ...rest, children: children }));
    }
    const scheme = !colorScheme || colorScheme === 'auto' ? systemScheme : colorScheme;
    const dark = scheme === 'dark';
    const tint = styleName === 'clear'
        ? dark
            ? 'systemUltraThinMaterialDark'
            : 'systemUltraThinMaterialLight'
        : dark
            ? 'systemMaterialDark'
            : 'systemMaterialLight';
    return (_jsxs(View, { style: [styles.clip, style], ...rest, children: [_jsx(BlurView, { tint: tint, intensity: 100, style: StyleSheet.absoluteFill, pointerEvents: "none" }), tintColor != null ? (_jsx(View, { style: [StyleSheet.absoluteFill, { backgroundColor: tintColor, opacity: 0.6 }], pointerEvents: "none" })) : null, children] }));
}
const styles = StyleSheet.create({
    // The blur must follow the corner radius
    clip: { overflow: 'hidden' },
});
//# sourceMappingURL=GlassView.ios.js.map