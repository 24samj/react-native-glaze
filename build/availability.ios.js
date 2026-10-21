import { isGlassEffectAPIAvailable, isLiquidGlassAvailable } from 'expo-glass-effect';
export { isGlassEffectAPIAvailable, isLiquidGlassAvailable };
/** Apple's material renders only when the design is in use and the runtime API exists. */
export function hasNativeGlass() {
    return isLiquidGlassAvailable() && isGlassEffectAPIAvailable();
}
//# sourceMappingURL=availability.ios.js.map