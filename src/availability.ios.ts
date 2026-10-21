import { isGlassEffectAPIAvailable, isLiquidGlassAvailable } from 'expo-glass-effect';

export { isGlassEffectAPIAvailable, isLiquidGlassAvailable };

/** Apple's material renders only when the design is in use and the runtime API exists. */
export function hasNativeGlass(): boolean {
  return isLiquidGlassAvailable() && isGlassEffectAPIAvailable();
}
