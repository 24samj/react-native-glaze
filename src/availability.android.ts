import { requireNativeModule } from 'expo';

type GlazeNativeModule = {
  isLiquidGlassAvailable(): boolean;
};

const Glaze = requireNativeModule<GlazeNativeModule>('Glaze');

/** `true` when the full lens tier is active (API 33+, not demoted). */
export function isLiquidGlassAvailable(): boolean {
  return Glaze.isLiquidGlassAvailable();
}

/** Always `true` on Android: some glass tier always renders. */
export function isGlassEffectAPIAvailable(): boolean {
  return true;
}
